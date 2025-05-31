package com.surendramaran.yolov8tflite

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import kotlin.jvm.java
import android.window.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Sistema Splash API para Android 12+
        installSplashScreen()

        super.onCreate(savedInstanceState)
        startActivity(Intent(this, IntroActivity::class.java))
        finish()
    }
}
