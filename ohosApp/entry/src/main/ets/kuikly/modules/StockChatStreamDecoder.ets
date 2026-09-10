export interface StockChatStreamEvent {
  success: number;
  event?: string;
  content?: string;
  errorCode?: string;
  errorMessage?: string;
}

export function streamFailure(code: string, message: string): StockChatStreamEvent {
  return { success: 0, errorCode: code, errorMessage: message };
}

export function streamRecord(value: Object | null | undefined): Record<string, Object> {
  if (value != null && typeof value === 'object' && !Array.isArray(value)) {
    return value as Record<string, Object>;
  }
  return {};
}

export function parseStreamRecord(value: Object | null | undefined): Record<string, Object> {
  return streamRecord(typeof value === 'string' ? JSON.parse(value) : value);
}

function contentText(value: Object | null | undefined): string {
  if (typeof value === 'string') {
    return value;
  }
  if (Array.isArray(value)) {
    return (value as Object[]).map((part: Object) => contentText(part)).join('');
  }
  const record = streamRecord(value);
  return record['text'] != null ? contentText(record['text']) :
    record['content'] != null ? contentText(record['content']) : '';
}

function firstChoice(payload: Record<string, Object>): Record<string, Object> {
  const choices = payload['choices'] ?? streamRecord(payload['output'])['choices'];
  return Array.isArray(choices) ? streamRecord((choices as Object[])[0]) : {};
}

export function streamContent(payload: Record<string, Object>, completion: boolean = false): string {
  const choice = firstChoice(payload);
  const primary = streamRecord(choice[completion ? 'message' : 'delta']);
  const secondary = streamRecord(choice[completion ? 'delta' : 'message']);
  return contentText(primary['content']) || contentText(secondary['content']) || contentText(payload['content']);
}

export function streamError(payload: Record<string, Object>): string {
  const error = payload['error'];
  if (error != null && error !== '') {
    const message = streamRecord(error)['message'];
    return typeof message === 'string' ? message :
      typeof error === 'string' ? error : '模型服务返回了错误，请重新生成。';
  }
  // DashScope also reports errors as top-level code/message fields.
  if (typeof payload['code'] === 'string' && payload['code'] !== '') {
    return typeof payload['message'] === 'string' ? payload['message'] as string : payload['code'] as string;
  }
  return '';
}

// Text is decoded incrementally by the transport; SSE lines and events may span packets.
export class StockChatStreamDecoder {
  private buffer: string = '';
  private dataLines: string[] = [];
  private eventName: string = '';
  private terminal: boolean = false;
  private skipLF: boolean = false;
  private firstLine: boolean = true;
  private emit: (event: StockChatStreamEvent) => void;

  constructor(emit: (event: StockChatStreamEvent) => void) {
    this.emit = emit;
  }

  append(text: string): void {
    if (this.terminal) {
      return;
    }
    if (this.skipLF && text.length > 0) {
      if (text.startsWith('\n')) {
        text = text.substring(1);
      }
      this.skipLF = false;
    }
    this.buffer += text;
    let start = 0;
    for (let i = 0; i < this.buffer.length && !this.terminal; i++) {
      const character = this.buffer.charAt(i);
      if (character !== '\r' && character !== '\n') {
        continue;
      }
      this.consumeLine(this.buffer.substring(start, i));
      if (character === '\r') {
        if (this.buffer.charAt(i + 1) === '\n') {
          i++;
        } else if (i + 1 === this.buffer.length) {
          this.skipLF = true;
        }
      }
      start = i + 1;
    }
    this.buffer = this.buffer.substring(start);
  }

  finish(): void {
    this.append('\n\n');
    if (!this.terminal) {
      this.fail('STREAM_TRUNCATED', 'AI 流式连接提前结束，请重新生成。');
    }
  }

  private consumeLine(line: string): void {
    if (this.firstLine) {
      line = line.replace(/^\uFEFF/, '');
      this.firstLine = false;
    }
    if (line.length === 0) {
      this.consumeEvent();
      this.eventName = '';
      return;
    }
    const separator = line.indexOf(':');
    const field = separator < 0 ? line : line.substring(0, separator);
    let value = separator < 0 ? '' : line.substring(separator + 1);
    if (value.startsWith(' ')) {
      value = value.substring(1);
    }
    if (field === 'data') {
      this.dataLines.push(value);
    } else if (field === 'event') {
      this.eventName = value;
    }
  }

  private consumeEvent(): void {
    if (this.dataLines.length === 0 || this.terminal) {
      return;
    }
    const data = this.dataLines.join('\n').trim();
    this.dataLines = [];
    if (this.eventName.toLowerCase() === 'error') {
      let message = data;
      try {
        const payload = parseStreamRecord(data);
        message = streamError(payload) || contentText(payload['message']) || data;
      } catch (_) {
        // Some providers send plain text in an error event.
      }
      this.fail('STREAM_PROVIDER_ERROR', message || '模型服务返回了流式错误。');
      return;
    }
    if (data === '[DONE]') {
      this.end();
      return;
    }
    if (data.length === 0) {
      return;
    }
    let payload: Record<string, Object>;
    try {
      payload = parseStreamRecord(data);
      if (Object.keys(payload).length === 0) {
        throw new Error('Empty stream payload');
      }
    } catch (_) {
      this.fail('STREAM_INVALID_RESPONSE', 'AI 流式响应格式无效，请重新生成。');
      return;
    }
    const error = streamError(payload);
    if (error.length > 0) {
      this.fail('STREAM_PROVIDER_ERROR', error);
      return;
    }
    const content = streamContent(payload);
    if (content.length > 0) {
      this.emit({ success: 1, event: 'delta', content: content });
    }
    const reason = firstChoice(payload)['finish_reason'];
    if (typeof reason === 'string' && reason.length > 0 && reason !== 'null') {
      this.end();
    }
  }

  private end(): void {
    this.terminal = true;
    this.emit({ success: 1, event: 'end' });
  }

  private fail(code: string, message: string): void {
    this.terminal = true;
    this.emit(streamFailure(code, message));
  }
}
