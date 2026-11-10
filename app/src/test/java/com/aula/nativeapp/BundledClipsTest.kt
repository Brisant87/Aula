package com.aula.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bundled-clips gate: every fixed text the app can speak must have a
 * recording in the APK, so the teacher's voice never depends on the
 * device engine (which fails silently on many Samsung setups), a
 * network, or the learner's cloud key. If content grows a new spoken
 * text, this test fails until its clip is recorded and mapped.
 */
class BundledClipsTest {

    private fun assertClip(text: String, where: String) {
        assertNotNull("missing bundled clip for $where: \"$text\"", BundledClips.assetFor(text))
    }

    @Test
    fun `every fixed spoken text has a bundled clip`() {
        for (gw in GUIDE_WORDS.values) assertClip(gw.word, "guide word")
        for (c in COGNATES) assertClip(c.en, "cognate")
        for (conv in CONVERSATIONS) {
            for (turn in conv.turns) assertClip(turn.en, "guided turn ${conv.id}")
            val bank = LiveConversationBanks.bankFor(conv.id)
            assertNotNull("live bank for ${conv.id}", bank)
            bank!!
            val prompts = listOf(bank.opening) + bank.branches.map { it.prompt } + bank.generics
            for (p in prompts) {
                assertClip(p.partnerLine, "live line ${conv.id}/${p.id}")
                assertClip(p.modelReplies.first(), "model reply ${conv.id}/${p.id}")
            }
            assertClip(bank.wrapUpLine, "wrap-up ${conv.id}")
        }
        for (item in LISTENING) assertClip(item.audio, "listening ${item.id}")
        for (item in SYNTAX) assertClip(item.words.joinToString(" "), "syntax ${item.id}")
        for (item in TRANSFER) {
            if (item.words.isNotEmpty()) {
                assertClip(item.words.joinToString(" "), "transfer ${item.id}")
            }
        }
        for (set in LAB_SETS) {
            for (item in set.items) assertClip(item.text, "lab ${set.id}")
        }
        assertClip("Hello! I am your teacher. Let's start today's lesson.", "teacher greeting")
        assertClip("Hello! I am your teacher. Let's practice English together.", "teacher greeting")
    }

    @Test
    fun `clip map is complete and well formed`() {
        assertEquals(569, BundledClips.size)
        val paths = BundledClips.assetPaths()
        assertEquals(BundledClips.size, paths.size) // no two texts share a file
        for (p in paths) {
            assertTrue("asset path shape: $p", p.startsWith("voice/") && p.endsWith(".mp3"))
        }
    }
}
