package com.aula.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Per-word feedback tests — the Aula mirror of Cognata's
 * LiveWordFeedbackTest coverage, over Aula's own curriculum traps
 * (the phantom-E s-words of the live banks: sister, sandwich, salad…).
 * The honesty core: PRONUNCIATION_SUSPECT exists only on recognizer
 * evidence — never from the transcript alone, never from trap-list
 * membership.
 *
 * Harness gap: see LiveConversationEngineTest's header — these suites
 * are dormant until a junit dependency + CI test step are added.
 */
class LiveWordFeedbackTest {

    @Test
    fun `a clean reply marks every word OK and has no weakest word`() {
        val feedback = LiveWordFeedbackAnalyzer.analyze(
            spokenText = "I watched a movie with my sister",
            hypotheses = listOf("I watched a movie with my sister"),
            modelReply = "I watched a movie with my sister.",
            trapWords = listOf("sister")
        )
        assertTrue(feedback.words.all { it.status == LiveWordStatus.OK })
        assertNull(feedback.weakestWord)
    }

    @Test
    fun `the trap form in the scored transcript marks the word suspect`() {
        val feedback = LiveWordFeedbackAnalyzer.analyze(
            spokenText = "I watched a movie with my esister",
            hypotheses = listOf("I watched a movie with my esister"),
            modelReply = "I watched a movie with my sister.",
            trapWords = listOf("sister")
        )
        val sister = feedback.words.first { it.word == "sister" }
        assertEquals(LiveWordStatus.PRONUNCIATION_SUSPECT, sister.status)
        assertEquals("esister", sister.spokenForm)
        assertEquals("sister", feedback.weakestWord?.word)
    }

    @Test
    fun `a trap-form alternative marks the word suspect even when the top hypothesis is clean`() {
        val feedback = LiveWordFeedbackAnalyzer.analyze(
            spokenText = "I would like a sandwich please",
            hypotheses = listOf(
                "I would like a sandwich please",
                "I would like a esandwich please"
            ),
            modelReply = "I would like a sandwich, please.",
            trapWords = listOf("sandwich")
        )
        val sandwich = feedback.words.first { it.word == "sandwich" }
        assertEquals(LiveWordStatus.PRONUNCIATION_SUSPECT, sandwich.status)
        assertEquals("esandwich", sandwich.spokenForm)
    }

    @Test
    fun `a known trap word with no evidence is never marked suspect`() {
        val feedback = LiveWordFeedbackAnalyzer.analyze(
            spokenText = "Yes, a fresh salad please",
            hypotheses = listOf("Yes, a fresh salad please"),
            modelReply = "Yes, a fresh salad, please.",
            trapWords = listOf("salad")
        )
        val salad = feedback.words.first { it.word == "salad" }
        assertEquals(LiveWordStatus.OK, salad.status)
    }

    @Test
    fun `an unrelated alternative does not incriminate a clean word`() {
        val feedback = LiveWordFeedbackAnalyzer.analyze(
            spokenText = "I am staying for three nights",
            hypotheses = listOf(
                "I am staying for three nights",
                "I am especially happy tonight"
            ),
            modelReply = "I am staying for three nights.",
            trapWords = listOf("staying")
        )
        val staying = feedback.words.first { it.word == "staying" }
        assertEquals(LiveWordStatus.OK, staying.status)
    }

    @Test
    fun `a missing word is marked wrong-or-missing and becomes the weakest word`() {
        val feedback = LiveWordFeedbackAnalyzer.analyze(
            spokenText = "I would like a coffee",
            hypotheses = listOf("I would like a coffee"),
            modelReply = "I would like a sandwich, please.",
            trapWords = listOf("sandwich")
        )
        val sandwich = feedback.words.first { it.word == "sandwich" }
        assertEquals(LiveWordStatus.WRONG_OR_MISSING, sandwich.status)
        assertNull(sandwich.spokenForm)
        assertNotNull(feedback.weakestWord)
    }

    @Test
    fun `a replaced word is marked wrong-or-missing`() {
        val feedback = LiveWordFeedbackAnalyzer.analyze(
            spokenText = "I bought a book tomorrow",
            hypotheses = listOf("I bought a book tomorrow"),
            modelReply = "I bought a book yesterday.",
            trapWords = emptyList()
        )
        val yesterday = feedback.words.first { it.word == "yesterday" }
        assertEquals(LiveWordStatus.WRONG_OR_MISSING, yesterday.status)
    }

    @Test
    fun `the weakest-word pick prefers a suspect over a missing word`() {
        val feedback = LiveWordFeedbackAnalyzer.analyze(
            spokenText = "I watched a movie with my esister",
            hypotheses = listOf("I watched a movie with my esister"),
            modelReply = "Last night we watched a movie with my sister.",
            trapWords = listOf("sister")
        )
        // "last"/"night"/"we" are missing AND "sister" is a suspect:
        // the suspect wins the chip.
        assertEquals(LiveWordStatus.PRONUNCIATION_SUSPECT, feedback.weakestWord?.status)
        assertEquals("sister", feedback.weakestWord?.word)
    }

    @Test
    fun `the weakest-word pick prefers a content word over a function word`() {
        val feedback = LiveWordFeedbackAnalyzer.analyze(
            spokenText = "I have reservation",
            hypotheses = listOf("I have reservation"),
            modelReply = "Yes, I have a reservation.",
            trapWords = emptyList()
        )
        // Missing: "yes" (content-ish opener) and "a" (function). The
        // first wrong/missing CONTENT word wins over the article.
        assertEquals("yes", feedback.weakestWord?.word)
    }

    @Test
    fun `suspect scoring uses the trap form score, not the cleaned top score`() {
        val feedback = LiveWordFeedbackAnalyzer.analyze(
            spokenText = "A single room please",
            hypotheses = listOf(
                "A single room please",
                "A esingle room please"
            ),
            modelReply = "A single room, please.",
            trapWords = listOf("single")
        )
        val single = feedback.words.first { it.word == "single" }
        assertEquals(LiveWordStatus.PRONUNCIATION_SUSPECT, single.status)
        // The trap form's own word accuracy (52 in WordAccuracy's trap
        // rule, adjusted by its exact formula) — never the clean 98.
        assertEquals(WordAccuracy.computeWordAccuracy("single", "esingle"), single.score)
        assertTrue(single.score < 98)
    }

    @Test
    fun `the sh gate - ship is never a phantom-E suspect`() {
        val feedback = LiveWordFeedbackAnalyzer.analyze(
            spokenText = "I see a ship",
            hypotheses = listOf("I see a ship", "I see a eship"),
            modelReply = "I see a ship.",
            trapWords = emptyList()
        )
        val ship = feedback.words.first { it.word == "ship" }
        assertEquals(LiveWordStatus.OK, ship.status)
    }
}
