package com.imomali.chatapp
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.imomali.chatapp.data.FirebaseAuthRepository
import com.imomali.chatapp.data.FirebaseBackend
import com.imomali.chatapp.databinding.ActivityMainBinding
import com.imomali.chatapp.domain.Subscription
import com.imomali.chatapp.navigation.Route
import com.imomali.chatapp.navigation.SessionRouter
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var session: Subscription? = null
    private val backend get() = (application as ChatApplication).backend
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val padding = (24 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(padding + bars.left, padding + bars.top, padding + bars.right, padding + bars.bottom)
            insets
        }
        binding.backendStatus.visibility = if (BuildConfig.DEBUG) View.VISIBLE else View.GONE
        binding.backendStatus.setText(when (backend.state) {
            FirebaseBackend.State.NOT_CONFIGURED -> R.string.backend_missing
            FirebaseBackend.State.CONFIGURED -> R.string.backend_ready
            FirebaseBackend.State.EMULATOR -> R.string.backend_emulator
            FirebaseBackend.State.INVALID -> R.string.backend_invalid
        })
        binding.signOut.setOnClickListener { backend.auth?.signOut() }
    }
    override fun onStart() {
        super.onStart()
        val auth = backend.auth
        if (auth == null) render(null)
        else session = FirebaseAuthRepository(auth).observeSession(::render)
    }
    override fun onStop() { session?.close(); session = null; super.onStop() }
    private fun render(uid: String?) {
        val signedIn = SessionRouter.resolve(Route.CHATS, uid) == Route.CHATS
        binding.title.setText(if (signedIn) R.string.chats else R.string.welcome)
        binding.subtitle.setText(if (signedIn) R.string.empty_chats else R.string.subtitle)
        binding.signOut.visibility = if (signedIn) View.VISIBLE else View.GONE
    }
}
