package com.guet.liang.kuiklytableview.table

import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.timer.setTimeout
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

public class KuiklyExcelTableView internal constructor(
    private val viewportHeight: Float,
    private val metrics: TableMetrics,
    private val loader: suspend () -> TableSpec<ExcelRow>,
    private val onLoaded: (TableSpec<ExcelRow>) -> Unit,
    private val onError: (Throwable) -> Unit,
) : ComposeView<ComposeAttr, ComposeEvent>() {

    private var loadStatus: Int by observable(STATUS_LOADING)
    private var loadedSpec: TableSpec<ExcelRow>? = null
    private var errorMessage: String = ""
    private var active: Boolean = true
    private var loadStarted: Boolean = false

    override fun createAttr(): ComposeAttr = ComposeAttr()

    override fun createEvent(): ComposeEvent = ComposeEvent()

    override fun viewDidLayout() {
        super.viewDidLayout()
        if (!loadStarted) {
            loadStarted = true
            loadFile()
        }
    }

    override fun body(): ViewBuilder {
        val context = this
        return {
            vif({ context.loadStatus == STATUS_READY }) {
                context.loadedSpec?.let { spec ->
                    KuiklyTable(
                        spec = spec,
                        viewportHeight = context.viewportHeight,
                        metrics = context.metrics,
                    )
                }
            }
            velse {
                vif({ context.loadStatus == STATUS_ERROR }) {
                    context.renderStatus(this, context.errorMessage, Color(0xFFB91C1C))
                }
                velse {
                    context.renderStatus(this, "正在读取 Excel 文件…", Color(0xFF475569))
                }
            }
        }
    }

    override fun viewDestroyed() {
        active = false
        super.viewDestroyed()
    }

    private fun loadFile() {
        loader.startCoroutine(
            object : Continuation<TableSpec<ExcelRow>> {
                override val context = EmptyCoroutineContext

                override fun resumeWith(result: Result<TableSpec<ExcelRow>>) {
                    if (!active) {
                        return
                    }
                    this@KuiklyExcelTableView.setTimeout {
                        if (active) {
                            result.fold(
                                onSuccess = ::showTable,
                                onFailure = ::showError,
                            )
                        }
                    }
                }
            },
        )
    }

    private fun showTable(spec: TableSpec<ExcelRow>) {
        if (viewportHeight <= spec.header.height) {
            showError(
                IllegalArgumentException(
                    "Table viewportHeight must be greater than Excel table header height",
                ),
            )
            return
        }
        loadedSpec = spec
        loadStatus = STATUS_READY
        onLoaded(spec)
    }

    private fun showError(error: Throwable) {
        errorMessage = error.message ?: "Excel 文件读取失败"
        loadStatus = STATUS_ERROR
        onError(error)
    }

    private fun renderStatus(
        container: ViewContainer<*, *>,
        message: String,
        textColor: Color,
    ) {
        val context = this
        with(container) {
            View {
                attr {
                    height(context.viewportHeight)
                    alignSelfStretch()
                    allCenter()
                    backgroundColor(Color(0xFFF8FAFC))
                }
                Text {
                    attr {
                        text(message)
                        fontSize(14f)
                        color(textColor)
                        lines(3)
                        textAlignCenter()
                    }
                }
            }
        }
    }

    private companion object {
        const val STATUS_LOADING = 0
        const val STATUS_READY = 1
        const val STATUS_ERROR = 2
    }
}

internal fun ViewContainer<*, *>.addKuiklyExcelTable(
    viewportHeight: Float,
    metrics: TableMetrics,
    loader: suspend () -> TableSpec<ExcelRow>,
    onLoaded: (TableSpec<ExcelRow>) -> Unit,
    onError: (Throwable) -> Unit,
    init: KuiklyExcelTableView.() -> Unit,
) {
    require(viewportHeight > 0f) { "Excel table viewportHeight must be greater than zero" }
    addChild(
        KuiklyExcelTableView(
            viewportHeight = viewportHeight,
            metrics = metrics,
            loader = loader,
            onLoaded = onLoaded,
            onError = onError,
        ),
    ) {
        attr {
            height(viewportHeight)
            alignSelfStretch()
        }
        init()
    }
}
