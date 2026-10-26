package com.deepshawn.hwcounter.recog

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * 连续语音识别：系统 SpeechRecognizer 每次只识别一句，这里在每句结束/超时后自动重新开始，
 * 让老师可以不停地念名字或学号。必须在主线程使用。
 */
class VoiceListener(
    private val ctx: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onState: (State) -> Unit,
) : RecognitionListener {
    enum class State { IDLE, LISTENING, HEARING, ERROR, UNAVAILABLE }

    private val main = Handler(Looper.getMainLooper())
    private var sr: SpeechRecognizer? = null
    private var running = false
    private var busyRetries = 0

    companion object {
        fun available(ctx: Context) = SpeechRecognizer.isRecognitionAvailable(ctx)
    }

    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "zh-CN")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        // 念名字之间会停顿，稍微放宽静音判定
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
    }

    fun start() {
        if (!available(ctx)) { onState(State.UNAVAILABLE); return }
        running = true
        if (sr == null) sr = SpeechRecognizer.createSpeechRecognizer(ctx).also { it.setRecognitionListener(this) }
        listen()
    }

    private fun listen() {
        if (!running) return
        try {
            sr?.startListening(intent)
        } catch (e: Exception) {
            onState(State.ERROR)
            restart(600)
        }
    }

    private fun restart(delayMs: Long) {
        if (!running) return
        main.removeCallbacksAndMessages(null)
        main.postDelayed({ listen() }, delayMs)
    }

    fun stop() {
        running = false
        main.removeCallbacksAndMessages(null)
        runCatching { sr?.cancel() }
        onState(State.IDLE)
    }

    fun destroy() {
        stop()
        runCatching { sr?.destroy() }
        sr = null
    }

    override fun onReadyForSpeech(params: Bundle?) { busyRetries = 0; onState(State.LISTENING) }
    override fun onBeginningOfSpeech() = onState(State.HEARING)
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    override fun onPartialResults(partialResults: Bundle?) {
        partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onPartial)
    }

    override fun onResults(results: Bundle?) {
        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let(onFinal)
        onPartial("")
        restart(80)
    }

    override fun onError(error: Int) {
        when (error) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> { running = false; onState(State.UNAVAILABLE) }
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> restart(80)
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                runCatching { sr?.cancel() }
                busyRetries++
                restart(300L * busyRetries.coerceAtMost(5))
            }
            else -> {
                // 网络/服务端错误：稍等后重试，界面提示
                onState(State.ERROR)
                restart(800)
            }
        }
    }
}
