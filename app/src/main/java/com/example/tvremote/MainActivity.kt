package com.example.tvremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tvremote.ui.TvRemoteApp
import com.example.tvremote.ui.TvRemoteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TvRemoteTheme {
                val vm: RemoteViewModel = viewModel()
                TvRemoteApp(vm)
            }
        }
    }
}
