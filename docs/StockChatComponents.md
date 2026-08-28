# StockChat 共享组件说明

## AI 服务配置

文本问答等常规 AI 任务使用阿里云百炼 DashScope，语音输入与回答朗读使用 Xiaomi MiMo。
两个供应商的 API Key 相互隔离，不通过页面输入，也不写入 `commonMain`；Android 本地调试时
请在项目根目录 `local.properties` 中分别配置：

```properties
QWEN_API_KEY=你的千问_API_Key
MIMO_VOICE_API_KEY=你的_MiMo_API_Key
```

构建时也可用同名环境变量覆盖本地配置。千问文本问答只读取 `QWEN_API_KEY`，语音接口
单独读取 `MIMO_VOICE_API_KEY`，两者不会混用。

文本问答使用 `qwen-plus`，请求 DashScope OpenAI 兼容接口
`https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions`。语音识别使用
`mimo-v2.5-asr`，回答朗读使用 `mimo-v2.5-tts` 与 `mimo_default` 音色，统一请求
`https://api.xiaomimimo.com/v1/chat/completions`，并按 MiMo 官方协议使用 `api-key` 请求头。
Android 录音使用 16 kHz 单声道 PCM16，并封装为 WAV；单次录音限制为
300 毫秒至 30 秒。TTS 默认使用流式 PCM16 输出，Android 收到音频分片后立即交给
`AudioTrack` 播放；不支持原生流式桥接的平台保留非流式 WAV 降级路径。API Key 会进入客户端
构建产物，仅适合本地调试，正式环境应改为
服务端代理或短期凭证。

未配置 `QWEN_API_KEY` 时，普通 AI 问答仍使用本地 Mock 降级；可识别的证券报价和走势请求
会优先调用腾讯证券行情服务，因此不依赖 AI Key。腾讯行情不可用或未识别到标的时会展示明确的
错误与重试入口，不会用 Mock 价格冒充实时行情。

## 行情数据与路由

聊天请求先由 `SecuritiesQueryRouter` 在本地识别报价、走势、对比、分析和普通问答，不额外消耗
一次 AI 请求。报价和走势直接返回腾讯行情与结构化卡片；分析类问题先获取同一份行情快照，再把
带时间戳的数据注入 AI 上下文，行情数字始终以接口数据为准。

行情快照和日线使用腾讯证券 `newfqkline` UTF-8 JSON 接口，详情页及分时问题补充调用
`minute/query`。名称查询使用腾讯证券 `smartbox` 搜索接口；该接口在 Web 环境存在 CORS 限制，
Web 端可直接输入六位代码或带交易所代码，正式部署应为名称搜索配置同域服务端代理。所有行情卡片
均标记腾讯行情时间，并保留“仅供参考，不构成投资建议”提示。

## 尺寸与适配

Kuikly 页面中的布局数值使用逻辑布局单位（Android 侧接近 dp，iOS 侧接近 pt），不是截图
里的物理像素。聊天页以约 `400` 逻辑宽度作为设计基准，通过 `StockChatLayoutMetrics` 对
关键间距、字体和控件尺寸做受限比例调整，并使用 `pageViewWidth` 计算 Drawer 与欢迎主视觉，
避免在不同屏幕上整体放大。欢迎 Logo 单独限制在约 `110–128` 逻辑单位，避免原先接近整屏宽。

## 欢迎 Logo

欢迎态使用 `assets/common/stockchat_logo_v2.png`，通过
`ImageUri.commonAssets("stockchat_logo_v2.png")` 加载，并按原图比例自适应宽度，避免
绑定到具体页面目录。

## Markdown

聊天回答的 `AnswerBlock.Markdown` 使用 `KuiklyMarkdown` 1.0.6，并保留
`fallbackText` 作为空源文本或组件不可用时的降级内容。行情卡片与 Markdown
块分别渲染，避免把结构化行情压成纯文本。

## 走势绘制

走势区域当前使用 Kuikly `Canvas` 绘制轻量折线。已评估
`KuiklyChartView`：该组件当前没有与本项目 OpenHarmony target 对应的发布变体，
为一个固定尺寸的走势图引入它会阻断跨端变体解析。因此保留 Canvas 作为
跨端降级实现，并对空数据和单点数据做保护；后续需要缩放、Tooltip 或多序列时，
可在补齐 OHOS 变体后替换为 `KuiklyChartView`。
