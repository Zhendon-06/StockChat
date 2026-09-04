package com.guet.liang.stockchat.ui.settings

import com.guet.liang.stockchat.base.BasePager
import com.guet.liang.stockchat.base.bridgeModule
import com.guet.liang.stockchat.data.ModelCatalogResult
import com.guet.liang.stockchat.data.ModelCatalogService
import com.guet.liang.stockchat.data.StockChatSettingsStore
import com.guet.liang.stockchat.model.ModelOption
import com.guet.liang.stockchat.model.ModelProviderConfig
import com.guet.liang.stockchat.model.ModelProviderKind
import com.guet.liang.stockchat.model.ThemeMode
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

@Page(MODEL_CONFIGURATION_PAGE_NAME, supportInLocal = true)
internal class ModelConfigurationPage : BasePager() {
    private var configuration by observable(StockChatSettingsStore.repository.loadSnapshot().modelConfiguration)
    private var themeMode by observable(ThemeMode.SYSTEM)
    private var selectedProviderId by observable("")
    private var providerName by observable("")
    private var baseUrl by observable("")
    private var apiKey by observable("")
    private var selectedModelId by observable("")
    private var keyVisible by observable(false)
    private var availableModels by observable<List<ModelOption>>(emptyList())
    private var modelListVisible by observable(false)
    private var modelListLoading by observable(false)
    private var modelListError by observable("")
    private var modelRequestToken = 0
    private var lastAttemptedModelRequest = ""
    private var lastLoadedModelRequest = ""
    private lateinit var modelCatalogService: ModelCatalogService

    override fun created() {
        super.created()
        modelCatalogService = ModelCatalogService(
            acquireModule<NetworkModule>(NetworkModule.MODULE_NAME),
        )
        val snapshot = StockChatSettingsStore.repository.loadSnapshot()
        configuration = snapshot.modelConfiguration
        themeMode = snapshot.appearance.themeMode
        selectProvider(configuration.activeProviderId)
    }

    override fun pageWillDestroy() {
        modelRequestToken += 1
        super.pageWillDestroy()
    }

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr {
                backgroundColor(ctx.palette().background)
            }
            SettingsPageHeader(
                statusBarHeight = ctx.pagerData.statusBarHeight,
                title = "模型配置",
                palette = ctx::palette,
                actionText = "保存",
                onBack = ctx::closePage,
                onAction = ctx::saveProvider,
            )
            Scroller {
                attr {
                    absolutePosition(
                        top = ctx.pagerData.statusBarHeight + SETTINGS_HEADER_HEIGHT,
                        left = 0f,
                        right = 0f,
                        bottom = ctx.pagerData.safeAreaInsets.bottom,
                    )
                    padding(bottom = 30f.settingsDp(), left = 0f, right = 0f, top = 0f)
                    showScrollerIndicator(false)
                    bouncesEnable(true)
                }
                ctx.Introduction(this)
                ctx.ProviderSelector(this)
                ctx.ProviderEditor(this)
                ctx.ModelList(this)
                ctx.SecurityNotice(this)
            }
        }
    }

    private fun Introduction(container: ViewContainer<*, *>) {
        val ctx = this
        with(container) {
            View {
                attr {
                    width((ctx.pagerData.pageViewWidth - 40f.settingsDp()).coerceAtLeast(1f))
                    alignSelfCenter()
                    padding(top = 10f.settingsDp(), bottom = 2f.settingsDp())
                }
                Text {
                    attr {
                        text("Provider 配置")
                        fontSize(14f.settingsDp())
                        fontWeightBold()
                        color(ctx.palette().textTertiary)
                    }
                }
                Text {
                    attr {
                        text("切换并维护五个模型服务商的地址、密钥与默认模型。")
                        fontSize(13f.settingsDp())
                        lineHeight(20f.settingsDp())
                        color(ctx.palette().textSecondary)
                        marginTop(7f.settingsDp())
                    }
                }
            }
        }
    }

    private fun ProviderSelector(container: ViewContainer<*, *>) {
        val ctx = this
        with(container) {
            Scroller {
                attr {
                    height(100f.settingsDp())
                    flexDirectionRow()
                    padding(left = 16f.settingsDp(), right = 6f.settingsDp())
                    marginTop(10f.settingsDp())
                    showScrollerIndicator(false)
                    bouncesEnable(true)
                }
                ctx.configuration.providers.forEach { provider ->
                    val iconAsset = ctx.providerAsset(provider.kind)
                    View {
                        attr {
                            val selected = ctx.selectedProviderId == provider.id
                            width(118f.settingsDp())
                            height(88f.settingsDp())
                            borderRadius(18f.settingsDp())
                            padding(
                                top = 12f.settingsDp(),
                                left = 12f.settingsDp(),
                                right = 12f.settingsDp(),
                                bottom = 10f.settingsDp(),
                            )
                            marginRight(10f.settingsDp())
                            backgroundColor(
                                if (selected) ctx.palette().accentSoft else ctx.palette().surface,
                            )
                            border(
                                Border(
                                    if (selected) 1.5f.settingsDp() else 1f.settingsDp(),
                                    BorderStyle.SOLID,
                                    if (selected) ctx.palette().accent else ctx.palette().divider,
                                ),
                            )
                        }
                        event {
                            click { ctx.switchProvider(provider.id) }
                        }
                        View {
                            attr {
                                size(32f.settingsDp(), 32f.settingsDp())
                                borderRadius(10f.settingsDp())
                                backgroundColor(Color.WHITE)
                                allCenter()
                            }
                            Image {
                                attr {
                                    size(27f.settingsDp(), 27f.settingsDp())
                                    resizeContain()
                                    src(ImageUri.commonAssets(iconAsset))
                                }
                            }
                        }
                        Text {
                            attr {
                                text(provider.kind.displayName)
                                fontSize(12f.settingsDp())
                                fontWeightBold()
                                color(ctx.palette().textPrimary)
                                marginTop(8f.settingsDp())
                                lines(1)
                            }
                        }
                        vif({ ctx.configuration.activeProviderId == provider.id }) {
                            View {
                                attr {
                                    absolutePosition(top = 9f.settingsDp(), right = 9f.settingsDp())
                                    size(18f.settingsDp(), 18f.settingsDp())
                                    borderRadius(9f.settingsDp())
                                    backgroundColor(ctx.palette().accent)
                                    allCenter()
                                }
                                Text {
                                    attr {
                                        text("✓")
                                        fontSize(10f.settingsDp())
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
    }

    private fun ProviderEditor(container: ViewContainer<*, *>) {
        val ctx = this
        val isDefaultProvider = ctx.selectedProvider().kind == ModelProviderKind.DEFAULT
        with(container) {
            SettingsCard(
                width = (ctx.pagerData.pageViewWidth - 32f.settingsDp()).coerceAtLeast(1f),
                palette = ctx::palette,
                marginTop = 6f.settingsDp(),
            ) {
                View {
                    attr {
                        padding(
                            top = 18f.settingsDp(),
                            left = 18f.settingsDp(),
                            right = 18f.settingsDp(),
                            bottom = 18f.settingsDp(),
                        )
                    }
                    View {
                        attr {
                            height(44f.settingsDp())
                            flexDirectionRow()
                            alignItemsCenter()
                        }
                        View {
                            attr {
                                size(38f.settingsDp(), 38f.settingsDp())
                                borderRadius(12f.settingsDp())
                                backgroundColor(Color.WHITE)
                                allCenter()
                            }
                            Image {
                                attr {
                                    size(32f.settingsDp(), 32f.settingsDp())
                                    resizeContain()
                                    // 必须在 attr 闭包内读取 observable，
                                    // 否则切换 Provider 时图标不会跟随刷新
                                    src(
                                        ImageUri.commonAssets(
                                            ctx.providerAsset(ctx.selectedProvider().kind),
                                        ),
                                    )
                                }
                            }
                        }
                        Text {
                            attr {
                                text(ctx.selectedProvider().kind.displayName)
                                fontSize(17f.settingsDp())
                                fontWeightBold()
                                color(ctx.palette().textPrimary)
                                marginLeft(11f.settingsDp())
                                flex(1f)
                                lines(1)
                            }
                        }
                    }
                    ctx.ProviderField(
                        this,
                        label = "Provider 名称",
                        value = { ctx.providerName },
                        placeholder = "服务商名称",
                        onChanged = { ctx.providerName = it },
                    )
                    ctx.ProviderField(
                        this,
                        label = "Base URL",
                        value = { ctx.baseUrl },
                        placeholder = "https://api.example.com/v1",
                        onChanged = { ctx.updateBaseUrl(it) },
                        readOnly = isDefaultProvider,
                    )
                    View {
                        attr {
                            marginTop(15f.settingsDp())
                        }
                        Text {
                            attr {
                                text("API Key")
                                fontSize(11f.settingsDp())
                                color(ctx.palette().textTertiary)
                                marginLeft(4f.settingsDp())
                            }
                        }
                        View {
                            attr {
                                height(48f.settingsDp())
                                marginTop(6f.settingsDp())
                                borderRadius(13f.settingsDp())
                                backgroundColor(ctx.palette().surfaceMuted)
                                border(Border(1f.settingsDp(), BorderStyle.SOLID, ctx.palette().divider))
                                flexDirectionRow()
                                alignItemsCenter()
                                overflow(true)
                                touchEnable(!isDefaultProvider)
                            }
                            vif({ !ctx.keyVisible }) {
                                Input {
                                    attr {
                                        flex(1f)
                                        height(48f.settingsDp())
                                        marginLeft(13f.settingsDp())
                                        marginRight(8f.settingsDp())
                                        text(ctx.apiKey)
                                        fontSize(14f.settingsDp())
                                        color(ctx.palette().textPrimary)
                                        tintColor(ctx.palette().accent)
                                        placeholder("未填写时使用本地 Mock 数据")
                                        placeholderColor(ctx.palette().textTertiary)
                                        keyboardTypePassword()
                                        maxTextLength(256)
                                    }
                                    event {
                                        textDidChange(isSyncEdit = true) {
                                            ctx.updateApiKey(it.text)
                                        }
                                        inputBlur { ctx.loadModelsIfNeeded() }
                                        inputReturn { ctx.loadModels(force = true) }
                                    }
                                }
                            }
                            vif({ ctx.keyVisible }) {
                                Input {
                                    attr {
                                        flex(1f)
                                        height(48f.settingsDp())
                                        marginLeft(13f.settingsDp())
                                        marginRight(8f.settingsDp())
                                        text(ctx.apiKey)
                                        fontSize(14f.settingsDp())
                                        color(ctx.palette().textPrimary)
                                        tintColor(ctx.palette().accent)
                                        placeholder("未填写时使用本地 Mock 数据")
                                        placeholderColor(ctx.palette().textTertiary)
                                        maxTextLength(256)
                                    }
                                    event {
                                        textDidChange(isSyncEdit = true) {
                                            ctx.updateApiKey(it.text)
                                        }
                                        inputBlur { ctx.loadModelsIfNeeded() }
                                        inputReturn { ctx.loadModels(force = true) }
                                    }
                                }
                            }
                            View {
                                attr {
                                    width(48f.settingsDp())
                                    height(48f.settingsDp())
                                    allCenter()
                                }
                                event {
                                    click { ctx.keyVisible = !ctx.keyVisible }
                                }
                                Text {
                                    attr {
                                        text(if (ctx.keyVisible) "隐藏" else "查看")
                                        fontSize(11f.settingsDp())
                                        fontWeightMedium()
                                        color(ctx.palette().textSecondary)
                                    }
                                }
                            }
                        }
                        vif({ isDefaultProvider }) {
                            Text {
                                attr {
                                    text("🔒")
                                    fontSize(12f.settingsDp())
                                    marginLeft(6f.settingsDp())
                                }
                            }
                        }
                    }
                    Text {
                        attr {
                            text("未填写 API Key 时不会发起该 Provider 的鉴权请求。")
                            fontSize(11f.settingsDp())
                            color(ctx.palette().textTertiary)
                            marginTop(8f.settingsDp())
                        }
                    }
                    View {
                        attr {
                            flexDirectionRow()
                            alignItemsCenter()
                            marginTop(14f.settingsDp())
                        }
                        View {
                            val loadInProgress = ctx.modelListLoading
                            attr {
                                height(44f.settingsDp())
                                width(116f.settingsDp())
                                borderRadius(22f.settingsDp())
                                backgroundColor(
                                    if (loadInProgress) {
                                        ctx.palette().surfaceMuted
                                    } else {
                                        ctx.palette().accentSoft
                                    },
                                )
                                allCenter()
                                touchEnable(!loadInProgress)
                            }
                            event {
                                click { ctx.loadModels(force = true) }
                            }
                            Text {
                                attr {
                                    text(if (loadInProgress) "获取中…" else "获取可用模型")
                                    fontSize(12f.settingsDp())
                                    fontWeightBold()
                                    color(
                                        if (loadInProgress) {
                                            ctx.palette().textTertiary
                                        } else {
                                            ctx.palette().accent
                                        },
                                    )
                                    lines(1)
                                }
                            }
                        }
                        View {
                            attr {
                                height(44f.settingsDp())
                                flex(1f)
                                borderRadius(22f.settingsDp())
                                backgroundColor(ctx.palette().accent)
                                allCenter()
                                marginLeft(10f.settingsDp())
                            }
                            event {
                                click { ctx.saveProvider() }
                            }
                            Text {
                                attr {
                                    text("保存并设为当前 Provider")
                                    fontSize(13f.settingsDp())
                                    fontWeightBold()
                                    color(Color.WHITE)
                                    lines(1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun ProviderField(
        container: ViewContainer<*, *>,
        label: String,
        value: () -> String,
        placeholder: String,
        onChanged: (String) -> Unit,
        readOnly: Boolean = false,
    ) {
        val ctx = this
        with(container) {
            View {
                attr { marginTop(15f.settingsDp()) }
                Text {
                    attr {
                        text(label)
                        fontSize(11f.settingsDp())
                        color(ctx.palette().textTertiary)
                        marginLeft(4f.settingsDp())
                    }
                }
                Input {
                    attr {
                        height(48f.settingsDp())
                        marginTop(6f.settingsDp())
                        borderRadius(13f.settingsDp())
                        backgroundColor(ctx.palette().surfaceMuted)
                        border(Border(1f.settingsDp(), BorderStyle.SOLID, ctx.palette().divider))
                        textAlignLeft()
                        text(value())
                        fontSize(14f.settingsDp())
                        color(ctx.palette().textPrimary)
                        tintColor(ctx.palette().accent)
                        placeholder(placeholder)
                        placeholderColor(ctx.palette().textTertiary)
                        maxTextLength(240)
                        touchEnable(!readOnly)
                    }
                    event {
                        textDidChange(isSyncEdit = true) { onChanged(it.text) }
                    }
                }
            }
        }
    }

    private fun ModelList(container: ViewContainer<*, *>) {
        val ctx = this
        with(container) {
            View {
                View {
                    attr {
                        width((ctx.pagerData.pageViewWidth - 40f.settingsDp()).coerceAtLeast(1f))
                        alignSelfCenter()
                        marginTop(20f.settingsDp())
                        flexDirectionRow()
                        alignItemsCenter()
                    }
                    Text {
                        attr {
                            text("模型列表")
                            fontSize(14f.settingsDp())
                            fontWeightBold()
                            color(ctx.palette().textTertiary)
                            flex(1f)
                        }
                    }
                    Text {
                        attr {
                            text(
                                when {
                                    ctx.modelListLoading -> "获取中"
                                    ctx.modelListVisible -> "${ctx.availableModels.size} 个模型"
                                    ctx.modelListError.isNotBlank() -> "获取失败"
                                    else -> "待获取"
                                },
                            )
                            fontSize(12f.settingsDp())
                            color(ctx.palette().textSecondary)
                        }
                    }
                }
                vif({ ctx.modelListLoading }) {
                    ctx.ModelCatalogStatus(this, "正在从 Provider 获取可用模型…", false)
                }
                vif({ !ctx.modelListLoading && ctx.modelListError.isNotBlank() }) {
                    ctx.ModelCatalogStatus(this, ctx.modelListError, true)
                }
                vif({
                    ctx.modelListVisible && ctx.availableModels.isEmpty()
                }) {
                    ctx.ModelCatalogStatus(this, "该 Provider 暂未返回可用模型。", true)
                }
                vif({
                    ctx.modelListVisible && ctx.availableModels.isNotEmpty()
                }) {
                    ctx.availableModels.forEach { model ->
                        ctx.ModelCard(this, model)
                    }
                }
                vif({
                    !ctx.modelListLoading &&
                        !ctx.modelListVisible &&
                        ctx.modelListError.isBlank()
                }) {
                    ctx.BuiltInModelCard(this)
                }
            }
        }
    }

    private fun BuiltInModelCard(container: ViewContainer<*, *>) {
        val ctx = this
        with(container) {
            SettingsCard(
                width = (ctx.pagerData.pageViewWidth - 32f.settingsDp()).coerceAtLeast(1f),
                palette = ctx::palette,
                marginTop = 10f.settingsDp(),
            ) {
                View {
                    attr {
                        minHeight(72f.settingsDp())
                        padding(all = 16f.settingsDp())
                        flexDirectionRow()
                        alignItemsCenter()
                        backgroundColor(ctx.palette().accentSoft)
                    }
                    Text {
                        attr {
                            text("千问")
                            fontSize(15f.settingsDp())
                            fontWeightBold()
                            color(ctx.palette().textPrimary)
                        }
                    }
                    Text {
                        attr {
                            text("🔒 内置默认模型")
                            fontSize(12f.settingsDp())
                            color(ctx.palette().textSecondary)
                            marginLeft(10f.settingsDp())
                        }
                    }
                }
            }
        }
    }

    private fun ModelCatalogStatus(
        container: ViewContainer<*, *>,
        message: String,
        isError: Boolean,
    ) {
        val ctx = this
        with(container) {
            SettingsCard(
                width = (ctx.pagerData.pageViewWidth - 32f.settingsDp()).coerceAtLeast(1f),
                palette = ctx::palette,
                marginTop = 10f.settingsDp(),
            ) {
                View {
                    attr {
                        minHeight(58f.settingsDp())
                        padding(
                            top = 14f.settingsDp(),
                            left = 16f.settingsDp(),
                            right = 16f.settingsDp(),
                            bottom = 14f.settingsDp(),
                        )
                    }
                    Text {
                        attr {
                            text(message)
                            fontSize(12f.settingsDp())
                            lineHeight(18f.settingsDp())
                            color(
                                if (isError) {
                                    ctx.palette().warning
                                } else {
                                    ctx.palette().textSecondary
                                },
                            )
                        }
                    }
                    if (isError) {
                        View {
                            attr {
                                height(30f.settingsDp())
                                alignSelfFlexStart()
                                padding(left = 10f.settingsDp(), right = 10f.settingsDp())
                                borderRadius(15f.settingsDp())
                                backgroundColor(ctx.palette().accentSoft)
                                allCenter()
                                marginTop(8f.settingsDp())
                            }
                            event {
                                click { ctx.loadModels(force = true) }
                            }
                            Text {
                                attr {
                                    text("重试")
                                    fontSize(11f.settingsDp())
                                    fontWeightBold()
                                    color(ctx.palette().accent)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun ModelCard(container: ViewContainer<*, *>, model: ModelOption) {
        val ctx = this
        with(container) {
            SettingsCard(
                width = (ctx.pagerData.pageViewWidth - 32f.settingsDp()).coerceAtLeast(1f),
                palette = ctx::palette,
                marginTop = 10f.settingsDp(),
            ) {
                View {
                    attr {
                        minHeight(84f.settingsDp())
                        padding(
                            top = 14f.settingsDp(),
                            left = 16f.settingsDp(),
                            right = 14f.settingsDp(),
                            bottom = 14f.settingsDp(),
                        )
                        flexDirectionRow()
                        alignItemsCenter()
                        backgroundColor(
                            if (ctx.selectedModelId == model.id) {
                                ctx.palette().accentSoft
                            } else {
                                ctx.palette().surface
                            },
                        )
                    }
                    event {
                        click { ctx.chooseModel(model.id) }
                    }
                    View {
                        attr { flex(1f) }
                        Text {
                            attr {
                                text(model.displayName)
                                fontSize(15f.settingsDp())
                                fontWeightBold()
                                color(ctx.palette().textPrimary)
                                lines(1)
                            }
                        }
                        Text {
                            attr {
                                text(model.id)
                                fontSize(11f.settingsDp())
                                color(ctx.palette().textSecondary)
                                marginTop(4f.settingsDp())
                                lines(1)
                            }
                        }
                        View {
                            attr {
                                height(22f.settingsDp())
                                flexDirectionRow()
                                alignItemsCenter()
                                marginTop(7f.settingsDp())
                            }
                            model.capabilities.forEach { capability ->
                                ctx.CapabilityChip(this, capability.displayName)
                            }
                        }
                    }
                    View {
                        attr {
                            size(26f.settingsDp(), 26f.settingsDp())
                            borderRadius(13f.settingsDp())
                            border(
                                Border(
                                    1.5f.settingsDp(),
                                    BorderStyle.SOLID,
                                    if (ctx.selectedModelId == model.id) {
                                        ctx.palette().accent
                                    } else {
                                        ctx.palette().divider
                                    },
                                ),
                            )
                            backgroundColor(
                                if (ctx.selectedModelId == model.id) {
                                    ctx.palette().accent
                                } else {
                                    ctx.palette().surface
                                },
                            )
                            allCenter()
                        }
                        Text {
                            attr {
                                text(if (ctx.selectedModelId == model.id) "✓" else "")
                                fontSize(13f.settingsDp())
                                fontWeightBold()
                                color(Color.WHITE)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun CapabilityChip(container: ViewContainer<*, *>, label: String) {
        val ctx = this
        with(container) {
            View {
                attr {
                    height(20f.settingsDp())
                    borderRadius(6f.settingsDp())
                    padding(left = 7f.settingsDp(), right = 7f.settingsDp())
                    marginRight(6f.settingsDp())
                    backgroundColor(ctx.palette().surfaceMuted)
                    allCenter()
                }
                Text {
                    attr {
                        text(label)
                        fontSize(9f.settingsDp())
                        fontWeightMedium()
                        color(ctx.palette().textSecondary)
                    }
                }
            }
        }
    }

    private fun SecurityNotice(container: ViewContainer<*, *>) {
        val ctx = this
        with(container) {
            View {
                attr {
                    width((ctx.pagerData.pageViewWidth - 32f.settingsDp()).coerceAtLeast(1f))
                    alignSelfCenter()
                    marginTop(14f.settingsDp())
                    borderRadius(16f.settingsDp())
                    backgroundColor(ctx.palette().warningSoft)
                    padding(
                        top = 13f.settingsDp(),
                        left = 14f.settingsDp(),
                        right = 14f.settingsDp(),
                        bottom = 13f.settingsDp(),
                    )
                }
                Text {
                    attr {
                        text("安全提示")
                        fontSize(12f.settingsDp())
                        fontWeightBold()
                        color(ctx.palette().warning)
                    }
                }
                Text {
                    attr {
                        text("源码不包含任何 API Key，测试密钥仅在本次运行中保留，应用重启后需重新填写。请避免在分享截图或日志中暴露。")
                        fontSize(11f.settingsDp())
                        lineHeight(17f.settingsDp())
                        color(ctx.palette().warning)
                        marginTop(4f.settingsDp())
                    }
                }
            }
        }
    }

    private fun selectProvider(providerId: String) {
        val provider = configuration.providers.firstOrNull { it.id == providerId } ?: return
        selectedProviderId = provider.id
        providerName = provider.displayName
        baseUrl = provider.baseUrl
        apiKey = provider.apiKey
        selectedModelId = provider.selectedModelId
        keyVisible = false
        resetModelCatalog()
        if (provider.models.isNotEmpty()) {
            // 先展示上一次拉取并持久化的模型列表，再视情况后台刷新
            availableModels = provider.models
            modelListVisible = true
        }
        if (provider.apiKey.isNotBlank()) {
            // 已填 API Key 时自动拉取实际模型，避免停留在过期的内置数据上
            loadModelsIfNeeded()
        }
    }

    private fun selectedProvider(): ModelProviderConfig {
        return configuration.providers.firstOrNull { it.id == selectedProviderId }
            ?: configuration.providers.first()
    }

    private fun saveProvider() {
        val updated = currentDraftProvider() ?: return
        StockChatSettingsStore.repository.saveModelProvider(updated)
        StockChatSettingsStore.repository.selectModel(updated.id, updated.selectedModelId)
        reloadConfiguration(updated.id)
        bridgeModule.toast("模型配置已保存")
    }

    private fun chooseModel(modelId: String) {
        selectedModelId = modelId
        val updated = currentDraftProvider() ?: return
        StockChatSettingsStore.repository.saveModelProvider(updated)
        StockChatSettingsStore.repository.selectModel(updated.id, modelId)
        reloadConfiguration(updated.id)
    }

    private fun switchProvider(providerId: String) {
        if (providerId == selectedProviderId) {
            return
        }
        val currentDraft = currentDraftProvider() ?: return
        StockChatSettingsStore.repository.saveModelProvider(currentDraft)
        configuration = StockChatSettingsStore.repository.loadSnapshot().modelConfiguration
        selectProvider(providerId)
    }

    private fun updateApiKey(value: String) {
        if (apiKey == value) {
            return
        }
        apiKey = value
        resetModelCatalog()
    }

    private fun updateBaseUrl(value: String) {
        if (baseUrl == value) {
            return
        }
        baseUrl = value
        resetModelCatalog()
    }

    private fun resetModelCatalog() {
        modelRequestToken += 1
        availableModels = emptyList()
        modelListVisible = false
        modelListLoading = false
        modelListError = ""
        // 保留 lastAttempted/lastLoaded 指纹：指纹包含 providerId、Base URL 与 Key，
        // 切回已拉取过的 Provider 时可避免重复请求
    }

    private fun modelRequestFingerprint(): String {
        return "${selectedProviderId}|${baseUrl.trim().trimEnd('/')}|${apiKey.trim()}"
    }

    private fun loadModelsIfNeeded() {
        if (apiKey.trim().isBlank()) {
            return
        }
        val fingerprint = modelRequestFingerprint()
        if (
            modelListLoading ||
            fingerprint == lastAttemptedModelRequest ||
            fingerprint == lastLoadedModelRequest
        ) {
            return
        }
        loadModels()
    }

    private fun loadModels(force: Boolean = false) {
        val normalizedBaseUrl = baseUrl.trim().trimEnd('/')
        val normalizedApiKey = apiKey.trim()
        if (normalizedBaseUrl.isBlank()) {
            modelListVisible = false
            modelListError = "请先填写 Base URL。"
            bridgeModule.toast("请先填写 Base URL")
            return
        }
        if (normalizedApiKey.isBlank()) {
            modelListVisible = false
            modelListError = "请先填写 API Key。"
            bridgeModule.toast("请先填写 API Key")
            return
        }
        if (!::modelCatalogService.isInitialized) {
            return
        }
        val fingerprint = modelRequestFingerprint()
        if (modelListLoading || (!force && fingerprint == lastAttemptedModelRequest)) {
            return
        }
        val providerId = selectedProviderId
        val requestToken = modelRequestToken + 1
        modelRequestToken = requestToken
        lastAttemptedModelRequest = fingerprint
        modelListLoading = true
        modelListVisible = false
        modelListError = ""
        modelCatalogService.load(normalizedBaseUrl, normalizedApiKey) { result ->
            if (requestToken != modelRequestToken || providerId != selectedProviderId) {
                return@load
            }
            modelListLoading = false
            when (result) {
                is ModelCatalogResult.Failure -> {
                    modelListVisible = false
                    modelListError = result.message
                    // 失败后清除尝试指纹，下次 blur/切换回该 Provider 时可自动重试
                    lastAttemptedModelRequest = ""
                }
                is ModelCatalogResult.Success -> {
                    val models = result.models
                    availableModels = models
                    modelListVisible = true
                    modelListError = ""
                    lastLoadedModelRequest = fingerprint
                    if (models.isNotEmpty()) {
                        val nextSelectedModelId = selectedModelId.takeIf { id ->
                            models.any { model -> model.id == id }
                        }.orEmpty()
                        selectedModelId = nextSelectedModelId
                        val provider = selectedProvider()
                        val updated = provider.copy(
                            displayName = providerName.trim().ifBlank { provider.kind.displayName },
                            baseUrl = normalizedBaseUrl,
                            apiKey = normalizedApiKey,
                            models = models,
                            selectedModelId = nextSelectedModelId,
                        )
                        StockChatSettingsStore.repository.saveModelProvider(updated)
                        configuration = StockChatSettingsStore.repository.loadSnapshot().modelConfiguration
                        providerName = updated.displayName
                        baseUrl = updated.baseUrl
                        apiKey = updated.apiKey
                        bridgeModule.toast("已获取 ${models.size} 个可用模型")
                    }
                }
            }
        }
    }

    private fun currentDraftProvider(): ModelProviderConfig? {
        val normalizedName = providerName.trim()
        if (normalizedName.isEmpty()) {
            bridgeModule.toast("请输入 Provider 名称")
            return null
        }
        val normalizedBaseUrl = baseUrl.trim()
        if (normalizedBaseUrl.isEmpty()) {
            bridgeModule.toast("请输入 Base URL")
            return null
        }
        return selectedProvider().copy(
            displayName = normalizedName,
            baseUrl = normalizedBaseUrl,
            apiKey = apiKey.trim(),
            selectedModelId = selectedModelId,
            isEnabled = true,
        )
    }

    private fun reloadConfiguration(providerId: String) {
        configuration = StockChatSettingsStore.repository.loadSnapshot().modelConfiguration
        val provider = configuration.providers.firstOrNull { it.id == providerId }
        if (provider == null) {
            selectProvider(providerId)
            return
        }
        selectedProviderId = provider.id
        providerName = provider.displayName
        baseUrl = provider.baseUrl
        apiKey = provider.apiKey
        selectedModelId = provider.selectedModelId
        keyVisible = false
    }

    private fun closePage() {
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
    }

    private fun palette(): SettingsPalette {
        val isDark = when (themeMode) {
            ThemeMode.SYSTEM -> isNightMode()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        return if (isDark) SettingsPalettes.Dark else SettingsPalettes.Light
    }

    private fun providerAsset(kind: ModelProviderKind): String = when (kind) {
        ModelProviderKind.DEFAULT -> "stockchat_app_icon.png"
        ModelProviderKind.ALIYUN -> "tongyi-qianwen.png"
        ModelProviderKind.DEEPSEEK -> "deepseek.png"
        ModelProviderKind.GLM -> "glm.png"
        ModelProviderKind.KIMI -> "kimi.png"
        ModelProviderKind.MIMO -> "mimo.png"
        ModelProviderKind.CUSTOM -> "stockchat_app_icon.png"
    }
}
