package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.base.BasePager
import com.guet.liang.stockchat.base.ShareModule
import com.guet.liang.stockchat.base.bridgeModule
import com.guet.liang.stockchat.base.setTimeout
import com.guet.liang.stockchat.data.AliyunApiConfig
import com.guet.liang.stockchat.data.AliyunStockChatDataSource
import com.guet.liang.stockchat.data.ChatHistoryDatabase
import com.guet.liang.stockchat.data.ChatHistoryRepository
import com.guet.liang.stockchat.data.ChatSessionSummary
import com.guet.liang.stockchat.data.ConversationMindMapArtifactGenerator
import com.guet.liang.stockchat.data.ConversationMindMapArtifactRepository
import com.guet.liang.stockchat.data.ConversationStockComparisonGenerator
import com.guet.liang.stockchat.data.ConversationTableArtifactRepository
import com.guet.liang.stockchat.data.MimoSpeechRecognitionService
import com.guet.liang.stockchat.data.MimoSpeechSynthesisService
import com.guet.liang.stockchat.data.MimoVoiceApiConfig
import com.guet.liang.stockchat.data.StockChatDataSource
import com.guet.liang.stockchat.data.StockChatShareContentBuilder
import com.guet.liang.stockchat.data.StockChatSettingsStore
import com.guet.liang.stockchat.data.TodayMarketDataSource
import com.guet.liang.stockchat.data.TencentTodayMarketDataSource
import com.guet.liang.stockchat.data.providerSymbolForQuote
import com.guet.liang.stockchat.model.AnswerBlock
import com.guet.liang.stockchat.model.ChatAnswer
import com.guet.liang.stockchat.model.ChatHistoryItem
import com.guet.liang.stockchat.model.ChatMessage
import com.guet.liang.stockchat.model.ChatRole
import com.guet.liang.stockchat.model.MessageState
import com.guet.liang.stockchat.model.ModelCapability
import com.guet.liang.stockchat.model.ModelProviderConfig
import com.guet.liang.stockchat.model.ModelProviderKind
import com.guet.liang.stockchat.model.ShareResult
import com.guet.liang.stockchat.model.SpeechRecognitionResult
import com.guet.liang.stockchat.model.SpeechSynthesisResult
import com.guet.liang.stockchat.model.StockQuote
import com.guet.liang.stockchat.model.TodayMarketUiState
import com.guet.liang.stockchat.model.VoiceInputState
import com.guet.liang.stockchat.ui.settings.SETTINGS_PAGE_NAME
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ColorStop
import com.tencent.kuikly.core.base.Direction
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.ViewRef
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.base.attr.CaptureRule
import com.tencent.kuikly.core.base.attr.CaptureRuleDirection
import com.tencent.kuikly.core.base.event.LongPressParams
import com.tencent.kuikly.core.base.event.PanGestureParams
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.timer.Timer
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.ScrollerView
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.TextArea
import com.tencent.kuikly.core.views.TextAreaView
import com.tencent.kuikly.core.views.View

private const val CHAT_PAGE_NAME = "router"
private const val STOCK_DETAIL_PAGE_NAME = "stock_detail"
private const val IMAGE_PREVIEW_PAGE_NAME = "stock_image_preview"
private const val DEFAULT_CHAT_MODEL_ID = "qwen-plus"
private const val DEFAULT_CHAT_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"
private const val HOME_TAB_CHAT = 0
private const val HOME_TAB_TODAY_MARKET = 1
private const val HOME_SCENE_EXIT_DURATION = 0.16f
private const val HOME_SCENE_ENTER_DURATION = 0.32f
private const val HOME_SCENE_ENTER_DAMPING = 0.92f
private const val HOME_SCENE_ENTER_VELOCITY = 0.12f
private const val HOME_COMPOSER_ENTER_DURATION = 0.38f
private const val HOME_CAPSULE_TRAVEL_DURATION = 0.46f
// 键盘回调未给出动画时长时的兜底值（秒）
private const val DEFAULT_KEYBOARD_ANIM_DURATION = 0.25f

private data class StockChatSuggestion(val iconAsset: String, val text: String)

// 欢迎页输入框上方的快捷问题，点击直接发送
private val WELCOME_SUGGESTIONS = listOf(
    StockChatSuggestion("ranking_icon.png", "今日大盘怎么样"),
    StockChatSuggestion("level_icon.png", "分析一下贵州茅台"),
    StockChatSuggestion("table_icon.png", "看看沪深 300 指数"),
    StockChatSuggestion("ai_generate.png", "现在市场风险大吗"),
    StockChatSuggestion("data_icon.png", "新手怎么开始炒股？"),
    StockChatSuggestion("file_icon.png", "什么是市盈率？"),
    StockChatSuggestion("ranking_icon.png", "怎么分散投资风险？"),
)

private data class ChatModelOption(
    val id: String,
    val displayName: String,
    val description: String,
    val badge: String,
    val multiplier: String,
    val capabilities: Set<ModelCapability> = setOf(ModelCapability.CHAT),
)

private val CHAT_MODEL_OPTIONS = listOf(
    ChatModelOption(
        id = DEFAULT_CHAT_MODEL_ID,
        displayName = "千问",
        description = "均衡，适合股票问答与综合分析",
        badge = "默认",
        multiplier = "1.00x",
    ),
    ChatModelOption(
        id = "qwen-max",
        displayName = "千问 Max",
        description = "复杂推理与深度研究",
        badge = "深度",
        multiplier = "2.00x",
    ),
    ChatModelOption(
        id = "qwen-turbo",
        displayName = "千问 Turbo",
        description = "更快响应日常问题",
        badge = "快速",
        multiplier = "0.50x",
    ),
    ChatModelOption(
        id = "qwen-long",
        displayName = "千问 Long",
        description = "适合长文本与财报分析",
        badge = "长文",
        multiplier = "1.20x",
    ),
)

@Page(CHAT_PAGE_NAME, supportInLocal = true)
internal class StockChatPage : BasePager() {
    private var drawerOpen by observable(false)
    private var composerFocused by observable(false)
    // 输入面板展开态，与聚焦态解耦：聚焦时进入展开，键盘收起后仍保持展开，
    // 点击页面空白区域才收缩——收缩的意义是"焦点离开输入后还给页面更多空间"，
    // 而不是紧跟键盘状态。这也让收缩动画天然避开键盘回落动画（互相打断问题）
    private var composerExpanded by observable(false)
    private var keyboardHeight by observable(0f)
    private var keyboardVisible by observable(false)
    // 键盘动画时长（秒），来自 keyboardHeightChange 回调，驱动输入面板跟随键盘平滑移动
    private var keyboardAnimDuration by observable(DEFAULT_KEYBOARD_ANIM_DURATION)
    private val homeFlow = StockChatHomeFlow()
    private var homeState by observable(homeFlow.state.value)
    private var homeSceneInteractive by observable(true)
    private var homeSceneEnterReady by observable(true)
    private var homeSceneOutgoingTab by observable(-1)
    private var homeSceneAnimationPhase by observable(0)
    private var homeSceneTransitionGeneration = 0
    private val selectedHomeTab: Int
        get() = when (homeState.destination) {
            StockChatHomeDestination.AI_CHAT -> HOME_TAB_CHAT
            StockChatHomeDestination.TODAY_MARKET -> HOME_TAB_TODAY_MARKET
        }
    private val todayMarketState: TodayMarketUiState
        get() = homeState.todayMarketState
    // 键盘关闭后的强制归位计数，见 scheduleComposerDockResync
    private var composerDockNudge by observable(0)
    // 键盘回落动画进行中的标记：期间主页内容不回挂（避免挂载大子树拖慢回落），
    // 空白点击/滑动触发的收缩也挂起到回落播完（避免同节点两动画互相打断）
    private var keyboardDropSettling by observable(false)
    // 空白交互发生在键盘在场/回落期间时的挂起收缩标记，回落播完后执行
    private var collapseComposerAfterSettle = false
    // 回落任务代次：键盘在回落窗口内再次弹起又收起时，作废旧定时器防止提前解冻
    private var dockSettleGeneration = 0
    private var inputText by observable("")
    // 输入内容折行后的行数（估算），驱动输入框与面板同步增高
    private var inputLineCount by observable(1)
    private var isSending by observable(false)
    private var voiceInputState by observable(VoiceInputState.IDLE)
    private var voiceMode by observable(false)
    private var voicePressActive by observable(false)
    private var voicePressCanceled by observable(false)
    private var voiceWavePhase by observable(0)
    // 等待首 token 的三点跳动动画相位，由定时器驱动
    private var typingDotPhase by observable(0)
    private var typingDotTimer: Timer? = null
    // 消息「更多」菜单当前指向的消息 id，非空时显示底部弹出菜单
    private var messageMenuTargetId by observable("")
    private var conversationMenuOpen by observable(false)
    private var modelMenuOpen by observable(false)
    private var selectedModelId by observable(DEFAULT_CHAT_MODEL_ID)
    private var activeModelProviderId by observable("")
    private var chatModelOptions by observable(CHAT_MODEL_OPTIONS)
    private var imagePickerOpen by observable(false)
    private var selectedImageCount by observable(0)
    private var messages by observableList<ChatMessage>()
    private var recentSessions by observableList<ChatSessionSummary>()
    private var managingSessions by observable(false)
    private var renameSessionId by observable("")
    private var renameInputText by observable("")
    private var selectedImages by observableList<String>()
    private val selectedImagePreviews = mutableListOf<String>()
    private val selectedImagePayloads = mutableListOf<String>()
    private var messageSequence = 0
    private var sessionSequence = 0
    // 当前会话 id：必须是 observable，抽屉列表项的高亮依赖它驱动重渲染
    private var activeSessionId by observable("")
    private var requestToken = 0
    private var voiceRequestToken = 0
    private var speechSynthesisRequestToken = 0
    // 正在生成/播放语音的消息 id（空串 = 无朗读任务），驱动声音按钮上的流动声纹
    private var readAloudMessageId by observable("")
    private var readAloudWavePhase by observable(0)
    private var readAloudWaveTimer: Timer? = null
    private var voicePressStartY = 0f
    private var voicePressReleaseRequested = false
    private var voiceWaveTimer: Timer? = null
    private var drawerPanStartX = 0f
    private var drawerPanStartY = 0f
    private var messageListNearBottom = true
    private var stickMessageListToBottom = true
    private lateinit var networkModule: NetworkModule
    private lateinit var dataSource: StockChatDataSource
    private lateinit var speechRecognitionService: MimoSpeechRecognitionService
    private lateinit var speechSynthesisService: MimoSpeechSynthesisService
    private lateinit var chatHistoryRepository: ChatHistoryRepository
    private lateinit var tableArtifactRepository: ConversationTableArtifactRepository
    private lateinit var mindMapArtifactRepository: ConversationMindMapArtifactRepository
    private lateinit var todayMarketDataSource: TodayMarketDataSource

    private lateinit var inputRef: ViewRef<TextAreaView>
    private lateinit var renameInputRef: ViewRef<TextAreaView>
    private lateinit var messageScrollerRef: ViewRef<ScrollerView<*, *>>

    private val layoutMetrics: StockChatLayoutMetrics
        get() = StockChatLayoutMetrics(pagerData.pageViewWidth)

    override fun created() {
        super.created()
        applySavedAppearance()
        val mimoVoiceConfig = MimoVoiceApiConfig(
            apiKey = pageData.params.optString("mimoVoiceApiKey").trim(),
        )
        networkModule = acquireModule(NetworkModule.MODULE_NAME)
        chatHistoryRepository = ChatHistoryDatabase.repository()
        tableArtifactRepository = ChatHistoryDatabase.artifactRepository()
        mindMapArtifactRepository = ChatHistoryDatabase.mindMapArtifactRepository()
        todayMarketDataSource = TencentTodayMarketDataSource(networkModule)
        configureChatProvider()
        speechRecognitionService = MimoSpeechRecognitionService(networkModule, mimoVoiceConfig)
        speechSynthesisService = MimoSpeechSynthesisService(
            networkModule = networkModule,
            config = mimoVoiceConfig,
            bridgeModule = bridgeModule,
            useNativeStreaming = pageData.params.optInt("mimoNativeStreaming") == 1,
        )
        initializeChatSessions()
        dispatchHome(StockChatHomeEvent.Started)
        bridgeModule.observeDrawerGestures { result ->
            when (result?.optString("direction")) {
                "right" -> openDrawer()
                "left" -> closeDrawer()
            }
        }
        // 键盘/聚焦任一信号出现时隐藏欢迎内容；节点保持挂载，只切透明度，
        // 避免键盘回落后重建绝对定位子树时从左上角飞入
        bindValueChange({
            composerFocused || keyboardVisible || keyboardHeight > 0f || keyboardDropSettling
        }) { hidden ->
            dispatchHome(
                StockChatHomeEvent.WelcomeObscuredChanged(hidden == true)
            )
        }
    }

    override fun pageDidAppear() {
        super.pageDidAppear()
        applySavedAppearance()
        configureChatProvider()
    }

    override fun themeDidChanged(data: JSONObject) {
        super.themeDidChanged(data)
        applySavedAppearance()
    }

    private fun dispatchHome(event: StockChatHomeEvent) {
        val previousDestination = homeState.destination
        val effects = homeFlow.dispatch(event)
        val nextState = homeFlow.state.value
        if (nextState.destination != previousDestination) {
            stageHomeSceneTransition(previousDestination, nextState.destination)
        }
        if (nextState != homeState) {
            homeState = nextState
        }
        effects.forEach(::handleHomeEffect)
    }

    private fun stageHomeSceneTransition(
        previousDestination: StockChatHomeDestination,
        destination: StockChatHomeDestination,
    ) {
        val generation = ++homeSceneTransitionGeneration
        homeSceneOutgoingTab = when (previousDestination) {
            StockChatHomeDestination.AI_CHAT -> HOME_TAB_CHAT
            StockChatHomeDestination.TODAY_MARKET -> HOME_TAB_TODAY_MARKET
        }
        homeSceneEnterReady = false
        homeSceneInteractive = false
        homeSceneAnimationPhase += 1
        setTimeout((HOME_SCENE_EXIT_DURATION * 1000f).toInt()) {
            if (
                generation == homeSceneTransitionGeneration &&
                homeState.destination == destination
            ) {
                homeSceneEnterReady = true
                homeSceneOutgoingTab = -1
                homeSceneAnimationPhase += 1
                val enterDuration = when (destination) {
                    StockChatHomeDestination.AI_CHAT -> HOME_COMPOSER_ENTER_DURATION
                    StockChatHomeDestination.TODAY_MARKET -> HOME_SCENE_ENTER_DURATION
                }
                setTimeout((enterDuration * 1000f).toInt()) {
                    if (
                        generation == homeSceneTransitionGeneration &&
                        homeState.destination == destination
                    ) {
                        homeSceneInteractive = true
                    }
                }
            }
        }
    }

    private fun handleHomeEffect(effect: StockChatHomeEffect) {
        when (effect) {
            StockChatHomeEffect.DismissChatUi -> {
                cancelVoiceInput()
                if (::inputRef.isInitialized) {
                    inputRef.view?.blur()
                }
                resetKeyboardState()
                composerExpanded = false
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
        requestToken += 1
        homeSceneTransitionGeneration += 1
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
            event {
                pan { params -> ctx.handleDrawerPan(params) }
            }
            ctx.DrawerLayer(this)
            ctx.MainLayer(this)
            ctx.SessionRenameOverlay(this)
        }
    }

    private fun DrawerLayer(container: ViewContainer<*, *>) {
        val ctx = this
        val metrics = ctx.layoutMetrics
        val drawerWidth = metrics.drawerWidth
        with(container) {
        View {
            attr {
                absolutePosition(top = 0f, left = 0f, bottom = 0f)
                width(drawerWidth)
                backgroundColor(StockChatTheme.surface)
                zIndex(1)
                transform(Translate(0f, 0f, if (ctx.drawerOpen) 0f else -drawerWidth, 0f))
                animation(Animation.springEaseOut(0.38f, 0.9f, 0.2f), ctx.drawerOpen)
                touchEnable(ctx.drawerOpen)
                padding(
                    top = pagerData.statusBarHeight + metrics.dp(18f),
                    left = metrics.dp(22f),
                    right = metrics.dp(22f),
                    bottom = pagerData.safeAreaInsets.bottom + metrics.dp(18f),
                )
                capture(CaptureRule.pan(CaptureRuleDirection.HORIZONTAL))
            }
            event {
                pan { params -> ctx.handleDrawerPan(params) }
            }
            View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                }
                View {
                    attr {
                        size(metrics.dp(44f), metrics.dp(44f))
                        borderRadius(metrics.dp(15f))
                        backgroundColor(StockChatTheme.accentSoft)
                        allCenter()
                    }
                    Text {
                        attr {
                            text("S")
                            fontSize(metrics.dp(20f))
                            fontWeightBold()
                            color(StockChatTheme.accent)
                        }
                    }
                }
                View {
                    attr {
                        flex(1f)
                        marginLeft(metrics.dp(12f))
                    }
                    Text {
                        attr {
                            text("StockMate")
                            fontSize(metrics.dp(20f))
                            fontWeightBold()
                            color(StockChatTheme.textPrimary)
                        }
                    }
                    Text {
                        attr {
                            text("AI 股票问答")
                            fontSize(metrics.dp(12f))
                            color(StockChatTheme.textSecondary)
                            marginTop(metrics.dp(2f))
                        }
                    }
                }
                View {
                    attr {
                        size(metrics.dp(36f), metrics.dp(36f))
                        borderRadius(metrics.dp(10f))
                        border(Border(1f, BorderStyle.SOLID, StockChatTheme.border))
                        allCenter()
                    }
                    event {
                        click { ctx.openSettings() }
                    }
                    Text {
                        attr {
                            text("⚙")
                            fontSize(metrics.dp(18f))
                            color(StockChatTheme.textPrimary)
                        }
                    }
                }
            }
            ctx.HomeTabSwitcher(
                this,
                marginTopDp = 20f,
                enabled = { ctx.drawerOpen },
            )
            ctx.DrawerMenuItem(this, "ranking_icon.png", "思维导图", metrics.scale) {
                ctx.openMindMapArtifactLibrary()
            }
            ctx.DrawerMenuItem(this, "table_icon.png", "表格", metrics.scale) {
                ctx.openStockComparisonLibrary()
            }
            ctx.DrawerMenuItem(this, "ai_generate.png", "AI 选股思路", metrics.scale) {
                ctx.sendQuickQuestion("如何建立自己的选股思路？")
            }
            View {
                attr {
                    height(1f)
                    backgroundColor(StockChatTheme.border)
                    marginTop(metrics.dp(6f))
                    marginBottom(metrics.dp(8f))
                }
            }

            View {
                attr {
                    height(metrics.dp(48f))
                    flexDirectionRow()
                    alignItemsCenter()
                    marginTop(metrics.dp(2f))
                }
                event {
                    click { ctx.startNewChat() }
                }
                Text {
                    attr {
                        text("＋")
                        fontSize(metrics.dp(25f))
                        color(StockChatTheme.textPrimary)
                        marginRight(metrics.dp(10f))
                    }
                }
                Text {
                    attr {
                        text("新建对话")
                        fontSize(metrics.dp(16f))
                        fontWeightBold()
                        color(StockChatTheme.textPrimary)
                    }
                }
            }
            View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                }
                Text {
                    attr {
                        text("最近对话")
                        fontSize(metrics.dp(13f))
                        color(StockChatTheme.textTertiary)
                        flex(1f)
                    }
                }
                Text {
                    attr {
                        text(if (ctx.managingSessions) "完成" else "管理")
                        fontSize(metrics.dp(13f))
                        color(StockChatTheme.accent)
                    }
                    event {
                        click { ctx.toggleSessionManagement() }
                    }
                }
            }
            Scroller {
                attr {
                    flex(1f)
                    showScrollerIndicator(false)
                    bouncesEnable(true)
                    capture(CaptureRule.pan(CaptureRuleDirection.VERTICAL))
                    padding(bottom = metrics.dp(8f))
                }
                    vif({ ctx.recentSessions.isEmpty() }) {
                        Text {
                            attr {
                                text("暂无已保存的对话")
                                fontSize(metrics.dp(13f))
                                color(StockChatTheme.textTertiary)
                                marginTop(metrics.dp(12f))
                            }
                        }
                    }
                    vfor({ ctx.recentSessions }) { session ->
                        ctx.DrawerConversation(this, session, metrics.scale)
                    }
            }

        }
        }
    }

    // 「AI 问答 / 今日市场」分段开关：主页与抽屉共用，选中态样式由同一状态驱动，保证两处一致
    private fun HomeTabSwitcher(
        container: ViewContainer<*, *>,
        marginTopDp: Float = 0f,
        widthDp: Float? = null,
        elevated: Boolean = false,
        enabled: () -> Boolean = { true },
    ) {
        val ctx = this
        val metrics = ctx.layoutMetrics
        with(container) {
            View {
                attr {
                    if (widthDp != null) {
                        width(metrics.dp(widthDp))
                    }
                    height(metrics.dp(44f))
                    borderRadius(metrics.dp(22f))
                    backgroundColor(StockChatTheme.recessed)
                    if (elevated) {
                        boxShadow(
                            BoxShadow(
                                0f,
                                metrics.dp(3f),
                                metrics.dp(12f),
                                Color(0x18000000),
                            )
                        )
                    }
                    flexDirectionRow()
                    padding(all = metrics.dp(3f))
                    touchEnable(enabled())
                    if (marginTopDp > 0f) {
                        marginTop(metrics.dp(marginTopDp))
                    }
                }
                ctx.HomeTabItem(this, "AI 问答", HOME_TAB_CHAT, enabled)
                ctx.HomeTabItem(this, "今日市场", HOME_TAB_TODAY_MARKET, enabled)
            }
        }
    }

    private fun SessionRenameOverlay(container: ViewContainer<*, *>) {
        val ctx = this
        val metrics = ctx.layoutMetrics
        with(container) {
            vif({ ctx.renameSessionId.isNotEmpty() }) {
                View {
                    attr {
                        absolutePositionAllZero()
                        backgroundColor(Color(0x66000000))
                        zIndex(20)
                    }
                    event {
                        click { ctx.closeRenameDialog() }
                    }
                }
                View {
                    attr {
                        absolutePosition(
                            top = pagerData.statusBarHeight + metrics.dp(150f),
                            left = metrics.dp(28f),
                            right = metrics.dp(28f),
                        )
                        borderRadius(metrics.dp(18f))
                        backgroundColor(StockChatTheme.surface)
                        padding(all = metrics.dp(20f))
                        zIndex(21)
                    }
                    Text {
                        attr {
                            text("重命名对话")
                            fontSize(metrics.dp(18f))
                            fontWeightBold()
                            color(StockChatTheme.textPrimary)
                        }
                    }
                    TextArea {
                        ref {
                            ctx.renameInputRef = it
                        }
                        attr {
                            width(pagerData.pageViewWidth - metrics.dp(96f))
                            height(metrics.dp(46f))
                            marginTop(metrics.dp(16f))
                            border(Border(1f, BorderStyle.SOLID, StockChatTheme.borderStrong))
                            borderRadius(metrics.dp(10f))
                            text(ctx.renameInputText)
                            fontSize(metrics.dp(15f))
                            color(StockChatTheme.textPrimary)
                            placeholder("输入对话名称")
                            placeholderColor(StockChatTheme.textTertiary)
                            maxTextLength(40)
                        }
                        event {
                            textDidChange(isSyncEdit = true) {
                                ctx.renameInputText = it.text
                            }
                        }
                    }
                    View {
                        attr {
                            flexDirectionRow()
                            justifyContentFlexEnd()
                            alignItemsCenter()
                            marginTop(metrics.dp(16f))
                        }
                        View {
                            attr {
                                height(metrics.dp(36f))
                                padding(left = metrics.dp(14f), right = metrics.dp(14f))
                                allCenter()
                            }
                            event {
                                click { ctx.closeRenameDialog() }
                            }
                            Text {
                                attr {
                                    text("取消")
                                    fontSize(metrics.dp(14f))
                                    color(StockChatTheme.textSecondary)
                                }
                            }
                        }
                        View {
                            attr {
                                height(metrics.dp(36f))
                                padding(left = metrics.dp(14f), right = metrics.dp(14f))
                                borderRadius(metrics.dp(18f))
                                backgroundColor(StockChatTheme.accent)
                                allCenter()
                            }
                            event {
                                click { ctx.commitSessionRename() }
                            }
                            Text {
                                attr {
                                    text("保存")
                                    fontSize(metrics.dp(14f))
                                    fontWeightBold()
                                    color(Color.WHITE)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun HomeTabItem(
        container: ViewContainer<*, *>,
        label: String,
        tabIndex: Int,
        enabled: () -> Boolean = { true },
    ) {
        val ctx = this
        val metrics = ctx.layoutMetrics
        with(container) {
            View {
                attr {
                    val selected = ctx.selectedHomeTab == tabIndex
                    flex(1f)
                    borderRadius(metrics.dp(19f))
                    // 选中态为带阴影的白色胶囊，未选中态透明；阴影不参与布局，切换时布局稳定
                    backgroundColor(
                        if (selected) StockChatTheme.surface else Color(0x00000000)
                    )
                    boxShadow(
                        BoxShadow(
                            metrics.dp(0f),
                            metrics.dp(2f),
                            metrics.dp(8f),
                            if (selected) Color(0x1F000000) else Color(0x00000000),
                        )
                    )
                    allCenter()
                    // 触摸门禁只放在外层容器和 click 回调里：enabled() 读的是本节点
                    // 未注册动画的 observable，放进 attr 会打断选中态动画
                    animate(Animation.easeOut(0.18f), ctx.selectedHomeTab)
                }
                event {
                    click {
                        if (enabled()) {
                            ctx.selectHomeTab(tabIndex)
                        }
                    }
                }
                Text {
                    attr {
                        text(label)
                        fontSize(metrics.dp(15f))
                        if (ctx.selectedHomeTab == tabIndex) {
                            fontWeightBold()
                        }
                        color(
                            if (ctx.selectedHomeTab == tabIndex) {
                                StockChatTheme.textPrimary
                            } else {
                                StockChatTheme.textSecondary
                            }
                        )
                    }
                }
            }
        }
    }

    private fun selectHomeTab(tabIndex: Int) {
        val destination = when (tabIndex) {
            HOME_TAB_CHAT -> StockChatHomeDestination.AI_CHAT
            HOME_TAB_TODAY_MARKET -> StockChatHomeDestination.TODAY_MARKET
            else -> return
        }
        dispatchHome(StockChatHomeEvent.DestinationSelected(destination))
    }

    private fun TodayMarketLayer(container: ViewContainer<*, *>) {
        val ctx = this
        val metrics = ctx.layoutMetrics
        with(container) {
            View {
                attr {
                    val active = ctx.selectedHomeTab == HOME_TAB_TODAY_MARKET
                    val entering = active && ctx.homeSceneEnterReady
                    val exiting =
                        ctx.homeSceneOutgoingTab == HOME_TAB_TODAY_MARKET &&
                            !ctx.homeSceneEnterReady
                    absolutePosition(
                        top = pagerData.statusBarHeight + metrics.dp(66f),
                        left = 0f,
                        right = 0f,
                        bottom = 0f,
                    )
                    visibility(active || exiting)
                    opacity(if (entering) 1f else 0f)
                    transform(
                        Translate(
                            0f,
                            0f,
                            0f,
                            if (entering) 0f else metrics.dp(12f),
                        )
                    )
                    touchEnable(entering && ctx.homeSceneInteractive)
                    zIndex(if (entering || exiting) 2 else 0)
                    animate(
                        if (entering) {
                            Animation.springEaseOut(
                                HOME_SCENE_ENTER_DURATION,
                                HOME_SCENE_ENTER_DAMPING,
                                HOME_SCENE_ENTER_VELOCITY,
                            )
                        } else {
                            Animation.easeOut(HOME_SCENE_EXIT_DURATION)
                        },
                        ctx.homeSceneAnimationPhase,
                    )
                }
                event {
                    click { }
                }
                TodayMarketContent(
                    state = { ctx.todayMarketState },
                    pageWidth = ctx.pagerData.pageViewWidth,
                    scale = ctx.layoutMetrics.scale,
                    safeAreaBottom = ctx.pagerData.safeAreaInsets.bottom,
                    touchEnabled = {
                        ctx.selectedHomeTab == HOME_TAB_TODAY_MARKET &&
                            ctx.homeSceneInteractive
                    },
                    onQuoteClick = { quote ->
                        if (ctx.selectedHomeTab == HOME_TAB_TODAY_MARKET) {
                            ctx.openStockDetail(
                                quote,
                                HOME_TAB_TODAY_MARKET,
                            )
                        }
                    },
                    onRetry = {
                        if (ctx.selectedHomeTab == HOME_TAB_TODAY_MARKET) {
                            ctx.dispatchHome(StockChatHomeEvent.TodayMarketRetryRequested)
                        }
                    },
                )
            }
        }
    }

    private fun ChatLayer(container: ViewContainer<*, *>) {
        val ctx = this
        val metrics = ctx.layoutMetrics
        with(container) {
            View {
                attr {
                    val active = ctx.selectedHomeTab == HOME_TAB_CHAT
                    val entering = active && ctx.homeSceneEnterReady
                    val exiting = ctx.homeSceneOutgoingTab == HOME_TAB_CHAT &&
                        !ctx.homeSceneEnterReady
                    absolutePositionAllZero()
                    visibility(active || exiting)
                    opacity(if (entering) 1f else 0f)
                    transform(
                        Translate(
                            0f,
                            0f,
                            0f,
                            if (entering) 0f else -metrics.dp(12f),
                        )
                    )
                    touchEnable(entering && ctx.homeSceneInteractive)
                    zIndex(if (entering || exiting) 2 else 0)
                    backgroundLinearGradient(
                        Direction.TO_BOTTOM_RIGHT,
                        ColorStop(StockChatTheme.chatBackgroundStart, 0f),
                        ColorStop(StockChatTheme.chatBackgroundEnd, 1f),
                    )
                    animate(
                        if (entering) {
                            Animation.springEaseOut(
                                HOME_SCENE_ENTER_DURATION,
                                HOME_SCENE_ENTER_DAMPING,
                                HOME_SCENE_ENTER_VELOCITY,
                            )
                        } else {
                            Animation.easeOut(HOME_SCENE_EXIT_DURATION)
                        },
                        ctx.homeSceneAnimationPhase,
                    )
                }
                event {
                    click {
                        if (ctx.selectedHomeTab == HOME_TAB_CHAT) {
                            ctx.handleBlankAreaTap()
                        }
                    }
                }
                vif({ !StockChatTheme.backgroundImageUri.isNullOrBlank() }) {
                    Image {
                        attr {
                            absolutePositionAllZero()
                            resizeCover()
                            src(StockChatTheme.backgroundImageUri.orEmpty(), false)
                            touchEnable(false)
                        }
                    }
                }
                View {
                    attr {
                        absolutePositionAllZero()
                        backgroundColor(StockChatTheme.backgroundSofteningMask)
                        touchEnable(false)
                    }
                }
                View {
                    attr {
                        absolutePositionAllZero()
                        backgroundColor(StockChatTheme.backgroundMask)
                        touchEnable(false)
                    }
                }
                View {
                    attr {
                        absolutePosition(
                            top = pagerData.statusBarHeight + metrics.dp(66f),
                            left = 0f,
                            right = 0f,
                            bottom = metrics.composerContentBottom(
                                metrics.composerBottomInset(
                                    ctx.keyboardHeight,
                                    pagerData.safeAreaInsets.bottom,
                                ),
                                ctx.composerExpanded,
                                ctx.voiceMode,
                                ctx.selectedImageCount > 0,
                                ctx.composerExtraInputLines(),
                            ) + (ctx.composerDockNudge % 2) * 0.1f,
                        )
                        animate(Animation.easeOut(ctx.keyboardAnimDuration), ctx.keyboardHeight)
                        animate(Animation.easeOut(0.2f), ctx.composerExpanded)
                    }
                    vif({ ctx.homeState.chatStage == StockChatHomeChatStage.WELCOME }) {
                        ctx.HomeContentLayer(this)
                    }
                    vif({ ctx.homeState.chatStage == StockChatHomeChatStage.CONVERSATION }) {
                        ctx.MessageList(this)
                    }
                }
                vif({ ctx.homeState.chatStage == StockChatHomeChatStage.CONVERSATION }) {
                    View {
                        attr {
                            absolutePosition(
                                left = 0f,
                                right = 0f,
                                bottom = metrics.composerContentBottom(
                                    metrics.composerBottomInset(
                                        ctx.keyboardHeight,
                                        pagerData.safeAreaInsets.bottom,
                                    ),
                                    ctx.composerExpanded,
                                    ctx.voiceMode,
                                    ctx.selectedImageCount > 0,
                                    ctx.composerExtraInputLines(),
                                ) + (ctx.composerDockNudge % 2) * 0.1f,
                            )
                            height(metrics.composerContentFadeHeight)
                            backgroundLinearGradient(
                                Direction.TO_BOTTOM,
                                ColorStop(Color(0x00000000), 0f),
                                ColorStop(StockChatTheme.chatBackgroundEnd, 1f),
                            )
                            touchEnable(false)
                            zIndex(5)
                            animate(Animation.easeOut(ctx.keyboardAnimDuration), ctx.keyboardHeight)
                            animate(Animation.easeOut(0.2f), ctx.composerExpanded)
                        }
                    }
                    ctx.ConversationHeader(this)
                }
            }
        }
    }

    // 胶囊的可见性、位置与动画时钟只依赖 HomeFlow 的单一派生展示态。
    private fun HomeTabCapsule(container: ViewContainer<*, *>) {
        val ctx = this
        val metrics = ctx.layoutMetrics
        with(container) {
            View {
                attr {
                    val presentation = ctx.homeState.capsulePresentation
                    val marketActive =
                        presentation == StockChatHomeCapsulePresentation.MARKET_BOTTOM
                    val visible = presentation != StockChatHomeCapsulePresentation.HIDDEN
                    val switcherWidth = metrics.dp(232f)
                    val switcherHeight = metrics.dp(44f)
                    val collapsedContentBottom = metrics.composerContentBottom(
                        pagerData.safeAreaInsets.bottom,
                        focused = false,
                    )
                    val heroTop = pagerData.statusBarHeight + metrics.dp(66f)
                    val heroBottom = pagerData.pageViewHeight -
                        collapsedContentBottom - metrics.dp(136f)
                    val heroCenter = (heroTop + heroBottom) / 2f
                    val suggestionTop = pagerData.pageViewHeight -
                        collapsedContentBottom - metrics.dp(52f)
                    val chatTop = minOf(
                        heroCenter + metrics.welcomeHeroSize / 2f + metrics.dp(68f),
                        suggestionTop - switcherHeight - metrics.dp(24f),
                    )
                    val marketTop = pagerData.pageViewHeight -
                        pagerData.safeAreaInsets.bottom - metrics.dp(14f) - switcherHeight
                    absolutePosition(
                        top = if (marketActive) marketTop else chatTop,
                        left = (pagerData.pageViewWidth - switcherWidth) / 2f,
                    )
                    width(switcherWidth)
                    height(switcherHeight)
                    touchEnable(visible && ctx.homeSceneInteractive)
                    zIndex(8)
                    animate(
                        Animation.springEaseOut(
                            HOME_CAPSULE_TRAVEL_DURATION,
                            0.92f,
                            0.16f,
                        ),
                        ctx.homeState.capsulePresentation.ordinal,
                    )
                }
                View {
                    attr {
                        val presentation = ctx.homeState.capsulePresentation
                        val marketActive =
                            presentation == StockChatHomeCapsulePresentation.MARKET_BOTTOM
                        val visible = presentation != StockChatHomeCapsulePresentation.HIDDEN
                        absolutePositionAllZero()
                        opacity(if (visible) 1f else 0f)
                        touchEnable(visible)
                        animate(
                            if (marketActive) {
                                Animation.easeOut(0.18f).delay(0.12f)
                            } else {
                                Animation.easeOut(0.14f)
                            },
                            ctx.homeState.capsulePresentation.ordinal,
                        )
                    }
                    event {
                        click { }
                    }
                    ctx.HomeTabSwitcher(
                        this,
                        widthDp = 232f,
                        elevated = true,
                        enabled = {
                            ctx.homeState.capsulePresentation !=
                                StockChatHomeCapsulePresentation.HIDDEN &&
                                ctx.homeSceneInteractive
                        },
                    )
                }
            }
        }
    }

    private fun requestTodayMarket(requestId: Int) {
        if (!::todayMarketDataSource.isInitialized) {
            return
        }
        todayMarketDataSource.load { result ->
            dispatchHome(
                StockChatHomeEvent.TodayMarketLoadCompleted(requestId, result)
            )
        }
    }

    // 聊天欢迎内容单独挂载；今日市场内容由 TodayMarketLayer 管理。
    private fun HomeContentLayer(container: ViewContainer<*, *>) {
        val ctx = this
        with(container) {
            View {
                attr {
                    absolutePositionAllZero()
                    overflow(true)
                    val visible = !ctx.homeState.welcomeObscured
                    visibility(visible)
                    touchEnable(visible)
                }
                ctx.WelcomeContent(this)
            }
        }
    }

    private fun DrawerMenuItem(
        container: ViewContainer<*, *>,
        iconAsset: String,
        label: String,
        scale: Float,
        onClick: () -> Unit,
    ) {
        with(container) {
            View {
                attr {
                    height(58f * scale)
                    flexDirectionRow()
                    alignItemsCenter()
                    marginTop(5f * scale)
                }
                event {
                    click { onClick() }
                }
                Image {
                    attr {
                        size(24f * scale, 24f * scale)
                        resizeContain()
                        src(ImageUri.commonAssets(iconAsset))
                        marginRight(18f * scale)
                    }
                }
                Text {
                    attr {
                        text(label)
                        fontSize(17f * scale)
                        fontWeightMedium()
                        color(StockChatTheme.textPrimary)
                        flex(1f)
                    }
                }
                Text {
                    attr {
                        text("›")
                        fontSize(26f * scale)
                        color(StockChatTheme.textTertiary)
                    }
                }
            }
        }
    }

    private fun DrawerConversation(
        container: ViewContainer<*, *>,
        session: ChatSessionSummary,
        scale: Float,
    ) {
        val ctx = this
        with(container) {
            View {
                attr {
                    padding(
                        top = 11f * scale,
                        left = 12f * scale,
                        bottom = 11f * scale,
                        right = 12f * scale,
                    )
                    flexDirectionRow()
                    alignItemsCenter()
                    borderRadius(14f * scale)
                    marginTop(5f * scale)
                    backgroundColor(
                        if (session.id == ctx.activeSessionId) {
                            StockChatTheme.accentSoft
                        } else {
                            Color(0x00000000)
                        }
                    )
                }
                event {
                    click {
                        if (!ctx.managingSessions) {
                            ctx.selectSession(session.id)
                        }
                    }
                }
                View {
                    attr {
                        flex(1f)
                    }
                    Text {
                        attr {
                            text(session.title.ifBlank { "新对话" })
                            fontSize(14f * scale)
                            fontWeightMedium()
                            color(StockChatTheme.textPrimary)
                            lines(1)
                        }
                    }
                    Text {
                        attr {
                            text("已保存到本地数据库")
                            fontSize(11f * scale)
                            color(StockChatTheme.textTertiary)
                            marginTop(4f * scale)
                        }
                    }
                }
                vif({ ctx.managingSessions }) {
                    View {
                        attr {
                            width(44f * scale)
                            height(36f * scale)
                            allCenter()
                        }
                        event {
                            click { ctx.openRenameDialog(session) }
                        }
                        Text {
                            attr {
                                text("编辑")
                                fontSize(12f * scale)
                                color(StockChatTheme.accent)
                            }
                        }
                    }
                    View {
                        attr {
                            width(44f * scale)
                            height(36f * scale)
                            allCenter()
                        }
                        event {
                            click { ctx.deleteSession(session.id) }
                        }
                        Text {
                            attr {
                                text("删除")
                                fontSize(12f * scale)
                                color(StockChatTheme.positive)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun MainLayer(container: ViewContainer<*, *>) {
        val ctx = this
        val metrics = ctx.layoutMetrics
        val drawerWidth = metrics.drawerWidth
        with(container) {
        View {
            attr {
                absolutePositionAllZero()
                backgroundColor(StockChatTheme.background)
                zIndex(2)
                transform(Translate(0f, 0f, if (ctx.drawerOpen) drawerWidth else 0f, 0f))
                animation(Animation.springEaseOut(0.38f, 0.9f, 0.2f), ctx.drawerOpen)
                capture(CaptureRule.pan(CaptureRuleDirection.HORIZONTAL))
            }
            event {
                pan { params -> ctx.handleDrawerPan(params) }
                // 点输入框以外的任意区域（顶栏/聊天区/欢迎区等，凡是没被子视图
                // 消费的点击都会冒泡到这里）：键盘弹起时先收键盘（面板保持展开）；
                // 键盘已收起时再点，面板才收缩还给页面空间。输入 dock 自己吞掉
                // 区域内的点击，不会冒泡到这
                click {
                    if (ctx.selectedHomeTab == HOME_TAB_CHAT) {
                        ctx.handleBlankAreaTap()
                    }
                }
            }
            ctx.ChatLayer(this)
            ctx.TodayMarketLayer(this)
            ctx.ConversationTopBar(this)
            ctx.HomeTabCapsule(this)
            ctx.ComposerDock(this)
            vif({ ctx.selectedHomeTab == HOME_TAB_CHAT }) {
                ctx.VoiceRecordingOverlay(this)
                ctx.MessageMenuOverlay(this)
                ctx.ModelMenuOverlay(this)
                ctx.ConversationMenuOverlay(this)
            }
            View {
                attr {
                    absolutePositionAllZero()
                    backgroundColor(Color(0xFF141A18))
                    opacity(if (ctx.drawerOpen) 0.34f else 0f)
                    touchEnable(ctx.drawerOpen)
                    zIndex(9)
                    animation(Animation.easeOut(0.3f), ctx.drawerOpen)
                    capture(CaptureRule.pan(CaptureRuleDirection.HORIZONTAL))
                }
                event {
                    click { ctx.closeDrawer() }
                    pan { params -> ctx.handleDrawerPan(params) }
                }
            }
        }
        }
    }

    private fun ConversationTopBar(container: ViewContainer<*, *>) {
        val ctx = this
        val metrics = ctx.layoutMetrics
        with(container) {
            View {
                attr {
                    val interactive = ctx.selectedHomeTab == HOME_TAB_CHAT ||
                        ctx.selectedHomeTab == HOME_TAB_TODAY_MARKET
                    absolutePosition(
                        top = pagerData.statusBarHeight + metrics.dp(14f),
                        left = metrics.dp(18f),
                    )
                    touchEnable(interactive)
                    zIndex(8)
                }
                HamburgerButton(scale = metrics.scale) {
                    if (
                        ctx.selectedHomeTab != HOME_TAB_CHAT &&
                        ctx.selectedHomeTab != HOME_TAB_TODAY_MARKET
                    ) {
                        return@HamburgerButton
                    }
                    if (ctx::inputRef.isInitialized) {
                        ctx.inputRef.view?.blur()
                    }
                    ctx.openDrawer()
                }
            }
        }
    }

    private fun ConversationHeader(container: ViewContainer<*, *>) {
        val ctx = this
        val metrics = ctx.layoutMetrics
        with(container) {
            View {
                attr {
                    absolutePosition(
                        top = pagerData.statusBarHeight + metrics.dp(14f),
                        left = metrics.dp(78f),
                        right = metrics.dp(145f),
                    )
                    height(metrics.dp(52f))
                    allCenter()
                    zIndex(4)
                }
                Text {
                    attr {
                        text(ctx.conversationTitle())
                        fontSize(metrics.dp(20f))
                        fontWeightMedium()
                        color(StockChatTheme.textPrimary)
                        textAlignCenter()
                        // 给定确定宽度并限单行，长标题截断显示，避免溢出遮挡两侧按钮
                        width(pagerData.pageViewWidth - metrics.dp(78f) - metrics.dp(145f))
                        lines(1)
                    }
                }
            }
            View {
                attr {
                    absolutePosition(
                        top = pagerData.statusBarHeight + metrics.dp(14f),
                        right = metrics.dp(18f),
                    )
                    width(metrics.dp(127f))
                    height(metrics.dp(52f))
                    borderRadius(metrics.dp(26f))
                    backgroundColor(StockChatTheme.surface)
                    boxShadow(
                        BoxShadow(
                            metrics.dp(1f),
                            metrics.dp(5f),
                            metrics.dp(14f),
                            Color(0x1A000000),
                        )
                    )
                    flexDirectionRow()
                    alignItemsCenter()
                    justifyContentSpaceAround()
                    padding(left = metrics.dp(7f), right = metrics.dp(7f))
                    zIndex(5)
                }
                View {
                    attr {
                        size(metrics.dp(44f), metrics.dp(44f))
                        allCenter()
                    }
                    event {
                        click {
                            if (ctx.selectedHomeTab == HOME_TAB_CHAT) {
                                ctx.startNewChat()
                            }
                        }
                    }
                    ctx.NewConversationMark(this, metrics.scale)
                }
                View {
                    attr {
                        size(metrics.dp(44f), metrics.dp(44f))
                        allCenter()
                    }
                    event {
                        click {
                            if (ctx.selectedHomeTab == HOME_TAB_CHAT) {
                                ctx.openConversationMenu()
                            }
                        }
                    }
                    ctx.MoreMark(this, metrics.scale)
                }
            }
        }
    }

    private fun NewConversationMark(container: ViewContainer<*, *>, scale: Float) {
        with(container) {
            View {
                attr {
                    size(30f * scale, 30f * scale)
                    borderRadius(15f * scale)
                    border(Border(3f * scale, BorderStyle.SOLID, StockChatTheme.textPrimary))
                    allCenter()
                }
                View {
                    attr {
                        size(14f * scale, 3f * scale)
                        borderRadius(2f * scale)
                        backgroundColor(StockChatTheme.textPrimary)
                    }
                }
                View {
                    attr {
                        absolutePosition(top = 8.5f * scale, left = 13.5f * scale)
                        size(3f * scale, 14f * scale)
                        borderRadius(2f * scale)
                        backgroundColor(StockChatTheme.textPrimary)
                    }
                }
                View {
                    attr {
                        absolutePosition(left = 1f * scale, bottom = -1f * scale)
                        size(10f * scale, 3f * scale)
                        borderRadius(2f * scale)
                        backgroundColor(StockChatTheme.textPrimary)
                    }
                }
            }
        }
    }

    private fun MoreMark(container: ViewContainer<*, *>, scale: Float) {
        with(container) {
            View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                    justifyContentCenter()
                }
                repeat(3) {
                    View {
                        attr {
                            size(5f * scale, 5f * scale)
                            borderRadius(3f * scale)
                            backgroundColor(StockChatTheme.textPrimary)
                            margin(left = 3f * scale, right = 3f * scale)
                        }
                    }
                }
            }
        }
    }

    private fun WelcomeContent(container: ViewContainer<*, *>) {
        val ctx = this
        val metrics = ctx.layoutMetrics
        with(container) {
            View {
                attr {
                    absolutePositionAllZero()
                }
                View {
                    attr {
                        absolutePositionAllZero()
                    }
                    View {
                        attr {
                            absolutePosition(
                                top = 0f,
                                left = 0f,
                                right = 0f,
                                bottom = metrics.dp(136f),
                            )
                            alignItemsCenter()
                            justifyContentCenter()
                            padding(left = metrics.dp(24f), right = metrics.dp(24f))
                        }
                        // 主视觉只放图形 logo，品牌名交给标题说一次，避免与横排 wordmark 重复
                        Image {
                            attr {
                                size(metrics.welcomeHeroSize, metrics.welcomeHeroSize)
                                resizeContain()
                                src(ImageUri.commonAssets("stockchat_app_icon.png"))
                                opacity(if (ctx.homeState.welcomeObscured) 0f else 1f)
                                animate(
                                    Animation.easeOut(0.2f),
                                    ctx.homeState.welcomeObscured,
                                )
                            }
                        }
                        Text {
                            attr {
                                text("StockChat，我帮你看行情")
                                fontSize(metrics.dp(26f))
                                fontWeightBold()
                                color(StockChatTheme.textPrimary)
                                textAlignCenter()
                                marginTop(metrics.dp(26f))
                                opacity(if (ctx.homeState.welcomeObscured) 0f else 1f)
                                animate(
                                    Animation.easeOut(0.2f),
                                    ctx.homeState.welcomeObscured,
                                )
                            }
                        }
                        Text {
                            attr {
                                text("支持查行情、学炒股，也可以直接问其他问题")
                                fontSize(metrics.dp(12f))
                                color(StockChatTheme.textTertiary)
                                textAlignCenter()
                                marginTop(metrics.dp(12f))
                                opacity(if (ctx.homeState.welcomeObscured) 0f else 1f)
                                animate(
                                    Animation.easeOut(0.2f),
                                    ctx.homeState.welcomeObscured,
                                )
                            }
                        }
                    }
                    ctx.SuggestionCardRow(this)
                }
            }
        }
    }

    private fun SuggestionCardRow(container: ViewContainer<*, *>) {
        val ctx = this
        val metrics = ctx.layoutMetrics
        with(container) {
            View {
                attr {
                    absolutePosition(
                        left = 0f,
                        right = 0f,
                        bottom = metrics.dp(6f),
                    )
                    height(metrics.dp(46f))
                }
                Scroller {
                    attr {
                        absolutePositionAllZero()
                        flexDirectionRow()
                        alignItemsCenter()
                        showScrollerIndicator(false)
                        bouncesEnable(true)
                        // 推荐 chip 自己消费横向手势，避免触发页面级 drawer 滑动
                        capture(CaptureRule.pan(CaptureRuleDirection.HORIZONTAL))
                        padding(left = metrics.dp(18f), right = metrics.dp(8f))
                    }
                    WELCOME_SUGGESTIONS.forEach { suggestion ->
                        View {
                            attr {
                                height(metrics.dp(40f))
                                borderRadius(metrics.dp(20f))
                                backgroundColor(StockChatTheme.surface)
                                border(Border(1f, BorderStyle.SOLID, StockChatTheme.border))
                                flexDirectionRow()
                                alignItemsCenter()
                                padding(left = metrics.dp(13f), right = metrics.dp(15f))
                                marginRight(metrics.dp(9f))
                                opacity(if (ctx.homeState.welcomeObscured) 0f else 1f)
                                animate(
                                    Animation.easeOut(0.2f),
                                    ctx.homeState.welcomeObscured,
                                )
                            }
                            event {
                                click {
                                    if (ctx.selectedHomeTab == HOME_TAB_CHAT) {
                                        ctx.sendMessage(
                                            suggestion.text,
                                            StockChatQuestionSource.WELCOME_SUGGESTION,
                                        )
                                    }
                                }
                            }
                            Image {
                                attr {
                                    size(metrics.dp(18f), metrics.dp(18f))
                                    resizeContain()
                                    src(ImageUri.commonAssets(suggestion.iconAsset))
                                    marginRight(metrics.dp(7f))
                                }
                            }
                            Text {
                                attr {
                                    text(suggestion.text)
                                    fontSize(metrics.dp(14f))
                                    fontWeightMedium()
                                    color(StockChatTheme.textPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun MessageList(container: ViewContainer<*, *>) {
        val ctx = this
        with(container) {
        Scroller {
            ref {
                ctx.messageScrollerRef = it
            }
            attr {
                absolutePositionAllZero()
                showScrollerIndicator(false)
                bouncesEnable(true)
                // 顶部不留 padding，内容直接从顶栏下缘开始；
                // 底部留出渐隐区高度，滚到底时最后一条消息不会停在淡出区内
                padding(bottom = ctx.layoutMetrics.composerContentFadeHeight + 8f)
            }
            event {
                // 滚动容器会消费触摸，点击/拖动到不了根容器的空白点击处理，
                // 在这里单独接上：点消息区空白或开始拖动列表都视作离开输入
                click {
                    if (ctx.selectedHomeTab == HOME_TAB_CHAT) {
                        ctx.handleBlankAreaTap()
                    }
                }
                dragBegin {
                    if (ctx.selectedHomeTab == HOME_TAB_CHAT) {
                        ctx.handleBlankAreaTap()
                    }
                }
                scroll { params ->
                    val remaining = params.contentHeight - params.offsetY - params.viewHeight
                    ctx.messageListNearBottom = remaining <= 48f
                    if (!ctx.messageListNearBottom) {
                        ctx.stickMessageListToBottom = false
                    }
                }
                contentSizeChanged { _, contentHeight ->
                    if (ctx.stickMessageListToBottom || ctx.messageListNearBottom) {
                        if (ctx::messageScrollerRef.isInitialized) {
                            ctx.messageScrollerRef.view?.setContentOffset(0f, contentHeight, true)
                        }
                    }
                }
            }
            vfor({ ctx.messages }) { message ->
                    ChatMessageItem(
                        message = message,
                        scale = ctx.layoutMetrics.scale,
                        isFirst = message.id == ctx.messages.firstOrNull()?.id,
                        typingPhase = { ctx.typingDotPhase },
                        onQuoteClick = {
                            if (ctx.selectedHomeTab == HOME_TAB_CHAT) {
                                ctx.openStockDetail(
                                    it,
                                    HOME_TAB_CHAT,
                                )
                            }
                        },
                        onImageClick = {
                            if (ctx.selectedHomeTab == HOME_TAB_CHAT) {
                                ctx.openImagePreview(it)
                            }
                        },
                        onRetry = {
                            if (ctx.selectedHomeTab == HOME_TAB_CHAT) {
                                ctx.retryMessage(it)
                            }
                        },
                        onCopy = {
                            if (ctx.selectedHomeTab == HOME_TAB_CHAT) {
                                ctx.copyMessage(it)
                            }
                        },
                        onCopySelection = {
                            if (ctx.selectedHomeTab == HOME_TAB_CHAT) {
                                ctx.copySelectedText(it)
                            }
                        },
                        onRegenerate = {
                            if (ctx.selectedHomeTab == HOME_TAB_CHAT) {
                                ctx.regenerateMessage(it)
                            }
                        },
                        onReadAloud = {
                            if (ctx.selectedHomeTab == HOME_TAB_CHAT) {
                                ctx.readMessageAloud(it)
                            }
                        },
                        readAloudPhase = {
                            if (ctx.readAloudMessageId == message.id) ctx.readAloudWavePhase else -1
                        },
                        onMore = {
                            if (ctx.selectedHomeTab == HOME_TAB_CHAT) {
                                ctx.openMessageMenu(it.id)
                            }
                        },
                    )
                }
        }
        }
    }

    private fun ComposerDock(container: ViewContainer<*, *>) {
        val ctx = this
        val metrics = ctx.layoutMetrics
        with(container) {
        View {
            attr {
                val active = ctx.selectedHomeTab == HOME_TAB_CHAT
                val entering = active && ctx.homeSceneEnterReady
                val exiting = ctx.homeSceneOutgoingTab == HOME_TAB_CHAT &&
                    !ctx.homeSceneEnterReady
                val effectiveInset = metrics.composerBottomInset(
                    ctx.keyboardHeight,
                    pagerData.safeAreaInsets.bottom,
                )
                absolutePosition(
                    left = metrics.dp(18f),
                    right = metrics.dp(18f),
                    // nudge 项：键盘关闭后强制归位用的 0.1px 无感偏移
                    bottom = effectiveInset + metrics.composerBottomGap +
                        (ctx.composerDockNudge % 2) * 0.1f,
                )
                height(
                    metrics.composerDockHeight(
                        focused = ctx.composerExpanded,
                        voiceMode = ctx.voiceMode,
                        hasAttachments = ctx.selectedImageCount > 0,
                        extraInputLines = ctx.composerExtraInputLines(),
                    )
                )
                visibility(active || exiting)
                opacity(if (entering) 1f else 0f)
                transform(
                    Translate(
                        0f,
                        0f,
                        0f,
                        if (entering) 0f else metrics.dp(30f),
                    )
                )
                touchEnable(entering && ctx.homeSceneInteractive)
                zIndex(if (entering || exiting) 6 else 0)
                // 展开态与键盘态解耦：键盘回落期间展开态不变，回落动画不会被
                // 无动画的几何更新打断；收缩只发生在键盘静止时，两条动画不并发
                animate(Animation.easeOut(ctx.keyboardAnimDuration), ctx.keyboardHeight)
                animate(Animation.easeOut(0.2f), ctx.composerExpanded)
                animate(
                    if (entering) {
                        Animation.springEaseOut(
                            HOME_COMPOSER_ENTER_DURATION,
                            0.88f,
                            0.18f,
                        )
                    } else {
                        Animation.easeOut(HOME_SCENE_EXIT_DURATION)
                    },
                    ctx.homeSceneAnimationPhase,
                )
            }
            event {
                // 吃掉输入区内未被子视图消费的点击，避免冒泡到根容器被当成
                // 空白区域点击而收缩面板
                click { }
            }
            View {
                attr {
                    absolutePosition(
                        left = 0f,
                        right = 0f,
                        bottom = metrics.composerFooterHeight,
                    )
                    height(
                        metrics.composerPanelHeight(
                            focused = ctx.composerExpanded,
                            voiceMode = ctx.voiceMode,
                            hasAttachments = ctx.selectedImageCount > 0,
                            extraInputLines = ctx.composerExtraInputLines(),
                        )
                    )
                    borderRadius(
                        metrics.dp(
                            if (ctx.composerExpanded || ctx.voiceMode || ctx.selectedImageCount > 0) {
                                24f
                            } else {
                                30f
                            }
                        )
                    )
                    backgroundColor(StockChatTheme.surface)
                    boxShadow(
                        BoxShadow(
                            metrics.dp(1f),
                            metrics.dp(5f),
                            metrics.dp(14f),
                            Color(0x1A000000),
                        )
                    )
                    animate(Animation.easeOut(0.2f), ctx.composerExpanded)
                    animate(Animation.easeOut(0.2f), ctx.voiceMode)
                    animate(Animation.easeOut(0.2f), ctx.selectedImageCount)
                    animate(Animation.easeInOut(0.18f), ctx.inputLineCount)
                }
                vif({ ctx.selectedImageCount > 0 }) {
                    View {
                        attr {
                            absolutePosition(
                                top = metrics.dp(9f),
                                left = metrics.dp(14f),
                                right = metrics.dp(14f),
                            )
                            height(metrics.dp(70f))
                            zIndex(5)
                        }
                        ComposerImageAttachments(
                            images = { ctx.selectedImages },
                            scale = metrics.scale,
                            onRemove = {
                                if (ctx.selectedHomeTab == HOME_TAB_CHAT) {
                                    ctx.removeSelectedImage(it)
                                }
                            },
                            onPreview = {
                                if (ctx.selectedHomeTab == HOME_TAB_CHAT) {
                                    ctx.openImagePreview(it)
                                }
                            },
                        )
                    }
                }
                TextArea {
                        ref {
                            ctx.inputRef = it
                        }
                        attr {
                            val hasAttachments = ctx.selectedImageCount > 0
                            val expanded = ctx.composerExpanded || ctx.voiceMode || hasAttachments
                            val attachmentOffset = if (hasAttachments) {
                                metrics.composerAttachmentStripHeight
                            } else {
                                0f
                            }
                            absolutePosition(
                                // 折叠态：面板高 68dp，按钮行居中后按钮中心在 34dp（即面板中心）；
                                // 文字行高 23dp 且从 TextArea 顶部绘制，top 取 (68 - 23) / 2 = 22.5，
                                // 让占位文字与加号、语音按钮一起垂直居中
                                top = attachmentOffset + if (expanded) metrics.dp(14f) else metrics.dp(22.5f),
                                left = metrics.dp(if (expanded) 20f else 61f),
                                right = metrics.dp(if (expanded) 20f else 60f),
                            )
                            // 单行高 38dp（23 行高 + 15 上下留白）；超过一行后按行数撑高，面板同步增高
                            val visibleLines = if (expanded && !ctx.voiceMode) {
                                ctx.inputLineCount
                            } else {
                                1
                            }
                            height(metrics.dp(15f) + metrics.composerInputLineHeight * visibleLines)
                            text(ctx.inputText)
                            fontSize(metrics.dp(17f))
                            lineHeight(metrics.dp(23f))
                            color(
                                if (ctx.voiceMode) Color(0x00000000) else StockChatTheme.textPrimary
                            )
                            tintColor(
                                if (ctx.voiceMode) Color(0x00000000) else StockChatTheme.accent
                            )
                            placeholder(
                                if (ctx.composerExpanded) {
                                    "问行情、炒股知识或其他问题…"
                                } else {
                                    ctx.voiceInputHint()
                                }
                            )
                            placeholderColor(
                                if (ctx.voiceMode) Color(0x00000000) else StockChatTheme.textTertiary
                            )
                            returnKeyTypeSend()
                            enablesReturnKeyAutomatically(true)
                            maxTextLength(300)
                            // 展开未聚焦时也可点：直接点输入区域原生聚焦拉起键盘
                            touchEnable(
                                ctx.selectedHomeTab == HOME_TAB_CHAT &&
                                    ctx.composerExpanded && !ctx.voiceMode
                            )
                            zIndex(2)
                            animate(Animation.easeOut(0.2f), ctx.composerExpanded)
                            animate(Animation.easeOut(0.2f), ctx.voiceMode)
                            animate(Animation.easeOut(0.2f), ctx.selectedImageCount)
                            animate(Animation.easeInOut(0.18f), ctx.inputLineCount)
                        }
                        event {
                            textDidChange(isSyncEdit = true) {
                                ctx.inputText = it.text
                                ctx.updateInputLineMetrics(it.text)
                            }
                            inputFocus {
                                ctx.inputText = it.text
                                if (ctx.selectedHomeTab != HOME_TAB_CHAT) {
                                    ctx.composerFocused = false
                                    setTimeout(0) {
                                        if (
                                            ctx.selectedHomeTab != HOME_TAB_CHAT &&
                                            ctx::inputRef.isInitialized
                                        ) {
                                            ctx.inputRef.view?.blur()
                                        }
                                    }
                                    return@inputFocus
                                }
                                ctx.closeDrawer()
                                if (ctx.voiceMode) {
                                    // 语音模式下输入框不应持有焦点（如长按触发录音前后的异常聚焦回调），
                                    // 立即交还焦点，避免焦点态与语音态叠加导致按钮行显示错乱
                                    setTimeout(0) {
                                        if (ctx.voiceMode && ctx::inputRef.isInitialized) {
                                            ctx.inputRef.view?.blur()
                                        }
                                    }
                                    return@inputFocus
                                }
                                ctx.composerFocused = true
                                ctx.composerExpanded = true
                                ctx.collapseComposerAfterSettle = false
                                ctx.updateInputLineMetrics(it.text)
                            }
                            inputBlur {
                                ctx.inputText = it.text
                                ctx.resetKeyboardState()
                            }
                            keyboardHeightChange {
                                // 安卓上 duration 常回调为 0，直接沿用会导致面板瞬移没有过渡；
                                // 无有效时长时用 0.25s 兜底，有则归一并限制在合理区间
                                ctx.keyboardAnimDuration = when {
                                    it.duration <= 0.01f -> DEFAULT_KEYBOARD_ANIM_DURATION
                                    it.duration > 3f -> it.duration / 1000f
                                    else -> it.duration
                                }.coerceIn(0.15f, 0.35f)
                                val nextKeyboardHeight = maxOf(it.height, 0f)
                                val nextKeyboardVisible = nextKeyboardHeight > 0.5f
                                val keyboardWasVisible = ctx.keyboardHeight > 0f
                                if (!ctx.composerFocused || ctx.voiceMode || ctx.imagePickerOpen) {
                                    ctx.resetKeyboardState()
                                } else if (nextKeyboardVisible) {
                                    ctx.keyboardVisible = true
                                    ctx.keyboardHeight = nextKeyboardHeight
                                } else {
                                    ctx.keyboardHeight = 0f
                                    ctx.keyboardVisible = false
                                    if (keyboardWasVisible) {
                                        // 键盘回落只退出聚焦态，面板保持展开（composerExpanded
                                        // 不变），点空白区域才收缩，见 handleBlankAreaTap
                                        ctx.beginComposerDockSettle()
                                        ctx.composerFocused = false
                                        if (ctx::inputRef.isInitialized) {
                                            ctx.inputRef.view?.blur()
                                        }
                                    }
                                }
                            }
                            inputReturn {
                                if (ctx.selectedHomeTab == HOME_TAB_CHAT) {
                                    ctx.sendMessage(it.text)
                                }
                            }
                        }
                }
                vif({ ctx.voiceMode }) {
                    Text {
                        attr {
                            val attachmentOffset = if (ctx.selectedImageCount > 0) {
                                metrics.composerAttachmentStripHeight
                            } else {
                                0f
                            }
                            absolutePosition(
                                top = attachmentOffset + metrics.dp(22f),
                                left = metrics.dp(56f),
                                right = metrics.dp(56f),
                            )
                            height(metrics.dp(32f))
                            text(ctx.voiceModePrompt())
                            textAlignCenter()
                            fontSize(metrics.dp(18f))
                            fontWeightMedium()
                            color(
                                if (ctx.voicePressCanceled) {
                                    StockChatTheme.warning
                                } else {
                                    StockChatTheme.textPrimary
                                }
                            )
                            zIndex(4)
                            animate(Animation.easeOut(0.2f), ctx.voiceMode)
                            animate(Animation.easeOut(0.2f), ctx.selectedImageCount)
                            animate(Animation.easeInOut(0.18f), ctx.inputLineCount)
                        }
                    }
                }
                View {
                    attr {
                        val hasAttachments = ctx.selectedImageCount > 0
                        val attachmentOffset = if (hasAttachments) metrics.composerAttachmentStripHeight else 0f
                        absolutePosition(
                            top = attachmentOffset,
                            left = metrics.dp(51f),
                            right = metrics.dp(51f),
                            bottom = if (ctx.voiceMode || hasAttachments) metrics.dp(52f) else 0f,
                        )
                        touchEnable(
                            ctx.selectedHomeTab == HOME_TAB_CHAT && !ctx.composerFocused
                        )
                        zIndex(3)
                        // 常驻捕获长按：是否真正进入按住说话在事件回调里实时判断，
                        // 避免 attr 依赖 composerFocused/inputText 等未注册动画的键
                        // 触发重跑打断本节点在飞动画
                        capture(CaptureRule.longPress())
                        animate(Animation.easeOut(0.2f), ctx.voiceMode)
                        animate(Animation.easeOut(0.2f), ctx.selectedImageCount)
                    }
                    event {
                        click {
                            // 长按松手后可能补发 click，语音流程未回到 IDLE 时不聚焦
                            if (
                                !ctx.voiceMode &&
                                !ctx.voicePressActive &&
                                ctx.voiceInputState == VoiceInputState.IDLE
                            ) {
                                ctx.focusComposer()
                            }
                        }
                        longPress { params ->
                            if (
                                ctx.voiceMode ||
                                ctx.voicePressActive ||
                                ctx.composerHoldToTalkReady()
                            ) {
                                ctx.handleVoiceLongPress(params)
                            }
                        }
                        touchCancel {
                            if (ctx.voiceMode || ctx.voicePressActive) {
                                ctx.cancelVoicePress()
                            }
                        }
                        touchUp {
                            if (ctx.voiceMode || ctx.voicePressActive) {
                                ctx.finishVoicePress()
                            }
                        }
                    }
                }
                View {
                    attr {
                        val expanded = ctx.composerExpanded ||
                            ctx.voiceMode ||
                            ctx.selectedImageCount > 0
                        absolutePosition(
                            left = metrics.dp(14f),
                            right = metrics.dp(14f),
                            // 折叠态：面板高 68dp、行高 42dp，bottom 取 (68 - 42) / 2 = 13
                            // 让按钮行在面板内垂直居中；展开态贴底部
                            bottom = metrics.dp(if (expanded) 8f else 13f),
                        )
                        height(metrics.dp(42f))
                        flexDirectionRow()
                        alignItemsCenter()
                        zIndex(6)
                        animate(Animation.easeOut(0.2f), ctx.composerExpanded)
                        animate(Animation.easeOut(0.2f), ctx.voiceMode)
                    }
                    View {
                        attr {
                            size(metrics.dp(34f), metrics.dp(34f))
                            allCenter()
                        }
                        ctx.InputModeMark(this, metrics.scale) {
                            if (ctx.selectedHomeTab == HOME_TAB_CHAT) {
                                ctx.toggleVoiceMode()
                            }
                        }
                    }
                    View {
                        attr {
                            val showModel = ctx.composerExpanded ||
                                ctx.voiceMode ||
                                ctx.selectedImageCount > 0
                            width(metrics.dp(104f))
                            height(metrics.dp(34f))
                            borderRadius(metrics.dp(17f))
                            marginLeft(metrics.dp(10f))
                            padding(left = metrics.dp(10f), right = metrics.dp(10f))
                            backgroundColor(StockChatTheme.recessed)
                            border(Border(1f, BorderStyle.SOLID, StockChatTheme.border))
                            flexDirectionRow()
                            alignItemsCenter()
                            justifyContentCenter()
                            opacity(if (showModel) 1f else 0f)
                            touchEnable(showModel)
                            animate(Animation.easeOut(0.2f), showModel)
                        }
                        event {
                            click {
                                val showModelNow = ctx.composerExpanded ||
                                    ctx.voiceMode ||
                                    ctx.selectedImageCount > 0
                                if (ctx.selectedHomeTab == HOME_TAB_CHAT && showModelNow) {
                                    ctx.openModelMenu()
                                }
                            }
                        }
                        Image {
                            attr {
                                size(metrics.dp(18f), metrics.dp(18f))
                                resizeContain()
                                src(ImageUri.commonAssets("tongyi-qianwen.png"))
                                marginRight(metrics.dp(6f))
                            }
                        }
                        Text {
                            attr {
                                text(ctx.selectedModel().displayName)
                                fontSize(metrics.dp(14f))
                                fontWeightBold()
                                color(StockChatTheme.textPrimary)
                            }
                        }
                    }
                    View {
                        attr {
                            flex(1f)
                        }
                    }
                    View {
                        attr {
                            size(metrics.dp(34f), metrics.dp(34f))
                            allCenter()
                        }
                        ctx.PlusMark(this, metrics.scale) {
                            if (ctx.selectedHomeTab == HOME_TAB_CHAT) {
                                ctx.openImagePicker()
                            }
                        }
                    }
                    View {
                        attr {
                            val visible = !ctx.voiceMode && (ctx.composerExpanded || ctx.selectedImageCount > 0)
                            width(if (visible) metrics.dp(42f) else 0f)
                            height(metrics.dp(42f))
                            marginLeft(if (visible) metrics.dp(12f) else 0f)
                            opacity(if (visible) 1f else 0f)
                            touchEnable(visible)
                            animate(Animation.easeOut(0.22f), ctx.composerExpanded)
                            animate(Animation.easeOut(0.22f), ctx.voiceMode)
                            animate(Animation.easeOut(0.22f), ctx.selectedImageCount)
                        }
                        event {
                            click {
                                // 可见性必须在点击时实时判断：构建期缓存的布尔值会永远
                                // 停留在初始 false，导致按钮显示出来了却点不了
                                val visibleNow = !ctx.voiceMode &&
                                    (ctx.composerExpanded || ctx.selectedImageCount > 0)
                                if (visibleNow && ctx.selectedHomeTab == HOME_TAB_CHAT) {
                                    ctx.sendMessage()
                                }
                            }
                        }
                        View {
                            attr {
                                val canSend = !ctx.isSending && (ctx.inputText.isNotBlank() || ctx.selectedImageCount > 0)
                                size(metrics.dp(42f), metrics.dp(42f))
                                borderRadius(metrics.dp(21f))
                                backgroundColor(
                                    if (!canSend) {
                                        Color(0xFFE4EAE7)
                                    } else {
                                        StockChatTheme.accent
                                    }
                                )
                                allCenter()
                            }
                            Text {
                                attr {
                                    text("↑")
                                    fontSize(metrics.dp(23f))
                                    fontWeightBold()
                                    color(Color.WHITE)
                                }
                            }
                        }
                    }
                }
            }
            View {
                attr {
                    absolutePosition(
                        left = 0f,
                        right = 0f,
                        bottom = 0f,
                    )
                    height(metrics.composerFooterHeight)
                    justifyContentCenter()
                    alignItemsCenter()
                    opacity(if (ctx.composerExpanded) 0f else 1f)
                    animation(Animation.easeOut(0.14f), ctx.composerExpanded)
                }
                Text {
                    attr {
                        text("")
                        fontSize(metrics.dp(11f))
                        color(StockChatTheme.textTertiary)
                    }
                }
            }
        }
        }
    }

    private fun VoiceRecordingOverlay(container: ViewContainer<*, *>) {
        val ctx = this
        val metrics = ctx.layoutMetrics
        with(container) {
            vif({ ctx.voicePressActive }) {
                View {
                    attr {
                        absolutePosition(left = 0f, right = 0f, bottom = 0f)
                        height(metrics.dp(350f) + pagerData.safeAreaInsets.bottom)
                        backgroundLinearGradient(
                            Direction.TO_BOTTOM,
                            ColorStop(Color(0x00F6F7F4), 0f),
                            // 中段起完全不透明：输入面板顶缘约在遮罩 48% 处，往下必须实色盖住输入框
                            ColorStop(
                                if (ctx.voicePressCanceled) Color(0xFFE7B35A) else Color(0xFF43D7BB),
                                0.48f,
                            ),
                            ColorStop(
                                if (ctx.voicePressCanceled) Color(0xFFE2A13C) else Color(0xFF32C9AA),
                                1f,
                            ),
                        )
                        touchEnable(false)
                        zIndex(8)
                    }
                    Text {
                        attr {
                            absolutePosition(
                                top = metrics.dp(122f),
                                left = metrics.dp(24f),
                                right = metrics.dp(24f),
                            )
                            text(if (ctx.voicePressCanceled) "松手取消" else "松手发送，上移取消")
                            fontSize(metrics.dp(17f))
                            fontWeightMedium()
                            textAlignCenter()
                            color(StockChatTheme.textPrimary)
                        }
                    }
                    View {
                        attr {
                            absolutePosition(
                                top = metrics.dp(184f),
                                left = metrics.dp(22f),
                                right = metrics.dp(22f),
                            )
                            height(metrics.dp(58f))
                            flexDirectionRow()
                            alignItemsCenter()
                            justifyContentCenter()
                        }
                        repeat(30) { barIndex ->
                            View {
                                attr {
                                    val phase = ctx.voiceWavePhase.toFloat()
                                    val primary = kotlin.math.abs(
                                        kotlin.math.sin((barIndex * 0.58f + phase * 0.42f).toDouble())
                                    ).toFloat()
                                    val secondary = kotlin.math.abs(
                                        kotlin.math.sin((barIndex * 0.21f - phase * 0.31f).toDouble())
                                    ).toFloat()
                                    width(metrics.dp(4f))
                                    height(metrics.dp(10f + primary * 31f + secondary * 11f))
                                    borderRadius(metrics.dp(2f))
                                    backgroundColor(Color.WHITE)
                                    margin(left = metrics.dp(2f), right = metrics.dp(2f))
                                    animation(Animation.easeInOut(0.12f), ctx.voiceWavePhase)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 消息「更多」底部弹出菜单：暗色遮罩 + 白色圆角面板，点击遮罩或取消关闭。
    // 常驻挂载（不用 vif）以支持开合过渡动画：遮罩淡入淡出、面板自底部滑入滑出
    private fun MessageMenuOverlay(container: ViewContainer<*, *>) {
        val ctx = this
        val metrics = ctx.layoutMetrics
        with(container) {
            View {
                attr {
                    val open = ctx.messageMenuTargetId.isNotEmpty()
                    absolutePositionAllZero()
                    backgroundColor(Color(0x8C141A18))
                    opacity(if (open) 1f else 0f)
                    touchEnable(open)
                    zIndex(11)
                    animation(Animation.easeOut(0.24f), ctx.messageMenuTargetId)
                }
                event {
                    click { ctx.messageMenuTargetId = "" }
                }
            }
            View {
                attr {
                    val open = ctx.messageMenuTargetId.isNotEmpty()
                    absolutePosition(left = 0f, right = 0f, bottom = 0f)
                    borderRadius(metrics.dp(22f), metrics.dp(22f), 0f, 0f)
                    backgroundColor(StockChatTheme.surface)
                    padding(bottom = pagerData.safeAreaInsets.bottom + metrics.dp(6f))
                    // 关闭态整体下移自身高度藏到屏幕外，开合切换即形成滑入滑出
                    transform(Translate(0f, if (open) 0f else 1f))
                    touchEnable(open)
                    zIndex(12)
                    animation(Animation.easeOut(0.28f), ctx.messageMenuTargetId)
                }
                ctx.MessageMenuItem(this, "复制内容", divider = false) { target ->
                    ctx.copyMessage(target)
                }
                ctx.MessageMenuItem(this, "重新生成") { target ->
                    ctx.regenerateMessage(target)
                }
                ctx.MessageMenuItem(this, "朗读") { target ->
                    ctx.readMessageAloud(target)
                }
                ctx.MessageMenuItem(this, "分享") { target ->
                    ctx.shareMessage(target)
                }
                ctx.MessageMenuItem(
                    this,
                    "删除",
                    labelColor = StockChatTheme.positive,
                ) { target ->
                    ctx.deleteMessage(target)
                }
                View {
                    attr {
                        height(metrics.dp(8f))
                        backgroundColor(Color(0xFFF2F3F1))
                    }
                }
                View {
                    attr {
                        height(metrics.dp(58f))
                        allCenter()
                    }
                    event {
                        click { ctx.messageMenuTargetId = "" }
                    }
                    Text {
                        attr {
                            text("取消")
                            fontSize(metrics.dp(18f))
                            fontWeightBold()
                            color(StockChatTheme.textPrimary)
                        }
                    }
                }
            }
        }
    }

    private fun MessageMenuItem(
        container: ViewContainer<*, *>,
        label: String,
        labelColor: Color = StockChatTheme.textPrimary,
        divider: Boolean = true,
        onClick: (ChatMessage) -> Unit,
    ) {
        val ctx = this
        val metrics = ctx.layoutMetrics
        with(container) {
            if (divider) {
                View {
                    attr {
                        height(0.7f)
                        backgroundColor(StockChatTheme.border)
                    }
                }
            }
            View {
                attr {
                    height(metrics.dp(58f))
                    allCenter()
                }
                event {
                    click {
                        val target = ctx.messages.firstOrNull { it.id == ctx.messageMenuTargetId }
                        ctx.messageMenuTargetId = ""
                        if (target != null) {
                            onClick(target)
                        }
                    }
                }
                Text {
                    attr {
                        text(label)
                        fontSize(metrics.dp(18f))
                        color(labelColor)
                    }
                }
            }
        }
    }

    // 模型选择底部弹层：与消息菜单一样常驻挂载，遮罩淡入淡出、面板自底部滑入滑出
    private fun ModelMenuOverlay(container: ViewContainer<*, *>) {
        val ctx = this
        val metrics = ctx.layoutMetrics
        with(container) {
            View {
                attr {
                    absolutePositionAllZero()
                    backgroundColor(Color(0x7A141A18))
                    opacity(if (ctx.modelMenuOpen) 1f else 0f)
                    touchEnable(ctx.modelMenuOpen)
                    zIndex(13)
                    animation(Animation.easeOut(0.24f), ctx.modelMenuOpen)
                }
                event {
                    click { ctx.closeModelMenu() }
                }
            }
            View {
                attr {
                    absolutePosition(left = 0f, right = 0f, bottom = 0f)
                    height(metrics.dp(466f) + pagerData.safeAreaInsets.bottom)
                    borderRadius(metrics.dp(28f), metrics.dp(28f), 0f, 0f)
                    backgroundColor(StockChatTheme.surface)
                    padding(
                        left = metrics.dp(20f),
                        right = metrics.dp(20f),
                        bottom = pagerData.safeAreaInsets.bottom + metrics.dp(12f),
                    )
                    transform(Translate(0f, if (ctx.modelMenuOpen) 0f else 1f))
                    touchEnable(ctx.modelMenuOpen)
                    zIndex(14)
                    animation(Animation.easeOut(0.28f), ctx.modelMenuOpen)
                }
                View {
                    attr {
                        width(metrics.dp(44f))
                        height(metrics.dp(5f))
                        borderRadius(metrics.dp(3f))
                        backgroundColor(StockChatTheme.borderStrong)
                        alignSelfCenter()
                        marginTop(metrics.dp(12f))
                    }
                }
                Text {
                    attr {
                        text("选择模型")
                        fontSize(metrics.dp(22f))
                        fontWeightBold()
                        color(StockChatTheme.textPrimary)
                        textAlignCenter()
                        marginTop(metrics.dp(20f))
                        marginBottom(metrics.dp(10f))
                    }
                }
                ctx.chatModelOptions.forEach { option ->
                    ctx.ModelMenuItem(this, option)
                }
                Text {
                    attr {
                        text("模型选择会同步到设置，并影响后续回答")
                        fontSize(metrics.dp(11f))
                        color(StockChatTheme.textTertiary)
                        textAlignCenter()
                        marginTop(metrics.dp(8f))
                    }
                }
            }
        }
    }

    private fun ConversationMenuOverlay(container: ViewContainer<*, *>) {
        val ctx = this
        val metrics = ctx.layoutMetrics
        with(container) {
            vif({ ctx.conversationMenuOpen }) {
                View {
                    attr {
                        absolutePositionAllZero()
                        backgroundColor(Color(0x00000000))
                        zIndex(15)
                    }
                    event {
                        click { ctx.closeConversationMenu() }
                    }
                }
                View {
                    attr {
                        absolutePosition(
                            top = pagerData.statusBarHeight + metrics.dp(76f),
                            right = metrics.dp(18f),
                        )
                        width(metrics.dp(220f))
                        borderRadius(metrics.dp(22f))
                        backgroundColor(StockChatTheme.surface)
                        padding(all = metrics.dp(8f))
                        boxShadow(
                            BoxShadow(
                                metrics.dp(1f),
                                metrics.dp(8f),
                                metrics.dp(24f),
                                Color(0x26000000),
                            )
                        )
                        zIndex(16)
                    }
                    View {
                        attr {
                            height(metrics.dp(64f))
                            borderRadius(metrics.dp(16f))
                            flexDirectionRow()
                            alignItemsCenter()
                            padding(left = metrics.dp(16f), right = metrics.dp(12f))
                        }
                        event {
                            click { ctx.createConversationStockComparison() }
                        }
                        View {
                            attr {
                                size(metrics.dp(42f), metrics.dp(42f))
                                borderRadius(metrics.dp(13f))
                                backgroundColor(StockChatTheme.accentSoft)
                                allCenter()
                            }
                            Image {
                                attr {
                                    size(metrics.dp(23f), metrics.dp(23f))
                                    resizeContain()
                                    src(ImageUri.commonAssets("table_icon.png"))
                                }
                            }
                        }
                        View {
                            attr {
                                flex(1f)
                                marginLeft(metrics.dp(12f))
                            }
                            Text {
                                attr {
                                    text("会话标的对比")
                                    fontSize(metrics.dp(17f))
                                    fontWeightMedium()
                                    color(StockChatTheme.textPrimary)
                                }
                            }
                            Text {
                                attr {
                                    text("汇总会话全部股票")
                                    fontSize(metrics.dp(11f))
                                    color(StockChatTheme.textTertiary)
                                    marginTop(metrics.dp(2f))
                                }
                            }
                        }
                        Text {
                            attr {
                                text("›")
                                fontSize(metrics.dp(24f))
                                color(StockChatTheme.textTertiary)
                            }
                        }
                    }
                    View {
                        attr {
                            height(metrics.dp(1f))
                            backgroundColor(StockChatTheme.border)
                            margin(left = metrics.dp(16f), right = metrics.dp(16f))
                        }
                    }
                    View {
                        attr {
                            height(metrics.dp(64f))
                            borderRadius(metrics.dp(16f))
                            flexDirectionRow()
                            alignItemsCenter()
                            padding(left = metrics.dp(16f), right = metrics.dp(12f))
                        }
                        event {
                            click { ctx.createConversationMindMapArtifact() }
                        }
                        View {
                            attr {
                                size(metrics.dp(42f), metrics.dp(42f))
                                borderRadius(metrics.dp(13f))
                                backgroundColor(Color(0xFFEAF2FF))
                                allCenter()
                            }
                            Image {
                                attr {
                                    size(metrics.dp(23f), metrics.dp(23f))
                                    resizeContain()
                                    src(ImageUri.commonAssets("ranking_icon.png"))
                                }
                            }
                        }
                        View {
                            attr {
                                flex(1f)
                                marginLeft(metrics.dp(12f))
                            }
                            Text {
                                attr {
                                    text("思维导图")
                                    fontSize(metrics.dp(17f))
                                    fontWeightMedium()
                                    color(StockChatTheme.textPrimary)
                                }
                            }
                            Text {
                                attr {
                                    text("梳理当前对话")
                                    fontSize(metrics.dp(11f))
                                    color(StockChatTheme.textTertiary)
                                    marginTop(metrics.dp(2f))
                                }
                            }
                        }
                        Text {
                            attr {
                                text("›")
                                fontSize(metrics.dp(24f))
                                color(StockChatTheme.textTertiary)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun ModelMenuItem(
        container: ViewContainer<*, *>,
        option: ChatModelOption,
    ) {
        val ctx = this
        val metrics = ctx.layoutMetrics
        with(container) {
            View {
                attr {
                    height(metrics.dp(76f))
                    borderRadius(metrics.dp(18f))
                    flexDirectionRow()
                    alignItemsCenter()
                    padding(left = metrics.dp(12f), right = metrics.dp(12f))
                    backgroundColor(
                        if (option.id == ctx.selectedModelId) {
                            StockChatTheme.accentSoft
                        } else {
                            StockChatTheme.surface
                        }
                    )
                }
                event {
                    click { ctx.selectModel(option.id) }
                }
                View {
                    attr {
                        size(metrics.dp(40f), metrics.dp(40f))
                        borderRadius(metrics.dp(13f))
                        backgroundColor(Color(0xFF171A18))
                        allCenter()
                    }
                    Text {
                        attr {
                            text("Q")
                            fontSize(metrics.dp(20f))
                            fontWeightBold()
                            color(Color.WHITE)
                        }
                    }
                }
                View {
                    attr {
                        flex(1f)
                        marginLeft(metrics.dp(12f))
                    }
                    View {
                        attr {
                            flexDirectionRow()
                            alignItemsCenter()
                        }
                        Text {
                            attr {
                                text(option.displayName)
                                fontSize(metrics.dp(17f))
                                fontWeightBold()
                                color(StockChatTheme.textPrimary)
                            }
                        }
                        View {
                            attr {
                                backgroundColor(Color(0xFFDDF5EC))
                                borderRadius(metrics.dp(5f))
                                padding(
                                    top = metrics.dp(2f),
                                    left = metrics.dp(5f),
                                    right = metrics.dp(5f),
                                    bottom = metrics.dp(2f),
                                )
                                marginLeft(metrics.dp(7f))
                            }
                            Text {
                                attr {
                                    text(option.badge)
                                    fontSize(metrics.dp(11f))
                                    color(StockChatTheme.accent)
                                }
                            }
                        }
                        Text {
                            attr {
                                text(option.multiplier)
                                fontSize(metrics.dp(13f))
                                color(StockChatTheme.textTertiary)
                                marginLeft(metrics.dp(7f))
                            }
                        }
                    }
                    Text {
                        attr {
                            text(option.description)
                            fontSize(metrics.dp(12f))
                            color(StockChatTheme.textSecondary)
                            marginTop(metrics.dp(4f))
                        }
                    }
                }
                Text {
                    attr {
                        text("✓")
                        fontSize(metrics.dp(24f))
                        fontWeightBold()
                        color(StockChatTheme.accent)
                        opacity(if (option.id == ctx.selectedModelId) 1f else 0f)
                    }
                }
            }
        }
    }

    private fun openConversationMenu() {
        cancelVoiceInput()
        if (::inputRef.isInitialized) {
            inputRef.view?.blur()
        }
        resetKeyboardState()
        messageMenuTargetId = ""
        modelMenuOpen = false
        closeDrawer()
        conversationMenuOpen = true
    }

    private fun closeConversationMenu() {
        conversationMenuOpen = false
    }

    private fun openMessageMenu(messageId: String) {
        conversationMenuOpen = false
        modelMenuOpen = false
        messageMenuTargetId = messageId
    }

    private fun createConversationStockComparison() {
        closeConversationMenu()
        if (messages.none { it.role == ChatRole.USER }) {
            bridgeModule.toast("当前对话还没有可对比的内容")
            return
        }
        try {
            val title = conversationTitle()
            val comparison = ConversationStockComparisonGenerator.generate(
                title = title,
                messages = messages,
            )
            if (comparison.rows.isEmpty()) {
                bridgeModule.toast("当前会话未识别到股票或指数")
                return
            }
            persistChatHistory()
            val snapshot = ConversationStockComparisonGenerator.toArtifactSnapshot(comparison)
            val artifactId = tableArtifactRepository.upsert(activeSessionId, snapshot)
            openTableArtifact(artifactId)
        } catch (_: Throwable) {
            bridgeModule.toast("会话标的对比生成失败，请重试")
        }
    }

    private fun createConversationMindMapArtifact() {
        closeConversationMenu()
        if (messages.none { it.role == ChatRole.USER }) {
            bridgeModule.toast("当前对话还没有可梳理的内容")
            return
        }
        try {
            persistChatHistory()
            val snapshot = ConversationMindMapArtifactGenerator.generate(
                title = conversationTitle(),
                messages = messages,
            )
            val artifactId = mindMapArtifactRepository.upsert(activeSessionId, snapshot)
            openMindMapArtifact(artifactId)
        } catch (_: Throwable) {
            bridgeModule.toast("思维导图生成失败，请重试")
        }
    }

    private fun openStockComparisonLibrary() {
        closeDrawer()
        closeConversationMenu()
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(
            CONVERSATION_TABLE_ARTIFACTS_PAGE_NAME,
            JSONObject(),
        )
    }

    private fun openMindMapArtifactLibrary() {
        closeDrawer()
        closeConversationMenu()
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(
            CONVERSATION_MIND_MAP_ARTIFACTS_PAGE_NAME,
            JSONObject(),
        )
    }

    private fun openTableArtifact(artifactId: Long) {
        val params = JSONObject()
        params.put(CONVERSATION_TABLE_ARTIFACT_ID_PARAM, artifactId.toString())
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(
            CONVERSATION_TABLE_ARTIFACT_PAGE_NAME,
            params,
        )
    }

    private fun openMindMapArtifact(artifactId: Long) {
        val params = JSONObject()
        params.put(CONVERSATION_MIND_MAP_ARTIFACT_ID_PARAM, artifactId.toString())
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(
            CONVERSATION_MIND_MAP_ARTIFACT_PAGE_NAME,
            params,
        )
    }

    private fun openModelMenu() {
        if (::inputRef.isInitialized) {
            inputRef.view?.blur()
        }
        resetKeyboardState()
        messageMenuTargetId = ""
        conversationMenuOpen = false
        closeDrawer()
        modelMenuOpen = true
    }

    private fun closeModelMenu() {
        modelMenuOpen = false
    }

    private fun selectModel(modelId: String) {
        if (chatModelOptions.none { it.id == modelId }) {
            return
        }
        selectedModelId = modelId
        if (activeModelProviderId.isNotBlank()) {
            StockChatSettingsStore.repository.selectModel(activeModelProviderId, modelId)
            configureChatProvider()
        }
        closeModelMenu()
    }

    private fun selectedModel(): ChatModelOption {
        return chatModelOptions.firstOrNull { it.id == selectedModelId }
            ?: chatModelOptions.firstOrNull()
            ?: CHAT_MODEL_OPTIONS.first()
    }

    private fun configureChatProvider() {
        val configuration = StockChatSettingsStore.repository.loadSnapshot().modelConfiguration
        val provider = configuration.providers.firstOrNull { candidate ->
            candidate.id == configuration.activeProviderId
        }
        val options = provider?.toChatModelOptions().orEmpty()
        chatModelOptions = options.ifEmpty { CHAT_MODEL_OPTIONS }
        activeModelProviderId = provider?.id.orEmpty()
        selectedModelId = provider?.selectedModelId
            ?.takeIf { modelId -> chatModelOptions.any { option -> option.id == modelId } }
            ?: chatModelOptions.first().id

        val routeApiKey = pageData.params.optString("qwenApiKey").trim()
        val providerApiKey = when {
            provider == null -> routeApiKey
            !provider.isEnabled -> ""
            provider.apiKey.isNotBlank() -> provider.apiKey
            provider.kind == ModelProviderKind.ALIYUN -> routeApiKey
            else -> ""
        }
        val config = AliyunApiConfig(
            apiKey = providerApiKey,
            baseUrl = provider?.baseUrl?.takeIf(String::isNotBlank)
                ?: DEFAULT_CHAT_BASE_URL,
            chatModel = selectedModelId,
            visionModel = selectedModelId,
            providerDisplayName = provider?.displayName ?: "阿里云百炼",
            useAliyunExtensions = provider == null || provider.kind == ModelProviderKind.ALIYUN,
            supportsVision = ModelCapability.VISION in selectedModel().capabilities,
        )
        val nativeStreamingEnabled = pageData.params.optInt(
            "aliyunNativeStreaming",
            pageData.params.optInt("mimoNativeStreaming"),
        ) == 1
        dataSource = AliyunStockChatDataSource(
            networkModule = networkModule,
            config = config,
            bridgeModule = bridgeModule,
            useNativeStreaming = nativeStreamingEnabled &&
                (provider == null || provider.kind == ModelProviderKind.ALIYUN),
        )
    }

    private fun applySavedAppearance() {
        StockChatTheme.applyAppearance(
            appearance = StockChatSettingsStore.repository.loadSnapshot().appearance,
            systemDark = isNightMode(),
        )
    }

    private fun ModelProviderConfig.toChatModelOptions(): List<ChatModelOption> {
        return models.mapIndexed { index, model ->
            val capabilityLabel = model.capabilities
                .joinToString(" · ") { capability -> capability.displayName }
                .ifBlank { "对话" }
            ChatModelOption(
                id = model.id,
                displayName = model.displayName,
                description = "$displayName · $capabilityLabel",
                badge = when {
                    model.id == selectedModelId -> "当前"
                    index == 0 -> "推荐"
                    else -> model.capabilities.firstOrNull()?.displayName ?: "对话"
                },
                multiplier = model.contextWindowLabel,
                capabilities = model.capabilities,
            )
        }
    }

    private fun openSettings() {
        closeDrawer()
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(
            SETTINGS_PAGE_NAME,
            JSONObject(),
        )
    }

    private fun focusComposer() {
        if (selectedHomeTab != HOME_TAB_CHAT) {
            return
        }
        composerFocused = true
        composerExpanded = true
        collapseComposerAfterSettle = false
        voiceMode = false
        conversationMenuOpen = false
        closeDrawer()
        focusTextInputAfterLayout()
    }

    // 点击/滑动输入框以外的区域：用户焦点离开输入，面板收缩还给页面空间。
    // 键盘在场时先收键盘，收缩挂起到回落动画播完再播（两个动画在同一批节点上
    // 并发会互相打断）；键盘静止时立即收缩
    private fun handleBlankAreaTap() {
        if (voiceMode || voicePressActive) {
            return
        }
        val keyboardWasUp = keyboardVisible || keyboardHeight > 0f
        if (composerFocused || keyboardWasUp) {
            if (::inputRef.isInitialized) {
                inputRef.view?.blur()
            }
            resetKeyboardState()
        }
        if (keyboardWasUp || keyboardDropSettling) {
            // 回落窗口结束时由 beginComposerDockSettle 的定时器执行收缩
            collapseComposerAfterSettle = true
        } else {
            composerExpanded = false
        }
    }

    private fun resetKeyboardState() {
        val keyboardWasUp = keyboardHeight > 0f
        keyboardHeight = 0f
        keyboardVisible = false
        if (keyboardWasUp) {
            beginComposerDockSettle()
        }
        composerFocused = false
    }

    // 键盘开始落下时调用：标记回落窗口（keyboardDropSettling）。窗口内主页
    // 内容不回挂（避免挂载大子树拖慢回落）、空白点击不触发面板收缩（避免
    // 收缩动画打断同节点的回落动画），并调度 nudge 兜底归位
    private fun beginComposerDockSettle() {
        keyboardDropSettling = true
        val generation = ++dockSettleGeneration
        setTimeout(((keyboardAnimDuration + 0.03f) * 1000).toInt()) {
            if (generation == dockSettleGeneration) {
                keyboardDropSettling = false
                // 回落期间挂起的空白交互收缩，此刻键盘动画已结束，可安全播放
                if (collapseComposerAfterSettle) {
                    collapseComposerAfterSettle = false
                    composerExpanded = false
                }
            }
        }
        scheduleComposerDockResync()
    }

    // 键盘落底动画应结束的时刻，再强制同步一次跟随键盘的容器位置：
    // composerDockNudge 自增会让 bottom 产生 0.1px 的无感变化，以一次无动画的
    // 属性更新把原生侧位置拉回正确值——兜底修复回落动画被打断后面板悬停的问题
    private fun scheduleComposerDockResync() {
        setTimeout(((keyboardAnimDuration + 0.12f) * 1000).toInt()) {
            if (keyboardHeight <= 0f && !keyboardVisible) {
                composerDockNudge += 1
            }
        }
    }

    private fun focusTextInputAfterLayout() {
        updateInputLineMetrics(inputText)
        setTimeout(0) {
            if (composerFocused && !voiceMode && ::inputRef.isInitialized) {
                inputRef.view?.setText(inputText)
                inputRef.view?.focus()
            }
        }
    }

    // 输入面板处于展开非语音态时，超出单行的部分行数，用于撑高输入框和面板
    private fun composerExtraInputLines(): Int {
        return if (composerExpanded && !voiceMode) {
            (inputLineCount - 1).coerceAtLeast(0)
        } else {
            0
        }
    }

    internal fun updateInputLineMetrics(text: String) {
        val metrics = layoutMetrics
        // 展开态输入框可用宽 = 页宽 - 面板左右外边距 18*2 - 输入框左右内边距 20*2
        val availableWidth = (pagerData.pageViewWidth - metrics.dp(76f)) * 0.97f
        inputLineCount = estimateWrappedLineCount(
            text,
            metrics.dp(17f),
            availableWidth,
        ).coerceIn(1, MAX_INPUT_LINES)
    }

    private fun InputModeMark(
        container: ViewContainer<*, *>,
        scale: Float,
        onClick: () -> Unit,
    ) {
        val ctx = this
        with(container) {
        View {
            attr {
                size(27f * scale, 34f * scale)
                allCenter()
            }
            event {
                click { onClick() }
            }
            Image {
                attr {
                    size(32f * scale, 32f * scale)
                    resizeContain()
                    src(ImageUri.commonAssets("composer_voice_32.png"))
                    opacity(if (ctx.voiceMode) 0f else 1f)
                    animation(Animation.easeOut(0.18f), ctx.voiceMode)
                }
            }
            View {
                attr {
                    absolutePositionAllZero()
                    allCenter()
                }
                View {
                    attr {
                        size(25f * scale, 19f * scale)
                        borderRadius(4f * scale)
                        border(Border(2f * scale, BorderStyle.SOLID, StockChatTheme.textPrimary))
                        opacity(if (ctx.voiceMode) 1f else 0f)
                        animation(Animation.easeOut(0.18f), ctx.voiceMode)
                    }
                    repeat(3) { row ->
                        repeat(5) { column ->
                            View {
                                attr {
                                    absolutePosition(
                                        top = (3f + row * 4f) * scale,
                                        left = (3f + column * 4f) * scale,
                                    )
                                    size(2f * scale, 2f * scale)
                                    borderRadius(1f * scale)
                                    backgroundColor(StockChatTheme.textPrimary)
                                }
                            }
                        }
                    }
                    View {
                        attr {
                            absolutePosition(bottom = 2f * scale, left = 7f * scale)
                            size(11f * scale, 2f * scale)
                            borderRadius(1f * scale)
                            backgroundColor(StockChatTheme.textPrimary)
                        }
                    }
                }
            }
        }
        }
    }

    private fun PlusMark(
        container: ViewContainer<*, *>,
        scale: Float,
        onClick: () -> Unit,
    ) {
        val ctx = this
        with(container) {
        View {
            attr {
                size(34f * scale, 34f * scale)
                allCenter()
            }
            event {
                click { onClick() }
            }
            Image {
                attr {
                    size(32f * scale, 32f * scale)
                    resizeContain()
                    src(ImageUri.commonAssets("composer_plus_32.png"))
                }
            }
        }
        }
    }

    private fun voiceInputHint(): String {
        return when (voiceInputState) {
            VoiceInputState.IDLE -> "问行情、学炒股或按住说话"
            VoiceInputState.STARTING -> "正在启动麦克风…"
            VoiceInputState.RECORDING -> "正在录音，再点一次结束"
            VoiceInputState.TRANSCRIBING -> "MiMo 正在识别语音…"
        }
    }

    private fun voiceModePrompt(): String {
        return when (voiceInputState) {
            VoiceInputState.IDLE -> "按住 说话"
            VoiceInputState.STARTING -> "正在启动麦克风…"
            VoiceInputState.RECORDING -> if (voicePressCanceled) "松手取消" else "松手发送"
            VoiceInputState.TRANSCRIBING -> "MiMo 正在识别…"
        }
    }

    private fun toggleVoiceMode() {
        if (voiceMode) {
            cancelVoicePress()
            composerFocused = true
            composerExpanded = true
            collapseComposerAfterSettle = false
            voiceMode = false
            closeDrawer()
            focusTextInputAfterLayout()
            return
        }
        cancelVoiceInput()
        if (::inputRef.isInitialized) {
            inputRef.view?.blur()
        }
        resetKeyboardState()
        voiceMode = true
        closeDrawer()
    }

    // 非语音模式下，输入框未输入（无文字、无附件、未聚焦）时长按也进入按住说话；
    // 有草稿时不触发，把长按留给文本相关操作
    private fun composerHoldToTalkReady(): Boolean =
        !voiceMode &&
            selectedHomeTab == HOME_TAB_CHAT &&
            !composerFocused &&
            inputText.isEmpty() &&
            selectedImageCount == 0

    // 识别结果落到输入框由用户确认后发送，不直接发出；追加在已有草稿之后。
    // 状态顺序与 toggleVoiceMode 关闭分支一致：展开先触发、键盘动画后接管
    private fun fillComposerWithVoiceResult(recognizedText: String) {
        inputText = (inputText + recognizedText).take(300)
        composerFocused = true
        composerExpanded = true
        collapseComposerAfterSettle = false
        voiceMode = false
        focusTextInputAfterLayout()
    }

    private fun handleVoiceLongPress(params: LongPressParams) {
        when (params.state) {
            "start" -> {
                if (isSending) {
                    bridgeModule.toast("请等待当前回答完成")
                    return
                }
                if (voiceInputState != VoiceInputState.IDLE) {
                    bridgeModule.toast("语音输入正在处理中")
                    return
                }
                voicePressStartY = params.pageY
                voicePressCanceled = false
                voicePressActive = true
                voicePressReleaseRequested = false
                startVoiceWaveAnimation()
                startVoiceInput()
            }
            "move" -> {
                if (voicePressActive) {
                    voicePressCanceled = voicePressStartY - params.pageY >= VOICE_CANCEL_DISTANCE
                }
            }
            "end" -> finishVoicePress()
        }
    }

    private fun finishVoicePress() {
        if (!voicePressActive) {
            return
        }
        val shouldCancel = voicePressCanceled
        voicePressActive = false
        voicePressCanceled = false
        voicePressStartY = 0f
        stopVoiceWaveAnimation()
        if (shouldCancel) {
            cancelVoiceInput()
            bridgeModule.toast("已取消语音发送")
            return
        }
        when (voiceInputState) {
            VoiceInputState.STARTING -> voicePressReleaseRequested = true
            VoiceInputState.RECORDING -> stopVoiceInput()
            VoiceInputState.IDLE,
            VoiceInputState.TRANSCRIBING -> Unit
        }
    }

    private fun cancelVoicePress() {
        if (!voicePressActive && voiceInputState == VoiceInputState.IDLE) {
            return
        }
        voicePressActive = false
        voicePressCanceled = false
        voicePressStartY = 0f
        stopVoiceWaveAnimation()
        cancelVoiceInput()
    }

    private fun startVoiceWaveAnimation() {
        stopVoiceWaveAnimation()
        voiceWavePhase = 0
        voiceWaveTimer = Timer().also { timer ->
            timer.schedule(0, 120) {
                voiceWavePhase = (voiceWavePhase + 1) % 120
            }
        }
    }

    private fun stopVoiceWaveAnimation() {
        voiceWaveTimer?.cancel()
        voiceWaveTimer = null
    }

    private fun startVoiceInput() {
        if (isSending) {
            bridgeModule.toast("请等待当前回答完成")
            return
        }
        if (!speechRecognitionService.isConfigured) {
            bridgeModule.toast("MiMo 语音 Key 尚未配置")
            return
        }
        if (::inputRef.isInitialized) {
            inputRef.view?.blur()
        }
        resetKeyboardState()
        voiceRequestToken += 1
        val currentVoiceToken = voiceRequestToken
        voicePressReleaseRequested = false
        voiceInputState = VoiceInputState.STARTING
        setTimeout(30_000) {
            if (
                currentVoiceToken == voiceRequestToken &&
                voiceInputState == VoiceInputState.STARTING
            ) {
                cancelVoiceInput()
                bridgeModule.toast("麦克风启动超时，请检查权限后重试")
            }
        }
        bridgeModule.startVoiceRecording { result ->
            if (currentVoiceToken != voiceRequestToken) {
                bridgeModule.cancelVoiceRecording()
                return@startVoiceRecording
            }
            if (result?.optInt("success") == 1) {
                voiceInputState = VoiceInputState.RECORDING
                if (voicePressReleaseRequested || !voicePressActive) {
                    voicePressReleaseRequested = false
                    stopVoiceInput()
                    return@startVoiceRecording
                }
                setTimeout(30_000) {
                    if (
                        currentVoiceToken == voiceRequestToken &&
                        voiceInputState == VoiceInputState.RECORDING
                    ) {
                        voicePressActive = false
                        voicePressCanceled = false
                        stopVoiceWaveAnimation()
                        stopVoiceInput()
                    }
                }
            } else {
                voicePressActive = false
                voicePressCanceled = false
                voicePressReleaseRequested = false
                stopVoiceWaveAnimation()
                voiceInputState = VoiceInputState.IDLE
                bridgeModule.toast(
                    result?.optString("errorMessage").orEmpty().ifBlank {
                        "无法启动麦克风，请检查录音权限"
                    }
                )
            }
        }
    }

    private fun stopVoiceInput() {
        if (voiceInputState != VoiceInputState.RECORDING) {
            return
        }
        val currentVoiceToken = voiceRequestToken
        voicePressReleaseRequested = false
        voiceInputState = VoiceInputState.TRANSCRIBING
        bridgeModule.stopVoiceRecording { result ->
            if (currentVoiceToken != voiceRequestToken) {
                return@stopVoiceRecording
            }
            val audioBase64 = result?.optString("audioBase64").orEmpty()
            if (result?.optInt("success") != 1 || audioBase64.isBlank()) {
                voiceInputState = VoiceInputState.IDLE
                bridgeModule.toast(
                    result?.optString("errorMessage").orEmpty().ifBlank {
                        "没有录到有效语音，请重试"
                    }
                )
                return@stopVoiceRecording
            }
            runCatching {
                speechRecognitionService.transcribe(
                    audioBase64 = audioBase64,
                    mimeType = result.optString("mimeType").ifBlank { "audio/wav" },
                ) { recognitionResult ->
                    if (currentVoiceToken != voiceRequestToken) {
                        return@transcribe
                    }
                    voiceInputState = VoiceInputState.IDLE
                    when (recognitionResult) {
                        is SpeechRecognitionResult.Success -> {
                            val recognizedText = recognitionResult.text.trim()
                            if (recognizedText.isEmpty()) {
                                bridgeModule.toast("没有识别到有效内容，请重试")
                            } else {
                                fillComposerWithVoiceResult(recognizedText)
                            }
                        }
                        is SpeechRecognitionResult.Failure -> {
                            bridgeModule.toast(recognitionResult.message)
                        }
                    }
                }
            }.onFailure {
                if (currentVoiceToken == voiceRequestToken) {
                    voiceInputState = VoiceInputState.IDLE
                    bridgeModule.toast("MiMo 语音识别暂时不可用，请稍后重试")
                }
            }
        }
    }

    private fun cancelVoiceInput() {
        voiceRequestToken += 1
        stopVoiceWaveAnimation()
        if (
            voiceInputState == VoiceInputState.STARTING ||
            voiceInputState == VoiceInputState.RECORDING
        ) {
            bridgeModule.cancelVoiceRecording()
        }
        voicePressActive = false
        voicePressCanceled = false
        voicePressStartY = 0f
        voicePressReleaseRequested = false
        voiceInputState = VoiceInputState.IDLE
    }

    private fun openImagePicker() {
        if (imagePickerOpen) {
            bridgeModule.toast("图片选择器已打开")
            return
        }
        val remainingCount = MAX_IMAGE_SELECTION_COUNT - selectedImageCount
        if (remainingCount <= 0) {
            bridgeModule.toast("最多选择 9 张图片")
            return
        }
        imagePickerOpen = true
        if (::inputRef.isInitialized) {
            inputRef.view?.blur()
        }
        resetKeyboardState()
        bridgeModule.pickImages(remainingCount) pickerResult@{ result ->
            resetKeyboardState()
            imagePickerOpen = false
            if (result == null) {
                bridgeModule.toast("图片选择暂时不可用")
                return@pickerResult
            }
            if (result.optInt("cancelled", 0) == 1) {
                return@pickerResult
            }
            if (result.optInt("success", 0) != 1) {
                bridgeModule.toast(
                    result.optString("errorMessage").ifBlank { "图片选择失败，请稍后重试" }
                )
                return@pickerResult
            }
            val imageArray = result.optJSONArray("images")
            val previewImageArray = result.optJSONArray("previewImages")
            val newImagePreviews = mutableListOf<String>()
            val newImagePayloads = mutableListOf<String>()
            if (imageArray != null) {
                for (index in 0 until imageArray.length()) {
                    if (selectedImagePreviews.size + newImagePreviews.size >= MAX_IMAGE_SELECTION_COUNT) {
                        break
                    }
                    val imagePayload = imageArray.optString(index).orEmpty().trim()
                    val imagePreview = previewImageArray
                        ?.optString(index)
                        .orEmpty()
                        .trim()
                        .ifBlank { imagePayload }
                    if (
                        imagePayload.isNotEmpty() &&
                        imagePreview.isNotEmpty() &&
                        imagePreview !in selectedImagePreviews &&
                        imagePreview !in newImagePreviews
                    ) {
                        newImagePreviews.add(imagePreview)
                        newImagePayloads.add(imagePayload)
                    }
                }
            }
            selectedImagePreviews.addAll(newImagePreviews)
            selectedImagePayloads.addAll(newImagePayloads)
            selectedImages.addAll(newImagePreviews)
            selectedImageCount = selectedImagePreviews.size
            if (newImagePreviews.isEmpty()) {
                bridgeModule.toast("没有选择新的图片")
            } else {
                if (result.optInt("truncated", 0) == 1) {
                    bridgeModule.toast("已保留前 9 张图片")
                }
            }
        }
    }

    private fun removeSelectedImage(imageUri: String) {
        val imageIndex = selectedImagePreviews.indexOf(imageUri)
        if (imageIndex < 0) {
            return
        }
        selectedImagePreviews.removeAt(imageIndex)
        selectedImages.removeAt(imageIndex)
        if (imageIndex < selectedImagePayloads.size) {
            selectedImagePayloads.removeAt(imageIndex)
        }
        selectedImageCount = selectedImagePreviews.size
    }

    private fun sendMessage(
        submittedText: String? = null,
        source: StockChatQuestionSource = StockChatQuestionSource.COMPOSER,
    ) {
        if (!homeState.canSubmitQuestion(source)) {
            return
        }
        if (isSending) {
            return
        }
        if (voiceInputState != VoiceInputState.IDLE) {
            bridgeModule.toast("请先结束语音输入")
            return
        }
        val attachedImages = selectedImagePreviews.toList()
        val attachedImagePayloads = selectedImagePayloads.toList()
        if (
            attachedImages.size != attachedImagePayloads.size ||
            attachedImagePayloads.any { !it.startsWith("data:image/") }
        ) {
            bridgeModule.toast("图片处理失败，请重新选择")
            return
        }
        if (
            attachedImages.isNotEmpty() &&
            ModelCapability.VISION !in selectedModel().capabilities
        ) {
            bridgeModule.toast("当前模型不支持图片理解，请切换到“视觉理解”模型后重试")
            return
        }
        val typedQuestion = (submittedText ?: inputText).trim()
        val question = typedQuestion.ifBlank {
            if (attachedImages.isNotEmpty()) IMAGE_ONLY_QUESTION else ""
        }
        if (question.isEmpty() && attachedImages.isEmpty()) {
            bridgeModule.toast("请输入问题，例如查行情、学炒股或问其他问题")
            return
        }
        dispatchHome(StockChatHomeEvent.QuestionCommitted(source))
        if (::inputRef.isInitialized) {
            inputRef.view?.setText("")
            inputRef.view?.blur()
        }
        inputText = ""
        inputLineCount = 1
        selectedImagePreviews.clear()
        selectedImages.clear()
        selectedImagePayloads.clear()
        selectedImageCount = 0
        voiceMode = false
        resetKeyboardState()
        isSending = true
        stickMessageListToBottom = true
        messageListNearBottom = true
        requestToken += 1
        val currentRequestToken = requestToken
        val userMessage = ChatMessage(
                id = nextMessageId(),
                role = ChatRole.USER,
                blocks = buildList {
                    if (attachedImages.isNotEmpty()) {
                        add(
                            AnswerBlock.ImageGallery(
                                images = attachedImages,
                                requestImages = attachedImagePayloads,
                            )
                        )
                    }
                    add(AnswerBlock.Markdown(question, question))
                },
            )
        messages.add(userMessage)
        val answerId = nextMessageId()
        messages.add(
            ChatMessage(
                id = answerId,
                role = ChatRole.ASSISTANT,
                blocks = emptyList(),
                state = MessageState.GENERATING,
                retryQuestion = question,
            )
        )
        persistChatHistory()
        updateTypingIndicatorTimer()
        completeAnswer(answerId, question, 0, currentRequestToken)
    }

    private fun sendQuickQuestion(question: String) {
        sendMessage(question, StockChatQuestionSource.DRAWER_SHORTCUT)
    }

    private fun completeAnswer(
        messageId: String,
        question: String,
        attempt: Int,
        currentRequestToken: Int,
    ) {
        val history = conversationHistoryBefore(messageId)
        val attachedImages = imagesBeforeAnswer(messageId)
        val activeModel = selectedModel()
        if (
            attachedImages.isNotEmpty() &&
            ModelCapability.VISION !in activeModel.capabilities
        ) {
            applyAnswer(
                messageId,
                question,
                attempt,
                ChatAnswer.Failure(
                    "当前模型 ${activeModel.displayName} 不支持图片理解，请切换到“视觉理解”模型后重试。"
                ),
            )
            return
        }
        runCatching {
            dataSource.answer(
                question,
                history,
                attachedImages,
                activeModel.id,
                attempt,
            ) response@{ answer ->
                if (currentRequestToken != requestToken) {
                    return@response
                }
                applyAnswer(messageId, question, attempt, answer)
            }
        }.onFailure {
            if (currentRequestToken == requestToken) {
                applyAnswer(
                    messageId,
                    question,
                    attempt,
                    ChatAnswer.Failure("AI 服务暂时不可用，请稍后重试。"),
                )
            }
        }
    }

    private fun imagesBeforeAnswer(messageId: String): List<String> {
        val answerIndex = messages.indexOfFirst { it.id == messageId }
        if (answerIndex <= 0) {
            return emptyList()
        }
        for (index in (answerIndex - 1) downTo 0) {
            val message = messages[index]
            if (message.role == ChatRole.USER) {
                return message.blocks
                    .filterIsInstance<AnswerBlock.ImageGallery>()
                    .flatMap { it.requestImages }
                    .filter { it.startsWith("data:image/") }
            }
        }
        return emptyList()
    }

    private fun conversationHistoryBefore(messageId: String): List<ChatHistoryItem> {
        val answerIndex = messages.indexOfFirst { it.id == messageId }
        if (answerIndex < 0) {
            return emptyList()
        }
        val historyItems = messages.take(answerIndex).mapNotNull { message ->
            val content = message.blocks.mapNotNull { block ->
                when (block) {
                    is AnswerBlock.Markdown -> block.source.trim().ifEmpty { null }
                    is AnswerBlock.MarketQuote -> providerSymbolForQuote(block.quote)?.let { providerSymbol ->
                        "[行情标的:$providerSymbol|${block.quote.name}] " +
                            "${block.quote.updatedAt}，现价 ${block.quote.price}，" +
                            "涨跌 ${block.quote.change}（${block.quote.changePercent}）"
                    }
                    is AnswerBlock.ImageGallery -> null
                }
            }.joinToString("\n\n").trim()
            if (content.isEmpty()) {
                null
            } else {
                ChatHistoryItem(message.role, content)
            }
        }
        val completedTurns = mutableListOf<ChatHistoryItem>()
        var pendingUserMessage: ChatHistoryItem? = null
        historyItems.forEach { item ->
            when (item.role) {
                ChatRole.USER -> pendingUserMessage = item
                ChatRole.ASSISTANT -> pendingUserMessage?.let { userMessage ->
                    completedTurns += userMessage
                    completedTurns += item
                    pendingUserMessage = null
                }
            }
        }
        return completedTurns.takeLast(MAX_HISTORY_TURNS * 2)
    }

    private fun applyAnswer(
        messageId: String,
        question: String,
        attempt: Int,
        answer: ChatAnswer,
    ) {
        val index = messages.indexOfFirst { it.id == messageId }
        if (index < 0) {
            return
        }
        val previousMessage = messages[index]
        messages[index] = when (answer) {
            is ChatAnswer.Streaming -> previousMessage.copy(
                blocks = listOf(AnswerBlock.Markdown(answer.markdown, answer.markdown)),
                state = MessageState.GENERATING,
            )
            is ChatAnswer.Success -> ChatMessage(
                id = messageId,
                role = ChatRole.ASSISTANT,
                blocks = answer.blocks,
                // 保留原始提问，「重新生成」直接复用
                retryQuestion = question,
            )
            is ChatAnswer.Failure -> ChatMessage(
                id = messageId,
                role = ChatRole.ASSISTANT,
                blocks = emptyList(),
                state = MessageState.FAILED,
                retryQuestion = question,
                retryAttempt = attempt,
                errorMessage = answer.message,
            )
        }
        if (answer !is ChatAnswer.Streaming) {
            isSending = false
            persistChatHistory()
        }
        updateTypingIndicatorTimer()
    }

    private fun retryMessage(message: ChatMessage) {
        if (isSending || message.retryQuestion.isEmpty()) {
            return
        }
        val index = messages.indexOfFirst { it.id == message.id }
        if (index < 0) {
            return
        }
        isSending = true
        stickMessageListToBottom = true
        messageListNearBottom = true
        requestToken += 1
        val currentRequestToken = requestToken
        messages[index] = message.copy(
            blocks = emptyList(),
            state = MessageState.GENERATING,
            retryAttempt = message.retryAttempt + 1,
            errorMessage = "",
        )
        persistChatHistory()
        updateTypingIndicatorTimer()
        completeAnswer(
            message.id,
            message.retryQuestion,
            message.retryAttempt + 1,
            currentRequestToken,
        )
    }

    // 「重新生成」：已完成的回答也可重来；老会话可能没存 retryQuestion，回退到前一条用户消息
    private fun regenerateMessage(message: ChatMessage) {
        if (isSending) {
            bridgeModule.toast("请等待当前回答完成")
            return
        }
        val index = messages.indexOfFirst { it.id == message.id }
        if (index < 0) {
            return
        }
        val question = message.retryQuestion.ifBlank {
            messages.take(index)
                .lastOrNull { it.role == ChatRole.USER }
                ?.let(::messageText)
                .orEmpty()
        }
        if (question.isBlank()) {
            bridgeModule.toast("找不到对应的提问，无法重新生成")
            return
        }
        val prepared = messages[index].copy(retryQuestion = question)
        messages[index] = prepared
        retryMessage(prepared)
    }

    private fun copyMessage(message: ChatMessage) {
        val content = messageText(message)
        if (content.isNotBlank()) {
            bridgeModule.copyToPasteboard(content)
            bridgeModule.toast("已复制回答")
        }
    }

    private fun shareMessage(message: ChatMessage) {
        val content = StockChatShareContentBuilder.fromMessage(message)
        if (content == null) {
            bridgeModule.toast("当前消息暂无可分享内容")
            return
        }
        val sharedSessionId = activeSessionId
        val sharedQuestion = sharedQuestion(message)
        acquireModule<ShareModule>(ShareModule.MODULE_NAME).share(content) { result ->
            when (result) {
                ShareResult.Success -> StockChatSettingsStore.repository.recordSharedChat(
                    sessionId = sharedSessionId,
                    question = sharedQuestion,
                    content = content,
                )
                ShareResult.Cancelled -> Unit
                is ShareResult.Failure -> bridgeModule.toast(result.errorMessage)
            }
        }
    }

    private fun sharedQuestion(message: ChatMessage): String {
        if (message.retryQuestion.isNotBlank()) {
            return message.retryQuestion.trim()
        }
        if (message.role == ChatRole.USER) {
            return messageText(message)
        }
        val messageIndex = messages.indexOfFirst { candidate -> candidate.id == message.id }
        return messages.take(messageIndex.coerceAtLeast(0))
            .lastOrNull { candidate -> candidate.role == ChatRole.USER }
            ?.let(::messageText)
            ?.ifBlank { null }
            ?: conversationTitle()
    }

    private fun copySelectedText(content: String) {
        if (content.isNotBlank()) {
            bridgeModule.copyToPasteboard(content)
            bridgeModule.toast("已复制选中文字")
        }
    }

    private fun deleteMessage(message: ChatMessage) {
        val index = messages.indexOfFirst { it.id == message.id }
        if (index < 0) {
            return
        }
        messages.removeAt(index)
        dispatchHome(
            StockChatHomeEvent.ConversationSynchronized(messages.isNotEmpty())
        )
        if (messages.isEmpty()) {
            // persistChatHistory 对空列表直接返回，这里显式清掉库里的旧消息
            chatHistoryRepository.clearSession(activeSessionId)
            refreshRecentSessions()
        } else {
            persistChatHistory()
        }
        bridgeModule.toast("已删除")
    }

    private fun updateTypingIndicatorTimer() {
        val waitingFirstToken = messages.any {
            it.state == MessageState.GENERATING && it.blocks.isEmpty()
        }
        if (waitingFirstToken && typingDotTimer == null) {
            typingDotPhase = 0
            typingDotTimer = Timer().also { timer ->
                timer.schedule(0, 320) {
                    typingDotPhase = (typingDotPhase + 1) % 3
                }
            }
        } else if (!waitingFirstToken && typingDotTimer != null) {
            typingDotTimer?.cancel()
            typingDotTimer = null
        }
    }

    private fun openStockDetail(
        quote: StockQuote,
        sourceTab: Int,
    ) {
        if (selectedHomeTab != sourceTab) {
            return
        }
        cancelVoiceInput()
        if (::inputRef.isInitialized) {
            inputRef.view?.blur()
        }
        val params = JSONObject()
        params.put("symbol", providerSymbolForQuote(quote) ?: quote.symbol)
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(STOCK_DETAIL_PAGE_NAME, params)
    }

    private fun openImagePreview(imageUri: String) {
        if (selectedHomeTab != HOME_TAB_CHAT || imageUri.isBlank()) {
            return
        }
        cancelVoiceInput()
        if (::inputRef.isInitialized) {
            inputRef.view?.blur()
        }
        val params = JSONObject()
        params.put(ImagePreviewPage.IMAGE_URI_PARAM, imageUri)
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(IMAGE_PREVIEW_PAGE_NAME, params)
    }

    private fun handleDrawerPan(params: PanGestureParams) {
        when (params.state) {
            "start", "move" -> {
                if (params.state == "move" && drawerPanStartX == 0f && drawerPanStartY == 0f) {
                    drawerPanStartX = params.pageX
                    drawerPanStartY = params.pageY
                }
                if (params.state == "move") {
                    val deltaX = params.pageX - drawerPanStartX
                    val deltaY = params.pageY - drawerPanStartY
                    val isHorizontalSwipe = kotlin.math.abs(deltaX) >= DRAWER_SWIPE_DISTANCE &&
                        kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY)
                    if (isHorizontalSwipe && deltaX < 0f && drawerOpen) {
                        closeDrawer()
                    }
                }
                if (params.state == "start") {
                    drawerPanStartX = params.pageX
                    drawerPanStartY = params.pageY
                }
            }
            "end" -> {
                val deltaX = params.pageX - drawerPanStartX
                val deltaY = params.pageY - drawerPanStartY
                val isHorizontalSwipe = kotlin.math.abs(deltaX) >= DRAWER_SWIPE_DISTANCE &&
                    kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY)
                if (isHorizontalSwipe) {
                    if (deltaX > 0f && !drawerOpen) {
                        openDrawer()
                    } else if (deltaX < 0f && drawerOpen) {
                        closeDrawer()
                    }
                }
                drawerPanStartX = 0f
                drawerPanStartY = 0f
            }
        }
    }

    private fun openDrawer() {
        cancelVoiceInput()
        conversationMenuOpen = false
        messageMenuTargetId = ""
        modelMenuOpen = false
        drawerOpen = true
    }

    private fun closeDrawer() {
        drawerOpen = false
        if (managingSessions || renameSessionId.isNotEmpty()) {
            managingSessions = false
            closeRenameDialog()
        }
    }

    private fun toggleSessionManagement() {
        managingSessions = !managingSessions
        if (!managingSessions) {
            closeRenameDialog()
        }
    }

    private fun openRenameDialog(session: ChatSessionSummary) {
        if (!managingSessions) {
            return
        }
        if (::inputRef.isInitialized) {
            inputRef.view?.blur()
        }
        resetKeyboardState()
        renameSessionId = session.id
        renameInputText = session.title.ifBlank { "新对话" }
    }

    private fun closeRenameDialog() {
        if (::renameInputRef.isInitialized) {
            renameInputRef.view?.blur()
        }
        renameSessionId = ""
        renameInputText = ""
    }

    private fun commitSessionRename() {
        val sessionId = renameSessionId
        val title = renameInputText.trim().take(40)
        if (sessionId.isBlank()) {
            return
        }
        if (title.isBlank()) {
            bridgeModule.toast("对话名称不能为空")
            return
        }
        chatHistoryRepository.renameSession(sessionId, title)
        closeRenameDialog()
        refreshRecentSessions()
        bridgeModule.toast("已重命名")
    }

    private fun deleteSession(sessionId: String) {
        if (sessionId.isBlank()) {
            return
        }
        closeRenameDialog()
        val deletingActiveSession = sessionId == activeSessionId
        persistChatHistory()
        requestToken += 1
        chatHistoryRepository.clearSession(sessionId)
        refreshRecentSessions()
        if (deletingActiveSession) {
            activeSessionId = recentSessions.firstOrNull()?.id ?: nextSessionId()
            messages.clear()
            messageSequence = 0
            inputText = ""
            inputLineCount = 1
            selectedImagePreviews.clear()
            selectedImages.clear()
            selectedImagePayloads.clear()
            selectedImageCount = 0
            isSending = false
            val hasMessages = loadMessagesForActiveSession()
            dispatchHome(StockChatHomeEvent.ConversationSynchronized(hasMessages))
            updateTypingIndicatorTimer()
        }
        bridgeModule.toast("已删除对话")
    }

    private fun selectSession(sessionId: String) {
        if (sessionId == activeSessionId) {
            dispatchHome(
                StockChatHomeEvent.ConversationOpened(messages.isNotEmpty())
            )
            return
        }
        cancelVoiceInput()
        persistChatHistory()
        requestToken += 1
        activeSessionId = sessionId
        messages.clear()
        messageSequence = 0
        isSending = false
        messageMenuTargetId = ""
        conversationMenuOpen = false
        val hasMessages = loadMessagesForActiveSession()
        dispatchHome(StockChatHomeEvent.ConversationOpened(hasMessages))
        updateTypingIndicatorTimer()
    }

    private fun startNewChat() {
        cancelVoiceInput()
        persistChatHistory()
        requestToken += 1
        activeSessionId = nextSessionId()
        messages.clear()
        dispatchHome(StockChatHomeEvent.NewConversationStarted)
        inputText = ""
        inputLineCount = 1
        selectedImagePreviews.clear()
        selectedImages.clear()
        selectedImagePayloads.clear()
        selectedImageCount = 0
        voiceMode = false
        isSending = false
        // 收缩要先于键盘归零：展开动画先触发、键盘动画后接管是验证过的安全
        // 顺序（反过来会打断键盘回落，消息区冻住）
        composerExpanded = false
        resetKeyboardState()
        stickMessageListToBottom = true
        messageListNearBottom = true
        drawerOpen = false
        messageMenuTargetId = ""
        conversationMenuOpen = false
        modelMenuOpen = false
        updateTypingIndicatorTimer()
        if (::inputRef.isInitialized) {
            inputRef.view?.setText("")
            inputRef.view?.blur()
        }
    }

    private fun nextMessageId(): String {
        messageSequence += 1
        return "message_${activeSessionId}_$messageSequence"
    }

    private fun persistChatHistory() {
        if (activeSessionId.isBlank() || messages.isEmpty()) {
            return
        }
        chatHistoryRepository.replaceMessages(activeSessionId, messages)
        refreshRecentSessions()
    }

    private fun readMessageAloud(message: ChatMessage) {
        // 再次点击同一条消息的声音按钮视为停止播放
        if (readAloudMessageId == message.id) {
            stopSpeechPlayback()
            return
        }
        val content = messageText(message)
        if (content.isBlank()) {
            bridgeModule.toast("没有可朗读的内容")
            return
        }
        if (!speechSynthesisService.isConfigured) {
            bridgeModule.toast("MiMo 语音 Key 尚未配置")
            return
        }
        speechSynthesisRequestToken += 1
        val currentRequestToken = speechSynthesisRequestToken
        bridgeModule.stopAudioPlayback()
        // 生成与播放进度不再弹 toast，用声音按钮上的流动声纹反馈
        beginReadAloudIndicator(message.id)
        speechSynthesisService.synthesize(content) synthesis@{ result ->
            if (currentRequestToken != speechSynthesisRequestToken) {
                return@synthesis
            }
            when (result) {
                // 流式播放开始：声纹从生成时起就已在流动，无需额外反馈
                SpeechSynthesisResult.Started -> Unit
                SpeechSynthesisResult.Completed -> endReadAloudIndicator()
                is SpeechSynthesisResult.Success -> {
                    bridgeModule.playBase64Audio(
                        audioBase64 = result.audioBase64,
                        mimeType = result.mimeType,
                    ) playback@{ payload ->
                        if (currentRequestToken != speechSynthesisRequestToken) {
                            return@playback
                        }
                        if (payload?.optInt("success", 0) != 1) {
                            endReadAloudIndicator()
                            bridgeModule.toast(
                                payload?.optString("errorMessage")
                                    ?.ifBlank { "语音播放失败，请稍后重试" }
                                    ?: "语音播放失败，请稍后重试"
                            )
                        } else {
                            scheduleReadAloudFinish(
                                currentRequestToken,
                                message.id,
                                result.audioBase64,
                            )
                        }
                    }
                }
                is SpeechSynthesisResult.Failure -> {
                    endReadAloudIndicator()
                    bridgeModule.toast(result.message)
                }
            }
        }
    }

    private fun stopSpeechPlayback() {
        speechSynthesisRequestToken += 1
        bridgeModule.stopAudioPlayback()
        endReadAloudIndicator()
    }

    private fun beginReadAloudIndicator(messageId: String) {
        readAloudMessageId = messageId
        if (readAloudWaveTimer == null) {
            readAloudWavePhase = 0
            readAloudWaveTimer = Timer().also { timer ->
                timer.schedule(0, 120) {
                    readAloudWavePhase = (readAloudWavePhase + 1) % 120
                }
            }
        }
    }

    private fun endReadAloudIndicator() {
        readAloudMessageId = ""
        readAloudWaveTimer?.cancel()
        readAloudWaveTimer = null
    }

    // 非流式播放没有完成回调：从 WAV 头解析时长，到点后收起声纹；
    // 解析失败兜底 60s，避免声纹无限滚动
    private fun scheduleReadAloudFinish(
        requestToken: Int,
        messageId: String,
        audioBase64: String,
    ) {
        val durationMs = estimateWavDurationMs(audioBase64) ?: 60_000L
        setTimeout((durationMs + 300).coerceAtMost(120_000L).toInt()) {
            if (requestToken == speechSynthesisRequestToken && readAloudMessageId == messageId) {
                endReadAloudIndicator()
            }
        }
    }

    private fun estimateWavDurationMs(audioBase64: String): Long? {
        // WAV 头 44 字节：byteRate 在偏移 28、data 块大小在偏移 40（均小端 int32）
        val header = decodeBase64Prefix(audioBase64, 44) ?: return null
        if (header[0] != 'R'.code.toByte() || header[1] != 'I'.code.toByte() ||
            header[2] != 'F'.code.toByte() || header[3] != 'F'.code.toByte()
        ) {
            return null
        }
        fun littleEndianInt(offset: Int): Long {
            var value = 0L
            for (i in 3 downTo 0) {
                value = (value shl 8) or (header[offset + i].toLong() and 0xFF)
            }
            return value
        }
        val byteRate = littleEndianInt(28)
        val dataSize = littleEndianInt(40)
        if (byteRate <= 0 || dataSize <= 0) {
            return null
        }
        return (dataSize * 1000 / byteRate).coerceAtLeast(800L)
    }

    private fun decodeBase64Prefix(text: String, byteCount: Int): ByteArray? {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val output = ByteArray(byteCount)
        var outputCount = 0
        var buffer = 0
        var bufferBits = 0
        for (char in text) {
            if (outputCount >= byteCount) {
                break
            }
            if (char == '=') {
                break
            }
            if (char == '\n' || char == '\r' || char == ' ') {
                continue
            }
            val value = alphabet.indexOf(char)
            if (value < 0) {
                return null
            }
            buffer = (buffer shl 6) or value
            bufferBits += 6
            if (bufferBits >= 8) {
                bufferBits -= 8
                output[outputCount] = ((buffer shr bufferBits) and 0xFF).toByte()
                outputCount += 1
            }
        }
        return if (outputCount >= byteCount) output else null
    }

    private fun messageText(message: ChatMessage): String {
        return message.blocks.mapNotNull { block ->
            when (block) {
                is AnswerBlock.Markdown -> block.fallbackText.ifBlank { block.source }
                is AnswerBlock.MarketQuote ->
                    "${block.quote.name}（${block.quote.symbol}） ${block.quote.price} " +
                        "${block.quote.change} ${block.quote.changePercent}"
                is AnswerBlock.ImageGallery -> "图片附件 × ${block.images.size}"
            }.trim().ifBlank { null }
        }.joinToString("\n\n").trim()
    }

    private fun conversationTitle(): String {
        return messages.firstOrNull { it.role == ChatRole.USER }
            ?.let(::messageText)
            ?.lineSequence()
            ?.firstOrNull()
            ?.trim()
            ?.take(16)
            ?.ifBlank { null }
            ?: "新对话"
    }

    private fun initializeChatSessions() {
        val sessions = chatHistoryRepository.loadSessions()
        recentSessions.clear()
        sessions.forEach { session ->
            recentSessions.add(session)
            session.id.substringAfterLast('_').toIntOrNull()?.let {
                sessionSequence = maxOf(sessionSequence, it)
            }
        }
        activeSessionId = nextSessionId()
    }

    private fun loadMessagesForActiveSession(): Boolean {
        messages.clear()
        chatHistoryRepository.loadMessages(activeSessionId).forEach { message ->
            messages.add(message)
            message.id.substringAfterLast('_').toIntOrNull()?.let {
                messageSequence = maxOf(messageSequence, it)
            }
        }
        return messages.isNotEmpty()
    }

    private fun refreshRecentSessions() {
        recentSessions.clear()
        chatHistoryRepository.loadSessions().forEach { session ->
            recentSessions.add(session)
        }
    }

    private fun nextSessionId(): String {
        sessionSequence += 1
        return "session_$sessionSequence"
    }

    companion object {
        private const val MAX_HISTORY_TURNS = 6
        private const val MAX_INPUT_LINES = 5
        private const val DRAWER_SWIPE_DISTANCE = 56f
        private const val VOICE_CANCEL_DISTANCE = 56f
        private const val MAX_IMAGE_SELECTION_COUNT = 9
        private const val IMAGE_ONLY_QUESTION = "请分析我上传的图片"
    }
}
