package com.imomali.chatapp

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.imomali.chatapp.data.FirebaseMessageRepository
import com.imomali.chatapp.domain.*

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val mutable = MutableLiveData(MessagingState())
    val state: LiveData<MessagingState> = mutable
    private val handler = Handler(Looper.getMainLooper())
    private val backend = (application as ChatApplication).backend
    private val session = if (backend.auth != null && backend.database != null) MessagingSession(
        FirebaseMessageRepository(backend.auth, backend.database),
        { delay, action ->
            val runnable = Runnable(action)
            handler.postDelayed(runnable, delay)
            Subscription { handler.removeCallbacks(runnable) }
        }, { mutable.value = it }) else null
    fun open(uid: String, conversation: String) { session?.open(uid, conversation) }
    fun pause() { session?.pause() }
    fun clear() { session?.clear() }
    fun edit(value: String) { session?.edit(value) }
    fun send() { session?.send() }
    fun retryHistory() { session?.retryHistory() }
    override fun onCleared() { session?.clear(); super.onCleared() }
}
