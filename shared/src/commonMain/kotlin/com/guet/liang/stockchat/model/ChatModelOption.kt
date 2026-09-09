package com.guet.liang.stockchat.model

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal data class ChatModelOption(
    val id: String,
    val displayName: String,
    val description: String,
    val badge: String,
    val multiplier: String,
    val capabilities: Set<ModelCapability> = setOf(ModelCapability.CHAT),
    val iconAsset: String = "stockchat_app_icon.png",
    val isLocked: Boolean = false,
)

internal const val DEFAULT_CHAT_MODEL_ICON_ASSET = "stockchat_app_icon.png"
