package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.model.TodayMarketResult
import com.guet.liang.stockchat.model.TodayMarketUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StockChatHomeFlowTest {
    @Test
    fun initialStateShowsChatWelcomeAndCenterCapsule() {
        val state = StockChatHomeFlow().state.value

        assertEquals(StockChatHomeDestination.AI_CHAT, state.destination)
        assertEquals(StockChatHomeChatStage.WELCOME, state.chatStage)
        assertEquals(
            StockChatHomeCapsulePresentation.CHAT_CENTER,
            state.capsulePresentation,
        )
    }

    @Test
    fun startLoadsTodayMarketOnlyOnce() {
        val flow = StockChatHomeFlow()

        val firstEffects = flow.dispatch(StockChatHomeEvent.Started)
        val secondEffects = flow.dispatch(StockChatHomeEvent.Started)
        val tabEffects = flow.dispatch(
            StockChatHomeEvent.DestinationSelected(
                StockChatHomeDestination.TODAY_MARKET
            )
        )

        assertEquals(
            listOf(StockChatHomeEffect.LoadTodayMarket(requestId = 1)),
            firstEffects,
        )
        assertTrue(secondEffects.isEmpty())
        assertEquals(
            listOf(
                StockChatHomeEffect.DismissChatUi,
                StockChatHomeEffect.CloseDrawer,
            ),
            tabEffects,
        )
        assertTrue(flow.state.value.todayMarketRequestInFlight)
        assertEquals(1, flow.state.value.todayMarketRequestId)
    }

    @Test
    fun allQuestionEntrancesCommitTheSameConversationState() {
        val composerState = committedState(StockChatQuestionSource.COMPOSER)
        val suggestionState = committedState(StockChatQuestionSource.WELCOME_SUGGESTION)
        val drawerState = committedState(
            StockChatQuestionSource.DRAWER_SHORTCUT,
            startFromTodayMarket = true,
        )

        assertEquals(composerState, suggestionState)
        assertEquals(composerState, drawerState)
        assertEquals(StockChatHomeChatStage.CONVERSATION, composerState.chatStage)
        assertEquals(
            StockChatHomeCapsulePresentation.HIDDEN,
            composerState.capsulePresentation,
        )
    }

    @Test
    fun pageLocalQuestionCannotCommitFromTodayMarket() {
        val flow = startedFlow()
        flow.dispatch(
            StockChatHomeEvent.DestinationSelected(
                StockChatHomeDestination.TODAY_MARKET
            )
        )
        val before = flow.state.value

        val effects = flow.dispatch(
            StockChatHomeEvent.QuestionCommitted(StockChatQuestionSource.COMPOSER)
        )

        assertEquals(before, flow.state.value)
        assertTrue(effects.isEmpty())
        assertEquals(
            StockChatHomeCapsulePresentation.MARKET_BOTTOM,
            flow.state.value.capsulePresentation,
        )
    }

    @Test
    fun chatAndTodayMarketKeepIndependentPresentationState() {
        val flow = startedFlow()
        flow.dispatch(
            StockChatHomeEvent.QuestionCommitted(StockChatQuestionSource.COMPOSER)
        )

        flow.dispatch(
            StockChatHomeEvent.DestinationSelected(
                StockChatHomeDestination.TODAY_MARKET
            )
        )
        assertEquals(
            StockChatHomeCapsulePresentation.MARKET_BOTTOM,
            flow.state.value.capsulePresentation,
        )

        flow.dispatch(
            StockChatHomeEvent.DestinationSelected(
                StockChatHomeDestination.AI_CHAT
            )
        )
        assertEquals(StockChatHomeChatStage.CONVERSATION, flow.state.value.chatStage)
        assertEquals(
            StockChatHomeCapsulePresentation.HIDDEN,
            flow.state.value.capsulePresentation,
        )
    }

    @Test
    fun welcomeObscuringAndNewConversationUseDerivedCapsuleState() {
        val flow = startedFlow()

        flow.dispatch(StockChatHomeEvent.WelcomeObscuredChanged(obscured = true))
        assertEquals(
            StockChatHomeCapsulePresentation.HIDDEN,
            flow.state.value.capsulePresentation,
        )

        flow.dispatch(StockChatHomeEvent.WelcomeObscuredChanged(obscured = false))
        flow.dispatch(
            StockChatHomeEvent.QuestionCommitted(StockChatQuestionSource.COMPOSER)
        )
        flow.dispatch(StockChatHomeEvent.NewConversationStarted)

        assertEquals(StockChatHomeDestination.AI_CHAT, flow.state.value.destination)
        assertEquals(StockChatHomeChatStage.WELCOME, flow.state.value.chatStage)
        assertEquals(
            StockChatHomeCapsulePresentation.CHAT_CENTER,
            flow.state.value.capsulePresentation,
        )
    }

    @Test
    fun staleTodayMarketResponsesAndDuplicateRetryAreIgnored() {
        val flow = startedFlow()

        flow.dispatch(
            StockChatHomeEvent.TodayMarketLoadCompleted(
                requestId = 99,
                result = TodayMarketResult.Failure("stale"),
            )
        )
        assertIs<TodayMarketUiState.Loading>(flow.state.value.todayMarketState)

        flow.dispatch(
            StockChatHomeEvent.TodayMarketLoadCompleted(
                requestId = 1,
                result = TodayMarketResult.Failure("暂时不可用"),
            )
        )
        assertEquals(
            TodayMarketUiState.Error("暂时不可用"),
            flow.state.value.todayMarketState,
        )
        assertFalse(flow.state.value.todayMarketRequestInFlight)

        val retryEffects = flow.dispatch(StockChatHomeEvent.TodayMarketRetryRequested)
        val duplicateEffects = flow.dispatch(StockChatHomeEvent.TodayMarketRetryRequested)
        assertEquals(
            listOf(StockChatHomeEffect.LoadTodayMarket(requestId = 2)),
            retryEffects,
        )
        assertTrue(duplicateEffects.isEmpty())

        flow.dispatch(
            StockChatHomeEvent.TodayMarketLoadCompleted(
                requestId = 1,
                result = TodayMarketResult.Empty,
            )
        )
        assertIs<TodayMarketUiState.Loading>(flow.state.value.todayMarketState)
        assertEquals(2, flow.state.value.todayMarketRequestId)

        flow.dispatch(
            StockChatHomeEvent.TodayMarketLoadCompleted(
                requestId = 2,
                result = TodayMarketResult.Empty,
            )
        )
        assertIs<TodayMarketUiState.Empty>(flow.state.value.todayMarketState)
        assertFalse(flow.state.value.todayMarketRequestInFlight)
    }

    @Test
    fun stoppedFlowRejectsLateTodayMarketResponse() {
        val flow = startedFlow()
        flow.dispatch(StockChatHomeEvent.Stopped)

        flow.dispatch(
            StockChatHomeEvent.TodayMarketLoadCompleted(
                requestId = 1,
                result = TodayMarketResult.Empty,
            )
        )

        assertFalse(flow.state.value.started)
        assertFalse(flow.state.value.todayMarketRequestInFlight)
        assertIs<TodayMarketUiState.Loading>(flow.state.value.todayMarketState)
    }

    private fun committedState(
        source: StockChatQuestionSource,
        startFromTodayMarket: Boolean = false,
    ): StockChatHomeState {
        val flow = startedFlow()
        if (startFromTodayMarket) {
            flow.dispatch(
                StockChatHomeEvent.DestinationSelected(
                    StockChatHomeDestination.TODAY_MARKET
                )
            )
        }
        flow.dispatch(StockChatHomeEvent.QuestionCommitted(source))
        return flow.state.value
    }

    private fun startedFlow(): StockChatHomeFlow = StockChatHomeFlow().also {
        it.dispatch(StockChatHomeEvent.Started)
    }
}
