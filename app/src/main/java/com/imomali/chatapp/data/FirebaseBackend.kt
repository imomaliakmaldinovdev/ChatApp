package com.imomali.chatapp.data
import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.imomali.chatapp.BuildConfig

class FirebaseBackend private constructor(val auth: FirebaseAuth?, val database: FirebaseFirestore?, val state: State) {
    enum class State { NOT_CONFIGURED, CONFIGURED, EMULATOR, INVALID }
    companion object {
        private fun legacyOptions(): FirebaseOptions? {
            val values = listOf(BuildConfig.FIREBASE_PROJECT_ID, BuildConfig.FIREBASE_APPLICATION_ID, BuildConfig.FIREBASE_API_KEY)
            if (values.all { it.isBlank() }) return null
            require(values.none { it.isBlank() }) { "Incomplete Firebase client configuration" }
            return FirebaseOptions.Builder()
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .setApplicationId(BuildConfig.FIREBASE_APPLICATION_ID)
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .build()
        }

        fun create(context: Context): FirebaseBackend {
            val emulator = BuildConfig.DEBUG && BuildConfig.USE_EMULATORS
            return try {
                val options = when {
                    emulator -> FirebaseOptions.Builder()
                        .setProjectId("demo-chat-app")
                        .setApplicationId("1:123456789:android:demo")
                        .setApiKey("demo-key-not-a-real-credential")
                        .build()
                    else -> FirebaseOptions.fromResource(context) ?: legacyOptions()
                } ?: return FirebaseBackend(null, null, State.NOT_CONFIGURED)
                if (options.projectId.isNullOrBlank() || options.apiKey.isNullOrBlank()) {
                    return FirebaseBackend(null, null, State.INVALID)
                }
                val app = FirebaseApp.initializeApp(context, options, "chat-app")
                val auth = FirebaseAuth.getInstance(app)
                val db = FirebaseFirestore.getInstance(app)
                // Avoid retaining another user's private messages on disk after logout.
                db.firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
                if (emulator) {
                    auth.useEmulator(BuildConfig.EMULATOR_HOST, 9099)
                    db.useEmulator(BuildConfig.EMULATOR_HOST, 8080)
                }
                FirebaseBackend(auth, db, if (emulator) State.EMULATOR else State.CONFIGURED)
            } catch (_: IllegalArgumentException) { FirebaseBackend(null, null, State.INVALID) }
        }
    }
}
