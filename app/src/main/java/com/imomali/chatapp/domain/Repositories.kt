package com.imomali.chatapp.domain
/** Subscription handles must be closed when a screen exits or the user signs out. */
fun interface Subscription { fun close() }
interface AuthRepository {
    val currentUserId: String?
    fun observeSession(onChanged: (String?) -> Unit): Subscription
    fun signOut()
}
interface ProfileRepository { fun getProfile(uid: String, result: (Result<UserProfile?>) -> Unit) }
// Subscription contracts are independent of Android views.
interface ConversationRepository { fun observeConversations(result: (Result<List<Conversation>>) -> Unit): Subscription }
interface MessageRepository {
    fun observeRecent(conversationId: String, result: (Result<MessageHistory>) -> Unit): Subscription
    fun send(conversationId: String, clientMessageId: String, text: String, result: (Result<Unit>) -> Unit)
}
