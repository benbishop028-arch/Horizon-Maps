package com.headunit.launcher.car

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.media.CarAudioRecord
import com.headunit.launcher.L
import com.headunit.launcher.ui.CarSearch

/**
 * Voice search on the car screen, listening as soon as the microphone button is tapped. Android Auto's own search screen has
 * no way for an app to start its microphone, so the app listens itself: the car's microphone (CarAudioRecord, which Android
 * Auto shares with apps) is fed to the phone's speech recogniser, and what it makes out goes to [onText]. On phones before
 * Android 13, where the recogniser can't be given the sound, or when the car's microphone can't be opened, the phone's own
 * microphone is used instead.
 */
class CarVoice(private val carContext: CarContext, private val onNoMic: () -> Unit, private val onText: (String) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var rec: SpeechRecognizer? = null
    private var car: CarAudioRecord? = null
    @Volatile private var pumping = false
    private var focus: AudioFocusRequest? = null
    private val giveUp = Runnable {
        val heard = CarSearch.heard.trim()
        Log.i(TAG, "voice: listening timed out; partial result=${heard.isNotBlank()}")
        if (heard.isNotBlank()) { finish(""); onText(heard) } else finish(L.t("Didn't catch that"))
    }

    /** The microphone button: starts listening, or stops when it already is. */
    fun toggle() { if (CarSearch.listening) finish("") else start() }

    fun start() {
        val app = carContext.applicationContext
        if (app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // The microphone isn't allowed yet. It can't be asked for from here: the car library's own request is refused on
            // newer Android ("Permission Denial ... launchDisplayId", it starts its screen on the car's display), so it's allowed
            // in the app on the phone (Settings > Android Auto > Voice search). Until then the button opens Android Auto's
            // search screen, which has a microphone of its own
            Log.i(TAG, "voice: the microphone isn't allowed, opening Android Auto's search")
            try { CarToast.makeText(carContext, L.t("To listen straight away, allow the microphone in Horizon Nav on your phone: Settings › Android Auto"), CarToast.LENGTH_LONG).show() } catch (e: Exception) { }
            onNoMic()
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(app)) { CarSearch.note = L.t("Voice search isn't available on this phone"); return }
        begin(Build.VERSION.SDK_INT >= 33 && carContext.carAppApiLevel >= 5)
    }

    private fun begin(carMic: Boolean) {
        release()
        CarSearch.note = ""; CarSearch.heard = ""
        val app = carContext.applicationContext
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, L.locale.toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        var usingCar = false
        if (carMic && Build.VERSION.SDK_INT >= 33) try {
            // the car's microphone: 16 kHz, one channel, 16 bit (CarAudioRecord's format), through a pipe to the recogniser
            val rec0 = CarAudioRecord.create(carContext)
            rec0.startRecording()
            val pipe = ParcelFileDescriptor.createPipe()
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe[0])
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, CarAudioRecord.AUDIO_CONTENT_SAMPLING_RATE)
            car = rec0; pumping = true; usingCar = true
            Thread({
                val out = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
                val buf = ByteArray(CarAudioRecord.AUDIO_CONTENT_BUFFER_SIZE)
                try {
                    while (pumping) {
                        val n = rec0.read(buf, 0, buf.size)
                        if (n < 0) break
                        if (n > 0) out.write(buf, 0, n)
                    }
                } catch (e: Exception) { Log.i(TAG, "voice: the car's microphone stopped: $e") }
                try { out.close() } catch (e: Exception) { }
                try { rec0.stopRecording() } catch (e: Exception) { }
            }, "CarVoiceMic").start()
        } catch (e: SecurityException) {
            // (start() only gets here with the microphone allowed; it can still be taken away in between)
            Log.w(TAG, "voice: the microphone isn't allowed", e)
            car = null; pumping = false
        } catch (e: Exception) {
            Log.w(TAG, "voice: couldn't open the car's microphone, using the phone's", e)
            car = null; pumping = false
        }
        Log.i(TAG, "voice: listening with ${if (usingCar) "the car's" else "the phone's"} microphone")
        requestFocus()
        val r = SpeechRecognizer.createSpeechRecognizer(app)
        rec = r
        r.setRecognitionListener(object : RecognitionListener {
            private fun first(b: Bundle?) = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            override fun onPartialResults(partialResults: Bundle?) {
                if (rec !== r) return
                first(partialResults).let { if (it.isNotBlank()) CarSearch.heard = it }
            }
            override fun onResults(results: Bundle?) {
                if (rec !== r) return
                val text = first(results).ifBlank { CarSearch.heard }.trim()
                Log.i(TAG, "voice: heard \"$text\"")
                finish(if (text.isBlank()) L.t("Didn't catch that") else "")
                if (text.isNotBlank()) onText(text)
            }
            override fun onError(error: Int) {
                Log.i(TAG, "voice: recogniser error $error (car microphone: $usingCar)")
                if (rec !== r) return
                // Some recognisers report a final error after already returning a usable partial result.
                // Keep that destination instead of discarding it or starting a second listening session.
                val heard = CarSearch.heard.trim()
                if (heard.isNotBlank()) {
                    Log.i(TAG, "voice: using partial result after error $error: \"$heard\"")
                    finish("")
                    onText(heard)
                    return
                }
                // The car microphone can fail before it produces any text; in that case try the phone microphone.
                val heardNothing = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                if (usingCar && !heardNothing) begin(false) else finish(L.t("Didn't catch that"))
            }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        try { r.startListening(intent) } catch (e: Exception) { Log.w(TAG, "voice: couldn't start listening", e); finish(L.t("Voice search isn't available on this phone")); return }
        CarSearch.listening = true
        main.removeCallbacks(giveUp); main.postDelayed(giveUp, 15_000L)
    }

    /** Music and guidance go quiet while it listens. */
    private fun requestFocus() {
        try {
            val am = carContext.applicationContext.getSystemService(AudioManager::class.java) ?: return
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .build()
            am.requestAudioFocus(req); focus = req
        } catch (e: Exception) { }
    }

    private fun release() {
        main.removeCallbacks(giveUp)
        pumping = false; car = null
        rec?.let { r -> rec = null; try { r.cancel() } catch (e: Exception) { }; try { r.destroy() } catch (e: Exception) { } }
        focus?.let { f -> focus = null; try { carContext.applicationContext.getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(f) } catch (e: Exception) { } }
    }

    /** Stops listening; [note] is what the search card says about it (blank for nothing). */
    fun finish(note: String) {
        release()
        CarSearch.listening = false; CarSearch.heard = ""
        CarSearch.note = note
    }

    private companion object { const val TAG = "CarNav" }
}
