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

        val traps = mutableListOf<String>()

        // 1. Check for 'E' fantasma (Spanish speaker initial vowel insertion)
        val phantomWords = listOf("es-", "is-", "especial", "estress", "eschedule", "esperience", "espicy", "esmart", "eschool")
        for (token in cleanSpoken.split(Regex("[^a-zA-Záéíóúñ]+"))) {
            if (phantomWords.any { token.startsWith(it) }) {
                traps.add("Trampa de la 'E' fantasma detectada en '$token': arranca con silbido 'sss' directo.")
            }
        }
        // Also check if any target keyword starts with s+consonant but spoken has e+s+consonant
        target.targetKeywords.forEach { kw ->
            val k = kw.lowercase(Locale.US)
            if (k.startsWith("s") && !k.startsWith("sh")) {
                if (cleanSpoken.contains("es$k") || cleanSpoken.contains("es $k") || cleanSpoken.contains("es-$k")) {
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
        val spokenTokens = cleanSpoken.split(Regex("[^a-zA-Záéíóúñ]+")).filter { it.isNotEmpty() }
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
                    partnerLine = "Of course. Would you like a table by the window?",
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
                    targetKeywords = listOf("breakfast", "eight", "seven", "please"),
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
                    objectiveEs = "Pregunta por la contraseña del wifi o pide algo más.",
                    modelReplies = listOf(
                        "What is the wifi password, please?",
                        "No, that is all. Thank you very much."
                    ),
                    targetKeywords = listOf("wifi", "password", "please", "thank"),
                    clarityTipEs = "«What is…?» abre la pregunta: palabra Wh + verbo + tema."
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
}
