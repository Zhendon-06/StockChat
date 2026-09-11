package com.guet.liang.stockchat.ui.settings

import com.guet.liang.stockchat.base.BasePager
import com.guet.liang.stockchat.base.bridgeModule
import com.guet.liang.stockchat.base.setTimeout
import com.guet.liang.stockchat.controller.ModelConfigurationController
import com.guet.liang.stockchat.controller.SettingsController
import com.guet.liang.stockchat.controller.settingsController
import com.guet.liang.stockchat.model.ModelConfiguration
import com.guet.liang.stockchat.model.ModelOption
import com.guet.liang.stockchat.model.ModelProviderKind
import com.guet.liang.stockchat.model.ThemeMode
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Scroller

@Page(MODEL_CONFIGURATION_PAGE_NAME, supportInLocal = true)
/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal class ModelConfigurationPage : BasePager() {
    internal lateinit var controller: SettingsController
    internal lateinit var modelController: ModelConfigurationController
    internal var configuration by observable(ModelConfiguration("", emptyList()))
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

    override fun created() {
        super.created()
        controller = settingsController()
        modelController =
            ModelConfigurationController(
                settings = controller,
                scheduleTimeout = { delay, callback -> setTimeout(delay, callback) },
                onCatalogChanged = { state ->
                    availableModels = state.models
                    modelListVisible = state.visible
                    modelListLoading = state.loading
                    modelListError = state.error
                },
                onProviderSaved = { provider ->
                    reloadConfiguration(provider.id)
                    bridgeModule.toast("已获取 ${provider.models.size} 个可用模型")
                },
            )
        val snapshot = controller.snapshot()
        configuration = snapshot.modelConfiguration
        themeMode = snapshot.appearance.themeMode
        selectProvider(configuration.activeProviderId)
        bridgeModule.observeBackRequests { closePage() }
    }

    override fun pageDidAppear() {
        super.pageDidAppear()
        val snapshot = controller.snapshot()
        configuration = snapshot.modelConfiguration
        themeMode = snapshot.appearance.themeMode
        selectProvider(configuration.activeProviderId)
    }

    override fun pageWillDestroy() {
        modelController.resetCatalog()
        bridgeModule.stopObservingBackRequests()
        super.pageWillDestroy()
    }

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr { backgroundColor(ctx.palette().background) }
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

    internal fun providerAsset(kind: ModelProviderKind): String =
        when (kind) {
            ModelProviderKind.DEFAULT -> "stockchat_app_icon.png"
            ModelProviderKind.ALIYUN -> "tongyi-qianwen.png"
            ModelProviderKind.DEEPSEEK -> "deepseek.png"
            ModelProviderKind.GLM -> "glm.png"
            ModelProviderKind.KIMI -> "kimi.png"
            ModelProviderKind.MIMO -> "mimo.png"
            ModelProviderKind.CUSTOM -> "stockchat_app_icon.png"
        }
}
