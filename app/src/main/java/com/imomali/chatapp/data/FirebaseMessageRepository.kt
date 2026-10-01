package com.imomali.chatapp.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.imomali.chatapp.domain.*

class FirebaseMessageRepository(private val auth: FirebaseAuth, private val db: FirebaseFirestore) : MessageRepository {
    private data class Request(val uid: String, val conversation: String, val id: String)
    private data class Flight(val text: String, val callbacks: MutableList<(Result<Unit>) -> Unit>)
    private val flights = mutableMapOf<Request, Flight>()

    override fun observeRecent(conversationId: String, result: (Result<MessageHistory>) -> Unit): Subscription {
        var closed = false
        val listener = db.collection("conversations").document(conversationId).collection("messages")
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(50)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (closed) return@addSnapshotListener
                if (error != null) { result(Result.failure(error)); return@addSnapshotListener }
                if (snapshot == null) return@addSnapshotListener
                val messages = snapshot.documents.map { doc ->
                    Message(doc.id, doc.getString("senderId").orEmpty(), doc.getString("text").orEmpty(),
                        doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time ?: 0,
                        doc.metadata.hasPendingWrites())
                }.distinctBy { it.id }.sortedWith(compareBy<Message> { it.createdAtMillis }.thenBy { it.id })
                result(Result.success(MessageHistory(messages, snapshot.metadata.isFromCache)))
            }
        return Subscription { closed = true; listener.remove() }
    }

    override fun send(conversationId: String, clientMessageId: String, text: String, result: (Result<Unit>) -> Unit) {
        val uid = auth.currentUser?.uid
        if (uid == null || !MessagePolicy.valid(text) || clientMessageId.isBlank() || '/' in clientMessageId) {
            result(Result.failure(IllegalArgumentException("Invalid message or session"))); return
        }
        val key = Request(uid, conversationId, clientMessageId)
        flights[key]?.let { flight ->
            if (flight.text == text) flight.callbacks += result
            else result(Result.failure(IllegalArgumentException("Message identifier already in use")))
            return
        }
        flights[key] = Flight(text, mutableListOf(result))
        fun finish(value: Result<Unit>) { flights.remove(key)?.callbacks?.forEach { it(value) } }
        val ref = db.collection("conversations").document(conversationId).collection("messages").document(clientMessageId)
        fun matches(doc: DocumentSnapshot) = doc.exists() && doc.getString("senderId") == uid && doc.getString("text") == text
        // Server-first reconciliation makes retries idempotent, including a lost acknowledgement.
        // Security rules still reject nonmembers and all message overwrites.
        ref.get(Source.SERVER).addOnSuccessListener { existing ->
            if (auth.currentUser?.uid != uid) {
                finish(Result.failure(IllegalStateException("Session changed"))); return@addOnSuccessListener
            }
            if (existing.exists()) {
                finish(if (matches(existing)) Result.success(Unit) else Result.failure(IllegalStateException("Message identifier conflict")))
                return@addOnSuccessListener
            }
            ref.set(mapOf("senderId" to uid, "text" to text, "createdAt" to FieldValue.serverTimestamp()))
                .addOnSuccessListener { finish(Result.success(Unit)) }
                .addOnFailureListener { error ->
                    if ((error as? FirebaseFirestoreException)?.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        finish(Result.failure(error)); return@addOnFailureListener
                    }
                    // Another same-ID request may have won the race. Never treat an unrelated
                    // permission failure as success: verify the immutable server document.
                    ref.get(Source.SERVER).addOnSuccessListener { doc ->
                        finish(if (matches(doc)) Result.success(Unit) else Result.failure(error))
                    }.addOnFailureListener { finish(Result.failure(it)) }
                }
        }.addOnFailureListener { finish(Result.failure(it)) }
    }
}
