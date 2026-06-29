package com.example.audio

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

class SpeechRecognizerManager(private val context: Context) {
    private var speechRecognizer: SpeechRecognizer? = null
    
    private val _transcription = MutableStateFlow("")
    val transcription: StateFlow<String> = _transcription

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var speechIntent: Intent? = null
    
    private fun setupRecognizer() {
        if (speechRecognizer != null) return

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                
                override fun onEndOfSpeech() {
                    // Do not restart here, wait for onResults
                }

                override fun onError(error: Int) {
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                            restartListening()
                        }
                        SpeechRecognizer.ERROR_CLIENT -> {
                            restartListening()
                        }
                        else -> {
                            _error.value = "Hata oluştu (Kodu: $error)"
                            _isListening.value = false
                        }
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val newText = matches[0]
                        _transcription.value = if (_transcription.value.isEmpty()) {
                            newText
                        } else {
                            "${_transcription.value} $newText"
                        }
                    }
                    restartListening()
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    // Could append partial results for a more "live" feel
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR") // Turkish, or Locale.getDefault()
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
    }

    private fun restartListening() {
        if (_isListening.value) {
            Handler(Looper.getMainLooper()).postDelayed({
                if (_isListening.value) {
                    try {
                        speechRecognizer?.startListening(speechIntent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }, 100)
        }
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _error.value = "Cihazınızda konuşma tanıma özelliği bulunmuyor."
            return
        }

        setupRecognizer()
        
        _isListening.value = true
        _error.value = null
        
        try {
            speechRecognizer?.startListening(speechIntent)
        } catch (e: Exception) {
            _error.value = "Hata: ${e.message}"
        }
    }

    fun stopListening() {
        _isListening.value = false
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
    
    fun clear() {
        _transcription.value = ""
    }
}
