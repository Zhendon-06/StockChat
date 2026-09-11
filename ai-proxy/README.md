# StockChat AI Proxy

这是 StockChat 的独立 AI API 转发服务。客户端只请求本服务；百炼 `QWEN_API_KEY` 只存在服务器的环境变量或 `.env` 中，不进入 Android、iOS、鸿蒙构建产物。

服务只开放三个端点：

- `GET /healthz`：健康检查，不需要密钥。
- `GET /v1/models`：转发模型列表。
- `POST /v1/chat/completions`：转发文本、视觉和流式对话，保留 SSE 流。

## 本地启动

```bash
cp .env.example .env
# 编辑 .env，至少填写 QWEN_API_KEY；生产环境再设置 PROXY_AUTH_TOKEN
set -a; source .env; set +a
npm start
curl http://127.0.0.1:3000/healthz
```

代理默认绑定 `127.0.0.1`。部署到云服务器时用 Docker 或 Node 进程管理器运行，并把 `HOST=0.0.0.0` 设置在服务环境中；只开放需要的端口，生产环境建议在 Nginx/Caddy 后启用 HTTPS。不要把 `.env` 上传到 Git，也不要把真实 Key 写进客户端的 `local.properties`。

## 客户端配置

在 StockChat 的模型配置页新增一个 OpenAI 兼容 Provider：

- 服务地址：`https://你的域名/v1`（或暂时使用 `http://服务器公网IP:3000/v1`）
- API Key：代理的 `PROXY_AUTH_TOKEN`，不是百炼 Key
- 模型：从 `/v1/models` 加载的模型

如果没有设置 `PROXY_AUTH_TOKEN`，客户端 API Key 可填任意非空值，但公网部署不建议这样做。

## 阿里云部署清单

当前 Edge 控制台未发现可用的 ECS 或轻量应用服务器实例（杭州、上海均显示 0），所以尚未执行远程部署。确认实例所在地域、公网 IP 和登录方式后，在服务器执行：

```bash
git clone <仓库地址> StockChat
cd StockChat/ai-proxy
cp .env.example .env
chmod 600 .env
# 编辑 .env 后启动
npm start
```

然后在阿里云安全组/防火墙只放行 80/443（若直接联调才临时放行 3000），并用 `curl https://域名/healthz` 验证。
