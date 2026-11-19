package com.example.ai_travel_assistant

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

class TravelApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        val appCheck = FirebaseAppCheck.getInstance()
        appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
        appCheck.getAppCheckToken(false)
            .addOnSuccessListener { result ->
                Log.d("APP_CHECK", "Token received successfully")
                Log.d("APP_CHECK", "Token length: ${result.token.length}")
            }
            .addOnFailureListener { exception ->
                Log.e("APP_CHECK", "Failed to get token", exception)
            }
    }
}
