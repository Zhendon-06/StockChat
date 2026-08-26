package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.base.BasePager
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

private const val IMAGE_PREVIEW_PAGE_NAME = "stock_image_preview"

@Page(IMAGE_PREVIEW_PAGE_NAME, supportInLocal = true)
internal class ImagePreviewPage : BasePager() {
    private var imageUri = ""

    override fun created() {
        super.created()
        imageUri = pageData.params.optString(IMAGE_URI_PARAM).trim()
    }

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr {
                backgroundColor(Color.BLACK)
            }
            if (ctx.imageUri.isNotEmpty()) {
                Image {
                    attr {
                        absolutePosition(
                            top = pagerData.statusBarHeight,
                            left = 0f,
                            right = 0f,
                            bottom = pagerData.safeAreaInsets.bottom,
                        )
                        resizeContain()
                        src(ctx.imageUri, false)
                    }
                    event {
                        click { ctx.closePreview() }
                    }
                }
            } else {
                View {
                    attr {
                        absolutePositionAllZero()
                        allCenter()
                    }
                    Text {
                        attr {
                            text("图片加载失败")
                            fontSize(16f)
                            color(Color.WHITE)
                        }
                    }
                }
            }
            View {
                attr {
                    absolutePosition(
                        top = pagerData.statusBarHeight + 12f,
                        left = 18f,
                    )
                    size(44f, 44f)
                    borderRadius(22f)
                    backgroundColor(Color(0x66000000))
                    border(Border(1f, BorderStyle.SOLID, Color(0x66FFFFFF)))
                    allCenter()
                }
                event {
                    click { ctx.closePreview() }
                }
                Text {
                    attr {
                        text("‹")
                        fontSize(34f)
                        color(Color.WHITE)
                        marginBottom(3f)
                    }
                }
            }
        }
    }

    private fun closePreview() {
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
    }

    companion object {
        const val IMAGE_URI_PARAM = "imageUri"
    }
}
