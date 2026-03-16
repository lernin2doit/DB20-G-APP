package com.db20g.controller.translation

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Real-time radio audio translation pipeline:
 *
 * Audio → Speech-to-Text → Language Detection → Translation → TTS
 *
 * Features:
 * - On-device ML Kit translation (no cloud)
 * - Language identification for auto-detection
 * - English ↔ Spanish priority, expandable
 * - Confidence threshold filtering
 * - Translation log with export
 * - Model download manager
 */
class RadioTranslator(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("translation_settings", Context.MODE_PRIVATE)

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var listener: TranslationListener? = null
    private var isListening = false
    private var errorBackoffMs = 500L
    private val MAX_ERROR_BACKOFF_MS = 30_000L

    // Supported language pairs
    data class LanguagePair(
        val sourceCode: String,
        val sourceName: String,
        val targetCode: String,
        val targetName: String
    )

    val supportedPairs = listOf(
        LanguagePair("en", "English", "es", "Spanish"),
        LanguagePair("es", "Spanish", "en", "English"),
        LanguagePair("en", "English", "fr", "French"),
        LanguagePair("fr", "French", "en", "English"),
        LanguagePair("en", "English", "de", "German"),
        LanguagePair("de", "German", "en", "English"),
        LanguagePair("en", "English", "pt", "Portuguese"),
        LanguagePair("pt", "Portuguese", "en", "English")
    )

    data class TranslationResult(
        val originalText: String,
        val translatedText: String,
        val sourceLanguage: String,
        val targetLanguage: String,
        val confidence: Float,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class ModelInfo(
        val languageCode: String,
        val languageName: String,
        val downloaded: Boolean,
        val sizeEstimateMb: Int
    )

    interface TranslationListener {
        fun onSpeechRecognized(text: String, language: String)
        fun onTranslationResult(result: TranslationResult)
        fun onTranslationError(error: String)
        fun onListeningStateChanged(isListening: Boolean)
        fun onTtsSpeaking(text: String)
    }

    // --- Settings ---

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) { prefs.edit().putBoolean("enabled", value).apply() }

    var preferredLanguage: String
        get() = prefs.getString("preferred_language", "en") ?: "en"
        set(value) { prefs.edit().putString("preferred_language", value).apply() }

    var confidenceThreshold: Float
        get() = prefs.getFloat("confidence_threshold", 0.5f)
        set(value) { prefs.edit().putFloat("confidence_threshold", value).apply() }

    var autoTranslateOutgoing: Boolean
        get() = prefs.getBoolean("auto_translate_outgoing", false)
        set(value) { prefs.edit().putBoolean("auto_translate_outgoing", value).apply() }

    var ttsEnabled: Boolean
        get() = prefs.getBoolean("tts_enabled", true)
        set(value) { prefs.edit().putBoolean("tts_enabled", value).apply() }

    fun setTranslationListener(l: TranslationListener) { listener = l }

    // --- Initialization ---

    fun initialize() {
        tts = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = Locale(preferredLanguage)
            }
        }
    }

    // --- Speech Recognition ---

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            listener?.onTranslationError("Speech recognition not available on this device")
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                listener?.onListeningStateChanged(true)
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                isListening = false
                listener?.onListeningStateChanged(false)
            }

            override fun onError(error: Int) {
                isListening = false
                listener?.onListeningStateChanged(false)
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error (using on-device only)"
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    else -> "Recognition error: $error"
                }
                listener?.onTranslationError(msg)

                // Auto-restart with exponential backoff
                if (enabled) {
                    errorBackoffMs = (errorBackoffMs * 2).coerceAtMost(MAX_ERROR_BACKOFF_MS)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (enabled) startListening()
                    }, errorBackoffMs)
                }
            }

            override fun onResults(results: Bundle?) {
                errorBackoffMs = 500L // Reset backoff on success
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val confidences = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)

                if (!matches.isNullOrEmpty()) {
                    val text = matches[0]
                    val confidence = confidences?.firstOrNull() ?: 0.8f

                    if (confidence >= confidenceThreshold) {
                        processRecognizedSpeech(text, confidence)
                    }
                }

                // Auto-restart for continuous listening
                if (enabled) {
                    startListening()
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    listener?.onSpeechRecognized(matches[0], "detecting...")
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            // Prefer offline recognition
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        isListening = false
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        listener?.onListeningStateChanged(false)
    }

    // --- Language Detection & Translation ---

    private fun processRecognizedSpeech(text: String, confidence: Float) {
        // Identify language
        val identifier = LanguageIdentification.getClient(
            LanguageIdentificationOptions.Builder()
                .setConfidenceThreshold(confidenceThreshold)
                .build()
        )

        identifier.identifyLanguage(text)
            .addOnSuccessListener { languageCode ->
                val detectedLang = if (languageCode == "und") "en" else languageCode
                listener?.onSpeechRecognized(text, detectedLang)

                // Only translate if detected language differs from preferred
                if (detectedLang != preferredLanguage) {
                    translateText(text, detectedLang, preferredLanguage, confidence)
                } else {
                    // Same language — log as recognized but don't translate
                    val result = TranslationResult(
                        originalText = text,
                        translatedText = text,
                        sourceLanguage = detectedLang,
                        targetLanguage = preferredLanguage,
                        confidence = confidence
                    )
                    addToHistory(result)
                    listener?.onTranslationResult(result)
                }

                identifier.close()
            }
            .addOnFailureListener { e ->
                // Default to English if detection fails
                listener?.onSpeechRecognized(text, "en")
                if (preferredLanguage != "en") {
                    translateText(text, "en", preferredLanguage, confidence)
                }
                identifier.close()
            }
    }

    private fun translateText(text: String, sourceLang: String, targetLang: String, confidence: Float) {
        val sourceTranslateLang = getTranslateLanguage(sourceLang) ?: return
        val targetTranslateLang = getTranslateLanguage(targetLang) ?: return

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceTranslateLang)
            .setTargetLanguage(targetTranslateLang)
            .build()

        val translator = Translation.getClient(options)
        val conditions = DownloadConditions.Builder().build()

        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                translator.translate(text)
                    .addOnSuccessListener { translatedText ->
                        val result = TranslationResult(
                            originalText = text,
                            translatedText = translatedText,
                            sourceLanguage = sourceLang,
                            targetLanguage = targetLang,
                            confidence = confidence
                        )

                        addToHistory(result)
                        listener?.onTranslationResult(result)

                        // Speak translated text if TTS enabled
                        if (ttsEnabled && ttsReady) {
                            speakText(translatedText, targetLang)
                        }

                        translator.close()
                    }
                    .addOnFailureListener { e ->
                        listener?.onTranslationError("Translation failed: ${e.message}")
                        translator.close()
                    }
            }
            .addOnFailureListener { e ->
                listener?.onTranslationError("Model download failed: ${e.message}")
                translator.close()
            }
    }

    /**
     * Translate outgoing text before transmission.
     */
    suspend fun translateOutgoing(text: String, targetLang: String): String? =
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { cont ->
                val sourceTranslateLang = getTranslateLanguage(preferredLanguage)
                val targetTranslateLang = getTranslateLanguage(targetLang)

                if (sourceTranslateLang == null || targetTranslateLang == null) {
                    cont.resume(null)
                    return@suspendCancellableCoroutine
                }

                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(sourceTranslateLang)
                    .setTargetLanguage(targetTranslateLang)
                    .build()

                val translator = Translation.getClient(options)
                val conditions = DownloadConditions.Builder().build()

                translator.downloadModelIfNeeded(conditions)
                    .addOnSuccessListener {
                        translator.translate(text)
                            .addOnSuccessListener { result ->
                                translator.close()
                                cont.resume(result)
                            }
                            .addOnFailureListener {
                                translator.close()
                                cont.resume(null)
                            }
                    }
                    .addOnFailureListener {
                        translator.close()
                        cont.resume(null)
                    }
            }
        }

    private fun getTranslateLanguage(code: String): String? {
        return when (code) {
            "en" -> TranslateLanguage.ENGLISH
            "es" -> TranslateLanguage.SPANISH
            "fr" -> TranslateLanguage.FRENCH
            "de" -> TranslateLanguage.GERMAN
            "pt" -> TranslateLanguage.PORTUGUESE
            "ja" -> TranslateLanguage.JAPANESE
            "ko" -> TranslateLanguage.KOREAN
            "zh" -> TranslateLanguage.CHINESE
            else -> null
        }
    }

    // --- TTS ---

    private fun speakText(text: String, language: String) {
        tts?.language = Locale(language)
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                listener?.onTtsSpeaking(text)
            }
            override fun onDone(utteranceId: String?) {}
            override fun onError(utteranceId: String?) {}
        })
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "translation_${System.currentTimeMillis()}")
    }

    fun stopTts() {
        tts?.stop()
    }

    // --- Translation History ---

    private val history = mutableListOf<TranslationResult>()

    fun getHistory(): List<TranslationResult> = history.toList()

    private fun addToHistory(result: TranslationResult) {
        history.add(result)
        // Keep last 500 entries
        if (history.size > 500) {
            history.removeAt(0)
        }
        persistHistory()
    }

    fun clearHistory() {
        history.clear()
        prefs.edit().remove("translation_history").apply()
    }

    private fun persistHistory() {
        val arr = JSONArray()
        for (r in history.takeLast(200)) {
            val obj = JSONObject()
            obj.put("original", r.originalText)
            obj.put("translated", r.translatedText)
            obj.put("sourceLang", r.sourceLanguage)
            obj.put("targetLang", r.targetLanguage)
            obj.put("confidence", r.confidence.toDouble())
            obj.put("timestamp", r.timestamp)
            arr.put(obj)
        }
        prefs.edit().putString("translation_history", arr.toString()).apply()
    }

    fun loadHistory() {
        val json = prefs.getString("translation_history", null) ?: return
        try {
            val arr = JSONArray(json)
            history.clear()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                history.add(TranslationResult(
                    originalText = obj.getString("original"),
                    translatedText = obj.getString("translated"),
                    sourceLanguage = obj.getString("sourceLang"),
                    targetLanguage = obj.getString("targetLang"),
                    confidence = obj.getDouble("confidence").toFloat(),
                    timestamp = obj.getLong("timestamp")
                ))
            }
        } catch (e: Exception) {
            // Ignore corrupt data
        }
    }

    /**
     * Export translation history as CSV text.
     */
    fun exportHistoryCsv(): String {
        val sb = StringBuilder()
        sb.appendLine("Timestamp,Source Language,Target Language,Original Text,Translated Text,Confidence")
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        for (r in history) {
            val ts = sdf.format(Date(r.timestamp))
            val orig = r.originalText.replace(",", ";").replace("\n", " ")
            val trans = r.translatedText.replace(",", ";").replace("\n", " ")
            sb.appendLine("$ts,${r.sourceLanguage},${r.targetLanguage},\"$orig\",\"$trans\",${r.confidence}")
        }
        return sb.toString()
    }

    // --- Model Management ---

    fun getAvailableModels(callback: (List<ModelInfo>) -> Unit) {
        val modelManager = RemoteModelManager.getInstance()
        modelManager.getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener { downloadedModels ->
                val downloadedCodes = downloadedModels.map { it.language }.toSet()

                val models = listOf(
                    ModelInfo("en", "English", downloadedCodes.contains(TranslateLanguage.ENGLISH), 30),
                    ModelInfo("es", "Spanish", downloadedCodes.contains(TranslateLanguage.SPANISH), 30),
                    ModelInfo("fr", "French", downloadedCodes.contains(TranslateLanguage.FRENCH), 30),
                    ModelInfo("de", "German", downloadedCodes.contains(TranslateLanguage.GERMAN), 30),
                    ModelInfo("pt", "Portuguese", downloadedCodes.contains(TranslateLanguage.PORTUGUESE), 30)
                )
                callback(models)
            }
            .addOnFailureListener {
                callback(emptyList())
            }
    }

    fun downloadModel(languageCode: String, onComplete: (Boolean) -> Unit) {
        val translateLang = getTranslateLanguage(languageCode) ?: run {
            onComplete(false)
            return
        }
        val model = TranslateRemoteModel.Builder(translateLang).build()
        val conditions = DownloadConditions.Builder().build()

        RemoteModelManager.getInstance().download(model, conditions)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    fun deleteModel(languageCode: String, onComplete: (Boolean) -> Unit) {
        val translateLang = getTranslateLanguage(languageCode) ?: run {
            onComplete(false)
            return
        }
        val model = TranslateRemoteModel.Builder(translateLang).build()

        RemoteModelManager.getInstance().deleteDownloadedModel(model)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    // --- Cleanup ---

    fun destroy() {
        stopListening()
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
