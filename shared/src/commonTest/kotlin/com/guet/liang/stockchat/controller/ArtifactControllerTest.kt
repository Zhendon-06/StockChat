@file:Suppress("MagicNumber", "LongParameterList", "MaxLineLength")
package com.guet.liang.stockchat.controller

import com.guet.liang.stockchat.model.ChatMessage
import com.guet.liang.stockchat.model.ChatRole
import com.guet.liang.stockchat.model.ChatSessionSummary
import com.guet.liang.stockchat.model.ConversationMindMapArtifact
import com.guet.liang.stockchat.model.ConversationMindMapArtifactSnapshot
import com.guet.liang.stockchat.model.ConversationMindMapArtifactSummary
import com.guet.liang.stockchat.model.ConversationMindMapBranch
import com.guet.liang.stockchat.model.ConversationStockComparisonRefreshResult
import com.guet.liang.stockchat.model.ConversationStockComparisonRow
import com.guet.liang.stockchat.model.ConversationStockComparisonSnapshot
import com.guet.liang.stockchat.model.ConversationTableArtifact
import com.guet.liang.stockchat.model.ConversationTableArtifactSnapshot
import com.guet.liang.stockchat.model.ConversationTableArtifactSummary
import com.guet.liang.stockchat.model.ConversationTableRowStatus
import com.guet.liang.stockchat.model.MermaidMindMapNode
import com.guet.liang.stockchat.model.StockQuote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ArtifactControllerTest {
    @Test
    fun creatingTablePersistsTheSessionBeforeSavingTheArtifact() {
        val fixture = Fixture()
        val result = fixture.controller.createTable("session", "比较", messages())
        assertEquals(7L, assertIs<ArtifactResult.Success<Long>>(result).value)
        assertEquals(listOf("persist", "saveTable"), fixture.events)
    }

    @Test
    fun noUserMessageDoesNotPersistOrGenerateArtifacts() {
        val fixture = Fixture()
        assertIs<ArtifactResult.Failure>(fixture.controller.createTable("session", "比较", emptyList()))
        assertIs<ArtifactResult.Failure>(fixture.controller.createMindMap("session", "脑图", emptyList()))
        assertTrue(fixture.events.isEmpty())
    }

    @Test
    fun tableWithoutSecuritiesReportsTheExistingFeedback() {
        val fixture = Fixture()
        fixture.generator.snapshot = fixture.generator.snapshot.copy(rows = emptyList())
        val result = fixture.controller.createTable("session", "比较", messages())
        assertEquals("当前会话未识别到股票或指数", assertIs<ArtifactResult.Failure>(result).message)
        assertTrue(fixture.events.isEmpty())
    }

    @Test
    fun mindMapCreationPersistsBeforeSaving() {
        val fixture = Fixture()
        assertIs<ArtifactResult.Success<Long>>(fixture.controller.createMindMap("session", "脑图", messages()))
        assertEquals(listOf("persist", "saveMindMap"), fixture.events)
    }

    @Test
    fun invalidAndMissingArtifactIdsHaveDistinctFeedback() {
        val fixture = Fixture()
        assertIs<ArtifactResult.Failure>(fixture.controller.loadMindMap("invalid"))
        assertEquals(null, assertIs<ArtifactResult.Success<ConversationMindMapArtifact?>>(fixture.controller.loadMindMap("1")).value)
        fixture.controller.loadComparison("0")
        assertIs<ComparisonDetailUiState.Error>(fixture.states.last())
        fixture.tables.artifact = null
        fixture.controller.loadComparison("1")
        assertIs<ComparisonDetailUiState.NotFound>(fixture.states.last())
    }

    @Test
    fun canceledRefreshCannotPublishAfterPageDestruction() {
        val fixture = Fixture()
        fixture.controller.loadComparison("7")
        assertIs<ComparisonDetailUiState.Refreshing>(fixture.states.last())
        val count = fixture.states.size
        fixture.controller.cancelRefresh()
        fixture.completeRefresh()
        assertEquals(count, fixture.states.size)
    }

    @Test
    fun emptyComparisonClearsThePreviousRefreshSnapshot() {
        val fixture = Fixture()
        fixture.controller.loadComparison("7")
        fixture.tables.artifact = null
        fixture.controller.loadComparison("8")
        fixture.controller.refreshComparison()
        fixture.completeRefresh()
        assertIs<ComparisonDetailUiState.NotFound>(fixture.states.last())
    }

    @Test
    fun completedMarketRefreshPublishesCurrentPrices() {
        val fixture = Fixture()
        fixture.controller.loadComparison("7")
        fixture.completeRefresh()
        val content = assertIs<ComparisonDetailUiState.Content>(fixture.states.last()).content
        assertEquals(ComparisonRefreshPhase.CURRENT, content.refreshPhase)
        assertEquals(1, content.refreshedCount)
    }

    @Test
    fun malformedMermaidFallsBackToStoredBranches() {
        val fixture = Fixture()
        val artifact =
            ConversationMindMapArtifact(
                1,
                "session",
                "脑图",
                2,
                "broken",
                0,
                0,
                listOf(ConversationMindMapBranch(1, "问题", "摘要", "未识别", ConversationTableRowStatus.FAILED)),
            )
        val tree = fixture.controller.mindMapTree(artifact)
        assertEquals("脑图", tree.label)
        assertEquals("1. 问题", tree.children.single().label)
        assertEquals(listOf("洞察：摘要", "状态：生成失败"), tree.children.single().children.map { it.label })
    }

    private class Fixture {
        val events = mutableListOf<String>()
        val states = mutableListOf<ComparisonDetailUiState>()
        val tables = Tables(events)
        val generator = Generator()
        private var refreshCallback: ((ConversationStockComparisonRefreshResult) -> Unit)? = null
        val controller =
            ArtifactController(
                Sessions(events),
                tables,
                MindMaps(events),
                generator,
                ArtifactMarketRepository { _, callback -> refreshCallback = callback },
                loadFavorites = { emptyList() },
                onComparisonChanged = states::add,
            )

        fun completeRefresh() {
            checkNotNull(refreshCallback)(ConversationStockComparisonRefreshResult(generator.snapshot, 1, 1, 0, emptyList(), "已更新"))
        }
    }

    private class Sessions(private val events: MutableList<String>) : ChatSessionRepository {
        override fun loadSessions(): List<ChatSessionSummary> = emptyList()

        override fun loadArchivedSessions(): List<ChatSessionSummary> = emptyList()

        override fun loadMessages(sessionId: String): List<ChatMessage> = messages()

        override fun replaceMessages(sessionId: String, messages: List<ChatMessage>) {
            events += "persist"
        }

        override fun clearSession(sessionId: String) = Unit

        override fun archiveSession(sessionId: String): Boolean = false

        override fun renameSession(sessionId: String, title: String) = Unit
    }

    private class Tables(private val events: MutableList<String>) : TableArtifactStore {
        var artifact: ConversationTableArtifact? = ConversationTableArtifact(7, "session", "比较", 1, 0, 0, emptyList())

        override fun upsert(sessionId: String, snapshot: ConversationTableArtifactSnapshot): Long {
            events += "saveTable"
            return 7
        }

        override fun load(artifactId: Long): ConversationTableArtifact? = artifact

        override fun listAll(): List<ConversationTableArtifactSummary> = emptyList()
    }

    private class MindMaps(private val events: MutableList<String>) : MindMapArtifactStore {
        override fun upsert(sessionId: String, snapshot: ConversationMindMapArtifactSnapshot): Long {
            events += "saveMindMap"
            return 8
        }

        override fun load(artifactId: Long): ConversationMindMapArtifact? = null

        override fun listAll(): List<ConversationMindMapArtifactSummary> = emptyList()
    }

    private class Generator : ArtifactGenerator {
        var snapshot = ConversationStockComparisonSnapshot("比较", 1, listOf(ConversationStockComparisonRow("sh600000", "浦发银行", "600000")))

        override fun comparison(title: String, messages: List<ChatMessage>) = snapshot

        override fun tableSnapshot(comparison: ConversationStockComparisonSnapshot) =
            ConversationTableArtifactSnapshot(comparison.title, comparison.sourceMessageCount, emptyList())

        override fun mindMap(title: String, messages: List<ChatMessage>) =
            ConversationMindMapArtifactSnapshot(title, messages.size, "mindmap", emptyList())

        override fun parseMindMap(source: String): MermaidMindMapNode? = null

        override fun providerSymbol(quote: StockQuote): String? = null
    }

    private companion object {
        fun messages() = listOf(ChatMessage("question", ChatRole.USER, emptyList()))
    }
}
