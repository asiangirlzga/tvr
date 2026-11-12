package com.example.privatevoice

import android.content.Context
import com.example.privatevoice.command.Command
import com.example.privatevoice.command.CommandParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

enum class ListenMode { OFF, ONCE, HANDS_FREE }

sealed interface ModelState {
    data object Loading : ModelState
    data object Ready : ModelState
    data class Error(val message: String) : ModelState
}

/**
 * Offline speech recognition (Vosk). The speech model ships inside the app; audio goes from the
 * microphone to the recogniser in memory and is never stored or sent anywhere.
 * All methods must be called on the main thread.
 */
object VoiceEngine {
    private const val SAMPLE_RATE = 16000.0f
    private const val ONCE_TIMEOUT_MS = 8000

    private val _modelState = MutableStateFlow<ModelState>(ModelState.Loading)
    val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    private val _mode = MutableStateFlow(ListenMode.OFF)
    val mode: StateFlow<ListenMode> = _mode.asStateFlow()

    /** Words heard so far in the current phrase (cleared when done). */
    private val _partial = MutableStateFlow("")
    val partial: StateFlow<String> = _partial.asStateFlow()

    private val _lastHeard = MutableStateFlow("")
    val lastHeard: StateFlow<String> = _lastHeard.asStateFlow()

    private val _lastResult = MutableStateFlow("")
    val lastResult: StateFlow<String> = _lastResult.asStateFlow()

    private var appContext: Context? = null
    private var model: Model? = null
    private var service: SpeechService? = null
    private var loading = false

    /** Unpacks the bundled model on first run (a few seconds), then keeps it ready. */
    fun init(context: Context) {
        appContext = context.applicationContext
        if (model != null || loading) return
        loading = true
        _modelState.value = ModelState.Loading
        StorageService.unpack(
            context.applicationContext, "model-en-us", "model",
            { m ->
                model = m
                loading = false
                _modelState.value = ModelState.Ready
            },
            { e ->
                loading = false
                _modelState.value = ModelState.Error(
                    "Speech model failed to load: ${e.message ?: "unknown error"}",
                )
            },
        )
    }

    fun startOnce() = start(ListenMode.ONCE)

    fun startHandsFree() = start(ListenMode.HANDS_FREE)

    private fun start(newMode: ListenMode) {
        val m = model
        if (m == null) {
            _lastResult.value = "Speech model isn't ready yet."
            return
        }
        stop()
        try {
            val recognizer = Recognizer(m, SAMPLE_RATE)
            val s = SpeechService(recognizer, SAMPLE_RATE)
            service = s
            _mode.value = newMode
            _partial.value = ""
            val listener = Listener()
            if (newMode == ListenMode.ONCE) s.startListening(listener, ONCE_TIMEOUT_MS) else s.startListening(listener)
        } catch (e: Exception) {
            stop()
            _lastResult.value = "Couldn't start the microphone."
        }
    }

    /** Stops listening and releases the microphone. */
    fun stop() {
        service?.let {
            it.cancel()
            it.shutdown()
        }
        service = null
        _mode.value = ListenMode.OFF
        _partial.value = ""
    }

    private fun handle(text: String) {
        val ctx = appContext ?: return
        if (text.isBlank()) return
        val handsFree = _mode.value == ListenMode.HANDS_FREE
        val spoken = if (handsFree) CommandParser.stripWakeWord(text) ?: return else text
        _lastHeard.value = spoken.ifBlank { text }
        val command: Command? = CommandParser.parse(spoken)
        if (command == null) {
            _lastResult.value = "Didn't understand “$spoken”"
        } else {
            _lastResult.value = CommandExecutor.execute(ctx, command)
            if (command == Command.StopListening) {
                stop()
                ctx.stopService(android.content.Intent(ctx, HandsFreeService::class.java))
                return
            }
        }
        if (!handsFree) stop()
    }

    private class Listener : RecognitionListener {
        override fun onPartialResult(hypothesis: String?) {
            val p = hypothesis?.let { runCatching { JSONObject(it).optString("partial") }.getOrNull() }.orEmpty()
            _partial.value = p
        }

        override fun onResult(hypothesis: String?) = finish(hypothesis)

        override fun onFinalResult(hypothesis: String?) = finish(hypothesis)

        private fun finish(hypothesis: String?) {
            _partial.value = ""
            val text = hypothesis?.let { runCatching { JSONObject(it).optString("text") }.getOrNull() }.orEmpty()
            handle(text)
        }

        override fun onError(exception: Exception?) {
            _lastResult.value = "Microphone error. Try again."
            stop()
        }

        override fun onTimeout() {
            if (_mode.value == ListenMode.ONCE) _lastResult.value = "Didn't hear anything."
            stop()
        }
    }
}
