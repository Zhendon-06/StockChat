package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.base.setTimeout
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Scale
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.CaptureRule
import com.tencent.kuikly.core.base.attr.CaptureRuleDirection
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

// 欢迎页：主视觉、文案入场动效与快捷问题卡片。

private const val WELCOME_MOTION_DELAY = 0.16f

private const val WELCOME_HERO_SETTLE_DELAY = 0.14f

private const val WELCOME_TEXT_DELAY = 0.04f

private const val WELCOME_TEXT_SETTLE_DELAY = 0.18f

private const val WELCOME_FADE_DURATION = 0.26f

private const val WELCOME_SPRING_DURATION = 0.24f

private const val WELCOME_SETTLE_DURATION = 0.18f

private const val WELCOME_MOTION_OFFSET_DP = 8f

private const val WELCOME_HERO_START_SCALE = 0.94f

private const val WELCOME_HERO_OVERSHOOT_SCALE = 1.035f

private const val WELCOME_HERO_OVERSHOOT_OFFSET_DP = 2f

private const val WELCOME_TEXT_MOTION_OFFSET_DP = 5f

private const val WELCOME_SUGGESTION_DELAY = 0.06f

internal const val WELCOME_SUGGESTIONS_PER_PAGE = 7

internal const val WELCOME_SUGGESTION_PAGE_COUNT = 4

internal const val WELCOME_SUGGESTION_ROTATION_INTERVAL_MS = 5200

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
private data class StockChatSuggestion(val text: String, val question: String = text)

// 欢迎页输入框上方的快捷问题，点击直接发送
private val WELCOME_SUGGESTIONS =
    listOf(
        StockChatSuggestion("今日大盘怎么样"),
        StockChatSuggestion("分析一下贵州茅台"),
        StockChatSuggestion("看看沪深 300 指数"),
        StockChatSuggestion("现在市场风险大吗"),
        StockChatSuggestion("AI 选股思路", "如何建立自己的选股思路？"),
        StockChatSuggestion("新手怎么开始炒股？"),
        StockChatSuggestion("什么是市盈率？"),
        StockChatSuggestion("怎么分散投资风险？"),
        StockChatSuggestion("比较一下腾讯和阿里巴巴"),
        StockChatSuggestion("宁德时代近期走势如何？"),
        StockChatSuggestion("适合长期关注哪些行业？"),
        StockChatSuggestion("如何看成交量变化？"),
        StockChatSuggestion("什么是市净率？"),
        StockChatSuggestion("解释一下北向资金"),
        StockChatSuggestion("港股和 A 股有什么区别？"),
        StockChatSuggestion("如何判断一只股票是否高估？"),
        StockChatSuggestion("复盘今天的市场热点"),
        StockChatSuggestion("哪些指标适合看趋势？"),
        StockChatSuggestion("分析一下比亚迪"),
        StockChatSuggestion("低风险投资应该关注什么？"),
        StockChatSuggestion("什么是现金流折现？"),
        StockChatSuggestion("怎么看财报中的毛利率？"),
        StockChatSuggestion("近期新能源板块怎么样？"),
        StockChatSuggestion("如何设置自己的止损线？"),
        StockChatSuggestion("比较沪深 300 和中证 500"),
        StockChatSuggestion("股票分红会影响价格吗？"),
        StockChatSuggestion("看看美股科技板块"),
        StockChatSuggestion("怎样理解换手率？"),
    )

internal fun StockChatPage.WelcomeContent(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        View {
            attr { absolutePositionAllZero() }
            View {
                attr { absolutePositionAllZero() }
                View {
                    attr {
                        // 外层容器的 bottom 会随输入框展开/多行/附件而变化，
                        // 这里用“折叠态基线”反向抵消该增量，避免欢迎图标随之抬高
                        val bottomInset = metrics.composerBottomInset(ctx.keyboardHeight, pagerData.safeAreaInsets.bottom)
                        val liveContentBottom =
                            metrics.composerContentBottom(
                                bottomInset,
                                ctx.composerExpanded,
                                ctx.voiceMode,
                                ctx.selectedImageCount > 0,
                                ctx.composerExtraInputLines(),
                            )
                        val collapsedContentBottom = metrics.composerContentBottom(bottomInset, focused = false)
                        absolutePosition(
                            top = 0f,
                            left = 0f,
                            right = 0f,
                            bottom = metrics.dp(136f) - (liveContentBottom - collapsedContentBottom),
                        )
                        animate(Animation.easeOut(ctx.keyboardAnimDuration), ctx.keyboardHeight)
                        animate(Animation.easeOut(0.2f), ctx.composerExpanded)
                        alignItemsCenter()
                        justifyContentCenter()
                        padding(left = metrics.dp(24f), right = metrics.dp(24f))
                    }
                    ctx.WelcomeMotionContent(this)
                }
                ctx.SuggestionCardRow(this)
            }
        }
    }
}

private fun StockChatPage.visibleWelcomeSuggestions(): List<StockChatSuggestion> {
    val pageStart = (welcomeSuggestionPage % WELCOME_SUGGESTION_PAGE_COUNT) * WELCOME_SUGGESTIONS_PER_PAGE
    return List(WELCOME_SUGGESTIONS_PER_PAGE) { index ->
        WELCOME_SUGGESTIONS[(pageStart + index) % WELCOME_SUGGESTIONS.size]
    }
}

internal fun StockChatPage.SuggestionCardRow(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        View {
            attr {
                absolutePosition(left = 0f, right = 0f, bottom = metrics.dp(6f))
                height(metrics.dp(46f))
                val phase = ctx.welcomeMotionPhase
                opacity(phase)
                animate(
                    if (phase >= 1f) {
                        Animation.easeOut(0.22f).delay(WELCOME_SUGGESTION_DELAY)
                    } else {
                        Animation.easeOut(0.12f)
                    },
                    phase,
                )
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
                ctx.visibleWelcomeSuggestions().forEachIndexed { index, suggestion ->
                    View {
                        attr {
                            height(metrics.dp(40f))
                            borderRadius(metrics.dp(20f))
                            backgroundColor(StockChatTheme.surface)
                            themedBorder()
                            flexDirectionRow()
                            alignItemsCenter()
                            padding(left = metrics.dp(13f), right = metrics.dp(15f))
                            marginRight(metrics.dp(9f))
                        }
                        event {
                            click {
                                if (ctx.selectedHomeTab == HOME_TAB_CHAT) {
                                    ctx.sendMessage(suggestion.question, StockChatQuestionSource.WELCOME_SUGGESTION)
                                }
                            }
                        }
                        View {
                            attr {
                                size(metrics.dp(8f), metrics.dp(8f))
                                borderRadius(metrics.dp(4f))
                                backgroundColor(StockChatTheme.welcomeSuggestionDotColors[index])
                                marginRight(metrics.dp(8f))
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

internal fun StockChatPage.stageWelcomeMotion(nextState: StockChatHomeState) {
    val generation = ++welcomeMotionGeneration
    welcomeMotionPhase = 0f
    welcomeHeroMotionStage = 0
    welcomeTextMotionStage = 0
    if (nextState.destination != StockChatHomeDestination.AI_CHAT || nextState.chatStage != StockChatHomeChatStage.WELCOME) {
        return
    }
    setTimeout((WELCOME_MOTION_DELAY * 1000f).toInt()) {
        if (welcomeMotionIsCurrent(generation)) {
            welcomeMotionPhase = 1f
            welcomeHeroMotionStage = 1
            setTimeout((WELCOME_TEXT_DELAY * 1000f).toInt()) {
                if (welcomeMotionIsCurrent(generation)) {
                    welcomeTextMotionStage = 1
                }
            }
            setTimeout((WELCOME_HERO_SETTLE_DELAY * 1000f).toInt()) {
                if (welcomeMotionIsCurrent(generation)) {
                    welcomeHeroMotionStage = 2
                }
            }
            setTimeout((WELCOME_TEXT_SETTLE_DELAY * 1000f).toInt()) {
                if (welcomeMotionIsCurrent(generation)) {
                    welcomeTextMotionStage = 2
                }
            }
        }
    }
}

private fun StockChatPage.WelcomeHero(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        View {
            attr {
                val stage = ctx.welcomeHeroMotionStage
                val scale =
                    when (stage) {
                        0 -> WELCOME_HERO_START_SCALE
                        1 -> WELCOME_HERO_OVERSHOOT_SCALE
                        else -> 1f
                    }
                val offsetY =
                    when (stage) {
                        0 -> -metrics.dp(WELCOME_MOTION_OFFSET_DP)
                        1 -> metrics.dp(WELCOME_HERO_OVERSHOOT_OFFSET_DP)
                        else -> 0f
                    }
                transform(scale = Scale(scale, scale), translate = Translate(0f, 0f, 0f, offsetY))
                animate(
                    if (stage == 2) {
                        Animation.springEaseOut(WELCOME_SETTLE_DURATION, 0.82f, 0.08f)
                    } else {
                        Animation.springEaseOut(WELCOME_SPRING_DURATION, 0.76f, 0.22f)
                    },
                    stage,
                )
                alignItemsCenter()
            }
            Image {
                attr {
                    size(metrics.welcomeHeroSize, metrics.welcomeHeroSize)
                    resizeContain()
                    src(ImageUri.commonAssets("stockchat_app_icon.png"))
                }
            }
        }
    }
}

private fun StockChatPage.WelcomeIntro(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        View {
            attr {
                val stage = ctx.welcomeTextMotionStage
                val textOpacity =
                    when (stage) {
                        0 -> 0f
                        1 -> 0.82f
                        else -> 1f
                    }
                val offsetY =
                    when (stage) {
                        0 -> -metrics.dp(WELCOME_TEXT_MOTION_OFFSET_DP)
                        1 -> metrics.dp(1f)
                        else -> 0f
                    }
                opacity(textOpacity)
                transform(Translate(0f, 0f, 0f, offsetY))
                animate(
                    if (stage == 2) {
                        Animation.springEaseOut(WELCOME_SETTLE_DURATION, 0.84f, 0.06f)
                    } else {
                        Animation.springEaseOut(WELCOME_SPRING_DURATION, 0.8f, 0.16f)
                    },
                    stage,
                )
                alignItemsCenter()
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
            Text {
                attr {
                    text("支持查行情、学炒股，也可以直接问其他问题")
                    fontSize(metrics.dp(12f))
                    color(StockChatTheme.textTertiary)
                    textAlignCenter()
                    marginTop(metrics.dp(12f))
                }
            }
        }
    }
}

private fun StockChatPage.WelcomeMotionContent(container: ViewContainer<*, *>) {
    val ctx = this
    with(container) {
        View {
            attr {
                val phase = ctx.welcomeMotionPhase
                opacity(phase)
                transform(Translate(0f, 0f, 0f, ctx.keyboardHeight / 2f))
                animate(Animation.easeOut(ctx.keyboardAnimDuration), ctx.keyboardHeight)
                animate(Animation.easeOut(WELCOME_FADE_DURATION), phase)
                touchEnable(ctx.selectedHomeTab == HOME_TAB_CHAT && phase >= 1f)
                alignItemsCenter()
            }
            ctx.WelcomeHero(this)
            ctx.WelcomeIntro(this)
        }
    }
}

private fun StockChatPage.welcomeMotionIsCurrent(generation: Int): Boolean =
    generation == welcomeMotionGeneration &&
        homeState.destination == StockChatHomeDestination.AI_CHAT &&
        homeState.chatStage == StockChatHomeChatStage.WELCOME
