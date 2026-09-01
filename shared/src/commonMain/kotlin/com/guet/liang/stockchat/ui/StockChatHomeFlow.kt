package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.model.TodayMarketResult
import com.guet.liang.stockchat.model.TodayMarketUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class StockChatHomeDestination {
    AI_CHAT,
    TODAY_MARKET,
}

internal enum class StockChatHomeChatStage {
    WELCOME,
    CONVERSATION,
}

internal enum class StockChatHomeCapsulePresentation {
    CHAT_CENTER,
    HIDDEN,
    MARKET_BOTTOM,
}

internal enum class StockChatQuestionSource(
    val canOpenChat: Boolean,
) {
    COMPOSER(canOpenChat = false),
    WELCOME_SUGGESTION(canOpenChat = false),
    DRAWER_SHORTCUT(canOpenChat = true),
}

internal data class StockChatHomeState(
    val destination: StockChatHomeDestination = StockChatHomeDestination.AI_CHAT,
    val chatStage: StockChatHomeChatStage = StockChatHomeChatStage.WELCOME,
    val welcomeObscured: Boolean = false,
    val todayMarketState: TodayMarketUiState = TodayMarketUiState.Loading,
    val todayMarketRequestId: Int = 0,
    val todayMarketRequestInFlight: Boolean = false,
    val started: Boolean = false,
) {
    val capsulePresentation: StockChatHomeCapsulePresentation
        get() = when {
            destination == StockChatHomeDestination.TODAY_MARKET ->
                StockChatHomeCapsulePresentation.MARKET_BOTTOM
            chatStage == StockChatHomeChatStage.WELCOME && !welcomeObscured ->
                StockChatHomeCapsulePresentation.CHAT_CENTER
            else -> StockChatHomeCapsulePresentation.HIDDEN
        }

    fun canSubmitQuestion(source: StockChatQuestionSource): Boolean =
        started && (
            destination == StockChatHomeDestination.AI_CHAT || source.canOpenChat
        )
}

internal sealed interface StockChatHomeEvent {
    data object Started : StockChatHomeEvent

    data object Stopped : StockChatHomeEvent

    data class DestinationSelected(
        val destination: StockChatHomeDestination,
    ) : StockChatHomeEvent

    data class WelcomeObscuredChanged(
        val obscured: Boolean,
    ) : StockChatHomeEvent

    data class QuestionCommitted(
        val source: StockChatQuestionSource,
    ) : StockChatHomeEvent

    data class ConversationSynchronized(
        val hasMessages: Boolean,
    ) : StockChatHomeEvent

    data object NewConversationStarted : StockChatHomeEvent

    data class ConversationOpened(
        val hasMessages: Boolean,
    ) : StockChatHomeEvent

    data object TodayMarketRetryRequested : StockChatHomeEvent

    data class TodayMarketLoadCompleted(
        val requestId: Int,
        val result: TodayMarketResult,
    ) : StockChatHomeEvent
}

internal sealed interface StockChatHomeEffect {
    data object DismissChatUi : StockChatHomeEffect

    data object CloseDrawer : StockChatHomeEffect

    data class LoadTodayMarket(
        val requestId: Int,
    ) : StockChatHomeEffect
}

internal data class StockChatHomeTransition(
    val state: StockChatHomeState,
    val effects: List<StockChatHomeEffect> = emptyList(),
)

internal class StockChatHomeFlow(
    initialState: StockChatHomeState = StockChatHomeState(),
) {
    private val mutableState = MutableStateFlow(initialState)

    val state: StateFlow<StockChatHomeState> = mutableState.asStateFlow()

    fun dispatch(event: StockChatHomeEvent): List<StockChatHomeEffect> {
        val transition = reduce(mutableState.value, event)
        mutableState.value = transition.state
        return transition.effects
    }

    internal companion object {
        fun reduce(
            state: StockChatHomeState,
            event: StockChatHomeEvent,
        ): StockChatHomeTransition = when (event) {
            StockChatHomeEvent.Started -> {
                if (state.started) {
                    StockChatHomeTransition(state)
                } else {
                    beginTodayMarketLoad(state.copy(started = true))
                }
            }

            StockChatHomeEvent.Stopped -> StockChatHomeTransition(
                state.copy(
                    started = false,
                    todayMarketRequestInFlight = false,
                )
            )

            is StockChatHomeEvent.DestinationSelected -> {
                selectDestination(state, event.destination)
            }

            is StockChatHomeEvent.WelcomeObscuredChanged -> StockChatHomeTransition(
                state.copy(welcomeObscured = event.obscured)
            )

            is StockChatHomeEvent.QuestionCommitted -> commitQuestion(state, event.source)

            is StockChatHomeEvent.ConversationSynchronized -> StockChatHomeTransition(
                state.copy(chatStage = event.hasMessages.toChatStage())
            )

            StockChatHomeEvent.NewConversationStarted -> StockChatHomeTransition(
                state.copy(
                    destination = StockChatHomeDestination.AI_CHAT,
                    chatStage = StockChatHomeChatStage.WELCOME,
                ),
                effects = listOf(StockChatHomeEffect.CloseDrawer),
            )

            is StockChatHomeEvent.ConversationOpened -> StockChatHomeTransition(
                state.copy(
                    destination = StockChatHomeDestination.AI_CHAT,
                    chatStage = event.hasMessages.toChatStage(),
                ),
                effects = listOf(StockChatHomeEffect.CloseDrawer),
            )

            StockChatHomeEvent.TodayMarketRetryRequested -> {
                if (!state.started || state.todayMarketRequestInFlight) {
                    StockChatHomeTransition(state)
                } else {
                    beginTodayMarketLoad(state)
                }
            }

            is StockChatHomeEvent.TodayMarketLoadCompleted -> {
                completeTodayMarketLoad(state, event)
            }
        }

        private fun selectDestination(
            state: StockChatHomeState,
            destination: StockChatHomeDestination,
        ): StockChatHomeTransition {
            if (destination == state.destination) {
                return if (
                    destination == StockChatHomeDestination.TODAY_MARKET &&
                    state.todayMarketState is TodayMarketUiState.Loading &&
                    !state.todayMarketRequestInFlight &&
                    state.started
                ) {
                    beginTodayMarketLoad(state)
                } else {
                    StockChatHomeTransition(state)
                }
            }
            val effects = buildList {
                if (destination == StockChatHomeDestination.TODAY_MARKET) {
                    add(StockChatHomeEffect.DismissChatUi)
                }
                add(StockChatHomeEffect.CloseDrawer)
            }
            return StockChatHomeTransition(
                state.copy(destination = destination),
                effects,
            )
        }

        private fun commitQuestion(
            state: StockChatHomeState,
            source: StockChatQuestionSource,
        ): StockChatHomeTransition {
            if (!state.canSubmitQuestion(source)) {
                return StockChatHomeTransition(state)
            }
            return StockChatHomeTransition(
                state.copy(
                    destination = StockChatHomeDestination.AI_CHAT,
                    chatStage = StockChatHomeChatStage.CONVERSATION,
                ),
                effects = if (source == StockChatQuestionSource.DRAWER_SHORTCUT) {
                    listOf(StockChatHomeEffect.CloseDrawer)
                } else {
                    emptyList()
                },
            )
        }

        private fun beginTodayMarketLoad(
            state: StockChatHomeState,
        ): StockChatHomeTransition {
            val requestId = state.todayMarketRequestId + 1
            return StockChatHomeTransition(
                state.copy(
                    todayMarketState = TodayMarketUiState.Loading,
                    todayMarketRequestId = requestId,
                    todayMarketRequestInFlight = true,
                ),
                effects = listOf(StockChatHomeEffect.LoadTodayMarket(requestId)),
            )
        }

        private fun completeTodayMarketLoad(
            state: StockChatHomeState,
            event: StockChatHomeEvent.TodayMarketLoadCompleted,
        ): StockChatHomeTransition {
            if (
                !state.started ||
                !state.todayMarketRequestInFlight ||
                event.requestId != state.todayMarketRequestId
            ) {
                return StockChatHomeTransition(state)
            }
            val marketState = when (val result = event.result) {
                is TodayMarketResult.Success -> TodayMarketUiState.Content(result.snapshot)
                TodayMarketResult.Empty -> TodayMarketUiState.Empty
                is TodayMarketResult.Failure -> TodayMarketUiState.Error(result.message)
            }
            return StockChatHomeTransition(
                state.copy(
                    todayMarketState = marketState,
                    todayMarketRequestInFlight = false,
                )
            )
        }

        private fun Boolean.toChatStage(): StockChatHomeChatStage =
            if (this) {
                StockChatHomeChatStage.CONVERSATION
            } else {
                StockChatHomeChatStage.WELCOME
            }
    }
}
