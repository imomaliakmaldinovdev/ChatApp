package com.imomali.chatapp.domain

import java.util.UUID

enum class SendStatus { SENDING, WAITING, FAILED }
data class Outgoing(val id: String, val text: String, val status: SendStatus)
data class MessagingState(
    val uid: String? = null, val conversationId: String? = null,
    val messages: List<Message> = emptyList(), val loading: Boolean = false, val fromCache: Boolean = false,
    val draft: String = "", val outgoing: Outgoing? = null,
    val receiveError: Boolean = false
) {
    val sending get() = outgoing?.status == SendStatus.SENDING
    val draftLocked get() = outgoing != null && outgoing.status != SendStatus.FAILED
    val canSend get() = uid != null && conversationId != null && !sending && MessagePolicy.valid(draft)
}

/** All calls/callbacks run on the UI thread; no Activity or disk-stored private draft. */
class MessagingSession(
    private val repository: MessageRepository,
    private val schedule: (Long, () -> Unit) -> Subscription,
    private val changed: (MessagingState) -> Unit,
    private val newId: () -> String = { UUID.randomUUID().toString() }
) {
    var state = MessagingState()
        private set
    private var generation = 0
    private var listenerGeneration = 0
    private var subscription: Subscription? = null
    private var timeout: Subscription? = null
    private var sendRequest = 0
    private fun update(value: MessagingState) { state = value; changed(value) }

    fun open(uid: String, conversationId: String) {
        if (state.uid != uid || state.conversationId != conversationId) {
            clear()
            update(MessagingState(uid = uid, conversationId = conversationId, loading = true))
        }
        if (subscription == null) listen()
    }
    fun pause() { listenerGeneration++; subscription?.close(); subscription = null }
    fun clear() {
        generation++; sendRequest++; pause(); timeout?.close(); timeout = null
        update(MessagingState())
    }
    fun retryHistory() { pause(); listen() }
    private fun listen() {
        val id = state.conversationId ?: return
        val ticket = ++listenerGeneration
        update(state.copy(loading = state.messages.isEmpty(), receiveError = false))
        subscription = repository.observeRecent(id) { result ->
            if (ticket != listenerGeneration) return@observeRecent
            result.fold({ history ->
                val messages = history.messages.distinctBy { it.id }
                val outgoing = state.outgoing
                val acknowledged = outgoing != null && messages.any {
                    it.id == outgoing.id && it.senderId == state.uid && it.text == outgoing.text && !it.pending
                }
                if (acknowledged) { timeout?.close(); timeout = null }
                update(state.copy(messages = messages, loading = false, fromCache = history.fromCache,
                    receiveError = false, outgoing = if (acknowledged) null else outgoing,
                    draft = if (acknowledged && state.draft == outgoing?.text) "" else state.draft))
            }, { update(state.copy(messages = emptyList(), loading = false, receiveError = true)) })
        }
    }
    fun edit(value: String) {
        if (state.draftLocked) return
        update(state.copy(draft = value.take(MessagePolicy.MAX_LENGTH),
            outgoing = state.outgoing?.takeIf { it.text == value }))
    }
    fun send() {
        if (!state.canSend) return
        val conversation = state.conversationId ?: return
        val ticket = generation
        val request = ++sendRequest
        val outgoing = state.outgoing?.copy(status = SendStatus.SENDING)
            ?: Outgoing(newId(), state.draft, SendStatus.SENDING)
        update(state.copy(outgoing = outgoing))
        timeout?.close()
        timeout = schedule(15000) {
            if (generation == ticket && request == sendRequest && state.outgoing?.id == outgoing.id) {
                update(state.copy(outgoing = outgoing.copy(status = SendStatus.WAITING)))
            }
        }
        repository.send(conversation, outgoing.id, outgoing.text) { result ->
            if (generation != ticket || request != sendRequest || state.outgoing?.id != outgoing.id) return@send
            timeout?.close(); timeout = null
            result.fold({ update(state.copy(outgoing = null, draft = if (state.draft == outgoing.text) "" else state.draft)) },
                { update(state.copy(outgoing = outgoing.copy(status = SendStatus.FAILED))) })
        }
    }
}
