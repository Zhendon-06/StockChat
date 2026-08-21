# StockChat 共享组件说明

## MiMo 配置

Android Demo 的聊天与语音识别使用 Xiaomi MiMo OpenAI 兼容接口。API Key 不通过页面
输入，也不写入 `commonMain`；请直接填写项目根目录 `local.properties` 中的空配置：

```properties
MIMO_API_KEY=你的_API_Key
```

构建时也可用同名环境变量覆盖本地配置。聊天模型为 `mimo-v2.5`，ASR 模型为
`mimo-v2.5-asr`，统一请求 `https://api.xiaomimimo.com/v1/chat/completions`。
Android 录音使用 16 kHz 单声道 PCM16，并封装为 MiMo 支持的 WAV；单次录音限制为
300 毫秒至 30 秒。当前 API Key 会进入客户端构建产物，仅适合本地 Demo，正式环境应改为
服务端代理或短期凭证。

## 尺寸与适配

Kuikly 页面中的布局数值使用逻辑布局单位（Android 侧接近 dp，iOS 侧接近 pt），不是截图
里的物理像素。聊天页以约 `400` 逻辑宽度作为设计基准，通过 `StockChatLayoutMetrics` 对
关键间距、字体和控件尺寸做受限比例调整，并使用 `pageViewWidth` 计算 Drawer 与欢迎主视觉，
避免在不同屏幕上整体放大。欢迎 Logo 单独限制在约 `110–128` 逻辑单位，避免原先接近整屏宽。

## 欢迎 Logo

欢迎态使用 `assets/common/stockchat_logo.png`，通过
`ImageUri.commonAssets("stockchat_logo.png")` 加载，并按原图比例自适应宽度，避免
绑定到具体页面目录。

## Markdown

聊天回答的 `AnswerBlock.Markdown` 使用 `KuiklyMarkdown` 1.0.6，并保留
`fallbackText` 作为空源文本或组件不可用时的降级内容。行情卡片与 Markdown
块分别渲染，避免把结构化行情压成纯文本。

## 走势绘制

走势区域当前使用 Kuikly `Canvas` 绘制轻量折线。已评估
`KuiklyChartView`：该组件当前没有与本项目 OpenHarmony target 对应的发布变体，
为一个固定尺寸的演示走势图引入它会阻断跨端变体解析。因此保留 Canvas 作为
跨端降级实现，并对空数据和单点数据做保护；后续需要缩放、Tooltip 或多序列时，
可在补齐 OHOS 变体后替换为 `KuiklyChartView`。
