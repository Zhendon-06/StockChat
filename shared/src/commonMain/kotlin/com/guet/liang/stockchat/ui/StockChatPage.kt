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
private const val IMAGE_PREVIEW_PAGE_NAME = "stock_image_preview"
private const val DEFAULT_CHAT_MODEL_ID = "qwen-plus"
private const val HOME_TAB_CHAT = 0
private const val HOME_TAB_WATCHLIST = 1
// 键盘回调未给出动画时长时的兜底值（秒）
private const val DEFAULT_KEYBOARD_ANIM_DURATION = 0.25f

private data class StockChatSuggestion(val iconAsset: String, val text: String)

// 欢迎页输入框上方的快捷问题，点击直接发送
private val WELCOME_SUGGESTIONS = listOf(
    StockChatSuggestion("ranking_icon.png", "今日大盘怎么样"),
    StockChatSuggestion("level_icon.png", "分析一下贵州茅台"),
    StockChatSuggestion("table_icon.png", "看看沪深 300 指数"),
    StockChatSuggestion("ai_generate.png", "现在市场风险大吗"),
)

private data class ChatModelOption(
    val id: String,
    val displayName: String,
    val description: String,
    val badge: String,
    val multiplier: String,
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
    // 主页与抽屉共用的分段 Tab（AI 问答 / 自选行情），保证两处选中态始终一致
    private var selectedHomeTab by observable(HOME_TAB_CHAT)
    // 键盘弹出/输入聚焦时主页内容（欢迎区/自选行情）直接整块卸载，不做联动动画
    private var homeContentHidden by observable(false)
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
    private var modelMenuOpen by observable(false)
    private var selectedModelId by observable(DEFAULT_CHAT_MODEL_ID)
    private var imagePickerOpen by observable(false)
    private var selectedImageCount by observable(0)
    private var messages by observableList<ChatMessage>()
    private var recentSessions by observableList<ChatSessionSummary>()
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
        // 键盘/聚焦任一信号出现，主页内容立即卸载；信号全部消失后立即恢复。
        // 不做任何过渡动画，避免与键盘动画联动时序打架
        bindValueChange({
            composerFocused || keyboardVisible || keyboardHeight > 0f || keyboardDropSettling
        }) { hidden ->
            homeContentHidden = hidden == true
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
            ctx.HomeTabSwitcher(this, marginTopDp = 20f)
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

    // 「AI 问答 / 自选行情」分段开关：主页与抽屉共用，选中态样式由同一状态驱动，保证两处一致
    private fun HomeTabSwitcher(
        container: ViewContainer<*, *>,
        marginTopDp: Float = 0f,
        widthDp: Float? = null,
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
                    flexDirectionRow()
                    padding(all = metrics.dp(3f))
                    if (marginTopDp > 0f) {
                        marginTop(metrics.dp(marginTopDp))
                    }
                }
                ctx.HomeTabItem(this, "AI 问答", HOME_TAB_CHAT)
                ctx.HomeTabItem(this, "自选行情", HOME_TAB_WATCHLIST)
            }
        }
    }

    private fun HomeTabItem(
        container: ViewContainer<*, *>,
        label: String,
        tabIndex: Int,
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
                    animate(Animation.easeOut(0.18f), ctx.selectedHomeTab)
                }
                event {
                    click { ctx.selectHomeTab(tabIndex) }
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
        if (tabIndex == selectedHomeTab) {
            return
        }
        selectedHomeTab = tabIndex
    }

    // 「自选行情」占位内容：功能未上线前在主页给出明确反馈，避免选中后界面无变化
    private fun WatchlistPlaceholder(container: ViewContainer<*, *>) {
        val ctx = this
        val metrics = ctx.layoutMetrics
        with(container) {
            vif({ ctx.selectedHomeTab == HOME_TAB_WATCHLIST && !ctx.homeContentHidden }) {
                View {
                    attr {
                        absolutePositionAllZero()
                        alignItemsCenter()
                        justifyContentCenter()
                        padding(left = metrics.dp(32f), right = metrics.dp(32f))
                    }
                    View {
                    attr {
                        size(metrics.dp(72f), metrics.dp(72f))
                        borderRadius(metrics.dp(22f))
                        backgroundColor(StockChatTheme.accentSoft)
                        flexDirectionRow()
                        alignItemsFlexEnd()
                        justifyContentCenter()
                        padding(bottom = metrics.dp(18f))
                    }
                    // 三根高低错落的行情柱，作为自选行情的示意图形
                    listOf(16f, 28f, 21f).forEach { barHeight ->
                        View {
                            attr {
                                width(metrics.dp(6f))
                                height(metrics.dp(barHeight))
                                borderRadius(metrics.dp(3f))
                                backgroundColor(StockChatTheme.accent)
                                margin(left = metrics.dp(3f), right = metrics.dp(3f))
                            }
                        }
                    }
                }
                Text {
                    attr {
                        text("自选行情")
                        fontSize(metrics.dp(20f))
                        fontWeightBold()
                        color(StockChatTheme.textPrimary)
                        marginTop(metrics.dp(20f))
                    }
                }
                Text {
                    attr {
                        text("自选股与指数追踪功能即将上线\n当前演示版本请先使用 AI 问答")
                        fontSize(metrics.dp(13f))
                        color(StockChatTheme.textSecondary)
                        textAlignCenter()
                        marginTop(metrics.dp(10f))
                    }
                }
                // 与欢迎页同款分段开关，保证切到自选行情后仍可切回 AI 问答
                ctx.HomeTabSwitcher(this, marginTopDp = 24f, widthDp = 232f)
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
                // 点输入框以外的任意区域（顶栏/聊天区/欢迎区等，凡是没被子视图
                // 消费的点击都会冒泡到这里）：键盘弹起时先收键盘（面板保持展开）；
                // 键盘已收起时再点，面板才收缩还给页面空间。输入 dock 自己吞掉
                // 区域内的点击，不会冒泡到这
                click { ctx.handleBlankAreaTap() }
            }
            View {
                attr {
                    absolutePosition(
                        // 顶栏按钮区：top 14dp + 高 52dp = 66dp，内容上缘严格贴住按钮下缘
                        top = pagerData.statusBarHeight + metrics.dp(66f),
                        left = 0f,
                        right = 0f,
                    bottom = metrics.composerContentBottom(
                        maxOf(ctx.keyboardHeight, pagerData.safeAreaInsets.bottom),
                        ctx.composerExpanded,
                        ctx.voiceMode,
                        ctx.selectedImageCount > 0,
                        ctx.composerExtraInputLines(),
                    ) + (ctx.composerDockNudge % 2) * 0.1f,
                    )
                    // 展开态与键盘态解耦后两个信号不会在键盘回落期间并发变化
                    // （收缩只发生在键盘静止时），可以安全地各绑各的动画
                    animate(Animation.easeOut(ctx.keyboardAnimDuration), ctx.keyboardHeight)
                    animate(Animation.easeOut(0.2f), ctx.composerExpanded)
                }
                // 两个模式常驻挂载于同一 vif 内：selectedHomeTab 驱动交叉切换动画，
                // 键盘弹出/收起驱动整块向上飞离/坠回的动画（见各自根节点的 transform）
                vif({ ctx.messages.isEmpty() }) {
                    ctx.WelcomeContent(this)
                    ctx.WatchlistPlaceholder(this)
                }
                vif({ ctx.messages.isNotEmpty() }) {
                    ctx.MessageList(this)
                }
            }
            // 输入框上缘的渐隐过渡：消息延伸到面板顶边，最后一段淡出到页面背景
            vif({ ctx.messages.isNotEmpty() }) {
                View {
                    attr {
                        absolutePosition(
                            left = 0f,
                            right = 0f,
                            bottom = metrics.composerContentBottom(
                                maxOf(ctx.keyboardHeight, pagerData.safeAreaInsets.bottom),
                                ctx.composerExpanded,
                                ctx.voiceMode,
                                ctx.selectedImageCount > 0,
                                ctx.composerExtraInputLines(),
                            ) + (ctx.composerDockNudge % 2) * 0.1f,
                        )
                        height(metrics.composerContentFadeHeight)
                        backgroundLinearGradient(
                            Direction.TO_BOTTOM,
                            ColorStop(Color(0x00F6F7F4), 0f),
                            ColorStop(Color(0xFFF6F7F4), 1f),
                        )
                        touchEnable(false)
                        zIndex(5)
                        // 与消息区容器保持一致的两条动画绑定
                        animate(Animation.easeOut(ctx.keyboardAnimDuration), ctx.keyboardHeight)
                        animate(Animation.easeOut(0.2f), ctx.composerExpanded)
                    }
                }
            }
            ctx.ConversationTopBar(this)
            ctx.ComposerDock(this)
            ctx.VoiceRecordingOverlay(this)
            ctx.MessageMenuOverlay(this)
            ctx.ModelMenuOverlay(this)
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
        vif({ ctx.selectedHomeTab == HOME_TAB_CHAT && !ctx.homeContentHidden }) {
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
                        }
                    }
                    // 欢迎语下方的分段开关：与抽屉内为同一组件、同一状态，选中态完全一致
                    ctx.HomeTabSwitcher(this, marginTopDp = 28f, widthDp = 232f)
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
                            }
                            event {
                                click { ctx.sendMessage(suggestion.text) }
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
                click { ctx.handleBlankAreaTap() }
                dragBegin { ctx.handleBlankAreaTap() }
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
                        onQuoteClick = { ctx.openStockDetail(it) },
                        onImageClick = { ctx.openImagePreview(it) },
                        onRetry = { ctx.retryMessage(it) },
                        onCopy = { ctx.copyMessage(it) },
                        onRegenerate = { ctx.regenerateMessage(it) },
                        onReadAloud = { ctx.readMessageAloud(it) },
                        onMore = { ctx.messageMenuTargetId = it.id },
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
                val effectiveInset = maxOf(
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
                zIndex(6)
                // 展开态与键盘态解耦：键盘回落期间展开态不变，回落动画不会被
                // 无动画的几何更新打断；收缩只发生在键盘静止时，两条动画不并发
                animate(Animation.easeOut(ctx.keyboardAnimDuration), ctx.keyboardHeight)
                animate(Animation.easeOut(0.2f), ctx.composerExpanded)
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
                            onRemove = { ctx.removeSelectedImage(it) },
                            onPreview = { ctx.openImagePreview(it) },
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
                            placeholder(if (ctx.composerExpanded) "发消息…" else ctx.voiceInputHint())
                            placeholderColor(
                                if (ctx.voiceMode) Color(0x00000000) else StockChatTheme.textTertiary
                            )
                            returnKeyTypeSend()
                            enablesReturnKeyAutomatically(true)
                            maxTextLength(300)
                            // 展开未聚焦时也可点：直接点输入区域原生聚焦拉起键盘
                            touchEnable(ctx.composerExpanded && !ctx.voiceMode)
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
                                ctx.sendMessage(it.text)
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
                        touchEnable(!ctx.composerFocused)
                        zIndex(3)
                        capture(if (ctx.voiceMode) CaptureRule.longPress() else null)
                        animate(Animation.easeOut(0.2f), ctx.voiceMode)
                        animate(Animation.easeOut(0.2f), ctx.selectedImageCount)
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
                            ctx.toggleVoiceMode()
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
                            click { ctx.openModelMenu() }
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
                            ctx.openImagePicker()
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
                                if (visibleNow) {
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
                    height(metrics.dp(16f))
                    justifyContentCenter()
                    alignItemsCenter()
                    opacity(if (ctx.composerExpanded) 0f else 1f)
                    animation(Animation.easeOut(0.14f), ctx.composerExpanded)
                }
                Text {
                    attr {
                        text("内容由 AI 生成")
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
                ctx.MessageMenuItem(this, "节选复制") {
                    ctx.bridgeModule.toast("节选复制暂未开放")
                }
                ctx.MessageMenuItem(this, "重新生成") { target ->
                    ctx.regenerateMessage(target)
                }
                ctx.MessageMenuItem(this, "朗读") { target ->
                    ctx.readMessageAloud(target)
                }
                ctx.MessageMenuItem(this, "分享") {
                    ctx.bridgeModule.toast("分享功能暂未开放")
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
                CHAT_MODEL_OPTIONS.forEach { option ->
                    ctx.ModelMenuItem(this, option)
                }
                Text {
                    attr {
                        text("模型选择仅影响后续回答；图片问题自动使用千问视觉模型")
                        fontSize(metrics.dp(11f))
                        color(StockChatTheme.textTertiary)
                        textAlignCenter()
                        marginTop(metrics.dp(8f))
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

    private fun openModelMenu() {
        if (::inputRef.isInitialized) {
            inputRef.view?.blur()
        }
        resetKeyboardState()
        messageMenuTargetId = ""
        closeDrawer()
        modelMenuOpen = true
    }

    private fun closeModelMenu() {
        modelMenuOpen = false
    }

    private fun selectModel(modelId: String) {
        if (CHAT_MODEL_OPTIONS.none { it.id == modelId }) {
            return
        }
        selectedModelId = modelId
        closeModelMenu()
    }

    private fun selectedModel(): ChatModelOption {
        return CHAT_MODEL_OPTIONS.firstOrNull { it.id == selectedModelId }
            ?: CHAT_MODEL_OPTIONS.first()
    }

    private fun focusComposer() {
        composerFocused = true
        composerExpanded = true
        collapseComposerAfterSettle = false
        voiceMode = false
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

    private fun sendMessage(submittedText: String? = null) {
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

    private fun completeAnswer(
        messageId: String,
        question: String,
        attempt: Int,
        currentRequestToken: Int,
    ) {
        val history = conversationHistoryBefore(messageId)
        val attachedImages = imagesBeforeAnswer(messageId)
        runCatching {
            dataSource.answer(
                question,
                history,
                attachedImages,
                selectedModel().id,
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
                    ChatAnswer.Failure("阿里云 AI 服务暂时不可用，请稍后重试。"),
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

    private fun deleteMessage(message: ChatMessage) {
        val index = messages.indexOfFirst { it.id == message.id }
        if (index < 0) {
            return
        }
        messages.removeAt(index)
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

    private fun openStockDetail(quote: StockQuote) {
        cancelVoiceInput()
        if (::inputRef.isInitialized) {
            inputRef.view?.blur()
        }
        val params = JSONObject()
        params.put("symbol", quote.symbol)
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(STOCK_DETAIL_PAGE_NAME, params)
    }

    private fun openImagePreview(imageUri: String) {
        if (imageUri.isBlank()) {
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
        messageMenuTargetId = ""
        loadMessagesForActiveSession()
        updateTypingIndicatorTimer()
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
