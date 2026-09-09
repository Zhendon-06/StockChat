const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { readProperties, generateLocalConfig } = require('./local-config.cjs');

test('supports Java properties separators, escapes and continuations', () => {
    assert.deepEqual(readProperties('# ignored\r\n QWEN_API_KEY = fake\\u002dkey==\r\nMIMO_VOICE_API_KEY: voice\\\n  -test\n'), {
        QWEN_API_KEY: 'fake-key==', MIMO_VOICE_API_KEY: 'voice-test',
    });
});

test('IDE builds refresh local keys, env overrides, and Release clears previous Debug values', () => {
    const projectRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'stockchat-config-test-'));
    try {
        const outputPath = path.join(projectRoot, 'build/rawfile/config.json');
        const localPath = path.join(projectRoot, 'local.properties');
        fs.writeFileSync(localPath, 'QWEN_API_KEY=fake-local\nMIMO_VOICE_API_KEY=fake-voice');
        const options = { projectRoot, outputPath, buildMode: 'debug', environment: {} };
        generateLocalConfig(options);
        const read = () => JSON.parse(fs.readFileSync(outputPath, 'utf8'));
        assert.equal(read().qwenApiKey, 'fake-local');
        fs.writeFileSync(localPath, 'QWEN_API_KEY=fake-updated');
        generateLocalConfig(options);
        assert.deepEqual(read(), { qwenApiKey: 'fake-updated', mimoVoiceApiKey: '' });
        generateLocalConfig({ ...options, environment: { QWEN_API_KEY: ' fake-env ' } });
        assert.equal(read().qwenApiKey, 'fake-env');
        generateLocalConfig({ ...options, buildMode: 'release' });
        assert.deepEqual(read(), {});
        fs.unlinkSync(localPath);
        generateLocalConfig(options);
        assert.deepEqual(read(), { qwenApiKey: '', mimoVoiceApiKey: '' });
    } finally {
        fs.rmSync(projectRoot, { recursive: true, force: true });
    }
});
