package com.guet.liang.stockchat.controller

import com.guet.liang.stockchat.model.ModelCatalogResult
import com.guet.liang.stockchat.model.ModelOption
import com.guet.liang.stockchat.model.ModelProviderConfig
import com.guet.liang.stockchat.model.ModelProviderKind

/** Editable provider fields, independent of the page and its observables. */
internal data class ModelProviderDraft(
    val provider: ModelProviderConfig,
    val displayName: String,
    val baseUrl: String,
    val apiKey: String,
    val selectedModelId: String,
)

/** Validation keeps invalid drafts out of persistent settings. */
internal sealed class ModelProviderDraftResult {
    data class Valid(val provider: ModelProviderConfig) : ModelProviderDraftResult()

    data class Invalid(val message: String) : ModelProviderDraftResult()
}

/** Catalog state consumed by the provider editor. */
internal data class ModelConfigurationCatalogState(
    val models: List<ModelOption> = emptyList(),
    val visible: Boolean = false,
    val loading: Boolean = false,
    val error: String = "",
)

/** Owns provider draft validation and cancellable, deduplicated catalog requests. */
internal class ModelConfigurationController(
    private val settings: SettingsController,
    private val scheduleTimeout: (Int, () -> Unit) -> Unit,
    private val onCatalogChanged: (ModelConfigurationCatalogState) -> Unit,
    private val onProviderSaved: (ModelProviderConfig) -> Unit,
) {
    var catalogState = ModelConfigurationCatalogState()
        private set

    private var requestToken = 0
    private var lastAttemptedRequest = ""

    fun resetCatalog() {
        requestToken += 1
        lastAttemptedRequest = ""
        publish(ModelConfigurationCatalogState())
    }

    fun validateDraft(draft: ModelProviderDraft): ModelProviderDraftResult {
        val name = draft.displayName.trim()
        val url = draft.baseUrl.trim()
        return when {
            name.isEmpty() -> ModelProviderDraftResult.Invalid("请输入 Provider 名称")
            url.isEmpty() && draft.provider.kind != ModelProviderKind.DEFAULT ->
                ModelProviderDraftResult.Invalid("请输入 Base URL")
            else ->
                ModelProviderDraftResult.Valid(
                    draft.provider.copy(
                        displayName = name,
                        baseUrl = url,
                        apiKey = draft.apiKey.trim(),
                        selectedModelId = draft.selectedModelId,
                        isEnabled = true,
                    )
                )
        }
    }

    fun hasUnsavedChanges(draft: ModelProviderDraft): Boolean {
        return draft.provider.copy(
            displayName = draft.displayName.trim().ifBlank { draft.provider.kind.displayName },
            baseUrl = draft.baseUrl.trim().trimEnd('/'),
            apiKey = draft.apiKey.trim(),
            selectedModelId = draft.selectedModelId,
        ) != draft.provider
    }

    fun loadModels(currentDraft: () -> ModelProviderDraft, force: Boolean = false): String? {
        val draft = currentDraft()
        val provider =
            draft.provider.copy(
                baseUrl = draft.baseUrl.trim().trimEnd('/'),
                apiKey = draft.apiKey.trim(),
            )
        val error = catalogInputError(provider)
        if (error != null) {
            resetCatalog()
            publish(ModelConfigurationCatalogState(error = "$error。"))
            return error
        }
        val fingerprint = "${provider.id}|${provider.baseUrl}|${provider.apiKey}"
        if (catalogState.loading || (!force && fingerprint == lastAttemptedRequest)) return null
        requestToken += 1
        val token = requestToken
        lastAttemptedRequest = fingerprint
        publish(ModelConfigurationCatalogState(loading = true))
        scheduleTimeout(MODEL_CATALOG_FALLBACK_TIMEOUT_MS) { timeout(token) }
        settings.fetchCatalog(provider) { result ->
            if (
                token == requestToken &&
                    catalogState.loading &&
                    provider.id == currentDraft().provider.id
            ) {
                completeCatalog(result, currentDraft())
            }
        }
        return null
    }

    private fun catalogInputError(provider: ModelProviderConfig): String? =
        when {
            provider.baseUrl.isBlank() -> "请先填写 Base URL"
            provider.apiKey.isBlank() -> "请先填写 API Key"
            else -> null
        }

    private fun timeout(token: Int) {
        if (token == requestToken && catalogState.loading) {
            requestToken += 1
            lastAttemptedRequest = ""
            publish(ModelConfigurationCatalogState(error = "模型列表请求超时，请检查网络后重试。"))
        }
    }

    private fun completeCatalog(result: ModelCatalogResult, draft: ModelProviderDraft) {
        when (result) {
            is ModelCatalogResult.Failure -> {
                lastAttemptedRequest = ""
                publish(ModelConfigurationCatalogState(error = result.message))
            }
            is ModelCatalogResult.Success -> {
                val updated = providerWithCatalog(draft, result.models)
                settings.saveProvider(updated)
                publish(ModelConfigurationCatalogState(models = result.models, visible = true))
                onProviderSaved(updated)
            }
        }
    }

    private fun providerWithCatalog(
        draft: ModelProviderDraft,
        models: List<ModelOption>,
    ): ModelProviderConfig {
        return draft.provider.copy(
            displayName = draft.displayName.trim().ifBlank { draft.provider.kind.displayName },
            baseUrl = draft.baseUrl.trim().trimEnd('/'),
            apiKey = draft.apiKey.trim(),
            models = models,
            selectedModelId =
                models.firstOrNull { it.id == draft.selectedModelId }?.id
                    ?: models.firstOrNull()?.id.orEmpty(),
        )
    }

    private fun publish(state: ModelConfigurationCatalogState) {
        catalogState = state
        onCatalogChanged(state)
    }

    companion object {
        private const val MODEL_CATALOG_FALLBACK_TIMEOUT_MS = 35_000
    }
}
