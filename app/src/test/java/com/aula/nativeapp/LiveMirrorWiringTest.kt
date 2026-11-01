package com.aula.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mirror wiring tests for Conversación en vivo (M1).
 *
 * The defect these pin: live mode used to log one
 * "pronunciation:<word>" event per suspect word — keys that could
 * NEVER be named by the Mirror (MirrorStore's rule needs >= 3 errors
 * across >= 2 contexts for ONE key; the Guide, the only other
 * pronunciation logger, teaches four unrelated words, and no
 * transfer items existed for stray words), while the recap copy
 * promised the Espejo would name repeated errors.
 *
 * The fix, pinned here at the level the app allows: the phantom-E
 * family is ONE pattern ([PHANTOM_E_PATTERN_KEY]); the shapes the
 * screen writes per graded turn are [liveMirrorEvents] (pure data);
 * and the naming rule itself is [qualifiedPatternsOf] — the exact
 * function MirrorStore.qualifiedPatterns delegates to (MirrorStore
 * is Android-bound, so the rule was extracted pure rather than
 * re-implemented here).
 *
 * Harness: see LiveConversationEngineTest's header — the CI gate
 * runs these suites.
 */
class LiveMirrorWiringTest {

    private val family = PHANTOM_E_PATTERN_KEY

    private fun ev(pattern: String, context: String, correct: Boolean = false) =
        MirrorEvent(pattern, context, correct, 0L)

    // ------------------------------------------------ event shapes

    @Test
    fun `a clean graded turn writes exactly one conversation_turn event`() {
        val cafe = LiveConversationBanks.bankFor("cafe")!!
        val (result, feedback) = LiveGrader.grade(
            spokenText = "I would like a coffee, please",
            hypotheses = listOf("I would like a coffee, please"),
            prompt = cafe.opening
        )
        assertEquals(
            listOf(Triple("conversation_turn", "conversaciones", true)),
            liveMirrorEvents(result, feedback)
        )
    }

    @Test
    fun `a suspect word adds exactly one family event in the live context`() {
        val food = LiveConversationBanks.bankFor("cafe")!!
            .branches.first { it.prompt.id == "b_food" }.prompt
        val (result, feedback) = LiveGrader.grade(
            spokenText = "I would like a esandwich, please",
            hypotheses = listOf("I would like a esandwich, please"),
            prompt = food
        )
        val events = liveMirrorEvents(result, feedback)
        assertEquals(2, events.size)
        assertEquals("conversation_turn", events[0].first)
        assertEquals("conversaciones", events[0].second)
        assertEquals(Triple(family, "conversaciones", false), events[1])
    }

    @Test
    fun `two suspects in one turn add two family events`() {
        val feedback = LiveWordFeedbackAnalyzer.analyze(
            spokenText = "esister and esoccer",
            hypotheses = listOf("esister and esoccer"),
            modelReply = "sister and soccer",
            trapWords = listOf("sister", "soccer")
        )
        val result = LiveTurnScore(
            expectedLine = "sister and soccer",
            spokenText = "esister and esoccer",
            accuracyScore = 40,
            scoreCategory = LiveScoreCategory.RED,
            clarityTipEs = "",
            feedbackMessageEs = ""
        )
        val events = liveMirrorEvents(result, feedback)
        assertEquals(3, events.size)
        assertEquals(2, events.count { it.first == family })
        assertTrue(events.filter { it.first == family }.all { !it.third })
    }

    // ------------------------------------------------ qualification

    @Test
    fun `qualification is reachable from live plus guide event shapes`() {
        // Two phantom-E errors in live conversations plus one honest
        // "Todavía no" self-rating on the Guide word "sister" in the
        // Biblioteca — every event a shape the app really emits.
        val events = listOf(
            ev(family, "conversaciones"),
            ev(family, "conversaciones"),
            ev(family, "biblioteca")
        )
        val qualified = qualifiedPatternsOf(events, emptyList())
        val stat = qualified.firstOrNull { it.key == family }
        assertTrue("the family pattern must be nameable", stat != null)
        assertEquals(3, stat!!.errors)
        assertEquals(2, stat.contexts)
    }

    @Test
    fun `live shapes alone never qualify - one context is not enough`() {
        val events = List(5) { ev(family, "conversaciones") }
        assertTrue(qualifiedPatternsOf(events, emptyList()).none { it.key == family })
    }

    @Test
    fun `fewer than three errors never qualify even across two contexts`() {
        val events = listOf(ev(family, "conversaciones"), ev(family, "biblioteca"))
        assertTrue(qualifiedPatternsOf(events, emptyList()).none { it.key == family })
    }

    @Test
    fun `correct events never count toward naming`() {
        val events = listOf(
            ev(family, "conversaciones"),
            ev(family, "conversaciones"),
            ev(family, "biblioteca", correct = true),
            ev(family, "hoy", correct = true)
        )
        assertTrue(qualifiedPatternsOf(events, emptyList()).none { it.key == family })
    }

    @Test
    fun `a dismissed family pattern is never named`() {
        val events = listOf(
            ev(family, "conversaciones"),
            ev(family, "conversaciones"),
            ev(family, "biblioteca")
        )
        assertTrue(qualifiedPatternsOf(events, listOf(family)).none { it.key == family })
    }

    // ------------------------------------------------ the pattern's surfaces

    @Test
    fun `the family pattern has real transfer items`() {
        val items = transferItemsFor(family)
        assertTrue("transfer check needs items", items.size >= 3)
        assertTrue(items.all { it.pattern == family })
        assertTrue(items.all { it.kind == "choice" })
        assertTrue(items.all { it.options.size >= 2 && it.answer in it.options.indices })
    }

    @Test
    fun `aimed practice for the family pattern routes to the guide word sister`() {
        val info = patternInfo(family)
        assertEquals("guide", info.kind)
        assertEquals("sister", info.word)
        assertTrue(GUIDE_WORDS.containsKey("sister"))
    }

    @Test
    fun `the phantom-E family gate - s initial, never sh`() {
        assertTrue(isPhantomEFamilyWord("sister"))
        assertTrue(isPhantomEFamilyWord("store"))
        assertFalse(isPhantomEFamilyWord("ship"))
        assertFalse(isPhantomEFamilyWord("sheep"))
        assertFalse(isPhantomEFamilyWord("went"))
        assertFalse(isPhantomEFamilyWord("three"))
    }
}
