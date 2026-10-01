package com.imomali.chatapp

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.imomali.chatapp.data.FirebaseBackend
import com.imomali.chatapp.databinding.ActivityMainBinding
import com.imomali.chatapp.domain.AuthValidation

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var model: AuthViewModel
    private lateinit var discovery: DiscoveryViewModel
    private lateinit var discoveryView: DiscoveryView
    private lateinit var chat: ChatViewModel
    private var active = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        model = ViewModelProvider(this)[AuthViewModel::class.java]
        discovery = ViewModelProvider(this)[DiscoveryViewModel::class.java]
        chat = ViewModelProvider(this)[ChatViewModel::class.java]
        discoveryView = DiscoveryView(this, discovery, chat)
        binding.discoveryContainer.addView(discoveryView)
        discovery.state.observe(this) { state ->
            discoveryView.render(state)
            if (model.state.value?.uid != null) renderDiscoveryTitle(state)
            syncChat()
        }
        chat.state.observe(this) { discoveryView.chatView.render(it) }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (model.state.value?.uid != null && discovery.back()) return
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        })
        if (savedInstanceState != null) model.registering = savedInstanceState.getBoolean("registering")
        val padding = (24 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(padding + bars.left, padding + bars.top, padding + bars.right, padding + bars.bottom)
            insets
        }
        val backend = (application as ChatApplication).backend
        binding.backendStatus.visibility = if (BuildConfig.DEBUG) View.VISIBLE else View.GONE
        binding.backendStatus.setText(when (backend.state) {
            FirebaseBackend.State.NOT_CONFIGURED -> R.string.backend_missing
            FirebaseBackend.State.CONFIGURED -> R.string.backend_ready
            FirebaseBackend.State.EMULATOR -> R.string.backend_emulator
            FirebaseBackend.State.INVALID -> R.string.backend_invalid
        })
        binding.signOut.setOnClickListener { chat.clear(); discovery.bind(null); clearInputs(); model.signOut() }
        binding.switchMode.setOnClickListener {
            model.registering = !model.registering
            clearInputs()
            render(model.state.value ?: AuthState())
        }
        binding.submit.setOnClickListener { submit() }
        model.state.observe(this, ::render)
    }
    override fun onStart() { active = true; super.onStart(); model.verifySession() }
    override fun onStop() { active = false; chat.pause(); discovery.pause(); super.onStop() }
    private fun syncChat() {
        if (!active) { chat.pause(); return }
        val auth = model.state.value ?: return
        val route = discovery.state.value ?: return
        if (auth.uid != null && route.screen == DiscoveryScreen.CONVERSATION && route.conversationId != null) {
            chat.open(auth.uid, route.conversationId)
        } else if (!auth.busy && auth.uid == null) chat.clear() else chat.pause()
    }
    private fun renderDiscoveryTitle(state: DiscoveryState) {
        binding.title.text = when (state.screen) {
            DiscoveryScreen.CHATS -> getString(R.string.chats)
            DiscoveryScreen.SEARCH -> getString(R.string.new_chat)
            DiscoveryScreen.PROFILE -> "Profile"
            DiscoveryScreen.CONVERSATION -> "Conversation"
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("registering", model.registering)
        super.onSaveInstanceState(outState)
    }
    private fun clearInputs() {
        listOf(binding.name, binding.email, binding.password, binding.confirm).forEach { it.text?.clear() }
        listOf(binding.nameLayout, binding.emailLayout, binding.passwordLayout, binding.confirmLayout).forEach { it.error = null }
    }
    private fun submit() {
        if (model.state.value?.busy == true) return
        val name = binding.name.text.toString()
        val email = binding.email.text.toString()
        val password = binding.password.text.toString()
        binding.nameLayout.error = if ((model.registering || model.needsProfile) && !AuthValidation.name(name)) "Enter a name of 1–60 characters." else null
        binding.emailLayout.error = if (!model.needsProfile && !AuthValidation.email(email)) "Enter a valid email address." else null
        binding.passwordLayout.error = if (!model.needsProfile && !AuthValidation.password(password)) "Use at least 6 characters." else null
        binding.confirmLayout.error = if (!model.needsProfile && model.registering && password != binding.confirm.text.toString()) "Passwords do not match." else null
        if (listOf(binding.nameLayout, binding.emailLayout, binding.passwordLayout, binding.confirmLayout).any { it.error != null }) return
        binding.password.text?.clear()
        binding.confirm.text?.clear()
        if (model.needsProfile) model.completeProfile(name) else model.submit(name, email, password)
    }
    private fun render(state: AuthState) {
        val signedIn = state.uid != null
        val repair = model.needsProfile
        binding.authContent.visibility = if (signedIn) View.GONE else View.VISIBLE
        binding.discoveryContainer.visibility = if (signedIn) View.VISIBLE else View.GONE
        // Session revalidation temporarily hides the surface without losing its route.
        if (signedIn) discovery.bind(state.uid) else if (!state.busy) discovery.bind(null)
        binding.title.text = when { signedIn -> getString(R.string.chats); repair -> "Complete your profile"; model.registering -> "Create account"; else -> getString(R.string.welcome) }
        binding.subtitle.text = when { signedIn -> getString(R.string.empty_chats); repair -> "Choose the name people will see."; model.registering -> "Start your next conversation."; else -> "Welcome back. Sign in to stay connected." }
        binding.subtitle.visibility = if (signedIn) View.GONE else View.VISIBLE
        binding.form.visibility = if (signedIn) View.GONE else View.VISIBLE
        binding.nameLayout.visibility = if (model.registering || repair) View.VISIBLE else View.GONE
        binding.emailLayout.visibility = if (repair) View.GONE else View.VISIBLE
        binding.passwordLayout.visibility = if (repair) View.GONE else View.VISIBLE
        binding.confirmLayout.visibility = if (model.registering && !repair) View.VISIBLE else View.GONE
        binding.submit.visibility = if (signedIn) View.GONE else View.VISIBLE
        binding.submit.text = when { repair -> "Save profile"; model.registering -> "Create account"; else -> "Sign in" }
        binding.switchMode.visibility = if (signedIn || repair) View.GONE else View.VISIBLE
        binding.switchMode.text = if (model.registering) "Already have an account? Sign in" else "Create an account"
        binding.signOut.visibility = if (signedIn || repair) View.VISIBLE else View.GONE
        binding.progress.visibility = if (state.busy) View.VISIBLE else View.GONE
        binding.error.text = state.error
        binding.error.visibility = if (state.error == null) View.GONE else View.VISIBLE
        listOf(binding.submit, binding.switchMode, binding.name, binding.email, binding.password, binding.confirm).forEach { it.isEnabled = !state.busy }
        if (signedIn) { clearInputs(); renderDiscoveryTitle(discovery.state.value!!) }
        syncChat()
    }
}
