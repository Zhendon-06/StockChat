package com.guet.liang.stockchat.data

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.guet.liang.stockchat.database.StockChatDatabase

fun initializeStockChatDatabase() {
    val driver = NativeSqliteDriver(StockChatDatabase.Schema, DATABASE_NAME)
    driver.execute(null, "PRAGMA foreign_keys = ON", 0)
    ChatHistoryDatabase.initialize(StockChatDatabase(driver))
}

private const val DATABASE_NAME = "stockchat.db"
