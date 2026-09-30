package com.imomali.chatapp

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.Source
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/** Exercises the deployed emulator rules through real Android SDK requests.
 * Each test has new users and document IDs, so no global reset or admin bypass is needed.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreRulesTest {
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private data class Client(val app: FirebaseApp, val auth: FirebaseAuth, val db: FirebaseFirestore) {
        val uid: String get() = requireNotNull(auth.currentUser).uid
    }
    private val clients = mutableListOf<Client>()
    private lateinit var alice: Client
    private lateinit var bob: Client
    private lateinit var eve: Client
    private lateinit var signedOut: Client
    private lateinit var conversationId: String

    private fun <T> await(task: Task<T>): T = Tasks.await(task, 15, TimeUnit.SECONDS)

    private fun client(signedIn: Boolean): Client {
        // Never use resource/client configuration here: every request targets the local demo project.
        val app = FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext<ChatApplication>(),
            FirebaseOptions.Builder().setProjectId("demo-chat-app")
                .setApplicationId("1:123456789:android:rules-test")
                // Auth's emulator registry is keyed by API key; isolate disposable clients
                // so reconfiguration cannot reference a previous test's deleted FirebaseApp.
                .setApiKey("demo-rules-${UUID.randomUUID()}").build(),
            "rules-${UUID.randomUUID()}"
        )
        val auth = FirebaseAuth.getInstance(app)
        auth.useEmulator(BuildConfig.EMULATOR_HOST, 9099)
        val db = FirebaseFirestore.getInstance(app)
        db.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
        db.useEmulator(BuildConfig.EMULATOR_HOST, 8080)
        val client = Client(app, auth, db)
        clients.add(client)
        if (signedIn) {
            await(auth.signInAnonymously())
            await(db.collection("profiles").document(client.uid).set(
                mapOf("displayName" to "Tester", "searchName" to "tester", "bio" to "")
            ))
        }
        return client
    }

    private fun pair(first: String, second: String) = listOf(first, second).sorted()
    private fun id(first: String, second: String) = pair(first, second).joinToString(":")
    private fun conversation(first: String, second: String): Map<String, Any> = mapOf(
        "memberIds" to pair(first, second), "createdAt" to FieldValue.serverTimestamp()
    )
    private fun message(sender: String, text: String = "Hello"): Map<String, Any> = mapOf(
        "senderId" to sender, "text" to text, "createdAt" to FieldValue.serverTimestamp()
    )
    private fun messages(client: Client) = client.db.collection("conversations")
        .document(conversationId).collection("messages")

    private fun denied(task: Task<*>) {
        try {
            await(task)
            fail("Expected the Firestore server to deny this request")
        } catch (failure: ExecutionException) {
            val cause = failure.cause
            assertTrue("Expected Firestore permission denial, got $cause", cause is FirebaseFirestoreException)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED,
                (cause as FirebaseFirestoreException).code)
        }
    }

    @Suppress("DEPRECATION")
    @Before fun setUp() {
        assumeTrue("Local Firebase emulator mode is required", BuildConfig.DEBUG && BuildConfig.USE_EMULATORS)
        val context = ApplicationProvider.getApplicationContext<ChatApplication>()
        val power = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        wakeLock = power.newWakeLock(
            android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "ChatApp:rules-test"
        ).also { it.acquire(120_000) }
        alice = client(true)
        bob = client(true)
        eve = client(true)
        signedOut = client(false)
        conversationId = id(alice.uid, bob.uid)
        await(alice.db.collection("conversations").document(conversationId)
            .set(conversation(alice.uid, bob.uid)))
        await(messages(alice).document("seed").set(message(alice.uid)))
    }

    @After fun tearDown() {
        // Release SDK resources even if a test or its fixture setup fails.
        try {
            clients.forEach {
                try { await(it.db.terminate()) } finally { it.auth.signOut(); it.app.delete() }
            }
        } finally {
            clients.clear()
            wakeLock?.let { if (it.isHeld) it.release() }
        }
    }

    @Test fun participantsCanReadAndSendText() {
        assertEquals("Hello", await(messages(bob).document("seed").get(Source.SERVER)).getString("text"))
        await(messages(bob).document("reply").set(message(bob.uid, "Reply")))
        assertEquals("Reply", await(messages(alice).document("reply").get(Source.SERVER)).getString("text"))
    }

    @Test fun nonMemberAndSignedOutCannotReadOrSend() {
        for (client in listOf(eve, signedOut)) {
            denied(client.db.collection("conversations").document(conversationId).get(Source.SERVER))
            denied(messages(client).document("seed").get(Source.SERVER))
            denied(messages(client).document("attack").set(message(client.auth.currentUser?.uid ?: alice.uid)))
        }
    }

    @Test fun senderSpoofingBlankAndOversizedMessagesAreDenied() {
        val ref = messages(alice).document("invalid")
        denied(ref.set(message(bob.uid)))
        for (text in listOf("", "   ", "\n", "x".repeat(4001))) denied(ref.set(message(alice.uid, text)))
        await(ref.set(message(alice.uid, "x".repeat(4000))))
        assertEquals(4000, await(ref.get(Source.SERVER)).getString("text")?.length)
    }

    @Test fun multilineMessagesArePermitted() {
        val ref = messages(alice).document("multiline")
        await(ref.set(message(alice.uid, "Hello\nBob")))
        assertEquals("Hello\nBob", await(ref.get(Source.SERVER)).getString("text"))
    }

    @Test fun messagesAreImmutableAndDuplicateIdsCannotOverwrite() {
        val ref = messages(alice).document("seed")
        denied(ref.set(message(alice.uid, "Changed")))
        denied(ref.update("text", "Changed"))
        denied(ref.delete())
        assertEquals("Hello", await(ref.get(Source.SERVER)).getString("text"))
    }

    @Test fun onlyProfileOwnerCanWriteAndPrivateFieldsAreRejected() {
        val ref = alice.db.collection("profiles").document(alice.uid)
        await(ref.update("bio", "Hi"))
        assertEquals("Hi", await(ref.get(Source.SERVER)).getString("bio"))
        denied(bob.db.collection("profiles").document(alice.uid).update("bio", "Forged"))
        denied(ref.update("email", "private@example.test"))
        denied(signedOut.db.collection("profiles").document(alice.uid).get(Source.SERVER))
    }

    @Test fun canonicalPairRequiresMembershipProfilesAndValidId() {
        val data = conversation(alice.uid, eve.uid)
        val newId = id(alice.uid, eve.uid)
        denied(bob.db.collection("conversations").document(newId).set(data))
        denied(alice.db.collection("conversations").document("random-${UUID.randomUUID()}").set(data))
        val missing = "missing-${UUID.randomUUID()}"
        denied(alice.db.collection("conversations").document(id(alice.uid, missing))
            .set(conversation(alice.uid, missing)))
        await(alice.db.collection("conversations").document(newId).set(data))
        denied(alice.db.collection("conversations").document(conversationId)
            .update("memberIds", pair(alice.uid, eve.uid)))
    }

    @Test fun queriesMustConstrainMembership() {
        val collection = alice.db.collection("conversations")
        denied(collection.get(Source.SERVER))
        val results = await(collection.whereArrayContains("memberIds", alice.uid).get(Source.SERVER))
        assertTrue(results.documents.any { it.id == conversationId })
        assertTrue(results.documents.all { (it.get("memberIds") as List<*>).contains(alice.uid) })
    }

    @Test fun forgedTimestampsExtraFieldsAndUnknownCollectionsAreDenied() {
        denied(messages(alice).document("fake").set(message(alice.uid) + ("createdAt" to Date(0))))
        denied(messages(alice).document("extra").set(message(alice.uid) + ("admin" to true)))
        denied(alice.db.collection("private").document("secrets").set(mapOf("value" to "x")))
        denied(alice.db.collection("conversations").document(id(alice.uid, eve.uid))
            .set(conversation(alice.uid, eve.uid) + ("createdAt" to Date(0))))
    }
}
