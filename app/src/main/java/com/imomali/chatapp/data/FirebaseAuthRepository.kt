package com.imomali.chatapp.data
import com.google.firebase.auth.FirebaseAuth
import com.imomali.chatapp.domain.AuthRepository
import com.imomali.chatapp.domain.Subscription
class FirebaseAuthRepository(private val auth: FirebaseAuth) : AuthRepository {
    override val currentUserId get() = auth.currentUser?.uid
    override fun observeSession(onChanged: (String?) -> Unit): Subscription {
        val listener = FirebaseAuth.AuthStateListener { onChanged(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        return Subscription { auth.removeAuthStateListener(listener) }
    }
    override fun signOut() = auth.signOut()
}
