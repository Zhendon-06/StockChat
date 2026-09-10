# StockChat

StockChat 是一个基于 Kotlin Multiplatform 与 Kuikly 的 AI 股票问答应用。用户用自然语言提问，AI 回答里直接嵌入实时行情卡片，点进卡片进入个股详情页，看走势、看 AI 预测，再把选中的点位带回聊天继续追问。一套共享代码同时运行在 Android、iOS 和 OpenHarmony 上。

> 行情与 AI 结论均为演示信息，仅供参考，不构成投资建议。

## 核心体验

**问 AI，得到带行情的回答。** 输入"腾讯和宁德时代最近怎么样"，回答由 Markdown 文本、行情卡片和图片等内容块组成，卡片可点击。模型菜单里可选两种回答模式：「更快回答速度」让聊天模型携带会话上下文流式作答，同时另一条只看这一句话的标的识别请求返回结构化企业列表、经腾讯证券搜索确认后拉取实时行情，卡片一到就插到正文下方；「联网精准实时数据回答」则先联网识别标的并拉取行情，再把实时数据注入上下文让模型作答，正文可以引用真实价格。

**详情页与 AI 联动。** 详情页展示分时、五日、日 K、周 K、月 K，叠加 MA5/10/20，支持缩放、平移和点选。请求 AI 预测后，预测曲线接在真实走势之后绘制，点击任一节点会同步切换点位说明、预测依据和风险提醒，可一键把当前标的和节点带回聊天。预测只在模型请求成功并通过结构校验后才绘制，不会用本地伪造曲线代替。

**今日市场。** 聊天主页内置市场 Tab，展示主要指数、样本股与涨跌分布，点击任意标的进入详情页。

**会话产物。** 一次会话里提到的所有标的可以生成横向对比表格，也可以生成 Mermaid mindmap 脑图，两者都保存在本地可反复查看。

**语音与图片。** 支持图片提问、语音输入和回答朗读。平台能力不可用时给出明确降级提示。

**设置。** 多模型服务商配置、字体大小、聊天背景、表格样式、会话归档与分享记录。

## 页面与路由

页面统一在共享层用 Kuikly `@Page` 注册，跳转走 `RouterModule`。

| 页面 | 路由名 | 说明 |
| --- | --- | --- |
| 聊天主页 | `router` | 对话、今日市场 Tab、会话抽屉 |
| 个股详情 | `stock_detail` | 行情、K 线 / 分时、盘口、AI 预测 |
| 收藏卡片 | `favorite_cards` | 收藏的行情卡片列表 |
| 会话对比表 | `conversation_table_artifacts` / `conversation_table_artifact` | 会话标的横向对比 |
| 会话脑图 | `conversation_mind_map_artifacts` / `conversation_mind_map_artifact` | Mermaid mindmap 渲染 |
| 图片预览 | `stock_image_preview` | 全屏查看消息图片 |
| 设置 | `stock_settings` 及 `stock_settings_*` 子页 | 模型、字体、背景、表格样式、分享、归档 |

## 架构

```text
shared/src/commonMain/kotlin/com/guet/liang/stockchat
├── ui/          Kuikly 页面与组件，只负责布局、渲染状态、转发用户动作
├── controller/  页面编排层：注入数据源，处理加载 / 重试 / 错误映射 / 请求代次
├── data/        AI 服务、行情接口、语音服务、本地持久化、产物生成器
├── model/       跨端纯数据结构
└── base/        路由、日志、错误映射与 Kuikly 兼容工具

kuikly-chart/    跨端金融图表组件库：K 线、分时、双轴、均线、十字光标、手势
table-core/      跨端表格组件库：DSL 建表、编辑缓冲、Excel 适配
androidApp/      Android 容器与原生桥接（录音、图片选择、路由）
iosApp/          iOS 容器与原生桥接
ohosApp/         OpenHarmony 容器与原生桥接
```

依赖方向固定为 `ui → controller → data → model`，data 与 model 不依赖 UI，页面不直接创建数据源。每个 controller 都通过接口接收依赖，可以在 `commonTest` 用假实现单独测试。

聊天请求链路：

```text
用户问题 / 图片
      │
      ▼
ChatSendController ─► AliyunStockChatDataSource（按 AnswerMode 编排）
      ├─ 聊天分支：携带上下文（问 A + 答 B + 问 D），走响应缓存，流式输出 ─► 当前 Provider
      └─ 标的分支：只发送当前这条问 D，无上下文
            ├─ 结构化标的识别（百炼 qwen-plus；精准模式强制联网，快速模式不联网）
            ├─ 并发调用腾讯 smartbox 名称搜索；识别请求给出且腾讯候选包含的代码直接采用
            ├─ 其余候选交当前模型确认具体证券
            └─ TencentMarketDataService 并发拉取行情
      │
      │  更快回答速度：两条分支并行，卡片就绪即随流式片段展示
      │  联网精准实时数据回答：标的分支先跑，行情注入聊天提示词后再作答
      ▼
ParallelAnswerJoin 合并两条分支
      ▼
AnswerBlock 列表（Markdown / MarketQuote / ImageGallery）
      │
      └─ 卡片点击 ─► StockDetailController ─► 详情页
```

## 数据来源与降级

- **行情**：腾讯证券公开接口，覆盖沪深北、港股快照、日线、分时与盘口。名称搜索使用腾讯 `smartbox`。
- **AI**：默认阿里云百炼 DashScope，兼容 OpenAI 协议；应用内可添加 DeepSeek、Moonshot、智谱等服务商。标的识别在百炼下固定走 `qwen-plus`，仅「联网精准实时数据回答」模式强制联网；其他服务商用当前模型按已有知识识别，不声称联网，也不做本地名称词典推断。
- **语音**：Xiaomi MiMo 识别与合成。
- **降级原则**：网络行情不可用时，今日市场只对缺失项使用带明确标记的本地演示数据；AI 预测失败、Key 缺失或返回结构非法时只展示错误状态；所有行情与 AI 结论保留时间戳与风险提示。

## 环境要求

| 组件 | 版本 |
| --- | --- |
| JDK | 17 |
| Gradle | 8.7（使用仓库内 `./gradlew`） |
| Kotlin | 2.1.21 |
| Kuikly | 2.26.0-2.1.21 |
| KuiklyMarkdown | 1.0.6-2.1.21 |
| Android Gradle Plugin | 8.6.1 |
| Android SDK | compileSdk 34，minSdk 23 |
| iOS | Xcode + CocoaPods，部署目标 iOS 14.1 |
| OpenHarmony | DevEco Studio |

## API Key 配置

在仓库根目录创建未提交的 `local.properties`：

```properties
QWEN_API_KEY=你的百炼_API_Key
MIMO_VOICE_API_KEY=你的_MiMo_API_Key
```

也可用同名环境变量覆盖。`QWEN_API_KEY` 用于文本 / 视觉问答、标的识别与 AI 预测，`MIMO_VOICE_API_KEY` 仅用于语音。三端 Debug 构建都会读取这份配置并生成到构建产物内，Release 构建清空本地密钥。Key 会进入 Debug 产物，因此这种方式只适合本地调试，正式环境应使用服务端代理或短期凭证。应用内的模型配置页可以在运行时另行添加服务商与 Key。

## 构建与运行

### Android

```bash
./gradlew :androidApp:assembleDebug
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

### iOS

```bash
cd iosApp
pod install
open iosApp.xcworkspace
```

在 Xcode 中选择 `iosApp` scheme 运行。iOS 宿主的桥接回归脚本见 `iosApp/tests/README.md`。

### OpenHarmony

```bash
./ohosApp/runOhosApp.sh
```

脚本会安装依赖、构建 HAP 并尝试安装到已连接设备；首次使用需在 DevEco Studio 完成签名配置。若直接用 DevEco Studio 构建，把 `ohosApp/local.properties.example` 复制为 `ohosApp/local.properties`。

## 测试与代码质量

```bash
# 三个共享模块的单元测试
./gradlew :shared:testDebugUnitTest :kuikly-chart:testDebugUnitTest :table-core:testDebugUnitTest

# 静态检查，配置位于 config/detekt/detekt.yml
./gradlew detekt

# 结构指标：函数长度、文件长度、KDoc 覆盖、分层依赖
bash tools/quality_metrics.sh
```

当前状态：

| 项目 | 结果 |
| --- | --- |
| 单元测试 | shared 176、kuikly-chart 61、table-core 31，全部通过 |
| detekt | 三模块 0 告警 |
| KDoc | 共享层与图表库顶层类型 100% 覆盖 |
| UI 对 data 层的直接依赖 | 仅两处装配入口 |

controller 层测试使用 `commonTest` 下的假 repository，不依赖 Kuikly 运行时。

## 组件库

`kuikly-chart` 和 `table-core` 是从本项目孵化的独立组件库，都只依赖 Kuikly `core`，可直接复制到其他 Kuikly 工程使用。

- **kuikly-chart**：DSL 声明式建图，支持折线 / 柱状 / 饼图，以及面向金融场景的 K 线、分时、成交量副图、双轴对照、均线叠加、十字光标与缩放平移手势。渲染基于 Kuikly Canvas，保证 OpenHarmony 兼容。
- **table-core**：DSL 建表、单元格编辑缓冲、列宽测量，并提供 Excel 文件适配。

## 更多文档

- [`docs/StockChatComponents.md`](docs/StockChatComponents.md)：AI 服务配置、行情链路、Markdown 选择、会话产物、走势绘制的实现细节。
- [`docs/StockMarketDetail.md`](docs/StockMarketDetail.md)：个股详情页的展示规则与数据边界。
