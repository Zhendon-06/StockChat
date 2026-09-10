package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.base.BasePager
import com.guet.liang.stockchat.base.bridgeModule
import com.guet.liang.stockchat.base.setTimeout
import com.guet.liang.stockchat.controller.ArtifactController
import com.guet.liang.stockchat.controller.ChatSendController
import com.guet.liang.stockchat.controller.ChatSessionController
import com.guet.liang.stockchat.controller.ModelSelectionController
import com.guet.liang.stockchat.controller.SettingsCatalogRepository
import com.guet.liang.stockchat.controller.SettingsController
import com.guet.liang.stockchat.controller.artifactController
import com.guet.liang.stockchat.data.AliyunStockChatDataSource
import com.guet.liang.stockchat.data.ChatHistoryDatabase
import com.guet.liang.stockchat.data.ChatHistoryRepository
import com.guet.liang.stockchat.data.ConversationMindMapArtifactRepository
import com.guet.liang.stockchat.data.ConversationTableArtifactRepository
import com.guet.liang.stockchat.data.MimoSpeechRecognitionService
import com.guet.liang.stockchat.data.MimoSpeechSynthesisService
import com.guet.liang.stockchat.data.MimoVoiceApiConfig
import com.guet.liang.stockchat.data.ModelCatalogService
import com.guet.liang.stockchat.data.StockChatSettingsStore
import com.guet.liang.stockchat.model.AnswerMode
import com.guet.liang.stockchat.data.TencentTodayMarketDataSource
import com.guet.liang.stockchat.data.TodayMarketDataSource
import com.guet.liang.stockchat.model.ChatMessage
import com.guet.liang.stockchat.model.ChatModelOption
import com.guet.liang.stockchat.model.ChatSessionSummary
import com.guet.liang.stockchat.model.DEFAULT_CHAT_MODEL_ICON_ASSET
import com.guet.liang.stockchat.model.TodayMarketUiState
import com.guet.liang.stockchat.model.VoiceInputState
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.ViewRef
import com.tencent.kuikly.core.base.attr.CaptureRule
import com.tencent.kuikly.core.base.attr.CaptureRuleDirection
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.timer.Timer
import com.tencent.kuikly.core.views.ScrollerView
import com.tencent.kuikly.core.views.TextAreaView

// 键盘回调未给出动画时长时的兜底值（秒）
internal const val DEFAULT_KEYBOARD_ANIM_DURATION = 0.25f

@Page(CHAT_PAGE_NAME, supportInLocal = true)
/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal class StockChatPage : BasePager() {
    internal var drawerOpen by observable(false)
    internal var composerFocused by observable(false)
    // 输入面板展开态，与聚焦态解耦：聚焦时进入展开，键盘收起后仍保持展开，
    // 点击页面空白区域才收缩——收缩的意义是"焦点离开输入后还给页面更多空间"，
    // 而不是紧跟键盘状态。这也让收缩动画天然避开键盘回落动画（互相打断问题）
    internal var composerExpanded by observable(false)
    internal var keyboardHeight by observable(0f)
    internal var keyboardVisible by observable(false)
    // 键盘动画时长（秒），来自 keyboardHeightChange 回调，驱动输入面板跟随键盘平滑移动
    internal var keyboardAnimDuration by observable(DEFAULT_KEYBOARD_ANIM_DURATION)
    private val homeFlow = StockChatHomeFlow()
    internal var homeState by observable(homeFlow.state.value)
    internal var welcomeMotionPhase by observable(1f)
    internal var welcomeHeroMotionStage by observable(2)
    internal var welcomeTextMotionStage by observable(2)
    internal var welcomeMotionGeneration = 0
    internal val selectedHomeTab: Int
        get() =
            when (homeState.destination) {
                StockChatHomeDestination.AI_CHAT -> HOME_TAB_CHAT
                StockChatHomeDestination.TODAY_MARKET -> HOME_TAB_TODAY_MARKET
            }

    internal val todayMarketState: TodayMarketUiState
        get() = homeState.todayMarketState

    // 键盘关闭后的强制归位计数，见 scheduleComposerDockResync
    internal var composerDockNudge by observable(0)
    // 输入框收缩动画结束后的强制归位计数，避免 TextArea 位置动画被打断后停在中间值
    internal var composerInputLayoutNudge by observable(0)
    internal var composerInputLayoutResyncGeneration = 0
    // 键盘回落动画进行中的标记：期间主页内容不回挂（避免挂载大子树拖慢回落），
    // 空白点击/滑动触发的收缩也挂起到回落播完（避免同节点两动画互相打断）
    internal var keyboardDropSettling by observable(false)
    // 空白交互发生在键盘在场/回落期间时的挂起收缩标记，回落播完后执行
    internal var collapseComposerAfterSettle = false
    // 回落任务代次：键盘在回落窗口内再次弹起又收起时，作废旧定时器防止提前解冻
    internal var dockSettleGeneration = 0
    internal var inputText by observable("")
    private var prefillQuestionConsumed = false
    // 输入内容折行后的行数（估算，封顶 MAX_INPUT_LINES），驱动输入框与面板同步增高；
    // 超出封顶后 TextArea 自身高度不再增长，交由原生多行输入框的内部滚动查看之前内容
    internal var inputLineCount by observable(1)
    internal var isSending by observable(false)
    internal var voiceInputState by observable(VoiceInputState.IDLE)
    internal var voiceMode by observable(false)
    internal var voicePressActive by observable(false)
    internal var voicePressCanceled by observable(false)
    internal var voiceWavePhase by observable(0)
    // 等待首 token 的三点跳动动画相位，由定时器驱动
    internal var typingDotPhase by observable(0)
    internal var typingDotTimer: Timer? = null
    // 消息「更多」菜单当前指向的消息 id，非空时显示底部弹出菜单
    internal var messageMenuTargetId by observable("")
    internal var conversationMenuOpen by observable(false)
    internal var modelMenuOpen by observable(false)
    internal var selectedModelId by observable("")
    internal var answerMode by observable(AnswerMode.FAST)
    internal var activeModelProviderId by observable("")
    internal var chatModelOptions by observable<List<ChatModelOption>>(emptyList())
    internal var composerModelLabel by observable("选择模型")
    internal var composerModelIcon by observable(DEFAULT_CHAT_MODEL_ICON_ASSET)
    internal var modelMenuContentRevision by observable(0)
    // drawer 模型列表请求状态
    internal var drawerModelsLoading by observable(false)
    internal var drawerModelsError by observable("")
    internal var imagePickerOpen by observable(false)
    internal var selectedImageCount by observable(0)
    internal var messages by observableList<ChatMessage>()
    // 正在流式输出的回答 id 与其正文：流式行按结构复用，正文由这两个 observable 实时驱动，
    // 避免每个片段整行重建导致行内卡片点不动（见 ChatMessageRows）
    internal var streamingAnswerId by observable("")
    internal var streamingAnswerMarkdown by observable("")
    internal var recentSessions by observableList<ChatSessionSummary>()
    internal var managingSessions by observable(false)
    internal var renameSessionId by observable("")
    internal var renameInputText by observable("")
    internal var selectedImages by observableList<String>()
    internal val selectedImagePreviews = mutableListOf<String>()
    internal val selectedImagePayloads = mutableListOf<String>()
    // 当前会话 id：必须是 observable，抽屉列表项的高亮依赖它驱动重渲染
    internal var activeSessionId by observable("")
    internal var voiceRequestToken = 0
    internal var speechSynthesisRequestToken = 0
    // 正在生成/播放语音的消息 id（空串 = 无朗读任务），驱动声音按钮上的流动声纹
    internal var readAloudMessageId by observable("")
    internal var readAloudWavePhase by observable(0)
    internal var readAloudWaveTimer: Timer? = null
    internal var voicePressStartY = 0f
    internal var voicePressReleaseRequested = false
    internal var voiceWaveTimer: Timer? = null
    internal var drawerPanStartX = 0f
    internal var drawerPanStartY = 0f
    internal var modelMenuPanStartX = 0f
    internal var modelMenuPanStartY = 0f
    internal var messageListNearBottom by observable(true)
    internal var stickMessageListToBottom = true
    internal var messageListContentHeight = 0f
    internal var messageListViewHeight = 0f
    internal lateinit var networkModule: NetworkModule
    internal lateinit var speechRecognitionService: MimoSpeechRecognitionService
    internal lateinit var speechSynthesisService: MimoSpeechSynthesisService
    private lateinit var chatHistoryRepository: ChatHistoryRepository
    internal lateinit var sessionController: ChatSessionController
    internal lateinit var sendController: ChatSendController
    internal lateinit var artifactController: ArtifactController
    internal lateinit var todayMarketDataSource: TodayMarketDataSource
    internal lateinit var modelSelectionController: ModelSelectionController
    internal lateinit var settingsController: SettingsController
    internal lateinit var inputRef: ViewRef<TextAreaView>
    internal lateinit var renameInputRef: ViewRef<TextAreaView>
    internal lateinit var messageScrollerRef: ViewRef<ScrollerView<*, *>>
    internal var todayMarketScrollerRef: ViewRef<ScrollerView<*, *>>? = null
    internal var todayMarketScrollOffsetY: Float = 0f
    internal val layoutMetrics: StockChatLayoutMetrics
        get() = StockChatLayoutMetrics(pagerData.pageViewWidth)

    // lateinit 就位探针：扩展函数无法直接使用 ::prop.isInitialized，统一从这里读取
    internal val inputRefReady: Boolean
        get() = ::inputRef.isInitialized

    internal val renameInputRefReady: Boolean
        get() = ::renameInputRef.isInitialized

    internal val messageScrollerRefReady: Boolean
        get() = ::messageScrollerRef.isInitialized

    internal val todayMarketDataSourceReady: Boolean
        get() = ::todayMarketDataSource.isInitialized

    override fun created() {
        super.created()
        applySavedAppearance()
        val mimoVoiceConfig = MimoVoiceApiConfig(apiKey = pageData.params.optString("mimoVoiceApiKey").trim())
        networkModule = acquireModule(NetworkModule.MODULE_NAME)
        val modelCatalogService = ModelCatalogService(networkModule)
        settingsController = settingsController()
        // All OpenAI-compatible providers use the same bridge. Streaming is enabled by
        // default and still falls back to a normal request when a platform bridge is absent.
        val nativeStreamingEnabled = pageData.params.optInt("aliyunNativeStreaming", pageData.params.optInt("mimoNativeStreaming", 1)) == 1
        modelSelectionController =
            ModelSelectionController(
                settings = StockChatSettingsStore.repository,
                catalog = SettingsCatalogRepository { url, key, callback -> modelCatalogService.load(url, key, callback) },
                sourceFactory = { config ->
                    AliyunStockChatDataSource(networkModule, config, bridgeModule, nativeStreamingEnabled && config.supportsStreaming)
                },
                scheduleTimeout = { delay, callback -> setTimeout(delay, callback) },
                routeApiKey = pageData.params.optString("qwenApiKey"),
                onChanged = { state ->
                    activeModelProviderId = state.providerId
                    selectedModelId = state.modelId
                    answerMode = state.answerMode
                    chatModelOptions = state.options
                    composerModelLabel = state.label
                    composerModelIcon = state.icon
                    drawerModelsLoading = state.loading
                    drawerModelsError = state.error
                    modelMenuContentRevision += 1
                },
            )
        chatHistoryRepository = ChatHistoryDatabase.repository()
        todayMarketDataSource = TencentTodayMarketDataSource(networkModule)
        configureChatProvider()
        speechRecognitionService = MimoSpeechRecognitionService(networkModule, mimoVoiceConfig)
        speechSynthesisService =
            MimoSpeechSynthesisService(
                networkModule = networkModule,
                config = mimoVoiceConfig,
                bridgeModule = bridgeModule,
                useNativeStreaming = pageData.params.optInt("mimoNativeStreaming") == 1,
            )
        sessionController =
            ChatSessionController(chatHistoryRepository) { state ->
                activeSessionId = state.activeSessionId
                recentSessions.diffUpdate(state.recentSessions)
                syncMessageRows(state.messages)
                updateTypingIndicatorTimer()
            }
        sendController =
            ChatSendController({ modelSelectionController.dataSource }, sessionController, { selectedModel() }) {
                isSending = it
                updateTypingIndicatorTimer()
            }
        artifactController = artifactController()
        initializeChatSessions()
        dispatchHome(StockChatHomeEvent.Started)
        bridgeModule.observeDrawerGestures { result ->
            when (result?.optString("direction")) {
                "right" -> openDrawer()
                "left" -> closeDrawer()
            }
        }
        observeBackRequests()
        // 键盘/聚焦任一信号出现时隐藏欢迎内容；节点保持挂载，只切透明度，
        // 避免键盘回落后重建绝对定位子树时从左上角飞入
        bindValueChange({ composerFocused || keyboardVisible || keyboardHeight > 0f || keyboardDropSettling }) { hidden ->
            dispatchHome(StockChatHomeEvent.WelcomeObscuredChanged(hidden == true))
        }
    }

    override fun pageDidAppear() {
        super.pageDidAppear()
        applySavedAppearance()
        observeBackRequests()
        configureChatProvider()
        refreshRecentSessions()
        applyPrefillQuestionIfNeeded()
    }

    private fun applyPrefillQuestionIfNeeded() {
        if (prefillQuestionConsumed) {
            return
        }
        val prefillQuestion = pageData.params.optString("prefillQuestion").trim()
        if (prefillQuestion.isBlank()) {
            return
        }
        prefillQuestionConsumed = true
        // stockContext 作为独立的结构化路由参数保留，不再重复拼进可见草稿。
        // 草稿与 TextArea 使用同一上限，避免原生限长截断后的文本、行数和面板高度失配。
        inputText = prefillQuestion.take(MAX_COMPOSER_TEXT_LENGTH)
        updateInputLineMetrics(inputText)
        focusComposer()
    }

    private fun observeBackRequests() {
        bridgeModule.observeBackRequests { handleBackRequest() }
    }

    override fun themeDidChanged(data: JSONObject) {
        super.themeDidChanged(data)
        applySavedAppearance()
    }

    internal fun dispatchHome(event: StockChatHomeEvent) {
        val previousState = homeState
        val effects = homeFlow.dispatch(event)
        val nextState = homeFlow.state.value
        val enteredWelcome =
            nextState.destination == StockChatHomeDestination.AI_CHAT &&
                nextState.chatStage == StockChatHomeChatStage.WELCOME &&
                (previousState.destination != StockChatHomeDestination.AI_CHAT || previousState.chatStage != StockChatHomeChatStage.WELCOME)
        val leftWelcome =
            previousState.destination == StockChatHomeDestination.AI_CHAT &&
                previousState.chatStage == StockChatHomeChatStage.WELCOME &&
                (nextState.destination != StockChatHomeDestination.AI_CHAT || nextState.chatStage != StockChatHomeChatStage.WELCOME)
        if (enteredWelcome || leftWelcome) {
            stageWelcomeMotion(nextState)
        }
        if (nextState != homeState) {
            homeState = nextState
        }
        effects.forEach(::handleHomeEffect)
    }

    private fun handleHomeEffect(effect: StockChatHomeEffect) {
        when (effect) {
            StockChatHomeEffect.DismissChatUi -> {
                cancelVoiceInput()
                if (::inputRef.isInitialized) {
                    inputRef.view?.blur()
                }
                resetKeyboardState()
                // 输入框有内容时保持展开：收缩态按单行布局排版，多行文本会挤乱
                if (inputText.isEmpty()) {
                    collapseComposer()
                }
                voiceMode = false
                imagePickerOpen = false
                messageMenuTargetId = ""
                conversationMenuOpen = false
                modelMenuOpen = false
            }
            StockChatHomeEffect.CloseDrawer -> closeDrawer()
            is StockChatHomeEffect.LoadTodayMarket -> {
                requestTodayMarket(effect.requestId)
            }
        }
    }

    override fun pageDidDisappear() {
        cancelVoiceInput()
        stopSpeechPlayback()
        super.pageDidDisappear()
    }

    override fun pageWillDestroy() {
        cancelVoiceInput()
        stopSpeechPlayback()
        typingDotTimer?.cancel()
        typingDotTimer = null
        if (::chatHistoryRepository.isInitialized) {
            persistChatHistory()
        }
        bridgeModule.stopObservingDrawerGestures()
        bridgeModule.stopObservingBackRequests()
        sendController.invalidate()
        modelSelectionController.invalidate()
        welcomeMotionGeneration += 1
        dispatchHome(StockChatHomeEvent.Stopped)
        super.pageWillDestroy()
    }

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr {
                backgroundColor(StockChatTheme.background)
                overflow(true)
                capture(CaptureRule.pan(CaptureRuleDirection.HORIZONTAL))
            }
            event { pan { params -> ctx.handleDrawerPan(params) } }
            vif({ StockChatTheme.renderRevision % 2 == 0 }) { ctx.ContentLayers(this) }
            velse { ctx.ContentLayers(this) }
        }
    }

    private fun ContentLayers(container: ViewContainer<*, *>) {
        val ctx = this
        with(container) {
            ctx.DrawerLayer(this)
            ctx.MainLayer(this)
            ctx.SessionRenameOverlay(this)
        }
    }
}
