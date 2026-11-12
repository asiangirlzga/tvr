package com.example.privatevoice.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.privatevoice.HandsFreeService
import com.example.privatevoice.ListenMode
import com.example.privatevoice.ModelState
import com.example.privatevoice.VoiceEngine
import com.example.privatevoice.access.ControlAccessibilityService

private fun hasMic(c: Context) =
    ContextCompat.checkSelfPermission(c, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceScreen() {
    val context = LocalContext.current
    val model by VoiceEngine.modelState.collectAsStateWithLifecycle()
    val mode by VoiceEngine.mode.collectAsStateWithLifecycle()
    val partial by VoiceEngine.partial.collectAsStateWithLifecycle()
    val heard by VoiceEngine.lastHeard.collectAsStateWithLifecycle()
    val result by VoiceEngine.lastResult.collectAsStateWithLifecycle()

    var micGranted by remember { mutableStateOf(hasMic(context)) }
    var accessOn by remember { mutableStateOf(ControlAccessibilityService.isEnabled(context)) }
    var pendingHandsFree by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        micGranted = hasMic(context)
        accessOn = ControlAccessibilityService.isEnabled(context)
        onPauseOrDispose {}
    }

    fun startService() {
        ContextCompat.startForegroundService(context, Intent(context, HandsFreeService::class.java))
    }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        micGranted = granted[Manifest.permission.RECORD_AUDIO] == true || hasMic(context)
        if (micGranted) {
            if (pendingHandsFree) startService() else VoiceEngine.startOnce()
        }
        pendingHandsFree = false
    }

    fun requestAndStart(handsFree: Boolean) {
        if (hasMic(context)) {
            if (handsFree) startService() else VoiceEngine.startOnce()
        } else {
            pendingHandsFree = handsFree
            val wanted = buildList {
                add(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permissions.launch(wanted.toTypedArray())
        }
    }

    val ready = model is ModelState.Ready
    val listening = mode != ListenMode.OFF

    Scaffold(topBar = { TopAppBar(title = { Text("Private Voice Control") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    "Control your phone by voice. Works with no internet. Nothing leaves your phone.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Setup", style = MaterialTheme.typography.titleMedium)
                        StepRow(
                            done = ready,
                            title = when (model) {
                                ModelState.Loading -> "Preparing speech model (first start only)…"
                                ModelState.Ready -> "Speech model ready (offline)"
                                is ModelState.Error -> (model as ModelState.Error).message
                            },
                            busy = model == ModelState.Loading,
                        )
                        StepRow(done = micGranted, title = "Microphone permission")
                        if (!micGranted) {
                            OutlinedButton(onClick = { permissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) }) {
                                Text("Allow microphone")
                            }
                        }
                        StepRow(done = accessOn, title = "Accessibility service (for Home, Back, scroll, lock…)")
                        if (!accessOn) {
                            Text(
                                "It cannot read your screen. If the switch is greyed out: App info → ⋮ menu → Allow restricted settings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                }) { Text("Open accessibility") }
                                OutlinedButton(onClick = {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                                    )
                                }) { Text("App info") }
                            }
                        }
                    }
                }
            }

            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    LargeFloatingActionButton(
                        onClick = {
                            when {
                                !ready -> Unit
                                mode == ListenMode.ONCE -> VoiceEngine.stop()
                                mode == ListenMode.HANDS_FREE -> Unit
                                else -> requestAndStart(handsFree = false)
                            }
                        },
                        containerColor = if (mode == ListenMode.ONCE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    ) {
                        Icon(
                            if (mode == ListenMode.ONCE) Icons.Rounded.Stop else Icons.Rounded.Mic,
                            contentDescription = if (mode == ListenMode.ONCE) "Stop listening" else "Tap and speak a command",
                            modifier = Modifier.size(36.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        when {
                            !ready -> "Waiting for the speech model…"
                            mode == ListenMode.ONCE -> partial.ifBlank { "Listening… say a command" }
                            mode == ListenMode.HANDS_FREE -> partial.ifBlank { "Listening for “Hey phone …”" }
                            else -> "Tap the mic and speak"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }

            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Hands-free “Hey phone”", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Keeps the microphone on with a visible notification. Say “Hey phone, volume up”. Uses more battery.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = mode == ListenMode.HANDS_FREE,
                            enabled = ready,
                            onCheckedChange = { on ->
                                if (on) requestAndStart(handsFree = true)
                                else {
                                    context.stopService(Intent(context, HandsFreeService::class.java))
                                    VoiceEngine.stop()
                                }
                            },
                        )
                    }
                }
            }

            if (heard.isNotBlank() || result.isNotBlank()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (heard.isNotBlank()) Text("Heard: “$heard”", style = MaterialTheme.typography.bodyMedium)
                            if (result.isNotBlank()) Text(result, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }

            item { CommandsCard() }
            item { PrivacyCard() }
        }
    }
}

@Composable
private fun StepRow(done: Boolean, title: String, busy: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                if (done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = if (done) "Done" else "Not done",
                tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CommandsCard() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Things you can say", style = MaterialTheme.typography.titleMedium)
            listOf(
                "Open YouTube · Open camera · Open settings",
                "Go home · Go back · Recent apps",
                "Open notifications · Quick settings",
                "Scroll down · Scroll up · Swipe left",
                "Volume up · Volume down 3 times · Mute · Max volume",
                "Flashlight on · Flashlight off",
                "Take a screenshot · Lock the phone",
                "Wi-Fi settings · Bluetooth settings · Brightness",
                "What time is it · Battery level · What's the date",
                "Call 9 8 7 6 5 4 3 2 1 0 (opens the dialer)",
                "Stop listening",
            ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            Text(
                "In hands-free mode start with “Hey phone”. English only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PrivacyCard() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Your privacy", style = MaterialTheme.typography.titleMedium)
            listOf(
                "The app has no internet permission, so it cannot send anything anywhere.",
                "Speech is recognised on your phone. Audio is never saved.",
                "No accounts, no ads, no analytics, no cloud backup.",
                "The accessibility service cannot read your screen or what you type.",
                "The microphone is on only while you tap the mic or hands-free mode is on (notification shown).",
            ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
