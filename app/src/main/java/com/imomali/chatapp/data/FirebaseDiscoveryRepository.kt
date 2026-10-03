package com.imomali.chatapp.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.imomali.chatapp.domain.*

class FirebaseDiscoveryRepository(private val db: FirebaseFirestore) : DiscoveryRepository {
    private fun DocumentSnapshot.profile() = if (exists()) UserProfile(id,
        getString("displayName").orEmpty(), getString("searchName").orEmpty(), getString("bio").orEmpty()) else null

    override fun profile(uid: String, result: (Result<UserProfile?>) -> Unit) {
        db.collection("profiles").document(uid).get(Source.SERVER)
            .addOnSuccessListener { result(Result.success(it.profile())) }
            .addOnFailureListener { result(Result.failure(it)) }
    }

    override fun search(uid: String, query: String, result: (Result<List<UserProfile>>) -> Unit) {
        val prefix = DiscoveryPolicy.query(query)
        if (prefix.isEmpty()) { result(Result.success(emptyList())); return }
        db.collection("profiles").orderBy("searchName").startAt(prefix).endAt(prefix + "\uf8ff")
            .limit(21).get(Source.SERVER)
            .addOnSuccessListener { snapshot -> result(Result.success(snapshot.documents.mapNotNull { it.profile() }
                .filter { it.uid != uid }.take(20))) }
            .addOnFailureListener { result(Result.failure(it)) }
    }

    override fun start(uid: String, participantId: String, result: (Result<String>) -> Unit) {
        val id = try { DiscoveryPolicy.conversationId(uid, participantId) }
            catch (error: IllegalArgumentException) { result(Result.failure(error)); return }
        val ref = db.collection("conversations").document(id)
        // A missing conversation cannot be read under membership rules. Try the canonical
        // create first; immutable rules reject duplicates, then verify the existing document.
        ref.set(mapOf("memberIds" to listOf(uid, participantId).sorted(), "createdAt" to FieldValue.serverTimestamp()))
            .addOnSuccessListener { result(Result.success(id)) }
            .addOnFailureListener { error ->
                if ((error as? FirebaseFirestoreException)?.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    result(Result.failure(error)); return@addOnFailureListener
                }
                ref.get(Source.SERVER).addOnSuccessListener { existing ->
                    if (existing.get("memberIds") == listOf(uid, participantId).sorted()) result(Result.success(id))
                    else result(Result.failure(error))
                }.addOnFailureListener { result(Result.failure(it)) }
            }
    }

    override fun observeChats(uid: String, result: (Result<List<ChatSummary>>) -> Unit): Subscription {
        var closed = false
        val rows = mutableMapOf<String, ChatSummary>()
        val children = mutableMapOf<String, MutableList<ListenerRegistration>>()
        val pending = mutableSetOf<String>()
        val failures = mutableMapOf<String, Exception>()
        fun publish() {
            if (closed) return
            val error = failures.values.firstOrNull()
            if (error != null) result(Result.failure(error))
            else if (pending.isEmpty()) result(Result.success(DiscoveryPolicy.sorted(rows.values)))
        }
        val parent = db.collection("conversations").whereArrayContains("memberIds", uid)
            .orderBy("createdAt", Query.Direction.DESCENDING).addSnapshotListener { snapshot, error ->
                if (closed) return@addSnapshotListener
                if (error != null) { result(Result.failure(error)); return@addSnapshotListener }
                val documents = snapshot?.documents.orEmpty()
                val ids = documents.map { it.id }.toSet()
                (rows.keys - ids).forEach { id ->
                    children.remove(id)?.forEach { it.remove() }
                    rows.remove(id); pending.remove("$id/profile"); pending.remove("$id/message")
                    failures.remove("$id/profile"); failures.remove("$id/message")
                }
                documents.forEach { doc ->
                    val members = (doc.get("memberIds") as? List<*>)?.filterIsInstance<String>().orEmpty()
                    val other = members.firstOrNull { it != uid } ?: return@forEach
                    val conversation = Conversation(doc.id, members, doc.getTimestamp("createdAt")?.toDate()?.time ?: 0)
                    val old = rows[doc.id]
                    if (old != null) { rows[doc.id] = old.copy(conversation = conversation); return@forEach }
                    rows[doc.id] = ChatSummary(conversation, null, null)
                    pending.add("${doc.id}/profile"); pending.add("${doc.id}/message")
                    val listeners = mutableListOf<ListenerRegistration>()
                    children[doc.id] = listeners
                    listeners += db.collection("profiles").document(other).addSnapshotListener profileListener@{ profile, failure ->
                        if (closed || doc.id !in rows) return@profileListener
                        val key = "${doc.id}/profile"
                        pending.remove(key)
                        if (failure != null) failures[key] = failure else {
                            failures.remove(key)
                            rows[doc.id] = rows.getValue(doc.id).copy(participant = profile?.profile())
                        }
                        publish()
                    }
                    listeners += doc.reference.collection("messages").orderBy("createdAt", Query.Direction.DESCENDING)
                        .limit(1).addSnapshotListener messageListener@{ messages, failure ->
                            if (closed || doc.id !in rows) return@messageListener
                            val key = "${doc.id}/message"
                            pending.remove(key)
                            if (failure != null) failures[key] = failure else {
                                failures.remove(key)
                                val message = messages?.documents?.firstOrNull()?.let {
                                    Message(it.id, it.getString("senderId").orEmpty(), it.getString("text").orEmpty(),
                                        it.getTimestamp("createdAt")?.toDate()?.time ?: conversation.createdAtMillis)
                                }
                                rows[doc.id] = rows.getValue(doc.id).copy(lastMessage = message)
                            }
                            publish()
                        }
                }
                publish()
            }
        return Subscription {
            closed = true; parent.remove(); children.values.flatten().forEach { it.remove() }
            children.clear(); rows.clear(); pending.clear(); failures.clear()
        }
    }
}
