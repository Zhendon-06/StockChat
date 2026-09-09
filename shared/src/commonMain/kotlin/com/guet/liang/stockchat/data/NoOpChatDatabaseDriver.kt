package com.guet.liang.stockchat.data

import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement

/**
 * Fallback used by the Kuikly OHOS container, which does not ship a
 * SQLDelight-native driver yet. It keeps the demo usable with an empty,
 * non-persistent history until a platform storage adapter is supplied.
 */
internal class NoOpChatDatabaseDriver : SqlDriver {
    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<R> {
        val hasGeneratedId = sql.contains("last_insert_rowid", ignoreCase = true) ||
            sql.contains("SELECT id FROM conversation_", ignoreCase = true)
        return mapper(NoOpCursor(hasGeneratedId))
    }

    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<Long> = QueryResult.Value(0L)

    override fun newTransaction(): QueryResult<Transacter.Transaction> =
        QueryResult.Value(NoOpTransaction())

    override fun currentTransaction(): Transacter.Transaction? = null

    override fun addListener(vararg queryKeys: String, listener: Query.Listener) = Unit

    override fun removeListener(vararg queryKeys: String, listener: Query.Listener) = Unit

    override fun notifyListeners(vararg queryKeys: String) = Unit

    override fun close() = Unit
}

private class NoOpCursor(
    private val hasRow: Boolean,
) : SqlCursor {
    private var consumed = false

    override fun next(): QueryResult<Boolean> {
        if (consumed || !hasRow) return QueryResult.Value(false)
        consumed = true
        return QueryResult.Value(true)
    }

    override fun getString(index: Int): String? = ""

    override fun getLong(index: Int): Long? = 0L

    override fun getBytes(index: Int): ByteArray? = null

    override fun getDouble(index: Int): Double? = 0.0

    override fun getBoolean(index: Int): Boolean? = false
}

private class NoOpTransaction : Transacter.Transaction() {
    override val enclosingTransaction: Transacter.Transaction?
        get() = null

    override fun endTransaction(successful: Boolean): QueryResult<Unit> = QueryResult.Unit
}
