# StockChat

StockChat 是一个基于 Kotlin Multiplatform 与 Kuikly 构建的 AI 股票问答 Demo。它把自然语言问答、行情数据和证券详情串成一条跨端体验，用于验证“聊天入口 → 富内容回答 → 行情详情承接”的完整链路。

> 行情与 AI 结论均为演示信息，仅供参考，不构成投资建议。

## 功能概览

- **AI 股票聊天**：支持欢迎/空状态、文本提问、历史会话、流式生成、失败重试和重复提交保护。
- **富内容回答**：回答由可扩展的 `AnswerBlock` 组成，当前支持 KuiklyMarkdown、结构化行情卡片和图片内容；行情卡片展示名称、代码、现价、涨跌、更新时间，并可点击进入详情页。
- **行情与大盘**：可识别股票、指数、六位代码及交易所代码；行情请求使用腾讯证券接口，并提供今日市场概览、板块观察和降级提示。
- **股票详情页**：展示核心行情、历史走势、摘要和 AI 解读；走势支持缩放与横向浏览，AI 预测仅在真实模型请求成功并通过校验后绘制。
- **语音与图片**：支持图片提问、MiMo 语音输入和回答朗读；平台能力不可用时保留明确的降级提示。
- **会话工具**：支持会话表格对比、脑图产物、会话归档/分享，以及模型、字体、背景和表格样式设置。

## 页面与路由

页面统一在共享层使用 Kuikly `@Page` 注册，并通过 `RouterModule` 跳转：

| 页面 | 路由 | 用途 |
| --- | --- | --- |
| 聊天主页 | `router` | AI 对话、行情卡片和会话管理 |
| 股票详情 | `stock_detail` | 行情、走势图、摘要和 AI 预测 |
| 今日市场 | 聊天主页内的市场 Tab | 指数、样本股和板块概览 |
| 会话表格 | `conversation_table_artifacts` / `conversation_table_artifact` | 汇总当前会话中识别到的证券并横向比较 |
| 会话脑图 | `conversation_mind_map_artifacts` / `conversation_mind_map_artifact` | 查看会话生成的结构化脑图 |
| 设置 | `stock_settings*` | 模型、字体、背景、表格样式和会话管理 |

## 技术结构

```text
shared/src/commonMain
├── model/   跨端消息、行情、预测和设置模型
├── data/    AI、行情、语音、会话持久化及产物数据源
└── ui/      Kuikly 聊天、详情、市场、表格、脑图和设置页面

androidApp/  Android 启动容器、原生桥接、录音/图片选择和路由适配
iosApp/      iOS Kuikly 容器与原生桥接
ohosApp/     OpenHarmony 容器与原生桥接
table-core/  跨端表格能力
static_server/  Web 静态资源本地服务
```

核心请求链路如下：

```text
用户问题/图片
      │
      ▼
StockChatPage ──► 意图识别与证券路由
      │                    │
      │                    ├─ 行情问题 ─► TencentMarketDataService
      │                    └─ 普通/分析问题 ─► DashScope AI
      ▼
AnswerBlock（Markdown / MarketQuote / ImageGallery）
      │
      └─ 行情卡片点击 ─► RouterModule ─► StockDetailPage
```

## 环境要求

- JDK 17（Android Gradle Plugin `8.6.1`）
- Gradle Wrapper `8.7`（优先使用仓库内的 `./gradlew`）
- Kotlin `2.1.21`
- Kuikly `2.26.0-2.1.21`
- Android Studio 与 Android SDK 34；Android 最低版本为 API 23
- iOS 开发需要 Xcode、CocoaPods，部署目标为 iOS 14.1+
- OpenHarmony 开发需要 DevEco Studio；可使用 `ohosApp/runOhosApp.sh`

## API Key 配置

本地 Android 调试可在项目根目录创建未提交的 `local.properties`，仅填写以下键：

```properties
QWEN_API_KEY=你的百炼_API_Key
MIMO_VOICE_API_KEY=你的_MiMo_API_Key
```

也可以使用同名环境变量覆盖本地配置：

```bash
export QWEN_API_KEY="你的百炼_API_Key"
export MIMO_VOICE_API_KEY="你的_MiMo_API_Key"
```

`QWEN_API_KEY` 用于 DashScope 文本/视觉问答、意图识别和 AI 预测；`MIMO_VOICE_API_KEY` 仅用于 MiMo 语音识别与合成。未配置千问 Key 时，项目会对部分入门问题使用本地教学模板；行情请求仍可独立访问腾讯行情服务。API Key 会进入当前 Android 构建产物，因此该方式只适合本地 Demo，正式环境应改为服务端代理或短期凭证。

## 构建与测试

在项目根目录执行：

```bash
# 编译共享层 Android 目标
./gradlew :shared:compileDebugKotlinAndroid

# 运行共享层单元测试
./gradlew :shared:testDebugUnitTest

# 构建 Android Debug APK
./gradlew :androidApp:assembleDebug
```

生成的 APK 位于 `androidApp/build/outputs/apk/debug/androidApp-debug.apk`。连接设备后可使用：

```bash
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

### iOS

```bash
cd iosApp
pod install
open iosApp.xcworkspace
```

然后在 Xcode 中选择 `iosApp` scheme 和模拟器或真机运行。

### OpenHarmony

在已安装并配置 DevEco Studio 的环境中执行：

```bash
./ohosApp/runOhosApp.sh
```

脚本会安装依赖、构建 HAP，并尝试安装到已连接的模拟器或设备；首次使用仍需在 DevEco Studio 中完成签名配置。

## 数据与降级策略

- 纯行情问题优先走腾讯证券行情接口；名称搜索失败时提示输入完整名称、六位代码或带交易所的代码。
- 分析类问题会把带时间戳的行情快照注入 AI 上下文，避免模型脱离当前行情回答。
- 网络行情不可用时，今日市场页面可对缺失项使用明确标记的本地演示数据；不会把 Mock 价格伪装成实时行情。
- AI 预测失败、Key 缺失、历史数据不足或返回结构非法时，只展示错误/不可用状态，不绘制本地伪造预测曲线。
- Web 端的腾讯 `smartbox` 名称搜索存在 CORS 限制，正式部署应配置同域服务端代理；当前仓库未提供独立的 `h5App`/`miniApp` 工程目录。

## 开发约束

- 核心页面和共享业务逻辑放在 `shared/src/commonMain/kotlin`，使用 Kuikly 组件实现；平台工程只负责容器和必要桥接。
- Markdown 优先使用 KuiklyMarkdown；走势图当前使用 Kuikly Canvas，以保持 OpenHarmony 目标的跨端兼容。
- 行情、AI 结论和预测必须保留演示标识与风险提示，不得在源码中提交 API Key、Token 或其他凭据。
- 更详细的组件、接口和降级说明见 [`docs/StockChatComponents.md`](docs/StockChatComponents.md)。

