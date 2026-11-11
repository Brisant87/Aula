package com.aula.nativeapp

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer

/*
 * Traducción Viva (v0.19) — the testers' ask: the learner writes a
 * word or phrase and gets back how a native speaker actually says
 * it day to day, with its voice — not a literal, robotic rendering.
 *
 * Two layers, in order:
 *
 * 1. THE BANK (this file): everyday Spanish phrases mapped to
 *    hand-written NATURAL English — "tengo hambre" is "I'm hungry",
 *    never "I have hunger". A bank hit is instant, offline and
 *    key-free, and it is the heart of the "not literal" promise.
 *    The same bank also answers in reverse (type the English side,
 *    get the Spanish).
 *
 * 2. THE CLOUD, only for phrases outside the bank: Google Cloud
 *    Translation API v2 over plain REST, with the learner's OWN
 *    Google Cloud key — the same key Ajustes already stores for
 *    the cloud voice (VoiceKeyStore). No new key, no account, no
 *    Firebase, no Gemini. Honest states only: on any failure the
 *    screen shows a plain message and NEVER a guessed translation.
 *
 * The audio is not here: the screen speaks the English through
 * TtsManager.speak, the same routing dynamic teacher lines use
 * (bundled clip first if one matches, then Chirp 3 HD → WaveNet →
 * device voice).
 */

// ------------------------------------------------------------------ bank

data class FraseViva(val es: String, val en: String)

/**
 * The phrase bank. The English is written the way people talk,
 * not the way a dictionary glosses: idioms land on their English
 * idiom ("cuesta un ojo de la cara" → "It costs a fortune"), and
 * the Spanish "tener" feelings land on English "to be".
 */
val FRASES_VIVAS: List<FraseViva> = listOf(
    // Saludos y despedidas
    FraseViva("buenos días", "Good morning"),
    FraseViva("buenas tardes", "Good afternoon"),
    FraseViva("buenas noches", "Good night"),
    FraseViva("¿cómo estás?", "How's it going?"),
    FraseViva("muy bien, gracias", "Doing great, thanks"),
    FraseViva("hasta luego", "See you later"),
    FraseViva("nos vemos mañana", "See you tomorrow"),
    FraseViva("que tengas un buen día", "Have a nice day"),
    // El cuerpo y los sentimientos: en español se «tiene», en inglés se «es/está»
    FraseViva("tengo hambre", "I'm hungry"),
    FraseViva("tengo sed", "I'm thirsty"),
    FraseViva("tengo sueño", "I'm sleepy"),
    FraseViva("tengo prisa", "I'm in a hurry"),
    FraseViva("tengo miedo", "I'm scared"),
    FraseViva("tengo calor", "I'm hot"),
    FraseViva("tengo frío", "I'm cold"),
    FraseViva("hace frío", "It's cold"),
    FraseViva("estoy cansado", "I'm tired"),
    FraseViva("estoy cansada", "I'm tired"),
    FraseViva("estoy de acuerdo", "I agree"),
    FraseViva("me duele la cabeza", "I have a headache"),
    FraseViva("no me siento bien", "I don't feel well"),
    FraseViva("me muero de risa", "I'm cracking up"),
    FraseViva("no te preocupes", "Don't worry"),
    FraseViva("no pasa nada", "No worries"),
    FraseViva("qué lástima", "That's too bad"),
    FraseViva("me encanta", "I love it"),
    // Cortesía
    FraseViva("muchas gracias", "Thanks so much"),
    FraseViva("de nada", "You're welcome"),
    FraseViva("con permiso", "Excuse me"),
    FraseViva("perdón", "Sorry"),
    FraseViva("claro que sí", "Of course"),
    FraseViva("por favor", "Please"),
    // Comer y comprar
    FraseViva("está riquísimo", "It's delicious"),
    FraseViva("la cuenta, por favor", "Check, please"),
    FraseViva("¿cuánto cuesta?", "How much is it?"),
    FraseViva("cuesta un ojo de la cara", "It costs a fortune"),
    FraseViva("solo estoy mirando", "I'm just looking"),
    // En la calle
    FraseViva("¿dónde está el baño?", "Where's the bathroom?"),
    FraseViva("¿dónde está la estación?", "Where's the station?"),
    FraseViva("está lejos", "It's far"),
    FraseViva("a la derecha", "To the right"),
    FraseViva("a la izquierda", "To the left"),
    FraseViva("todo recto", "Straight ahead"),
    // Charla de cada día
    FraseViva("qué buena onda", "That's awesome"),
    FraseViva("no entiendo", "I don't understand"),
    FraseViva("es broma", "Just kidding"),
    FraseViva("¿de verdad?", "Really?"),
    FraseViva("tengo que irme", "I have to go"),
    FraseViva("ya voy", "I'm coming"),
    FraseViva("vamos", "Let's go")
)

/**
 * Lookup normalization: case, accents, punctuation and extra
 * spaces must not matter — "  ¡TENGO   Hambre! ", "tengo hambre"
 * and "Téngo hambre" are the same phrase. Anything that is not a
 * letter or a digit becomes a space, runs collapse, ends trim.
 * Apostrophes vanish instead of splitting: English contractions
 * join ("I'm" and typed "im" are the same lookup key).
 */
fun normalizarFrase(texto: String): String {
    val sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
    val sb = StringBuilder()
    var ultimoFueEspacio = true // also trims the leading edge
    for (c in sinAcentos.lowercase()) {
        if (c == '\'' || c == '’') {
            continue // contraction glue, not a word break
        }
        if (c.isLetterOrDigit()) {
            sb.append(c)
            ultimoFueEspacio = false
        } else if (!ultimoFueEspacio) {
            sb.append(' ')
            ultimoFueEspacio = true
        }
    }
    return sb.toString().trim()
}

private val BANCO_POR_ESPANOL: Map<String, FraseViva> by lazy {
    FRASES_VIVAS.associateBy { normalizarFrase(it.es) }
}

private val BANCO_POR_INGLES: Map<String, FraseViva> by lazy {
    // First Spanish entry wins a shared English rendering
    // ("estoy cansado" before "estoy cansada").
    val m = LinkedHashMap<String, FraseViva>()
    for (f in FRASES_VIVAS) m.putIfAbsent(normalizarFrase(f.en), f)
    m
}

/** A bank hit; [entradaEnIngles] marks the reverse direction. */
data class HallazgoBanco(val frase: FraseViva, val entradaEnIngles: Boolean)

fun buscarEnBanco(texto: String): HallazgoBanco? {
    val clave = normalizarFrase(texto)
    if (clave.isEmpty()) return null
    BANCO_POR_ESPANOL[clave]?.let { return HallazgoBanco(it, entradaEnIngles = false) }
    BANCO_POR_INGLES[clave]?.let { return HallazgoBanco(it, entradaEnIngles = true) }
    return null
}

// ------------------------------------------------------------------ states

enum class VivaOrigen { BANCO, NUBE }

enum class VivaFallo { VACIO, NECESITA_CLAVE, API_NO_ACTIVADA, REINTENTAR }

/** What the screen shows after pressing «Traducir». */
sealed class VivaEstado {
    /**
     * A real translation. [paraEscuchar] is always the ENGLISH
     * text: the result when translating into English, the
     * learner's own input when the result is Spanish.
     */
    data class Traduccion(
        val original: String,
        val traducida: String,
        val paraEscuchar: String,
        val origen: VivaOrigen
    ) : VivaEstado()

    /** An honest non-result: a plain message, never a guess. */
    data class Aviso(val fallo: VivaFallo) : VivaEstado()
}

/** 403 means the Cloud Translation API is not enabled on the key's project. */
fun falloDeHttp(code: Int): VivaFallo =
    if (code == 403) VivaFallo.API_NO_ACTIVADA else VivaFallo.REINTENTAR

// ------------------------------------------------------------------ cloud

class TraduccionHttpException(val code: Int) : Exception("HTTP $code")

/** One raw Translation API call: returns the response body, throws on failure. */
typealias PeticionTraduccion = (texto: String, destino: String, clave: String) -> String

/**
 * The real call — Google Cloud Translation API v2, mirroring the
 * TTS client's style in Engine.kt (HttpURLConnection, 4-second
 * timeouts, the key in the URL and the X-Goog-Api-Key header).
 * Source language is omitted on purpose: Google detects it.
 */
val peticionTraduccionReal: PeticionTraduccion = { texto, destino, clave ->
    val params = "q=" + URLEncoder.encode(texto, "UTF-8") +
        "&target=" + URLEncoder.encode(destino, "UTF-8") +
        "&format=text"
    val conn = URL(
        "https://translation.googleapis.com/language/translate/v2?key=" +
            URLEncoder.encode(clave, "UTF-8")
    ).openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.connectTimeout = 4000
    conn.readTimeout = 4000
    conn.doOutput = true
    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
    conn.setRequestProperty("X-Goog-Api-Key", clave)
    conn.outputStream.use { it.write(params.toByteArray(Charsets.UTF_8)) }
    val code = conn.responseCode
    if (code !in 200..299) throw TraduccionHttpException(code)
    conn.inputStream.bufferedReader().use { it.readText() }
}

data class RespuestaTraduccion(val texto: String, val idiomaDetectado: String?)

/**
 * Parse a v2 response body: data.translations[0]. Null on any
 * malformed or empty body — a parse failure must surface as the
 * retry state, never as an invented translation.
 */
fun parseRespuestaTraduccion(json: String): RespuestaTraduccion? {
    return try {
        val translations = JSONObject(json)
            .optJSONObject("data")
            ?.optJSONArray("translations")
            ?: return null
        if (translations.length() == 0) return null
        val first = translations.optJSONObject(0) ?: return null
        val texto = first.optString("translatedText")
        if (texto.isBlank()) return null
        val detectado = first.optString("detectedSourceLanguage").ifBlank { null }
        RespuestaTraduccion(texto, detectado)
    } catch (e: Exception) {
        null
    }
}

private fun esIngles(idioma: String?): Boolean =
    idioma != null && (idioma.equals("en", ignoreCase = true) || idioma.startsWith("en-", ignoreCase = true))

/**
 * The whole decision, pure and testable: bank first (instant, no
 * key, no network — [peticion] is never invoked), then the key
 * check, then the cloud. English input is detected by the first
 * call and translated back into Spanish by a second one; the
 * audio text is the English side either way.
 */
fun resolverViva(textoCrudo: String, clave: String, peticion: PeticionTraduccion?): VivaEstado {
    val texto = textoCrudo.trim()
    if (texto.isEmpty()) return VivaEstado.Aviso(VivaFallo.VACIO)

    buscarEnBanco(texto)?.let { hallazgo ->
        return if (hallazgo.entradaEnIngles) {
            VivaEstado.Traduccion(
                original = texto,
                traducida = hallazgo.frase.es,
                paraEscuchar = hallazgo.frase.en,
                origen = VivaOrigen.BANCO
            )
        } else {
            VivaEstado.Traduccion(
                original = texto,
                traducida = hallazgo.frase.en,
                paraEscuchar = hallazgo.frase.en,
                origen = VivaOrigen.BANCO
            )
        }
    }

    if (clave.isBlank() || peticion == null) {
        return VivaEstado.Aviso(VivaFallo.NECESITA_CLAVE)
    }

    return try {
        val primera = parseRespuestaTraduccion(peticion(texto, "en", clave))
            ?: return VivaEstado.Aviso(VivaFallo.REINTENTAR)
        if (esIngles(primera.idiomaDetectado)) {
            // The learner wrote English: give them the Spanish,
            // and the audio speaks their own English input.
            val segunda = parseRespuestaTraduccion(peticion(texto, "es", clave))
                ?: return VivaEstado.Aviso(VivaFallo.REINTENTAR)
            VivaEstado.Traduccion(
                original = texto,
                traducida = segunda.texto,
                paraEscuchar = texto,
                origen = VivaOrigen.NUBE
            )
        } else {
            VivaEstado.Traduccion(
                original = texto,
                traducida = primera.texto,
                paraEscuchar = primera.texto,
                origen = VivaOrigen.NUBE
            )
        }
    } catch (e: TraduccionHttpException) {
        VivaEstado.Aviso(falloDeHttp(e.code))
    } catch (e: IOException) {
        VivaEstado.Aviso(VivaFallo.REINTENTAR)
    } catch (e: Exception) {
        VivaEstado.Aviso(VivaFallo.REINTENTAR)
    }
}
