package com.guet.liang.stockchat.ui.settings

import com.guet.liang.stockchat.base.BasePager
import com.guet.liang.stockchat.base.bridgeModule
import com.guet.liang.stockchat.data.ModelCatalogService
import com.guet.liang.stockchat.data.StockChatSettingsStore
import com.guet.liang.stockchat.model.ModelOption
import com.guet.liang.stockchat.model.ModelProviderKind
import com.guet.liang.stockchat.model.ThemeMode
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Scroller

@Page(MODEL_CONFIGURATION_PAGE_NAME, supportInLocal = true)
internal class ModelConfigurationPage : BasePager() {
    internal var configuration by observable(StockChatSettingsStore.repository.loadSnapshot().modelConfiguration)
    private var themeMode by observable(ThemeMode.SYSTEM)
    internal var selectedProviderId by observable("")
    internal var providerName by observable("")
    internal var baseUrl by observable("")
    internal var apiKey by observable("")
    internal var selectedModelId by observable("")
    internal var keyVisible by observable(false)
    internal var availableModels by observable<List<ModelOption>>(emptyList())
    internal var modelListVisible by observable(false)
    internal var modelListLoading by observable(false)
    internal var modelListError by observable("")
    internal var unsavedDialogOpen by observable(false)
    internal var modelRequestToken = 0
    internal var lastAttemptedModelRequest = ""
    internal lateinit var modelCatalogService: ModelCatalogService
    // lateinit 就位探针：扩展函数无法直接使用 ::prop.isInitialized，统一从这里读取
    internal val modelCatalogServiceReady: Boolean
        get() = ::modelCatalogService.isInitialized

    override fun created() {
        super.created()
        modelCatalogService = ModelCatalogService(
            acquireModule<NetworkModule>(NetworkModule.MODULE_NAME),
        )
        val snapshot = StockChatSettingsStore.repository.loadSnapshot()
        configuration = snapshot.modelConfiguration
        themeMode = snapshot.appearance.themeMode
        selectProvider(configuration.activeProviderId)
        bridgeModule.observeBackRequests {
            closePage()
        }
    }

    override fun pageDidAppear() {
        super.pageDidAppear()
        val snapshot = StockChatSettingsStore.repository.loadSnapshot()
        configuration = snapshot.modelConfiguration
        themeMode = snapshot.appearance.themeMode
        selectProvider(configuration.activeProviderId)
    }

    override fun pageWillDestroy() {
        modelRequestToken += 1
        bridgeModule.stopObservingBackRequests()
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
            ctx.UnsavedChangesDialog(this)
        }
    }

    internal fun closePage() {
        if (unsavedDialogOpen) {
            return
        }
        if (hasUnsavedChanges()) {
            unsavedDialogOpen = true
            return
        }
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
    }

    internal fun palette(): SettingsPalette = settingsPalette(themeMode)

    internal fun providerAsset(kind: ModelProviderKind): String = when (kind) {
        ModelProviderKind.DEFAULT -> "stockchat_app_icon.png"
        ModelProviderKind.ALIYUN -> "tongyi-qianwen.png"
        ModelProviderKind.DEEPSEEK -> "deepseek.png"
        ModelProviderKind.GLM -> "glm.png"
        ModelProviderKind.KIMI -> "kimi.png"
        ModelProviderKind.MIMO -> "mimo.png"
        ModelProviderKind.CUSTOM -> "stockchat_app_icon.png"
    }
}
