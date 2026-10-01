package com.imomali.chatapp.domain
/** Only public discovery fields; do not store email or credentials in profiles. */
data class UserProfile(val uid: String, val displayName: String, val searchName: String, val bio: String = "")
data class Conversation(val id: String, val memberIds: List<String>, val createdAtMillis: Long)
data class Message(val id: String, val senderId: String, val text: String, val createdAtMillis: Long, val pending: Boolean = false)
data class MessageHistory(val messages: List<Message>, val fromCache: Boolean)
object MessagePolicy {
    const val MAX_LENGTH = 4000
    fun valid(text: String) = text.isNotBlank() && text.length <= MAX_LENGTH
}
