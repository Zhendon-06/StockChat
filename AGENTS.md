# StockChat 开发约束

## 项目目标

本项目是“AI 股票问答应用 Demo”。所有后续设计、实现和重构都必须围绕以下验收范围，不得退化为普通聊天 Demo：

1. AI 聊天主页面：支持输入问题、发送消息和展示完整会话记录。
2. AI 回答富内容：除 Markdown 文本外，至少支持股票、指数或行情相关的结构化内容，例如行情卡片、指标摘要、走势小图或可交互推荐项。
3. 股票或指数详情页：至少提供一个可从聊天结果点击进入的承接页，展示基础行情、走势区域、摘要信息或 AI 解读。

## 技术栈硬约束

- 页面 UI 和可复用业务逻辑必须使用 Kuikly 组件实现，优先放在 `shared/src/commonMain/kotlin`，确保 Android、iOS、Web/小程序及鸿蒙侧可复用。
- 不得用 Jetpack Compose、Android XML、SwiftUI 或 ArkUI 重写核心业务页面；平台工程仅负责启动、渲染容器、路由和必要的原生适配。
- Kuikly 页面使用 `@Page` 注册，页面跳转使用 Kuikly `RouterModule`；聊天结果中的股票或指数入口必须能跳转到详情页并传递标的参数。
- Markdown 内容优先使用 KuiklyMarkdown 组件，不自行实现一套 Markdown 解析器；若组件存在平台兼容限制，需保留明确的降级渲染路径。
- 当页面需要表格、图表、加载态或日历交互时，优先评估并复用对应的 Kuikly 组件：`KuiKlyTableView`、`KuiklyChartView`、`KuiklyLoadView`、`KuiklyCalendarView`；只有在组件能力、跨端兼容性或视觉需求不满足时才补充自定义实现。
- 依赖版本需与当前 Kuikly、Kotlin 版本兼容；新增依赖前先核对仓库可用性、跨端支持和构建影响。

## 产品与交互约束

- 聊天页至少覆盖欢迎或空状态、用户消息、AI 消息、生成中、失败与重试、输入为空等状态。
- 消息列表在新增消息后应保持合理滚动位置；发送过程中避免重复提交，并处理键盘遮挡和底部安全区。
- AI 消息采用可扩展的数据模型，不把所有返回内容压成单一字符串；至少区分 Markdown 块与一种结构化行情块，便于后续扩展图表、列表和操作项。
- 行情卡片应展示标的名称、代码、现价、涨跌额或涨跌幅以及时间或状态；涨跌颜色与符号需同时表达结果，避免只依赖颜色。
- 详情页必须有明确返回路径，展示标的身份、核心价格信息、走势区域和摘要或 AI 解读，并对加载、空数据和错误状态提供反馈。
- Demo 可使用本地 mock 数据，但数据访问应与 UI 分离，保留替换真实 AI 或行情服务的接口；不得在源码中写入 API Key、Token 或其他凭据。
- 所有行情与 AI 结论需标注为演示信息，并提供“仅供参考，不构成投资建议”的风险提示。

## 代码组织与质量

- 优先新增聚焦的页面、组件、模型和数据源文件，避免继续把业务堆叠进示例 `RouterPage.kt`。
- 共享模型不得依赖 Android、iOS 或其他平台专有类型；平台差异通过已有适配层或最小化桥接处理。
- 保持 Kotlin 官方代码风格；命名应表达业务含义，避免无意义缩写、散落硬编码和超大函数。
- 不修改与需求无关的宿主或构建配置；不要提交生成物、Pods、Gradle 缓存、本地配置或密钥。
- 新增功能后至少执行相关 shared 编译与 Android Debug 构建；若环境导致无法验证，交付时明确说明未验证项和原因。

## 参考资料

- Kuikly 快速开始：https://kuikly.tds.qq.com/QuickStart/env-setup.html
- KuiklyMarkdown：https://github.com/Kuikly-contrib/KuiklyMarkdown
- `KuiKlyTableView`（表格）：https://github.com/Zhendon-06/KuiKlyTableView
- `KuiklyChartView`（图表）：https://github.com/Zhendon-06/KuiklyChartView
- `KuiklyLoadView`（加载）：https://github.com/Zhendon-06/KuiklyLoadView
- `KuiklyCalendarView`（日历）：https://github.com/Zhendon-06/KuiklyCalendarView
