package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.ModelCapability
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModelCatalogResponseParserTest {
    @Test
    fun parsesOpenAiModelsAndInfersMetadata() {
        val response = JSONObject().apply {
            put(
                "data",
                JSONArray().apply {
                    put(JSONObject().apply { put("id", "deepseek-r1:32k") })
                    put(JSONObject().apply { put("id", "qwen-vl-plus") })
                    put(JSONObject().apply { put("id", "gpt-4o"); put("name", "GPT 4 Omni") })
                    put(
                        JSONObject().apply {
                            put("id", "custom-model")
                            put("context_length", 32768)
                            put("capabilities", JSONArray().apply { put("reasoning") })
                        }
                    )
                    put(JSONObject().apply { put("id", "") })
                }
            )
        }

        val models = ModelCatalogResponseParser.parseModels(response)

        assertEquals(4, models.size)
        assertEquals("32K", models[0].contextWindowLabel)
        assertTrue(ModelCapability.REASONING in models[0].capabilities)
        assertTrue(ModelCapability.VISION in models[1].capabilities)
        assertTrue(ModelCapability.STREAMING in models[1].capabilities)
        assertEquals("GPT 4 Omni", models[2].displayName)
        assertEquals("128K", models[2].contextWindowLabel)
        assertEquals("32K", models[3].contextWindowLabel)
        assertTrue(ModelCapability.REASONING in models[3].capabilities)
        assertTrue(ModelCapability.STREAMING in models[3].capabilities)
    }

    @Test
    fun skipsInvalidAndDuplicateEntries() {
        val response = JSONObject().apply {
            put(
                "data",
                JSONArray().apply {
                    put(JSONObject().apply { put("id", "model-a") })
                    put(JSONObject().apply { put("id", "model-a") })
                    put(JSONObject().apply { put("object", "model") })
                }
            )
        }

        val models = ModelCatalogResponseParser.parse(response)

        assertEquals(listOf("model-a"), models.map { it.id })
        assertEquals("未知", models.single().contextWindowLabel)
    }

    @Test
    fun acceptsModelsAliasAndExtendedMetadataFields() {
        val response = JSONObject().apply {
            put(
                "models",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("id", "provider-model")
                            put("display_name", "Provider Model")
                            put("max_model_len", 131072)
                            put("modalities", JSONArray().apply { put("text"); put("image") })
                        }
                    )
                }
            )
        }

        val model = ModelCatalogResponseParser.parse(response).single()

        assertEquals("Provider Model", model.displayName)
        assertEquals("128K", model.contextWindowLabel)
        assertTrue(ModelCapability.VISION in model.capabilities)
        assertTrue(ModelCapability.STREAMING in model.capabilities)
    }

    @Test
    fun honorsExplicitStreamingSupportMetadata() {
        val response = JSONObject().apply {
            put(
                "data",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("id", "completion-only")
                            put("supports_streaming", false)
                        }
                    )
                    put(
                        JSONObject().apply {
                            put("id", "streaming-model")
                            put("supportsStreaming", "true")
                        }
                    )
                },
            )
        }

        val models = ModelCatalogResponseParser.parse(response)

        assertTrue(ModelCapability.STREAMING !in models[0].capabilities)
        assertTrue(ModelCapability.STREAMING in models[1].capabilities)
    }

    @Test
    fun honorsExplicitVisionSupportMetadata() {
        val response = JSONObject().apply {
            put(
                "data",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("id", "qwen-vl-plus")
                            put("supports_vision", false)
                        }
                    )
                    put(
                        JSONObject().apply {
                            put("id", "custom-chat")
                            put("visionSupported", "true")
                        }
                    )
                },
            )
        }

        val models = ModelCatalogResponseParser.parse(response)

        assertTrue(ModelCapability.VISION !in models[0].capabilities)
        assertTrue(ModelCapability.VISION in models[1].capabilities)
    }

    @Test
    fun parsesStringAndObjectModalities() {
        val response = JSONObject().apply {
            put(
                "data",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("id", "string-modalities")
                            put("modalities", "text,image")
                        }
                    )
                    put(
                        JSONObject().apply {
                            put("id", "object-modalities")
                            put(
                                "modalities",
                                JSONObject().apply {
                                    put("input", JSONArray().apply { put("text"); put("image") })
                                    put("output", JSONArray().apply { put("text") })
                                },
                            )
                        }
                    )
                    put(
                        JSONObject().apply {
                            put("id", "object-negative")
                            put(
                                "modalities",
                                JSONObject().apply {
                                    put("vision", false)
                                    put("image", true)
                                },
                            )
                        }
                    )
                },
            )
        }

        val models = ModelCatalogResponseParser.parse(response)

        assertTrue(ModelCapability.VISION in models[0].capabilities)
        assertTrue(ModelCapability.VISION in models[1].capabilities)
        assertTrue(ModelCapability.VISION !in models[2].capabilities)
    }

    @Test
    fun sanitizesCredentialDetailsFromProviderErrors() {
        val apiKey = "sk-demo-secret-123"

        val exactKeyMessage = sanitizeModelCatalogError(
            "request failed with key=$apiKey",
            apiKey,
        )
        val partialKeyMessage = sanitizeModelCatalogError(
            "invalid api key *****t123",
            apiKey,
        )
        val bareKeyMessage = sanitizeModelCatalogError(
            "invalid key *****t123",
            apiKey,
        )
        val bearerMessage = sanitizeModelCatalogError(
            "Authorization: Bearer $apiKey",
            apiKey,
        )

        assertTrue(apiKey !in exactKeyMessage)
        assertTrue("t123" !in partialKeyMessage)
        assertTrue("t123" !in bareKeyMessage)
        assertTrue(apiKey !in bearerMessage)
        assertEquals("模型列表请求失败，请检查 API Key 和服务地址。", partialKeyMessage)
    }
}
