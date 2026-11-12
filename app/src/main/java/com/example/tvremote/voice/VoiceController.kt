package com.example.tvremote.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VoiceLanguage(val tag: String, val label: String) {
    ENGLISH_INDIA("en-IN", "English (India)"),
    HINDI("hi-IN", "हिन्दी"),
    ENGLISH_US("en-US", "English (US)");

    companion object {
        fun fromTag(tag: String): VoiceLanguage = entries.firstOrNull { it.tag == tag } ?: ENGLISH_INDIA
    }
}

sealed interface VoiceState {
    data object Idle : VoiceState
    data class Listening(val partial: String = "") : VoiceState
    data class Error(val message: String) : VoiceState
}

/** Wraps Android's SpeechRecognizer. Create and call it on the main thread. */
class VoiceController(
    private val context: Context,
    private val onFinal: (alternatives: List<String>) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state.asStateFlow()
    private var recognizer: SpeechRecognizer? = null

    fun start(languageTag: String) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = VoiceState.Error("Speech recognition isn't available on this device.")
            return
        }
        release()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.value = VoiceState.Listening()
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!partial.isNullOrBlank()) _state.value = VoiceState.Listening(partial)
            }

            override fun onResults(results: Bundle?) {
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                _state.value = VoiceState.Idle
                releaseLater()
                if (list.isNotEmpty()) onFinal(list) else _state.value = VoiceState.Error("Didn't catch that. Try again.")
            }

            override fun onError(error: Int) {
                _state.value = VoiceState.Error(messageFor(error))
                releaseLater()
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            // Keep speech on the phone when an offline language pack is installed.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        _state.value = VoiceState.Listening()
        r.startListening(intent)
    }

    /** Stops recording; the recognizer still delivers whatever it heard so far. */
    fun stop() {
        recognizer?.stopListening()
    }

    fun setError(message: String) {
        _state.value = VoiceState.Error(message)
    }

    fun release() {
        recognizer?.let {
            it.setRecognitionListener(null)
            it.destroy()
        }
        recognizer = null
    }

    private fun releaseLater() {
        main.post { release() }
    }

    private fun messageFor(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that. Try again."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed for voice commands."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Speech recognition needs an internet connection."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice recognition is busy. Try again in a moment."
        SpeechRecognizer.ERROR_AUDIO -> "Couldn't use the microphone."
        else -> "Voice recognition failed. Try again."
    }
}
