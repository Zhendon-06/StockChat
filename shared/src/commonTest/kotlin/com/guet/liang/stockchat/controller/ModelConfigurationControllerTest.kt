package com.guet.liang.stockchat.controller

import com.guet.liang.stockchat.model.ModelCatalogResult
import com.guet.liang.stockchat.model.ModelOption
import com.guet.liang.stockchat.model.ModelProviderKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ModelConfigurationControllerTest {
    @Test
    fun blankProviderNameAndCustomUrlAreRejected() {
        val fixture = Fixture()
        assertIs<ModelProviderDraftResult.Invalid>(fixture.controller.validateDraft(fixture.draft.copy(displayName = " ")))
        assertIs<ModelProviderDraftResult.Invalid>(fixture.controller.validateDraft(fixture.draft.copy(baseUrl = " ")))
        assertIs<ModelProviderDraftResult.Valid>(
            fixture.controller.validateDraft(
                fixture.draft.copy(provider = settingsTestProvider.copy(kind = ModelProviderKind.DEFAULT), baseUrl = "")
            )
        )
    }

    @Test
    fun draftComparisonNormalizesWhitespaceAndTrailingSlash() {
        val fixture = Fixture()
        val provider = settingsTestProvider.copy(baseUrl = "https://example.com/v1", apiKey = "demo-key")
        assertFalse(fixture.controller.hasUnsavedChanges(fixture.draft.copy(provider = provider)))
        assertTrue(fixture.controller.hasUnsavedChanges(fixture.draft.copy(provider = provider, displayName = "new name")))
    }

    @Test
    fun missingCredentialsPublishErrorWithoutNetworkRequest() {
        val fixture = Fixture()
        fixture.draft = fixture.draft.copy(apiKey = "")
        assertEquals("请先填写 API Key", fixture.load())
        assertEquals(0, fixture.requests.size)
        assertFalse(fixture.controller.catalogState.loading)
        assertEquals("请先填写 API Key。", fixture.controller.catalogState.error)
    }

    @Test
    fun loadingAndSuccessfulFingerprintAreDeduplicatedUntilForced() {
        val fixture = Fixture()
        fixture.load()
        fixture.load(force = true)
        assertEquals(1, fixture.requests.size)
        fixture.requests.single()(ModelCatalogResult.Success(settingsTestProvider.models))
        fixture.load()
        assertEquals(1, fixture.requests.size)
        fixture.load(force = true)
        assertEquals(2, fixture.requests.size)
    }

    @Test
    fun catalogSuccessPersistsModelsAndSelectsValidFallback() {
        val fixture = Fixture()
        fixture.load()
        val model = ModelOption("model-new", "New Model", "64K", emptySet())
        fixture.requests.single()(ModelCatalogResult.Success(listOf(model)))

        val provider = fixture.repository.snapshot.value.modelConfiguration.providers.single()
        assertEquals(listOf(model), provider.models)
        assertEquals(model.id, provider.selectedModelId)
        assertEquals("https://example.com/v1", provider.baseUrl)
        assertEquals("demo-key", provider.apiKey)
        assertTrue(fixture.controller.catalogState.visible)
        assertFalse(fixture.controller.catalogState.loading)
    }

    @Test
    fun timeoutAllowsRetryAndIgnoresLateResponse() {
        val fixture = Fixture()
        fixture.load()
        fixture.timeouts.single()()
        assertEquals("模型列表请求超时，请检查网络后重试。", fixture.controller.catalogState.error)
        fixture.requests.single()(ModelCatalogResult.Success(emptyList()))
        assertEquals(settingsTestProvider.models, fixture.repository.snapshot.value.modelConfiguration.providers.single().models)
        fixture.load()
        assertEquals(2, fixture.requests.size)
    }

    @Test
    fun changingProviderInvalidatesPreviousCatalogRequest() {
        val fixture = Fixture()
        fixture.load()
        fixture.controller.resetCatalog()
        fixture.draft = fixture.draft.copy(provider = settingsTestProvider.copy(id = "other"))
        fixture.requests.single()(ModelCatalogResult.Success(emptyList()))
        assertEquals(ModelConfigurationCatalogState(), fixture.controller.catalogState)
        assertEquals(settingsTestProvider.models, fixture.repository.snapshot.value.modelConfiguration.providers.single().models)
    }

    @Test
    fun failureAllowsRetryWithoutForce() {
        val fixture = Fixture()
        fixture.load()
        fixture.requests.single()(ModelCatalogResult.Failure("offline"))
        assertEquals("offline", fixture.controller.catalogState.error)
        fixture.load()
        assertEquals(2, fixture.requests.size)
        assertTrue(fixture.controller.catalogState.loading)
    }

    private class Fixture {
        val repository = FakeSettingsRepository()
        val requests = mutableListOf<(ModelCatalogResult) -> Unit>()
        val timeouts = mutableListOf<() -> Unit>()
        var draft =
            ModelProviderDraft(
                provider = settingsTestProvider,
                displayName = settingsTestProvider.displayName,
                baseUrl = settingsTestProvider.baseUrl,
                apiKey = settingsTestProvider.apiKey,
                selectedModelId = settingsTestProvider.selectedModelId,
            )
        val controller =
            ModelConfigurationController(
                settings = SettingsController(repository, catalog = SettingsCatalogRepository { _, _, callback -> requests += callback }),
                scheduleTimeout = { _, callback -> timeouts += callback },
                onCatalogChanged = {},
                onProviderSaved = {},
            )

        fun load(force: Boolean = false): String? = controller.loadModels({ draft }, force)
    }
}
