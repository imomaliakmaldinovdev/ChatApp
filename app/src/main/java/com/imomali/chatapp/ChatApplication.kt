package com.imomali.chatapp
import android.app.Application
import com.imomali.chatapp.data.FirebaseBackend
class ChatApplication : Application() {
    lateinit var backend: FirebaseBackend
        private set
    override fun onCreate() { super.onCreate(); backend = FirebaseBackend.create(this) }
}
