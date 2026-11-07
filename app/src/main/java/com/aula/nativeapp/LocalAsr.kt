package com.aula.nativeapp

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * El oído propio de Aula — the lesson Cognata paid for, applied
 * here from day one to BOTH speaking surfaces (Copia's auto-check
 * and Conversación en vivo).
 *
 * The phone's built-in recognizer cannot be trusted on the owner's
 * Galaxy class of devices: it accepts the attempt and banks
 * nothing, take after take, and a learner-facing app must never let
 * that wall grade — or silently discard — real speech. So Aula
 * ships its OWN speech engine (sherpa-onnx, a Zipformer transducer)
 * that runs entirely inside the app: no network, no system
 * recognition service, the voice never leaves the phone.
 *
 * The engine never opens the microphone. While the system
 * recognizer listens, a plain [WavCapture] records the same take;
 * at settlement, ONLY when the recognizer banked nothing usable
 * (an empty result, a no-match, a dead-service timeout) and the
 * capture holds real signal, the engine transcribes the finished
 * capture and fills the empty candidate — once per attempt, badged
 * wherever the transcript is used ("motor de voz de la app"). A
 * genuine recognizer transcript is never touched, and an engine
 * failure changes nothing: the attempt settles exactly as it did
 * before the engine existed.
 */

/**
 * Pure PCM16LE → float conversion for the in-app speech engine:
 * accepts either a full WAV file or raw PCM bytes. Kept free of
 * any native dependency so the JVM gate can pin it — loading the
 * engine itself needs the device. (Ported from Cognata's
 * LocalAsrEngine, where this exact decoder is gate-pinned.)
 */
object Pcm16 {
    fun floatSamples(bytes: ByteArray): FloatArray {
        val pcm = pcmPayload(bytes) ?: return FloatArray(0)
        val count = pcm.size / 2
        val out = FloatArray(count)
        var i = 0
        while (i < count) {
            val lo = pcm[2 * i].toInt() and 0xFF
            val hi = pcm[2 * i + 1].toInt()
            out[i] = ((hi shl 8) or lo).toShort() / 32768f
            i++
        }
        return out
    }

    fun pcmPayload(bytes: ByteArray): ByteArray? {
        if (bytes.size < 4) {
            val riffMagic = byteArrayOf(
                'R'.code.toByte(), 'I'.code.toByte(),
                'F'.code.toByte(), 'F'.code.toByte()
            )
            val isRiffFragment = bytes.indices.all { bytes[it] == riffMagic[it] }
            return if (isRiffFragment) null else bytes
        }
        if (!isRiff(bytes)) return bytes
        if (bytes.size < 12) return null
        var offset = 12 // "RIFF" + size + "WAVE"
        while (offset + 8 <= bytes.size) {
            val chunkId = bytes.decodeToString(offset, offset + 4)
            val chunkSize = readLeInt(bytes, offset + 4)
            if (chunkSize < 0) return null
            val dataStart = offset + 8
            if (chunkId == "data") {
                val end = minOf(dataStart + chunkSize, bytes.size)
                return bytes.copyOfRange(dataStart, end)
            }
            offset = dataStart + chunkSize + (chunkSize and 1)
        }
        return null
    }

    private fun isRiff(bytes: ByteArray): Boolean =
        bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte()

    private fun readLeInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
}

object LocalAsrEngine {

    const val SAMPLE_RATE = 16000

    private const val MODEL_DIR = "asr-en"
    private const val DECODE_TIMEOUT_MS = 20_000L

    private val initMutex = Mutex()

    @Volatile
    private var recognizer: OnlineRecognizer? = null

    @Volatile
    private var initFailed = false

    /**
     * Transcribes [wavOrPcm] (a finished capture) on a background
     * dispatcher; returns the trimmed transcript, or null when the
     * engine produced nothing usable. Failure is a value, never a
     * crash.
     */
    suspend fun transcribe(context: Context, wavOrPcm: ByteArray): String? {
        val samples = Pcm16.floatSamples(wavOrPcm)
        if (samples.isEmpty()) return null
        val rec = recognizer(context) ?: return null
        return withTimeoutOrNull(DECODE_TIMEOUT_MS) {
            withContext(Dispatchers.Default) {
                try {
                    val stream = rec.createStream()
                    stream.acceptWaveform(samples, SAMPLE_RATE)
                    stream.inputFinished()
                    while (rec.isReady(stream)) {
                        rec.decode(stream)
                    }
                    rec.getResult(stream).text.trim().ifBlank { null }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    private suspend fun recognizer(context: Context): OnlineRecognizer? {
        recognizer?.let { return it }
        if (initFailed) return null
        return initMutex.withLock {
            recognizer?.let { return@withLock it }
            try {
                val config = OnlineRecognizerConfig(
                    featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
                    modelConfig = OnlineModelConfig(
                        transducer = OnlineTransducerModelConfig(
                            encoder = "$MODEL_DIR/encoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx",
                            decoder = "$MODEL_DIR/decoder-epoch-99-avg-1-chunk-16-left-128.onnx",
                            joiner = "$MODEL_DIR/joiner-epoch-99-avg-1-chunk-16-left-128.onnx"
                        ),
                        tokens = "$MODEL_DIR/tokens.txt",
                        numThreads = 2,
                        modelType = "zipformer2"
                    ),
                    enableEndpoint = false
                )
                OnlineRecognizer(
                    assetManager = context.applicationContext.assets,
                    config = config
                ).also { recognizer = it }
            } catch (e: Throwable) {
                initFailed = true
                null
            }
        }
    }
}

/**
 * A plain 16 kHz mono PCM capture (AudioRecord, VOICE_RECOGNITION
 * source — the same shape Cognata's studio recorder uses) that
 * runs alongside a SpeechRecognizer attempt so the take itself
 * survives even when the recognition service banks nothing. The
 * capture is memory-only, capped at [MAX_SECONDS]; it is never
 * written to disk and never leaves the phone. Any failure to start
 * (mic busy, device quirk) simply means no capture — every caller
 * behaves exactly as it did before this class existed.
 */
class WavCapture {

    @Volatile
    private var running = false

    private var record: AudioRecord? = null
    private var reader: Thread? = null
    private val buffer = ByteArrayOutputStream()

    /** Peak absolute sample seen so far (0..32767). */
    @Volatile
    var peak: Int = 0
        private set

    fun start() {
        stopQuietly()
        synchronized(buffer) { buffer.reset() }
        peak = 0
        try {
            val minBuf = AudioRecord.getMinBufferSize(
                LocalAsrEngine.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf <= 0) return
            val rec = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                LocalAsrEngine.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, 8192)
            )
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                try { rec.release() } catch (e: Exception) {}
                return
            }
            record = rec
            running = true
            rec.startRecording()
            reader = thread(name = "aula-wav-capture") {
                val chunk = ByteArray(4096)
                while (running) {
                    val n = try {
                        rec.read(chunk, 0, chunk.size)
                    } catch (e: Exception) {
                        -1
                    }
                    if (n <= 0) break
                    synchronized(buffer) {
                        val room = MAX_BYTES - buffer.size()
                        if (room > 0) {
                            buffer.write(chunk, 0, minOf(n, room))
                        }
                    }
                    var i = 0
                    while (i + 1 < n) {
                        val v = ((chunk[i + 1].toInt() shl 8) or
                            (chunk[i].toInt() and 0xFF)).toShort().toInt()
                        val a = if (v < 0) -v else v
                        if (a > peak) peak = a
                        i += 2
                    }
                    if (buffer.size() >= MAX_BYTES) break
                }
            }
        } catch (e: Exception) {
            running = false
            record = null
        }
    }

    /**
     * Stops the capture and returns its PCM bytes — or null when
     * there is too little audio to be a take at all.
     */
    fun stopAndGet(): ByteArray? {
        stopQuietly()
        val bytes = synchronized(buffer) { buffer.toByteArray() }
        return if (bytes.size >= MIN_BYTES) bytes else null
    }

    fun stopQuietly() {
        running = false
        try {
            record?.stop()
        } catch (e: Exception) {
        }
        try {
            record?.release()
        } catch (e: Exception) {
        }
        record = null
        try {
            reader?.join(300)
        } catch (e: Exception) {
        }
        reader = null
    }

    companion object {
        private const val MAX_SECONDS = 30
        private const val MAX_BYTES = LocalAsrEngine.SAMPLE_RATE * 2 * MAX_SECONDS
        private const val MIN_BYTES = LocalAsrEngine.SAMPLE_RATE * 2 / 5 // 0.2 s

        /** Peak above which a capture counts as holding real speech energy. */
        const val SIGNAL_PEAK = 1200
    }
}
