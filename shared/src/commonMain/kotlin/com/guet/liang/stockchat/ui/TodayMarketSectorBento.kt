package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.model.StockQuote
import com.guet.liang.stockchat.model.TodayMarketSectorObservation
import com.guet.liang.stockchat.model.TodayMarketSnapshot
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ColorStop
import com.tencent.kuikly.core.base.Direction
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

// 今日市场采用非对称 Bento 构图：主卡、辅卡和底板各司其职，内容之间不互相遮挡。


internal fun ViewContainer<*, *>.MarketSectorBento(
    sectors: List<TodayMarketSectorObservation>,
    contentWidth: Float,
    scale: Float,
    onSectorClick: (TodayMarketSectorObservation) -> Unit,
    focusIndex: Int,
    pulse: Boolean,
    onFocusAdvance: () -> Unit,
) {
    if (sectors.isEmpty()) return
    val focusedSectors = sectors.focusedFrom(focusIndex)
    TodayMarketSectionTitle(this, "板块观察", "方向与分化", scale, onFocusAdvance)
    val featureWidth = contentWidth * 0.58f
    val sideWidth = contentWidth - featureWidth - 9f * scale
    val stageHeight = if (focusedSectors.size > 2) 305f * scale else 197f * scale
    View {
        attr {
            width(contentWidth)
            height(stageHeight)
            positionRelative()
            opacity(if (pulse) 0.84f else 1f)
            transform(Translate(0f, 0f, 0f, if (pulse) 4f * scale else 0f))
            animate(Animation.easeInOut(0.24f), pulse)
        }
        View {
            attr {
                absolutePosition(top = 8f * scale, left = 10f * scale, right = 0f)
                height(181f * scale)
                borderRadius(23f * scale)
                backgroundColor(StockChatTheme.recessed)
            }
        }
        SectorFeatureCard(
            container = this,
            sector = focusedSectors.first(),
            width = featureWidth,
            height = 177f * scale,
            scale = scale,
        ) {
            onSectorClick(focusedSectors.first())
        }
        focusedSectors.getOrNull(1)?.let { sector ->
            SectorSideCard(
                container = this,
                sector = sector,
                width = sideWidth,
                top = 11f * scale,
                height = 103f * scale,
                scale = scale,
            ) {
                onSectorClick(sector)
            }
        }
        focusedSectors.getOrNull(2)?.let { sector ->
            SectorStripCard(this, sector, contentWidth * 0.47f, 187f * scale, 3f * scale, scale, 3) {
                onSectorClick(sector)
            }
        }
        focusedSectors.getOrNull(3)?.let { sector ->
            SectorStripCard(
                this,
                sector,
                contentWidth * 0.47f,
                187f * scale,
                contentWidth * 0.51f,
                scale,
                4,
            ) {
                onSectorClick(sector)
            }
        }
        focusedSectors.getOrNull(4)?.let { sector ->
            SectorStripCard(this, sector, contentWidth * 0.72f, 245f * scale, contentWidth * 0.14f, scale, 5) {
                onSectorClick(sector)
            }
        }
    }
}

private fun SectorFeatureCard(
    container: ViewContainer<*, *>,
    sector: TodayMarketSectorObservation,
    width: Float,
    height: Float,
    scale: Float,
    onClick: () -> Unit,
) {
    val tint = marketSectorColor(sector)
    with(container) {
        View {
            attr {
                absolutePosition(top = 0f, left = 0f)
                width(width)
                height(height)
                padding(
                    top = 15f * scale,
                    left = 15f * scale,
                    bottom = 12f * scale,
                    right = 15f * scale,
                )
                borderRadius(22f * scale)
                backgroundColor(StockChatTheme.surface)
                themedBorder()
                boxShadow(BoxShadow(0f, 6f * scale, 16f * scale, Color(0x1A000000)))
                zIndex(2)
            }
            event { click { onClick() } }
            Text {
                attr {
                    text("最活跃方向")
                    fontSize(10f * scale)
                    color(StockChatTheme.textTertiary)
                }
            }
            Text {
                attr {
                    text(sector.name)
                    fontSize(21f * scale)
                    fontWeightBold()
                    color(StockChatTheme.textPrimary)
                    marginTop(12f * scale)
                    lines(1)
                }
            }
            Text {
                attr {
                    text(sector.changeLabel)
                    fontSize(28f * scale)
                    fontWeightBold()
                    color(tint)
                    marginTop(3f * scale)
                }
            }
            Text {
                attr {
                    text(sector.members.ifBlank { "暂无成员快照" })
                    fontSize(11f * scale)
                    lineHeight(17f * scale)
                    color(StockChatTheme.textSecondary)
                    marginTop(11f * scale)
                    lines(2)
                }
            }
            Text {
                attr {
                    text("查看成员  ›")
                    fontSize(10f * scale)
                    color(StockChatTheme.accent)
                    marginTop(9f * scale)
                }
            }
        }
    }
}

private fun SectorSideCard(
    container: ViewContainer<*, *>,
    sector: TodayMarketSectorObservation,
    width: Float,
    top: Float,
    height: Float,
    scale: Float,
    onClick: () -> Unit,
) {
    val tint = marketSectorColor(sector)
    with(container) {
        View {
            attr {
                absolutePosition(top = top, right = 0f)
                width(width)
                height(height)
                padding(top = 13f * scale, left = 12f * scale, bottom = 11f * scale, right = 11f * scale)
                borderRadius(19f * scale)
                backgroundColor(StockChatTheme.accentSoft)
                themedBorder()
                boxShadow(BoxShadow(0f, 4f * scale, 11f * scale, Color(0x14000000)))
                zIndex(2)
            }
            event { click { onClick() } }
            Text {
                attr {
                    text("同时观察")
                    fontSize(10f * scale)
                    color(StockChatTheme.textTertiary)
                }
            }
            Text {
                attr {
                    text(sector.name)
                    fontSize(15f * scale)
                    fontWeightBold()
                    color(StockChatTheme.textPrimary)
                    marginTop(8f * scale)
                    lines(1)
                }
            }
            Text {
                attr {
                    text(sector.changeLabel)
                    fontSize(19f * scale)
                    fontWeightBold()
                    color(tint)
                    marginTop(8f * scale)
                }
            }
            Text {
                attr {
                    text("点击查看成员")
                    fontSize(9f * scale)
                    color(StockChatTheme.textTertiary)
                    marginTop(4f * scale)
                }
            }
        }
    }
}

private fun SectorStripCard(
    container: ViewContainer<*, *>,
    sector: TodayMarketSectorObservation,
    width: Float,
    top: Float,
    left: Float,
    scale: Float,
    zIndex: Int,
    onClick: () -> Unit,
) {
    val tint = marketSectorColor(sector)
    with(container) {
        View {
            attr {
                absolutePosition(top = top, left = left)
                width(width)
                height(50f * scale)
                padding(left = 12f * scale, right = 11f * scale)
                borderRadius(16f * scale)
                backgroundColor(StockChatTheme.surface)
                themedBorder()
                flexDirectionRow()
                alignItemsCenter()
                boxShadow(BoxShadow(0f, 3f * scale, 9f * scale, Color(0x12000000)))
                zIndex(zIndex)
            }
            event { click { onClick() } }
            View {
                attr { flex(1f) }
                Text {
                    attr {
                        text(sector.name)
                        fontSize(12f * scale)
                        fontWeightMedium()
                        color(StockChatTheme.textPrimary)
                        lines(1)
                    }
                }
                Text {
                    attr {
                        text(sector.members)
                        fontSize(9f * scale)
                        color(StockChatTheme.textTertiary)
                        marginTop(2f * scale)
                        lines(1)
                    }
                }
            }
            Text {
                attr {
                    text(sector.changeLabel)
                    fontSize(14f * scale)
                    fontWeightBold()
                    color(tint)
                    lines(1)
                }
            }
        }
    }
}
