package com.example.privatevoice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.privatevoice.ui.PrivateVoiceTheme
import com.example.privatevoice.ui.VoiceScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        VoiceEngine.init(applicationContext)
        setContent {
            PrivateVoiceTheme { VoiceScreen() }
        }
    }

    override fun onStop() {
        // One-shot listening never continues in the background. Hands-free mode has its own service.
        if (VoiceEngine.mode.value == ListenMode.ONCE) VoiceEngine.stop()
        super.onStop()
    }
}
