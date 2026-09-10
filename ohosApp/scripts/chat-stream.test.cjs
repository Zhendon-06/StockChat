const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

// Run the production ArkTS sources with mocked Harmony HTTP, UTF-8 and timer APIs.
// The HAP build separately checks ArkTS restrictions and real SDK signatures.
const studio = process.env.DEV_STUDIO_HOME || '/Applications/DevEco-Studio.app/Contents';
const ts = require(process.env.STOCKCHAT_TYPESCRIPT_PATH ||
    path.join(studio, 'tools/hvigor/hvigor/node_modules/typescript'));
const sourceDirectory = path.join(__dirname, '../entry/src/main/ets/kuikly/modules');
const compiled = new Map();

function fixture(params = {}) {
    const events = [];
    const handlers = new Map();
    const timers = new Map();
    let nextTimer = 0;
    let resolveStatus;
    let rejectRequest;
    let finished = 0;
    const httpRequest = {
        destroyed: 0,
        on: (event, handler) => handlers.set(event, handler),
        off: event => handlers.delete(event),
        destroy() { this.destroyed++; },
        requestInStream(url, options) {
            this.url = url;
            this.options = options;
            return new Promise((resolve, reject) => { resolveStatus = resolve; rejectRequest = reject; });
        },
    };
    const sdk = {
        '@ohos.net.http': { default: { createHttp: () => httpRequest, RequestMethod: { POST: 'POST' } } },
        '@ohos.util': { default: { TextDecoder: { create(encoding, options) {
            const decoder = new TextDecoder(encoding, options);
            return { decodeToString: (bytes, streamOptions) => decoder.decode(bytes, streamOptions) };
        } } } },
    };
    const modules = new Map();
    function load(name) {
        if (sdk[name]) return sdk[name];
        if (modules.has(name)) return modules.get(name);
        if (!compiled.has(name)) {
            const filename = path.join(sourceDirectory, `${name}.ets`);
            compiled.set(name, ts.transpileModule(fs.readFileSync(filename, 'utf8'), {
                compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS },
                fileName: filename.replace(/\.ets$/, '.ts'),
            }).outputText);
        }
        const module = { exports: {} };
        vm.runInNewContext(compiled.get(name), {
            exports: module.exports, require: load, Uint8Array,
            setTimeout(callback, delay) {
                assert.equal(delay, 50);
                timers.set(++nextTimer, callback);
                return nextTimer;
            },
            clearTimeout: id => timers.delete(id),
        }, { filename: `${name}.ets` });
        modules.set(name, module.exports);
        return module.exports;
    }
    const { StockChatStreamRequest } = load('./StockChatStreamRequest');
    const request = new StockChatStreamRequest(event => events.push(JSON.parse(JSON.stringify(event))), () => finished++);
    request.start(typeof params === 'string' ? params : JSON.stringify({
        url: 'https://example.test/v1/chat/completions', apiKey: 'test-key',
        requestBody: JSON.stringify({ model: 'test-model', messages: [], stream: false }), ...params,
    }));
    const emit = (event, value) => handlers.get(event)?.(value);
    return {
        events, request, httpRequest, handlers, timers, emit,
        get finished() { return finished; },
        headers: (type = 'text/event-stream; charset=utf-8') => emit('headersReceive', { 'Content-Type': type }),
        data(value) {
            const bytes = typeof value === 'string' ? new TextEncoder().encode(value) : value;
            emit('dataReceive', bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength));
        },
        flush() { for (const callback of [...timers.values()]) callback(); },
        async status(code = 200) { resolveStatus(code); await Promise.resolve(); },
        async fail(message) { rejectRequest({ message }); await Promise.resolve(); await Promise.resolve(); },
        async end(code = 200) { emit('dataEnd'); await this.status(code); },
    };
}

const delta = content => `data: ${JSON.stringify({ choices: [{ delta: { content } }] })}\n\n`;
const done = 'data: [DONE]\n\n';
const text = f => f.events.filter(event => event.event === 'delta').map(event => event.content).join('');
const terminal = f => f.events.filter(event => event.success === 0 || event.event === 'end');
function assertReleased(f) {
    assert.equal(f.finished, 1);
    assert.equal(f.httpRequest.destroyed, 1);
    assert.equal(f.handlers.size, 0);
    assert.equal(f.timers.size, 0);
    assert.equal(terminal(f).length, 1);
}

test('sends a streaming POST with custom headers and emits deltas before the response ends', async () => {
    const f = fixture({ headers: JSON.stringify({ Authorization: 'custom-token', 'X-Trace': 'trace' }) });
    assert.equal(f.httpRequest.options.method, 'POST');
    assert.equal(f.httpRequest.options.header.authorization, 'custom-token');
    assert.equal(f.httpRequest.options.header['x-trace'], 'trace');
    assert.equal(f.httpRequest.options.header.accept, 'text/event-stream');
    assert.equal(JSON.parse(f.httpRequest.options.extraData).stream, true);
    f.headers();
    f.data(delta('首段'));
    assert.equal(text(f), '首段');
    assert.equal(terminal(f).length, 0);
    f.data(delta('第二段') + delta('第三段'));
    assert.equal(text(f), '首段');
    assert.equal(f.timers.size, 1);
    f.flush();
    assert.equal(text(f), '首段第二段第三段');
    f.data(delta('尾段') + done);
    await f.end();
    assert.equal(text(f), '首段第二段第三段尾段');
    assert.equal(f.events.at(-1).event, 'end');
    assertReleased(f);
});

test('preserves Chinese, emoji, BOM, split CRLF and multiline SSE at every byte boundary', async () => {
    const source = '\uFEFF: keepalive\r\nevent: message\r\nid: 1\r\n' +
        'data: {"choices":\r\ndata: [{"delta":{"content":"中文📈 流式"}}]}\r\n\r\n' +
        'data: [DONE]\r\n\r\n';
    const bytes = new TextEncoder().encode(source);
    for (let split = 1; split < bytes.length; split++) {
        const f = fixture();
        f.headers();
        f.data(bytes.slice(0, split));
        f.data(bytes.slice(split));
        await f.end();
        assert.equal(text(f), '中文📈 流式', `byte split ${split}`);
        assert.equal(f.events.at(-1).event, 'end');
        assertReleased(f);
    }
    const f = fixture();
    f.headers();
    for (const byte of bytes) f.data(new Uint8Array([byte]));
    await f.end();
    assert.equal(text(f), '中文📈 流式');
    assertReleased(f);
});

test('accepts finish_reason without DONE, content arrays and a final event without newline', async () => {
    const f = fixture();
    f.headers();
    f.data('data: ' + JSON.stringify({ output: { choices: [{ delta: {
        content: [{ text: '阿里云' }, { content: '回答' }],
    }, finish_reason: 'stop' }] } }));
    await f.status(); // HTTP status may arrive before dataEnd.
    assert.equal(terminal(f).length, 0);
    f.emit('dataEnd');
    assert.equal(text(f), '阿里云回答');
    assert.equal(f.events.at(-1).event, 'end');
    assertReleased(f);
});

test('accepts a normal JSON response from providers that ignore stream=true', async () => {
    const f = fixture();
    assert.equal(f.httpRequest.options.header.authorization, 'Bearer test-key');
    f.headers('application/json');
    f.data(JSON.stringify({ choices: [{ message: { content: '完整回答' } }] }));
    assert.equal(f.events.length, 0);
    await f.end();
    assert.equal(text(f), '完整回答');
    assert.equal(f.events.at(-1).event, 'end');
    assertReleased(f);
});

test('HTTP and provider errors produce one failure and never a success end', async () => {
    for (const [type, body, code, message] of [
        ['application/json', '{"error":{"message":"Key 无效"}}', 401, 'Key 无效'],
        ['text/html', '<html>gateway failed</html>', 502, 'HTTP 502'],
        ['text/event-stream', 'event: error\ndata: {"message":"限流"}\n\n', 200, '限流'],
        ['text/event-stream', 'data: {"error":{"message":"配额不足"}}\n\n', 200, '配额不足'],
        ['text/event-stream', 'data: {"code":"InvalidApiKey","message":"密钥无效"}\n\n', 200, '密钥无效'],
        ['text/event-stream', 'event: error\ndata: unavailable\n\n', 200, 'unavailable'],
    ]) {
        const f = fixture();
        f.headers(type);
        f.data(body);
        await f.end(code);
        assert.equal(f.events.at(-1).success, 0);
        assert.ok(f.events.at(-1).errorMessage.includes(message));
        assertReleased(f);
    }
});

test('rejects broken JSON, empty answers, truncated SSE and invalid UTF-8', async () => {
    for (const [type, body, errorCode] of [
        ['application/json', '{bad', 'STREAM_INVALID_RESPONSE'],
        ['application/json', '{}', 'STREAM_EMPTY_RESPONSE'],
        ['text/event-stream', 'data: {bad}\n\n', 'STREAM_INVALID_RESPONSE'],
        ['text/event-stream', delta('尚未结束'), 'STREAM_TRUNCATED'],
        ['text/event-stream', new Uint8Array([0xff]), 'STREAM_INVALID_ENCODING'],
        ['text/event-stream', new Uint8Array([0xe4, 0xb8]), 'STREAM_INVALID_ENCODING'],
    ]) {
        const f = fixture();
        f.headers(type);
        f.data(body);
        await f.end();
        assert.equal(f.events.at(-1).errorCode, errorCode);
        assertReleased(f);
    }
});

test('flushes pending text on network failure and ignores late callbacks after cancellation', async () => {
    for (const cancel of [false, true]) {
        const f = fixture();
        f.headers();
        f.data(delta('已收到') + delta('的内容'));
        if (cancel) f.request.cancel();
        else await f.fail('网络中断');
        assert.equal(text(f), '已收到的内容');
        assert.equal(f.events.at(-1).errorCode, cancel ? 'STREAM_CANCELLED' : 'STREAM_NETWORK_ERROR');
        f.data(delta('迟到的数据') + done);
        f.request.cancel();
        await f.end();
        f.flush();
        assertReleased(f);
        assert.equal(text(f), '已收到的内容');
    }
});

test('rejects oversized responses and malformed request parameters', () => {
    const f = fixture();
    f.headers('application/json');
    f.data(new Uint8Array(4 * 1024 * 1024 + 1));
    assert.equal(f.events.at(-1).errorCode, 'RESPONSE_TOO_LARGE');
    assertReleased(f);
    for (const params of ['{bad', { url: '' }, { requestBody: '[]' }, { headers: '{bad' }]) {
        const invalid = fixture(params);
        assert.equal(invalid.events.at(-1).errorCode, 'INVALID_STREAM_REQUEST');
        assert.equal(invalid.finished, 1);
        assert.equal(invalid.httpRequest.options, undefined);
    }
});

test('concurrent requests keep their deltas and cleanup independent', async () => {
    const first = fixture();
    const second = fixture();
    first.headers();
    second.headers();
    first.data(delta('会话一'));
    second.data(delta('会话二'));
    first.request.cancel();
    second.data(delta('完成') + done);
    await second.end();
    assert.equal(text(first), '会话一');
    assert.equal(text(second), '会话二完成');
    assertReleased(first);
    assertReleased(second);
});
