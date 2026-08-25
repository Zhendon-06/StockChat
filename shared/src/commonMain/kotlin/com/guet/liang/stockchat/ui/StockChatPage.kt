package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.base.BasePager
import com.guet.liang.stockchat.base.bridgeModule
import com.guet.liang.stockchat.base.setTimeout
import com.guet.liang.stockchat.data.AliyunApiConfig
import com.guet.liang.stockchat.data.AliyunStockChatDataSource
import com.guet.liang.stockchat.data.ChatHistoryDatabase
import com.guet.liang.stockchat.data.ChatHistoryRepository
import com.guet.liang.stockchat.data.ChatSessionSummary
import com.guet.liang.stockchat.data.MimoSpeechRecognitionService
import com.guet.liang.stockchat.data.MimoSpeechSynthesisService
import com.guet.liang.stockchat.data.MimoVoiceApiConfig
import com.guet.liang.stockchat.data.StockChatDataSource
import com.guet.liang.stockchat.model.AnswerBlock
import com.guet.liang.stockchat.model.ChatAnswer
import com.guet.liang.stockchat.model.ChatHistoryItem
import com.guet.liang.stockchat.model.ChatMessage
import com.guet.liang.stockchat.model.ChatRole
import com.guet.liang.stockchat.model.MessageState
import com.guet.liang.stockchat.model.SpeechRecognitionResult
import com.guet.liang.stockchat.model.SpeechSynthesisResult
import com.guet.liang.stockchat.model.StockQuote
import com.guet.liang.stockchat.model.VoiceInputState
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

private data class StockChatSuggestion(val iconAsset: String, val text: String)

@Page(CHAT_PAGE_NAME, supportInLocal = true)
internal class StockChatPage : BasePager() {
    private var drawerOpen by observable(false)
    private var composerFocused by observable(false)
    private var keyboardHeight by observable(0f)
    private var keyboardVisible by observable(false)
    // 跟随系统键盘动画时长（秒），由 keyboardHeightChange 事件携带，使输入框与键盘同速联动
    private var keyboardAnimDuration by observable(0.2f)
    private var inputText by observable("")
    // 输入内容折行后的行数（估算），驱动输入框与面板同步增高
    private var inputLineCount by observable(1)
    private var isSending by observable(false)
    private var voiceInputState by observable(VoiceInputState.IDLE)
    private var voiceMode by observable(false)
    private var voicePressActive by observable(false)
    private var voicePressCanceled by observable(false)
    private var voiceWavePhase by observable(0)
    private var imagePickerOpen by observable(false)
    private var messages by observableList<ChatMessage>()
    private var recentSessions by observableList<ChatSessionSummary>()
    private var selectedImages by observableList<String>()
    private var messageSequence = 0
    private var sessionSequence = 0
    // 当前会话 id：必须是 observable，抽屉列表项的高亮依赖它驱动重渲染
    private var activeSessionId by observable("")
    private var requestToken = 0
    private var voiceRequestToken = 0
    private var speechSynthesisRequestToken = 0
    private var voicePressStartY = 0f
    private var voicePressReleaseRequested = false
    private var voiceWaveTimer: Timer? = null
    private var drawerPanStartX = 0f
    private var drawerPanStartY = 0f
    private var messageListNearBottom = true
    private var stickMessageListToBottom = true
    private lateinit var dataSource: StockChatDataSource
    private lateinit var speechRecognitionService: MimoSpeechRecognitionService
    private lateinit var speechSynthesisService: MimoSpeechSynthesisService
    private lateinit var chatHistoryRepository: ChatHistoryRepository

    private lateinit var inputRef: ViewRef<TextAreaView>
    private lateinit var messageScrollerRef: ViewRef<ScrollerView<*, *>>

    private val layoutMetrics: StockChatLayoutMetrics
        get() = StockChatLayoutMetrics(pagerData.pageViewWidth)

    override fun created() {
        super.created()
        val qwenConfig = AliyunApiConfig(
            apiKey = pageData.params.optString("qwenApiKey").trim(),
        )
        val mimoVoiceConfig = MimoVoiceApiConfig(
            apiKey = pageData.params.optString("mimoVoiceApiKey").trim(),
        )
        val networkModule = acquireModule<NetworkModule>(NetworkModule.MODULE_NAME)
        chatHistoryRepository = ChatHistoryDatabase.repository()
        dataSource = AliyunStockChatDataSource(
            networkModule = networkModule,
            config = qwenConfig,
            bridgeModule = bridgeModule,
            useNativeStreaming = pageData.params.optInt(
                "aliyunNativeStreaming",
                pageData.params.optInt("mimoNativeStreaming"),
            ) == 1,
        )
        speechRecognitionService = MimoSpeechRecognitionService(networkModule, mimoVoiceConfig)
        speechSynthesisService = MimoSpeechSynthesisService(networkModule, mimoVoiceConfig)
        restoreChatHistory()
        bridgeModule.observeDrawerGestures { result ->
            when (result?.optString("direction")) {
                "right" -> openDrawer()
                "left" -> closeDrawer()
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
        if (::chatHistoryRepository.isInitialized) {
            persistChatHistory()
        }
        bridgeModule.stopObservingDrawerGestures()
        requestToken += 1
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
                        click { ctx.bridgeModule.toast("设置功能暂未开放") }
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
            View {
                attr {
                    height(metrics.dp(44f))
                    borderRadius(metrics.dp(22f))
                    backgroundColor(StockChatTheme.recessed)
                    border(Border(1f, BorderStyle.SOLID, StockChatTheme.border))
                    flexDirectionRow()
                    marginTop(metrics.dp(20f))
                    padding(all = metrics.dp(3f))
                }
                View {
                    attr {
                        flex(1f)
                        borderRadius(metrics.dp(19f))
                        backgroundColor(StockChatTheme.surfaceSoft)
                        border(Border(1f, BorderStyle.SOLID, StockChatTheme.borderStrong))
                        allCenter()
                    }
                    Text {
                        attr {
                            text("AI 问答")
                            fontSize(metrics.dp(15f))
                            fontWeightBold()
                            color(StockChatTheme.textPrimary)
                        }
                    }
                }
                View {
                    attr {
                        flex(1f)
                        allCenter()
                    }
                    Text {
                        attr {
                            text("自选行情")
                            fontSize(metrics.dp(15f))
                            color(StockChatTheme.textSecondary)
                        }
                    }
                }
            }
            ctx.DrawerMenuItem(this, "ranking_icon.png", "市场概览", metrics.scale) {
            }
            ctx.DrawerMenuItem(this, "table_icon.png", "指数追踪", metrics.scale) {
            }
            ctx.DrawerMenuItem(this, "ai_generate.png", "AI 选股思路", metrics.scale) {
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
                        text("管理")
                        fontSize(metrics.dp(13f))
                        color(StockChatTheme.accent)
                    }
                }
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
                    click { ctx.selectSession(session.id) }
                }
                Text {
                    attr {
                        text(session.title.ifBlank { "新对话" })
                        fontSize(14f * scale)
                        fontWeightMedium()
                        color(StockChatTheme.textPrimary)
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
            }
            View {
                attr {
                    absolutePosition(
                        top = pagerData.statusBarHeight + metrics.dp(76f),
                        left = 0f,
                        right = 0f,
                    bottom = metrics.composerContentBottom(
                        maxOf(ctx.keyboardHeight, pagerData.safeAreaInsets.bottom),
                        ctx.composerFocused,
                        ctx.voiceMode,
                        ctx.selectedImages.isNotEmpty(),
                        ctx.composerExtraInputLines(),
                    ),
                    )
                    animate(Animation.easeInOut(ctx.keyboardAnimDuration), ctx.keyboardHeight)
                    animate(Animation.easeInOut(0.26f), ctx.composerFocused)
                    animate(Animation.easeInOut(0.26f), ctx.voiceMode)
                    animate(Animation.easeInOut(0.26f), ctx.selectedImages.size)
                    animate(Animation.easeInOut(0.18f), ctx.inputLineCount)
                }
                vif({ ctx.messages.isEmpty() }) {
                    ctx.WelcomeContent(this)
                }
                vif({ ctx.messages.isNotEmpty() }) {
                    ctx.MessageList(this)
                }
            }
            ctx.ConversationTopBar(this)
            ctx.ComposerDock(this)
            ctx.VoiceRecordingOverlay(this)
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
                    absolutePosition(
                        top = pagerData.statusBarHeight + metrics.dp(14f),
                        left = metrics.dp(18f),
                    )
                    zIndex(8)
                }
                HamburgerButton(scale = metrics.scale) {
                    if (ctx::inputRef.isInitialized) {
                        ctx.inputRef.view?.blur()
                    }
                    ctx.openDrawer()
                }
            }
            vif({ ctx.messages.isNotEmpty() }) {
                ctx.ConversationHeader(this)
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
                        click { ctx.startNewChat() }
                    }
                    ctx.NewConversationMark(this, metrics.scale)
                }
                View {
                    attr {
                        size(metrics.dp(44f), metrics.dp(44f))
                        allCenter()
                    }
                    event {
                        click { ctx.bridgeModule.toast("更多选项暂未开放") }
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
                val welcomeHidden = ctx.composerFocused || ctx.keyboardVisible
                absolutePositionAllZero()
                opacity(if (welcomeHidden) 0f else 1f)
                transform(
                    Translate(
                        percentageX = 0f,
                        percentageY = 0f,
                        offsetY = if (welcomeHidden) -metrics.dp(12f) else 0f,
                    )
                )
                animation(Animation.easeInOut(0.26f), ctx.composerFocused)
                animation(Animation.easeInOut(0.26f), ctx.keyboardVisible)
                touchEnable(!welcomeHidden)
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
                Image {
                    attr {
                        size(
                            metrics.welcomeLogoWidth,
                            metrics.welcomeLogoWidth * 200f / 803f,
                        )
                        resizeContain()
                        src(ImageUri.commonAssets("stockchat_logo.png"))
                    }
                }
                Text {
                    attr {
                        text("StockMate，我帮你看行情")
                        fontSize(metrics.dp(23f))
                        fontWeightBold()
                        color(StockChatTheme.textPrimary)
                        textAlignCenter()
                        marginTop(metrics.dp(22f))
                    }
                }
                Text {
                    attr {
                        text("问个股、看指数，也可以聊市场风险")
                        fontSize(metrics.dp(14f))
                        color(StockChatTheme.textSecondary)
                        textAlignCenter()
                        marginTop(metrics.dp(10f))
                    }
                }
            }
            ctx.SuggestionCardRow(this)
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
                        padding(left = metrics.dp(18f), right = metrics.dp(8f))
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
                padding(top = 2f, bottom = 12f)
            }
            event {
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
                    onQuoteClick = { ctx.openStockDetail(it) },
                    onRetry = { ctx.retryMessage(it) },
                    onCopy = { answer ->
                        val content = ctx.messageText(answer)
                        if (content.isNotBlank()) {
                            ctx.bridgeModule.copyToPasteboard(content)
                            ctx.bridgeModule.toast("已复制回答")
                        }
                    },
                    onLike = { ctx.bridgeModule.toast("感谢反馈") },
                    onDislike = { ctx.bridgeModule.toast("已记录反馈") },
                    onReadAloud = { ctx.readMessageAloud(it) },
                    onMore = { ctx.bridgeModule.toast("更多操作暂未开放") },
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
                absolutePosition(
                    left = metrics.dp(18f),
                    right = metrics.dp(18f),
                    bottom = maxOf(ctx.keyboardHeight, pagerData.safeAreaInsets.bottom) +
                        metrics.composerBottomGap,
                )
                height(
                    metrics.composerDockHeight(
                        focused = ctx.composerFocused,
                        voiceMode = ctx.voiceMode,
                        hasAttachments = ctx.selectedImages.isNotEmpty(),
                        extraInputLines = ctx.composerExtraInputLines(),
                    )
                )
                zIndex(6)
                animate(Animation.easeInOut(ctx.keyboardAnimDuration), ctx.keyboardHeight)
                animate(Animation.easeInOut(0.26f), ctx.composerFocused)
                animate(Animation.easeInOut(0.26f), ctx.voiceMode)
                animate(Animation.easeInOut(0.26f), ctx.selectedImages.size)
                animate(Animation.easeInOut(0.18f), ctx.inputLineCount)
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
                            focused = ctx.composerFocused,
                            voiceMode = ctx.voiceMode,
                            hasAttachments = ctx.selectedImages.isNotEmpty(),
                            extraInputLines = ctx.composerExtraInputLines(),
                        )
                    )
                    borderRadius(
                        metrics.dp(
                            if (ctx.composerFocused || ctx.voiceMode || ctx.selectedImages.isNotEmpty()) {
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
                    animate(Animation.easeInOut(0.26f), ctx.composerFocused)
                    animate(Animation.easeInOut(0.26f), ctx.voiceMode)
                    animate(Animation.easeInOut(0.26f), ctx.selectedImages.size)
                    animate(Animation.easeInOut(0.18f), ctx.inputLineCount)
                }
                vif({ ctx.selectedImages.isNotEmpty() }) {
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
                            images = ctx.selectedImages,
                            scale = metrics.scale,
                            onRemove = { ctx.removeSelectedImage(it) },
                        )
                    }
                }
                TextArea {
                        ref {
                            ctx.inputRef = it
                        }
                        attr {
                            val hasAttachments = ctx.selectedImages.isNotEmpty()
                            val expanded = ctx.composerFocused || ctx.voiceMode || hasAttachments
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
                            placeholder(if (ctx.composerFocused) "发消息…" else ctx.voiceInputHint())
                            placeholderColor(
                                if (ctx.voiceMode) Color(0x00000000) else StockChatTheme.textTertiary
                            )
                            returnKeyTypeSend()
                            enablesReturnKeyAutomatically(true)
                            maxTextLength(300)
                            touchEnable(ctx.composerFocused && !ctx.voiceMode)
                            zIndex(2)
                            animate(Animation.easeInOut(0.26f), ctx.composerFocused)
                            animate(Animation.easeInOut(0.26f), ctx.voiceMode)
                            animate(Animation.easeInOut(0.26f), ctx.selectedImages.size)
                            animate(Animation.easeInOut(0.18f), ctx.inputLineCount)
                        }
                        event {
                            textDidChange(isSyncEdit = true) {
                                ctx.inputText = it.text
                                ctx.updateInputLineMetrics(it.text)
                            }
                            inputFocus {
                                ctx.inputText = it.text
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
                                ctx.updateInputLineMetrics(it.text)
                            }
                            inputBlur {
                                ctx.inputText = it.text
                                ctx.composerFocused = false
                            }
                            keyboardHeightChange {
                                val nextKeyboardHeight = maxOf(it.height, 0f)
                                val nextKeyboardVisible = nextKeyboardHeight > 0.5f
                                val keyboardWasVisible = ctx.keyboardHeight > 0f
                                // 先记录本次键盘动画时长，再更新高度，保证后续布局动画与键盘同速
                                ctx.keyboardAnimDuration = if (it.duration > 0.01f) it.duration else 0f
                                ctx.keyboardHeight = nextKeyboardHeight
                                if (ctx.keyboardVisible != nextKeyboardVisible) {
                                    ctx.keyboardVisible = nextKeyboardVisible
                                }
                                if (
                                    keyboardWasVisible &&
                                    nextKeyboardHeight <= 0.5f &&
                                    ctx.composerFocused
                                ) {
                                    ctx.composerFocused = false
                                    setTimeout(0) {
                                        if (ctx::inputRef.isInitialized) {
                                            ctx.inputRef.view?.blur()
                                        }
                                    }
                                }
                            }
                            inputReturn {
                                ctx.sendMessage(it.text)
                            }
                        }
                }
                vif({ ctx.voiceMode }) {
                    Text {
                        attr {
                            val attachmentOffset = if (ctx.selectedImages.isNotEmpty()) {
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
                        }
                    }
                }
                View {
                    attr {
                        val hasAttachments = ctx.selectedImages.isNotEmpty()
                        val attachmentOffset = if (hasAttachments) metrics.composerAttachmentStripHeight else 0f
                        absolutePosition(
                            top = attachmentOffset,
                            left = metrics.dp(51f),
                            right = metrics.dp(51f),
                            bottom = if (ctx.voiceMode || hasAttachments) metrics.dp(52f) else 0f,
                        )
                        touchEnable(!ctx.composerFocused)
                        zIndex(3)
                        capture(if (ctx.voiceMode) CaptureRule.longPress() else null)
                    }
                    event {
                        click {
                            if (!ctx.voiceMode) {
                                ctx.focusComposer()
                            }
                        }
                        longPress { params ->
                            if (ctx.voiceMode) {
                                ctx.handleVoiceLongPress(params)
                            }
                        }
                        touchCancel {
                            if (ctx.voiceMode) {
                                ctx.cancelVoicePress()
                            }
                        }
                        touchUp {
                            if (ctx.voiceMode) {
                                ctx.finishVoicePress()
                            }
                        }
                    }
                }
                View {
                    attr {
                        val expanded = ctx.composerFocused ||
                            ctx.voiceMode ||
                            ctx.selectedImages.isNotEmpty()
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
                        zIndex(4)
                        animate(Animation.easeInOut(0.26f), ctx.composerFocused)
                        animate(Animation.easeInOut(0.26f), ctx.voiceMode)
                        animate(Animation.easeInOut(0.26f), ctx.selectedImages.size)
                    }
                    View {
                        attr {
                            size(metrics.dp(34f), metrics.dp(34f))
                            allCenter()
                        }
                        ctx.InputModeMark(this, metrics.scale) {
                            ctx.toggleVoiceMode()
                        }
                    }
                    View {
                        attr {
                            val showModel = ctx.composerFocused ||
                                ctx.voiceMode ||
                                ctx.selectedImages.isNotEmpty()
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
                        Text {
                            attr {
                                text("Qwen")
                                fontSize(metrics.dp(11f))
                                fontWeightBold()
                                color(StockChatTheme.accent)
                            }
                        }
                        Text {
                            attr {
                                text("  Auto⌄")
                                fontSize(metrics.dp(14f))
                                fontWeightMedium()
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
                            ctx.openImagePicker()
                        }
                    }
                    // 发送按钮用 vif 整体挂载/移除：此前用 width/opacity 动画收起，
                    // 在进入语音模式时收起动画可能不生效，导致灰色圆形残留并遮挡加号按钮
                    vif({ ctx.composerFocused && !ctx.voiceMode }) {
                        View {
                            attr {
                                val hasAttachments = ctx.selectedImages.isNotEmpty()
                                val canSend = !ctx.isSending && (ctx.inputText.isNotBlank() || hasAttachments)
                                width(metrics.dp(42f))
                                height(metrics.dp(42f))
                                borderRadius(metrics.dp(21f))
                                marginLeft(metrics.dp(12f))
                                backgroundColor(
                                    if (!canSend) {
                                        Color(0xFFE4EAE7)
                                    } else {
                                        StockChatTheme.accent
                                    }
                                )
                                allCenter()
                                touchEnable(canSend)
                            }
                            event {
                                click { ctx.sendMessage() }
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
                    height(metrics.dp(16f))
                    justifyContentCenter()
                    alignItemsCenter()
                    opacity(if (ctx.composerFocused) 0f else 1f)
                    animation(Animation.easeOut(0.14f), ctx.composerFocused)
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
                            ColorStop(
                                if (ctx.voicePressCanceled) Color(0xCDE7B35A) else Color(0xC843D7BB),
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

    private fun focusComposer() {
        composerFocused = true
        voiceMode = false
        closeDrawer()
        focusTextInputAfterLayout()
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

    // 输入框处于聚焦非语音态时，超出单行的部分行数，用于撑高输入框和面板
    private fun composerExtraInputLines(): Int {
        return if (composerFocused && !voiceMode) {
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
            VoiceInputState.IDLE -> "发消息或按住说话"
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
            voiceMode = false
            closeDrawer()
            focusTextInputAfterLayout()
            return
        }
        cancelVoiceInput()
        if (::inputRef.isInitialized) {
            inputRef.view?.blur()
        }
        voiceMode = true
        composerFocused = false
        keyboardVisible = false
        keyboardHeight = 0f
        closeDrawer()
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
        composerFocused = false
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
                                sendMessage(recognizedText)
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
        val remainingCount = MAX_IMAGE_SELECTION_COUNT - selectedImages.size
        if (remainingCount <= 0) {
            bridgeModule.toast("最多选择 9 张图片")
            return
        }
        imagePickerOpen = true
        bridgeModule.pickImages(remainingCount) pickerResult@{ result ->
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
            var addedCount = 0
            if (imageArray != null) {
                for (index in 0 until imageArray.length()) {
                    if (selectedImages.size >= MAX_IMAGE_SELECTION_COUNT) {
                        break
                    }
                    val imageUri = imageArray.optString(index).orEmpty().trim()
                    if (imageUri.isNotEmpty() && imageUri !in selectedImages) {
                        selectedImages.add(imageUri)
                        addedCount += 1
                    }
                }
            }
            if (addedCount == 0) {
                bridgeModule.toast("没有选择新的图片")
            } else if (result.optInt("truncated", 0) == 1) {
                bridgeModule.toast("已保留前 9 张图片")
            }
        }
    }

    private fun removeSelectedImage(imageUri: String) {
        selectedImages.remove(imageUri)
    }

    private fun sendMessage(submittedText: String? = null) {
        if (isSending) {
            return
        }
        if (voiceInputState != VoiceInputState.IDLE) {
            bridgeModule.toast("请先结束语音输入")
            return
        }
        val attachedImages = selectedImages.toList()
        val typedQuestion = (submittedText ?: inputText).trim()
        val question = typedQuestion.ifBlank {
            if (attachedImages.isNotEmpty()) IMAGE_ONLY_QUESTION else ""
        }
        if (question.isEmpty() && attachedImages.isEmpty()) {
            bridgeModule.toast("请输入想了解的股票或指数")
            return
        }
        if (::inputRef.isInitialized) {
            inputRef.view?.setText("")
            inputRef.view?.blur()
        }
        inputText = ""
        inputLineCount = 1
        selectedImages.clear()
        voiceMode = false
        composerFocused = false
        keyboardVisible = false
        keyboardHeight = 0f
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
                        add(AnswerBlock.ImageGallery(attachedImages))
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
        completeAnswer(answerId, question, 0, currentRequestToken)
    }

    private fun completeAnswer(
        messageId: String,
        question: String,
        attempt: Int,
        currentRequestToken: Int,
    ) {
        val history = conversationHistoryBefore(messageId)
        runCatching {
            dataSource.answer(question, history, attempt) response@{ answer ->
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
                    ChatAnswer.Failure("阿里云 AI 服务暂时不可用，请稍后重试。"),
                )
            }
        }
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
                    is AnswerBlock.MarketQuote -> null
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
            state = MessageState.GENERATING,
            retryAttempt = message.retryAttempt + 1,
            errorMessage = "",
        )
        persistChatHistory()
        completeAnswer(
            message.id,
            message.retryQuestion,
            message.retryAttempt + 1,
            currentRequestToken,
        )
    }

    private fun openStockDetail(quote: StockQuote) {
        cancelVoiceInput()
        if (::inputRef.isInitialized) {
            inputRef.view?.blur()
        }
        val params = JSONObject()
        params.put("symbol", quote.symbol)
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(STOCK_DETAIL_PAGE_NAME, params)
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
        drawerOpen = true
    }

    private fun closeDrawer() {
        drawerOpen = false
    }

    private fun selectSession(sessionId: String) {
        if (sessionId == activeSessionId) {
            closeDrawer()
            return
        }
        cancelVoiceInput()
        persistChatHistory()
        requestToken += 1
        activeSessionId = sessionId
        messages.clear()
        messageSequence = 0
        isSending = false
        loadMessagesForActiveSession()
        closeDrawer()
    }

    private fun startNewChat() {
        cancelVoiceInput()
        persistChatHistory()
        requestToken += 1
        activeSessionId = nextSessionId()
        messages.clear()
        inputText = ""
        inputLineCount = 1
        selectedImages.clear()
        voiceMode = false
        isSending = false
        composerFocused = false
        keyboardVisible = false
        keyboardHeight = 0f
        stickMessageListToBottom = true
        messageListNearBottom = true
        drawerOpen = false
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
        bridgeModule.toast("MiMo 正在生成语音…")
        speechSynthesisService.synthesize(content) synthesis@{ result ->
            if (currentRequestToken != speechSynthesisRequestToken) {
                return@synthesis
            }
            when (result) {
                is SpeechSynthesisResult.Success -> {
                    bridgeModule.playBase64Audio(
                        audioBase64 = result.audioBase64,
                        mimeType = result.mimeType,
                    ) { payload ->
                        if (payload?.optInt("success", 0) != 1) {
                            bridgeModule.toast(
                                payload?.optString("errorMessage")
                                    ?.ifBlank { "语音播放失败，请稍后重试" }
                                    ?: "语音播放失败，请稍后重试"
                            )
                        }
                    }
                }
                is SpeechSynthesisResult.Failure -> bridgeModule.toast(result.message)
            }
        }
    }

    private fun stopSpeechPlayback() {
        speechSynthesisRequestToken += 1
        bridgeModule.stopAudioPlayback()
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

    private fun restoreChatHistory() {
        val sessions = chatHistoryRepository.loadSessions()
        recentSessions.clear()
        sessions.take(MAX_RECENT_SESSIONS).forEach { session ->
            recentSessions.add(session)
            session.id.substringAfterLast('_').toIntOrNull()?.let {
                sessionSequence = maxOf(sessionSequence, it)
            }
        }
        if (activeSessionId.isBlank()) {
            activeSessionId = sessions.firstOrNull()?.id ?: nextSessionId()
        }
        loadMessagesForActiveSession()
    }

    private fun loadMessagesForActiveSession() {
        messages.clear()
        chatHistoryRepository.loadMessages(activeSessionId).forEach { message ->
            messages.add(message)
            message.id.substringAfterLast('_').toIntOrNull()?.let {
                messageSequence = maxOf(messageSequence, it)
            }
        }
    }

    private fun refreshRecentSessions() {
        recentSessions.clear()
        chatHistoryRepository.loadSessions().take(MAX_RECENT_SESSIONS).forEach { session ->
            recentSessions.add(session)
        }
    }

    private fun nextSessionId(): String {
        sessionSequence += 1
        return "session_$sessionSequence"
    }

    companion object {
        private const val MAX_HISTORY_TURNS = 6
        private const val MAX_RECENT_SESSIONS = 6
        private const val MAX_INPUT_LINES = 5
        private const val DRAWER_SWIPE_DISTANCE = 56f
        private const val VOICE_CANCEL_DISTANCE = 56f
        private const val MAX_IMAGE_SELECTION_COUNT = 9
        private const val IMAGE_ONLY_QUESTION = "请分析我上传的图片"
    }
}
