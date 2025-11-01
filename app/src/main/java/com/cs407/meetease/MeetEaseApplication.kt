package com.cs407.meetease

import android.app.Application
import com.google.firebase.FirebaseApp

class MeetEaseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}