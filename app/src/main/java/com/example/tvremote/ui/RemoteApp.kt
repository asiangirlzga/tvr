package com.example.tvremote.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tvremote.PairingUi
import com.example.tvremote.RemoteViewModel
import com.example.tvremote.data.PairedTv
import com.example.tvremote.data.TvType
import com.example.tvremote.protocol.ConnectionState
import com.example.tvremote.protocol.TvKey
import com.example.tvremote.voice.TvApps
import com.example.tvremote.voice.VoiceLanguage
import com.example.tvremote.voice.VoiceState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun TvRemoteApp(vm: RemoteViewModel) {
    val active by vm.activeTv.collectAsStateWithLifecycle()
    val pairing by vm.pairing.collectAsStateWithLifecycle()
    val tv = active

    BackHandler(enabled = tv != null) { vm.disconnect() }

    if (tv == null) DeviceScreen(vm) else RemoteScreen(vm, tv)
    PairingDialog(pairing, vm)
}

// ---- TV list -------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceScreen(vm: RemoteViewModel) {
    val paired by vm.paired.collectAsStateWithLifecycle()
    val discovered by vm.discovered.collectAsStateWithLifecycle()
    var manualHost by rememberSaveable { mutableStateOf("") }
    var manualPort by rememberSaveable { mutableStateOf("") }
    val newTvs = discovered.filter { d -> paired.none { it.host == d.host } }

    Scaffold(topBar = { TopAppBar(title = { Text("Voice TV Remote") }) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Keep your phone and TV on the same Wi-Fi network.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (paired.isNotEmpty()) {
                item { SectionTitle("Paired TVs") }
                items(paired, key = { "p-" + it.host }) { tv ->
                    TvRow(tv.name, tv.subtitle, onClick = { vm.connect(tv) }) {
                        IconButton(onClick = { vm.forget(tv) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Forget ${tv.name}")
                        }
                    }
                }
            }
            item { SectionTitle("TVs on your network") }
            if (newTvs.isEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Searching…")
                    }
                }
            }
            items(newTvs, key = { "d-" + it.host }) { tv ->
                TvRow(tv.name, tv.subtitle, onClick = { vm.onDiscoveredTapped(tv) })
            }
            item { SectionTitle("Add by IP address") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = manualHost,
                            onValueChange = { manualHost = it.trim() },
                            label = { Text("TV IP, e.g. 192.168.1.20") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        Button(
                            onClick = { vm.addManual(manualHost, manualPort) },
                            enabled = manualHost.isNotBlank() && (manualPort.isBlank() || manualPort.toIntOrNull() in 1..65535),
                        ) { Text(if (manualPort.isBlank()) "Pair" else "Add") }
                    }
                    OutlinedTextField(
                        value = manualPort,
                        onValueChange = { manualPort = it.filter(Char::isDigit).take(5) },
                        label = { Text("Port (Cloud TV only, leave empty otherwise)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun TvRow(name: String, host: String, onClick: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Tv, contentDescription = null)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(
                    host,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            trailing?.invoke()
        }
    }
}

// ---- Pairing dialog ------------------------------------------------------------------------

@Composable
private fun PairingDialog(state: PairingUi, vm: RemoteViewModel) {
    when (state) {
        PairingUi.Idle -> Unit
        is PairingUi.Connecting -> BusyDialog("Connecting to ${state.name}…", vm)
        PairingUi.Verifying -> BusyDialog("Checking the code…", vm)
        is PairingUi.AwaitingCode -> CodeDialog(state, vm)
        is PairingUi.Failed -> AlertDialog(
            onDismissRequest = vm::cancelPairing,
            title = { Text("Pairing didn't work") },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = vm::cancelPairing) { Text("OK") } },
        )
    }
}

@Composable
private fun BusyDialog(message: String, vm: RemoteViewModel) {
    AlertDialog(
        onDismissRequest = vm::cancelPairing,
        title = { Text("Pairing") },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(16.dp))
                Text(message)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = vm::cancelPairing) { Text("Cancel") } },
    )
}

@Composable
private fun CodeDialog(state: PairingUi.AwaitingCode, vm: RemoteViewModel) {
    var code by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = vm::cancelPairing,
        title = { Text("Enter the code on your TV") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${state.name} is showing a 6-character code. Type it here.")
                OutlinedTextField(
                    value = code,
                    onValueChange = { v -> code = v.uppercase().filter { it in "0123456789ABCDEF" }.take(6) },
                    label = { Text("Code") },
                    singleLine = true,
                    isError = state.error != null,
                    supportingText = { state.error?.let { Text(it) } },
                    textStyle = LocalTextStyle.current.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 24.sp,
                        letterSpacing = 4.sp,
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (code.length == 6) vm.submitCode(code) }),
                )
            }
        },
        confirmButton = {
            Button(onClick = { vm.submitCode(code) }, enabled = code.length == 6) { Text("Pair") }
        },
        dismissButton = { TextButton(onClick = vm::cancelPairing) { Text("Cancel") } },
    )
}

// ---- Remote --------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteScreen(vm: RemoteViewModel, tv: PairedTv) {
    val connection by vm.connection.collectAsStateWithLifecycle()
    val diagnostic by vm.diagnostic.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(tv.name, maxLines = 1)
                        Text(statusText(connection), style = MaterialTheme.typography.labelSmall)
                        if (diagnostic.isNotBlank()) {
                            Text(
                                diagnostic,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = vm::disconnect) {
                        Icon(Icons.Rounded.SwapHoriz, contentDescription = "Switch TV")
                    }
                },
            )
        },
        bottomBar = { VoiceBar(vm) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Capped width keeps the layout comfortable on tablets and TV screens.
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                ConnectionBanner(connection, vm)
                TopRow(vm)
                DPad(vm)
                NavRow(vm)
                ControlsRow(vm)
                AppsRow(vm)
                if (tv.type == TvType.CLOUD_TV) CloudOptions(vm)
            }
        }
    }
}

private fun statusText(state: ConnectionState): String = when (state) {
    ConnectionState.Ready -> "Connected"
    ConnectionState.Connecting -> "Connecting…"
    else -> "Disconnected"
}

@Composable
private fun ConnectionBanner(state: ConnectionState, vm: RemoteViewModel) {
    when (state) {
        ConnectionState.Ready -> Unit
        ConnectionState.Connecting -> LinearProgressIndicator(Modifier.fillMaxWidth())
        ConnectionState.Disconnected -> Banner("Not connected to the TV.", "Connect") { vm.reconnect() }
        is ConnectionState.Failed -> Banner(
            state.message,
            if (state.needsPairing) "Pair again" else "Retry",
        ) { if (state.needsPairing) vm.repair() else vm.reconnect() }
    }
}

@Composable
private fun Banner(message: String, actionLabel: String, onAction: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/**
 * One remote button. Works by touch (with optional press-and-hold repeat) and by D-pad/keyboard
 * focus, so the same screen is usable on a phone and on Android TV.
 */
@Composable
private fun RemoteKey(
    description: String,
    onKey: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    repeating: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val currentOnKey by rememberUpdatedState(onKey)
    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(shape)
            .background(container)
            .then(if (focused) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                val select = event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.DirectionCenter
                if (select && event.type == KeyEventType.KeyUp) {
                    currentOnKey()
                    true
                } else {
                    false
                }
            }
            .focusable()
            .pointerInput(repeating) {
                detectTapGestures(onPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    currentOnKey()
                    val repeater = if (repeating) {
                        scope.launch {
                            delay(450)
                            while (isActive) {
                                currentOnKey()
                                delay(120)
                            }
                        }
                    } else {
                        null
                    }
                    tryAwaitRelease()
                    repeater?.cancel()
                })
            }
            .semantics(mergeDescendants = true) {
                contentDescription = description
                role = Role.Button
                onClick {
                    currentOnKey()
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) { content() }
    }
}

@Composable
private fun TopRow(vm: RemoteViewModel) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteKey(
            "Power", { vm.press(TvKey.POWER) }, Modifier.size(56.dp),
            container = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ) { Icon(Icons.Rounded.PowerSettingsNew, null) }
        RemoteKey("Mute", { vm.press(TvKey.VOLUME_MUTE) }, Modifier.size(56.dp)) {
            Icon(Icons.Rounded.VolumeOff, null)
        }
        RemoteKey(
            "Input", { vm.press(TvKey.TV_INPUT) },
            Modifier.height(56.dp), shape = RoundedCornerShape(28.dp),
        ) { Text("INPUT", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.labelLarge) }
        RemoteKey("Settings", { vm.press(TvKey.SETTINGS) }, Modifier.size(56.dp)) {
            Icon(Icons.Rounded.Settings, null)
        }
    }
}

@Composable
private fun DPad(vm: RemoteViewModel) {
    Box(
        Modifier
            .size(248.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        val none = Color.Transparent
        val ink = MaterialTheme.colorScheme.onSurface
        RemoteKey(
            "Up", { vm.press(TvKey.DPAD_UP) },
            Modifier.align(Alignment.TopCenter).padding(top = 6.dp).size(64.dp),
            container = none, contentColor = ink, repeating = true,
        ) { Icon(Icons.Rounded.KeyboardArrowUp, null, Modifier.size(40.dp)) }
        RemoteKey(
            "Down", { vm.press(TvKey.DPAD_DOWN) },
            Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).size(64.dp),
            container = none, contentColor = ink, repeating = true,
        ) { Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(40.dp)) }
        RemoteKey(
            "Left", { vm.press(TvKey.DPAD_LEFT) },
            Modifier.align(Alignment.CenterStart).padding(start = 6.dp).size(64.dp),
            container = none, contentColor = ink, repeating = true,
        ) { Icon(Icons.Rounded.KeyboardArrowLeft, null, Modifier.size(40.dp)) }
        RemoteKey(
            "Right", { vm.press(TvKey.DPAD_RIGHT) },
            Modifier.align(Alignment.CenterEnd).padding(end = 6.dp).size(64.dp),
            container = none, contentColor = ink, repeating = true,
        ) { Icon(Icons.Rounded.KeyboardArrowRight, null, Modifier.size(40.dp)) }
        RemoteKey(
            "OK", { vm.press(TvKey.DPAD_CENTER) }, Modifier.size(88.dp),
            container = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) { Text("OK", style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
private fun NavRow(vm: RemoteViewModel) {
    val shape = RoundedCornerShape(28.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RemoteKey("Back", { vm.press(TvKey.BACK) }, Modifier.weight(1f).height(56.dp), shape = shape) {
            Icon(Icons.Rounded.ArrowBack, null)
        }
        RemoteKey("Home", { vm.press(TvKey.HOME) }, Modifier.weight(1f).height(56.dp), shape = shape) {
            Icon(Icons.Rounded.Home, null)
        }
        RemoteKey("Menu", { vm.press(TvKey.MENU) }, Modifier.weight(1f).height(56.dp), shape = shape) {
            Icon(Icons.Rounded.Menu, null)
        }
    }
}

@Composable
private fun ControlsRow(vm: RemoteViewModel) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Rocker(
            label = "VOL",
            upDescription = "Volume up", downDescription = "Volume down",
            onUp = { vm.press(TvKey.VOLUME_UP) }, onDown = { vm.press(TvKey.VOLUME_DOWN) },
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                RemoteKey("Rewind", { vm.press(TvKey.MEDIA_REWIND) }, Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.FastRewind, null)
                }
                RemoteKey(
                    "Play or pause", { vm.press(TvKey.MEDIA_PLAY_PAUSE) }, Modifier.size(60.dp),
                    container = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) { Icon(Icons.Rounded.PlayArrow, null, Modifier.size(32.dp)) }
                RemoteKey("Fast forward", { vm.press(TvKey.MEDIA_FAST_FORWARD) }, Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.FastForward, null)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RemoteKey("Previous", { vm.press(TvKey.MEDIA_PREVIOUS) }, Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, null)
                }
                RemoteKey("Next", { vm.press(TvKey.MEDIA_NEXT) }, Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.SkipNext, null)
                }
            }
        }
        Rocker(
            label = "CH",
            upDescription = "Channel up", downDescription = "Channel down",
            onUp = { vm.press(TvKey.CHANNEL_UP) }, onDown = { vm.press(TvKey.CHANNEL_DOWN) },
        )
    }
}

@Composable
private fun Rocker(
    label: String,
    upDescription: String,
    downDescription: String,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    Column(
        Modifier
            .clip(RoundedCornerShape(36.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RemoteKey(upDescription, onUp, Modifier.size(52.dp), repeating = true) { Icon(Icons.Rounded.Add, null) }
        Text(label, Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
        RemoteKey(downDescription, onDown, Modifier.size(52.dp), repeating = true) { Icon(Icons.Rounded.Remove, null) }
    }
}

/** Cloud TV only: the one setting that may be needed if buttons do nothing. */
@Composable
private fun CloudOptions(vm: RemoteViewModel) {
    val names by vm.cloudKeyNames.collectAsStateWithLifecycle()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Send button names", style = MaterialTheme.typography.titleSmall)
            Text(
                "Turn on only if buttons do nothing. The note under the TV name shows the TV's reply.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = names, onCheckedChange = vm::setCloudKeyNames)
    }
}

@Composable
private fun AppsRow(vm: RemoteViewModel) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Apps")
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TvApps.all.forEach { app ->
                AssistChip(onClick = { vm.openApp(app) }, label = { Text(app.label) })
            }
        }
    }
}

// ---- Voice ---------------------------------------------------------------------------------

@Composable
private fun VoiceBar(vm: RemoteViewModel) {
    val voice by vm.voiceState.collectAsStateWithLifecycle()
    val feedback by vm.feedback.collectAsStateWithLifecycle()
    val language by vm.language.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    val listening = voice is VoiceState.Listening

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startListening() else vm.onMicPermissionDenied()
    }

    val onMicClick = {
        if (listening) {
            vm.stopListening()
        } else {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) vm.startListening() else permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val statusLine = when (val v = voice) {
        is VoiceState.Listening -> v.partial.ifBlank { "Listening…" }
        is VoiceState.Error -> v.message
        VoiceState.Idle -> feedback ?: "Tap the mic and say “volume up”, “open YouTube”, “channel 5”…"
    }

    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                statusLine,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Box {
                    AssistChip(
                        onClick = { menuOpen = true },
                        label = { Text(language.label) },
                        leadingIcon = { Icon(Icons.Rounded.Language, null, Modifier.size(18.dp)) },
                    )
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        VoiceLanguage.entries.forEach { l ->
                            DropdownMenuItem(
                                text = { Text(l.label) },
                                onClick = {
                                    vm.setLanguage(l)
                                    menuOpen = false
                                },
                            )
                        }
                    }
                }
                MicButton(listening = listening, onClick = onMicClick)
            }
        }
    }
}

@Composable
private fun MicButton(listening: Boolean, onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "mic pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "mic scale",
    )
    LargeFloatingActionButton(
        onClick = onClick,
        modifier = Modifier.scale(if (listening) scale else 1f),
        containerColor = if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        contentColor = if (listening) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
    ) {
        Icon(
            Icons.Rounded.Mic,
            contentDescription = if (listening) "Stop listening" else "Start voice command",
            modifier = Modifier.size(36.dp),
        )
    }
}
