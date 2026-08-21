package com.guet.liang.stockchat

import android.app.Application
import com.guet.liang.stockchat.data.initializeStockChatDatabase

class KRApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initializeStockChatDatabase(this)
    }

    init {
        application = this
    }

    companion object {
        lateinit var application: Application
    }
}
