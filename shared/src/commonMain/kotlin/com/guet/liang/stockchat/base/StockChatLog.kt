package com.guet.liang.stockchat.base

import com.tencent.kuikly.core.log.KLog

/**
 * Shared logging gateway. Keeping logging behind one small abstraction lets common code remain portable while platform adapters decide
 * where Kuikly logs are written.
 */
internal object StockChatLog {
    fun d(tag: String, message: String) {
        runCatching { KLog.i(tag, message) }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        val suffix = throwable?.let { " (${it::class.simpleName}: ${it.message.orEmpty()})" }.orEmpty()
        runCatching { KLog.e(tag, message + suffix) }
    }
}
