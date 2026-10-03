package com.imomali.chatapp

import android.content.Context
import android.os.PowerManager
import android.view.View
import android.widget.EditText
import android.widget.TextView
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
import com.imomali.chatapp.domain.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.*
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class DiscoveryFlowTest {
    private val app get() = ApplicationProvider.getApplicationContext<ChatApplication>()
    private lateinit var db: FirebaseFirestore
    private lateinit var repo: FirebaseDiscoveryRepository
    private lateinit var me: String
    private lateinit var bob: UserProfile
    private lateinit var eve: UserProfile
    private lateinit var bobDb: FirebaseFirestore
    private val clients = mutableListOf<FirebaseApp>()
    private var wakeLock: PowerManager.WakeLock? = null
    private val prefix = "Wednesday${UUID.randomUUID().toString().take(8)}"
    private fun main(action: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    private fun screenshot(name: String) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val file = java.io.File(app.getExternalFilesDir(null), "$name.png")
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun <T> await(block: ((Result<T>) -> Unit) -> Unit): T {
        val future = CompletableFuture<T>()
        main { block { it.fold(future::complete, future::completeExceptionally) } }
        return future.get(20, TimeUnit.SECONDS)
    }
    private fun waitFor(model: DiscoveryViewModel, check: (DiscoveryState) -> Boolean) {
        val deadline = System.currentTimeMillis() + 20000
        while (System.currentTimeMillis() < deadline) {
            var ready = false
            main { ready = check(model.state.value!!) }
            if (ready) return
            Thread.sleep(100)
        }
        fail("Discovery state not reached: ${model.state.value}")
    }
    private fun profile(database: FirebaseFirestore, uid: String, name: String): UserProfile {
        val value = UserProfile(uid, name, DiscoveryPolicy.query(name), "")
        Tasks.await(database.collection("profiles").document(uid).set(mapOf("displayName" to value.displayName,
            "searchName" to value.searchName, "bio" to value.bio)), 15, TimeUnit.SECONDS)
        return value
    }
    private fun client(name: String): Pair<FirebaseFirestore, UserProfile> {
        val id = UUID.randomUUID().toString()
        val firebase = FirebaseApp.initializeApp(app, FirebaseOptions.Builder().setProjectId("demo-chat-app")
            .setApplicationId("1:123456789:android:discovery").setApiKey("demo-discovery-$id").build(), id)
        clients += firebase
        val auth = FirebaseAuth.getInstance(firebase).apply { useEmulator(BuildConfig.EMULATOR_HOST, 9099) }
        val database = FirebaseFirestore.getInstance(firebase).apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder().setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
            useEmulator(BuildConfig.EMULATOR_HOST, 8080)
        }
        val user = Tasks.await(auth.signInAnonymously(), 15, TimeUnit.SECONDS).user!!
        return database to profile(database, user.uid, name)
    }
    @Suppress("DEPRECATION")
    @Before fun setup() {
        assumeTrue(BuildConfig.USE_EMULATORS)
        wakeLock = (app.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "ChatApp:discovery-test")
            .also { it.acquire(120000) }
        app.backend.auth!!.signOut()
        me = Tasks.await(app.backend.auth!!.signInAnonymously(), 15, TimeUnit.SECONDS).user!!.uid
        db = app.backend.database!!; repo = FirebaseDiscoveryRepository(db)
        profile(db, me, "$prefix Me")
        val second = client("$prefix Bob"); bobDb = second.first; bob = second.second
        eve = client("$prefix Eve").second
    }
    @After fun cleanup() {
        if (BuildConfig.USE_EMULATORS && ::db.isInitialized) Tasks.await(db.enableNetwork(), 15, TimeUnit.SECONDS)
        app.backend.auth?.signOut()
        clients.forEach { firebase ->
            Tasks.await(FirebaseFirestore.getInstance(firebase).terminate(), 15, TimeUnit.SECONDS)
            FirebaseAuth.getInstance(firebase).signOut(); firebase.delete()
        }
        wakeLock?.let { if (it.isHeld) it.release() }
    }
    @Test fun searchProfilesAndConcurrentConversationCreationUseRealRules() {
        val found = await<List<UserProfile>> { repo.search(me, prefix.uppercase(), it) }
        assertEquals(setOf(bob.uid, eve.uid), found.map { it.uid }.toSet())
        assertTrue(await<List<UserProfile>> { repo.search(me, "missing-$prefix", it) }.isEmpty())
        assertEquals(bob, await<UserProfile?> { repo.profile(bob.uid, it) })
        val first = CompletableFuture<String>(); val second = CompletableFuture<String>()
        main {
            repo.start(me, bob.uid) { it.fold(first::complete, first::completeExceptionally) }
            FirebaseDiscoveryRepository(bobDb).start(bob.uid, me) { it.fold(second::complete, second::completeExceptionally) }
        }
        val id = first.get(20, TimeUnit.SECONDS)
        assertEquals(id, second.get(20, TimeUnit.SECONDS))
        assertEquals(id, await<String> { repo.start(me, bob.uid, it) })
        assertEquals(1, Tasks.await(db.collection("conversations").whereArrayContains("memberIds", me).get(Source.SERVER), 15, TimeUnit.SECONDS).size())
    }
    @Test fun liveRowsUpdateAndReorderWithoutLeakingUnrelatedConversations() {
        val id = await<String> { repo.start(me, bob.uid, it) }
        val other = await<String> { repo.start(me, eve.uid, it) }
        await<String> { FirebaseDiscoveryRepository(bobDb).start(bob.uid, eve.uid, it) }
        val store = androidx.lifecycle.ViewModelStore()
        lateinit var model: DiscoveryViewModel
        main { model = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))[DiscoveryViewModel::class.java]; model.bind(me) }
        try {
            waitFor(model) { !it.loadingChats && it.chats.size == 2 }
            assertEquals(other, model.state.value!!.chats.first().conversation.id)
            Tasks.await(bobDb.collection("conversations").document(id).collection("messages").document("preview").set(
                mapOf("senderId" to bob.uid, "text" to "Latest preview", "createdAt" to FieldValue.serverTimestamp())), 15, TimeUnit.SECONDS)
            waitFor(model) { it.chats.firstOrNull()?.lastMessage?.text == "Latest preview" }
            assertEquals(id, model.state.value!!.chats.first().conversation.id)
            main { model.bind(null) }
            assertTrue(model.state.value!!.chats.isEmpty())
            Thread.sleep(400)
            assertTrue(model.state.value!!.chats.isEmpty())
        } finally { main { store.clear() } }
    }
    @Test fun offlineConversationTimesOutAndRetryReusesCanonicalId() {
        val store = androidx.lifecycle.ViewModelStore()
        lateinit var model: DiscoveryViewModel
        main { model = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))[DiscoveryViewModel::class.java]; model.bind(me); model.showSearch() }
        try {
            waitFor(model) { !it.loadingChats }
            Tasks.await(db.disableNetwork(), 15, TimeUnit.SECONDS)
            main { model.start(bob); model.start(bob) }
            waitFor(model) { !it.starting && it.error != null }
            assertEquals(DiscoveryScreen.SEARCH, model.state.value!!.screen)
            assertTrue(model.state.value!!.error!!.contains("not confirmed"))
            Tasks.await(db.enableNetwork(), 15, TimeUnit.SECONDS)
            main { model.retry() }
            waitFor(model) { !it.starting && it.screen == DiscoveryScreen.CONVERSATION }
            assertEquals(DiscoveryPolicy.conversationId(me, bob.uid), model.state.value!!.conversationId)
            assertEquals(1, Tasks.await(db.collection("conversations").whereArrayContains("memberIds", me).get(Source.SERVER), 15, TimeUnit.SECONDS).size())
        } finally { main { store.clear() } }
    }
    @Test fun screensSearchOpenProfileRestoreAndClearAfterLogout() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: DiscoveryViewModel
            scenario.onActivity { model = ViewModelProvider(it)[DiscoveryViewModel::class.java] }
            waitFor(model) { !it.loadingChats }
            main { model.showSearch() }
            scenario.onActivity { it.findViewById<EditText>(R.id.userSearch).setText("$prefix Bob") }
            waitFor(model) { !it.searching && it.results.size == 1 }
            screenshot("wednesday-search")
            scenario.onActivity {
                val list = it.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.discoveryList)
                assertEquals(1, list.adapter!!.itemCount)
                list.findViewHolderForAdapterPosition(0)!!.itemView.performClick()
            }
            waitFor(model) { it.screen == DiscoveryScreen.CONVERSATION }
            assertEquals(DiscoveryPolicy.conversationId(me, bob.uid), model.state.value!!.conversationId)
            main { model.showProfile(bob.uid) }
            waitFor(model) { !it.loadingProfile && it.profile?.uid == bob.uid }
            screenshot("wednesday-profile")
            scenario.recreate()
            lateinit var authModel: AuthViewModel
            scenario.onActivity { authModel = ViewModelProvider(it)[AuthViewModel::class.java] }
            waitFor(model) { !it.loadingChats && it.screen == DiscoveryScreen.PROFILE && authModel.state.value?.uid == me }
            scenario.onActivity { assertEquals("Profile", it.findViewById<TextView>(R.id.title).text.toString()) }
            main { model.back(); model.back(); model.showOwnProfile() }
            waitFor(model) { !it.loadingProfile && it.profile?.uid == me }
            main { model.back(); model.showSearch(); model.search("$prefix Bob"); model.search("missing-$prefix") }
            waitFor(model) { !it.searching && it.query.startsWith("missing") }
            assertTrue(model.state.value!!.results.isEmpty())
            // Logout also cancels a debounced request before it can repopulate private state.
            main { model.search(prefix) }
            scenario.onActivity { it.findViewById<View>(R.id.signOut).performClick() }
            Thread.sleep(500)
            assertTrue(model.state.value!!.results.isEmpty())
            assertEquals("", model.state.value!!.query)
        }
    }
}
