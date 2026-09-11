import http from 'node:http';
import process from 'node:process';

const PORT = positiveInt(process.env.PORT, 3000);
const HOST = process.env.HOST || '127.0.0.1';
const UPSTREAM_BASE_URL = (process.env.UPSTREAM_BASE_URL || 'https://dashscope.aliyuncs.com/compatible-mode/v1').replace(/\/+$/, '');
const QWEN_API_KEY = (process.env.QWEN_API_KEY || '').trim();
const PROXY_AUTH_TOKEN = (process.env.PROXY_AUTH_TOKEN || '').trim();
const REQUEST_TIMEOUT_MS = positiveInt(process.env.REQUEST_TIMEOUT_MS, 120_000);
const MAX_BODY_BYTES = positiveInt(process.env.MAX_BODY_BYTES, 2 * 1024 * 1024);
const CORS_ORIGINS = new Set((process.env.CORS_ORIGINS || '*').split(',').map((item) => item.trim()).filter(Boolean));

function positiveInt(value, fallback) {
  const parsed = Number.parseInt(value || '', 10);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

function sendJson(response, status, payload, extraHeaders = {}) {
  const body = JSON.stringify(payload);
  response.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'cache-control': 'no-store',
    'content-length': Buffer.byteLength(body),
    ...extraHeaders,
  });
  response.end(body);
}

function corsHeaders(request) {
  const origin = request.headers.origin;
  const allowed = CORS_ORIGINS.has('*') ? '*' : (origin && CORS_ORIGINS.has(origin) ? origin : null);
  return allowed ? {
    'access-control-allow-origin': allowed,
    'access-control-allow-headers': 'authorization, content-type, x-proxy-token',
    'access-control-allow-methods': 'GET, POST, OPTIONS',
    vary: 'Origin',
  } : {};
}

function isAuthorized(request) {
  if (!PROXY_AUTH_TOKEN) return true;
  const bearer = request.headers.authorization?.match(/^Bearer\s+(.+)$/i)?.[1]?.trim();
  const supplied = request.headers['x-proxy-token']?.trim() || bearer;
  return supplied === PROXY_AUTH_TOKEN;
}

function readBody(request) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    request.on('data', (chunk) => {
      size += chunk.length;
      if (size > MAX_BODY_BYTES) {
        reject(Object.assign(new Error('request body too large'), { statusCode: 413 }));
        return;
      }
      chunks.push(chunk);
    });
    request.on('end', () => resolve(Buffer.concat(chunks)));
    request.on('error', reject);
  });
}

function upstreamUrl(pathname) {
  if (pathname === '/v1/models') return `${UPSTREAM_BASE_URL}/models`;
  if (pathname === '/v1/chat/completions') return `${UPSTREAM_BASE_URL}/chat/completions`;
  return null;
}

async function forward(request, response, pathname) {
  if (!QWEN_API_KEY) return sendJson(response, 503, { error: { message: 'AI proxy is not configured: QWEN_API_KEY is missing', type: 'configuration_error' } });
  const target = upstreamUrl(pathname);
  if (!target) return sendJson(response, 404, { error: { message: 'Unsupported proxy endpoint' } });
  if (!isAuthorized(request)) return sendJson(response, 401, { error: { message: 'Invalid proxy token' } });

  const isModels = pathname === '/v1/models';
  let body;
  try {
    body = isModels ? undefined : await readBody(request);
  } catch (error) {
    return sendJson(response, error.statusCode || 400, { error: { message: error.message || 'Invalid request body', type: 'invalid_request_error' } }, corsHeaders(request));
  }
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
  try {
    const upstream = await fetch(target, {
      method: isModels ? 'GET' : 'POST',
      headers: {
        authorization: `Bearer ${QWEN_API_KEY}`,
        accept: request.headers.accept || 'application/json',
        ...(body ? { 'content-type': request.headers['content-type'] || 'application/json' } : {}),
      },
      body,
      signal: controller.signal,
    });
    const contentType = upstream.headers.get('content-type') || 'application/json';
    const headers = { ...corsHeaders(request), 'cache-control': 'no-store', 'content-type': contentType };
    response.writeHead(upstream.status, headers);
    if (upstream.body) {
      for await (const chunk of upstream.body) response.write(chunk);
    }
    response.end();
  } catch (error) {
    if (!response.headersSent) {
      const message = error.name === 'AbortError' ? 'Upstream AI request timed out' : 'Upstream AI request failed';
      sendJson(response, 502, { error: { message, type: 'upstream_error' } }, corsHeaders(request));
    } else response.destroy(error);
  } finally {
    clearTimeout(timer);
  }
}

const server = http.createServer(async (request, response) => {
  const headers = corsHeaders(request);
  if (request.method === 'OPTIONS') {
    response.writeHead(204, headers);
    response.end();
    return;
  }
  if (request.url === '/healthz' && request.method === 'GET') {
    sendJson(response, 200, { ok: true, service: 'stockchat-ai-proxy' }, headers);
    return;
  }
  const pathname = new URL(request.url || '/', `http://${request.headers.host || 'localhost'}`).pathname;
  if (pathname === '/v1/models' && request.method === 'GET') return forward(request, response, pathname);
  if (pathname === '/v1/chat/completions' && request.method === 'POST') return forward(request, response, pathname);
  sendJson(response, 404, { error: { message: 'Not found' } }, headers);
});

server.listen(PORT, HOST, () => console.log(`StockChat AI proxy listening on http://${HOST}:${PORT}`));
