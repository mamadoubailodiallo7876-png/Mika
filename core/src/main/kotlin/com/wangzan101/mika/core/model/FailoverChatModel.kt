package com.wangzan101.mika.core.model

import java.util.concurrent.ConcurrentHashMap

class FailoverChatModel(
    private val candidates: List<Pair<String, ChatModel>>,
) : StreamingChatModel {

    init {
        require(candidates.isNotEmpty()) { "FailoverChatModel requires at least one candidate" }
    }

    override val capabilities: Capabilities
        get() = orderedCandidates().first().second.capabilities

    override val name: String
        get() = orderedCandidates().first().second.name

    override suspend fun complete(
        system: String,
        messages: List<Message>,
        tools: List<ToolSpec>,
    ): ModelReply = runWithFailover { model ->
        model.complete(system, messages, tools)
    }

    override suspend fun completeStreaming(
        system: String,
        messages: List<Message>,
        tools: List<ToolSpec>,
        onText: (String) -> Unit,
    ): ModelReply = runWithFailover { model ->
        if (model is StreamingChatModel) {
            model.completeStreaming(system, messages, tools, onText)
        } else {
            model.complete(system, messages, tools)
        }
    }

    private fun orderedCandidates(): List<Pair<String, ChatModel>> {
        val now = System.currentTimeMillis()
        val healthy = candidates.filter { (cooldowns[it.first] ?: 0L) <= now }
        val cooled = candidates.filter { (cooldowns[it.first] ?: 0L) > now }
            .sortedBy { cooldowns[it.first] ?: 0L }
        return if (healthy.isNotEmpty()) healthy + cooled else cooled
    }

    private suspend fun runWithFailover(call: suspend (ChatModel) -> ModelReply): ModelReply {
        var lastError: ModelError? = null
        for ((key, model) in orderedCandidates()) {
            try {
                val reply = call(model)
                cooldowns.remove(key)
                return reply
            } catch (e: ModelError) {
                lastError = e
                val cooldownMs = cooldownFor(e)
                if (cooldownMs > 0L) {
                    cooldowns[key] = System.currentTimeMillis() + cooldownMs
                } else {
                    throw e
                }
            }
        }
        throw lastError!!
    }

    companion object {
        private val cooldowns = ConcurrentHashMap<String, Long>()

        private fun cooldownFor(e: ModelError): Long = when (e) {
            is ModelError.RateLimited -> 60_000L
            is ModelError.QuotaExceeded,
            is ModelError.ChatGptLimitReached -> 15 * 60_000L
            is ModelError.Server,
            is ModelError.Network,
            is ModelError.StreamCut -> 30_000L
            is ModelError.Auth,
            is ModelError.ModelNotAvailable,
            is ModelError.ModelNotFound,
            is ModelError.ToolsUnsupported,
            is ModelError.AccountPlanNotAllowed,
            is ModelError.SignInExpired,
            is ModelError.ChatGptNotEligible -> 10 * 60_000L
            else -> 0L
        }
    }
}
