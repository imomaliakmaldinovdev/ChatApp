package com.imomali.chatapp

import android.content.Context
import android.os.PowerManager
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.imomali.chatapp.data.FirebaseDiscoveryRepository
import com.imomali.chatapp.data.FirebaseMessageRepository
import com.imomali.chatapp.domain.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.*
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class MessagingFlowTest {
    private val app get() = ApplicationProvider.getApplicationContext<ChatApplication>()
    private val auth get() = app.backend.auth!!
    private val db get() = app.backend.database!!
    private lateinit var alice: String
    private lateinit var bob: String
    private lateinit var email: String
    private lateinit var conversation: String
    private lateinit var other: FirebaseApp
    private lateinit var otherAuth: FirebaseAuth
    private lateinit var otherDb: FirebaseFirestore
    private lateinit var repository: FirebaseMessageRepository
    private lateinit var otherRepository: FirebaseMessageRepository
    private var wakeLock: PowerManager.WakeLock? = null
    private fun main(action: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    private fun <T> await(action: ((Result<T>) -> Unit) -> Unit): T {
        val future = CompletableFuture<T>()
        main { action { it.fold(future::complete, future::completeExceptionally) } }
        return future.get(20, TimeUnit.SECONDS)
    }
    private fun waitFor(check: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 20000
        while (System.currentTimeMillis() < deadline) {
            var ready = false
            main { ready = check() }
            if (ready) return
            Thread.sleep(100)
        }
        fail("Messaging did not reach the expected state")
    }
    private fun profile(database: FirebaseFirestore, uid: String, name: String) {
        Tasks.await(database.collection("profiles").document(uid).set(mapOf(
            "displayName" to name, "searchName" to name.lowercase(), "bio" to "")), 15, TimeUnit.SECONDS)
    }
    @Suppress("DEPRECATION")
    @Before fun setup() {
        assumeTrue(BuildConfig.USE_EMULATORS)
        wakeLock = (app.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "ChatApp:messaging-test")
            .also { it.acquire(180000) }
        auth.signOut()
        email = "messaging-${UUID.randomUUID()}@example.test"
        alice = Tasks.await(auth.createUserWithEmailAndPassword(email, "Test-only-123!"), 15, TimeUnit.SECONDS).user!!.uid
        profile(db, alice, "Alice")
        val id = UUID.randomUUID().toString()
        other = FirebaseApp.initializeApp(app, FirebaseOptions.Builder().setProjectId("demo-chat-app")
            .setApplicationId("1:123456789:android:messaging").setApiKey("demo-messaging-$id").build(), id)
        otherAuth = FirebaseAuth.getInstance(other).apply { useEmulator(BuildConfig.EMULATOR_HOST, 9099) }
        otherDb = FirebaseFirestore.getInstance(other).apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder().setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
            useEmulator(BuildConfig.EMULATOR_HOST, 8080)
        }
        bob = Tasks.await(otherAuth.signInAnonymously(), 15, TimeUnit.SECONDS).user!!.uid
        profile(otherDb, bob, "Bob")
        repository = FirebaseMessageRepository(auth, db)
        otherRepository = FirebaseMessageRepository(otherAuth, otherDb)
        conversation = await { FirebaseDiscoveryRepository(db).start(alice, bob, it) }
    }
    @After fun cleanup() {
        if (BuildConfig.USE_EMULATORS) {
            Tasks.await(db.enableNetwork(), 15, TimeUnit.SECONDS)
            auth.signOut()
            if (::other.isInitialized) {
                Tasks.await(otherDb.terminate(), 15, TimeUnit.SECONDS)
                otherAuth.signOut(); other.delete()
            }
        }
        wakeLock?.let { if (it.isHeld) it.release() }
    }
    @Test fun twoUsersReceiveWithoutReloadAndRetrySurvivesNewSession() {
        val first = AtomicReference<MessageHistory>(); val second = AtomicReference<MessageHistory>()
        lateinit var one: Subscription; lateinit var two: Subscription
        main {
            one = repository.observeRecent(conversation) { it.onSuccess(first::set) }
            two = otherRepository.observeRecent(conversation) { it.onSuccess(second::set) }
        }
        try {
            await<Unit> { repository.send(conversation, "one", "Hello Bob", it) }
            waitFor { first.get()?.messages?.size == 1 && second.get()?.messages?.size == 1 && second.get()?.fromCache == false }
            await<Unit> { otherRepository.send(conversation, "two", "Hi Alice\nTwo lines", it) }
            waitFor { first.get()?.messages?.size == 2 && first.get()?.messages?.all { !it.pending } == true }
            await<Unit> { repository.send(conversation, "one", "Hello Bob", it) }
            assertEquals(2, Tasks.await(db.collection("conversations").document(conversation).collection("messages").get(Source.SERVER), 15, TimeUnit.SECONDS).size())
            assertEquals(listOf("Hello Bob", "Hi Alice\nTwo lines"), first.get().messages.map { it.text })
            assertTrue(first.get().messages.all { it.createdAtMillis > 0 })
        } finally { main { one.close(); two.close() } }
        auth.signOut()
        Tasks.await(auth.signInWithEmailAndPassword(email, "Test-only-123!"), 15, TimeUnit.SECONDS)
        val restored = AtomicReference<MessageHistory>()
        lateinit var newSession: Subscription
        main { newSession = FirebaseMessageRepository(auth, db).observeRecent(conversation) { it.onSuccess(restored::set) } }
        try { waitFor { restored.get()?.messages?.size == 2 && restored.get()?.fromCache == false } }
        finally { main { newSession.close() } }
    }
    @Test fun historyIsBoundedAndIdentifierConflictsCannotOverwriteMessages() {
        val messages = db.collection("conversations").document(conversation).collection("messages")
        val batch = db.batch()
        for (index in 0..60) batch.set(messages.document("m%02d".format(index)), mapOf(
            "senderId" to alice, "text" to "Message $index", "createdAt" to FieldValue.serverTimestamp()))
        Tasks.await(batch.commit(), 15, TimeUnit.SECONDS)
        val history = AtomicReference<MessageHistory>()
        lateinit var listener: Subscription
        main { listener = repository.observeRecent(conversation) { it.onSuccess(history::set) } }
        try {
            waitFor { history.get()?.messages?.size == 50 && history.get()?.fromCache == false }
            assertEquals(50, history.get().messages.distinctBy { it.id }.size)
            assertEquals("m11", history.get().messages.first().id)
            assertEquals("m60", history.get().messages.last().id)
            val conflict = CompletableFuture<Result<Unit>>()
            main { repository.send(conversation, "m60", "replacement") { conflict.complete(it) } }
            assertTrue(conflict.get(20, TimeUnit.SECONDS).isFailure)
            assertEquals("Message 60", Tasks.await(messages.document("m60").get(Source.SERVER), 15, TimeUnit.SECONDS).getString("text"))
        } finally { main { listener.close() } }
    }
    @Test fun offlineFailureRetainsDraftAndRetryWritesExactlyOnce() {
        val store = androidx.lifecycle.ViewModelStore()
        lateinit var chat: ChatViewModel
        main {
            chat = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))[ChatViewModel::class.java]
            chat.open(alice, conversation)
        }
        try {
            waitFor { !chat.state.value!!.loading && !chat.state.value!!.fromCache }
            Tasks.await(db.disableNetwork(), 15, TimeUnit.SECONDS)
            main { chat.edit("Keep this draft"); chat.send() }
            waitFor { chat.state.value!!.outgoing?.status == SendStatus.FAILED }
            assertEquals("Keep this draft", chat.state.value!!.draft)
            val id = chat.state.value!!.outgoing!!.id
            Tasks.await(db.enableNetwork(), 15, TimeUnit.SECONDS)
            main { chat.send() }
            waitFor { chat.state.value!!.outgoing == null && chat.state.value!!.draft.isEmpty() }
            val saved = Tasks.await(db.collection("conversations").document(conversation).collection("messages").get(Source.SERVER), 15, TimeUnit.SECONDS)
            assertEquals(1, saved.size()); assertEquals(id, saved.documents.single().id)
        } finally { main { store.clear() } }
    }
    @Test fun composerSendsMultilineAndKeepsDraftAcrossRotationAndClearsOnLogout() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var discovery: DiscoveryViewModel; lateinit var chat: ChatViewModel
            scenario.onActivity { activity ->
                discovery = ViewModelProvider(activity)[DiscoveryViewModel::class.java]
                chat = ViewModelProvider(activity)[ChatViewModel::class.java]
            }
            waitFor { discovery.state.value!!.chats.size == 1 && !discovery.state.value!!.loadingChats }
            main { discovery.open(discovery.state.value!!.chats.single()) }
            waitFor { chat.state.value!!.conversationId == conversation && !chat.state.value!!.loading && !chat.state.value!!.fromCache }
            scenario.onActivity { activity ->
                val input = activity.findViewById<EditText>(R.id.messageInput)
                val button = activity.findViewById<View>(R.id.sendMessage)
                val editor = EditorInfo()
                input.onCreateInputConnection(editor)
                assertEquals(EditorInfo.IME_ACTION_SEND, editor.imeOptions and EditorInfo.IME_MASK_ACTION)
                assertEquals(0, editor.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION)
                assertTrue(editor.imeOptions and EditorInfo.IME_FLAG_NO_EXTRACT_UI != 0)
                input.setText("  \n "); assertFalse(button.isEnabled)
                input.setText("x".repeat(4001)); assertEquals(4000, input.text.length)
                input.setText("Hello Bob\nSent from Android")
                input.onEditorAction(EditorInfo.IME_ACTION_SEND)
                assertFalse(button.isEnabled)
            }
            waitFor { chat.state.value!!.draft.isEmpty() && chat.state.value!!.messages.any { it.text == "Hello Bob\nSent from Android" && !it.pending } }
            await<Unit> { otherRepository.send(conversation, "reply", "Hi Alice! This longer reply should wrap naturally inside an incoming message bubble.", it) }
            waitFor { chat.state.value!!.messages.size == 2 }
            scenario.onActivity { it.findViewById<EditText>(R.id.messageInput).setText("Unsent draft") }
            scenario.recreate()
            lateinit var authModel: AuthViewModel
            scenario.onActivity { authModel = ViewModelProvider(it)[AuthViewModel::class.java] }
            waitFor { chat.state.value!!.messages.size == 2 && !chat.state.value!!.fromCache && authModel.state.value?.uid == alice }
            lateinit var composer: EditText
            scenario.onActivity { activity ->
                val input = activity.findViewById<EditText>(R.id.messageInput)
                composer = input
                assertEquals("Unsent draft", input.text.toString())
                input.requestFocus()
                (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }
            waitFor {
                val insets = androidx.core.view.ViewCompat.getRootWindowInsets(composer)
                val height = insets?.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())?.bottom ?: 0
                val bounds = android.graphics.Rect()
                composer.getGlobalVisibleRect(bounds)
                height > 100 && bounds.bottom <= composer.rootView.height - height
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(500, 5000)
            // Artifact for manual layout review while the test is running.
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            java.io.File(app.getExternalFilesDir(null), "thursday-chat.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
            scenario.onActivity { activity ->
                assertTrue(activity.findViewById<View>(R.id.sendMessage).isShown)
                val visible = android.graphics.Rect()
                activity.window.decorView.getWindowVisibleDisplayFrame(visible)
                val sendBounds = android.graphics.Rect()
                val sendButton = activity.findViewById<View>(R.id.sendMessage)
                assertTrue(sendButton.getGlobalVisibleRect(sendBounds))
                assertEquals("Send button must not be clipped", sendButton.height, sendBounds.height())
                val composerBounds = android.graphics.Rect()
                assertTrue(composer.getGlobalVisibleRect(composerBounds))
                assertEquals("Composer must not be clipped", composer.height, composerBounds.height())
                assertTrue("Composer must remain above the keyboard", sendBounds.bottom <= visible.bottom)
                activity.findViewById<View>(R.id.signOut).performClick()
            }
            assertEquals("", chat.state.value!!.draft); assertTrue(chat.state.value!!.messages.isEmpty())
        }
    }
}
