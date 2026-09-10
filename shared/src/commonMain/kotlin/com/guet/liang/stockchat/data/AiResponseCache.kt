package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.AnswerBlock
import com.tencent.kuikly.core.datetime.DateTime

/** Small process-local LRU cache shared by every OpenAI-compatible provider. */
internal object AiResponseCache {
    private data class Entry(val blocks: List<AnswerBlock>, val expiresAt: Long)

    private val entries = LinkedHashMap<String, Entry>(CACHE_CAPACITY, 0.75f, true)
    private var hitCount = 0L
    private var missCount = 0L

    fun get(key: String, nowMillis: Long = currentTimeMillis()): List<AnswerBlock>? {
        val entry = entries[key]
        if (entry == null) {
            missCount++
            return null
        }
        if (entry.expiresAt <= nowMillis) {
            entries.remove(key)
            missCount++
            return null
        }
        hitCount++
        return entry.blocks
    }

    fun peek(key: String, nowMillis: Long = currentTimeMillis()): List<AnswerBlock>? {
        val entry = entries[key] ?: return null
        if (entry.expiresAt <= nowMillis) {
            entries.remove(key)
            return null
        }
        return entry.blocks
    }

    fun put(key: String, blocks: List<AnswerBlock>, nowMillis: Long = currentTimeMillis()) {
        entries[key] = Entry(blocks.toList(), nowMillis + TTL_MILLIS)
        while (entries.size > CACHE_CAPACITY) entries.entries.iterator().apply { next(); remove() }
    }

    fun clear() {
        entries.clear()
        hitCount = 0
        missCount = 0
    }

    val hits: Long get() = hitCount
    val misses: Long get() = missCount
    val size: Int get() = entries.size
    val stats: AiCacheStats get() = AiCacheStats(hitCount, missCount, entries.size)

    private fun currentTimeMillis(): Long = DateTime.currentTimestamp()

    private const val CACHE_CAPACITY = 64
    private const val TTL_MILLIS = 5 * 60 * 1000L
}

internal data class AiCacheStats(val hits: Long, val misses: Long, val entries: Int) {
    val totalRequests: Long get() = hits + misses
    val hitRate: Double get() = if (totalRequests == 0L) 0.0 else hits.toDouble() / totalRequests
}

/** Stable key for a request; credentials are deliberately excluded. */
internal fun aiResponseCacheKey(
    provider: AliyunApiConfig,
    model: String,
    question: String,
    history: List<com.guet.liang.stockchat.model.ChatHistoryItem>,
    images: List<String>,
): String {
    val material = buildString {
        append(provider.baseUrl.trim().trimEnd('/')).append('|')
        append(provider.providerDisplayName).append('|').append(model).append('|')
        history.forEach { append(it.role.name).append(':').append(it.content).append('\u0001') }
        append('|').append(question).append('|')
        images.forEach { append(it).append('\u0001') }
    }
    var hash = FNV_OFFSET
    material.encodeToByteArray().forEach { byte -> hash = (hash xor (byte.toLong() and 0xff)) * FNV_PRIME }
    return hash.toULong().toString(16)
}

private const val FNV_OFFSET = -3750763034362895579L
private const val FNV_PRIME = 1099511628211L
