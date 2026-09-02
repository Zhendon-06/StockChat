package com.guet.liang.stockchat.ui.settings

import com.guet.liang.stockchat.base.BasePager
import com.guet.liang.stockchat.base.bridgeModule
import com.guet.liang.stockchat.data.StockChatSettingsStore
import com.guet.liang.stockchat.model.ModelCapability
import com.guet.liang.stockchat.model.ModelConfiguration
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
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Switch
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.TextArea
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
    private var providerEnabled by observable(true)
    private var keyVisible by observable(false)
    private var customModelId by observable("")
    private var customHeadersExpanded by observable(false)

    override fun created() {
        super.created()
        val snapshot = StockChatSettingsStore.repository.loadSnapshot()
        configuration = snapshot.modelConfiguration
        themeMode = snapshot.appearance.themeMode
        selectProvider(configuration.activeProviderId)
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
                        text("切换并维护五个模型服务商的地址、密钥与默认模型，也可补充自定义 Model ID。")
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
                    val selected = ctx.selectedProviderId == provider.id
                    View {
                        attr {
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
                                backgroundColor(ctx.providerColor(provider.kind))
                                allCenter()
                            }
                            Text {
                                attr {
                                    text(ctx.providerMark(provider.kind))
                                    fontSize(14f.settingsDp())
                                    fontWeightBold()
                                    color(Color.WHITE)
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
                                backgroundColor(ctx.providerColor(ctx.selectedProvider().kind))
                                allCenter()
                            }
                            Text {
                                attr {
                                    text(ctx.providerMark(ctx.selectedProvider().kind))
                                    fontSize(16f.settingsDp())
                                    fontWeightBold()
                                    color(Color.WHITE)
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
                        Switch {
                            attr {
                                size(48f.settingsDp(), 29f.settingsDp())
                                isOn(ctx.providerEnabled)
                                onColor(ctx.palette().accent)
                                unOnColor(ctx.palette().surfaceMuted)
                                thumbColor(Color.WHITE)
                            }
                            event {
                                switchOnChanged { ctx.providerEnabled = it }
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
                        onChanged = { ctx.baseUrl = it },
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
                            }
                            TextArea {
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
                                    if (!ctx.keyVisible) {
                                        keyboardTypePassword()
                                    }
                                    maxTextLength(256)
                                }
                                event {
                                    textDidChange(isSyncEdit = true) { ctx.apiKey = it.text }
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
                            height(48f.settingsDp())
                            flexDirectionRow()
                            alignItemsCenter()
                            marginTop(10f.settingsDp())
                        }
                        event {
                            click { ctx.customHeadersExpanded = !ctx.customHeadersExpanded }
                        }
                        Text {
                            attr {
                                text("自定义请求头")
                                fontSize(15f.settingsDp())
                                fontWeightBold()
                                color(ctx.palette().textPrimary)
                            }
                        }
                        Text {
                            attr {
                                text("点击展开配置")
                                fontSize(12f.settingsDp())
                                color(ctx.palette().textTertiary)
                                marginLeft(10f.settingsDp())
                                flex(1f)
                            }
                        }
                        Text {
                            attr {
                                text(if (ctx.customHeadersExpanded) "⌃" else "⌄")
                                fontSize(18f.settingsDp())
                                color(ctx.palette().textSecondary)
                            }
                        }
                    }
                    vif({ ctx.customHeadersExpanded }) {
                        View {
                            attr {
                                minHeight(54f.settingsDp())
                                borderRadius(12f.settingsDp())
                                backgroundColor(ctx.palette().surfaceMuted)
                                padding(
                                    top = 10f.settingsDp(),
                                    left = 12f.settingsDp(),
                                    right = 12f.settingsDp(),
                                    bottom = 10f.settingsDp(),
                                )
                            }
                            Text {
                                attr {
                                    text("当前五个预置 Provider 均使用 Authorization: Bearer <API Key>。自定义请求头将在接入安全存储后开放。")
                                    fontSize(11f.settingsDp())
                                    lineHeight(17f.settingsDp())
                                    color(ctx.palette().textSecondary)
                                }
                            }
                        }
                    }
                    View {
                        attr {
                            height(44f.settingsDp())
                            borderRadius(22f.settingsDp())
                            backgroundColor(ctx.palette().accent)
                            allCenter()
                            marginTop(14f.settingsDp())
                        }
                        event {
                            click { ctx.saveProvider() }
                        }
                        Text {
                            attr {
                                text("保存并设为当前 Provider")
                                fontSize(14f.settingsDp())
                                fontWeightBold()
                                color(Color.WHITE)
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
                TextArea {
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
                        text("${ctx.selectedProvider().models.size} 个模型")
                        fontSize(12f.settingsDp())
                        color(ctx.palette().textSecondary)
                    }
                }
            }
            ctx.configuration.providers.forEach { provider ->
                vif({ ctx.selectedProviderId == provider.id }) {
                    provider.models.forEach { model ->
                        ctx.ModelCard(this, model)
                    }
                }
            }
            SettingsCard(
                width = (ctx.pagerData.pageViewWidth - 32f.settingsDp()).coerceAtLeast(1f),
                palette = ctx::palette,
                marginTop = 10f.settingsDp(),
            ) {
                View {
                    attr {
                        height(76f.settingsDp())
                        padding(left = 14f.settingsDp(), right = 12f.settingsDp())
                        flexDirectionRow()
                        alignItemsCenter()
                    }
                    TextArea {
                        attr {
                            flex(1f)
                            height(44f.settingsDp())
                            borderRadius(12f.settingsDp())
                            backgroundColor(ctx.palette().surfaceMuted)
                            textAlignLeft()
                            text(ctx.customModelId)
                            fontSize(13f.settingsDp())
                            color(ctx.palette().textPrimary)
                            tintColor(ctx.palette().accent)
                            placeholder("输入自定义 Model ID")
                            placeholderColor(ctx.palette().textTertiary)
                            maxTextLength(120)
                        }
                        event {
                            textDidChange(isSyncEdit = true) { ctx.customModelId = it.text }
                        }
                    }
                    View {
                        attr {
                            width(66f.settingsDp())
                            height(44f.settingsDp())
                            borderRadius(20f.settingsDp())
                            backgroundColor(ctx.palette().accent)
                            marginLeft(9f.settingsDp())
                            allCenter()
                        }
                        event {
                            click { ctx.addCustomModel() }
                        }
                        Text {
                            attr {
                                text("＋ 添加")
                                fontSize(12f.settingsDp())
                                fontWeightBold()
                                color(Color.WHITE)
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
                            ctx.CapabilityChip(this, model.contextWindowLabel)
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
        providerEnabled = provider.isEnabled
        keyVisible = false
        customModelId = ""
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

    private fun addCustomModel() {
        val modelId = customModelId.trim()
        if (modelId.isEmpty()) {
            bridgeModule.toast("请输入 Model ID")
            return
        }
        val provider = currentDraftProvider() ?: return
        if (provider.models.any { it.id == modelId }) {
            chooseModel(modelId)
            customModelId = ""
            return
        }
        val updated = provider.copy(
            models = provider.models + ModelOption(
                id = modelId,
                displayName = modelId,
                contextWindowLabel = "自定义",
                capabilities = setOf(ModelCapability.CHAT),
            ),
            selectedModelId = modelId,
        )
        StockChatSettingsStore.repository.saveModelProvider(updated)
        StockChatSettingsStore.repository.selectModel(updated.id, modelId)
        customModelId = ""
        reloadConfiguration(updated.id)
        bridgeModule.toast("已添加自定义模型")
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
            isEnabled = providerEnabled,
        )
    }

    private fun reloadConfiguration(providerId: String) {
        configuration = StockChatSettingsStore.repository.loadSnapshot().modelConfiguration
        selectProvider(providerId)
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

    private fun providerMark(kind: ModelProviderKind): String = when (kind) {
        ModelProviderKind.ALIYUN -> "千"
        ModelProviderKind.DEEPSEEK -> "D"
        ModelProviderKind.GLM -> "G"
        ModelProviderKind.KIMI -> "K"
        ModelProviderKind.MIMO -> "M"
        ModelProviderKind.CUSTOM -> "+"
    }

    private fun providerColor(kind: ModelProviderKind): Color = when (kind) {
        ModelProviderKind.ALIYUN -> Color(0xFF6557E8)
        ModelProviderKind.DEEPSEEK -> Color(0xFF4D6BFE)
        ModelProviderKind.GLM -> Color(0xFF2457D6)
        ModelProviderKind.KIMI -> Color(0xFF111827)
        ModelProviderKind.MIMO -> Color(0xFFFF6A2A)
        ModelProviderKind.CUSTOM -> palette().accent
    }
}
