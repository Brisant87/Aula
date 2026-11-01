package com.aula.nativeapp

import android.content.Context
import android.content.SharedPreferences
import android.speech.tts.TextToSpeech
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/*
 * Aula native — Mirror engine, settings and voice.
 *
 * Same rules as the web app's Espejo (Mirror):
 *  - An event log (pattern, context, correct, ts), capped at 500.
 *  - A pattern is only NAMED at >= 3 errors across >= 2 distinct contexts,
 *    and only if the learner has not dismissed it ("No es mi problema").
 *  - Transfer successes on different days move a pattern
 *    Activo -> Mejorando (1) -> Dominado (2). A failed transfer restores support.
 *  - A Dominado pattern left unconfirmed for >= 3 days is "retention due";
 *    a failed retention check sends it back to Mejorando with its hint.
 * Storage is SharedPreferences-held JSON (like the web app's localStorage),
 * so no database or network is involved in the learning engine. No Firebase and no Gemini, ever.
 * The one optional network feature is the cloud VOICE with the learner's own Google key
 * (Brian's standing pattern from PuenteLingua/Cognata) — see TtsManager.
 */

object Dates {
    private val fmt = SimpleDateFormat("EEE MMM dd yyyy", Locale.US)
    fun today(): String = fmt.format(Date())
    fun parseMs(s: String): Long = try {
        fmt.parse(s)?.time ?: 0L
    } catch (e: Exception) {
        0L
    }

    /** Day-of-year (1-based), matching the web app's dayIndex(). */
    fun dayIndex(): Int = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
    fun label(): String =
        SimpleDateFormat("EEEE, d 'de' MMMM", Locale("es", "ES")).format(Date())
}

data class MirrorEvent(val pattern: String, val context: String, val correct: Boolean, val ts: Long)

class PatternProgress(
    var successes: Int = 0,
    val days: MutableList<String> = mutableListOf(),
    var support: Int = 1,
    var status: String = "activo", // "activo" | "mejorando" | "dominado"
    var lastRetention: String? = null,
    val retentionDays: MutableList<String> = mutableListOf()
)

data class PatternStat(val key: String, val errors: Int, val contexts: Int)

data class CaminoEntry(var done: Boolean = false, var date: String = "", var score: Int = 0)

class MirrorStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val events: MutableList<MirrorEvent> = mutableListOf()
    val dismissed: MutableList<String> = mutableListOf()
    val progress: MutableMap<String, PatternProgress> = mutableMapOf()
    val camino: MutableMap<String, CaminoEntry> = mutableMapOf()

    init {
        load()
    }

    // ------------------------------------------------------------ storage

    private fun load() {
        val raw = prefs.getString(KEY_JSON, null) ?: return
        try {
            val root = JSONObject(raw)
            val evArr = root.optJSONArray("events") ?: JSONArray()
            for (i in 0 until evArr.length()) {
                val o = evArr.optJSONObject(i) ?: continue
                events.add(
                    MirrorEvent(
                        o.optString("pattern"),
                        o.optString("context"),
                        o.optBoolean("correct"),
                        o.optLong("ts")
                    )
                )
            }
            val disArr = root.optJSONArray("dismissed") ?: JSONArray()
            for (i in 0 until disArr.length()) dismissed.add(disArr.optString(i))
            val progObj = root.optJSONObject("progress") ?: JSONObject()
            val keys = progObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val o = progObj.optJSONObject(k) ?: continue
                val p = PatternProgress(
                    successes = o.optInt("successes", 0),
                    support = o.optInt("support", 1),
                    status = o.optString("status", "activo"),
                    lastRetention = if (o.isNull("lastRetention")) null else o.optString("lastRetention")
                )
                val dArr = o.optJSONArray("days") ?: JSONArray()
                for (i in 0 until dArr.length()) p.days.add(dArr.optString(i))
                val rArr = o.optJSONArray("retentionDays") ?: JSONArray()
                for (i in 0 until rArr.length()) p.retentionDays.add(rArr.optString(i))
                progress[k] = p
            }
            val camObj = root.optJSONObject("camino") ?: JSONObject()
            val ckeys = camObj.keys()
            while (ckeys.hasNext()) {
                val k = ckeys.next()
                val o = camObj.optJSONObject(k) ?: continue
                camino[k] = CaminoEntry(o.optBoolean("done"), o.optString("date"), o.optInt("score"))
            }
        } catch (e: Exception) {
            // Corrupt diary: start clean rather than crash (same rule as the web app).
            events.clear(); dismissed.clear(); progress.clear(); camino.clear()
        }
    }

    fun save() {
        try {
            val root = JSONObject()
            val evArr = JSONArray()
            for (e in events) {
                evArr.put(
                    JSONObject()
                        .put("pattern", e.pattern)
                        .put("context", e.context)
                        .put("correct", e.correct)
                        .put("ts", e.ts)
                )
            }
            root.put("events", evArr)
            val disArr = JSONArray()
            for (d in dismissed) disArr.put(d)
            root.put("dismissed", disArr)
            val progObj = JSONObject()
            for ((k, p) in progress) {
                val o = JSONObject()
                    .put("successes", p.successes)
                    .put("support", p.support)
                    .put("status", p.status)
                val dArr = JSONArray(); for (d in p.days) dArr.put(d)
                o.put("days", dArr)
                val rArr = JSONArray(); for (d in p.retentionDays) rArr.put(d)
                o.put("retentionDays", rArr)
                if (p.lastRetention != null) o.put("lastRetention", p.lastRetention)
                progObj.put(k, o)
            }
            root.put("progress", progObj)
            val camObj = JSONObject()
            for ((k, c) in camino) {
                camObj.put(
                    k, JSONObject()
                        .put("done", c.done)
                        .put("date", c.date)
                        .put("score", c.score)
                )
            }
            root.put("camino", camObj)
            prefs.edit().putString(KEY_JSON, root.toString()).apply()
        } catch (e: Exception) {
            // A failed save must never break the lesson.
        }
    }

    fun reset() {
        events.clear(); dismissed.clear(); progress.clear(); camino.clear()
        prefs.edit().remove(KEY_JSON).apply()
    }

    // ------------------------------------------------------------ engine

    fun logEvent(pattern: String, context: String, correct: Boolean) {
        events.add(MirrorEvent(pattern, context, correct, System.currentTimeMillis()))
        while (events.size > 500) events.removeAt(0)
        save()
    }

    fun dismissPattern(pattern: String) {
        if (pattern !in dismissed) dismissed.add(pattern)
        save()
    }

    fun progressOf(key: String): PatternProgress? = progress[key]

    fun statusOf(key: String): String = progress[key]?.status ?: "activo"

    fun supportLevel(key: String): Int = progress[key]?.support ?: 1

    /** Transfer successes on different days: Mejorando (1) -> Dominado (2). */
    fun recordTransfer(key: String, success: Boolean): PatternProgress {
        val p = progress.getOrPut(key) { PatternProgress() }
        val today = Dates.today()
        if (success) {
            if (today !in p.days) {
                p.successes++
                p.days.add(today)
            }
            p.status = if (p.successes >= 2) "dominado" else "mejorando"
            p.support = 0
        } else {
            p.status = "activo"
            p.support = 1
        }
        save()
        return p
    }

    fun retentionDue(key: String): Boolean {
        val p = progress[key] ?: return false
        if (p.status != "dominado") return false
        val today = Dates.today()
        if (p.lastRetention == today) return false
        val last = p.retentionDays.lastOrNull() ?: p.days.lastOrNull() ?: return false
        val t = Dates.parseMs(last)
        if (t == 0L) return false
        return (System.currentTimeMillis() - t) >= 3L * 86400000L
    }

    fun retentionDueKeys(): List<String> = progress.keys.filter { retentionDue(it) }

    fun recordRetention(key: String, success: Boolean) {
        val p = progress[key] ?: return
        val today = Dates.today()
        p.lastRetention = today
        if (success) {
            p.retentionDays.add(today)
            p.status = "dominado"
            p.support = 0
        } else {
            p.status = "mejorando"
            p.support = 1
            p.successes = 1
        }
        save()
    }

    /** Last transfer-context result for a pattern at or after [fromIndex]; null if none. */
    fun lastTransferResultSince(key: String, fromIndex: Int): Boolean? {
        var i = events.size - 1
        while (i >= maxOf(0, fromIndex)) {
            val ev = events[i]
            if (ev.context == "transfer" && ev.pattern == key) return ev.correct
            i--
        }
        return null
    }

    /** A card ONLY with >= 3 errors in >= 2 distinct contexts, and not dismissed. */
    fun qualifiedPatterns(): List<PatternStat> = qualifiedPatternsOf(events, dismissed)

    fun activePatternsRanked(): List<PatternStat> =
        qualifiedPatterns().filter { statusOf(it.key) != "dominado" }

    fun saveCamino(levelId: String, score: Int) {
        camino[levelId] = CaminoEntry(done = true, date = Dates.today(), score = score)
        save()
    }

    companion object {
        private const val PREFS_NAME = "aula_mirror"
        private const val KEY_JSON = "store_json_v1"
    }
}

// ---------------------------------------------------------------- settings

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("aula_settings_v1", Context.MODE_PRIVATE)

    var speed: Float
        get() = prefs.getFloat("speed", 1f)
        set(v) {
            prefs.edit().putFloat("speed", v).apply()
        }

    var guide: Boolean
        get() = prefs.getBoolean("guide", true)
        set(v) {
            prefs.edit().putBoolean("guide", v).apply()
        }

    /** Chosen Chirp voice for the cloud teacher (Ajustes). */
    var voice: String
        get() = prefs.getString("voice", "en-US-Chirp3-HD-Aoede") ?: "en-US-Chirp3-HD-Aoede"
        set(v) {
            prefs.edit().putString("voice", v).apply()
        }

    fun reset() {
        prefs.edit().clear().apply()
    }
}

// ---------------------------------------------------------------- patterns

/**
 * The Mirror naming rule as a pure function of the event log, so it
 * can be unit-tested without Android: a pattern is named ONLY with
 * >= 3 error events across >= 2 distinct contexts, and never when
 * the learner dismissed it. [MirrorStore.qualifiedPatterns] is this
 * function over the live log — there is one rule, not two.
 */
fun qualifiedPatternsOf(events: List<MirrorEvent>, dismissed: List<String>): List<PatternStat> {
    val errors = mutableMapOf<String, Int>()
    val contexts = mutableMapOf<String, MutableSet<String>>()
    for (ev in events) {
        if (ev.correct) continue
        errors[ev.pattern] = (errors[ev.pattern] ?: 0) + 1
        contexts.getOrPut(ev.pattern) { mutableSetOf() }.add(ev.context)
    }
    val out = mutableListOf<PatternStat>()
    for ((k, n) in errors) {
        val nctx = contexts[k]?.size ?: 0
        if (n >= 3 && nctx >= 2 && k !in dismissed) {
            out.add(PatternStat(k, n, nctx))
        }
    }
    out.sortByDescending { it.errors }
    return out
}

/**
 * The phantom-E family is ONE Mirror pattern, not one key per word:
 * adding a vowel before an initial /s/ ("esister", "esalad",
 * "esoccer") is a single habit, and a per-word key could never reach
 * the naming rule (the Guide — the only other logger of
 * pronunciation events — teaches four unrelated words). Live
 * conversation suspects log this key in the "conversaciones"
 * context; GuideCard self-ratings on an s-trap guide word log it in
 * the Guide's own contexts, which is the family's honest second
 * surface. Per-word detail lives in the live session's recap (the
 * store's event shape has no detail field).
 */
const val PHANTOM_E_PATTERN_KEY = "pronunciation_phantom_e"

/**
 * True when [word] belongs to the phantom-E family: it starts with
 * "s" but not "sh" — the same gate PronunciationBoundary applies to
 * trap forms. Of the original guide words, none qualifies (ship and
 * sheep are "sh"; went and three do not start with "s"), so this
 * gate leaves every existing Guide rating exactly as it was.
 */
fun isPhantomEFamilyWord(word: String): Boolean {
    val w = word.trim().lowercase()
    return w.length > 1 && w.startsWith("s") && !w.startsWith("sh")
}

data class PatternInfo(val name: String, val explain: String, val kind: String, val word: String = "")

val PATTERN_INFO: Map<String, PatternInfo> = mapOf(
    "syntax_past_tense" to PatternInfo(
        "El pasado con «yesterday»",
        "Cuando hablas de ayer, el verbo cambia: go → went, buy → bought, visit → visited.", "syntax"
    ),
    "syntax_word_order" to PatternInfo(
        "El orden de las palabras",
        "En inglés el adjetivo va antes del sustantivo («red car») y las frases siguen su orden fijo.", "syntax"
    ),
    "cognates" to PatternInfo(
        "El significado de los cognados",
        "Algunos cognados engañan; conviene repasar su significado real en la Biblioteca.", "cognates"
    ),
    "syntax_present_questions" to PatternInfo(
        "Las preguntas en presente (Do / Does)",
        "En presente, preguntas y negaciones usan «do/does» y el verbo va en su forma base: Does she live…?, no «lives».", "syntax"
    ),
    "conversation_turn" to PatternInfo(
        "Responder en conversación",
        "Las respuestas completas y corteses («That is all, thank you») funcionan mejor que traducir palabra por palabra.", "syntax"
    ),
    "pronunciation_phantom_e" to PatternInfo(
        "La «e» fantasma antes de la /s/",
        "Cuando una palabra empieza con el silbido /s/ (sister, salad, soccer, store…), le añades una vocal que no va: «esister». En inglés la /s/ arranca directa: primero el silbido y luego la palabra, sin vocal antes. En la Guía lo practicas con «sister».", "guide", "sister"
    ),
    "listening" to PatternInfo(
        "Escuchar con precisión",
        "Distinguir lo que realmente suena — pasado o presente, una palabra u otra — antes de leerlo. Se entrena en Escucha (Conversaciones).", "listening"
    )
)

fun patternInfo(key: String): PatternInfo {
    PATTERN_INFO[key]?.let { return it }
    if (key.startsWith("pronunciation:")) {
        val w = key.removePrefix("pronunciation:")
        return PatternInfo(
            "La pronunciación de «$w»",
            "Tu propia nota dice que esta palabra todavía no sale; mírala sonido por sonido en la Guía.",
            "guide", w
        )
    }
    return PatternInfo(
        "Este punto de práctica",
        "Aparece en varias actividades; vale la pena practicarlo.", "syntax"
    )
}

/** Unseen transfer items per pattern — never the guided-practice items. */
fun transferItemsFor(key: String): List<TransferItem> {
    if (key == "listening") {
        return LISTENING.filter { it.transfer }.map { l ->
            TransferItem(
                id = l.id, pattern = "listening", kind = "listening",
                prompt = l.audio, options = l.options, answer = l.answer,
                rule = "", trap = l.note
            )
        }
    }
    if (key.startsWith("pronunciation:")) {
        val w = key.removePrefix("pronunciation:")
        return if (GUIDE_WORDS.containsKey(w)) {
            listOf(TransferItem(id = "tg-$w", pattern = key, kind = "guide", word = w))
        } else {
            emptyList()
        }
    }
    return TRANSFER.filter { it.pattern == key }
}

fun poolFor(patternKey: String): List<SyntaxItem> {
    val p = SYNTAX.filter { it.pattern == patternKey }
    return if (p.isNotEmpty()) p else SYNTAX.filter { it.pattern == "syntax_word_order" }
}

fun convForPattern(key: String): String = when (key) {
    "syntax_past_tense" -> "ayer"
    "syntax_present_questions" -> "cafe"
    "syntax_word_order" -> "cafe"
    "cognates" -> "cafe"
    "conversation_turn" -> "hotel"
    "pronunciation_phantom_e" -> "ayer"
    else -> "cafe"
}

// ---------------------------------------------------------------- voice

/**
 * The learner's own Google Cloud voice key, encrypted on the device
 * (Android Keystore) and excluded from backup/transfer in the XML rules.
 * Every failure falls back to "no key" (device voice): a corrupt
 * encrypted store must never crash the app at startup.
 */
class VoiceKeyStore(context: Context) {

    private val appContext: Context = context.applicationContext

    @Volatile
    private var prefs: SharedPreferences? = null

    @Volatile
    private var broken = false

    private fun prefs(): SharedPreferences? {
        if (broken) return null
        prefs?.let { return it }
        return try {
            create()
        } catch (e: Exception) {
            // One retry after wiping a possibly corrupt store file.
            try {
                File(appContext.applicationInfo.dataDir + "/shared_prefs/aula_voice_key.xml").delete()
            } catch (ignored: Exception) {
            }
            try {
                create()
            } catch (e2: Exception) {
                broken = true
                null
            }
        }
    }

    private fun create(): SharedPreferences {
        val master = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val p = EncryptedSharedPreferences.create(
            appContext,
            "aula_voice_key",
            master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        prefs = p
        return p
    }

    fun get(): String = try {
        prefs()?.getString("key", "") ?: ""
    } catch (e: Exception) {
        ""
    }

    fun set(key: String) {
        try {
            prefs()?.edit()?.putString("key", key.trim())?.apply()
        } catch (e: Exception) {
        }
    }

    fun clear() {
        try {
            prefs()?.edit()?.remove("key")?.apply()
        } catch (e: Exception) {
        }
    }

    fun has(): Boolean = get().isNotEmpty()
}

private class HttpVoiceException(val code: Int) : Exception("HTTP " + code)

/**
 * Voice, two engines:
 *
 * 1. Optional AI cloud voice — Google Cloud Text-to-Speech, Chirp 3 HD
 *    (female teacher voice), falling back to WaveNet, using the
 *    LEARNER'S OWN API key from Ajustes: the same pattern as Brian's
 *    other apps. The audio plays through MediaPlayer, and while it
 *    plays a ticker reports the spoken character position to
 *    [syncListener], so the Guide's mouth follows the REAL audio.
 *    Any failure (no key, no network, 4-second timeout, HTTP error)
 *    falls back to the device voice for that utterance, silently.
 *    Fetched clips are cached on the device, so repeats are instant
 *    and free.
 *
 * 2. Device voice with the Samsung fix: initialize Android
 *    TextToSpeech with the Google engine ("com.google.android.tts")
 *    FIRST, because the system default engine fails silently on many
 *    Samsung Galaxy setups; fall back to the default engine.
 */
class TtsManager(
    context: Context,
    private val speedProvider: () -> Float,
    private val keyProvider: () -> String,
    private val voiceProvider: () -> String
) {

    /** True while either engine is actually producing sound. */
    val speaking = MutableStateFlow(false)

    /** Human-readable state of the last cloud-voice attempt (Ajustes). */
    val cloudStatus = MutableStateFlow("Sin probar todavía: pulsa «Probar voz».")

    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = mutableListOf<Pair<String, Boolean>>()
    private val appContext: Context = context.applicationContext

    /**
     * The Guide registers here before speaking: utterance progress
     * (start, character ranges, done) is forwarded so the teacher's
     * mouth moves WITH the voice instead of on an independent timer.
     * Both engines feed this same listener.
     */
    @Volatile
    var syncListener: android.speech.tts.UtteranceProgressListener? = null

    // ---- cloud voice state ----
    private val mainHandler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var ticker: Runnable? = null
    private var currentUtteranceId: String? = null
    private var currentText: String = ""
    private var utteranceSeq = 0
    private val fetchExecutor = Executors.newSingleThreadExecutor()

    @Volatile
    private var cloudBlocked = false

    @Volatile
    private var chirpBlocked = false

    @Volatile
    private var netFailures = 0
    private val cloudCacheDir = File(appContext.cacheDir, "cloudtts")

    init {
        create("com.google.android.tts")
    }

    private fun create(engine: String?) {
        val listener = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                try {
                    tts?.language = Locale.US
                } catch (e: Exception) {
                    // Language data missing: speech may stay silent, UI still works.
                }
                try {
                    tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                        override fun onStart(id: String?) {
                            speaking.value = true
                            syncListener?.onStart(id)
                        }

                        override fun onDone(id: String?) {
                            speaking.value = false
                            syncListener?.onDone(id)
                        }

                        @Deprecated("Forwarded for older engines")
                        override fun onError(id: String?) {
                            speaking.value = false
                            @Suppress("DEPRECATION")
                            syncListener?.onError(id)
                        }

                        override fun onError(id: String?, errorCode: Int) {
                            speaking.value = false
                            @Suppress("DEPRECATION")
                            syncListener?.onError(id)
                        }

                        override fun onRangeStart(id: String?, start: Int, end: Int, frame: Int) {
                            syncListener?.onRangeStart(id, start, end, frame)
                        }
                    })
                } catch (e: Exception) {
                    // No progress callbacks on this engine: the Guide's
                    // fallback timer keeps the mouth moving.
                }
                ready = true
                val copy = pending.toList()
                pending.clear()
                for ((text, slow) in copy) speakNow(text, slow)
            } else {
                ready = false
                if (engine != null) {
                    try {
                        tts?.shutdown()
                    } catch (e: Exception) {
                    }
                    tts = null
                    create(null) // fall back to the device default engine
                }
            }
        }
        tts = if (engine != null) {
            TextToSpeech(appContext, listener, engine)
        } else {
            TextToSpeech(appContext, listener)
        }
    }

    /** Saving or clearing the key re-enables the cloud voice. */
    fun onKeyChanged() {
        cloudBlocked = false
        chirpBlocked = false
        netFailures = 0
    }

    fun speak(text: String, slow: Boolean = false) {
        val key = try {
            keyProvider().trim()
        } catch (e: Exception) {
            ""
        }
        if (key.isNotEmpty() && !cloudBlocked) {
            speakCloud(text, slow, key)
        } else {
            speakDevice(text, slow)
        }
    }

    // ---------------- cloud voice ----------------

    private fun speakCloud(text: String, slow: Boolean, key: String) {
        stopPlayer()
        try {
            tts?.stop()
        } catch (e: Exception) {
        }
        val id = "cloud-" + (++utteranceSeq)
        currentUtteranceId = id
        fetchExecutor.execute {
            try {
                val file = fetchAudio(text, key)
                netFailures = 0
                mainHandler.post {
                    if (currentUtteranceId == id) playCloudFile(file, text, slow, id)
                }
            } catch (e: Exception) {
                mainHandler.post {
                    if (currentUtteranceId == id) speakDevice(text, slow)
                }
            }
        }
    }

    private fun fetchAudio(text: String, key: String): File {
        if (!cloudCacheDir.exists()) cloudCacheDir.mkdirs()
        val chirp = voiceProvider().ifBlank { CHIRP_VOICE }
        val voices = if (chirpBlocked) {
            listOf(WAVENET_VOICE)
        } else {
            listOf(chirp, WAVENET_VOICE)
        }
        var failure: Exception? = null
        for (voice in voices) {
            val cacheFile = File(cloudCacheDir, md5(text + "|" + voice) + ".mp3")
            if (cacheFile.exists() && cacheFile.length() > 0) {
                cloudStatus.value = "Voz IA activa ✓ · $voice (desde la caché del teléfono)"
                return cacheFile
            }
            try {
                val bytes = synthesize(text, voice, key)
                cacheFile.writeBytes(bytes)
                cloudStatus.value = "Voz IA activa ✓ · $voice"
                return cacheFile
            } catch (e: HttpVoiceException) {
                cloudStatus.value = when (e.code) {
                    400 -> "Google rechazó la petición (HTTP 400): puede ser la voz elegida. Prueba otra voz o avisa a Pablo."
                    401, 403 -> "Tu clave fue rechazada (HTTP ${e.code}): puede estar restringida a web o sin la API de voz activada. Mientras tanto suena la voz del teléfono."
                    else -> "La voz IA falló (HTTP ${e.code}): suena la voz del teléfono."
                }
                if (voice == chirp) {
                    chirpBlocked = true
                    failure = e
                } else {
                    cloudBlocked = true
                    throw e
                }
            } catch (e: Exception) {
                failure = e
            }
        }
        cloudStatus.value = "Sin conexión con la voz IA: suena la voz del teléfono. Revisa tu internet y prueba otra vez."
        netFailures++
        if (netFailures >= 3) cloudBlocked = true
        throw (failure ?: Exception("cloud voice failed"))
    }

    private fun synthesize(text: String, voice: String, key: String): ByteArray {
        val body = JSONObject()
        body.put("input", JSONObject().put("text", text))
        body.put("voice", JSONObject().put("languageCode", "en-US").put("name", voice))
        body.put("audioConfig", JSONObject().put("audioEncoding", "MP3"))
        val conn = URL(
            "https://texttospeech.googleapis.com/v1/text:synthesize?key=" +
                java.net.URLEncoder.encode(key, "UTF-8")
        ).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 4000
        conn.readTimeout = 4000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.setRequestProperty("X-Goog-Api-Key", key)
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        if (code !in 200..299) throw HttpVoiceException(code)
        val resp = conn.inputStream.bufferedReader().use { it.readText() }
        val b64 = JSONObject(resp).optString("audioContent")
        if (b64.isEmpty()) throw Exception("empty audioContent")
        return Base64.decode(b64, Base64.DEFAULT)
    }

    private fun playCloudFile(file: File, text: String, slow: Boolean, id: String) {
        stopPlayer()
        currentText = text
        try {
            val mp = MediaPlayer()
            player = mp
            mp.setDataSource(file.absolutePath)
            mp.setOnPreparedListener {
                try {
                    val spd = ((if (slow) 0.68f else 1f) * speedProvider()).coerceIn(0.5f, 1.5f)
                    mp.playbackParams = mp.playbackParams.setSpeed(spd)
                } catch (e: Exception) {
                }
                try {
                    mp.start()
                    speaking.value = true
                    syncListener?.onStart(id)
                    startTicker(id)
                } catch (e: Exception) {
                    speaking.value = false
                    @Suppress("DEPRECATION")
                    syncListener?.onError(id)
                }
            }
            mp.setOnCompletionListener {
                stopTicker()
                speaking.value = false
                syncListener?.onDone(id)
            }
            mp.setOnErrorListener { _, _, _ ->
                stopTicker()
                speaking.value = false
                @Suppress("DEPRECATION")
                syncListener?.onError(id)
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            @Suppress("DEPRECATION")
            syncListener?.onError(id)
        }
    }

    private fun startTicker(id: String) {
        stopTicker()
        var lastPos = -1
        val r = object : Runnable {
            override fun run() {
                val mp = player
                if (mp != null && currentUtteranceId == id) {
                    try {
                        if (mp.isPlaying) {
                            val dur = mp.duration
                            if (dur > 0 && currentText.isNotEmpty()) {
                                val cp = (mp.currentPosition.toDouble() / dur.toDouble() * currentText.length)
                                    .toInt()
                                    .coerceIn(0, currentText.length - 1)
                                if (cp != lastPos) {
                                    lastPos = cp
                                    syncListener?.onRangeStart(id, cp, minOf(cp + 1, currentText.length), 0)
                                }
                            }
                        }
                    } catch (e: Exception) {
                    }
                    mainHandler.postDelayed(this, 40)
                }
            }
        }
        ticker = r
        mainHandler.postDelayed(r, 40)
    }

    private fun stopTicker() {
        ticker?.let { mainHandler.removeCallbacks(it) }
        ticker = null
    }

    private fun stopPlayer() {
        stopTicker()
        try {
            player?.stop()
        } catch (e: Exception) {
        }
        try {
            player?.release()
        } catch (e: Exception) {
        }
        player = null
    }

    private fun md5(s: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    // ---------------- device voice ----------------

    private fun speakDevice(text: String, slow: Boolean) {
        stopPlayer()
        currentUtteranceId = null
        if (ready) {
            speakNow(text, slow)
        } else {
            pending.add(text to slow)
        }
    }

    private fun speakNow(text: String, slow: Boolean) {
        val t = tts ?: return
        try {
            val base = if (slow) 0.65f else 1f
            t.setSpeechRate(base * speedProvider())
            t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "aula-" + System.nanoTime())
        } catch (e: Exception) {
            // A voice failure is never the learner's problem: stay silent, never crash.
        }
    }

    fun stop() {
        currentUtteranceId = null
        speaking.value = false
        stopPlayer()
        try {
            tts?.stop()
        } catch (e: Exception) {
        }
    }

    fun shutdown() {
        stop()
        try {
            fetchExecutor.shutdownNow()
        } catch (e: Exception) {
        }
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
        }
        tts = null
        ready = false
    }

    companion object {
        private const val CHIRP_VOICE = "en-US-Chirp3-HD-Aoede"
        private const val WAVENET_VOICE = "en-US-Wavenet-F"
    }
}
