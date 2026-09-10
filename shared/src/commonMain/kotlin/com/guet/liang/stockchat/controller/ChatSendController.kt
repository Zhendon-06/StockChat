package com.guet.liang.stockchat.controller

import com.guet.liang.stockchat.base.StockChatLog
import com.guet.liang.stockchat.base.messageText
import com.guet.liang.stockchat.data.StockChatDataSource
import com.guet.liang.stockchat.model.AnswerBlock
import com.guet.liang.stockchat.model.ChatAnswer
import com.guet.liang.stockchat.model.ChatMessage
import com.guet.liang.stockchat.model.ChatModelOption
import com.guet.liang.stockchat.model.ChatRole
import com.guet.liang.stockchat.model.MessageState
import com.guet.liang.stockchat.model.ModelCapability

/** Validated user input and its image request payloads travel together. */
internal data class ChatSubmission(val text: String, val images: List<String> = emptyList(), val payloads: List<String> = emptyList()) {
    val question: String
        get() = text.trim().ifBlank { if (images.isNotEmpty()) "请分析我上传的图片" else "" }
}

/** Owns answer lifecycle and ignores responses after the conversation is invalidated. */
internal class ChatSendController(
    private val source: () -> StockChatDataSource,
    private val sessions: ChatSessionController,
    private val selectedModel: () -> ChatModelOption,
    private val onSendingChanged: (Boolean) -> Unit = {},
) {
    var isSending = false
        private set

    private var requestToken = 0

    fun validationError(input: ChatSubmission): String? {
        val model = selectedModel()
        return when {
            model.id.isBlank() -> "当前 Provider 暂无可用模型，请先在模型配置中获取模型"
            input.images.size != input.payloads.size || input.payloads.any { !it.startsWith("data:image/") } -> "图片处理失败，请重新选择"
            input.images.isNotEmpty() && ModelCapability.VISION !in model.capabilities -> "当前模型不支持图片理解，请切换到“视觉理解”模型后重试"
            input.question.isBlank() -> "请输入问题，例如查行情、学炒股或问其他问题"
            else -> null
        }
    }

    fun sendMessage(input: ChatSubmission) {
        if (isSending || validationError(input) != null) return
        setSending(true)
        val token = ++requestToken
        val question = input.question
        val user =
            ChatMessage(
                id = sessions.nextMessageId(),
                role = ChatRole.USER,
                blocks =
                    buildList {
                        if (input.images.isNotEmpty()) add(AnswerBlock.ImageGallery(input.images, input.payloads))
                        add(AnswerBlock.Markdown(question, question))
                    },
            )
        val answer =
            ChatMessage(
                id = sessions.nextMessageId(),
                role = ChatRole.ASSISTANT,
                blocks = emptyList(),
                state = MessageState.GENERATING,
                retryQuestion = question,
            )
        sessions.replaceMessages(sessions.messages + user + answer)
        sessions.persist()
        completeAnswer(answer.id, question, 0, token)
    }

    fun retryMessage(message: ChatMessage) {
        if (isSending || message.retryQuestion.isEmpty()) return
        val index = sessions.messages.indexOfFirst { it.id == message.id }
        if (index < 0) return
        setSending(true)
        val token = ++requestToken
        val next =
            message.copy(blocks = emptyList(), state = MessageState.GENERATING, retryAttempt = message.retryAttempt + 1, errorMessage = "")
        sessions.replaceMessages(sessions.messages.toMutableList().apply { this[index] = next })
        sessions.persist()
        completeAnswer(next.id, next.retryQuestion, next.retryAttempt, token)
    }

    fun regenerateMessage(message: ChatMessage): String? {
        val index = sessions.messages.indexOfFirst { it.id == message.id }
        val question =
            message.retryQuestion.ifBlank {
                sessions.messages.take(index.coerceAtLeast(0)).lastOrNull { it.role == ChatRole.USER }?.let(::messageText).orEmpty()
            }
        return when {
            isSending -> "请等待当前回答完成"
            index < 0 -> null
            question.isBlank() -> "找不到对应的提问，无法重新生成"
            else -> {
                retryMessage(message.copy(retryQuestion = question))
                null
            }
        }
    }

    fun invalidate() {
        requestToken += 1
        setSending(false)
    }

    private fun completeAnswer(messageId: String, question: String, attempt: Int, token: Int) {
        val model = selectedModel()
        val images = imagesBeforeAnswer(sessions.messages, messageId)
        val validation =
            when {
                model.id.isBlank() -> "当前 Provider 暂无可用模型，请先在模型配置中获取模型。"
                images.isNotEmpty() && ModelCapability.VISION !in model.capabilities -> "当前模型 ${model.displayName} 不支持图片理解，请切换到“视觉理解”模型后重试。"
                else -> null
            }
        if (validation != null) {
            applyAnswer(messageId, question, attempt, ChatAnswer.Failure(validation))
            return
        }
        runCatching {
                source().answer(question, conversationHistoryBefore(sessions.messages, messageId), images, model.id, attempt) {
                    if (token == requestToken) applyAnswer(messageId, question, attempt, it)
                }
            }
            .onFailure {
                StockChatLog.w("ChatSendController", "answer failed", it)
                if (token == requestToken) {
                    applyAnswer(messageId, question, attempt, ChatAnswer.Failure("AI 服务暂时不可用，请稍后重试。"))
                }
            }
    }

    private fun applyAnswer(messageId: String, question: String, attempt: Int, answer: ChatAnswer) {
        val index = sessions.messages.indexOfFirst { it.id == messageId }
        if (index < 0) return
        val previous = sessions.messages[index]
        val next =
            when (answer) {
                is ChatAnswer.Streaming ->
                    previous.copy(
                        blocks = listOf(AnswerBlock.Markdown(answer.markdown, answer.markdown)) + answer.blocks,
                        state = MessageState.GENERATING,
                    )
                is ChatAnswer.Success ->
                    ChatMessage(id = messageId, role = ChatRole.ASSISTANT, blocks = answer.blocks, retryQuestion = question)
                is ChatAnswer.Failure ->
                    ChatMessage(
                        id = messageId,
                        role = ChatRole.ASSISTANT,
                        blocks = emptyList(),
                        state = MessageState.FAILED,
                        retryQuestion = question,
                        retryAttempt = attempt,
                        errorMessage = answer.message,
                    )
            }
        sessions.replaceMessages(sessions.messages.toMutableList().apply { this[index] = next })
        if (answer !is ChatAnswer.Streaming) {
            setSending(false)
            sessions.persist()
        }
    }

    private fun setSending(value: Boolean) {
        isSending = value
        onSendingChanged(value)
    }
}
