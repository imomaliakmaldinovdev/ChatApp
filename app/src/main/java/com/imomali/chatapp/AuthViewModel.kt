package com.imomali.chatapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Source
import java.util.Locale

data class AuthState(val busy: Boolean = false, val uid: String? = null, val error: String? = null)

/** Owns requests across rotation. Passwords are passed to Firebase and never retained here. */
class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val backend = (application as ChatApplication).backend
    val state = MutableLiveData(AuthState())
    var registering = false
    private var operation = false
    private var generation = 0
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var profileTimeout: Runnable? = null
    private fun cancelProfileTimeout() { profileTimeout?.let(handler::removeCallbacks); profileTimeout = null }
    private fun watchProfileWrite(ticket: Int) {
        cancelProfileTimeout()
        profileTimeout = Runnable {
            if (generation == ticket) {
                generation++
                fail(FirebaseNetworkException("Profile confirmation timed out"))
            }
        }.also { handler.postDelayed(it, 15000) }
    }
    private val listener = FirebaseAuth.AuthStateListener { auth ->
        if (!operation) {
            if (auth.currentUser == null) state.value = AuthState(error = state.value?.error)
            else verifySession()
        }
    }
    init { backend.auth?.addAuthStateListener(listener) }

    fun submit(name: String, email: String, password: String) {
        if (state.value?.busy == true) return
        val auth = backend.auth
        if (auth == null) {
            state.value = AuthState(error = "Firebase setup is pending. Add the teacher’s configuration file before signing in.")
            return
        }
        operation = true
        state.value = AuthState(busy = true)
        val ticket = ++generation
        val request = if (registering) auth.createUserWithEmailAndPassword(email.trim(), password)
            else auth.signInWithEmailAndPassword(email.trim(), password)
        val createProfile = registering
        request.addOnCompleteListener { result ->
            if (ticket != generation) return@addOnCompleteListener
            if (!result.isSuccessful) { fail(result.exception); return@addOnCompleteListener }
            val uid = requireNotNull(result.result.user).uid
            if (createProfile) {
                watchProfileWrite(ticket)
                val displayName = name.trim()
                requireNotNull(backend.database).collection("profiles").document(uid)
                    .set(mapOf("displayName" to displayName, "searchName" to displayName.lowercase(Locale.ROOT), "bio" to ""))
                    .addOnCompleteListener { saved ->
                        if (ticket != generation) return@addOnCompleteListener
                        if (saved.isSuccessful) finish(uid)
                        else {
                            cancelProfileTimeout()
                            operation = false
                            auth.signOut()
                            state.value = AuthState(error = "Account created, but profile setup failed. Sign in again and complete your profile.")
                        }
                    }
            } else loadProfile(uid, ticket)
        }
    }

    // A missing profile can follow an interrupted registration. Allow an explicit repair,
    // without exposing the protected application until its required profile exists.
    fun completeProfile(name: String) {
        val user = backend.auth?.currentUser ?: return
        if (state.value?.busy == true) return
        operation = true
        state.value = AuthState(busy = true)
        val ticket = ++generation
        val displayName = name.trim()
        watchProfileWrite(ticket)
        requireNotNull(backend.database).collection("profiles").document(user.uid)
            .set(mapOf("displayName" to displayName, "searchName" to displayName.lowercase(Locale.ROOT), "bio" to ""))
            .addOnCompleteListener {
                if (ticket != generation) return@addOnCompleteListener
                if (it.isSuccessful) finish(user.uid) else fail(it.exception)
            }
    }

    var needsProfile = false
        private set

    fun verifySession() {
        val user = backend.auth?.currentUser ?: return
        if (operation) return
        operation = true
        state.value = AuthState(busy = true)
        val ticket = ++generation
        user.reload().addOnCompleteListener { result ->
            if (ticket != generation) return@addOnCompleteListener
            if (result.isSuccessful) loadProfile(user.uid, ticket)
            else fail(result.exception)
        }
    }

    private fun loadProfile(uid: String, ticket: Int) {
        requireNotNull(backend.database).collection("profiles").document(uid).get(Source.SERVER)
            .addOnCompleteListener {
                if (ticket != generation) return@addOnCompleteListener
                if (!it.isSuccessful) fail(it.exception)
                else if (!it.result.exists()) {
                    operation = false
                    needsProfile = true
                    state.value = AuthState(error = "Finish your profile to continue.")
                } else finish(uid)
            }
    }

    private fun finish(uid: String) {
        cancelProfileTimeout()
        operation = false
        needsProfile = false
        state.value = AuthState(uid = uid)
    }

    private fun fail(error: Exception?) {
        cancelProfileTimeout()
        operation = false
        needsProfile = false
        backend.auth?.signOut()
        val message = when {
            error is FirebaseNetworkException -> "Connection unavailable. Check your internet connection and try again."
            error is FirebaseAuthException && error.errorCode == "ERROR_EMAIL_ALREADY_IN_USE" -> "Unable to register. Try signing in or use another email."
            error is FirebaseAuthException && error.errorCode == "ERROR_WEAK_PASSWORD" -> "Use a stronger password with at least 6 characters."
            else -> "Unable to sign in or finish setup. Check your details and try again."
        }
        state.value = AuthState(error = message)
    }

    fun signOut() {
        cancelProfileTimeout()
        ++generation
        operation = false
        needsProfile = false
        backend.auth?.signOut()
        state.value = AuthState()
    }
    override fun onCleared() {
        cancelProfileTimeout()
        ++generation
        backend.auth?.removeAuthStateListener(listener)
    }
}
