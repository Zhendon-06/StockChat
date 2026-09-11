package com.guet.liang.stockchat.base

import com.guet.liang.stockchat.data.ChatHistoryDatabase
import com.guet.liang.stockchat.data.FavoriteCardsStore
import com.guet.liang.stockchat.data.StockChatSettingsStore
import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.module.SharedPreferencesModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.pager.Pager
import com.tencent.kuikly.core.reactive.handler.observable

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal abstract class BasePager : Pager() {
    private var nightModel by observable(false)
    private var nightModelInitialized = false

    override fun createExternalModules(): Map<String, Module>? {
        val externalModules = hashMapOf<String, Module>()
        externalModules[BridgeModule.MODULE_NAME] = BridgeModule()
        externalModules[ShareModule.MODULE_NAME] = ShareModule()
        externalModules[StockChatDatabaseModule.MODULE_NAME] = StockChatDatabaseModule()
        return externalModules
    }

    override fun created() {
        super.created()
        if (pageData.isOhOs) {
            ChatHistoryDatabase.initializeOhos(acquireModule(StockChatDatabaseModule.MODULE_NAME))
        }
        val sharedPreferencesModule = acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)
        StockChatSettingsStore.initialize(sharedPreferencesModule)
        FavoriteCardsStore.initialize(sharedPreferencesModule)
        isNightMode()
    }

    override fun themeDidChanged(data: JSONObject) {
        super.themeDidChanged(data)
        nightModel = data.optBoolean(IS_NIGHT_MODE_KEY)
        nightModelInitialized = true
    }

    // 是否为夜间模式
    override fun isNightMode(): Boolean {
        if (!nightModelInitialized) {
            nightModel = pageData.params.optBoolean(IS_NIGHT_MODE_KEY)
            nightModelInitialized = true
        }
        return nightModel
    }

    // 不开启调试UI模式
    override fun debugUIInspector(): Boolean {
        return false
    }

    companion object {
        const val IS_NIGHT_MODE_KEY = "isNightMode"
    }
}
