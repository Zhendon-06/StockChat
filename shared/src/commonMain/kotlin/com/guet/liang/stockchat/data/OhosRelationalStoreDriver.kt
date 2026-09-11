package com.guet.liang.stockchat.data

import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import com.guet.liang.stockchat.base.StockChatDatabaseModule
import com.guet.liang.stockchat.database.StockChatDatabase
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * SQLDelight driver for the Kuikly OHOS container. Every statement is executed
 * synchronously by the host's relationalStore through [StockChatDatabaseModule],
 * so the generated queries behave exactly like the Android / iOS SQLite drivers.
 */
internal class OhosRelationalStoreDriver private constructor(
    private val module: StockChatDatabaseModule,
) : SqlDriver {
    private var transaction: BridgeTransaction? = null

    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<R> {
        val rows = module.query(sql, collectArguments(parameters, binders))
        return mapper(RowCursor(rows))
    }

    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<Long> = QueryResult.Value(module.execute(sql, collectArguments(parameters, binders)))

    override fun newTransaction(): QueryResult<Transacter.Transaction> {
        val enclosing = transaction
        val created = BridgeTransaction(enclosing)
        transaction = created
        if (enclosing == null) {
            module.beginTransaction()
        }
        return QueryResult.Value(created)
    }

    override fun currentTransaction(): Transacter.Transaction? = transaction

    override fun addListener(vararg queryKeys: String, listener: Query.Listener) = Unit

    override fun removeListener(vararg queryKeys: String, listener: Query.Listener) = Unit

    override fun notifyListeners(vararg queryKeys: String) = Unit

    override fun close() = Unit

    private fun collectArguments(parameters: Int, binders: (SqlPreparedStatement.() -> Unit)?): JSONArray {
        val statement = ArgumentStatement(parameters)
        binders?.invoke(statement)
        return statement.toJson()
    }

    private inner class BridgeTransaction(
        override val enclosingTransaction: BridgeTransaction?,
    ) : Transacter.Transaction() {
        private var childFailed = false

        override fun endTransaction(successful: Boolean): QueryResult<Unit> {
            val outcome = successful && !childFailed
            val enclosing = enclosingTransaction
            if (enclosing == null) {
                if (outcome) module.commit() else module.rollback()
            } else if (!outcome) {
                enclosing.childFailed = true
            }
            transaction = enclosing
            return QueryResult.Unit
        }
    }

    /** Collects bound values by 0-based index into the bridge's cell encoding. */
    private class ArgumentStatement(size: Int) : SqlPreparedStatement {
        private val cells = arrayOfNulls<JSONObject>(size.coerceAtLeast(0))

        override fun bindBytes(index: Int, bytes: ByteArray?) = set(index, null)

        override fun bindLong(index: Int, long: Long?) = set(index, long?.let { JSONObject().put("n", it) })

        override fun bindDouble(index: Int, double: Double?) = set(index, double?.let { JSONObject().put("n", it) })

        override fun bindString(index: Int, string: String?) = set(index, string?.let { JSONObject().put("s", it) })

        override fun bindBoolean(index: Int, boolean: Boolean?) =
            set(index, boolean?.let { JSONObject().put("n", if (it) 1L else 0L) })

        private fun set(index: Int, cell: JSONObject?) {
            if (index in cells.indices) {
                cells[index] = cell
            }
        }

        fun toJson(): JSONArray = JSONArray().apply { cells.forEach { put(it ?: JSONObject()) } }
    }

    private class RowCursor(private val rows: JSONArray) : SqlCursor {
        private var position = -1

        override fun next(): QueryResult<Boolean> {
            position += 1
            return QueryResult.Value(position < rows.length())
        }

        private fun cell(index: Int): JSONObject? = rows.optJSONArray(position)?.optJSONObject(index)

        override fun getString(index: Int): String? {
            val cell = cell(index) ?: return null
            return when (val text = cell.opt("s")) {
                is String -> text
                else -> cell.opt("n")?.toString()
            }
        }

        override fun getLong(index: Int): Long? {
            val cell = cell(index) ?: return null
            return when (val number = cell.opt("n")) {
                is Number -> number.toLong()
                else -> (cell.opt("s") as? String)?.toLongOrNull()
            }
        }

        override fun getDouble(index: Int): Double? {
            val cell = cell(index) ?: return null
            return when (val number = cell.opt("n")) {
                is Number -> number.toDouble()
                else -> (cell.opt("s") as? String)?.toDoubleOrNull()
            }
        }

        override fun getBoolean(index: Int): Boolean? = getLong(index)?.let { it != 0L }

        override fun getBytes(index: Int): ByteArray? = null
    }

    companion object {
        private const val SCHEMA_TABLE = "stockchat_schema"

        /** Returns null when the host has no relational store available for this page. */
        fun open(module: StockChatDatabaseModule): OhosRelationalStoreDriver? {
            if (!module.ping()) {
                return null
            }
            val driver = OhosRelationalStoreDriver(module)
            driver.execute(
                null,
                "CREATE TABLE IF NOT EXISTS $SCHEMA_TABLE (key TEXT NOT NULL PRIMARY KEY, value INTEGER NOT NULL)",
                0,
            )
            val currentVersion = driver.executeQuery(
                null,
                "SELECT value FROM $SCHEMA_TABLE WHERE key = 'version'",
                { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null) },
                0,
            ).value ?: 0L
            val targetVersion = StockChatDatabase.Schema.version
            when {
                currentVersion == 0L -> StockChatDatabase.Schema.create(driver).value
                currentVersion < targetVersion -> StockChatDatabase.Schema.migrate(driver, currentVersion, targetVersion).value
            }
            if (currentVersion != targetVersion) {
                driver.execute(null, "INSERT OR REPLACE INTO $SCHEMA_TABLE (key, value) VALUES ('version', ?)", 1) {
                    bindLong(0, targetVersion)
                }
            }
            // relationalStore 可能拒绝 PRAGMA；仓库层已手动级联删除，失败可忽略
            runCatching { driver.execute(null, "PRAGMA foreign_keys = ON", 0) }
            return driver
        }
    }
}
