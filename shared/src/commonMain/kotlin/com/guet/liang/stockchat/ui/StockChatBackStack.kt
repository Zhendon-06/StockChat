package com.guet.liang.stockchat.ui

internal enum class StockChatBackLayer {
    RENAME_DIALOG,
    VOICE_RECORDING,
    MODEL_MENU,
    CONVERSATION_MENU,
    MESSAGE_MENU,
    IMAGE_PICKER,
    DRAWER,
    COMPOSER,
    PAGE,
}

internal data class StockChatBackState(
    val renameDialogOpen: Boolean = false,
    val voiceRecording: Boolean = false,
    val modelMenuOpen: Boolean = false,
    val conversationMenuOpen: Boolean = false,
    val messageMenuOpen: Boolean = false,
    val imagePickerOpen: Boolean = false,
    val drawerOpen: Boolean = false,
    val composerOpen: Boolean = false,
)

internal object StockChatBackStack {
    fun topLayer(state: StockChatBackState): StockChatBackLayer = when {
        state.renameDialogOpen -> StockChatBackLayer.RENAME_DIALOG
        state.voiceRecording -> StockChatBackLayer.VOICE_RECORDING
        state.modelMenuOpen -> StockChatBackLayer.MODEL_MENU
        state.conversationMenuOpen -> StockChatBackLayer.CONVERSATION_MENU
        state.messageMenuOpen -> StockChatBackLayer.MESSAGE_MENU
        state.imagePickerOpen -> StockChatBackLayer.IMAGE_PICKER
        state.drawerOpen -> StockChatBackLayer.DRAWER
        state.composerOpen -> StockChatBackLayer.COMPOSER
        else -> StockChatBackLayer.PAGE
    }
}
