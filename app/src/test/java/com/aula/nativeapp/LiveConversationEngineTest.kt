package com.aula.nativeapp

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Conversación en vivo flow-engine tests — the Aula mirror of
 * Cognata's LiveConversationEngineTest coverage, over Aula's own
 * nine conversation banks.
 *
 * Harness: app/build.gradle.kts declares testImplementation
 * junit 4.13.2 and CI (.github/workflows/android.yml) runs
 * testDebugUnitTest as a gate BEFORE assembleDebug — a red battery
 * fails the build. These suites are live: run #33 passed 29/29
 * across the three original live suites.
 */
class LiveConversationEngineTest {

    private val bankIds = listOf("cafe", "ayer", "hotel", "mercado", "trabajo", "direcciones", "medico", "aeropuerto", "restaurante", "farmacia", "banco", "tienda", "telefono")

    @Test
    fun `every conversation has a complete live bank and the opening reuses the guided first line`() {
        for (conv in CONVERSATIONS) {
            val bank = LiveConversationBanks.bankFor(conv.id)
            assertNotNull("missing live bank for ${conv.id}", bank)
            bank!!
            assertEquals(conv.id, bank.conversationId)
            // The opening reuses the guided scene's first partner line verbatim.
            assertEquals(conv.turns.first().en, bank.opening.partnerLine)
            assertTrue("bank ${conv.id} needs >= 6 branches", bank.branches.size >= 6)
            assertTrue("bank ${conv.id} needs >= 4 generics", bank.generics.size >= 4)
            assertTrue(bank.wrapUpLine.isNotBlank())
            val prompts = listOf(bank.opening) + bank.branches.map { it.prompt } + bank.generics
            for (prompt in prompts) {
                assertTrue("${conv.id}/${prompt.id} model replies", prompt.modelReplies.size in 1..2)
                assertTrue("${conv.id}/${prompt.id} objective", prompt.objectiveEs.isNotBlank())
                assertTrue("${conv.id}/${prompt.id} tip", prompt.clarityTipEs.isNotBlank())
                assertTrue("${conv.id}/${prompt.id} keywords", prompt.targetKeywords.isNotEmpty())
            }
            for (branch in bank.branches) {
                assertTrue("${conv.id}/${branch.prompt.id} triggers", branch.triggerKeywords.isNotEmpty())
            }
            // Prompt ids are unique per bank (used-sets key on them).
            assertEquals(prompts.size, prompts.map { it.id }.toSet().size)
        }
        assertEquals(bankIds.size, CONVERSATIONS.size)
    }

    @Test
    fun `a transcript hitting a branch trigger serves that branch`() {
        val bank = LiveConversationBanks.bankFor("cafe")!!
        val next = LiveConversationEngine.nextPrompt(
            bank = bank,
            usedBranchIds = emptySet(),
            usedGenericIds = emptySet(),
            exchangesDone = 1,
            lastTranscript = "I would like a sandwich, please"
        )
        assertEquals("b_food", next?.id)
    }

    @Test
    fun `trigger matching uses the scorer stem semantics`() {
        val bank = LiveConversationBanks.bankFor("ayer")!!
        // "playing" must hit the soccer branch through the stem
        // matcher ('playing' ~ 'played' share the stripped stem
        // 'play'), not exact-token equality.
        val next = LiveConversationEngine.nextPrompt(
            bank = bank,
            usedBranchIds = emptySet(),
            usedGenericIds = emptySet(),
            exchangesDone = 1,
            lastTranscript = "I was playing soccer with friends"
        )
        assertEquals("b_soccer", next?.id)
    }

    @Test
    fun `no trigger hit falls back to the first unused generic, anchored to the scene`() {
        val bank = LiveConversationBanks.bankFor("hotel")!!
        val next = LiveConversationEngine.nextPrompt(
            bank = bank,
            usedBranchIds = emptySet(),
            usedGenericIds = emptySet(),
            exchangesDone = 1,
            lastTranscript = "Yes, I have a reservation"
        )
        assertEquals(bank.generics.first().id, next?.id)
    }

    @Test
    fun `a used branch is never served again - the next hit wins`() {
        val bank = LiveConversationBanks.bankFor("cafe")!!
        val next = LiveConversationEngine.nextPrompt(
            bank = bank,
            usedBranchIds = setOf("b_food"),
            usedGenericIds = emptySet(),
            exchangesDone = 2,
            // Hits b_food (used) — the next matching branch or a
            // generic must win instead; "milk" hits b_milk.
            lastTranscript = "A sandwich with milk, please"
        )
        assertEquals("b_milk", next?.id)
    }

    @Test
    fun `when generics run out the partner steers to an unused branch`() {
        val bank = LiveConversationBanks.bankFor("ayer")!!
        val next = LiveConversationEngine.nextPrompt(
            bank = bank,
            usedBranchIds = emptySet(),
            usedGenericIds = bank.generics.map { it.id }.toSet(),
            exchangesDone = 3,
            lastTranscript = "It was a good day"
        )
        assertEquals(bank.branches.first().prompt.id, next?.id)
    }

    @Test
    fun `the exchange goal wraps up regardless of remaining bank`() {
        val bank = LiveConversationBanks.bankFor("cafe")!!
        val next = LiveConversationEngine.nextPrompt(
            bank = bank,
            usedBranchIds = emptySet(),
            usedGenericIds = emptySet(),
            exchangesDone = LiveConversationEngine.EXCHANGE_GOAL,
            lastTranscript = "I would like a sandwich, please"
        )
        assertNull(next)
    }

    @Test
    fun `bank exhaustion wraps up before the goal`() {
        val bank = LiveConversationBanks.bankFor("hotel")!!
        val next = LiveConversationEngine.nextPrompt(
            bank = bank,
            usedBranchIds = bank.branches.map { it.prompt.id }.toSet(),
            usedGenericIds = bank.generics.map { it.id }.toSet(),
            exchangesDone = 5,
            lastTranscript = "Thank you very much"
        )
        assertNull(next)
    }

    @Test
    fun `selection is deterministic - same inputs, same prompt`() {
        val bank = LiveConversationBanks.bankFor("ayer")!!
        val first = LiveConversationEngine.nextPrompt(bank, emptySet(), emptySet(), 1, "I visited my family")
        val second = LiveConversationEngine.nextPrompt(bank, emptySet(), emptySet(), 1, "I visited my family")
        assertEquals(first?.id, second?.id)
        assertEquals("b_family", first?.id)
    }

    @Test
    fun `an unknown conversation has no bank`() {
        assertNull(LiveConversationBanks.bankFor("no-existe"))
    }

    @Test
    fun `every target keyword is matchable against its own prompt model replies`() {
        // Bank invariant (Mi5): a keyword the scorer counts must be a
        // word the learner can actually produce by following the
        // prompt's own model replies — matched with the scorer's own
        // stem semantics, against the replies collectively.
        for (conv in CONVERSATIONS) {
            val bank = LiveConversationBanks.bankFor(conv.id)!!
            val prompts = listOf(bank.opening) + bank.branches.map { it.prompt } + bank.generics
            for (prompt in prompts) {
                for (keyword in prompt.targetKeywords) {
                    val k = keyword.lowercase(Locale.US)
                    val hit = prompt.modelReplies.any { reply ->
                        val clean = reply.lowercase(Locale.US)
                        val tokens = clean.split(Regex("[^a-zA-Záéíóúñ]+")).filter { it.isNotEmpty() }
                        ConversationScorer.keywordMatched(k, clean, tokens)
                    }
                    assertTrue(
                        "${conv.id}/${prompt.id}: keyword '$keyword' matches none of its model replies",
                        hit
                    )
                }
            }
        }
    }
}
