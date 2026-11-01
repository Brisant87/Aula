package com.aula.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Live session / grading tests — the Aula mirror of Cognata's
 * LiveConversationViewModelTest coverage, at the level Aula's
 * architecture allows: Aula has no ViewModel, so the session glue
 * lives in the Compose screen; what is pure — and therefore testable
 * — is [LiveGrader] (the exact function the screen calls),
 * [LiveGrader.isSilenceMarker] (the screen's ungraded-close guard),
 * the Mirror event SHAPES the screen writes per graded turn (asserted
 * here as data: one conversation_turn + one pronunciation:<word> per
 * suspect), and [LiveSessionState]'s recap math. The composable glue
 * itself (mic funnel arming) is not unit-testable without
 * instrumentation and is flagged as such in the delivery report.
 *
 * Harness gap: see LiveConversationEngineTest's header — these suites
 * are dormant until a junit dependency + CI test step are added.
 */
class LiveConversationSessionTest {

    private val cafe = LiveConversationBanks.bankFor("cafe")!!
    private val opening = cafe.opening

    @Test
    fun `a model reply grades GREEN and produces word feedback for the same reply`() {
        val (result, feedback) = LiveGrader.grade(
            spokenText = "I would like a coffee, please",
            hypotheses = listOf("I would like a coffee, please"),
            prompt = opening
        )
        assertEquals(LiveScoreCategory.GREEN, result.scoreCategory)
        assertTrue(result.accuracyScore >= 85)
        assertEquals("I would like a coffee, please.", result.expectedLine)
        assertTrue(feedback.words.all { it.status == LiveWordStatus.OK })
    }

    @Test
    fun `grading is best-of across the prompt model replies`() {
        // The second cafe opening reply adds a sandwich; saying the
        // long form must grade against IT, not the short one.
        val (result, _) = LiveGrader.grade(
            spokenText = "I would like a coffee and a sandwich, please",
            hypotheses = listOf("I would like a coffee and a sandwich, please"),
            prompt = opening
        )
        assertEquals("I would like a coffee and a sandwich, please.", result.expectedLine)
        assertEquals(LiveScoreCategory.GREEN, result.scoreCategory)
    }

    @Test
    fun `a Spanish answer is gated to the RED band`() {
        val (result, _) = LiveGrader.grade(
            spokenText = "Yo quiero un café por favor",
            hypotheses = listOf("Yo quiero un café por favor"),
            prompt = opening
        )
        assertEquals(LiveScoreCategory.RED, result.scoreCategory)
        assertTrue(result.accuracyScore <= 55)
        assertTrue(result.detectedTraps.any { it.contains("español") })
    }

    @Test
    fun `trap evidence yields exactly the per-word Mirror event shapes the screen writes`() {
        val (_, feedback) = LiveGrader.grade(
            spokenText = "I would like a coffee, please",
            hypotheses = listOf(
                "I would like a coffee, please",
                "I would like a esandwich, please"
            ),
            prompt = LiveConversationBanks.bankFor("cafe")!!.branches.first { it.prompt.id == "b_food" }.prompt
        )
        // The screen writes one pronunciation:<word> FALSE event per
        // suspect word, context "conversaciones" — this is the list.
        val suspects = feedback.words.filter { it.status == LiveWordStatus.PRONUNCIATION_SUSPECT }
        // "sandwich" was never spoken in the top hypothesis, so it is
        // wrong-or-missing, NOT a suspect: alternatives only incriminate
        // a word that was actually produced (alignment first).
        assertTrue(suspects.isEmpty())
    }

    @Test
    fun `a produced word betrayed by its alternative yields one suspect event shape`() {
        val (_, feedback) = LiveGrader.grade(
            spokenText = "I would like a sandwich, please",
            hypotheses = listOf(
                "I would like a sandwich, please",
                "I would like a esandwich, please"
            ),
            prompt = LiveConversationBanks.bankFor("cafe")!!.branches.first { it.prompt.id == "b_food" }.prompt
        )
        val suspects = feedback.words.filter { it.status == LiveWordStatus.PRONUNCIATION_SUSPECT }
        assertEquals(listOf("sandwich"), suspects.map { it.word })
    }

    @Test
    fun `silence and blank input are recognition failures, not replies`() {
        assertTrue(LiveGrader.isSilenceMarker(""))
        assertTrue(LiveGrader.isSilenceMarker("   "))
        assertTrue(LiveGrader.isSilenceMarker("(Sin audio detectado)"))
        assertFalse(LiveGrader.isSilenceMarker("I would like a coffee, please"))
    }

    @Test
    fun `session recap math - running average and distinct weak words in order`() {
        val (r1, f1) = LiveGrader.grade(
            "I would like a coffee, please",
            listOf("I would like a coffee, please"),
            opening
        )
        val foodPrompt = cafe.branches.first { it.prompt.id == "b_food" }.prompt
        val (r2, f2) = LiveGrader.grade(
            "I would like a esandwich, please",
            listOf("I would like a esandwich, please"),
            foodPrompt
        )
        val state = LiveSessionState(
            conversation = CONVERSATIONS.first { it.id == "cafe" },
            bank = cafe,
            partnerName = "Mesera",
            exchanges = listOf(
                LiveExchange(opening.id, opening.partnerLine, "I would like a coffee, please", r1, f1),
                LiveExchange(foodPrompt.id, foodPrompt.partnerLine, "I would like a esandwich, please", r2, f2)
            ),
            isFinished = true
        )
        assertEquals(2, state.exchangesDone)
        assertEquals((r1.accuracyScore + r2.accuracyScore) / 2, state.runningAverageScore)
        assertEquals(listOf("sandwich"), state.weakWords.map { it.word })
        assertEquals(
            LiveWordStatus.PRONUNCIATION_SUSPECT,
            state.weakWords.first().status
        )
    }

    @Test
    fun `the single-word retry score is boundary-policed and display-only by construction`() {
        // A clean top hit betrayed by a trap-form alternative scores
        // the TRAP form (52-family), never the clean 98.
        val betrayed = PronunciationBoundary.scoreSingleWord(
            hypotheses = listOf("sister", "esister"),
            confidences = null,
            expectedWord = "sister"
        )
        assertEquals(WordAccuracy.computeWordAccuracy("sister", "esister"), betrayed)
        assertTrue(betrayed < 85)

        // A clean hit with no contradicting evidence stays a clean hit.
        val clean = PronunciationBoundary.scoreSingleWord(
            hypotheses = listOf("sister"),
            confidences = null,
            expectedWord = "sister"
        )
        assertEquals(98, clean)

        // A fragment alternative caps a clean hit at 84.
        val capped = PronunciationBoundary.scoreSingleWord(
            hypotheses = listOf("sister", "sist"),
            confidences = null,
            expectedWord = "sister"
        )
        assertEquals(PronunciationBoundary.UNCERTAIN_GREEN_CAP, capped)
    }
}
