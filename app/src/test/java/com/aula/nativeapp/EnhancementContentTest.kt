package com.aula.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Content pins for the Cognata→Aula integration builds (F + H):
 * the Lab's series are well-formed and honestly wired, the
 * ghosting map only names real guide words with in-range silent
 * letters, and the Gran Prueba can actually draw its ten unseen
 * items across the course's patterns.
 */
class EnhancementContentTest {

    @Test
    fun `lab series are complete and uniquely identified`() {
        assertEquals(5, LAB_SETS.size)
        assertEquals(LAB_SETS.size, LAB_SETS.map { it.id }.distinct().size)
        for (set in LAB_SETS) {
            assertTrue("set ${set.id} needs >= 5 items", set.items.size >= 5)
            for (item in set.items) {
                assertTrue("blank text in ${set.id}", item.text.isNotBlank())
                assertTrue("blank mouth cue in ${set.id}", item.mouth.isNotBlank())
                assertTrue("blank listen cue in ${set.id}", item.listenFor.isNotBlank())
            }
        }
    }

    @Test
    fun `only the phantom-e lab set feeds the mirror`() {
        val wired = LAB_SETS.filter { it.patternKey != null }
        assertEquals(1, wired.size)
        assertEquals(PHANTOM_E_PATTERN_KEY, wired.single().patternKey)
        // Every item of the wired set is an s-initial trap word.
        for (item in wired.single().items) {
            assertTrue(item.text.lowercase().startsWith("s"))
        }
    }

    @Test
    fun `ghosting map names real guide words and in-range letters`() {
        assertTrue(SILENT_LETTERS.isNotEmpty())
        for ((word, indices) in SILENT_LETTERS) {
            assertTrue("ghosted word must be a guide word: $word", GUIDE_WORDS.containsKey(word))
            for (i in indices) {
                assertTrue("index $i out of range for $word", i in word.indices)
            }
        }
        // The canonical cases stay pinned.
        assertEquals(setOf(2, 3), SILENT_LETTERS["night"])
        assertEquals(setOf(3), SILENT_LETTERS["time"])
    }

    @Test
    fun `the final exam pool spans the course`() {
        assertTrue("exam needs >= 10 transfer items", TRANSFER.size >= 10)
        assertTrue(
            "exam needs >= 4 distinct patterns",
            TRANSFER.map { it.pattern }.distinct().size >= 4
        )
    }
}
