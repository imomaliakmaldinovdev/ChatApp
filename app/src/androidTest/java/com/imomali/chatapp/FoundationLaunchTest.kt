package com.imomali.chatapp

import android.widget.TextView
import android.os.PowerManager
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.Source
import com.imomali.chatapp.data.FirebaseBackend
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class FoundationLaunchTest {
    private var wakeLock: PowerManager.WakeLock? = null

    @Suppress("DEPRECATION")
    @Before fun keepTestEmulatorAwake() {
        val context = ApplicationProvider.getApplicationContext<ChatApplication>()
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = power.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "ChatApp:instrumentation"
        ).also { it.acquire(120_000) }
    }

    @After fun releaseTestWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    @Test fun launchesAndRecreatesWithoutCrashing() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(activity.findViewById<TextView>(R.id.title).text.isNotBlank())
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertTrue(activity.findViewById<TextView>(R.id.title).text.isNotBlank())
            }
        }
    }

    @Test fun missingConfigKeepsProtectedScreenClosed() {
        val app = ApplicationProvider.getApplicationContext<ChatApplication>()
        assumeTrue(app.backend.state == FirebaseBackend.State.NOT_CONFIGURED)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(activity.getString(R.string.welcome), activity.findViewById<TextView>(R.id.title).text.toString())
            }
        }
    }

    @Test fun emulatorAuthAndServerRoundTrip() {
        assumeTrue(BuildConfig.DEBUG && BuildConfig.USE_EMULATORS)
        val backend = ApplicationProvider.getApplicationContext<ChatApplication>().backend
        assertEquals(FirebaseBackend.State.EMULATOR, backend.state)
        val auth = requireNotNull(backend.auth)
        val db = requireNotNull(backend.database)
        try {
            val uid = requireNotNull(Tasks.await(auth.signInAnonymously(), 15, TimeUnit.SECONDS).user).uid
            val ref = db.collection("profiles").document(uid)
            Tasks.await(ref.set(mapOf("displayName" to "Local test", "searchName" to "local test", "bio" to "")), 15, TimeUnit.SECONDS)
            assertEquals("Local test", Tasks.await(ref.get(Source.SERVER), 15, TimeUnit.SECONDS).getString("displayName"))
        } finally {
            auth.signOut()
        }
        assertNull(auth.currentUser)
    }
}
