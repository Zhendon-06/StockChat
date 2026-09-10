import http from '@ohos.net.http';
import util from '@ohos.util';
import { BusinessError } from '@kit.BasicServicesKit';
import { KRAny, KuiklyRenderCallback } from '@kuikly-open/render';
import {
  parseStreamRecord,
  StockChatStreamDecoder,
  StockChatStreamEvent,
  streamContent,
  streamError,
  streamFailure
} from './StockChatStreamDecoder';

export class StockChatStreamRequest {
  private request: http.HttpRequest | null = null;
  private decoder: util.TextDecoder = util.TextDecoder.create('utf-8', { fatal: true });
  private sse: StockChatStreamDecoder = new StockChatStreamDecoder((event: StockChatStreamEvent) => {
    if (event.event === 'end') {
      this.streamEnded = true;
    } else {
      this.receiveEvent(event);
    }
  });
  private isEventStream: boolean = false;
  private streamEnded: boolean = false;
  private dataEnded: boolean = false;
  private statusCode: number | null = null;
  private finished: boolean = false;
  private responseBytes: number = 0;
  private responseBody: string = '';
  private pendingDelta: string = '';
  private emittedDelta: boolean = false;
  private flushTimer: number | null = null;
  private provider: string = '模型服务';
  private callback: KuiklyRenderCallback | null;
  private onFinished: (request: StockChatStreamRequest) => void;

  constructor(callback: KuiklyRenderCallback | null, onFinished: (request: StockChatStreamRequest) => void) {
    this.callback = callback;
    this.onFinished = onFinished;
  }

  start(params: KRAny): void {
    let url: string;
    let body: Record<string, Object>;
    const headers: Record<string, string> = {};
    try {
      const payload = parseStreamRecord(params);
      url = typeof payload['url'] === 'string' ? (payload['url'] as string).trim() : '';
      body = parseStreamRecord(payload['requestBody']);
      if (!/^https?:\/\/[^/\s]+/i.test(url) || Object.keys(body).length === 0) {
        throw new Error('Invalid request');
      }
      const provider = payload['providerDisplayName'];
      if (typeof provider === 'string' && provider.trim().length > 0) {
        this.provider = provider.trim();
      }
      const suppliedHeaders = parseStreamRecord(payload['headers']);
      Object.keys(suppliedHeaders).forEach((key: string) => {
        const value = suppliedHeaders[key];
        if (typeof value === 'string' && value.trim().length > 0) {
          headers[key.toLowerCase()] = value;
        }
      });
      const apiKey = payload['apiKey'];
      if (!headers['authorization'] && typeof apiKey === 'string' && apiKey.trim().length > 0) {
        headers['authorization'] = `Bearer ${apiKey.trim()}`;
      }
      headers['content-type'] = 'application/json';
      headers['accept'] = 'text/event-stream';
      body['stream'] = true;
    } catch (_) {
      this.receiveEvent(streamFailure('INVALID_STREAM_REQUEST', 'AI 流式请求参数无效。'));
      return;
    }

    try {
      const request = http.createHttp();
      this.request = request;
      request.on('headersReceive', (value: Object) => {
        const responseHeaders = value as Record<string, Object>;
        this.isEventStream = Object.keys(responseHeaders).some((key: string) => {
          const contentType = responseHeaders[key];
          return key.toLowerCase() === 'content-type' && typeof contentType === 'string' &&
            contentType.toLowerCase().split(';')[0].trim() === 'text/event-stream';
        });
      });
      request.on('dataReceive', (data: ArrayBuffer) => this.receiveData(data));
      request.on('dataEnd', () => {
        this.dataEnded = true;
        this.finishResponse();
      });
      request.requestInStream(url, {
        method: http.RequestMethod.POST,
        header: headers,
        extraData: JSON.stringify(body),
        usingCache: false,
        connectTimeout: 20000,
        readTimeout: 90000
      }).then((statusCode: number) => {
        this.statusCode = statusCode;
        this.finishResponse();
      }).catch((error: BusinessError) => {
        this.receiveEvent(streamFailure('STREAM_NETWORK_ERROR',
          error.message || `${this.provider} 流式请求失败，请稍后重试。`));
      });
    } catch (_) {
      this.receiveEvent(streamFailure('STREAM_NETWORK_ERROR', `${this.provider} 流式请求启动失败，请稍后重试。`));
    }
  }

  cancel(): void {
    this.receiveEvent(streamFailure('STREAM_CANCELLED', 'AI 请求已取消。'));
  }

  private receiveData(data: ArrayBuffer): void {
    if (this.finished) {
      return;
    }
    this.responseBytes += data.byteLength;
    if (this.responseBytes > 4 * 1024 * 1024) {
      this.receiveEvent(streamFailure('RESPONSE_TOO_LARGE', 'AI 响应过大，请重新生成。'));
      return;
    }
    let text: string;
    try {
      // Preserve incomplete UTF-8 sequences, including Chinese and emoji, across packets.
      text = this.decoder.decodeToString(new Uint8Array(data), { stream: true });
    } catch (_) {
      this.receiveEvent(streamFailure('STREAM_INVALID_ENCODING', 'AI 流式响应编码无效，请重新生成。'));
      return;
    }
    this.consumeText(text);
  }

  private consumeText(text: string): void {
    if (this.isEventStream) {
      this.sse.append(text);
    } else {
      this.responseBody += text;
    }
  }

  private finishResponse(): void {
    // Harmony delivers the HTTP status and dataEnd separately; accept either ordering.
    if (this.finished || !this.dataEnded || this.statusCode == null) {
      return;
    }
    let tail: string;
    try {
      tail = this.decoder.decodeToString(new Uint8Array(0), { stream: false });
    } catch (_) {
      this.receiveEvent(streamFailure('STREAM_INVALID_ENCODING', 'AI 流式响应编码无效，请重新生成。'));
      return;
    }
    this.consumeText(tail);
    if (this.finished) {
      return;
    }
    if (this.statusCode < 200 || this.statusCode >= 300) {
      let message = '';
      try {
        message = streamError(parseStreamRecord(this.responseBody));
      } catch (_) {
        // Non-JSON HTTP errors still include the status code below.
      }
      this.receiveEvent(streamFailure(`STREAM_HTTP_${this.statusCode}`,
        message || `${this.provider} 请求失败（HTTP ${this.statusCode}）。`));
    } else if (this.isEventStream) {
      this.sse.finish();
      if (this.streamEnded) {
        this.receiveEvent({ success: 1, event: 'end' });
      }
    } else {
      this.deliverJsonResponse();
    }
  }

  private deliverJsonResponse(): void {
    let payload: Record<string, Object>;
    try {
      payload = parseStreamRecord(this.responseBody);
    } catch (_) {
      this.receiveEvent(streamFailure('STREAM_INVALID_RESPONSE', `${this.provider} 返回了无法解析的响应。`));
      return;
    }
    const error = streamError(payload);
    if (error.length > 0) {
      this.receiveEvent(streamFailure('STREAM_PROVIDER_ERROR', error));
      return;
    }
    const content = streamContent(payload, true);
    if (content.trim().length === 0) {
      this.receiveEvent(streamFailure('STREAM_EMPTY_RESPONSE', `${this.provider} 没有返回可展示的回答。`));
      return;
    }
    this.receiveEvent({ success: 1, event: 'delta', content: content });
    this.receiveEvent({ success: 1, event: 'end' });
  }

  private receiveEvent(event: StockChatStreamEvent): void {
    if (this.finished) {
      return;
    }
    if (event.event === 'delta') {
      this.pendingDelta += event.content ?? '';
      if (!this.emittedDelta) {
        this.emittedDelta = true;
        this.flushDelta();
      } else if (this.flushTimer == null) {
        this.flushTimer = setTimeout(() => this.flushDelta(), 50);
      }
      return;
    }
    this.flushDelta();
    this.finished = true;
    const callback = this.callback;
    this.callback = null;
    this.request?.off('headersReceive');
    this.request?.off('dataReceive');
    this.request?.off('dataEnd');
    this.request?.destroy();
    this.request = null;
    this.responseBody = '';
    this.onFinished(this);
    callback?.(event);
  }

  private flushDelta(): void {
    if (this.flushTimer != null) {
      clearTimeout(this.flushTimer);
      this.flushTimer = null;
    }
    if (!this.finished && this.pendingDelta.length > 0) {
      const content = this.pendingDelta;
      this.pendingDelta = '';
      this.callback?.({ success: 1, event: 'delta', content: content });
    }
  }
}
