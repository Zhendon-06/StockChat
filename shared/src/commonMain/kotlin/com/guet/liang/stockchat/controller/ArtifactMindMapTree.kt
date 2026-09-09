package com.guet.liang.stockchat.controller

import com.guet.liang.stockchat.model.ConversationMindMapArtifact
import com.guet.liang.stockchat.model.ConversationTableRowStatus
import com.guet.liang.stockchat.model.MermaidMindMapNode

internal fun fallbackArtifactMindMap(artifact: ConversationMindMapArtifact): MermaidMindMapNode {
    return MermaidMindMapNode(
        label = artifact.title.ifBlank { "当前会话" },
        children = artifact.branches.map { branch ->
            val details = mutableListOf<MermaidMindMapNode>()
            details += MermaidMindMapNode(
                label = "洞察：${branch.insight.ifBlank { "暂无 AI 摘要" }}",
                children = emptyList(),
            )
            if (branch.relatedInstrument.isNotBlank() && branch.relatedInstrument != "未识别") {
                details += MermaidMindMapNode(
                    label = "标的：${branch.relatedInstrument}",
                    children = emptyList(),
                )
            }
            details += MermaidMindMapNode(
                label = "状态：${statusLabel(branch.status)}",
                children = emptyList(),
            )
            MermaidMindMapNode(
                label = "${branch.sequence}. ${branch.topic.ifBlank { "未命名问题" }}",
                children = details,
            )
        },
    )
}

private fun statusLabel(status: ConversationTableRowStatus): String {
    return when (status) {
        ConversationTableRowStatus.COMPLETED -> "已完成"
        ConversationTableRowStatus.GENERATING -> "生成中"
        ConversationTableRowStatus.FAILED -> "生成失败"
        ConversationTableRowStatus.WAITING -> "等待回答"
    }
}
