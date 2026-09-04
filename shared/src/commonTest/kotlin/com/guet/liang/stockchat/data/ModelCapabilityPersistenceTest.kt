package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.ModelCapability
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ModelCapabilityPersistenceTest {
    @Test
    fun legacyCatalogModelsGainStreamingCapabilityOnRestore() {
        val state = SettingsSnapshotJsonCodec.decode(
            persistedConfiguration(modelStreamingFlag = null),
        )
        val model = state?.modelConfiguration
            ?.providers
            ?.single()
            ?.models
            ?.single()

        assertNotNull(model)
        assertTrue(ModelCapability.CHAT in model.capabilities)
        assertTrue(ModelCapability.STREAMING in model.capabilities)
    }

    @Test
    fun explicitNonStreamingFlagSurvivesRestore() {
        val state = SettingsSnapshotJsonCodec.decode(
            persistedConfiguration(modelStreamingFlag = false),
        )
        val model = state?.modelConfiguration
            ?.providers
            ?.single()
            ?.models
            ?.single()

        assertNotNull(model)
        assertFalse(ModelCapability.STREAMING in model.capabilities)
    }

    @Test
    fun explicitNonVisionFlagSurvivesRestore() {
        val state = SettingsSnapshotJsonCodec.decode(
            persistedConfiguration(modelVisionFlag = false),
        )
        val model = state?.modelConfiguration
            ?.providers
            ?.single()
            ?.models
            ?.single()

        assertNotNull(model)
        assertFalse(ModelCapability.VISION in model.capabilities)
    }

    @Test
    fun explicitVisionFlagAddsCapabilityOnRestore() {
        val state = SettingsSnapshotJsonCodec.decode(
            persistedConfiguration(modelVisionFlag = true),
        )
        val model = state?.modelConfiguration
            ?.providers
            ?.single()
            ?.models
            ?.single()

        assertNotNull(model)
        assertTrue(ModelCapability.VISION in model.capabilities)
    }

    @Test
    fun stringAndObjectCapabilityMetadataRestores() {
        val state = SettingsSnapshotJsonCodec.decode(
            persistedConfiguration(
                capabilities = "CHAT,VISION",
                modalities = JSONObject().apply {
                    put("input", JSONArray().apply { put("text"); put("image") })
                },
            ),
        )
        val model = state?.modelConfiguration
            ?.providers
            ?.single()
            ?.models
            ?.single()

        assertNotNull(model)
        assertTrue(ModelCapability.CHAT in model.capabilities)
        assertTrue(ModelCapability.VISION in model.capabilities)
    }

    private fun persistedConfiguration(
        modelStreamingFlag: Boolean? = null,
        modelVisionFlag: Boolean? = null,
        capabilities: Any? = JSONArray().apply { put("CHAT") },
        modalities: Any? = null,
    ): String {
        return JSONObject().apply {
            put(
                "modelConfiguration",
                JSONObject().apply {
                    put("activeProviderId", "custom")
                    put(
                        "providers",
                        JSONArray().apply {
                            put(
                                JSONObject().apply {
                                    put("id", "custom")
                                    put("kind", "CUSTOM")
                                    put("displayName", "自定义")
                                    put("baseUrl", "https://example.com/v1")
                                    put("selectedModelId", "legacy-model")
                                    put(
                                        "models",
                                        JSONArray().apply {
                                            put(
                                                JSONObject().apply {
                                                    put("id", "legacy-model")
                                                    put("displayName", "Legacy Model")
                                                    put("capabilities", capabilities)
                                                    modalities?.let { put("modalities", it) }
                                                    modelStreamingFlag?.let {
                                                        put("streamingSupported", it)
                                                    }
                                                    modelVisionFlag?.let {
                                                        put("visionSupported", it)
                                                    }
                                                }
                                            )
                                        },
                                    )
                                }
                            )
                        },
                    )
                },
            )
        }.toString()
    }
}
