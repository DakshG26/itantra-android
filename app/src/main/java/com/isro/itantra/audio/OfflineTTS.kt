package com.isro.itantra.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class OfflineTTS(context: Context, onReady: (() -> Unit)? = null) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            setLanguage("hi")
            Log.d("OfflineTTS", "Native Offline TTS Engine initialized ✓")
        } else {
            Log.e("OfflineTTS", "TTS Initialization failed with status: $status")
        }
    }

    fun setLanguage(langCode: String) {
        if (!isInitialized || tts == null) return

        val locale = when (langCode) {
            "hi" -> Locale("hi", "IN")
            "ta" -> Locale("ta", "IN")
            "mr" -> Locale("mr", "IN")
            "te" -> Locale("te", "IN")
            "bn" -> Locale("bn", "IN")
            else -> Locale.ENGLISH
        }

        val result = tts?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w("OfflineTTS", "Language $langCode not supported directly, using default.")
            tts?.language = Locale.ENGLISH
        }
    }

    fun speak(text: String, langCode: String = "hi") {
        if (!isInitialized || tts == null) return
        setLanguage(langCode)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "iTantraMessageId")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
