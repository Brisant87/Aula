package com.aula.nativeapp

import android.content.Context
import android.content.SharedPreferences
import android.speech.tts.TextToSpeech
import org.json.JSONArray
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
 * so no database or network is involved. No Firebase, no AI APIs, ever.
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
    fun qualifiedPatterns(): List<PatternStat> {
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

    fun reset() {
        prefs.edit().clear().apply()
    }
}

// ---------------------------------------------------------------- patterns

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
    else -> "cafe"
}

// ---------------------------------------------------------------- voice

/**
 * Device voice with the Samsung fix: try to initialize Android TextToSpeech
 * with the Google engine ("com.google.android.tts") FIRST, because the system
 * default engine fails silently on many Samsung Galaxy setups. If the Google
 * engine cannot initialize, fall back to the default engine.
 *
 * No cloud voice and no API keys in the native scaffold: device TTS only.
 */
class TtsManager(context: Context, private val speedProvider: () -> Float) {

    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = mutableListOf<Pair<String, Boolean>>()
    private val appContext: Context = context.applicationContext

    /**
     * The Guide registers here before speaking: utterance progress
     * (start, character ranges, done) is forwarded so the teacher's
     * mouth moves WITH the voice instead of on an independent timer.
     */
    @Volatile
    var syncListener: android.speech.tts.UtteranceProgressListener? = null

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
                            syncListener?.onStart(id)
                        }

                        override fun onDone(id: String?) {
                            syncListener?.onDone(id)
                        }

                        @Deprecated("Forwarded for older engines")
                        override fun onError(id: String?) {
                            @Suppress("DEPRECATION")
                            syncListener?.onError(id)
                        }

                        override fun onError(id: String?, errorCode: Int) {
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

    fun speak(text: String, slow: Boolean = false) {
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
        try {
            tts?.stop()
        } catch (e: Exception) {
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
        }
        tts = null
        ready = false
    }
}
