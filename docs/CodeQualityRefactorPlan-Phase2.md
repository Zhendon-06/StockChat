# StockChat 代码质量改造计划 · 第二阶段

> 前置：`docs/CodeQualityRefactorPlan.md` 的阶段 A、B1、C、G 已完成（KDoc 186/186 与 58/58，硬编码颜色 0，`!!` 0）。
> 本文件只覆盖 2026-09-09 复检后仍未完成的四项工作，并修正第一阶段计划中两处与实际不符的地方。
> 第一阶段的 0.1 硬约束、0.2 验证命令、0.3 提交策略、第 3 节禁止事项全部继续有效。

---

## 0. 复检发现的两个问题（先修，再往下做）

### 0.1 `StockDetailController` 是死代码

`controller/StockDetailController.kt` 及其 4 个测试已存在，但**全仓没有任何地方引用它**。`ui/StockDetailPage.kt:48-54` 仍直接 `lateinit var marketDataService: TencentMarketDataService` 并在 `created()` 里构造；`ui/StockDetailPredictionRequest.kt:39,138` 仍直接读 `StockChatSettingsStore` 和构造 `StockPredictionService`。

这意味着详情页解耦实际上没有发生。必须先把这个模板接上，再照它做其他页面，否则其他页面也会复制出一堆没人用的 controller。

### 0.2 detekt 存量不是 341，是 2664

341 只是 `kuikly-chart` 一个模块的数字。全仓 `./gradlew detekt --rerun-tasks` 实测：

| 模块 | 条数 |
| --- | --- |
| shared | 2284 |
| kuikly-chart | 341 |
| table-core | 46 |

按规则：

| 规则 | 条数 | 处理方式 |
| --- | --- | --- |
| MagicNumber | 2127 | **改配置**，见 2.1 |
| FunctionNaming | 182 | **改配置**，见 2.1 |
| MaxLineLength | 82 | 改代码，机械 |
| ReturnCount | 66 | 改代码 |
| LongMethod | 49 | 与拆函数任务合并 |
| CyclomaticComplexMethod | 45 | 与拆函数任务合并 |
| TooManyFunctions | 33 | 部分改配置，部分拆文件 |
| ComplexCondition | 18 | 改代码 |
| LongParameterList | 12 | 改代码 |
| WildcardImport | 11 | 改代码，机械 |
| TooGenericExceptionCaught | 10 | 改配置，见 2.1 |
| 其余 9 种 | 29 | 逐条处理 |

前两项占 87%，是 Kuikly DSL 的固有写法，硬改会让代码更差。真正要动代码的约 355 条。

---

## 1. 修复详情页 controller 接线（0.5 天）

目标：`ui/StockDetail*.kt` 全部不再 import `data.*`（`TencentMarketSnapshot` 等纯数据类型见第 3 节的搬迁）。

1. 在 `data/` 下新增两个适配器，实现 controller 里已定义的接口：
   - `TencentStockDetailMarketRepository(service: TencentMarketDataService) : StockDetailMarketRepository`
   - `StockPredictionRepositoryAdapter(settings: SettingsRepository, networkModule: NetworkModule) : StockDetailPredictionRepository`，把 `ui/StockDetailPredictionRequest.kt` 里读取模型配置、构造 `StockPredictionService`、调用、解析的 169 行逻辑整体搬进来。
2. `StockDetailPage.created()` 只做依赖装配：构造两个适配器，构造 `StockDetailController`，把 `onMarketStateChanged` / `onPredictionStateChanged` 回调绑定到页面现有的 `observable` 状态字段上。
3. 把 `ui/StockDetailUiState.kt` 的 `DetailUiState` / `PredictionUiState` 删除，页面直接使用 `StockDetailControllerState` / `StockDetailPredictionControllerState`；或者反过来删 controller 里那套、保留 ui 那套并移到 controller 包。二选一，不允许两套并存。
4. `loadDetail()` 改为 `controller.load(symbol)`，`requestPrediction()` 改为 `controller.requestPrediction(symbol)`；删除 `ui/StockDetailPredictionRequest.kt`。
5. 收藏与分享：`FavoriteCardsStore` / `StockChatSettingsStore.repository.recordSharedChat` 的调用移入 controller 的 `toggleFavorite()` / `shareSnapshot()`，通过构造函数注入接口。
6. 验收：
   - `grep -rn 'StockDetailController' shared/src/commonMain/kotlin/com/guet/liang/stockchat/ui` 至少命中 `StockDetailPage.kt`。
   - `grep -l 'import com.guet.liang.stockchat.data' shared/src/commonMain/kotlin/com/guet/liang/stockchat/ui/StockDetail*.kt` 为空。
   - Android Debug 安装后手动走一遍：进入详情页、切周期、点 AI 预测、收藏、分享，行为与改造前一致。
   - 为两个适配器各补 1 个测试，`StockDetailControllerTest` 增加收藏与分享路径 2 个测试。

---

## 2. detekt 清零（1 天，先配置后代码）

### 2.1 配置调整（半小时，先做）

修改 `config/detekt/detekt.yml`，每条都要在 yml 里写一行注释说明原因：

```yaml
style:
  MagicNumber:
    # Kuikly DSL 中 dp / 字号 / 动画时长 / 颜色透明度是布局参数，不是业务魔数
    excludes: ['**/ui/**', '**/kuiklychart/**', '**/kuiklytableview/**']
    ignorePropertyDeclaration: true
    ignoreCompanionObjectPropertyDeclaration: true
    ignoreEnums: true
    ignoreRanges: true
  MaxLineLength:
    maxLineLength: 140
  ReturnCount:
    max: 3
    excludeGuardClauses: true
  WildcardImport:
    excludeImports: ['kotlin.test.*']
naming:
  FunctionNaming:
    # Kuikly 组件函数与 Compose 一样使用大写开头命名
    ignoreAnnotated: ['Page']
    excludes: ['**/ui/**', '**/kuiklychart/**/*View.kt', '**/kuiklychart/**/*Chart.kt']
complexity:
  LongMethod:
    threshold: 80
  LongParameterList:
    functionThreshold: 8
    constructorThreshold: 8
    ignoreDefaultParameters: true
  TooManyFunctions:
    thresholdInFiles: 20
    thresholdInClasses: 15
    excludes: ['**/*Page.kt', '**/*Test.kt']
exceptions:
  TooGenericExceptionCaught:
    # 这 10 处已经统一经 Throwable.toUserMessage 映射并记录日志，见 base/ErrorMessages.kt
    active: false
```

不允许再放宽别的规则。改完后重跑 `./gradlew detekt --rerun-tasks`，把剩余条数记录到 commit message。预期剩余约 300 条。

### 2.2 机械修复（半天）

- **MaxLineLength 82 条**：`ui/StockMarketPanel.kt` 一个文件占 54 条，先处理它。其余按报告逐条折行。
- **WildcardImport 11 条**：展开为具名 import。
- **InvalidRange 3 条**：`kuikly-chart/.../internal/ChartGeometry.kt:205,214` 和 `FinancialEvidenceViewportTest.kt:21` 用 `1..0` 表示空区间，改为 `IntRange.EMPTY`。行为不变。
- **UnusedParameter 4 / UnusedPrivateMember 1 / SpreadOperator 1 / ForEachOnRange 1**：逐条处理。
- **MatchingDeclarationName 7 条**：文件名与唯一顶层类名不一致，重命名文件或拆出去。

### 2.3 与第 4 节合并处理

LongMethod、CyclomaticComplexMethod、ComplexCondition、ReturnCount、NestedBlockDepth、LongParameterList、LargeClass 这些规则的告警，在第 4 节拆函数时一起消掉，不单独处理。

### 2.4 验收

`./gradlew detekt` 输出 `BUILD SUCCESSFUL` 且报告 0 条。`config/detekt/detekt.yml` 中除 2.1 列出的规则外无其他改动。

---

## 3. 降低 ui 对 data 的依赖（1.5 天）

当前 28 个 ui 文件 import `data.*`。分三类处理，按顺序做，每做完一类跑一次 `tools/quality_metrics.sh`。

### 3.1 纯数据类型搬到 `model/`（半天，收益最大最安全）

以下类型是纯数据结构，只是碰巧定义在 data 包的服务文件里。把它们移到 `model/` 包对应文件，所有 import 随之更新，不改任何逻辑：

| 类型 | 当前位置 | 搬到 |
| --- | --- | --- |
| `TencentMarketSnapshot`、`MarketDataResult`、`HistoricalPointsResult` | `data/TencentMarketDataService.kt` | `model/MarketDataModels.kt`（新建） |
| `MarketPeriod` | `data/StockMarketDetailDataSource.kt` | `model/MarketDataModels.kt` |
| `ChatSessionSummary` | `data/ChatHistoryRepository.kt` | `model/StockChatModels.kt` |
| `ChartEvidenceResolution` | `data/ChartEvidenceResolver.kt` | `model/ChartEvidenceModels.kt`（已存在） |
| `MermaidMindMapNode` | `data/MermaidMindMapParser.kt` | `model/ConversationMindMapArtifactModels.kt`（已存在） |
| `ModelCatalogResult` | `data/ModelCatalogService.kt` | `model/SettingsModels.kt` |

做完后 `ui/StockDetailMarketSection.kt`、`ui/StockDetailUiState.kt`、`ui/ChartConclusionCard.kt`、`ui/StockChatDrawer.kt`、`ui/StockDetailInsightCard.kt`（还剩 `ChartEvidenceResolver`，见 3.3）这几个文件的 data import 自然消失。预期 28 → 23。

### 3.2 设置页：一个 `SettingsController` 覆盖 9 个文件（半天）

`ui/settings/` 下 9 个文件全部只依赖 `StockChatSettingsStore`（少数加 `ChatHistoryDatabase` / `ModelCatalogService`）。

1. 新建 `controller/SettingsController.kt`，构造函数接收 `SettingsRepository`、`ChatHistoryRepository`、`ModelCatalogService`（后两者可空）。暴露：`snapshot()`、`setThemeMode`、`setFontSize`、`setTableStyle`、`setChatBackground`、`saveProvider`、`deleteProvider`、`selectModel`、`fetchCatalog(providerId, callback)`、`archivedSessions()`、`sharedChats()`、`deleteSharedChat`。基本上是把 `SettingsRepository` 的接口再包一层并加上 catalog 拉取和错误映射。
2. 新建 `controller/ControllerFactory.kt`（或放在 `base/`），提供 `internal fun Pager.settingsController(): SettingsController`，内部读 `StockChatSettingsStore.repository`。这是**唯一允许**出现 `StockChatSettingsStore` 的 ui 侧入口，其余 ui 文件只拿 controller。
3. 9 个设置页替换调用。`ui/settings/ModelConfigurationCatalog.kt` 和 `ModelConfigurationDraft.kt` 里的业务逻辑（catalog 拉取、草稿校验）搬进 controller 或拆一个 `ModelConfigurationController`。
4. 同样处理 `ui/StockChatTheme.kt`（读主题快照）和 `ui/ConversationStockComparisonView.kt`（读表格样式），改为接收 `SettingsSnapshot` 参数或经 controller 读取。
5. 验收：`ui/settings/` 下 data import 为 0；`ui/StockChatTheme.kt`、`ui/ConversationStockComparisonView.kt` data import 为 0。预期 23 → 12。补 `SettingsControllerTest` ≥ 5 个测试。

### 3.3 聊天主页与产物页（1 天）

聊天主页当前的结构是 `StockChatPage` 持有 9 个 `lateinit` 数据对象（`StockChatPage.kt:139-147`），14 个扩展函数文件直接读它们。不需要一次到位，按以下顺序做，每步独立提交：

**3.3.1 `ChatSessionController`**
- 搬入 `ui/StockChatSessions.kt` 全部 9 个函数和 `ui/StockChatMessaging.kt` 的 `persistChatHistory`。
- 依赖：`ChatHistoryRepository`。
- 页面保留 `activeSessionId`、`recentSessions` 等 observable，由 controller 回调更新。

**3.3.2 `ChatSendController`**
- 搬入 `ui/StockChatMessaging.kt` 的 `sendMessage`、`completeAnswer`、`applyAnswer`、`retryMessage`、`regenerateMessage`、`conversationHistoryBefore`、`imagesBeforeAnswer`。
- 依赖：`StockChatDataSource`、`ChatSessionController`。
- `copyMessage` / `shareMessage` / `deleteMessage` 这类只碰页面状态和 bridge 的函数留在 ui。
- `shareMessage` 里的 `StockChatShareContentBuilder` 是纯函数，可以留，但改成从 `model` 或 `base` 包调用；如果它依赖 data 包内部类型，先把它移到 `base/`。

**3.3.3 `ModelSelectionController`**
- 搬入 `ui/StockChatModelSelection.kt` 的 `configureChatProvider`、`fetchDrawerModels`（137 行）、`retryDrawerModels`、`selectModel`。
- 依赖：`SettingsRepository`、`ModelCatalogService`、以及一个 `(AliyunApiConfig) -> StockChatDataSource` 工厂函数，这样 controller 不直接 import `AliyunStockChatDataSource`。

**3.3.4 `ArtifactController`**
- 搬入 `ui/StockChatNavigation.kt` 的 `createConversationStockComparison`、`createConversationMindMapArtifact`，以及 `ConversationTableArtifact*Page.kt`、`ConversationMindMapArtifact*Page.kt`、`FavoriteCardsPage.kt` 里的加载和生成逻辑。
- 依赖：`ChatHistoryRepository`、两个 ArtifactRepository、两个 Generator、`ConversationStockComparisonDataSource`、`FavoriteCardsStore`。
- 五个产物页都改为在 `created()` 里通过工厂拿 controller。

**3.3.5 `StockChatPage` 装配收口**
- `StockChatPage.created()` 变成纯装配：构造 `networkModule`、各 data 对象、各 controller，然后把 controller 挂在页面字段上。9 个 `lateinit` 数据对象字段改为 `private`，扩展函数只能通过 controller 访问。
- 语音相关 `MimoSpeech*Service` 与 `TodayMarketDataSource` 如果只在 `StockChatPage.kt` 内部使用，允许保留在 `StockChatPage.kt` 的装配代码里，不强求再包 controller。

**3.3.6 验收**
- `tools/quality_metrics.sh` 的"ui 直接 import data 层的文件数" ≤ 4。允许保留的 4 个：`ui/StockChatPage.kt`（装配）、`ui/StockDetailPage.kt`（装配）、`controller/ControllerFactory.kt` 所在处若放在 ui 下、以及一个语音装配文件。
- `grep -rn 'StockChatSettingsStore\|ChatHistoryDatabase' shared/src/commonMain/kotlin/com/guet/liang/stockchat/ui` 只命中装配文件。
- 每个 controller ≥ 3 个测试，用假的 repository 接口，不依赖 Kuikly `Pager`。
- `StockChatHomeFlowTest`、`StockChatBackStackTest` 继续通过。
- Android Debug 手动回归：发送消息、重试、切模型、切会话、生成表格、生成脑图、收藏、设置页每一项。

---

## 4. 拆长函数与大文件（1.5 天，在第 3 节之后做）

第 3 节完成后重新跑 `tools/quality_metrics.sh`，以新结果为准。以下是基于当前代码的清单，搬进 controller 的函数会自然从列表消失。

### 4.1 函数 ≥ 100 行（当前 25 个）

原则：Kuikly DSL 函数拆到 ≤ 80 行（与 detekt `LongMethod` 阈值一致）。拆法只有三种，选一种：
- **按视觉区块拆**：头部 / 列表 / 底部各一个 `private fun`。
- **按状态分支拆**：`when (state)` 的每个分支一个函数。
- **数据驱动**：重复的按钮或菜单项改成 `List<XxxItem>` + 一个渲染函数。

| 行数 | 位置 | 拆法 |
| --- | --- | --- |
| 265 | `ui/settings/ModelConfigurationProviderEditor.kt` `ProviderEditor` | 区块：基础字段 / Key 字段 / 模型列表 / 操作按钮 |
| 204 | `ui/StockDetailPredictionSection.kt` `PredictionStatusCard` | 状态分支 |
| 187 | `ui/StockChatDrawer.kt` `DrawerLayer` | 区块：头 / 会话列表 / 底 |
| 180 | `ui/StockChatMenus.kt` `ConversationMenuOverlay` | 数据驱动 |
| 171 | `ui/ConversationStockComparisonView.kt` `ConversationStockComparisonTable` | 区块：表头 / 行 / 单元格 |
| 170 | `ui/StockChatWelcome.kt` `WelcomeContent` | 区块 |
| 169 | `ui/StockDetailPredictionRequest.kt` `requestPrediction` | 第 1 节已搬走并删除 |
| 163 | `ui/StockChatComposer.kt` `ComposerToolbar` | 数据驱动 |
| 159 | `ui/StockDetailInsightCard.kt` `LinkedInsightCard` | 区块 |
| 146 | `ui/StockChatComposer.kt` `ComposerTextInput` | 属性配置与事件绑定分开 |
| 137 | `ui/StockChatModelSelection.kt` `fetchDrawerModels` | 3.3.3 已搬走 |
| 125 | `ui/StockChatHomeLayers.kt` `ChatLayer` | 区块 |
| 124 / 121 | `ui/TodayMarketSections.kt` 两个 Stocks 列表 | 合并为一个 `MarketStockList(title, quotes, onClick)` |
| 122 | `ui/settings/ModelConfigurationModelList.kt` `ModelCard` | 区块 |
| 114 | `ui/settings/ModelConfigurationDialogs.kt` `UnsavedChangesDialog` | 区块 |
| 113 | `ui/StockDetailPredictionSection.kt` `AiPredictionContent` | 状态分支 |
| 112 | `ui/StockChatDrawer.kt` `DrawerConversation` | 区块 |
| 112 | `ui/MarketQuoteCard.kt` `MarketQuoteCard` | 区块 |
| 109 | `ui/StockChatModelMenu.kt` `ModelMenuItem` | 区块 |
| 107 | `ui/StockChatDrawer.kt` `SessionRenameOverlay` | 区块 |
| 106 | `ui/StockChatComposer.kt` `ComposerDock` | 区块 |
| 102 | `ui/StockDetailHeaderAndStates.kt` `DetailHeader` | 区块 |
| 101 | `ui/StockChatMessageList.kt` `MessageList` | 区块 |
| 100 | `ui/ConversationTableArtifactContent.kt` `ComparisonContent` | 区块 |

### 4.2 文件 > 500 行（当前 11 个）

目标 ≤ 3 个且每个 ≤ 600 行。detekt `LargeClass` 命中的三个文件必须拆。

| 行数 | 文件 | 拆法 |
| --- | --- | --- |
| 943 | `ui/StockChatComposer.kt` | `StockChatComposerDock.kt` / `StockChatComposerInput.kt` / `StockChatComposerToolbar.kt` |
| 769 | `kuikly-chart/.../internal/ChartRenderer.kt`（LargeClass） | 按图表类型拆 `LineSeriesRenderer` / `BarSeriesRenderer` / `PieRenderer`，公共坐标与图例留在 `ChartRenderer` |
| 753 | `ui/settings/BackgroundSettingsPage.kt`（LargeClass） | `BackgroundPresets.kt`（数据）/ `BackgroundPreview.kt`（预览组件）/ 页面 |
| 718 | `ui/settings/TableStyleSettingsPage.kt`（LargeClass） | 同上模式 |
| 659 | `data/TencentMarketDataService.kt` | 3.1 搬走数据类型后，再拆 `TencentQuoteUrls.kt`（URL 构造）与 `TencentMarketResponseParser.kt`（解析，已有对应测试文件） |
| 597 | `ui/StockMarketPanel.kt` | 先修 54 条 MaxLineLength，再拆面板头 / 列表 / 状态视图 |
| 580 | `kuikly-chart/.../ChartView.kt` | 手势处理抽 `ChartGestureHandler.kt` |
| 579 | `ui/StockChatDrawer.kt` | 随 4.1 拆函数自然下降，不够再拆 `StockChatDrawerOverlays.kt` |
| 577 | `ui/ConversationMindMapArtifactPage.kt` | 3.3.4 搬走逻辑后再看 |
| 572 | `data/SettingsPersistence.kt` | 按设置类别拆序列化器 |
| 543 | `data/ConversationStockComparisonGenerator.kt` | 拆"字段提取"与"表格组装" |

### 4.3 验收

- `tools/quality_metrics.sh`："函数 ≥ 100 行"为空；"文件 > 500 行" ≤ 3 且均 ≤ 600。
- `./gradlew detekt` 0 条（LongMethod、CyclomaticComplexMethod、LargeClass 等在此阶段清零）。
- 全部测试通过，数量 ≥ 210（当前 184 加第 1、3 节新增）。

---

## 5. 执行顺序与提交切分

```
第 1 节  修详情页接线            1 个 commit：refactor: 详情页接入 StockDetailController
2.1      detekt 配置             1 个 commit：chore: 调整 detekt 规则适配 Kuikly DSL
2.2      detekt 机械修复          1 个 commit：style: 修复 detekt 折行/导入/空区间告警
3.1      数据类型搬 model         1 个 commit：refactor: 纯数据类型从 data 迁移到 model
3.2      SettingsController      1 个 commit
3.3.1-4  四个 controller         每个 1 个 commit
3.3.5    StockChatPage 装配收口   1 个 commit
4.1/4.2  拆函数拆文件            按文件分组，每组 1 个 commit，每个 commit 后 detekt 数量只减不增
```

每个 commit 前必须通过第一阶段 0.2 的三条验证命令。每个 commit message 末尾附一行 `detekt: N` 记录当时的剩余告警数。

## 6. 最终验收

```bash
bash tools/quality_metrics.sh          # 函数≥100行为空；文件>500行≤3；ui import data≤4；!!/println/TODO 为 0
./gradlew detekt --rerun-tasks         # 0 条
./gradlew :shared:testDebugUnitTest :kuikly-chart:testDebugUnitTest :table-core:testDebugUnitTest
./gradlew :androidApp:assembleDebug
grep -rn 'StockDetailController\|SettingsController\|ChatSendController' shared/src/commonMain/kotlin/com/guet/liang/stockchat/ui | wc -l   # 必须 > 0，证明 controller 被页面实际使用
```
