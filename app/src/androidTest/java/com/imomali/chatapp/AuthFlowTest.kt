package com.imomali.chatapp

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelProvider
import android.widget.EditText
import android.widget.TextView
import android.view.View
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.Source
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class AuthFlowTest {
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    @Suppress("DEPRECATION")
    @org.junit.Before fun wake() {
        val power = app.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        wakeLock = power.newWakeLock(android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP, "ChatApp:auth-test").also { it.acquire(120000) }
    }
    @org.junit.After fun release() { wakeLock?.let { if (it.isHeld) it.release() } }
    private val app get() = ApplicationProvider.getApplicationContext<ChatApplication>()
    private fun main(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    private fun waitFor(model: AuthViewModel, predicate: (AuthState) -> Boolean) {
        val until = System.currentTimeMillis() + 20000
        while (System.currentTimeMillis() < until) {
            var done = false
            main { done = predicate(requireNotNull(model.state.value)) }
            if (done) return
            Thread.sleep(100)
        }
        fail("Authentication did not reach the expected state: ${model.state.value}")
    }
    @Test fun twoAccountsRegisterRestoreLogoutAndRejectWrongPassword() {
        assumeTrue(BuildConfig.USE_EMULATORS)
        val backend = app.backend
        val auth = requireNotNull(backend.auth)
        auth.signOut()
        val store = androidx.lifecycle.ViewModelStore()
        lateinit var model: AuthViewModel
        main { model = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))[AuthViewModel::class.java] }
        val stamp = System.currentTimeMillis()
        try {
            for (number in 1..2) {
                val email = "tuesday-$stamp-$number@example.test"
                main { model.registering = true; model.submit("Tester $number", email, "Test-only-123!") }
                waitFor(model) { !it.busy && it.uid != null }
                val uid = requireNotNull(auth.currentUser).uid
                assertEquals("Tester $number", Tasks.await(requireNotNull(backend.database).collection("profiles").document(uid).get(Source.SERVER), 15, TimeUnit.SECONDS).getString("displayName"))
                main { model.signOut() }
                assertNull(auth.currentUser)
                main { model.registering = false; model.submit("", email, "wrong-password") }
                waitFor(model) { !it.busy && it.error != null }
                assertNull(auth.currentUser)
                main { model.submit("", email, "Test-only-123!") }
                waitFor(model) { !it.busy && it.uid == uid }
                main { model.verifySession() }
                waitFor(model) { !it.busy && it.uid == uid }
                main { model.signOut() }
            }
            // An interrupted registration leaves an account without a profile.
            val orphan = Tasks.await(auth.createUserWithEmailAndPassword("orphan-$stamp@example.test", "Test-only-123!"), 15, TimeUnit.SECONDS).user!!
            waitFor(model) { !it.busy && model.needsProfile }
            assertNull(model.state.value?.uid)
            main { model.completeProfile("Recovered") }
            waitFor(model) { !it.busy && it.uid == orphan.uid }
            Tasks.await(orphan.delete(), 15, TimeUnit.SECONDS)
            waitFor(model) { !it.busy && it.uid == null }
        } finally { main { model.signOut(); store.clear() } }
    }

    @Test fun registrationFormRoutesToChatsAndRestoresSession() {
        assumeTrue(BuildConfig.USE_EMULATORS)
        app.backend.auth?.signOut()
        lateinit var model: AuthViewModel
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                model = ViewModelProvider(activity)[AuthViewModel::class.java]
                activity.findViewById<View>(R.id.switchMode).performClick()
                activity.findViewById<EditText>(R.id.name).setText("UI Tester")
                activity.findViewById<EditText>(R.id.email).setText("ui-${System.currentTimeMillis()}@example.test")
                activity.findViewById<EditText>(R.id.password).setText("Test-only-123!")
                activity.findViewById<EditText>(R.id.confirm).setText("Test-only-123!")
                activity.findViewById<View>(R.id.submit).performClick()
                assertFalse(activity.findViewById<View>(R.id.submit).isEnabled)
            }
            waitFor(model) { !it.busy && it.uid != null }
            scenario.recreate()
            waitFor(model) { !it.busy && it.uid != null }
            scenario.onActivity { activity ->
                assertEquals("Chats", activity.findViewById<TextView>(R.id.title).text.toString())
            }
        }
        // A fresh Activity/ViewModel restores the Firebase session rather than keeping UI state.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { model = ViewModelProvider(it)[AuthViewModel::class.java] }
            waitFor(model) { !it.busy && it.uid != null }
            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.signOut).performClick()
                assertEquals(activity.getString(R.string.welcome), activity.findViewById<TextView>(R.id.title).text.toString())
                assertEquals("", activity.findViewById<EditText>(R.id.email).text.toString())
            }
        }
        assertNull(app.backend.auth?.currentUser)
    }

    @Test fun formsValidateAndNeverSavePasswordsOnRotation() {
        app.backend.auth?.signOut()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.switchMode).performClick()
                activity.findViewById<EditText>(R.id.name).setText("Ada")
                activity.findViewById<EditText>(R.id.email).setText("invalid")
                activity.findViewById<EditText>(R.id.password).setText("secret123")
                activity.findViewById<EditText>(R.id.confirm).setText("different")
                activity.findViewById<View>(R.id.submit).performClick()
                assertNotNull(activity.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.emailLayout).error)
                assertNotNull(activity.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.confirmLayout).error)
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertEquals("Create account", activity.findViewById<TextView>(R.id.title).text.toString())
                assertEquals("", activity.findViewById<EditText>(R.id.password).text.toString())
                assertEquals("", activity.findViewById<EditText>(R.id.confirm).text.toString())
            }
        }
    }
}
