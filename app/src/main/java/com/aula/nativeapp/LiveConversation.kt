package com.aula.nativeapp

import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/*
 * Conversación en vivo — the real-time-feel conversation mode inside
 * Conversaciones, ported from the proven Cognata design (run #229) and
 * adapted to Aula native's actual structure. What differs from Cognata,
 * verified on disk before writing this file:
 *
 *  - Aula native had NO free-speech conversation evaluator at all:
 *    Conversaciones was multiple-choice only. [ConversationScorer] is
 *    therefore a port of Cognata's AiPartnerScenarios.evaluateReply
 *    (same weighted math, same Spanish-answer gate, same trap
 *    penalties, same whole-token/stem keyword matcher), re-typed over
 *    [LivePrompt] instead of Cognata's ConversationTurn. There is no
 *    second evaluator with different math anywhere in this app: the
 *    guided scenes keep their choice-based logging untouched, and
 *    every spoken live reply is graded by this one scorer.
 *  - Aula has no XP ledger and no conversation "completion record":
 *    Conversaciones' own completion semantics are per-turn Mirror
 *    events in the "conversaciones" context, and the live mode follows
 *    exactly that (see LiveConversationScreen). Nothing else is
 *    written at wrap-up, and leaving mid-session writes nothing extra.
 *  - Aula has no bundled voice clips and no DualModeVoiceResolver:
 *    partner lines speak through TtsManager, exactly like the guided
 *    scenes' lines do.
 *  - Session state lives in Compose `remember` (this app has no
 *    ViewModel layer): a live session is session-only by construction.
 *    Process death therefore resets it cleanly — the documented v1
 *    exclusion, same as Cognata's live mode. Per-turn Mirror events
 *    already logged survive, exactly as guided picks do.
 *
 * The flow engine is deterministic and local: the partner follows up
 * on what the learner's transcript actually contained (branch
 * triggers), or honestly re-anchors to the scene's topic (generics),
 * or steers to a fresh topic (unused branches). No cloud model, no
 * free generation — the partner never pretends to understand what its
 * keyword detection did not catch.
 */

// ------------------------------------------------------------------ scoring types

/** Turn verdict bands — same thresholds as Cognata's ScoreCategory. */
enum class LiveScoreCategory { GREEN, YELLOW, RED }

/** The graded verdict for one spoken live reply. */
data class LiveTurnScore(
    val expectedLine: String,
    val spokenText: String,
    val accuracyScore: Int, // 0 - 100
    val scoreCategory: LiveScoreCategory, // GREEN (>=85), YELLOW (>=60), RED (<60)
    val clarityTipEs: String,
    val feedbackMessageEs: String,
    val detectedTraps: List<String> = emptyList()
)

/** What [ConversationScorer.evaluateReply] grades a reply against. */
data class LiveScoringTarget(
    val modelReply: String,
    val targetKeywords: List<String>,
    val clarityTipEs: String,
    val trapNotesEs: List<String> = emptyList()
)

// ------------------------------------------------------------------ live content model

/**
 * One partner prompt in live mode: the line the partner says, the
 * Spanish objective shown to the learner (always visible), the model
 * replies the turn is scored against (their full text stays hidden
 * until the learner's first real attempt or an explicit reveal — the
 * suggestion gate), and the teaching metadata scoring reuses.
 */
data class LivePrompt(
    val id: String,
    val partnerLine: String,
    val objectiveEs: String,
    val modelReplies: List<String>,
    val targetKeywords: List<String>,
    val clarityTipEs: String,
    val trapNotesEs: List<String> = emptyList(),
    /**
     * Words in this prompt's replies that Aula's own curriculum knows
     * are Spanish-speaker traps (the phantom-E s-words the syntax and
     * conversation notes warn about). Membership only enriches a
     * pronunciation mark's explanation and the improvement chip's
     * priority — it never creates a mark by itself; a mark requires
     * recognizer evidence (see LiveWordFeedbackAnalyzer).
     */
    val trapWords: List<String> = emptyList()
)

/**
 * A topic branch: when the learner's latest transcript hits any of
 * [triggerKeywords] (matched with the scorer's own stem semantics),
 * the partner follows up on THAT topic.
 */
data class LiveBranch(
    val prompt: LivePrompt,
    val triggerKeywords: List<String>
)

/** The full live bank for one of Aula's conversations. */
data class LiveBank(
    val conversationId: String,
    /** Opening prompt; its line reuses the guided scene's first partner line verbatim. */
    val opening: LivePrompt,
    val branches: List<LiveBranch>,
    /** Topic-anchored follow-ups used when no branch trigger hits. */
    val generics: List<LivePrompt>,
    /** Partner's closing line once the exchange goal is reached or the bank is exhausted. */
    val wrapUpLine: String
)

// ------------------------------------------------------------------ word feedback model

/** Per-word verdict for one graded live turn. */
enum class LiveWordStatus {
    /** The word was produced cleanly. */
    OK,

    /** The word was missing from the reply, or a different word took its place. */
    WRONG_OR_MISSING,

    /**
     * Pronunciation trap evidence exists for this word: the scored form
     * IS the trap form, or a recognizer alternative carries the trap
     * form for it (PronunciationBoundary rule (a)). Never assigned
     * without that evidence.
     */
    PRONUNCIATION_SUSPECT
}

/** One model-reply word's feedback inside a graded live turn. */
data class LiveWordFeedback(
    /** Normalized model word (lowercase, punctuation stripped). */
    val word: String,
    /** The model word as displayed in the reply (original casing). */
    val displayWord: String,
    /** What the recognizer aligned to this word, or null when nothing matched it. */
    val spokenForm: String?,
    val status: LiveWordStatus,
    /** One-line Spanish explanation La Profesora shows verbatim. */
    val reasonEs: String,
    /** The word's own accuracy (word-alignment score, or the trap-form score for suspects). */
    val score: Int
)

/** Word-level feedback for one graded live turn. */
data class LiveTurnFeedback(
    val words: List<LiveWordFeedback>,
    /**
     * The single weakest word of the turn — the "Puedes mejorar" chip.
     * Deterministic pick: first pronunciation-suspect, else first
     * wrong/missing content word, else first wrong/missing word;
     * null when every word is OK.
     */
    val weakestWord: LiveWordFeedback?
)

// ------------------------------------------------------------------ session model

/** One completed exchange: the prompt served, what the learner said, and both verdicts. */
data class LiveExchange(
    val promptId: String,
    val partnerLine: String,
    val spokenText: String,
    val result: LiveTurnScore,
    val feedback: LiveTurnFeedback
)

/** One chat bubble in the live transcript. */
data class LiveChatEntry(
    val id: Int,
    val fromPartner: Boolean,
    val text: String,
    /** Partner entries: the prompt this line belongs to (null for the wrap-up line). */
    val promptId: String? = null,
    /** Learner entries: index into the session's exchanges. */
    val exchangeIndex: Int? = null
)

/**
 * Live session state, held only in the live screen's `remember` —
 * session-only by design (no draft, no process-death restore in v1).
 */
data class LiveSessionState(
    val conversation: Conversation,
    val bank: LiveBank,
    val partnerName: String,
    val entries: List<LiveChatEntry> = emptyList(),
    val exchanges: List<LiveExchange> = emptyList(),
    /** The prompt the learner is currently answering; null once finished. */
    val currentPrompt: LivePrompt? = null,
    val usedBranchIds: Set<String> = emptySet(),
    val usedGenericIds: Set<String> = emptySet(),
    /**
     * True between the learner's mic tap and the recording of that
     * reply. The record path refuses to grade when this is false, so a
     * stale recognition value can never be recorded twice (or as
     * another prompt's answer).
     */
    val awaitingReply: Boolean = false,
    val isFinished: Boolean = false,
    /** Word currently expanded in the inline word-practice card, if any. */
    val practiceWord: String? = null,
    /**
     * The exchange whose chip opened the practice card, and that
     * exchange's own feedback for the word. The card must speak for
     * ITS turn — the same word can be weak in several turns with
     * different evidence, so the reason/score are carried from the
     * tapped chip, never re-derived from the first matching exchange.
     */
    val practiceExchangeIndex: Int? = null,
    val practiceFeedback: LiveWordFeedback? = null,
    /**
     * Latest single-word retry score for [practiceWord], produced by
     * the word-practice path (PronunciationBoundary + word accuracy).
     * Display-only: it belongs to the turn that surfaced the word and
     * is never written to the Mirror diary as a separate event — the
     * Copia first-verdict spirit: the turn's own verdict already
     * counted; a retry is practice, not a new rating.
     */
    val practiceScore: Int? = null
) {
    val exchangesDone: Int
        get() = exchanges.size

    val runningAverageScore: Int
        get() {
            if (exchanges.isEmpty()) return 0
            return exchanges.sumOf { it.result.accuracyScore } / exchanges.size
        }

    /** Distinct weak words across the session, in first-appearance order (recap). */
    val weakWords: List<LiveWordFeedback>
        get() {
            val seen = mutableSetOf<String>()
            val out = mutableListOf<LiveWordFeedback>()
            for (exchange in exchanges) {
                for (word in exchange.feedback.words) {
                    if (word.status != LiveWordStatus.OK && seen.add(word.word)) {
                        out.add(word)
                    }
                }
            }
            return out
        }
}

/**
 * The Mirror events ONE graded live turn writes, as data — the exact
 * shapes LiveConversationScreen logs, in order, so the wiring itself
 * is unit-testable:
 *
 *  - one "conversation_turn" event in the "conversaciones" context
 *    (correct = the scorer's GREEN band) — the same shape the guided
 *    scenes write for a turn;
 *  - one [PHANTOM_E_PATTERN_KEY] event (correct = false) per
 *    PRONUNCIATION_SUSPECT word. Suspects are phantom-E evidence by
 *    construction, and the family is ONE Mirror pattern: a per-word
 *    key per suspect could never satisfy Mirror's naming rule
 *    (>= 3 errors across >= 2 contexts — the Guide, the only other
 *    pronunciation logger, teaches unrelated words, and no transfer
 *    items exist for stray words). The family's second context is
 *    the Guide itself: GuideCard ratings on an s-trap guide word
 *    ("sister") log the same key in the Guide's contexts.
 */
fun liveMirrorEvents(result: LiveTurnScore, feedback: LiveTurnFeedback): List<Triple<String, String, Boolean>> {
    val events = mutableListOf<Triple<String, String, Boolean>>()
    events.add(
        Triple(
            "conversation_turn", "conversaciones",
            result.scoreCategory == LiveScoreCategory.GREEN
        )
    )
    feedback.words
        .filter { it.status == LiveWordStatus.PRONUNCIATION_SUSPECT }
        .forEach { events.add(Triple(PHANTOM_E_PATTERN_KEY, "conversaciones", false)) }
    return events
}

// ------------------------------------------------------------------ the scorer

/**
 * The conversation scorer — a faithful port of Cognata's
 * AiPartnerScenarios.evaluateReply math (proven there against a
 * 61-case battery): phantom-E trap detection, Spanish-word
 * substitutes, the Spanish-answer gate (cap 55), whole-token/stem
 * keyword coverage, Levenshtein similarity against the model reply,
 * the coverage-weighted blend, and −12 per detected trap.
 *
 * Documented adaptations for Aula (presentation only, never math):
 *  - it grades against a [LiveScoringTarget] built from a [LivePrompt]
 *    instead of Cognata's ConversationTurn;
 *  - feedback strings are written in Aula's tutor voice (La Profesora
 *    presents them); the GREEN/YELLOW/RED wording differs, the bands
 *    and numbers do not.
 */
object ConversationScorer {

    fun evaluateReply(
        spokenText: String,
        target: LiveScoringTarget
    ): LiveTurnScore {
        val cleanSpoken = spokenText.trim().lowercase(Locale.US)
        val cleanExpected = target.modelReply.trim().lowercase(Locale.US)

        if (cleanSpoken.isEmpty() || cleanSpoken == "(sin audio detectado)") {
            return LiveTurnScore(
                expectedLine = target.modelReply,
                spokenText = if (spokenText.isBlank()) "(Silencio detectado)" else spokenText,
                accuracyScore = 45,
                scoreCategory = LiveScoreCategory.RED,
                clarityTipEs = target.clarityTipEs,
                feedbackMessageEs = "No se detectó audio claro. Habla un poco más fuerte y más cerca del micrófono.",
                detectedTraps = listOf("Sin audio o volumen bajo")
            )
        }

        val spokenTokens = cleanSpoken.split(Regex("[^a-zA-Záéíóúñ]+")).filter { it.isNotEmpty() }

        val traps = mutableListOf<String>()

        // 1. Check for 'E' fantasma (Spanish speaker initial vowel insertion)
        val phantomWords = listOf("es-", "is-", "especial", "estress", "eschedule", "esperience", "espicy", "esmart", "eschool")
        for (token in spokenTokens) {
            if (phantomWords.any { token.startsWith(it) }) {
                traps.add("Trampa de la 'E' fantasma detectada en '$token': arranca con silbido 'sss' directo.")
            }
        }
        // Also check the target keywords against the trap forms a
        // learner actually produces: the phantom-E is ONE prepended
        // vowel — "e" + keyword ("esister") or "i" + keyword — the
        // exact forms PronunciationBoundary polices, under the same
        // s-not-sh gate. Matched as whole tokens, so a word that
        // merely CONTAINS the form ("restore" contains "estore")
        // never fires it.
        target.targetKeywords.forEach { kw ->
            val k = kw.lowercase(Locale.US)
            if (k.startsWith("s") && !k.startsWith("sh")) {
                val trapFormSpoken = spokenTokens.any { it == "e$k" || it == "i$k" }
                if (trapFormSpoken || cleanSpoken.contains("es$k") || cleanSpoken.contains("es $k") || cleanSpoken.contains("es-$k")) {
                    traps.add("Vocalización española involuntaria antes de '$k'.")
                }
            }
        }

        // 2. Check for Spanish words substituted in place of English
        val spanishSubstitutes = mapOf(
            "actualmente" to "Trampa de Falso Amigo: dijiste 'actualmente'; en inglés se dice 'currently'.",
            "en realidad" to "Falso amigo: 'en realidad' se dice 'actually'.",
            "embarazada" to "Trampa: 'embarazada' en inglés es 'pregnant', no 'embarrassed'.",
            "carpeta" to "Trampa: 'carpeta' es 'folder', no 'carpet'.",
            "fabrica" to "Trampa: 'fábrica' es 'factory', no 'fabric'.",
            "exito" to "Trampa: 'éxito' es 'success', no 'exit'.",
            "inmediatamente" to "Palabra en español detectada: usa el cognado inglés 'immediately'.",
            "gracias" to "Palabra en español detectada: usa 'thank you'.",
            "hola" to "Palabra en español detectada: usa 'hello' o 'hi'.",
            "bueno" to "Palabra en español detectada: usa 'good' o 'well'.",
            "porque" to "Palabra en español detectada: usa 'because'.",
            "pero" to "Palabra en español detectada: usa 'but'."
        )
        spanishSubstitutes.forEach { (word, message) ->
            if (cleanSpoken.contains(word)) {
                traps.add(message)
            }
        }

        // Spanish-answer gate: a reply given mostly in Spanish must
        // never be graded as a partial success. Signals are distinct
        // Spanish function words or accented tokens; three or more
        // means the answer is Spanish, whatever cognates it contains.
        // The trap also feeds the standard penalty below, and the final
        // score is capped in the RED band.
        val spanishAnswer = spanishSignalCount(spokenTokens) >= 3
        if (spanishAnswer) {
            traps.add("Respuesta en español detectada: esta práctica debe ser en inglés.")
        }

        // 3. Keyword hit counting — whole-token matching (see
        // keywordMatched): raw substring search credited Spanish
        // cognates ('moderno' -> 'modern', 'arte' -> 'art') and
        // unrelated words ('art' inside 'started'/'party') as hits.
        val matchedKeywords = target.targetKeywords.count { kw ->
            keywordMatched(kw.lowercase(Locale.US), cleanSpoken, spokenTokens)
        }
        val keywordRatio = if (target.targetKeywords.isEmpty()) 1.0 else (matchedKeywords.toDouble() / target.targetKeywords.size)

        // 4. Similarity against the model reply
        val distance = calculateLevenshtein(cleanSpoken, cleanExpected)
        val maxLen = max(cleanSpoken.length, cleanExpected.length)
        val similarityScore = if (maxLen == 0) 100 else ((1.0 - (distance.toDouble() / maxLen)) * 100).toInt()

        // Weighted final precision score
        var finalScore = when {
            cleanSpoken == cleanExpected -> 99
            cleanSpoken.contains(cleanExpected) -> 96
            matchedKeywords == target.targetKeywords.size -> max(88, similarityScore)
            matchedKeywords >= 2 -> {
                // Coverage-weighted blend plus an 86 floor at >= 80%
                // coverage — the same coverage-dominant rule the
                // all-keywords branch applies at 100%, one notch down.
                val blended = max(72, (keywordRatio * 80 + similarityScore * 0.4).toInt())
                if (keywordRatio >= 0.8) max(blended, 86) else blended
            }
            matchedKeywords == 1 -> {
                // A lone keyword is not a reply. A short but genuinely
                // overlapping fragment of the target reply (similarity
                // >= 45) still earns a low YELLOW.
                val base = (keywordRatio * 40 + similarityScore * 0.4).toInt()
                val withFragmentFloor = if (similarityScore >= 45) max(base, 62) else base
                withFragmentFloor.coerceIn(35, 65)
            }
            else -> similarityScore.coerceIn(35, 65)
        }
        if (finalScore > 100) finalScore = 100

        // Penalty for traps detected
        if (traps.isNotEmpty()) {
            finalScore = (finalScore - (traps.size * 12)).coerceAtLeast(40)
        }
        if (spanishAnswer) {
            finalScore = min(finalScore, 55)
        }

        val category = when {
            finalScore >= 85 -> LiveScoreCategory.GREEN
            finalScore >= 60 -> LiveScoreCategory.YELLOW
            else -> LiveScoreCategory.RED
        }

        val feedback = when (category) {
            LiveScoreCategory.GREEN -> "¡Eso sonó a conversación de verdad! Frase completa, clara y natural."
            LiveScoreCategory.YELLOW -> "Se entendió. Tu frase llegó; ahora pule el ritmo y las consonantes finales."
            LiveScoreCategory.RED -> "Esa no llegó todavía. Mira el consejo de claridad y la palabra marcada: ahí está el arreglo."
        }

        return LiveTurnScore(
            expectedLine = target.modelReply,
            spokenText = spokenText,
            accuracyScore = finalScore,
            scoreCategory = category,
            clarityTipEs = target.clarityTipEs,
            feedbackMessageEs = feedback,
            detectedTraps = traps
        )
    }

    // ── Scoring helpers (ported with the scorer; keep in sync) ─────

    private val spanishStopwords = setOf(
        "el", "la", "los", "las", "un", "una", "unos", "unas", "de", "en",
        "y", "es", "son", "mi", "tu", "su", "del", "al", "por", "para",
        "con", "que", "se", "me", "te", "lo", "estoy", "tengo", "tiene",
        "hace", "quiero", "puedo", "voy", "fue"
    )

    /** Distinct Spanish signals in [tokens]: function words + accented tokens. */
    private fun spanishSignalCount(tokens: List<String>): Int {
        val signals = mutableSetOf<String>()
        for (token in tokens) {
            if (token in spanishStopwords || token.any { it in "áéíóúüñ" }) {
                signals.add(token)
            }
        }
        return signals.size
    }

    /** Light English suffix strip used only for keyword matching. */
    internal fun normalizeForMatch(word: String): String = when {
        word.length > 6 && word.endsWith("ing") -> word.dropLast(3)
        word.length > 5 && word.endsWith("ed") -> word.dropLast(2)
        word.length > 5 && word.endsWith("es") -> word.dropLast(2)
        word.length > 4 && word.endsWith("s") && !word.endsWith("ss") -> word.dropLast(1)
        else -> word
    }

    /**
     * Whole-token keyword match: exact token, English plural (± "s"), or
     * a light-stem match (suffix-stripped equality, or a >= 5-char stem
     * prefix in either direction, e.g. 'managing' ~ 'management').
     * Hyphenated keywords ('follow-up') match the hyphenated or spaced
     * form in the raw text.
     *
     * Internal (not private) so the live flow engine matches branch
     * triggers with EXACTLY this semantics — one matcher for scoring
     * and for flow, never two divergent stemmers.
     */
    internal fun keywordMatched(keyword: String, cleanSpoken: String, tokens: List<String>): Boolean {
        if ("-" in keyword) {
            return cleanSpoken.contains(keyword) || cleanSpoken.contains(keyword.replace('-', ' '))
        }
        val nk = normalizeForMatch(keyword)
        return tokens.any { token ->
            val nt = normalizeForMatch(token)
            token == keyword || token == keyword + "s" || token + "s" == keyword ||
                nt == nk ||
                (nk.length >= 5 && nt.startsWith(nk)) ||
                (nt.length >= 5 && nk.startsWith(nt))
        }
    }

    private fun calculateLevenshtein(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[s1.length][s2.length]
    }
}

// ------------------------------------------------------------------ the flow engine

/**
 * The deterministic flow engine behind Conversación en vivo — the
 * Cognata engine's semantics, unchanged:
 *
 * Given a conversation's [LiveBank], the session's used-prompt state
 * and the learner's latest transcript, it picks the partner's next
 * prompt:
 *
 *  1. an UNUSED topic branch whose trigger keywords hit the transcript
 *     (the partner follows up on what the learner actually said);
 *  2. else an UNUSED generic follow-up (anchored to the scene's topic,
 *     so the partner honestly re-anchors instead of faking
 *     comprehension of content it did not detect);
 *  3. else an UNUSED branch in bank order (the partner changes the
 *     subject to a fresh scene topic);
 *  4. else null — the caller serves the wrap-up line and closes the
 *     session.
 *
 * The exchange goal ([EXCHANGE_GOAL] learner turns) ends the session
 * the same way: [nextPrompt] returns null once it is reached.
 *
 * Selection is a pure function of (bank, used-state, transcript):
 * strict bank order, no randomness anywhere, so the same inputs always
 * produce the same conversation — unit-testable and reproducible.
 * Trigger matching reuses [ConversationScorer.keywordMatched], the
 * scorer's own whole-token/stem matcher, so flow and scoring can never
 * disagree about what a transcript "contains".
 */
object LiveConversationEngine {

    /** Learner turns per live session before the partner wraps up. */
    const val EXCHANGE_GOAL = 8

    /** The prompt a session always opens with. */
    fun openingPrompt(bank: LiveBank): LivePrompt = bank.opening

    /**
     * Picks the next prompt after a graded learner reply, or null when
     * the session should wrap up (goal reached, or branches and
     * generics both exhausted). [usedBranchIds] / [usedGenericIds] hold
     * the PROMPT ids already served (the opening is served before any
     * call and is in neither set); [exchangesDone] counts graded
     * learner turns INCLUDING the one just answered.
     */
    fun nextPrompt(
        bank: LiveBank,
        usedBranchIds: Set<String>,
        usedGenericIds: Set<String>,
        exchangesDone: Int,
        lastTranscript: String
    ): LivePrompt? {
        if (exchangesDone >= EXCHANGE_GOAL) return null

        val clean = lastTranscript.trim().lowercase(Locale.US)
        val tokens = clean.split(Regex("[^a-zA-Záéíóúñ]+")).filter { it.isNotEmpty() }

        // 1. Follow the learner's actual topic.
        for (branch in bank.branches) {
            if (branch.prompt.id in usedBranchIds) continue
            val hit = branch.triggerKeywords.any { trigger ->
                ConversationScorer.keywordMatched(trigger.lowercase(Locale.US), clean, tokens)
            }
            if (hit) return branch.prompt
        }

        // 2. Re-anchor with an unused topic generic.
        for (generic in bank.generics) {
            if (generic.id !in usedGenericIds) return generic
        }

        // 3. Steer to a fresh scene topic.
        for (branch in bank.branches) {
            if (branch.prompt.id !in usedBranchIds) return branch.prompt
        }

        // 4. Bank exhausted.
        return null
    }
}

// ------------------------------------------------------------------ the banks

/**
 * The Conversación en vivo content banks — one per existing Aula
 * conversation (Data.kt CONVERSATIONS: "cafe", "ayer", "hotel" — all
 * three of Aula native's conversations are covered; the web app's
 * wider set does not exist in this native build, so there is nothing
 * else to cover).
 *
 * Authoring rules, so the flow engine stays honest:
 *  - each OPENING reuses the guided scene's first partner line
 *    verbatim (Aula has no bundled clips, so the reuse is for persona
 *    and wording continuity, and its model replies extend the guided
 *    scene's own correct option);
 *  - branch triggers are words the LEARNER plausibly says about that
 *    topic (reply vocabulary), never words only the partner would
 *    say — a branch is served because the transcript earned it;
 *  - generic follow-ups assume nothing about the transcript beyond
 *    the scene's world: they re-anchor the topic honestly;
 *  - trigger matching is stem-based via the scorer's own matcher, so
 *    triggers are written as base forms;
 *  - trapWords come from Aula's own curriculum: the phantom-E s-words
 *    that appear in these scenes' replies and in the syntax notes
 *    (sister, sandwich, salad, store, soccer…).
 */
object LiveConversationBanks {

    fun bankFor(conversationId: String): LiveBank? = when (conversationId) {
        "cafe" -> cafe
        "ayer" -> ayer
        "hotel" -> hotel
        "mercado" -> mercado
        "trabajo" -> trabajo
        "direcciones" -> direcciones
        "medico" -> medico
        "aeropuerto" -> aeropuerto
        "restaurante" -> restaurante
        else -> null
    }

    /** The partner's display name for a conversation (its guided first speaker). */
    fun partnerNameFor(conversation: Conversation): String =
        conversation.turns.firstOrNull()?.who ?: "Tu compañero"

    // ---------------------------------------------------------- cafetería

    private val cafe = LiveBank(
        conversationId = "cafe",
        opening = LivePrompt(
            id = "opening",
            partnerLine = "Good morning! What would you like?",
            objectiveEs = "Pide un café con la forma cortés: «I would like…».",
            modelReplies = listOf(
                "I would like a coffee, please.",
                "I would like a coffee and a sandwich, please."
            ),
            targetKeywords = listOf("would", "like", "coffee", "please"),
            clarityTipEs = "«I would like» es la forma cortés de pedir; «I want» suena rudo.",
            trapWords = listOf("sandwich")
        ),
        branches = listOf(
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_food",
                    partnerLine = "We have fresh sandwiches and salads today. Would you like something to eat?",
                    objectiveEs = "Pide algo de comer: un sándwich o una ensalada fresca.",
                    modelReplies = listOf(
                        "I would like a sandwich, please.",
                        "Yes, a fresh salad, please."
                    ),
                    targetKeywords = listOf("sandwich", "salad", "fresh", "please"),
                    clarityTipEs = "«Sandwich» y «salad» arrancan con /s/ directa, sin vocal antes: nada de «es-andwich».",
                    trapNotesEs = listOf("La 'e' fantasma antes de la /s/ inicial es la trampa clásica: «sandwich», no «esandwich»."),
                    trapWords = listOf("sandwich", "salad")
                ),
                triggerKeywords = listOf("sandwich", "hungry", "eat", "food", "salad")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_size",
                    partnerLine = "Small, medium, or large?",
                    objectiveEs = "Elige el tamaño de tu café.",
                    modelReplies = listOf(
                        "A large coffee, please.",
                        "Medium, please, with milk."
                    ),
                    targetKeywords = listOf("large", "medium", "coffee", "please"),
                    clarityTipEs = "Respuesta corta y completa: el tamaño y «please».",
                    trapWords = listOf("small")
                ),
                triggerKeywords = listOf("big", "large", "small", "size", "medium")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_milk",
                    partnerLine = "With milk or sugar?",
                    objectiveEs = "Di cómo quieres el café: con leche, con azúcar o solo.",
                    modelReplies = listOf(
                        "With milk, please, no sugar.",
                        "A little sugar, please."
                    ),
                    targetKeywords = listOf("milk", "sugar", "please"),
                    clarityTipEs = "«Sugar» arranca con /s/: «shú-gar», sin «e» antes.",
                    trapWords = listOf("sugar")
                ),
                triggerKeywords = listOf("milk", "sugar", "sweet")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_price",
                    partnerLine = "It is three dollars. Cash or card?",
                    objectiveEs = "Di cómo pagas: efectivo o tarjeta.",
                    modelReplies = listOf(
                        "By card, please.",
                        "Cash, please. Here you are."
                    ),
                    targetKeywords = listOf("card", "cash", "please"),
                    clarityTipEs = "«By card» es la fórmula breve y natural para pagar con tarjeta."
                ),
                triggerKeywords = listOf("much", "cost", "price", "pay", "card", "cash")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_iced",
                    partnerLine = "Would you like it iced or hot today?",
                    objectiveEs = "Elige: café helado o caliente.",
                    modelReplies = listOf(
                        "Iced, please. It is hot today.",
                        "Hot, please."
                    ),
                    targetKeywords = listOf("iced", "hot", "please"),
                    clarityTipEs = "«Iced» se dice en una sola sílaba larga: «aist»."
                ),
                triggerKeywords = listOf("iced", "cold", "ice", "hot")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_table",
                    partnerLine = "We have a table by the window free. Would you like it?",
                    objectiveEs = "Acepta la mesa junto a la ventana, o pide sentarte ahí.",
                    modelReplies = listOf(
                        "Yes, please. Can I sit by the window?",
                        "A table by the window is perfect."
                    ),
                    targetKeywords = listOf("table", "window", "sit", "please"),
                    clarityTipEs = "«Sit» arranca con /s/ corta y seca, sin vocal antes.",
                    trapWords = listOf("sit")
                ),
                triggerKeywords = listOf("table", "sit", "seat", "window")
            )
        ),
        generics = listOf(
            LivePrompt(
                id = "g_first",
                partnerLine = "Is this your first time here?",
                objectiveEs = "Responde: ¿es tu primera vez en esta cafetería?",
                modelReplies = listOf(
                    "Yes, it is my first time here.",
                    "No, I come here every week."
                ),
                targetKeywords = listOf("first", "time", "come", "week"),
                clarityTipEs = "Frase completa: «It is my first time here»."
            ),
            LivePrompt(
                id = "g_dessert",
                partnerLine = "Would you like a small dessert with your coffee?",
                objectiveEs = "Acepta o rechaza el postre con cortesía.",
                modelReplies = listOf(
                    "Yes, a small dessert, please.",
                    "No, thank you. That is all."
                ),
                targetKeywords = listOf("small", "dessert", "please", "all"),
                clarityTipEs = "«Small» arranca con /s/ directa: «smol», sin «e» antes.",
                trapWords = listOf("small")
            ),
            LivePrompt(
                id = "g_day",
                partnerLine = "Your order is almost ready. How is your day going?",
                objectiveEs = "Cuenta brevemente cómo va tu día.",
                modelReplies = listOf(
                    "My day is going well, thank you.",
                    "Very well. It is a beautiful day."
                ),
                targetKeywords = listOf("day", "well", "thank"),
                clarityTipEs = "«How is your day going?» es saludo, no examen: responde corto y sonríe."
            ),
            LivePrompt(
                id = "g_else",
                partnerLine = "Can I get you anything else while you wait?",
                objectiveEs = "Pide algo más, o cierra con «That is all».",
                modelReplies = listOf(
                    "That is all, thank you.",
                    "A glass of water, please."
                ),
                targetKeywords = listOf("all", "thank", "water", "please"),
                clarityTipEs = "«That is all» = eso es todo: la fórmula de cierre de la escena guiada."
            )
        ),
        wrapUpLine = "Perfect. Your order is coming right up. Enjoy your coffee, and have a great day!"
    )

    // ---------------------------------------------------------- ¿qué hiciste ayer?

    private val ayer = LiveBank(
        conversationId = "ayer",
        opening = LivePrompt(
            id = "opening",
            partnerLine = "Hi! What did you do yesterday?",
            objectiveEs = "Cuenta qué hiciste ayer, con el verbo en pasado.",
            modelReplies = listOf(
                "Yesterday I went to the park.",
                "Yesterday I visited my family."
            ),
            targetKeywords = listOf("yesterday", "went", "visited"),
            clarityTipEs = "Con «yesterday», el verbo cambia: go → went, visit → visited.",
            trapNotesEs = listOf("Nunca «Yesterday I go»: el pasado es obligatorio con «yesterday».")
        ),
        branches = listOf(
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_family",
                    partnerLine = "Nice! Did your family enjoy the day too?",
                    objectiveEs = "Cuenta qué hizo tu familia ayer.",
                    modelReplies = listOf(
                        "Yes, I watched a movie with my sister.",
                        "My sister cooked dinner for the family."
                    ),
                    targetKeywords = listOf("sister", "family", "watched", "dinner"),
                    clarityTipEs = "«Sister» arranca con /s/ directa, sin «e» antes: «sís-ter».",
                    trapWords = listOf("sister")
                ),
                triggerKeywords = listOf("family", "sister", "mother", "brother", "father")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_park",
                    partnerLine = "The park is beautiful in the morning. Did you walk a lot?",
                    objectiveEs = "Cuenta tu paseo por el parque, en pasado.",
                    modelReplies = listOf(
                        "We walked in the park for two hours.",
                        "I walked my dog in the park."
                    ),
                    targetKeywords = listOf("walked", "park", "hours", "dog"),
                    clarityTipEs = "«Walked» suena «uókt»: la -ed final es una /t/ seca."
                ),
                triggerKeywords = listOf("park", "walk", "dog", "walked")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_food",
                    partnerLine = "And what did you eat yesterday?",
                    objectiveEs = "Cuenta qué comiste, con el verbo en pasado.",
                    modelReplies = listOf(
                        "We ate pizza at a small restaurant.",
                        "I had dinner with my family."
                    ),
                    targetKeywords = listOf("ate", "pizza", "dinner", "restaurant"),
                    clarityTipEs = "Eat → ate: el pasado de «eat» es irregular, «ate».",
                    trapWords = listOf("small")
                ),
                triggerKeywords = listOf("ate", "eat", "pizza", "dinner", "lunch", "restaurant")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_buy",
                    partnerLine = "Did you buy anything interesting?",
                    objectiveEs = "Cuenta qué compraste ayer.",
                    modelReplies = listOf(
                        "Yes, I bought a book yesterday.",
                        "I bought new shoes at the store."
                    ),
                    targetKeywords = listOf("bought", "book", "shoes", "store"),
                    clarityTipEs = "Buy → bought: nunca «buyed».",
                    trapWords = listOf("store")
                ),
                triggerKeywords = listOf("bought", "buy", "book", "shop", "store", "shoes")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_soccer",
                    partnerLine = "Did you play soccer, or did you watch the game?",
                    objectiveEs = "Di si jugaste al fútbol o viste el partido.",
                    modelReplies = listOf(
                        "We played soccer in the park.",
                        "I watched the game at home."
                    ),
                    targetKeywords = listOf("played", "soccer", "watched", "game"),
                    clarityTipEs = "«Soccer» arranca con /s/ directa: «sá-ker», sin «e» antes.",
                    trapWords = listOf("soccer")
                ),
                triggerKeywords = listOf("soccer", "played", "football", "game", "sport")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_evening",
                    partnerLine = "And last night? What did you do in the evening?",
                    objectiveEs = "Cuenta qué hiciste anoche, en pasado.",
                    modelReplies = listOf(
                        "Last night we watched a movie at home.",
                        "I went to bed early last night."
                    ),
                    targetKeywords = listOf("last", "night", "watched", "movie", "went"),
                    clarityTipEs = "«Last night» manda el verbo al pasado: watch → watched."
                ),
                triggerKeywords = listOf("night", "evening", "movie", "watched")
            )
        ),
        generics = listOf(
            LivePrompt(
                id = "g_morning",
                partnerLine = "What about the morning? How did your day start?",
                objectiveEs = "Cuenta cómo empezó tu día.",
                modelReplies = listOf(
                    "I woke up early and drank coffee.",
                    "My day started with a long walk."
                ),
                targetKeywords = listOf("woke", "early", "coffee", "started"),
                clarityTipEs = "«Started» lleva la -ed del pasado: «stár-ted».",
                trapWords = listOf("started")
            ),
            LivePrompt(
                id = "g_weather",
                partnerLine = "Was the weather good yesterday?",
                objectiveEs = "Cuenta qué tiempo hizo ayer.",
                modelReplies = listOf(
                    "Yes, it was sunny all day.",
                    "No, it rained in the afternoon."
                ),
                targetKeywords = listOf("was", "sunny", "rained", "day"),
                clarityTipEs = "«Sunny» arranca con /s/ directa: «sá-ni», sin «e» antes.",
                trapWords = listOf("sunny")
            ),
            LivePrompt(
                id = "g_friends",
                partnerLine = "Did you see your friends yesterday?",
                objectiveEs = "Di si viste a tus amigos y qué hicieron.",
                modelReplies = listOf(
                    "Yes, I visited my friends in the afternoon.",
                    "No, I stayed home all day."
                ),
                targetKeywords = listOf("visited", "friends", "stayed", "home"),
                clarityTipEs = "«Stayed» también es pasado regular: stay → stayed.",
                trapWords = listOf("stayed")
            ),
            LivePrompt(
                id = "g_tired",
                partnerLine = "It sounds like a full day. Were you tired at the end?",
                objectiveEs = "Di si terminaste el día cansado.",
                modelReplies = listOf(
                    "Yes, I was very tired at night.",
                    "A little, but it was a great day."
                ),
                targetKeywords = listOf("was", "tired", "night", "day"),
                clarityTipEs = "«I was» es el pasado de «I am»: úsalo para contar cómo estabas."
            )
        ),
        wrapUpLine = "What a day! Thanks for telling me everything. Let's talk again tomorrow!"
    )

    // ---------------------------------------------------------- en el hotel

    private val hotel = LiveBank(
        conversationId = "hotel",
        opening = LivePrompt(
            id = "opening",
            partnerLine = "Good evening! Do you have a reservation?",
            objectiveEs = "Confirma tu reserva con una frase completa y tranquila.",
            modelReplies = listOf(
                "Yes, I have a reservation.",
                "Yes, I have a reservation for tonight."
            ),
            targetKeywords = listOf("have", "reservation"),
            clarityTipEs = "Frase completa y tranquila: «I have a reservation»."
        ),
        branches = listOf(
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_nights",
                    partnerLine = "How many nights are you staying with us?",
                    objectiveEs = "Di cuántas noches te quedas.",
                    modelReplies = listOf(
                        "I am staying for three nights.",
                        "Two nights, please."
                    ),
                    targetKeywords = listOf("staying", "nights", "three", "two"),
                    clarityTipEs = "«Staying» arranca con /s/ directa: «stéi-ing», sin «e» antes.",
                    trapWords = listOf("staying")
                ),
                triggerKeywords = listOf("nights", "night", "stay", "days", "week")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_room",
                    partnerLine = "Would you like a single room or a double room?",
                    objectiveEs = "Elige: habitación individual o doble.",
                    modelReplies = listOf(
                        "A single room, please.",
                        "A double room for two people, please."
                    ),
                    targetKeywords = listOf("single", "double", "room", "please"),
                    clarityTipEs = "«Single» arranca con /s/ directa: «síng-gol», sin «e» antes.",
                    trapWords = listOf("single")
                ),
                triggerKeywords = listOf("room", "single", "double", "bed")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_breakfast",
                    partnerLine = "Breakfast is from seven to ten. Would you like breakfast with your room?",
                    objectiveEs = "Acepta o rechaza el desayuno, y di a qué hora.",
                    modelReplies = listOf(
                        "Yes, breakfast at eight, please.",
                        "No, thank you. Just the room."
                    ),
                    targetKeywords = listOf("breakfast", "eight", "please"),
                    clarityTipEs = "Las horas se dicen cortas y claras: «at eight»."
                ),
                triggerKeywords = listOf("breakfast", "eat", "morning", "food")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_floor",
                    partnerLine = "Would you like a quiet room on a high floor?",
                    objectiveEs = "Pide una habitación tranquila en un piso alto.",
                    modelReplies = listOf(
                        "Yes, a quiet room on the second floor, please.",
                        "A quiet room, please, on any floor."
                    ),
                    targetKeywords = listOf("quiet", "room", "second", "floor"),
                    clarityTipEs = "«Second» arranca con /s/ directa: «sé-kond».",
                    trapWords = listOf("second")
                ),
                triggerKeywords = listOf("floor", "view", "quiet", "second")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_bags",
                    partnerLine = "Can I help you with your bags?",
                    objectiveEs = "Acepta la ayuda con tu maleta.",
                    modelReplies = listOf(
                        "Yes, please. My suitcase is heavy.",
                        "No, thank you. I can carry it."
                    ),
                    targetKeywords = listOf("suitcase", "heavy", "please", "carry"),
                    clarityTipEs = "«Suitcase» arranca con /s/ directa: «sút-keis».",
                    trapWords = listOf("suitcase")
                ),
                triggerKeywords = listOf("help", "bags", "luggage", "suitcase")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_wifi",
                    partnerLine = "The wifi password is on your key card. Do you need anything else?",
                    objectiveEs = "Escucha dónde está la contraseña, da las gracias y cierra — o pide una cosa más.",
                    modelReplies = listOf(
                        "Thank you very much. That is all.",
                        "Yes, please. One more pillow."
                    ),
                    targetKeywords = listOf("thank", "all", "please", "pillow"),
                    clarityTipEs = "«That is all» = eso es todo: la fórmula cortés para cerrar la conversación."
                ),
                triggerKeywords = listOf("wifi", "internet", "password")
            )
        ),
        generics = listOf(
            LivePrompt(
                id = "g_id",
                partnerLine = "May I see your passport or ID, please?",
                objectiveEs = "Entrega tu documento con la fórmula cortés.",
                modelReplies = listOf(
                    "Of course, here is my passport.",
                    "Yes, here you are."
                ),
                targetKeywords = listOf("course", "passport", "here"),
                clarityTipEs = "«Here is my passport» = aquí tiene mi pasaporte."
            ),
            LivePrompt(
                id = "g_pay",
                partnerLine = "How would you like to pay?",
                objectiveEs = "Di cómo pagas la habitación.",
                modelReplies = listOf(
                    "By card, please.",
                    "I pay by card."
                ),
                targetKeywords = listOf("card", "pay", "please"),
                clarityTipEs = "«By card» es la fórmula breve de la recepción."
            ),
            LivePrompt(
                id = "g_city",
                partnerLine = "Is this your first time in our city?",
                objectiveEs = "Responde: ¿es tu primera vez en la ciudad?",
                modelReplies = listOf(
                    "Yes, it is my first visit.",
                    "No, I come here every year."
                ),
                targetKeywords = listOf("first", "visit", "come", "year"),
                clarityTipEs = "Frase completa: «It is my first visit»."
            ),
            LivePrompt(
                id = "g_wakeup",
                partnerLine = "Your room is ready now. Would you like a wake-up call?",
                objectiveEs = "Pide (o no) la llamada de despertador y di la hora.",
                modelReplies = listOf(
                    "Yes, at seven in the morning, please.",
                    "No, thank you very much."
                ),
                targetKeywords = listOf("seven", "morning", "please", "thank"),
                clarityTipEs = "«Seven» arranca con /s/ directa: «sé-ven», sin «e» antes.",
                trapWords = listOf("seven")
            )
        ),
        wrapUpLine = "Everything is ready. Welcome to our hotel — enjoy your stay!"
    )

    // ---------------------------------------------------------- mercado

    private val mercado = LiveBank(
        conversationId = "mercado",
        opening = LivePrompt(
            id = "opening",
            partnerLine = "Hello! Can I help you?",
            objectiveEs = "Di qué buscas: pan fresco.",
            modelReplies = listOf(
                "Yes, I am looking for fresh bread.",
                "Yes, please. Do you have fresh bread?"
            ),
            targetKeywords = listOf("looking", "fresh", "bread", "please"),
            clarityTipEs = "«I am looking for…» es la fórmula para buscar algo en una tienda."
        ),
        branches = listOf(
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_fruit",
                    partnerLine = "The fruit is fresh today. We have apples and oranges.",
                    objectiveEs = "Pide fruta: manzanas o naranjas.",
                    modelReplies = listOf(
                        "I would like six apples, please.",
                        "A kilo of oranges, please."
                    ),
                    targetKeywords = listOf("apples", "oranges", "kilo", "please"),
                    clarityTipEs = "Las cantidades van antes de la fruta: «six apples», «a kilo of oranges»."
                ),
                triggerKeywords = listOf("fruit", "apple", "orange", "banana")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_howmany",
                    partnerLine = "How many would you like?",
                    objectiveEs = "Di la cantidad: dos o tres panes.",
                    modelReplies = listOf(
                        "Two, please.",
                        "Three loaves, please."
                    ),
                    targetKeywords = listOf("two", "three", "loaves", "please"),
                    clarityTipEs = "Respuesta corta y completa: el número y «please»."
                ),
                triggerKeywords = listOf("many", "two", "three", "number", "quantity")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_vegetables",
                    partnerLine = "We also have fresh vegetables: tomatoes and peppers.",
                    objectiveEs = "Pide verduras: tomates o pimientos.",
                    modelReplies = listOf(
                        "I would like some tomatoes, please.",
                        "Half a kilo of peppers, please."
                    ),
                    targetKeywords = listOf("tomatoes", "peppers", "kilo", "please"),
                    clarityTipEs = "«Some» es tu comodín de cantidad: «some tomatoes»."
                ),
                triggerKeywords = listOf("vegetable", "tomato", "pepper", "salad")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_price",
                    partnerLine = "That is four euros in total.",
                    objectiveEs = "Paga: entrega el dinero o pide pagar con tarjeta.",
                    modelReplies = listOf(
                        "Here you are. Thank you.",
                        "By card, please."
                    ),
                    targetKeywords = listOf("card", "please", "thank"),
                    clarityTipEs = "«Here you are» es lo que se dice al entregar algo."
                ),
                triggerKeywords = listOf("much", "cost", "price", "pay", "total", "euros")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_bag",
                    partnerLine = "Would you like a bag for your bread?",
                    objectiveEs = "Acepta la bolsa, o di que traes la tuya.",
                    modelReplies = listOf(
                        "Yes, please. One bag.",
                        "No, thank you. I have my bag."
                    ),
                    targetKeywords = listOf("bag", "please", "thank"),
                    clarityTipEs = "«I have my bag»: lo que traes contigo se dice con «I have»."
                ),
                triggerKeywords = listOf("bag")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_eggs",
                    partnerLine = "The eggs are fresh this morning, from the farm.",
                    objectiveEs = "Pide huevos: media docena o una docena.",
                    modelReplies = listOf(
                        "I would like six eggs, please.",
                        "A dozen eggs, please."
                    ),
                    targetKeywords = listOf("eggs", "six", "dozen", "please"),
                    clarityTipEs = "«A dozen» es una palabra-cantidad: doce de una vez."
                ),
                triggerKeywords = listOf("egg", "eggs", "dozen", "farm")
            )
        ),
        generics = listOf(
            LivePrompt(
                id = "g_first",
                partnerLine = "Is this your first time at this market?",
                objectiveEs = "Responde: ¿es tu primera vez en este mercado?",
                modelReplies = listOf(
                    "Yes, it is my first time here.",
                    "No, I come every Saturday."
                ),
                targetKeywords = listOf("first", "time", "come", "saturday"),
                clarityTipEs = "«Saturday» arranca con /s/ directa, sin «e» antes.",
                trapWords = listOf("saturday")
            ),
            LivePrompt(
                id = "g_morning",
                partnerLine = "It is a beautiful morning for the market, is it not?",
                objectiveEs = "Comenta la mañana tan bonita para el mercado.",
                modelReplies = listOf(
                    "Yes, it is a beautiful day.",
                    "Yes, perfect for fresh bread."
                ),
                targetKeywords = listOf("beautiful", "day", "fresh"),
                clarityTipEs = "Pequeña charla, frase completa: «It is a beautiful day»."
            ),
            LivePrompt(
                id = "g_local",
                partnerLine = "Everything here is local, from small farms.",
                objectiveEs = "Valora que todo sea local y de granjas pequeñas.",
                modelReplies = listOf(
                    "That is great. I love local food.",
                    "Local food is the best."
                ),
                targetKeywords = listOf("local", "food", "love", "best"),
                clarityTipEs = "«Local» se dice igual que se escribe: «lóu-cal»."
            ),
            LivePrompt(
                id = "g_help",
                partnerLine = "Take your time. I am here if you need anything.",
                objectiveEs = "Agradece su paciencia y su ayuda.",
                modelReplies = listOf(
                    "Thank you very much.",
                    "Thank you. You are very kind."
                ),
                targetKeywords = listOf("thank", "very", "kind"),
                clarityTipEs = "«You are very kind» cierra cualquier compra con clase."
            )
        ),
        wrapUpLine = "Thank you for visiting my stall. Come back any day!"
    )

    // ---------------------------------------------------------- trabajo

    private val trabajo = LiveBank(
        conversationId = "trabajo",
        opening = LivePrompt(
            id = "opening",
            partnerLine = "Welcome to the team! Do you speak English?",
            objectiveEs = "Responde con honestidad: hablas un poco de inglés.",
            modelReplies = listOf(
                "Yes, I speak a little English.",
                "Yes, a little. I am learning."
            ),
            targetKeywords = listOf("speak", "little", "english", "learning"),
            clarityTipEs = "«Speak» arranca con /sp/ directa: nada de «espeak».",
            trapNotesEs = listOf("La 'e' fantasma antes de la /s/ inicial es la trampa clásica: «speak», no «espeak»."),
            trapWords = listOf("speak")
        ),
        branches = listOf(
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_hours",
                    partnerLine = "The office opens at nine and closes at five.",
                    objectiveEs = "Confirma tu horario o pregunta a qué hora terminan.",
                    modelReplies = listOf(
                        "I start at nine every day.",
                        "What time do we finish?"
                    ),
                    targetKeywords = listOf("start", "nine", "finish", "time"),
                    clarityTipEs = "«Start» también arranca directa: «start», sin «e» antes.",
                    trapWords = listOf("start")
                ),
                triggerKeywords = listOf("time", "hours", "start", "finish", "schedule", "nine")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_lunch",
                    partnerLine = "We have lunch at one, in the small kitchen.",
                    objectiveEs = "Pregunta dónde está la cocina o confirma la hora de comer.",
                    modelReplies = listOf(
                        "Perfect. Where is the kitchen?",
                        "Thank you. I bring my lunch."
                    ),
                    targetKeywords = listOf("lunch", "kitchen", "bring", "where"),
                    clarityTipEs = "«Where is the kitchen?» — la pregunta lleva el verbo antes del lugar."
                ),
                triggerKeywords = listOf("lunch", "eat", "break", "kitchen")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_computer",
                    partnerLine = "Your computer is on that desk. The password is on the note.",
                    objectiveEs = "Pregunta dónde entrar o agradece la información.",
                    modelReplies = listOf(
                        "Thank you. I see the computer.",
                        "On that desk. Thank you very much."
                    ),
                    targetKeywords = listOf("computer", "desk", "thank"),
                    clarityTipEs = "«Computer» lleva la fuerza en el medio: «com-piú-ter»."
                ),
                triggerKeywords = listOf("computer", "desk", "password", "login")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_meeting",
                    partnerLine = "We have a team meeting on Mondays at ten.",
                    objectiveEs = "Confirma que estarás en la reunión de los lunes.",
                    modelReplies = listOf(
                        "Great. I will be at the meeting.",
                        "On Mondays at ten. I understand."
                    ),
                    targetKeywords = listOf("meeting", "mondays", "ten", "understand"),
                    clarityTipEs = "Los días de la semana llevan «on»: «on Mondays»."
                ),
                triggerKeywords = listOf("meeting", "monday", "team")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_questions",
                    partnerLine = "If you have questions, ask Maria. She is very kind.",
                    objectiveEs = "Di que preguntarás a María, o dónde encontrarla.",
                    modelReplies = listOf(
                        "I will ask Maria. Thank you.",
                        "Where can I find Maria?"
                    ),
                    targetKeywords = listOf("ask", "maria", "find", "thank"),
                    clarityTipEs = "«I will ask Maria»: el futuro simple es «will» + verbo."
                ),
                triggerKeywords = listOf("question", "help", "ask", "maria")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_tasks",
                    partnerLine = "Today you start with the emails, then the calls.",
                    objectiveEs = "Confirma el orden de tus tareas de hoy.",
                    modelReplies = listOf(
                        "I understand. Emails first, then calls.",
                        "Can you show me the emails?"
                    ),
                    targetKeywords = listOf("emails", "calls", "first", "show"),
                    clarityTipEs = "«First… then…» ordena cualquier lista de tareas."
                ),
                triggerKeywords = listOf("task", "work", "do", "emails", "calls")
            )
        ),
        generics = listOf(
            LivePrompt(
                id = "g_from",
                partnerLine = "Where are you from?",
                objectiveEs = "Di de dónde eres y dónde vives ahora.",
                modelReplies = listOf(
                    "I am from Mexico.",
                    "I am from Spain, but I live here now."
                ),
                targetKeywords = listOf("from", "live", "here"),
                clarityTipEs = "«I am from…» para el origen; «I live…» para donde vives hoy."
            ),
            LivePrompt(
                id = "g_experience",
                partnerLine = "Is this your first job in an office?",
                objectiveEs = "Responde: ¿es tu primer trabajo de oficina?",
                modelReplies = listOf(
                    "No, I worked in an office before.",
                    "Yes, it is my first office job."
                ),
                targetKeywords = listOf("first", "job", "office", "worked"),
                clarityTipEs = "«Worked» es el pasado de «work»: una sílaba seca al final."
            ),
            LivePrompt(
                id = "g_team",
                partnerLine = "The team is small and friendly.",
                objectiveEs = "Di que estás contento de estar aquí.",
                modelReplies = listOf(
                    "I am happy to be here.",
                    "Everyone is very kind."
                ),
                targetKeywords = listOf("happy", "here", "kind"),
                clarityTipEs = "«I am happy to be here» es la frase de primer día perfecta."
            ),
            LivePrompt(
                id = "g_tomorrow",
                partnerLine = "Tomorrow we start at the same time.",
                objectiveEs = "Confirma la hora de mañana.",
                modelReplies = listOf(
                    "Perfect. See you tomorrow.",
                    "At nine tomorrow. I understand."
                ),
                targetKeywords = listOf("tomorrow", "nine", "understand"),
                clarityTipEs = "«See you tomorrow» cierra el día sin esfuerzo."
            )
        ),
        wrapUpLine = "That is all for today. Welcome to the team — see you tomorrow!"
    )

    // ---------------------------------------------------------- direcciones

    private val direcciones = LiveBank(
        conversationId = "direcciones",
        opening = LivePrompt(
            id = "opening",
            partnerLine = "Hello! Can I help you?",
            objectiveEs = "Pide ayuda: preguntas dónde está el museo.",
            modelReplies = listOf(
                "Yes, please. Where is the museum?",
                "Excuse me. Where is the museum?"
            ),
            targetKeywords = listOf("where", "museum", "please", "excuse"),
            clarityTipEs = "«Excuse me» es la llave cortés para pedir ayuda en la calle."
        ),
        branches = listOf(
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_library",
                    partnerLine = "The museum is next to the library, on Green Street.",
                    objectiveEs = "Confirma el sitio: junto a la biblioteca, en Green Street.",
                    modelReplies = listOf(
                        "Next to the library. Thank you.",
                        "On Green Street, next to the library."
                    ),
                    targetKeywords = listOf("next", "library", "street", "thank"),
                    clarityTipEs = "«Street» arranca con /str/ directa: «strit», sin «e» antes.",
                    trapWords = listOf("street")
                ),
                triggerKeywords = listOf("library", "museum", "street", "next")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_far",
                    partnerLine = "It is not far. Ten minutes on foot, or one stop by bus.",
                    objectiveEs = "Decide: vas andando o en autobús.",
                    modelReplies = listOf(
                        "I will walk, thank you.",
                        "One stop by bus. Thank you."
                    ),
                    targetKeywords = listOf("walk", "bus", "thank", "stop"),
                    clarityTipEs = "«Stop» directa: la parada es «stop», no «estop».",
                    trapWords = listOf("stop")
                ),
                triggerKeywords = listOf("far", "walk", "bus", "long")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_turn",
                    partnerLine = "Go straight and turn left at the bank.",
                    objectiveEs = "Repite la ruta para confirmarla: recto y a la izquierda en el banco.",
                    modelReplies = listOf(
                        "Straight, then left at the bank.",
                        "Turn left at the bank. I understand."
                    ),
                    targetKeywords = listOf("straight", "left", "bank", "turn"),
                    clarityTipEs = "«Straight» entra directa, sin «e»: no es «estraight».",
                    trapNotesEs = listOf("La 'e' fantasma antes de la /s/ inicial: «straight», no «estraight»."),
                    trapWords = listOf("straight")
                ),
                triggerKeywords = listOf("turn", "left", "right", "straight", "corner")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_station",
                    partnerLine = "The station is behind the park, near the river.",
                    objectiveEs = "Confirma dónde está la estación.",
                    modelReplies = listOf(
                        "Behind the park. Thank you very much.",
                        "Is the station near here?"
                    ),
                    targetKeywords = listOf("station", "behind", "park", "near"),
                    clarityTipEs = "«Station» directa: «stéi-shon», sin «e» antes.",
                    trapWords = listOf("station")
                ),
                triggerKeywords = listOf("station", "train", "park")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_open",
                    partnerLine = "The museum opens at ten and closes at six.",
                    objectiveEs = "Confirma el horario del museo.",
                    modelReplies = listOf(
                        "It opens at ten and closes at six.",
                        "Thank you. I will go now."
                    ),
                    targetKeywords = listOf("opens", "ten", "closes", "six"),
                    clarityTipEs = "Los horarios llevan tercera persona: «it opens», «it closes»."
                ),
                triggerKeywords = listOf("open", "close", "time", "hours")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_map",
                    partnerLine = "I can show you on my phone. Look, it is this street.",
                    objectiveEs = "Confirma que ya ves la calle en el mapa.",
                    modelReplies = listOf(
                        "I see it now. Thank you!",
                        "This street, two blocks away."
                    ),
                    targetKeywords = listOf("see", "street", "blocks", "thank"),
                    clarityTipEs = "«I see it now» = ya lo veo: la frase cuando el mapa por fin habla.",
                    trapWords = listOf("street")
                ),
                triggerKeywords = listOf("map", "phone", "show", "lost")
            )
        ),
        generics = listOf(
            LivePrompt(
                id = "g_visit",
                partnerLine = "Are you visiting our city?",
                objectiveEs = "Responde: estás de visita unos días.",
                modelReplies = listOf(
                    "Yes, I am here for two days.",
                    "Yes, it is my first visit."
                ),
                targetKeywords = listOf("visiting", "days", "first", "visit"),
                clarityTipEs = "«My first visit» deja claro que es tu primera vez aquí."
            ),
            LivePrompt(
                id = "g_cafe",
                partnerLine = "There is a nice café on that corner, if you are thirsty.",
                objectiveEs = "Agradece el consejo del café de la esquina.",
                modelReplies = listOf(
                    "Thank you. A coffee sounds good.",
                    "Maybe later. Thank you!"
                ),
                targetKeywords = listOf("coffee", "sounds", "thank", "later"),
                clarityTipEs = "«Sounds good» = suena bien: para ideas y planes.",
                trapWords = listOf("sounds")
            ),
            LivePrompt(
                id = "g_weather",
                partnerLine = "It is a good day for a walk in the city.",
                objectiveEs = "Comenta el buen día para pasear.",
                modelReplies = listOf(
                    "Yes, the weather is beautiful.",
                    "I love walking in this city."
                ),
                targetKeywords = listOf("weather", "beautiful", "walking", "love"),
                clarityTipEs = "El tiempo atmosférico es «weather»; la hora es «time». No se mezclan."
            ),
            LivePrompt(
                id = "g_kind",
                partnerLine = "Do not worry, the city is small and friendly.",
                objectiveEs = "Agradece su ayuda y su amabilidad.",
                modelReplies = listOf(
                    "Thank you for your help.",
                    "You are very kind. Thank you!"
                ),
                targetKeywords = listOf("thank", "help", "kind"),
                clarityTipEs = "«Thank you for your help»: el «for» presenta el motivo."
            )
        ),
        wrapUpLine = "You are welcome! Enjoy the museum and our city."
    )

    // ---------------------------------------------------------- médico

    private val medico = LiveBank(
        conversationId = "medico",
        opening = LivePrompt(
            id = "opening",
            partnerLine = "Good morning. How can I help you?",
            objectiveEs = "Di que tienes una cita a las diez.",
            modelReplies = listOf(
                "Good morning. I have an appointment at ten.",
                "I have an appointment with the doctor."
            ),
            targetKeywords = listOf("appointment", "ten", "doctor", "morning"),
            clarityTipEs = "«Appointment» es la cita: «I have an appointment»."
        ),
        branches = listOf(
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_pain",
                    partnerLine = "Where does it hurt?",
                    objectiveEs = "Di dónde te duele: el brazo o la espalda.",
                    modelReplies = listOf(
                        "My arm hurts, here.",
                        "It hurts in my back."
                    ),
                    targetKeywords = listOf("hurts", "arm", "back", "here"),
                    clarityTipEs = "El dolor lleva «hurts» con s: «my arm hurts»."
                ),
                triggerKeywords = listOf("pain", "hurt", "hurts", "arm", "leg", "back", "head")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_when",
                    partnerLine = "When did it start?",
                    objectiveEs = "Di cuándo empezó: ayer, tras la caída.",
                    modelReplies = listOf(
                        "It started yesterday, after the fall.",
                        "Two days ago, in the morning."
                    ),
                    targetKeywords = listOf("started", "yesterday", "days", "morning"),
                    clarityTipEs = "«Started» entra directa: «stár-ted», sin «e» antes.",
                    trapWords = listOf("started")
                ),
                triggerKeywords = listOf("when", "start", "yesterday", "ago")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_fever",
                    partnerLine = "Do you have a fever or a cough?",
                    objectiveEs = "Di qué tienes: un poco de fiebre o solo tos.",
                    modelReplies = listOf(
                        "A little fever, and my throat hurts.",
                        "No fever, only a cough."
                    ),
                    targetKeywords = listOf("fever", "cough", "throat", "hurts"),
                    clarityTipEs = "«Throat» lleva la «th»: lengua entre los dientes y aire."
                ),
                triggerKeywords = listOf("fever", "cough", "cold", "throat")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_medicine",
                    partnerLine = "Take this medicine twice a day, with food.",
                    objectiveEs = "Confirma la dosis o pregunta cuántos días.",
                    modelReplies = listOf(
                        "Twice a day, with food. I understand.",
                        "How many days, doctor?"
                    ),
                    targetKeywords = listOf("twice", "day", "food", "understand"),
                    clarityTipEs = "«Twice a day» = dos veces al día; «once» es una vez."
                ),
                triggerKeywords = listOf("medicine", "take", "pills", "days")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_rest",
                    partnerLine = "You need rest and water for three days.",
                    objectiveEs = "Confirma el reposo o pregunta si puedes trabajar mañana.",
                    modelReplies = listOf(
                        "Three days of rest. I understand.",
                        "Can I work tomorrow?"
                    ),
                    targetKeywords = listOf("rest", "days", "work", "tomorrow"),
                    clarityTipEs = "«Rest» es el reposo; el verbo es «to rest»."
                ),
                triggerKeywords = listOf("rest", "work", "sleep")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_insurance",
                    partnerLine = "Do you have your insurance card with you?",
                    objectiveEs = "Di que traes tu tarjeta del seguro.",
                    modelReplies = listOf(
                        "Yes, here is my card.",
                        "Yes, I have it in my bag."
                    ),
                    targetKeywords = listOf("insurance", "card", "here", "bag"),
                    clarityTipEs = "«Here is my card» al entregarla: la misma fórmula de «here you are»."
                ),
                triggerKeywords = listOf("insurance", "card", "pay", "cost")
            )
        ),
        generics = listOf(
            LivePrompt(
                id = "g_name",
                partnerLine = "Can I have your name and date of birth?",
                objectiveEs = "Di tu nombre y cuándo naciste.",
                modelReplies = listOf(
                    "My name is Alex. I was born in May.",
                    "Alex Garcia, born in nineteen ninety."
                ),
                targetKeywords = listOf("name", "born", "may"),
                clarityTipEs = "Nacer es «was born»: «I was born in May»."
            ),
            LivePrompt(
                id = "g_first",
                partnerLine = "Is this your first visit to this clinic?",
                objectiveEs = "Responde: ¿es tu primera visita a esta clínica?",
                modelReplies = listOf(
                    "Yes, it is my first time here.",
                    "No, I came last year."
                ),
                targetKeywords = listOf("first", "time", "came", "year"),
                clarityTipEs = "«Came» es el pasado de «come»: «I came last year»."
            ),
            LivePrompt(
                id = "g_wait",
                partnerLine = "The doctor is with a patient. Please take a seat.",
                objectiveEs = "Acepta esperar sentado.",
                modelReplies = listOf(
                    "Thank you. I will wait here.",
                    "I will take a seat. Thank you."
                ),
                targetKeywords = listOf("wait", "seat", "thank"),
                clarityTipEs = "«Seat» entra directa: «sit», sin «e» antes.",
                trapWords = listOf("seat")
            ),
            LivePrompt(
                id = "g_water",
                partnerLine = "Would you like a glass of water while you wait?",
                objectiveEs = "Acepta o rechaza el vaso de agua, con cortesía.",
                modelReplies = listOf(
                    "Yes, please. Water is good.",
                    "No, thank you very much."
                ),
                targetKeywords = listOf("water", "please", "thank"),
                clarityTipEs = "El rechazo cortés también lleva «thank you»: nunca un «no» seco."
            )
        ),
        wrapUpLine = "Take care, rest well, and come back if it hurts more. Get well soon!"
    )

    // ---------------------------------------------------------- aeropuerto

    private val aeropuerto = LiveBank(
        conversationId = "aeropuerto",
        opening = LivePrompt(
            id = "opening",
            partnerLine = "Good afternoon. Where are you traveling to?",
            objectiveEs = "Di tu destino y muestra el pasaporte.",
            modelReplies = listOf(
                "To Tampa, please. Here is my passport.",
                "I am traveling to Tampa."
            ),
            targetKeywords = listOf("traveling", "tampa", "passport", "here"),
            clarityTipEs = "Al dar un papel se dice «Here is…»: «Here is my passport»."
        ),
        branches = listOf(
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_bags",
                    partnerLine = "Do you have any bags to check?",
                    objectiveEs = "Di qué equipaje facturas: solo una maleta.",
                    modelReplies = listOf(
                        "Yes, just this one suitcase.",
                        "No, only my backpack."
                    ),
                    targetKeywords = listOf("suitcase", "backpack", "one"),
                    clarityTipEs = "«Suitcase» entra directa: «sút-keis», sin «e» antes.",
                    trapWords = listOf("suitcase")
                ),
                triggerKeywords = listOf("bag", "bags", "suitcase", "check", "luggage")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_seat",
                    partnerLine = "Window or aisle seat?",
                    objectiveEs = "Elige asiento: ventana o pasillo.",
                    modelReplies = listOf(
                        "A window seat, please.",
                        "The aisle, please. Thank you."
                    ),
                    targetKeywords = listOf("window", "aisle", "seat", "please"),
                    clarityTipEs = "«Aisle» se dice «áil»: la a inicial suena sola.",
                    trapWords = listOf("seat")
                ),
                triggerKeywords = listOf("seat", "window", "aisle", "sit")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_gate",
                    partnerLine = "Your gate is B7, on the left.",
                    objectiveEs = "Confirma tu puerta: la B7, a la izquierda.",
                    modelReplies = listOf(
                        "Gate B7, on the left. Thank you.",
                        "Where is gate B7?"
                    ),
                    targetKeywords = listOf("gate", "left", "thank", "where"),
                    clarityTipEs = "Repetir el dato («Gate B7») es la forma natural de confirmarlo."
                ),
                triggerKeywords = listOf("gate", "where", "boarding")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_time",
                    partnerLine = "Boarding starts at six. You have two hours.",
                    objectiveEs = "Valora tu tiempo: da para un café.",
                    modelReplies = listOf(
                        "Boarding starts at six. I have time.",
                        "Two hours, time for a coffee."
                    ),
                    targetKeywords = listOf("starts", "hours", "time", "coffee"),
                    clarityTipEs = "«Starts» entra directa: «starts», sin «e» antes.",
                    trapWords = listOf("starts")
                ),
                triggerKeywords = listOf("time", "boarding", "starts", "late", "hour")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_id",
                    partnerLine = "Can I see your passport and ticket, please?",
                    objectiveEs = "Entrega pasaporte y billete.",
                    modelReplies = listOf(
                        "Of course. Here they are.",
                        "Here is my passport and my ticket."
                    ),
                    targetKeywords = listOf("passport", "ticket", "here", "course"),
                    clarityTipEs = "Para dos cosas a la vez: «Here they are»."
                ),
                triggerKeywords = listOf("passport", "ticket", "id")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_flight",
                    partnerLine = "Your flight is on time today.",
                    objectiveEs = "Celebra que el vuelo va puntual.",
                    modelReplies = listOf(
                        "That is great news. My flight is on time.",
                        "On time. Perfect."
                    ),
                    targetKeywords = listOf("flight", "time", "news", "perfect"),
                    clarityTipEs = "«On time» es puntual; «in time» es llegar con margen. Este es «on time»."
                ),
                triggerKeywords = listOf("flight", "delay", "delayed", "time")
            )
        ),
        generics = listOf(
            LivePrompt(
                id = "g_first",
                partnerLine = "Is this your first flight?",
                objectiveEs = "Responde: ¿es tu primer vuelo?",
                modelReplies = listOf(
                    "No, I fly every year.",
                    "Yes, it is my first flight."
                ),
                targetKeywords = listOf("first", "flight", "fly", "year"),
                clarityTipEs = "El avión es «flight»; el verbo, «fly»."
            ),
            LivePrompt(
                id = "g_visit",
                partnerLine = "Are you traveling for work or for vacation?",
                objectiveEs = "Di el motivo de tu viaje.",
                modelReplies = listOf(
                    "For vacation, with my family.",
                    "For work. I return on Friday."
                ),
                targetKeywords = listOf("vacation", "family", "work", "friday"),
                clarityTipEs = "El motivo lleva «for»: «for work», «for vacation»."
            ),
            LivePrompt(
                id = "g_food",
                partnerLine = "There are cafés after security, if you are hungry.",
                objectiveEs = "Agradece el dato de los cafés tras el control.",
                modelReplies = listOf(
                    "Thank you. I will have a coffee.",
                    "Good to know. Thank you!"
                ),
                targetKeywords = listOf("coffee", "thank", "know"),
                clarityTipEs = "«Good to know» = bueno saberlo: frase de viajero experto."
            ),
            LivePrompt(
                id = "g_help",
                partnerLine = "If you need help, our desk is near gate B.",
                objectiveEs = "Agradece saber dónde pedir ayuda.",
                modelReplies = listOf(
                    "Thank you for your help.",
                    "That is very kind. Thank you."
                ),
                targetKeywords = listOf("help", "thank", "kind"),
                clarityTipEs = "«If you need help…» es la frase que escucharás en cada aeropuerto."
            )
        ),
        wrapUpLine = "Everything is ready. Have a safe flight and a great trip!"
    )

    // ---------------------------------------------------------- restaurante

    private val restaurante = LiveBank(
        conversationId = "restaurante",
        opening = LivePrompt(
            id = "opening",
            partnerLine = "Good evening. Are you ready to order?",
            objectiveEs = "Pide el pollo a la parrilla con la forma cortés.",
            modelReplies = listOf(
                "Yes, please. I would like the grilled chicken.",
                "I would like the grilled chicken, please."
            ),
            targetKeywords = listOf("would", "like", "grilled", "chicken"),
            clarityTipEs = "«I would like» otra vez: la fórmula cortés también pide la cena."
        ),
        branches = listOf(
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_drink",
                    partnerLine = "And to drink?",
                    objectiveEs = "Pide tu bebida: un vaso de agua o vino.",
                    modelReplies = listOf(
                        "A glass of water, please.",
                        "Red wine for me, please."
                    ),
                    targetKeywords = listOf("glass", "water", "wine", "please"),
                    clarityTipEs = "«A glass of water»: el vaso se pide entero, con su «of»."
                ),
                triggerKeywords = listOf("drink", "water", "wine", "thirsty")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_starter",
                    partnerLine = "Would you like a starter? The soup is fresh today.",
                    objectiveEs = "Pide un entrante, o di que no, gracias.",
                    modelReplies = listOf(
                        "The soup, please.",
                        "No starter, thank you."
                    ),
                    targetKeywords = listOf("soup", "starter", "please", "thank"),
                    clarityTipEs = "«Starter» y «soup» entran directas: sin «e» antes de la /s/.",
                    trapNotesEs = listOf("La 'e' fantasma antes de la /s/ inicial: «starter», no «estarter»."),
                    trapWords = listOf("soup", "starter")
                ),
                triggerKeywords = listOf("starter", "soup", "salad", "begin")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_side",
                    partnerLine = "The chicken comes with rice or potatoes.",
                    objectiveEs = "Elige el acompañamiento: arroz o patatas.",
                    modelReplies = listOf(
                        "With rice, please.",
                        "Potatoes for me, thank you."
                    ),
                    targetKeywords = listOf("rice", "potatoes", "please"),
                    clarityTipEs = "«With rice» — el acompañamiento va con «with»."
                ),
                triggerKeywords = listOf("rice", "potatoes", "side", "with")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_dessert",
                    partnerLine = "Would you like dessert? We have ice cream and fruit.",
                    objectiveEs = "Pide el postre: helado o fruta.",
                    modelReplies = listOf(
                        "Yes, ice cream for dessert, please.",
                        "Just fruit, thank you."
                    ),
                    targetKeywords = listOf("dessert", "ice", "cream", "fruit"),
                    clarityTipEs = "«Ice cream» son dos palabras que suenan a una: «áis-crim»."
                ),
                triggerKeywords = listOf("dessert", "sweet", "ice", "cake")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_check",
                    partnerLine = "Can I bring you anything else?",
                    objectiveEs = "Pide la cuenta para cerrar la cena.",
                    modelReplies = listOf(
                        "The check, please.",
                        "That is all, thank you."
                    ),
                    targetKeywords = listOf("check", "all", "thank"),
                    clarityTipEs = "La cuenta en EE. UU. es «the check»; en Reino Unido, «the bill»."
                ),
                triggerKeywords = listOf("check", "bill", "pay", "else")
            ),
            LiveBranch(
                prompt = LivePrompt(
                    id = "b_taste",
                    partnerLine = "How is everything? Is the chicken good?",
                    objectiveEs = "Di qué tal está la comida.",
                    modelReplies = listOf(
                        "Everything is delicious, thank you.",
                        "The chicken is perfect."
                    ),
                    targetKeywords = listOf("delicious", "perfect", "chicken", "thank"),
                    clarityTipEs = "«Delicious» es el cumplido fuerte: úsalo cuando de verdad lo sea."
                ),
                triggerKeywords = listOf("good", "taste", "delicious", "like")
            )
        ),
        generics = listOf(
            LivePrompt(
                id = "g_first",
                partnerLine = "Is this your first time in our restaurant?",
                objectiveEs = "Responde: ¿es tu primera vez en este restaurante?",
                modelReplies = listOf(
                    "Yes, it is my first time here.",
                    "No, I come here with my family."
                ),
                targetKeywords = listOf("first", "time", "family", "come"),
                clarityTipEs = "La misma respuesta sirve en casi cualquier sitio nuevo."
            ),
            LivePrompt(
                id = "g_table",
                partnerLine = "Your table is by the window, with a nice view.",
                objectiveEs = "Valora la mesa junto a la ventana.",
                modelReplies = listOf(
                    "What a beautiful view. Thank you.",
                    "The window table is perfect."
                ),
                targetKeywords = listOf("window", "view", "beautiful", "perfect"),
                clarityTipEs = "«What a beautiful view!» — el «what a…» hace el cumplido."
            ),
            LivePrompt(
                id = "g_wait",
                partnerLine = "Your food will be ready in fifteen minutes.",
                objectiveEs = "Acepta la espera sin problema.",
                modelReplies = listOf(
                    "No problem. Fifteen minutes is fine.",
                    "Perfect, we will wait until it is ready."
                ),
                targetKeywords = listOf("ready", "minutes", "wait", "fine"),
                clarityTipEs = "«We will wait»: esperar a alguien o algo es «wait», sin «for» aquí."
            ),
            LivePrompt(
                id = "g_special",
                partnerLine = "The fish is the special of the house today.",
                objectiveEs = "Muestra interés por el pescado, la especialidad de la casa.",
                modelReplies = listOf(
                    "Then I will try the fish.",
                    "The special sounds good. Thank you."
                ),
                targetKeywords = listOf("fish", "special", "try", "sounds"),
                clarityTipEs = "«Special» entra directa: «spé-shal», sin «e» antes.",
                trapWords = listOf("special", "sounds")
            )
        ),
        wrapUpLine = "Thank you for dining with us. Have a wonderful evening!"
    )

}
