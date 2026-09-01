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

## Markdown 文本选择与复制

AI 回答完成后，Android、iOS 和鸿蒙端支持在 KuiklyMarkdown 内容上长按选词，并拖动左右手柄
调整选择范围；松手后可点击“复制”写入系统剪贴板。流式生成期间暂不启用选区，避免内容尺寸变化
导致选区被框架取消。

Kuikly 的跨节点文本选区目前不支持 Web/小程序，因此这些平台保留消息操作栏的“复制整条回答”
作为明确降级路径。当前每个 Markdown 块维护独立选区，不能跨两个结构化回答块连续拖选。

文本问答使用 `qwen-plus`，请求 DashScope OpenAI 兼容接口
`https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions`；意图识别使用同一 Key 和
`text-embedding-v4`。语音识别使用
`mimo-v2.5-asr`，回答朗读使用 `mimo-v2.5-tts` 与 `mimo_default` 音色，统一请求
`https://api.xiaomimimo.com/v1/chat/completions`，并按 MiMo 官方协议使用 `api-key` 请求头。
Android 录音使用 16 kHz 单声道 PCM16，并封装为 WAV；单次录音限制为
300 毫秒至 30 秒。TTS 默认使用流式 PCM16 输出，Android 收到音频分片后立即交给
`AudioTrack` 播放；不支持原生流式桥接的平台保留非流式 WAV 降级路径。API Key 会进入客户端
构建产物，仅适合本地调试，正式环境应改为
服务端代理或短期凭证。

未配置 `QWEN_API_KEY` 时，常见投资入门问题使用本地教学模板，其他普通问题会提示配置 AI；
可识别的证券报价和走势请求仍优先调用腾讯证券行情服务，因此不依赖 AI Key。腾讯行情不可用或
明确标的加载失败时会展示错误与重试入口；语义判为行情但名称搜索为空时会退回普通 AI 回答，
不会用 Mock 价格冒充实时行情。

## 行情数据与路由

纯文本请求先将问题与 `MARKET_DATA`、`INVESTMENT_EDUCATION`、`GENERAL` 三类原型一起提交到
DashScope Embeddings，并以余弦相似度和前两名分差判定。仅当 embedding 失败、相似度不足或分类
边界过近时，才调用 `qwen-plus` 返回结构化意图；两条远程链路都不可用时再退回本地规则。图片问题
不做文本意图识别，直接进入视觉模型。

只有 `MARKET_DATA` 会继续交给 `SecuritiesQueryRouter` 提取报价、走势、对比、分析和证券实体。
报价和走势直接返回腾讯行情与结构化卡片；分析类问题先获取同一份行情快照，再把带时间戳的数据
注入 AI 上下文。投资教育与通用问题直接交给 AI，因此不再要求问题必须包含公司名称或证券代码。

沪深北及港股行情快照和日线使用腾讯证券 `newfqkline` UTF-8 JSON 接口，详情页及分时问题补充调用
`minute/query`。名称查询使用腾讯证券 `smartbox` 搜索接口；该接口在 Web 环境存在 CORS 限制，
Web 端可直接输入六位代码或带交易所代码，正式部署应为名称搜索配置同域服务端代理。所有行情卡片
均标记腾讯行情时间，并保留“仅供参考，不构成投资建议”提示。

## 会话标的对比

聊天页的“会话标的对比”不是问答内容转抄，而是扫描当前会话中的用户消息、AI Markdown 和
`AnswerBlock.MarketQuote`，按交易所代码去重后汇总本次会话出现的全部可识别股票与指数。每个标的
保留“用户提及”“AI 生成”来源，并横向比较最新价、涨跌额/幅、今开、昨收、最高、最低、振幅、
换手率、市盈率、成交量/额、近期走势和行情时间；点击任意一行通过 Kuikly `RouterModule` 进入
`stock_detail` 详情页。

对比页使用 `KuiKlyTableView` 的 `KuiklyTable` 承载横向滚动表格。进入页面后会按标的代码逐项刷新
腾讯行情；部分请求失败时保留会话中已有的结构化行情并明确标记降级状态，全部失败时仍展示已识别
标的而不伪造实时数据。当前可确定识别带交易所代码、六位代码、内置常用名称、历史行情标签及所有
结构化行情卡片；仅有名称且无法确定交易所的标的，需要先通过行情名称搜索或由 AI 同时给出证券代码。
页面统一展示“行情与 AI 结论均为演示信息，仅供参考，不构成投资建议”的风险提示。

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
