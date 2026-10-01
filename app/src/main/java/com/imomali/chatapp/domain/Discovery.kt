package com.imomali.chatapp.domain

import java.util.Locale

data class ChatSummary(val conversation: Conversation, val participant: UserProfile?, val lastMessage: Message?) {
    val activityMillis get() = lastMessage?.createdAtMillis ?: conversation.createdAtMillis
}

object DiscoveryPolicy {
    fun query(value: String) = value.trim().lowercase(Locale.ROOT).take(60)
    fun conversationId(first: String, second: String): String {
        require(first.isNotBlank() && second.isNotBlank() && first != second)
        require(':' !in first && ':' !in second)
        return listOf(first, second).sorted().joinToString(":")
    }
    fun initials(name: String): String = name.trim().split(Regex("\\s+"))
        .filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1) }.uppercase(Locale.ROOT).ifEmpty { "?" }
    fun sorted(chats: Collection<ChatSummary>) = chats.sortedWith(
        compareByDescending<ChatSummary> { it.activityMillis }.thenBy { it.conversation.id })
}

interface DiscoveryRepository {
    fun observeChats(uid: String, result: (Result<List<ChatSummary>>) -> Unit): Subscription
    fun search(uid: String, query: String, result: (Result<List<UserProfile>>) -> Unit)
    fun profile(uid: String, result: (Result<UserProfile?>) -> Unit)
    fun start(uid: String, participantId: String, result: (Result<String>) -> Unit)
}
