package com.guet.liang.stockchat.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class StockChatBackStackTest {
    @Test
    fun overlaysAreDismissedBeforeDrawerAndPage() {
        assertEquals(
            StockChatBackLayer.MODEL_MENU,
            StockChatBackStack.topLayer(
                StockChatBackState(
                    modelMenuOpen = true,
                    drawerOpen = true,
                    composerOpen = true,
                )
            ),
        )
        assertEquals(
            StockChatBackLayer.DRAWER,
            StockChatBackStack.topLayer(
                StockChatBackState(
                    drawerOpen = true,
                    composerOpen = true,
                )
            ),
        )
    }

    @Test
    fun pageIsLastBackStackLayer() {
        assertEquals(
            StockChatBackLayer.PAGE,
            StockChatBackStack.topLayer(StockChatBackState()),
        )
    }

    @Test
    fun modalPriorityIsDeterministic() {
        assertEquals(
            StockChatBackLayer.RENAME_DIALOG,
            StockChatBackStack.topLayer(
                StockChatBackState(
                    renameDialogOpen = true,
                    voiceRecording = true,
                    modelMenuOpen = true,
                    conversationMenuOpen = true,
                    messageMenuOpen = true,
                    imagePickerOpen = true,
                    drawerOpen = true,
                    composerOpen = true,
                )
            ),
        )
    }
}
