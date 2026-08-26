package com.guet.liang.stockchat.ui

import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.CaptureRule
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal fun ViewContainer<*, *>.ComposerImageAttachments(
    images: () -> ObservableList<String>,
    scale: Float,
    onRemove: (String) -> Unit,
    onPreview: (String) -> Unit,
) {
    Scroller {
        attr {
            height(70f * scale)
            flexDirectionRow()
            alignItemsCenter()
            showScrollerIndicator(false)
            bouncesEnable(true)
            scrollEnable(true)
            padding(left = 4f * scale, right = 4f * scale)
        }
        vfor(images) { imageUri ->
            View {
                attr {
                    size(64f * scale, 64f * scale)
                    borderRadius(14f * scale)
                    backgroundColor(StockChatTheme.recessed)
                    border(Border(1f, BorderStyle.SOLID, StockChatTheme.borderStrong))
                    marginRight(9f * scale)
                    overflow(true)
                }
                Image {
                    attr {
                        absolutePositionAllZero()
                        resizeCover()
                        src(imageUri, false)
                    }
                    event {
                        click { onPreview(imageUri) }
                    }
                }
                View {
                    attr {
                        absolutePosition(top = 0f, right = 0f)
                        size(32f * scale, 32f * scale)
                        allCenter()
                        touchEnable(true)
                        zIndex(10)
                        capture(CaptureRule.click())
                    }
                    event {
                        click { onRemove(imageUri) }
                    }
                    View {
                        attr {
                            size(22f * scale, 22f * scale)
                            borderRadius(11f * scale)
                            backgroundColor(Color(0xCC17201D))
                            allCenter()
                            touchEnable(false)
                        }
                        Text {
                            attr {
                                text("×")
                                fontSize(16f * scale)
                                color(Color.WHITE)
                                touchEnable(false)
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun ViewContainer<*, *>.MessageImageGallery(
    images: List<String>,
    scale: Float,
    onPreview: (String) -> Unit,
) {
    if (images.isEmpty()) {
        return
    }
    val imageSize = if (images.size == 1) 176f * scale else 122f * scale
    val itemSpacing = 8f * scale
    val contentWidth = images.size * imageSize + (images.size - 1).coerceAtLeast(0) * itemSpacing
    val galleryWidth = if (images.size == 1) imageSize else minOf(290f * scale, contentWidth)
    Scroller {
        attr {
            width(galleryWidth)
            height(imageSize)
            flexDirectionRow()
            showScrollerIndicator(false)
            bouncesEnable(images.size > 2)
            scrollEnable(images.size > 2)
            marginBottom(8f * scale)
        }
        images.forEachIndexed { index, imageUri ->
            Image {
                attr {
                    size(imageSize, imageSize)
                    resizeCover()
                    src(imageUri, false)
                    borderRadius(18f * scale)
                    border(Border(1f, BorderStyle.SOLID, StockChatTheme.borderStrong))
                    if (index < images.lastIndex) {
                        marginRight(itemSpacing)
                    }
                }
                event {
                    click { onPreview(imageUri) }
                }
            }
        }
    }
}
