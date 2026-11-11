package com.aula.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Traducción Viva pins (v0.19): the bank is the "not literal"
 * promise — its size and its classic non-literal pairs are pinned,
 * and lookup must survive the way people actually type (caps,
 * missing accents, punctuation, extra spaces). The cloud path is
 * pinned against fixture JSON: parsing, the detected-English
 * second hop, and the honest error states (403 → enable the API,
 * no key → key needed, network → retry) — a failure must never
 * surface an invented translation.
 */
class TraduccionVivaTest {

    // ------------------------------------------------------------ bank

    @Test
    fun `bank size is pinned and well formed`() {
        assertEquals(50, FRASES_VIVAS.size)
        for (f in FRASES_VIVAS) {
            assertTrue("blank es", f.es.isNotBlank())
            assertTrue("blank en", f.en.isNotBlank())
        }
        // No two Spanish entries may collide after normalization:
        // a collision would shadow one of them in the lookup map.
        val claves = FRASES_VIVAS.map { normalizarFrase(it.es) }
        assertEquals(claves.size, claves.distinct().size)
    }

    @Test
    fun `the classic non literal pairs are in the bank`() {
        val pares = mapOf(
            "tengo hambre" to "I'm hungry",
            "tengo sueño" to "I'm sleepy",
            "me muero de risa" to "I'm cracking up",
            "no pasa nada" to "No worries",
            "qué buena onda" to "That's awesome",
            "tengo prisa" to "I'm in a hurry",
            "hace frío" to "It's cold",
            "cuesta un ojo de la cara" to "It costs a fortune",
            "estoy de acuerdo" to "I agree"
        )
        for ((es, en) in pares) {
            val hit = buscarEnBanco(es)
            assertTrue("missing bank entry: $es", hit != null)
            assertEquals(en, hit!!.frase.en)
            assertTrue("spanish lookup must not flag english input", !hit.entradaEnIngles)
        }
    }

    @Test
    fun `bank lookup ignores case accents punctuation and extra spaces`() {
        assertEquals("I'm hungry", buscarEnBanco("  ¡TENGO   Hambre! ")?.frase?.en)
        assertEquals("How's it going?", buscarEnBanco("como estas")?.frase?.en)
        assertEquals("Where's the bathroom?", buscarEnBanco("¿DONDE ESTA EL BAÑO?")?.frase?.en)
        assertEquals("It costs a fortune", buscarEnBanco("Cuesta un ojo de la cara.")?.frase?.en)
        assertEquals("Check, please", buscarEnBanco("la cuenta por favor")?.frase?.en)
    }

    @Test
    fun `bank lookup answers in reverse from the english side`() {
        val hit = buscarEnBanco("I'm hungry")
        assertTrue(hit != null)
        assertEquals("tengo hambre", hit!!.frase.es)
        assertTrue(hit.entradaEnIngles)
        // Typed English without its punctuation still finds it.
        assertEquals("tengo sueño", buscarEnBanco("im sleepy")?.frase?.es)
    }

    @Test
    fun `unknown phrases miss the bank`() {
        assertNull(buscarEnBanco("quiero reservar una mesa para dos personas"))
        assertNull(buscarEnBanco(""))
        assertNull(buscarEnBanco("   "))
    }

    @Test
    fun `normalization collapses and trims`() {
        assertEquals("tengo hambre", normalizarFrase("  ¡Tengo   HAMBRE! "))
        assertEquals("donde esta el bano", normalizarFrase("¿Dónde está el baño?"))
        assertEquals("", normalizarFrase("  ¿?  "))
        // Contractions join: "I'm" and typed "im" share one key.
        assertEquals("im sleepy", normalizarFrase("I'm sleepy"))
        assertEquals("im sleepy", normalizarFrase("im  sleepy"))
    }

    // ------------------------------------------------------------ parsing

    private val jsonEsEn = """
        {"data":{"translations":[{"translatedText":"I'm sleepy","detectedSourceLanguage":"es"}]}}
    """.trimIndent()

    private val jsonDetectadoEn = """
        {"data":{"translations":[{"translatedText":"Where is the pharmacy?","detectedSourceLanguage":"en"}]}}
    """.trimIndent()

    private val jsonEnEs = """
        {"data":{"translations":[{"translatedText":"¿Dónde está la farmacia?","detectedSourceLanguage":"en"}]}}
    """.trimIndent()

    @Test
    fun `parses a successful spanish to english response`() {
        val r = parseRespuestaTraduccion(jsonEsEn)
        assertTrue(r != null)
        assertEquals("I'm sleepy", r!!.texto)
        assertEquals("es", r.idiomaDetectado)
    }

    @Test
    fun `parses a detected english response`() {
        val r = parseRespuestaTraduccion(jsonDetectadoEn)
        assertTrue(r != null)
        assertEquals("Where is the pharmacy?", r!!.texto)
        assertEquals("en", r.idiomaDetectado)
    }

    @Test
    fun `malformed or empty responses parse to null`() {
        assertNull(parseRespuestaTraduccion("{}"))
        assertNull(parseRespuestaTraduccion("esto no es json"))
        assertNull(parseRespuestaTraduccion("""{"data":{"translations":[]}}"""))
        assertNull(parseRespuestaTraduccion("""{"data":{"translations":[{"translatedText":""}]}}"""))
    }

    // ------------------------------------------------------------ resolver

    /** A recording fake cloud call: [responder] gets (texto, destino). */
    private class PeticionFalsa(val responder: (String, String) -> String) {
        val llamadas = mutableListOf<Pair<String, String>>()
        val fn: PeticionTraduccion = { texto, destino, _ ->
            llamadas.add(texto to destino)
            responder(texto, destino)
        }
    }

    @Test
    fun `resolver answers bank hits with no key and no cloud call`() {
        val falsa = PeticionFalsa { _, _ -> throw AssertionError("cloud must not be called") }
        val e = resolverViva("  ¡Tengo hambre! ", "", falsa.fn)
        assertTrue(e is VivaEstado.Traduccion)
        e as VivaEstado.Traduccion
        assertEquals(VivaOrigen.BANCO, e.origen)
        assertEquals("I'm hungry", e.traducida)
        assertEquals("I'm hungry", e.paraEscuchar)
        assertTrue(falsa.llamadas.isEmpty())
    }

    @Test
    fun `resolver speaks the english side when the bank answers in reverse`() {
        val e = resolverViva("I'm sleepy", "", null)
        assertTrue(e is VivaEstado.Traduccion)
        e as VivaEstado.Traduccion
        assertEquals(VivaOrigen.BANCO, e.origen)
        assertEquals("tengo sueño", e.traducida)
        assertEquals("I'm sleepy", e.paraEscuchar)
    }

    @Test
    fun `resolver asks for the key on a bank miss without one`() {
        val falsa = PeticionFalsa { _, _ -> throw AssertionError("cloud must not be called") }
        val e = resolverViva("quiero reservar una mesa para dos", "", falsa.fn)
        assertEquals(VivaEstado.Aviso(VivaFallo.NECESITA_CLAVE), e)
        assertTrue(falsa.llamadas.isEmpty())
        // No fetcher at all (the screen's local pass) decides the same.
        assertEquals(
            VivaEstado.Aviso(VivaFallo.NECESITA_CLAVE),
            resolverViva("quiero reservar una mesa para dos", "clave", null)
        )
    }

    @Test
    fun `resolver flags empty input`() {
        assertEquals(VivaEstado.Aviso(VivaFallo.VACIO), resolverViva("", "clave", null))
        assertEquals(VivaEstado.Aviso(VivaFallo.VACIO), resolverViva("   ", "clave", null))
    }

    @Test
    fun `resolver translates spanish to english through the cloud`() {
        val falsa = PeticionFalsa { _, _ -> jsonEsEn }
        val e = resolverViva("tengo mucho sueño hoy", "clave", falsa.fn)
        assertTrue(e is VivaEstado.Traduccion)
        e as VivaEstado.Traduccion
        assertEquals(VivaOrigen.NUBE, e.origen)
        assertEquals("I'm sleepy", e.traducida)
        assertEquals("I'm sleepy", e.paraEscuchar)
        assertEquals(listOf("tengo mucho sueño hoy" to "en"), falsa.llamadas)
    }

    @Test
    fun `resolver translates english input back to spanish and keeps english for audio`() {
        val falsa = PeticionFalsa { _, destino -> if (destino == "en") jsonDetectadoEn else jsonEnEs }
        val e = resolverViva("Where is the pharmacy?", "clave", falsa.fn)
        assertTrue(e is VivaEstado.Traduccion)
        e as VivaEstado.Traduccion
        assertEquals(VivaOrigen.NUBE, e.origen)
        assertEquals("¿Dónde está la farmacia?", e.traducida)
        assertEquals("Where is the pharmacy?", e.paraEscuchar)
        assertEquals(
            listOf("Where is the pharmacy?" to "en", "Where is the pharmacy?" to "es"),
            falsa.llamadas
        )
    }

    @Test
    fun `resolver maps http 403 to the api not enabled state`() {
        val falsa = PeticionFalsa { _, _ -> throw TraduccionHttpException(403) }
        assertEquals(
            VivaEstado.Aviso(VivaFallo.API_NO_ACTIVADA),
            resolverViva("una frase cualquiera fuera del banco", "clave", falsa.fn)
        )
    }

    @Test
    fun `resolver maps other http errors to the retry state`() {
        val falsa = PeticionFalsa { _, _ -> throw TraduccionHttpException(500) }
        assertEquals(
            VivaEstado.Aviso(VivaFallo.REINTENTAR),
            resolverViva("una frase cualquiera fuera del banco", "clave", falsa.fn)
        )
    }

    @Test
    fun `resolver maps network failure to the retry state`() {
        val falsa = PeticionFalsa { _, _ -> throw IOException("sin red") }
        assertEquals(
            VivaEstado.Aviso(VivaFallo.REINTENTAR),
            resolverViva("una frase cualquiera fuera del banco", "clave", falsa.fn)
        )
    }

    @Test
    fun `resolver never invents a translation on malformed cloud json`() {
        val falsa = PeticionFalsa { _, _ -> "{}" }
        assertEquals(
            VivaEstado.Aviso(VivaFallo.REINTENTAR),
            resolverViva("una frase cualquiera fuera del banco", "clave", falsa.fn)
        )
    }

    @Test
    fun `http failure mapping pins 403 apart from the rest`() {
        assertEquals(VivaFallo.API_NO_ACTIVADA, falloDeHttp(403))
        assertEquals(VivaFallo.REINTENTAR, falloDeHttp(400))
        assertEquals(VivaFallo.REINTENTAR, falloDeHttp(401))
        assertEquals(VivaFallo.REINTENTAR, falloDeHttp(429))
        assertEquals(VivaFallo.REINTENTAR, falloDeHttp(503))
    }
}
