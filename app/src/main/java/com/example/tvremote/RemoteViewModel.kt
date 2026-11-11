package com.example.tvremote

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tvremote.data.DeviceStore
import com.example.tvremote.data.PairedTv
import com.example.tvremote.discovery.DiscoveredTv
import com.example.tvremote.discovery.TvDiscovery
import com.example.tvremote.protocol.CertStore
import com.example.tvremote.protocol.ConnectionState
import com.example.tvremote.protocol.PairingClient
import com.example.tvremote.protocol.PairingException
import com.example.tvremote.protocol.RemoteClient
import com.example.tvremote.protocol.TvKey
import com.example.tvremote.voice.TvAction
import com.example.tvremote.voice.TvApp
import com.example.tvremote.voice.VoiceCommandParser
import com.example.tvremote.voice.VoiceController
import com.example.tvremote.voice.VoiceLanguage
import com.example.tvremote.voice.VoiceState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException

sealed interface PairingUi {
    data object Idle : PairingUi
    data class Connecting(val name: String) : PairingUi
    data class AwaitingCode(val host: String, val name: String, val error: String? = null) : PairingUi
    data object Verifying : PairingUi
    data class Failed(val message: String) : PairingUi
}

class RemoteViewModel(app: Application) : AndroidViewModel(app) {
    private val certStore = CertStore(app)
    private val store = DeviceStore(app)
    private val discovery = TvDiscovery(app)
    private val voice = VoiceController(app, ::onSpeech)

    val discovered: StateFlow<List<DiscoveredTv>> = discovery.tvs
    val voiceState: StateFlow<VoiceState> = voice.state

    private val _paired = MutableStateFlow(store.all())
    val paired: StateFlow<List<PairedTv>> = _paired.asStateFlow()

    private val _activeTv = MutableStateFlow<PairedTv?>(null)
    val activeTv: StateFlow<PairedTv?> = _activeTv.asStateFlow()

    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _pairing = MutableStateFlow<PairingUi>(PairingUi.Idle)
    val pairing: StateFlow<PairingUi> = _pairing.asStateFlow()

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    private val _language = MutableStateFlow(VoiceLanguage.fromTag(store.languageTag))
    val language: StateFlow<VoiceLanguage> = _language.asStateFlow()

    private var remote: RemoteClient? = null
    private var remoteWatcher: Job? = null
    private var pairingClient: PairingClient? = null
    private var pairingJob: Job? = null
    private var commandJob: Job? = null

    init {
        // If a paired TV changed IP address, follow it by name.
        viewModelScope.launch {
            discovery.tvs.collect { found ->
                var changed = false
                for (p in _paired.value) {
                    val moved = found.firstOrNull { it.name == p.name && it.host != p.host }
                    if (moved != null) {
                        store.save(p.copy(host = moved.host))
                        changed = true
                    }
                }
                if (changed) _paired.value = store.all()
            }
        }
        val last = store.lastHost?.let { host -> _paired.value.firstOrNull { it.host == host } }
        if (last != null) connect(last) else discovery.start()
    }

    // ---- Connection -------------------------------------------------------------------------

    fun connect(tv: PairedTv) {
        closeRemote()
        markActive(tv)
        discovery.stop()
        val client = RemoteClient(certStore, tv.host, tv.fingerprint)
        remote = client
        remoteWatcher = viewModelScope.launch { client.state.collect { _connection.value = it } }
        client.connect()
    }

    private fun markActive(tv: PairedTv) {
        _activeTv.value = tv
        store.lastHost = tv.host
    }

    fun reconnect() {
        _activeTv.value?.let { connect(it) }
    }

    /** Back to the TV list. */
    fun disconnect() {
        closeRemote()
        _activeTv.value = null
        store.lastHost = null
        discovery.start()
    }

    fun repair() {
        val tv = _activeTv.value ?: return
        disconnect()
        startPairing(tv.host, tv.name)
    }

    fun forget(tv: PairedTv) {
        store.remove(tv.host)
        _paired.value = store.all()
    }

    private fun closeRemote() {
        commandJob?.cancel()
        remoteWatcher?.cancel()
        remote?.close()
        remote = null
        _connection.value = ConnectionState.Disconnected
    }

    // ---- Pairing ----------------------------------------------------------------------------

    fun startPairing(host: String, name: String) {
        cancelPairing()
        _pairing.value = PairingUi.Connecting(name)
        val client = PairingClient(certStore, host)
        pairingClient = client
        pairingJob = viewModelScope.launch {
            try {
                client.begin()
                _pairing.value = PairingUi.AwaitingCode(host, name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                client.close()
                _pairing.value = PairingUi.Failed(friendly(e))
            }
        }
    }

    fun submitCode(code: String) {
        val awaiting = _pairing.value as? PairingUi.AwaitingCode ?: return
        val client = pairingClient ?: return
        _pairing.value = PairingUi.Verifying
        pairingJob = viewModelScope.launch {
            try {
                val fingerprint = client.finish(code)
                client.close()
                val tv = PairedTv(awaiting.host, awaiting.name, fingerprint)
                store.save(tv)
                _paired.value = store.all()
                _pairing.value = PairingUi.Idle
                connect(tv)
            } catch (e: CancellationException) {
                throw e
            } catch (e: PairingException) {
                if (e.retryable) {
                    _pairing.value = awaiting.copy(error = e.message)
                } else {
                    client.close()
                    _pairing.value = PairingUi.Failed(e.message ?: "Pairing failed.")
                }
            } catch (e: Exception) {
                client.close()
                _pairing.value = PairingUi.Failed(friendly(e))
            }
        }
    }

    fun cancelPairing() {
        pairingJob?.cancel()
        pairingClient?.close()
        pairingClient = null
        _pairing.value = PairingUi.Idle
    }

    private fun friendly(e: Exception): String = when (e) {
        is PairingException -> e.message ?: "Pairing failed."
        is SocketTimeoutException, is ConnectException, is NoRouteToHostException ->
            "Couldn't reach the TV. Check that it's on and on the same Wi-Fi."
        else -> e.message ?: "Pairing failed."
    }

    // ---- Buttons ----------------------------------------------------------------------------

    fun press(code: Int) {
        remote?.sendKey(code)
    }

    fun openApp(app: TvApp) {
        remote?.launchApp(app.link)
        _feedback.value = "Opening ${app.label}"
    }

    // ---- Voice ------------------------------------------------------------------------------

    fun startListening() {
        voice.start(_language.value.tag)
    }

    fun stopListening() {
        voice.stop()
    }

    fun onMicPermissionDenied() {
        voice.setError("Microphone permission is needed for voice commands.")
    }

    fun setLanguage(language: VoiceLanguage) {
        store.languageTag = language.tag
        _language.value = language
    }

    private fun onSpeech(alternatives: List<String>) {
        for (heard in alternatives) {
            val action = VoiceCommandParser.parse(heard) ?: continue
            _feedback.value = "“$heard” → ${action.description}"
            execute(action)
            return
        }
        _feedback.value = "Didn't understand “${alternatives.firstOrNull().orEmpty()}”"
    }

    private fun execute(action: TvAction) {
        val client = remote
        if (client == null || _connection.value !is ConnectionState.Ready) {
            _feedback.value = "Not connected to a TV."
            return
        }
        commandJob?.cancel()
        commandJob = viewModelScope.launch {
            when (action) {
                is TvAction.Key -> repeat(action.repeat) {
                    client.sendKey(action.code)
                    delay(120)
                }
                is TvAction.Digits -> action.digits.forEach { ch ->
                    client.sendKey(TvKey.digit(ch - '0'))
                    delay(250)
                }
                is TvAction.OpenLink -> client.launchApp(action.link)
            }
        }
    }

    override fun onCleared() {
        closeRemote()
        cancelPairing()
        discovery.stop()
        voice.release()
    }
}
