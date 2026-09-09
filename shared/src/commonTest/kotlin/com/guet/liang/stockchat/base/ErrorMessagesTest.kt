package com.guet.liang.stockchat.base

import kotlin.test.Test
import kotlin.test.assertEquals

class ErrorMessagesTest {
    @Test
    fun timeoutFailuresUseTimeoutCopy() {
        assertEquals("网络请求超时，请稍后重试。", IllegalStateException("request timed out").toUserMessage("fallback"))
    }

    @Test
    fun malformedJsonUsesFormatCopy() {
        assertEquals("服务返回的数据格式暂不可用，请稍后重试。", IllegalArgumentException("JSON parse failed").toUserMessage("fallback"))
    }

    @Test
    fun unknownFailuresKeepFallback() {
        assertEquals("fallback", IllegalStateException("disk unavailable").toUserMessage("fallback"))
    }
}
