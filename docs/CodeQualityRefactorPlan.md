# StockChat 代码质量改造计划

> 目标：让"代码质量与工程设计"维度（评分表权重 25%，考察点：分层设计 / 可维护性 / 扩展性 / 规范性）在自动化与人工评审下都能拿到接近满分。
> 本计划只改代码结构、注释和规范，**不改任何业务行为、不改 UI 视觉、不动宿主工程（androidApp / iosApp / ohosApp）**。

---

## 0. 执行前必读

### 0.1 硬约束（来自 `AGENTS.md`，必须遵守）

- 页面 UI 和业务逻辑只能用 Kuikly 组件，放在 `shared/src/commonMain/kotlin`。
- 不引入 Jetpack Compose / SwiftUI / ArkUI。
- 共享模型不得依赖平台类型。
- 不提交 API Key、生成物、`local.properties`。
- Kotlin 官方代码风格。

### 0.2 每完成一个阶段都必须通过的验证

```bash
./gradlew :shared:compileDebugKotlinAndroid :kuikly-chart:compileDebugKotlinAndroid --console=plain
./gradlew :shared:testDebugUnitTest :kuikly-chart:testDebugUnitTest --console=plain
./gradlew :androidApp:assembleDebug --console=plain
```

基线：编译 0 业务警告（仅允许 2 条 Kotlin Hierarchy Template 提示），`shared` 123 个测试 + `kuikly-chart` 61 个测试全部通过。改造后**测试数量只能增不能减**，任何测试失败都要修到通过再进入下一阶段。

### 0.3 提交策略

- 每个阶段一个或多个独立 commit，commit message 用 `refactor:` / `docs:` / `chore:` 前缀 + 中文描述，不出现 `1`、`feat` 这类无意义信息。
- 纯移动代码的 commit 和修改逻辑的 commit 分开，方便 review。
- 开始前先 `git status` 确认工作区干净；如果不干净，先停下来问，不要 stash 别人的改动。

### 0.4 自检脚本（第一步先创建）

新建 `tools/quality_metrics.sh`，内容如下，后续每个阶段结束时运行一次并把输出贴进 commit message 或 PR 描述：

```bash
#!/usr/bin/env bash
# 代码质量自检：函数长度、文件长度、KDoc 覆盖、硬编码颜色、危险模式
set -euo pipefail
cd "$(dirname "$0")/.."
SRC_LIST=$(mktemp)
find shared/src/commonMain/kotlin kuikly-chart/src/commonMain -name '*.kt' > "$SRC_LIST"

echo "== 函数 >= 100 行 =="
while read -r f; do
  awk -v F="$f" '
    /^[[:space:]]*(private |internal |public |override |suspend |inline |protected )*fun / && depth==0 { start=NR; name=$0; sub(/^[[:space:]]*/,"",name); sub(/\(.*/,"",name) }
    { o=gsub(/\{/,"{"); c=gsub(/\}/,"}"); depth+=o-c;
      if(start && depth==0 && (o||c)) { len=NR-start+1; if(len>=100) printf "%4d %s:%d %s\n", len, F, start, name; start=0 } }' "$f"
done < "$SRC_LIST" | sort -rn

echo "== 文件 > 500 行 =="
xargs wc -l < "$SRC_LIST" | awk '$1>500 && $2!="total"' | sort -rn

echo "== 顶层类型 KDoc 覆盖 =="
for dir in shared/src/commonMain/kotlin kuikly-chart/src/commonMain; do
  awk -v D="$dir" 'FNR==1{prev=""}
    /^(internal |public )?(sealed |data |abstract |open |enum |value )*(class|object|interface) / { total++; if (prev ~ /\*\//) doc++ }
    { if ($0 !~ /^[[:space:]]*$/) prev=$0 }
    END{ printf "%s: %d/%d\n", D, doc, total }' $(find "$dir" -name '*.kt')
done

echo "== ui 目录硬编码颜色（Theme 文件之外） =="
grep -rnoE '0x[Ff]{2}[0-9A-Fa-f]{6}' shared/src/commonMain/kotlin/com/guet/liang/stockchat/ui \
  | grep -vE 'StockChatTheme\.kt|SettingsTheme\.kt' | wc -l

echo "== 危险模式 =="
echo "!!            : $(xargs cat < "$SRC_LIST" | grep -c '!!' || true)"
echo "catch Throwable: $(xargs grep -nE 'catch \(\w+: (Throwable|Exception)\)' < "$SRC_LIST" | wc -l)"
echo "println       : $(xargs grep -n 'println(' < "$SRC_LIST" | wc -l)"
echo "TODO/FIXME    : $(xargs grep -nE 'TODO|FIXME|HACK' < "$SRC_LIST" | wc -l)"

echo "== ui 直接 import data 层的文件数 =="
grep -rl 'import com.guet.liang.stockchat.data' shared/src/commonMain/kotlin/com/guet/liang/stockchat/ui | wc -l
rm -f "$SRC_LIST"
```

---

## 1. 当前基线（2026-09-09 实测）

| 指标 | 当前值 | 目标值 |
| --- | --- | --- |
| 函数 ≥ 100 行 | 15 个，最长 265 行 | 0 个 ≥ 100 行；UI DSL 函数允许 ≤ 80 行 |
| 文件 > 500 行 | 11 个，最大 943 行 | ≤ 3 个，且单文件 ≤ 600 行 |
| shared 顶层类型 KDoc | 18 / 180 | ≥ 150 / 180，data 与 model 包 100% |
| kuikly-chart 顶层类型 KDoc | 18 / 58 | 58 / 58（这是对外组件库） |
| ui 硬编码颜色（Theme 外） | 81 处 | 0 处 |
| `!!` | 8 | 0 |
| `catch (_: Throwable)` 吞异常 | 10 | 0（统一走错误映射函数） |
| `println` | 2 | 0（统一走 `StockChatLog`） |
| ui 文件直接 import data | 28 个 | ≤ 4 个（仅 Page 入口通过 controller 注入） |
| 静态检查 | 无 | detekt 接入且 0 告警 |
| 单元测试 | 184 | ≥ 200 |

---

## 2. 分阶段任务

按顺序执行。每个阶段完成后运行 0.2 的验证和 0.4 的自检。

### 阶段 A：规范基础设施（半天）

**A1. 接入 detekt**
- 在 `gradle/libs.versions.toml` 增加 detekt 插件版本（选与 Kotlin 2.1.21 兼容的最新稳定版）。
- 根 `build.gradle.kts` 应用插件，作用于 `shared`、`kuikly-chart`、`table-core` 三个模块。
- 新建 `config/detekt/detekt.yml`，基于默认配置放开：`LongMethod` 阈值 80，`LongParameterList` 阈值 8（Kuikly DSL 参数多），`MaxLineLength` 140，`TooManyFunctions` 对 `*Page.kt` 排除。
- 先跑一次 `./gradlew detekt`，把当前告警数记录到本阶段 commit message 里；后续阶段逐步清零。
- 验收：`./gradlew detekt` 可运行；本阶段不要求 0 告警。

**A2. 新建 `.editorconfig`**
- Kotlin 4 空格缩进、UTF-8、LF、行尾无空白、文件末尾换行。

**A3. 统一日志入口**
- 新建 `shared/src/commonMain/kotlin/com/guet/liang/stockchat/base/StockChatLog.kt`，提供 `internal object StockChatLog { fun d(tag, msg); fun w(tag, msg, throwable?) }`，内部用 Kuikly `KLog`（若可用）否则 `println`，但全项目只允许这一处 `println`。
- 当前仅 `data/StockPredictionLog.kt:13,18` 两处 `println`；把该文件合并进 `StockChatLog` 或让它委托 `StockChatLog`。
- 验收：自检脚本 `println` 计数 ≤ 1（仅 `StockChatLog` 内部）。

### 阶段 B：消灭危险模式（半天）

**B1. 去掉 8 处 `!!`**
- `data/StockMarketDetailDataSource.kt:93,165`：解析 K 线 / 资金流时先做 `values.all { it != null }` 守卫后用局部非空变量，或改成 `requireNotNull` 并附带说明字段名的消息。
- `base/BasePager.kt:41`：`nightModel` 改成 `lateinit` 或带默认值的 `by lazy`。
- `kuikly-chart/.../ChartRenderer.kt:146`：`holeColor` 在 spec 构建阶段给默认值，渲染阶段不再判空。
- `kuikly-chart/.../FinancialChartRenderer.kt:49,50,99,100`：把 `previousClose` 提取为 `val prevClose = s.previousClose ?: return/默认分支`，一次解包多处使用。
- 验收：`!!` 计数 0，`kuikly-chart` 61 个测试仍通过。

**B2. 统一异常到错误状态的映射**
- 新建 `shared/.../base/ErrorMessages.kt`：`internal fun Throwable.toUserMessage(fallback: String): String`，区分网络超时、JSON 解析、Key 缺失三类，其余用 fallback。
- 10 处宽泛 catch（`ui/ConversationMindMapArtifactPage.kt:566`、`ui/StockChatNavigation.kt:76,95`、`ui/ConversationTableArtifactsPage.kt:408`、`ui/ConversationTableArtifactPage.kt:130`、`ui/ConversationMindMapArtifactsPage.kt:408`、`ui/StockDetailPredictionRequest.kt:177`、`ui/StockChatModelSelection.kt:259`、`ui/settings/ModelConfigurationCatalog.kt:104`、`data/StockPredictionService.kt:152`）全部改为 `catch (throwable: Throwable) { StockChatLog.w(TAG, "...", throwable); state = Error(throwable.toUserMessage("...")) }`。
- 用户可见文案保持原样，不改变行为。
- 验收：`catch (_: Throwable)` 计数 0；为 `toUserMessage` 补 3 个单元测试。

### 阶段 C：颜色与样式收口（半天）

**C1. 主题外零硬编码颜色**
- 目标文件：`ui/settings/BackgroundSettingsPage.kt`（26 处）、`ui/SelectableMarkdownContent.kt`（15 处）、`ui/ConversationStockComparisonView.kt`（13 处）、`ui/settings/TableStyleSettingsPage.kt`（8 处）及其余零散文件。
- 做法：把语义颜色移到 `ui/StockChatTheme.kt` 的 `StockChatTheme` 对象，命名按用途（`comparisonHeaderBackground`、`markdownSelectionHighlight`），不按颜色值命名。设置页专属的色板放到已有的 `ui/settings/SettingsTheme.kt`。背景设置页里的"用户可选背景色预设列表"是数据不是样式，允许保留在一个 `BackgroundPresets` 常量对象里，但不能散在函数体内。
- 验收：自检脚本"ui 硬编码颜色（Theme 外）"为 0。

**C2. 抽公共样式扩展**
- `border(Border(1f, BorderStyle.SOLID, StockChatTheme.border))` 重复 27 次：在 `StockChatTheme.kt` 旁新建 `ui/StockChatStyles.kt`，提供 `internal fun Attr.themedBorder()`、`internal fun Attr.cardSurface()` 等扩展，替换所有重复。
- `width = (ctx.pagerData.pageViewWidth - 32f.settingsDp()).coerceAtLeast(1f)` 重复 9 次：在 `ui/StockChatLayoutMetrics.kt` 增加 `contentWidth(ctx)` 之类的函数。
- 验收：上述两种重复各 ≤ 1 处定义。

### 阶段 D：路由收口（半天）

**D1. 统一页面跳转**
- `acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(` 在 ui 层出现 21 次，且 `SettingsPage.openPage`、`FavoriteCardsPage.openStockDetail`、`ConversationTableArtifactPage.openStockDetail` 各自实现了一份。
- 在已有的 `base/PagerRouting.kt` 里补齐：`Pager.openStockDetail(quote)`、`Pager.openSettings(subPage)`、`Pager.openArtifact(kind, id)`，参数打包逻辑只在这里出现一次，路由名常量继续放 `ui/StockChatRoutes.kt`。
- 替换所有调用点，删除三处私有实现。
- 验收：ui 目录下 `openPage(` 直接调用 0 处（全部经 `PagerRouting`）；`StockChatBackStackTest` 等路由相关测试通过；为 `PagerRouting` 参数打包补 2 个测试。

### 阶段 E：页面与数据解耦（1.5 天，最重要）

评分表要求"页面 / 组件 / 数据 / 状态四层分离"。当前 28 个 ui 文件直接 import data 层，业务编排写在 Page 的扩展函数里。目标是引入一层 controller，页面只持有 UiState 并把用户动作转发给 controller。

**E1. 约定**
- 新建包 `shared/src/commonMain/kotlin/com/guet/liang/stockchat/controller/`。
- 每个 controller 是普通 Kotlin 类，构造函数注入所需 data 层接口，不依赖 Kuikly 的 `Pager`，这样可以在 commonTest 直接测。
- controller 通过回调或 `observable` 状态对象向页面暴露 UiState；页面不再直接 new 任何 `*DataSource` / `*Repository` / `*Store`。
- 保留现有 `*UiState` sealed class，把它们移到 `controller` 包或新建 `state` 包（二选一，全项目统一）。

**E2. 先做详情页（作为模板）**
- 把 `ui/StockDetailPredictionRequest.kt` 的 `requestPrediction`（168 行）和 `ui/StockDetailPage.kt` 中的数据加载、预测调用、日志逻辑搬到 `controller/StockDetailController.kt`。
- 页面保留：布局、事件绑定、把 controller 状态渲染出来。
- 为 controller 写测试：加载成功 / 空数据 / 网络失败 / 预测结构非法 四条路径，用假的 `StockMarketDetailDataSource` 和 `StockPredictionService`。
- 验收：`ui/StockDetail*.kt` 不再 import `data.*`；新增 ≥ 4 个测试。

**E3. 再做聊天主页**
- `ui/StockChatMessaging.kt`、`ui/StockChatModelSelection.kt`（`fetchDrawerModels` 136 行）、`ui/StockChatSessions.kt` 中的发送、重试、模型拉取、会话持久化编排搬到 `controller/StockChatController.kt`；如果单个类超过 400 行，按 `ChatSendController` / `ModelSelectionController` / `SessionController` 拆分。
- `StockChatSettingsStore`（10 处 ui 引用）和 `ChatHistoryDatabase`（7 处）只允许在 controller 和 `StockChatPage` 的依赖装配处出现。
- 验收：ui 目录直接 import data 的文件数 ≤ 4；`StockChatHomeFlowTest` 通过；新增 ≥ 6 个 controller 测试。

**E4. 产物页与设置页**
- `ConversationTableArtifact*Page.kt`、`ConversationMindMapArtifact*Page.kt`、`FavoriteCardsPage.kt`、`settings/*Page.kt` 按同样模式各抽一个轻量 controller；这些页面逻辑简单，每个 controller 预计 < 100 行。
- 验收：同 E3 的 import 指标。

### 阶段 F：拆长函数与大文件（1 天）

基于阶段 E 之后的代码再做，避免拆两遍。

**F1. 必拆函数清单**（当前 ≥ 100 行，全部拆到 ≤ 80 行）

| 行数 | 位置 | 拆法建议 |
| --- | --- | --- |
| 265 | `ui/settings/ModelConfigurationProviderEditor.kt` `ProviderEditor` | 按表单分区拆成 `ProviderBasicFields` / `ProviderKeyField` / `ProviderModelList` / `ProviderActions` |
| 204 | `ui/StockDetailPredictionSection.kt` `PredictionStatusCard` | 按 `PredictionUiState` 的每个分支拆一个子函数 |
| 187 | `ui/StockChatDrawer.kt` `DrawerLayer` | 拆 `DrawerHeader` / `DrawerSessionList` / `DrawerFooter` |
| 180 | `ui/StockChatMenus.kt` `ConversationMenuOverlay` | 菜单项声明改为数据列表 + 一个渲染函数 |
| 171 | `ui/ConversationStockComparisonView.kt` `ConversationStockComparisonTable` | 拆表头 / 行 / 单元格 |
| 170 | `ui/StockChatWelcome.kt` `WelcomeContent` | 拆 Logo 区 / 推荐问题区 / 免责声明区 |
| 168 | `ui/StockDetailPredictionRequest.kt` `requestPrediction` | 阶段 E 已搬到 controller，再按"构建请求 / 调用 / 解析 / 落状态"拆 |
| 163 | `ui/StockChatComposer.kt` `ComposerToolbar` | 每个工具按钮一个函数 |
| 159 | `ui/StockDetailInsightCard.kt` `LinkedInsightCard` | 拆标题 / 依据列表 / 风险提示 |
| 146 | `ui/StockChatComposer.kt` `ComposerTextInput` | 拆输入框属性配置与事件绑定 |
| 136 | `ui/StockChatModelSelection.kt` `fetchDrawerModels` | 阶段 E 已搬走 |
| 125 | `ui/StockChatHomeLayers.kt` `ChatLayer` | 拆消息列表层 / 输入层 / 浮层 |
| 124 / 121 | `ui/TodayMarketSections.kt` 两个 Stocks 列表 | 两者结构相似，抽一个 `MarketStockList(title, quotes, onClick)` 复用 |
| 122 | `ui/settings/ModelConfigurationModelList.kt` `ModelCard` | 拆卡片头 / 能力标签 / 操作按钮 |

**F2. 必拆文件清单**（> 500 行）

| 行数 | 文件 | 拆法 |
| --- | --- | --- |
| 943 | `ui/StockChatComposer.kt` | 拆 `StockChatComposerInput.kt` / `StockChatComposerToolbar.kt` / `StockChatComposerAttachments.kt` |
| 768 | `kuikly-chart/.../internal/ChartRenderer.kt` | 按图表类型拆 `LineChartRenderer` / `BarChartRenderer` / `PieChartRenderer`，公共部分留在 `ChartRenderer` |
| 751 | `ui/settings/BackgroundSettingsPage.kt` | 预设数据、预览组件、页面三分 |
| 716 | `ui/settings/TableStyleSettingsPage.kt` | 同上 |
| 650 | `data/TencentMarketDataService.kt` | 拆 `TencentQuoteApi`（请求构造）/ `TencentMarketResponseParser`（已有测试文件对应）/ `TencentMarketDataService`（编排） |
| 597 | `ui/StockMarketPanel.kt` | 拆面板头 / 列表 / 状态视图 |
| 580 | `kuikly-chart/.../ChartView.kt` | 手势处理抽 `ChartGestureHandler.kt` |
| 579 | `ui/StockChatDrawer.kt` | 随 F1 拆分自然降到 500 以下 |
| 571 | `ui/ConversationMindMapArtifactPage.kt` | 阶段 E 抽 controller 后再看 |
| 568 | `data/SettingsPersistence.kt` | 按设置类别拆序列化器 |
| 542 | `data/ConversationStockComparisonGenerator.kt` | 拆"字段提取"与"表格组装" |

- 验收：自检脚本"函数 ≥ 100 行"为空；"文件 > 500 行"≤ 3 个且均 ≤ 600 行；`./gradlew detekt` 0 告警。

### 阶段 G：注释补齐（1 天）

**G1. 范围与标准**
- `data/`、`model/`、`controller/`、`base/` 包：每个顶层 class / object / interface / sealed class 及其公开函数写 KDoc。
- `kuikly-chart` 全部公开 API 写 KDoc，包括 `api/` 下 DSL 入口和 `finance/` 下的金融图表参数；这是"可孵化通用组件"的证据。
- `ui/` 包：每个文件顶部一段文件级说明（该文件承担哪个页面的哪一块），每个 `@Page` 类写 KDoc；内部小的 DSL 函数不强求。
- KDoc 写"为什么"和"边界"，不复述函数名。数据源类要写清依赖的外部接口、失败时的降级行为、返回值中哪些字段可能为空。
- 优先补的文件（当前无注释类型最多）：`data/StockMarketDetailDataSource.kt`（9 个）、`data/TencentMarketDataService.kt`（8 个）、`data/SecuritiesSearchResolver.kt`（5）、`data/IntentRecognition.kt`（5）、`data/SettingsPersistence.kt`（4）。
- 验收：自检脚本 KDoc 覆盖 shared ≥ 150/180，kuikly-chart = 58/58。

**G2. 文档同步**
- `README.md` 技术结构树补上 `kuikly-chart/`、`table-core/`、`controller/` 包，并在"项目亮点"里写明两个可复用组件库和四层分离。
- `docs/StockChatComponents.md` 增加"分层与依赖方向"一节：`ui → controller → data → model`，禁止反向依赖。
- 更新 `docs/StockChatComponents.md` 的"当前验证状态"为改造后的真实数据（测试数、detekt 状态）。

### 阶段 H：测试补强（半天）

- 目标总测试数 ≥ 200。阶段 B、D、E 已要求新增约 15 个。
- 补 `TencentMarketDataService` 的 URL 构造测试与异常响应测试。
- 补 `StockChatTheme` / `StockChatStyles` 无需测试。
- 验收：`./gradlew :shared:testDebugUnitTest :kuikly-chart:testDebugUnitTest :table-core:testDebugUnitTest` 全绿。

---

## 3. 禁止事项

- 不修改任何用户可见文案、颜色值、间距、动画时长；改造是纯结构性的。颜色搬进 Theme 时必须保持原十六进制值。
- 不删除现有测试，不用 `@Ignore` 跳过测试。
- 不引入 Compose、ViewModel（AndroidX）、Koin 等平台或 DI 框架；controller 用手写构造函数注入。
- 不改 `androidApp/`、`iosApp/`、`ohosApp/` 下的代码；如果 Kuikly 桥接接口需要调整，先停下来说明。
- 不在 `commonMain` 引入 JVM 专有 API（`java.*`）。
- 不用 `@Suppress` 压 detekt 告警来达标；确有必要时在 `detekt.yml` 里给出规则级排除并写原因。

## 4. 最终交付检查

全部阶段完成后，在仓库根目录依次执行并把输出附在最后一个 commit 或 PR 描述里：

```bash
bash tools/quality_metrics.sh
./gradlew detekt
./gradlew :shared:testDebugUnitTest :kuikly-chart:testDebugUnitTest :table-core:testDebugUnitTest
./gradlew :androidApp:assembleDebug
```

四条全部通过、自检指标全部达到第 1 节的目标值，即视为完成。
