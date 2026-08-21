package com.guet.liang.stockchat.data

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.guet.liang.stockchat.database.StockChatDatabase

fun initializeStockChatDatabase(context: Context) {
    val driver = AndroidSqliteDriver(
        schema = StockChatDatabase.Schema,
        context = context,
        name = DATABASE_NAME,
    )
    driver.execute(null, "PRAGMA foreign_keys = ON", 0)
    ChatHistoryDatabase.initialize(StockChatDatabase(driver))
}

private const val DATABASE_NAME = "stockchat.db"
