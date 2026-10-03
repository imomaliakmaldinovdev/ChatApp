package com.imomali.chatapp

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.imomali.chatapp.data.FirebaseDiscoveryRepository
import com.imomali.chatapp.domain.*

enum class DiscoveryScreen { CHATS, SEARCH, PROFILE, CONVERSATION }
data class DiscoveryState(
    val screen: DiscoveryScreen = DiscoveryScreen.CHATS,
    val chats: List<ChatSummary> = emptyList(), val loadingChats: Boolean = true,
    val query: String = "", val results: List<UserProfile> = emptyList(), val searching: Boolean = false,
    val profile: UserProfile? = null, val loadingProfile: Boolean = false,
    val conversationId: String? = null, val participant: UserProfile? = null,
    val starting: Boolean = false, val error: String? = null, val chatsError: String? = null
)

class DiscoveryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as ChatApplication).backend.database?.let { FirebaseDiscoveryRepository(it) }
    private val mutable = MutableLiveData(DiscoveryState())
    val state: LiveData<DiscoveryState> = mutable
    private val handler = Handler(Looper.getMainLooper())
    private var uid: String? = null
    private var session = 0
    private var request = 0
    private var profileRequest = 0
    private var subscription: Subscription? = null
    private var searchJob: Runnable? = null
    private var startJob: Runnable? = null
    private var startRequest = 0
    private var retryParticipant: UserProfile? = null
    private var previous = DiscoveryScreen.CHATS
    private fun update(change: (DiscoveryState) -> DiscoveryState) { mutable.value = change(mutable.value!!) }

    fun bind(userId: String?) {
        if (userId == uid) {
            if (userId != null && subscription == null) {
                listen()
                if (state.value!!.screen == DiscoveryScreen.SEARCH && retryParticipant == null) search(state.value!!.query)
            }
            return
        }
        pause(); session++; request++; profileRequest++; uid = userId
        startRequest++; startJob?.let(handler::removeCallbacks); startJob = null; retryParticipant = null
        mutable.value = DiscoveryState()
        if (userId != null) listen()
    }
    fun pause() {
        subscription?.close(); subscription = null
        searchJob?.let(handler::removeCallbacks); searchJob = null
        request++
        update { it.copy(searching = false) }
    }
    private fun listen() {
        val userId = uid ?: return
        val ticket = session
        update { it.copy(loadingChats = true) }
        subscription = repository?.observeChats(userId) { result ->
            if (session != ticket) return@observeChats
            result.fold({ chats -> update { it.copy(chats = chats, loadingChats = false, chatsError = null) } },
                { update { it.copy(chats = emptyList(), loadingChats = false, chatsError = "Could not load chats. Check your connection and retry.") } })
        }
    }
    fun retry() {
        when (state.value!!.screen) {
            DiscoveryScreen.SEARCH -> retryParticipant?.let(::start) ?: search(state.value!!.query)
            DiscoveryScreen.PROFILE -> showProfile(state.value!!.profile?.uid ?: state.value!!.participant?.uid ?: uid ?: return, false)
            else -> { subscription?.close(); subscription = null; listen() }
        }
    }
    fun showSearch() { update { it.copy(screen = DiscoveryScreen.SEARCH, error = null) } }
    fun search(value: String) {
        retryParticipant = null
        searchJob?.let(handler::removeCallbacks)
        val ticket = ++request
        val identity = session
        val userId = uid ?: return
        val query = DiscoveryPolicy.query(value)
        update { it.copy(query = value, results = emptyList(), searching = query.isNotEmpty(), error = null) }
        if (query.isEmpty()) return
        searchJob = Runnable {
            repository?.search(userId, query) { result ->
                if (ticket != request || identity != session) return@search
                result.fold({ users -> update { it.copy(results = users, searching = false) } },
                    { update { it.copy(searching = false, error = "Search failed. Check your connection and retry.") } })
            }
        }.also { handler.postDelayed(it, 300) }
    }
    fun showOwnProfile() { uid?.let { showProfile(it) } }
    fun showProfile(userId: String, remember: Boolean = true) {
        if (remember) previous = state.value!!.screen
        val ticket = ++profileRequest
        val identity = session
        update { it.copy(screen = DiscoveryScreen.PROFILE, profile = UserProfile(userId, "", ""), loadingProfile = true, error = null) }
        repository?.profile(userId) { result ->
            if (ticket != profileRequest || identity != session) return@profile
            result.fold({ profile -> update { it.copy(profile = profile, loadingProfile = false) } },
                { update { it.copy(loadingProfile = false, error = "Could not load this profile. Please retry.") } })
        }
    }
    fun open(chat: ChatSummary) {
        update { it.copy(screen = DiscoveryScreen.CONVERSATION, conversationId = chat.conversation.id,
            participant = chat.participant, error = null) }
    }
    fun start(profile: UserProfile) {
        val userId = uid ?: return
        if (state.value!!.starting) return
        val ticket = session
        val attempt = ++startRequest
        retryParticipant = profile
        update { it.copy(starting = true, error = null) }
        startJob?.let(handler::removeCallbacks)
        startJob = Runnable {
            if (ticket == session && attempt == startRequest) {
                startRequest++
                update { it.copy(starting = false, error = "Conversation not confirmed. Retry opens the same conversation; it will not create a duplicate.") }
            }
        }.also { handler.postDelayed(it, 15000) }
        repository?.start(userId, profile.uid) { result ->
            if (ticket != session || attempt != startRequest) return@start
            startJob?.let(handler::removeCallbacks); startJob = null
            result.fold({ id -> update { it.copy(screen = DiscoveryScreen.CONVERSATION, conversationId = id,
                participant = profile, starting = false, error = null) } },
                { update { it.copy(starting = false, error = "Could not start this conversation. Check your connection and retry.") } })
        }
    }
    fun back(): Boolean {
        if (state.value!!.starting) return true
        if (state.value!!.screen == DiscoveryScreen.CHATS) return false
        retryParticipant = null
        profileRequest++
        update { it.copy(screen = if (it.screen == DiscoveryScreen.PROFILE) previous else DiscoveryScreen.CHATS, error = null) }
        return true
    }
    override fun onCleared() { pause(); session++; startJob?.let(handler::removeCallbacks); super.onCleared() }
}
