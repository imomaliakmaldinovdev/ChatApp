package com.imomali.chatapp

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.imomali.chatapp.domain.DiscoveryPolicy
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

/** Uses actual form controls and row actions against isolated local Auth/Firestore. */
class FridayJourneyTest {
    private val app get() = ApplicationProvider.getApplicationContext<ChatApplication>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun waitFor(check: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 25000
        while (System.currentTimeMillis() < deadline) {
            var done = false
            instrumentation.runOnMainSync { done = check() }
            if (done) { instrumentation.waitForIdleSync(); return }
            Thread.sleep(100)
        }
        fail("Friday journey did not reach the expected UI state")
    }
    private fun views(root: View): List<View> = listOf(root) +
        if (root is ViewGroup) (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()
    private fun clickText(activity: MainActivity, text: String) {
        val control = views(activity.window.decorView).filterIsInstance<TextView>()
            .single { it.text.toString() == text && it.isShown && it.isClickable }
        assertTrue(control.isEnabled); control.performClick()
    }
    private fun screenshot(name: String) {
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(500, 5000)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        java.io.File(app.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
    @Test fun twoUsersRegisterDiscoverExchangeReloadAndLogoutThroughUi() {
        assumeTrue(BuildConfig.USE_EMULATORS)
        app.backend.auth!!.signOut()
        val unique = UUID.randomUUID().toString().take(8)
        val bobName = "Friday Bob $unique"
        val bobEmail = "bob-$unique@example.test"
        val aliceEmail = "alice-$unique@example.test"
        val password = "Test-only-123!"
        var bobId = ""
        var aliceId = ""
        var conversation = ""
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var auth: AuthViewModel
            lateinit var discovery: DiscoveryViewModel
            lateinit var chat: ChatViewModel
            scenario.onActivity {
                auth = ViewModelProvider(it)[AuthViewModel::class.java]
                discovery = ViewModelProvider(it)[DiscoveryViewModel::class.java]
                chat = ViewModelProvider(it)[ChatViewModel::class.java]
            }
            screenshot("ui-login.png")
            fun register(name: String, email: String) {
                scenario.onActivity { activity ->
                    if (!auth.registering) activity.findViewById<View>(R.id.switchMode).performClick()
                    activity.findViewById<EditText>(R.id.name).setText(name)
                    activity.findViewById<EditText>(R.id.email).setText(email)
                    activity.findViewById<EditText>(R.id.password).setText(password)
                    activity.findViewById<EditText>(R.id.confirm).setText(password)
                }
                screenshot("ui-registration.png")
                scenario.onActivity { it.findViewById<View>(R.id.submit).performClick() }
                waitFor { auth.state.value?.uid != null && !discovery.state.value!!.loadingChats }
                assertTrue(discovery.state.value!!.chats.isEmpty())
            }
            register(bobName, bobEmail)
            bobId = auth.state.value!!.uid!!
            scenario.onActivity { it.findViewById<View>(R.id.signOut).performClick() }
            register("Friday Alice $unique", aliceEmail)
            aliceId = auth.state.value!!.uid!!
            screenshot("friday-empty-chats.png")
            scenario.onActivity { activity ->
                clickText(activity, activity.getString(R.string.new_chat))
                activity.findViewById<EditText>(R.id.userSearch).setText("NoMatch$unique")
            }
            waitFor { !discovery.state.value!!.searching && discovery.state.value!!.results.isEmpty() }
            scenario.onActivity { activity ->
                assertTrue(views(activity.window.decorView).filterIsInstance<TextView>().any { it.isShown && it.text.toString() == activity.getString(R.string.no_users) })
                activity.findViewById<EditText>(R.id.userSearch).setText(bobName)
            }
            waitFor { !discovery.state.value!!.searching && discovery.state.value!!.results.size == 1 }
            screenshot("ui-search.png")
            scenario.onActivity { activity ->
                val row = activity.findViewById<RecyclerView>(R.id.discoveryList).findViewHolderForAdapterPosition(0)!!.itemView
                assertTrue(row.contentDescription.toString().contains(bobName)); row.performClick()
            }
            waitFor { chat.state.value!!.conversationId != null && !chat.state.value!!.loading && !chat.state.value!!.fromCache }
            conversation = chat.state.value!!.conversationId!!
            assertEquals(DiscoveryPolicy.conversationId(aliceId, bobId), conversation)
            scenario.onActivity { clickText(it, it.getString(R.string.view_profile)) }
            waitFor { discovery.state.value!!.screen == DiscoveryScreen.PROFILE && !discovery.state.value!!.loadingProfile }
            screenshot("ui-profile.png")
            scenario.onActivity { clickText(it, it.getString(R.string.back)) }
            waitFor { discovery.state.value!!.screen == DiscoveryScreen.CONVERSATION && !chat.state.value!!.loading }
            scenario.onActivity { activity ->
                val input = activity.findViewById<EditText>(R.id.messageInput)
                input.setText("Hello Bob — Friday end-to-end check")
                activity.findViewById<View>(R.id.sendMessage).performClick()
            }
            waitFor { chat.state.value!!.messages.size == 1 && chat.state.value!!.draft.isEmpty() }
            scenario.onActivity { it.findViewById<View>(R.id.signOut).performClick() }
            assertTrue(chat.state.value!!.messages.isEmpty())
            scenario.onActivity { activity ->
                if (auth.registering) activity.findViewById<View>(R.id.switchMode).performClick()
                activity.findViewById<EditText>(R.id.email).setText(bobEmail)
                activity.findViewById<EditText>(R.id.password).setText(password)
                activity.findViewById<View>(R.id.submit).performClick()
            }
            waitFor { auth.state.value?.uid == bobId && discovery.state.value!!.chats.size == 1 && !discovery.state.value!!.loadingChats }
            screenshot("ui-chats.png")
            scenario.onActivity { it.findViewById<RecyclerView>(R.id.discoveryList).findViewHolderForAdapterPosition(0)!!.itemView.performClick() }
            waitFor { chat.state.value!!.messages.size == 1 && !chat.state.value!!.fromCache }
            scenario.onActivity { activity ->
                activity.findViewById<EditText>(R.id.messageInput).setText("Hi Alice!\nReply persisted from Bob.")
                activity.findViewById<View>(R.id.sendMessage).performClick()
            }
            waitFor { chat.state.value!!.messages.size == 2 && chat.state.value!!.draft.isEmpty() }
            scenario.recreate()
            waitFor { auth.state.value?.uid == bobId && chat.state.value!!.messages.size == 2 && !chat.state.value!!.fromCache }
            scenario.onActivity { activity ->
                val composer = activity.findViewById<EditText>(R.id.messageInput)
                val bounds = Rect()
                assertTrue(composer.getGlobalVisibleRect(bounds))
                assertEquals(composer.height, bounds.height())
                val history = activity.findViewById<RecyclerView>(R.id.messageHistory)
                assertTrue("History must retain readable space", history.height >= (48 * activity.resources.displayMetrics.density).toInt())
                assertTrue(history.findViewHolderForAdapterPosition(1)!!.itemView.contentDescription.toString().contains("You:"))
            }
            screenshot("friday-conversation.png")
            scenario.onActivity { it.findViewById<View>(R.id.signOut).performClick() }
            assertNull(app.backend.auth!!.currentUser)
            assertTrue(chat.state.value!!.messages.isEmpty())
        }
    }
}
