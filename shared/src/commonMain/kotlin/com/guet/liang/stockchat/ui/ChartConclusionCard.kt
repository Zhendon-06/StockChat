package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.model.ChartConclusion
import com.guet.liang.stockchat.model.ChartEvidenceResolution
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal fun ViewContainer<*, *>.ChartConclusionCard(
    conclusion: ChartConclusion,
    resolution: ChartEvidenceResolution,
    onLocate: () -> Unit,
) {
    View {
        attr {
            marginTop(10f)
            padding(12f)
            borderRadius(12f)
            backgroundColor(StockChatTheme.accentSoft)
        }
        Text {
            attr {
                text(conclusion.text)
                fontSize(13f)
                lineHeight(20f)
                color(StockChatTheme.textPrimary)
            }
        }
        if (resolution is ChartEvidenceResolution.Valid) {
            Text {
                attr {
                    text(resolution.label)
                    fontSize(10f)
                    lineHeight(16f)
                    color(StockChatTheme.textSecondary)
                    marginTop(6f)
                }
            }
            View {
                attr {
                    marginTop(6f)
                    minHeight(40f)
                    justifyContentCenter()
                }
                event { click { onLocate() } }
                Text {
                    attr {
                        text("查看对应区间  ›")
                        fontSize(13f)
                        fontWeightMedium()
                        color(StockChatTheme.accent)
                    }
                }
            }
        } else {
            Text {
                attr {
                    text((resolution as ChartEvidenceResolution.Invalid).reason)
                    fontSize(11f)
                    lineHeight(17f)
                    color(StockChatTheme.textTertiary)
                    marginTop(6f)
                }
            }
        }
    }
}
