package com.aula.nativeapp

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.StringReader

/**
 * Big-lexicon pins (v0.20): the 50,000-entry word bank that lives
 * under the curated phrases in Traducción Viva. The asset is real
 * data (Wiktionary, CC BY-SA, frequency-ordered), so the pins are:
 * its exact size and shape, a handful of spot equivalents read
 * from the file itself, a full parse into both directions, and
 * the tier order — a curated phrase's hand-written rendering must
 * beat the lexicon every time.
 */
class TraduccionLexicoTest {

    companion object {
        /** Exact entry count of assets/traduccion_bank.tsv. */
        const val EXPECTED_ENTRIES = 50000
    }

    @After
    fun limpiarLexico() {
        // Never leak installed test maps into other suites.
        BancoLexico.instalarParaPruebas(null, null)
    }

    private fun assetFile(): File {
        val candidates = listOf(
            File("src/main/assets/traduccion_bank.tsv"),
            File("app/src/main/assets/traduccion_bank.tsv")
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("traduccion_bank.tsv not found (cwd=${File(".").absolutePath})")
    }

    private fun assetLines(): List<String> =
        assetFile().bufferedReader(Charsets.UTF_8).use { it.readLines() }
            .filter { it.isNotEmpty() }

    private fun parsedMaps(): Pair<Map<String, FraseViva>, Map<String, FraseViva>> =
        assetFile().bufferedReader(Charsets.UTF_8).use { parseBancoLexico(it) }

    // ------------------------------------------------------------ asset shape

    @Test
    fun `asset has the pinned entry count and every line is well formed`() {
        val lines = assetLines()
        assertEquals(EXPECTED_ENTRIES, lines.size)
        for (line in lines) {
            val parts = line.split('\t')
            assertEquals("line must have exactly 3 fields: $line", 3, parts.size)
            assertTrue("blank key", parts[0].isNotBlank())
            assertTrue("blank spanish", parts[1].isNotBlank())
            assertTrue("blank english", parts[2].isNotBlank())
        }
    }

    @Test
    fun `asset keys are unique`() {
        val keys = assetLines().map { it.split('\t')[0] }
        assertEquals(keys.size, keys.distinct().size)
    }

    // ------------------------------------------------------------ full parse

    @Test
    fun `full asset parses into both lookup directions`() {
        val (es, en) = parsedMaps()
        assertEquals(EXPECTED_ENTRIES, es.size)
        // English equivalents collide across Spanish words (many
        // words share a gloss), so the reverse map is smaller —
        // but it must still be the bulk of the bank.
        assertTrue("reverse map too small: ${en.size}", en.size > 30000)
    }

    @Test
    fun `spot equivalents from the file`() {
        val (es, _) = parsedMaps()
        assertEquals("house", es["casa"]?.en)
        assertEquals("water", es["agua"]?.en)
        assertEquals("dog", es["perro"]?.en)
        assertEquals("thank you", es["gracias"]?.en)
    }

    @Test
    fun `reverse lookup answers from the english side`() {
        val (_, en) = parsedMaps()
        assertEquals("casa", en[normalizarFrase("house")]?.es)
        // "dog" is shared by two real entries, "can" (the literary
        // word) and "perro" (the everyday one). The file is sorted
        // by Spanish key and the first entry wins a shared English
        // gloss, so the reverse answer is "can" — verified against
        // the file itself; forward, "perro" still gives "dog".
        assertEquals("can", en[normalizarFrase("dog")]?.es)
    }

    // ------------------------------------------------------------ parsing rules

    @Test
    fun `parser skips malformed lines and keeps the first entry per key`() {
        val tsv = listOf(
            "casa\tcasa\thouse",
            "linea-sin-tabs",
            "perro\tperro\tdog",
            "casa\tcasa\tCHAIR",
            "\t\t",
            "gato\tgato\tcat\textra",
            "mesa\tmesa\ttable"
        ).joinToString("\n")
        val (es, _) = parseBancoLexico(java.io.BufferedReader(StringReader(tsv)))
        assertEquals(3, es.size)
        assertEquals("house", es["casa"]?.en)
        assertEquals("dog", es["perro"]?.en)
        assertEquals("table", es["mesa"]?.en)
    }

    // ------------------------------------------------------------ tier order

    @Test
    fun `lexicon hits resolve offline without a key`() {
        val frase = FraseViva("casa", "house")
        BancoLexico.instalarParaPruebas(
            mapOf("casa" to frase),
            mapOf("house" to frase)
        )
        val e = resolverViva("casa", "", null)
        assertTrue(e is VivaEstado.Traduccion)
        e as VivaEstado.Traduccion
        assertEquals(VivaOrigen.BANCO, e.origen)
        assertEquals("house", e.traducida)
        assertEquals("house", e.paraEscuchar)
        // And in reverse: Spanish shown, English spoken.
        val r = resolverViva("house", "", null)
        assertTrue(r is VivaEstado.Traduccion)
        r as VivaEstado.Traduccion
        assertEquals("casa", r.traducida)
        assertEquals("house", r.paraEscuchar)
    }

    @Test
    fun `curated bank overrides the lexicon on the same phrase`() {
        // Even if the lexicon carried a flatter rendering of the
        // curated phrase, the hand-written one must win.
        val plana = FraseViva("tengo hambre", "I have hunger")
        BancoLexico.instalarParaPruebas(
            mapOf("tengo hambre" to plana),
            mapOf("i have hunger" to plana)
        )
        val e = resolverViva("tengo hambre", "", null)
        assertTrue(e is VivaEstado.Traduccion)
        e as VivaEstado.Traduccion
        assertEquals("I'm hungry", e.traducida)
        assertNotNull(buscarEnBanco("tengo hambre"))
    }
}
