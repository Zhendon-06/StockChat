package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.base.BasePager
import com.guet.liang.stockchat.base.bridgeModule
import com.guet.liang.stockchat.base.setTimeout
import com.guet.liang.stockchat.data.MimoApiConfig
import com.guet.liang.stockchat.data.ChatHistoryDatabase
import com.guet.liang.stockchat.data.ChatHistoryRepository
import com.guet.liang.stockchat.data.MimoSpeechRecognitionService
import com.guet.liang.stockchat.data.MimoStockChatDataSource
import com.guet.liang.stockchat.data.StockChatDataSource
import com.guet.liang.stockchat.model.AnswerBlock
import com.guet.liang.stockchat.model.ChatAnswer
import com.guet.liang.stockchat.model.ChatHistoryItem
import com.guet.liang.stockchat.model.ChatMessage
import com.guet.liang.stockchat.model.ChatRole
import com.guet.liang.stockchat.model.MessageState
import com.guet.liang.stockchat.model.SpeechRecognitionResult
import com.guet.liang.stockchat.model.StockQuote
import com.guet.liang.stockchat.model.VoiceInputState
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.ViewRef
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.base.attr.CaptureRule
import com.tencent.kuikly.core.base.attr.CaptureRuleDirection
import com.tencent.kuikly.core.base.event.PanGestureParams
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.ScrollerView
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.TextArea
import com.tencent.kuikly.core.views.TextAreaView
import com.tencent.kuikly.core.views.View

private const val CHAT_PAGE_NAME = "router"
private const val STOCK_DETAIL_PAGE_NAME = "stock_detail"

@Page(CHAT_PAGE_NAME, supportInLocal = true)
internal class StockChatPage : BasePager() {
    private var drawerOpen by observable(false)
    private var composerFocused by observable(false)
    private var keyboardHeight by observable(0f)
    private var keyboardVisible by observable(false)
    private var inputText by observable("")
    private var isSending by observable(false)
    private var voiceInputState by observable(VoiceInputState.IDLE)
    private var messages by observableList<ChatMessage>()
    private var messageSequence = 0
    private var drawerAskToken = 0
    private var requestToken = 0
    private var voiceRequestToken = 0
    private var drawerPanStartX = 0f
    private var drawerPanStartY = 0f
    private var messageListNearBottom = true
    private var stickMessageListToBottom = true
    private lateinit var dataSource: StockChatDataSource
    private lateinit var speechRecognitionService: MimoSpeechRecognitionService
    private lateinit var chatHistoryRepository: ChatHistoryRepository

    private lateinit var inputRef: ViewRef<TextAreaView>
    private lateinit var messageScrollerRef: ViewRef<ScrollerView<*, *>>

    private val layoutMetrics: StockChatLayoutMetrics
        get() = StockChatLayoutMetrics(pagerData.pageViewWidth)

    override fun created() {
        super.created()
        val config = MimoApiConfig(
            apiKey = pageData.params.optString("mimoApiKey").trim(),
        )
        val networkModule = acquireModule<NetworkModule>(NetworkModule.MODULE_NAME)
        chatHistoryRepository = ChatHistoryDatabase.repository()
        dataSource = MimoStockChatDataSource(
            networkModule = networkModule,
            config = config,
            bridgeModule = bridgeModule,
            useNativeStreaming = pageData.params.optInt("mimoNativeStreaming") == 1,
        )
        speechRecognitionService = MimoSpeechRecognitionService(networkModule, config)
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
        super.pageDidDisappear()
    }

    override fun pageWillDestroy() {
        cancelVoiceInput()
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
                            text("MiMo AI 股票问答 · 体验版")
                            fontSize(metrics.dp(12f))
                            color(StockChatTheme.textSecondary)
                            marginTop(metrics.dp(2f))
                        }
                    }
                }
                View {
                    attr {
                        size(metrics.dp(38f), metrics.dp(38f))
                        borderRadius(metrics.dp(19f))
                        border(Border(1f, BorderStyle.SOLID, StockChatTheme.border))
                        allCenter()
                    }
                    event {
                        click { ctx.bridgeModule.toast("设置功能为演示入口") }
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
            ctx.DrawerMenuItem(this, "◷", "市场概览", metrics.scale) {
                ctx.closeDrawerThenAsk("今天大盘表现如何？")
            }
            ctx.DrawerMenuItem(this, "◎", "指数追踪", metrics.scale) {
                ctx.closeDrawerThenAsk("分析一下沪深300")
            }
            ctx.DrawerMenuItem(this, "◇", "AI 选股思路", metrics.scale) {
                ctx.closeDrawerThenAsk("有哪些风险要关注？")
            }
            View {
                attr {
                    height(1f)
                    backgroundColor(StockChatTheme.border)
                    marginTop(metrics.dp(6f))
                    marginBottom(metrics.dp(18f))
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
            View {
                attr {
                    height(metrics.dp(48f))
                    flexDirectionRow()
                    alignItemsCenter()
                    marginTop(metrics.dp(8f))
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
            ctx.DrawerConversation(this, "沪深300 今日表现", "指数 · 刚刚", metrics.scale) {
                ctx.closeDrawerThenAsk("分析一下沪深300")
            }
            ctx.DrawerConversation(this, "贵州茅台行情速览", "个股 · 演示", metrics.scale) {
                ctx.closeDrawerThenAsk("贵州茅台今天怎么样？")
            }
            View {
                attr {
                    absolutePosition(
                        left = metrics.dp(22f),
                        right = metrics.dp(22f),
                        bottom = pagerData.safeAreaInsets.bottom + metrics.dp(18f),
                    )
                    padding(top = metrics.dp(14f))
                    border(Border(1f, BorderStyle.SOLID, StockChatTheme.border))
                    borderRadius(metrics.dp(16f))
                    backgroundColor(Color(0xFFFAFBF9))
                }
                Text {
                    attr {
                        text("DEMO MODE")
                        fontSize(metrics.dp(10f))
                        fontWeightBold()
                        color(StockChatTheme.accent)
                        margin(left = metrics.dp(14f), right = metrics.dp(14f))
                    }
                }
                Text {
                    attr {
                        text("AI 回答由 MiMo 生成，行情卡为本地演示数据。")
                        fontSize(metrics.dp(12f))
                        lineHeight(metrics.dp(18f))
                        color(StockChatTheme.textSecondary)
                        margin(
                            top = metrics.dp(6f),
                            left = metrics.dp(14f),
                            bottom = metrics.dp(14f),
                            right = metrics.dp(14f),
                        )
                    }
                }
            }
        }
        }
    }

    private fun DrawerMenuItem(
        container: ViewContainer<*, *>,
        icon: String,
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
            Text {
                attr {
                    text(icon)
                    fontSize(23f * scale)
                    color(StockChatTheme.textPrimary)
                    width(42f * scale)
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
        title: String,
        meta: String,
        scale: Float,
        onClick: () -> Unit,
    ) {
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
                marginBottom(5f * scale)
            }
            event {
                click { onClick() }
            }
            Text {
                attr {
                    text(title)
                    fontSize(14f * scale)
                    fontWeightMedium()
                    color(StockChatTheme.textPrimary)
                }
            }
            Text {
                attr {
                    text(meta)
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
                        top = pagerData.statusBarHeight + metrics.dp(14f),
                        left = metrics.dp(18f),
                    )
                    zIndex(4)
                }
                HamburgerButton(scale = metrics.scale) {
                    if (ctx::inputRef.isInitialized) {
                        ctx.inputRef.view?.blur()
                    }
                    ctx.openDrawer()
                }
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
                        ),
                    )
                    animate(Animation.easeInOut(0.22f), ctx.keyboardHeight)
                    animate(Animation.easeInOut(0.26f), ctx.composerFocused)
                }
                vif({ ctx.messages.isEmpty() }) {
                    ctx.WelcomeContent(this)
                }
                vif({ ctx.messages.isNotEmpty() }) {
                    ctx.MessageList(this)
                }
            }
            ctx.ComposerDock(this)
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
                        bottom = metrics.dp(70f),
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
                        resizeStretch()
                        src(ImageUri.commonAssets("stockchat_logo.png"))
                    }
                }
                Text {
                    attr {
                        text("StockChat · MiMo，我帮你看行情")
                        fontSize(metrics.dp(22f))
                        fontWeightBold()
                        color(StockChatTheme.textPrimary)
                        textAlignCenter()
                        marginTop(metrics.dp(18f))
                    }
                }
                Text {
                    attr {
                        text("问个股、看指数，也可以聊市场风险")
                        fontSize(metrics.dp(14f))
                        color(StockChatTheme.textSecondary)
                        textAlignCenter()
                        marginTop(metrics.dp(8f))
                    }
                }
            }
            Scroller {
                attr {
                    absolutePosition(left = 0f, right = 0f, bottom = metrics.dp(8f))
                    height(metrics.dp(50f))
                    flexDirectionRow()
                    padding(left = metrics.dp(18f), right = metrics.dp(18f))
                    showScrollerIndicator(false)
                    bouncesEnable(true)
                    scrollEnable(true)
                }
                PromptChip("贵州茅台今天怎么样？", metrics.scale) {
                    ctx.sendMessage("贵州茅台今天怎么样？")
                }
                PromptChip("分析沪深300", metrics.scale) {
                    ctx.sendMessage("分析一下沪深300")
                }
                PromptChip("市场有哪些风险？", metrics.scale) {
                    ctx.sendMessage("市场有哪些风险要关注？")
                }
                PromptChip("指数走势怎么走？", metrics.scale) {
                    ctx.sendMessage("指数走势怎么走？")
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
                padding(top = 14f, bottom = 12f)
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
                height(metrics.composerDockHeight(focused = true))
                zIndex(6)
                animate(Animation.easeInOut(0.22f), ctx.keyboardHeight)
            }
            View {
                attr {
                    absolutePosition(
                        left = 0f,
                        right = 0f,
                        bottom = metrics.composerFooterHeight,
                    )
                    height(metrics.composerPanelHeight(ctx.composerFocused))
                    borderRadius(metrics.dp(if (ctx.composerFocused) 26f else 34f))
                    backgroundColor(StockChatTheme.surface)
                    border(Border(1f, BorderStyle.SOLID, StockChatTheme.borderStrong))
                    overflow(true)
                    animate(Animation.easeInOut(0.26f), ctx.composerFocused)
                }
                TextArea {
                    ref {
                        ctx.inputRef = it
                    }
                    attr {
                        absolutePosition(
                            top = if (ctx.composerFocused) metrics.dp(14f) else metrics.dp(24f),
                            left = metrics.dp(if (ctx.composerFocused) 20f else 61f),
                            right = metrics.dp(if (ctx.composerFocused) 20f else 60f),
                        )
                        height(metrics.dp(38f))
                        fontSize(metrics.dp(17f))
                        lineHeight(metrics.dp(23f))
                        color(StockChatTheme.textPrimary)
                        tintColor(StockChatTheme.accent)
                        placeholder(
                            if (ctx.composerFocused) {
                                "发消息…"
                            } else {
                                ctx.voiceInputHint()
                            }
                        )
                        placeholderColor(StockChatTheme.textTertiary)
                        returnKeyTypeSend()
                        enablesReturnKeyAutomatically(true)
                        maxTextLength(300)
                        touchEnable(ctx.composerFocused)
                        zIndex(2)
                        animate(Animation.easeInOut(0.26f), ctx.composerFocused)
                    }
                    event {
                        textDidChange(isSyncEdit = true) {
                            ctx.inputText = it.text
                        }
                        inputFocus {
                            ctx.inputText = it.text
                            ctx.closeDrawer()
                            ctx.composerFocused = true
                        }
                        inputBlur {
                            ctx.inputText = it.text
                            ctx.composerFocused = false
                        }
                        keyboardHeightChange {
                            val nextKeyboardHeight = maxOf(it.height, 0f)
                            val nextKeyboardVisible = nextKeyboardHeight > 0.5f
                            val keyboardWasVisible = ctx.keyboardHeight > 0f
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
                View {
                    attr {
                        absolutePosition(
                            top = 0f,
                            left = metrics.dp(51f),
                            right = metrics.dp(51f),
                            bottom = 0f,
                        )
                        touchEnable(!ctx.composerFocused)
                        zIndex(3)
                    }
                    event {
                        click { ctx.focusComposer() }
                    }
                }
                View {
                    attr {
                        absolutePosition(
                            top = metrics.dp(if (ctx.composerFocused) 64f else 17f),
                            left = metrics.dp(17f),
                        )
                        size(metrics.dp(27f), metrics.dp(34f))
                        zIndex(4)
                        animate(Animation.easeInOut(0.26f), ctx.composerFocused)
                    }
                    ctx.VoiceMark(this, metrics.scale) {
                        ctx.toggleVoiceInput()
                    }
                }
                View {
                    attr {
                        absolutePosition(
                            top = metrics.dp(if (ctx.composerFocused) 64f else 17f),
                            right = metrics.dp(if (ctx.composerFocused) 68f else 17f),
                        )
                        size(metrics.dp(34f), metrics.dp(34f))
                        zIndex(4)
                        animate(Animation.easeInOut(0.26f), ctx.composerFocused)
                    }
                    ctx.PlusMark(this, metrics.scale) {
                        ctx.bridgeModule.toast("可扩展图片、文件与自选股")
                    }
                }
                View {
                    attr {
                        absolutePosition(
                            top = metrics.dp(64f),
                            left = metrics.dp(58f),
                        )
                        height(metrics.dp(34f))
                        borderRadius(metrics.dp(17f))
                        padding(left = metrics.dp(10f), right = metrics.dp(10f))
                        backgroundColor(StockChatTheme.recessed)
                        border(Border(1f, BorderStyle.SOLID, StockChatTheme.border))
                        flexDirectionRow()
                        alignItemsCenter()
                        opacity(if (ctx.composerFocused) 1f else 0f)
                        touchEnable(ctx.composerFocused)
                        zIndex(3)
                        transform(Translate(0f, if (ctx.composerFocused) 0f else 0.2f))
                        animate(Animation.easeOut(0.2f), ctx.composerFocused)
                    }
                    Text {
                        attr {
                            text("MiMo")
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
                        absolutePosition(
                            top = metrics.dp(60f),
                            right = metrics.dp(14f),
                        )
                        size(metrics.dp(42f), metrics.dp(42f))
                        borderRadius(metrics.dp(21f))
                        backgroundColor(
                            if (ctx.inputText.isBlank() || ctx.isSending) {
                                Color(0xFFE4EAE7)
                            } else {
                                StockChatTheme.accent
                            }
                        )
                        allCenter()
                        opacity(if (ctx.composerFocused) 1f else 0f)
                        touchEnable(ctx.composerFocused)
                        zIndex(4)
                        transform(Translate(0f, if (ctx.composerFocused) 0f else 0.2f))
                        animate(Animation.easeOut(0.2f), ctx.composerFocused)
                        animation(Animation.easeOut(0.16f), ctx.inputText)
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
            View {
                attr {
                    absolutePosition(
                        left = 0f,
                        right = 0f,
                        bottom = 0f,
                    )
                    height(metrics.dp(16f))
                    justifyContentCenter()
                    opacity(if (ctx.composerFocused) 0f else 1f)
                    animation(Animation.easeOut(0.14f), ctx.composerFocused)
                }
            }
        }
        }
    }

    private fun focusComposer() {
        composerFocused = true
        closeDrawer()
        setTimeout(0) {
            if (::inputRef.isInitialized) {
                inputRef.view?.focus()
            }
        }
    }

    private fun VoiceMark(
        container: ViewContainer<*, *>,
        scale: Float,
        onClick: () -> Unit,
    ) {
        val ctx = this
        with(container) {
        View {
            attr {
                size(27f * scale, 34f * scale)
                justifyContentCenter()
            }
            event {
                click { onClick() }
            }
            Text {
                attr {
                    text(if (ctx.voiceInputState == VoiceInputState.RECORDING) "■" else "◖))")
                    fontSize(18f * scale)
                    fontWeightBold()
                    color(
                        when (ctx.voiceInputState) {
                            VoiceInputState.RECORDING -> StockChatTheme.positive
                            VoiceInputState.STARTING,
                            VoiceInputState.TRANSCRIBING -> StockChatTheme.accent
                            VoiceInputState.IDLE -> StockChatTheme.textPrimary
                        }
                    )
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
        with(container) {
        View {
            attr {
                size(34f * scale, 34f * scale)
                allCenter()
            }
            event {
                click { onClick() }
            }
            View {
                attr {
                    size(24f * scale, 2f * scale)
                    borderRadius(1f * scale)
                    backgroundColor(StockChatTheme.textPrimary)
                }
            }
            View {
                attr {
                    absolutePosition(top = 5f * scale, left = 16f * scale)
                    size(2f * scale, 24f * scale)
                    borderRadius(1f * scale)
                    backgroundColor(StockChatTheme.textPrimary)
                }
            }
        }
        }
    }

    private fun voiceInputHint(): String {
        return when (voiceInputState) {
            VoiceInputState.IDLE -> "发消息或点击麦克风说话"
            VoiceInputState.STARTING -> "正在启动麦克风…"
            VoiceInputState.RECORDING -> "正在录音，再点一次结束"
            VoiceInputState.TRANSCRIBING -> "MiMo 正在识别语音…"
        }
    }

    private fun toggleVoiceInput() {
        when (voiceInputState) {
            VoiceInputState.IDLE -> startVoiceInput()
            VoiceInputState.RECORDING -> stopVoiceInput()
            VoiceInputState.STARTING -> bridgeModule.toast("正在等待麦克风，请稍候")
            VoiceInputState.TRANSCRIBING -> bridgeModule.toast("MiMo 正在识别，请稍候")
        }
    }

    private fun startVoiceInput() {
        if (isSending) {
            bridgeModule.toast("请等待当前回答完成")
            return
        }
        if (::inputRef.isInitialized) {
            inputRef.view?.blur()
        }
        composerFocused = false
        voiceRequestToken += 1
        val currentVoiceToken = voiceRequestToken
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
                setTimeout(30_000) {
                    if (
                        currentVoiceToken == voiceRequestToken &&
                        voiceInputState == VoiceInputState.RECORDING
                    ) {
                        stopVoiceInput()
                    }
                }
            } else {
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
                            inputText = recognizedText
                            if (::inputRef.isInitialized) {
                                inputRef.view?.setText(recognizedText)
                            }
                            focusComposer()
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
        if (
            voiceInputState == VoiceInputState.STARTING ||
            voiceInputState == VoiceInputState.RECORDING
        ) {
            bridgeModule.cancelVoiceRecording()
        }
        voiceInputState = VoiceInputState.IDLE
    }

    private fun sendMessage(submittedText: String? = null) {
        if (isSending) {
            return
        }
        if (voiceInputState != VoiceInputState.IDLE) {
            bridgeModule.toast("请先结束语音输入")
            return
        }
        val question = (submittedText ?: inputText).trim()
        if (question.isEmpty()) {
            bridgeModule.toast("请输入想了解的股票或指数")
            return
        }
        if (::inputRef.isInitialized) {
            inputRef.view?.setText("")
        }
        inputText = ""
        isSending = true
        stickMessageListToBottom = true
        messageListNearBottom = true
        requestToken += 1
        val currentRequestToken = requestToken
        val userMessage = ChatMessage(
                id = nextMessageId(),
                role = ChatRole.USER,
                blocks = listOf(AnswerBlock.Markdown(question, question)),
            )
        messages.add(userMessage)
        persistChatHistory()
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
                    ChatAnswer.Failure("MiMo 服务暂时不可用，请稍后重试。"),
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

    private fun closeDrawerThenAsk(question: String) {
        drawerAskToken += 1
        val token = drawerAskToken
        drawerOpen = false
        setTimeout(260) {
            if (token == drawerAskToken) {
                sendMessage(question)
            }
        }
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
        drawerAskToken += 1
        drawerOpen = true
    }

    private fun closeDrawer() {
        drawerAskToken += 1
        drawerOpen = false
    }

    private fun startNewChat() {
        cancelVoiceInput()
        drawerAskToken += 1
        requestToken += 1
        messages.clear()
        chatHistoryRepository.clearActiveSession()
        inputText = ""
        isSending = false
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
        return "message_$messageSequence"
    }

    private fun persistChatHistory() {
        chatHistoryRepository.replaceMessages(messages)
    }

    private fun restoreChatHistory() {
        if (messages.isNotEmpty()) {
            return
        }
        chatHistoryRepository.loadMessages().forEach { message ->
            messages.add(message)
            message.id.removePrefix("message_").toIntOrNull()?.let {
                messageSequence = maxOf(messageSequence, it)
            }
        }
    }

    companion object {
        private const val MAX_HISTORY_TURNS = 6
        private const val DRAWER_SWIPE_DISTANCE = 56f
    }
}
