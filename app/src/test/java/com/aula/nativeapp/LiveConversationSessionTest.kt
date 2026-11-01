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
 * the per-turn suspect selection the screen's Mirror events are
 * built from (the event shapes themselves are pinned in
 * LiveMirrorWiringTest), and [LiveSessionState]'s recap math. The
 * composable glue itself (mic funnel arming) is not unit-testable
 * without instrumentation and is flagged as such in the delivery
 * report.
 *
 * Harness: see LiveConversationEngineTest's header — the CI gate
 * runs these suites (run #33: 29/29 across the live suites).
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
    fun `an unspoken word is never a suspect - alternatives only incriminate produced words`() {
        val (_, feedback) = LiveGrader.grade(
            spokenText = "I would like a coffee, please",
            hypotheses = listOf(
                "I would like a coffee, please",
                "I would like a esandwich, please"
            ),
            prompt = LiveConversationBanks.bankFor("cafe")!!.branches.first { it.prompt.id == "b_food" }.prompt
        )
        // The screen writes one phantom-E family Mirror event per
        // suspect word (see LiveMirrorWiringTest) — this is the list
        // it is built from.
        val suspects = feedback.words.filter { it.status == LiveWordStatus.PRONUNCIATION_SUSPECT }
        // "sandwich" was never spoken in the top hypothesis, so it is
        // wrong-or-missing, NOT a suspect: alternatives only incriminate
        // a word that was actually produced (alignment first).
        assertTrue(suspects.isEmpty())
    }

    @Test
    fun `a produced word betrayed by its alternative yields exactly one suspect`() {
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
        // "(Sin audio detectado)" is NOT a live marker (Mi2): the
        // probe never emits it, so isSilenceMarker no longer treats
        // it as one. As plain text it would reach ConversationScorer,
        // whose own silence branch still handles it as defense.
        assertFalse(LiveGrader.isSilenceMarker("(Sin audio detectado)"))
        assertFalse(LiveGrader.isSilenceMarker("I would like a coffee, please"))
    }

    @Test
    fun `the scorer detects the true phantom-E form - e plus keyword`() {
        // The audit's hand-derived case (Mi1): the trap form learners
        // actually produce is "e" + keyword ("esister"), not
        // "es" + keyword. Against ayer/b_family's first model reply,
        // the scorer's trap note and its -12 must fire.
        val prompt = LiveConversationBanks.bankFor("ayer")!!
            .branches.first { it.prompt.id == "b_family" }.prompt
        val (trapped, feedback) = LiveGrader.grade(
            spokenText = "I watched a movie with my esister",
            hypotheses = listOf("I watched a movie with my esister"),
            prompt = prompt
        )
        assertTrue(trapped.detectedTraps.any { it.contains("sister") })
        assertEquals(
            LiveWordStatus.PRONUNCIATION_SUSPECT,
            feedback.words.first { it.word == "sister" }.status
        )
        // And the trap costs points versus the same reply said clean.
        val (clean, _) = LiveGrader.grade(
            spokenText = "I watched a movie with my sister",
            hypotheses = listOf("I watched a movie with my sister"),
            prompt = prompt
        )
        assertTrue(trapped.accuracyScore < clean.accuracyScore)
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
