package com.aula.nativeapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * Speech machinery for Conversación en vivo: the word-alignment
 * evaluator, the recognizer-boundary honesty rules, the per-word
 * feedback analyzer, the pure turn grader, and the SpeechRecognizer
 * probe that feeds them.
 *
 * Aula native had NO speech recognition before this file (verified:
 * no SpeechRecognizer anywhere in the project, and the manifest
 * declared no RECORD_AUDIO — the PARITY-COPA plan lists Copia
 * recording/auto-check as planned, not built). [LiveSpeechProbe] is
 * therefore new, written in the exact shape Cognata's SpeechManager
 * established for its conversation path:
 *  - EXTRA_MAX_RESULTS 3, en-US, free-form;
 *  - onResults publishes the raw top hypothesis AND a
 *    [RecognitionEvidence] (all hypotheses + confidences when the
 *    service reports them) in the same synchronous callback, so a
 *    consumer reacting to the top value already sees THIS result's
 *    alternatives — never the previous turn's;
 *  - onError clears the evidence and surfaces ONLY a non-scored
 *    micError message. An error turn leaves nothing behind that any
 *    consumer could mistake for a graded attempt: a recognizer error
 *    logs NOTHING in the Mirror diary, ever.
 */

// ------------------------------------------------------------------ recognizer evidence

/**
 * The recognizer's full evidence for the latest result: every
 * hypothesis it returned (top first) plus the per-hypothesis
 * confidence scores when the service reported them (the literal
 * "results_confidence_scores" bundle key — the SDK exposes no
 * compile-time constant for it).
 */
data class RecognitionEvidence(
    val hypotheses: List<String>,
    val confidences: List<Float>? = null
)

// ------------------------------------------------------------------ word accuracy

/** One target word's alignment verdict inside [WordAccuracy.evaluateSentence]. */
data class WordAccuracyToken(
    val index: Int,
    val displayWord: String,
    val cleanWord: String,
    val spokenMatch: String?,
    val accuracyScore: Int
)

/** Per-word alignment of a spoken sentence against a target sentence. */
data class SentenceAccuracyAnalysis(
    val targetSentence: String,
    val spokenSentence: String,
    val overallScore: Int,
    val wordTokens: List<WordAccuracyToken>
)

/**
 * Word-level alignment — a port of Cognata's SentenceAccuracyEvaluator
 * math (window alignment ±3/+4 with a global fallback at >= 45,
 * a >= 30 match threshold, the same per-word scoring: exact 98,
 * phantom-E trap 52, ship/sheep minimal pair 55, th-substitution 68,
 * Levenshtein ratio with the same distance-1 floors).
 *
 * Documented adaptation: Cognata's copy carries a Spanish-respelling
 * dictionary for ITS curriculum words and GREEN/YELLOW/RED categories
 * per token. Aula's respellings live in its own data (Cognate.ph, the
 * Guide steps) and the live analyzer only needs the alignment and the
 * scores, so the dictionary and the category field are not ported;
 * every number the alignment produces is unchanged.
 */
object WordAccuracy {

    fun evaluateSentence(
        targetSentence: String,
        spokenSentence: String
    ): SentenceAccuracyAnalysis {
        val effectiveTarget = targetSentence.trim().ifBlank { spokenSentence.trim() }
        if (effectiveTarget.isEmpty()) {
            return SentenceAccuracyAnalysis(
                targetSentence = "",
                spokenSentence = spokenSentence,
                overallScore = 0,
                wordTokens = emptyList()
            )
        }

        val rawTargetTokens = effectiveTarget.split(Regex("\\s+")).filter { it.isNotBlank() }
        val rawSpokenTokens = spokenSentence.trim()
            .split(Regex("\\s+"))
            .map { normalizeToken(it) }
            .filter { it.isNotBlank() }

        val usedSpokenIndices = BooleanArray(rawSpokenTokens.size)
        val evaluatedTokens = mutableListOf<WordAccuracyToken>()

        rawTargetTokens.forEachIndexed { idx, displayToken ->
            val cleanTarget = normalizeToken(displayToken)
            if (cleanTarget.isEmpty()) return@forEachIndexed

            // Search for the best matching word in a window around the expected position
            var bestSpokenIdx = -1
            var bestScore = -1
            var bestSpokenWord: String? = null

            val searchStart = (idx - 3).coerceAtLeast(0)
            val searchEnd = (idx + 4).coerceAtMost(rawSpokenTokens.size - 1)

            if (rawSpokenTokens.isNotEmpty() && searchStart <= searchEnd) {
                for (sIdx in searchStart..searchEnd) {
                    if (usedSpokenIndices[sIdx]) continue
                    val candidate = rawSpokenTokens[sIdx]
                    val sim = computeWordAccuracy(cleanTarget, candidate)
                    if (sim > bestScore) {
                        bestScore = sim
                        bestSpokenIdx = sIdx
                        bestSpokenWord = candidate
                    }
                }
            }

            // If no local window match had >= 45%, check any remaining unused spoken token
            if (bestScore < 45 && rawSpokenTokens.isNotEmpty()) {
                for (sIdx in rawSpokenTokens.indices) {
                    if (usedSpokenIndices[sIdx]) continue
                    val candidate = rawSpokenTokens[sIdx]
                    val sim = computeWordAccuracy(cleanTarget, candidate)
                    if (sim > bestScore) {
                        bestScore = sim
                        bestSpokenIdx = sIdx
                        bestSpokenWord = candidate
                    }
                }
            }

            val finalScore: Int
            val matchedWord: String?
            if (bestSpokenIdx >= 0 && bestScore >= 30) {
                usedSpokenIndices[bestSpokenIdx] = true
                finalScore = bestScore
                matchedWord = bestSpokenWord
            } else {
                finalScore = if (rawSpokenTokens.isEmpty()) 0 else 25
                matchedWord = null
            }

            evaluatedTokens.add(
                WordAccuracyToken(
                    index = idx,
                    displayWord = displayToken,
                    cleanWord = cleanTarget,
                    spokenMatch = matchedWord,
                    accuracyScore = finalScore
                )
            )
        }

        val overallScore = if (evaluatedTokens.isEmpty()) {
            0
        } else {
            evaluatedTokens.map { it.accuracyScore }.average().roundToInt().coerceIn(0, 100)
        }

        return SentenceAccuracyAnalysis(
            targetSentence = effectiveTarget,
            spokenSentence = spokenSentence,
            overallScore = overallScore,
            wordTokens = evaluatedTokens
        )
    }

    internal fun normalizeToken(raw: String): String {
        return raw.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9']"), "")
            .trim()
    }

    internal fun computeWordAccuracy(expected: String, spoken: String): Int {
        if (expected.isEmpty() || spoken.isEmpty()) return 0
        if (expected == spoken) return 98

        // Check epenthetic 'e' trap (e.g., "espeak" for "speak", "especial" for "special")
        if (expected.startsWith("s") && !expected.startsWith("sh") &&
            (spoken == "e$expected" || spoken == "es${expected.drop(1)}")
        ) {
            return 52
        }

        // Check short/long vowel minimal pair trap (ship vs sheep — Aula's own Guide pair)
        if ((expected == "ship" && spoken == "sheep") || (expected == "sheep" && spoken == "ship")) {
            return 55
        }

        // Check th -> d/t/s substitution (think -> sink/tink, this -> dis)
        if (expected.startsWith("th") && (spoken.startsWith("d") || spoken.startsWith("t") || spoken.startsWith("s"))) {
            val restExpected = expected.removePrefix("th")
            val restSpoken = spoken.drop(1)
            if (restExpected == restSpoken) {
                return 68
            }
        }

        val distance = levenshtein(expected, spoken)
        val maxLen = max(expected.length, spoken.length)
        val rawRatio = 1.0 - (distance.toDouble() / maxLen.toDouble())
        val baseScore = (rawRatio * 100.0).roundToInt().coerceIn(10, 96)

        return when {
            distance == 1 && expected.length >= 5 -> max(82, baseScore)
            distance == 1 && expected.length >= 3 -> max(74, baseScore)
            else -> baseScore
        }
    }

    private fun levenshtein(s1: String, s2: String): Int {
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

// ------------------------------------------------------------------ pronunciation boundary

/**
 * Recognizer-boundary honesty rules for single-word scoring — ported
 * verbatim from Cognata's PronunciationBoundary.
 *
 * The defect this guards: the recognizer's top hypothesis is
 * ASR-cleaned — an accented "esister" can come back as "sister" and a
 * half-spoken word can come back whole. The evidence of what the
 * learner actually said survives in the alternatives and confidence
 * scores. This pure layer can only ever LOWER a certification, never
 * raise one: a top hypothesis that is not exactly the clean target is
 * scored exactly as before, with the alternatives ignored. When the
 * top hypothesis IS the clean target, three contradiction checks run,
 * in precedence order:
 *
 *  a. TRAP ALTERNATIVE — an alternative is the target's phantom-E trap
 *     form (leading "e"/"i", target starts with "s" but not "sh");
 *     THAT alternative is scored instead. The prepend must be exact.
 *  b. FRAGMENT ALTERNATIVE — an alternative is a proper fragment of
 *     the target (length >= 3); the score is capped at 84.
 *  c. LOW CONFIDENCE — the top hypothesis scores below 0.5 when the
 *     service reports scores at all (absent scores are neutral; an
 *     all-zero array counts as absent). Same cap.
 */
object PronunciationBoundary {

    /** Cap applied when the recognizer's own evidence contradicts a clean hit. */
    const val UNCERTAIN_GREEN_CAP = 84

    /** Top-hit confidence below this cannot certify a clean hit. */
    const val LOW_CONFIDENCE_THRESHOLD = 0.5f

    /** Shortest alternative that counts as the recognizer hearing a fragment. */
    private const val MIN_FRAGMENT_LENGTH = 3

    data class Decision(
        val spokenToScore: String,
        val scoreCap: Int?
    )

    fun decide(
        hypotheses: List<String>,
        confidences: List<Float>?,
        expected: String
    ): Decision {
        val top = hypotheses.firstOrNull() ?: return Decision("", null)
        val cleanExpected = expected.trim().lowercase(Locale.US)
        val cleanTop = top.trim().lowercase(Locale.US)
        // The rules only police a clean-target certification. Anything
        // else scores exactly as the inner scorer scores it, and the
        // alternatives stay inert (they never raise a score).
        if (cleanExpected.isEmpty() || cleanTop != cleanExpected) {
            return Decision(top, null)
        }

        // (a) Trap alternative.
        if (cleanExpected.startsWith("s") && !cleanExpected.startsWith("sh")) {
            for (alternative in hypotheses.drop(1)) {
                val cleanAlt = alternative.trim().lowercase(Locale.US)
                if (cleanAlt == "e" + cleanExpected || cleanAlt == "i" + cleanExpected) {
                    return Decision(alternative, null)
                }
            }
        }

        // (b) Fragment alternative.
        for (alternative in hypotheses.drop(1)) {
            val cleanAlt = alternative.trim().lowercase(Locale.US)
            if (cleanAlt.length >= MIN_FRAGMENT_LENGTH &&
                cleanAlt != cleanExpected &&
                cleanExpected.startsWith(cleanAlt)
            ) {
                return Decision(top, UNCERTAIN_GREEN_CAP)
            }
        }

        // (c) Low confidence on the top hypothesis. Scores count as
        // present only when at least one value is > 0.
        val scoresPresent = confidences?.any { it > 0f } == true
        val topConfidence = if (scoresPresent) confidences?.firstOrNull() else null
        if (topConfidence != null && topConfidence < LOW_CONFIDENCE_THRESHOLD) {
            return Decision(top, UNCERTAIN_GREEN_CAP)
        }

        return Decision(top, null)
    }

    /**
     * The single-word retry verdict for the word-practice card: the
     * boundary decides what gets scored, [WordAccuracy] scores it, the
     * cap applies. Pure — the same function the unit tests drive.
     */
    fun scoreSingleWord(
        hypotheses: List<String>,
        confidences: List<Float>?,
        expectedWord: String
    ): Int {
        val decision = decide(hypotheses, confidences, expectedWord)
        val spoken = WordAccuracy.normalizeToken(decision.spokenToScore)
        val expected = WordAccuracy.normalizeToken(expectedWord)
        var score = WordAccuracy.computeWordAccuracy(expected, spoken)
        decision.scoreCap?.let { cap -> if (score > cap) score = cap }
        return score
    }
}

// ------------------------------------------------------------------ word feedback analyzer

/**
 * Per-word feedback for one graded live turn — the marked transcript
 * and the "Puedes mejorar" chip. Ported from Cognata's
 * LiveWordFeedbackAnalyzer against Aula's [WordAccuracy] alignment
 * (the same math, so a word's status here can never disagree with the
 * alignment's verdict on the same pair of sentences).
 *
 * Each model-reply word gets exactly one status:
 *  - OK — the aligned spoken token is the word itself, with no
 *    contradicting recognizer evidence;
 *  - WRONG_OR_MISSING — nothing aligned to the word, or a different
 *    word took its place;
 *  - PRONUNCIATION_SUSPECT — trap EVIDENCE exists, and only then:
 *    either the aligned spoken token IS the word's trap form, or an
 *    alternative recognizer hypothesis carries the trap form while
 *    the scored top hypothesis is clean (the final say goes through
 *    [PronunciationBoundary.decide]). A word is NEVER marked suspect
 *    from the transcript alone, and a curriculum-known trap word
 *    ([trapWords]) with no evidence stays OK — membership only
 *    enriches the suspect reason and never creates the mark.
 *
 * The trap form mirrors PronunciationBoundary's definition exactly:
 * the expected word starts with "s" (but not "sh") and the spoken form
 * is the word with one prepended "e" or "i" — the Spanish phantom-E.
 *
 * Pure and deterministic: same inputs, same feedback.
 */
object LiveWordFeedbackAnalyzer {

    /** Grammatical glue words; the weakest-word pick prefers content words. */
    private val functionWords = setOf(
        "the", "a", "an", "i", "you", "he", "she", "it", "we", "they",
        "is", "are", "was", "were", "am", "to", "of", "and", "or", "in",
        "on", "at", "my", "your", "his", "her", "our", "their", "this",
        "that", "with", "for", "have", "has", "do", "does", "did", "be",
        "been", "me", "him", "us", "them", "what", "when", "would", "can",
        "could", "should", "will", "just", "there", "here"
    )

    fun analyze(
        spokenText: String,
        hypotheses: List<String>,
        modelReply: String,
        trapWords: List<String>
    ): LiveTurnFeedback {
        val analysis = WordAccuracy.evaluateSentence(modelReply, spokenText)
        val knownTraps = trapWords.map { it.lowercase(Locale.US) }.toSet()
        // The scored top hypothesis is hypotheses[0] (== spokenText);
        // only the ALTERNATIVES can carry contradicting evidence.
        val alternativeTokens: List<List<String>> = hypotheses.drop(1).map { hypothesis ->
            hypothesis.lowercase(Locale.US)
                .split(Regex("\\s+"))
                .map { WordAccuracy.normalizeToken(it) }
                .filter { it.isNotEmpty() }
        }

        val words = analysis.wordTokens.map { token ->
            val word = token.cleanWord
            val spoken = token.spokenMatch
            when {
                spoken == null -> LiveWordFeedback(
                    word = word,
                    displayWord = token.displayWord,
                    spokenForm = null,
                    status = LiveWordStatus.WRONG_OR_MISSING,
                    reasonEs = "Faltó la palabra «$word» en tu respuesta.",
                    score = token.accuracyScore
                )

                spoken == word -> {
                    // Clean on the scored hypothesis — but an
                    // alternative may betray the trap form (the ASR
                    // cleaned the top hit). Route the final say
                    // through PronunciationBoundary.
                    val trapForm = trapFormInAlternatives(word, alternativeTokens)
                    val decision = trapForm?.let {
                        PronunciationBoundary.decide(listOf(word, it), null, word)
                    }
                    if (trapForm != null && decision?.spokenToScore == trapForm) {
                        suspectFeedback(word, token.displayWord, trapForm, word in knownTraps)
                    } else {
                        LiveWordFeedback(
                            word = word,
                            displayWord = token.displayWord,
                            spokenForm = spoken,
                            status = LiveWordStatus.OK,
                            reasonEs = "«$word» sonó clara.",
                            score = token.accuracyScore
                        )
                    }
                }

                isTrapForm(spoken, word) -> suspectFeedback(
                    word, token.displayWord, spoken, word in knownTraps
                )

                else -> LiveWordFeedback(
                    word = word,
                    displayWord = token.displayWord,
                    spokenForm = spoken,
                    status = LiveWordStatus.WRONG_OR_MISSING,
                    reasonEs = "Dijiste «$spoken» en vez de «$word».",
                    score = token.accuracyScore
                )
            }
        }

        // Deterministic weakest-word pick (the improvement chip):
        // pronunciation suspect first, then a wrong/missing content
        // word, then any wrong/missing word — bank order throughout.
        val weakest = words.firstOrNull { it.status == LiveWordStatus.PRONUNCIATION_SUSPECT }
            ?: words.firstOrNull {
                it.status == LiveWordStatus.WRONG_OR_MISSING && it.word !in functionWords
            }
            ?: words.firstOrNull { it.status == LiveWordStatus.WRONG_OR_MISSING }

        return LiveTurnFeedback(words = words, weakestWord = weakest)
    }

    private fun suspectFeedback(
        word: String,
        displayWord: String,
        trapForm: String,
        knownTrap: Boolean
    ): LiveWordFeedback {
        val reason = if (knownTrap) {
            "«$word» es una palabra trampa de tu curso: se escuchó «$trapForm». " +
                "Empieza directo con el silbido /s/, sin vocal antes."
        } else {
            "Se escuchó «$trapForm» en vez de «$word»: es la 'e' fantasma antes de la /s/ inicial."
        }
        return LiveWordFeedback(
            word = word,
            displayWord = displayWord,
            spokenForm = trapForm,
            status = LiveWordStatus.PRONUNCIATION_SUSPECT,
            reasonEs = reason,
            // The word's score is what the TRAP form earns, not what
            // the cleaned top hypothesis earned.
            score = WordAccuracy.computeWordAccuracy(word, trapForm)
        )
    }

    /** True when [spoken] is [word]'s phantom-E trap form (PronunciationBoundary's definition). */
    private fun isTrapForm(spoken: String, word: String): Boolean {
        if (!word.startsWith("s") || word.startsWith("sh")) return false
        return spoken == "e$word" || spoken == "i$word"
    }

    /** The first trap-form token for [word] found in any alternative hypothesis, if any. */
    private fun trapFormInAlternatives(word: String, alternativeTokens: List<List<String>>): String? {
        if (!word.startsWith("s") || word.startsWith("sh")) return null
        val eForm = "e$word"
        val iForm = "i$word"
        for (tokens in alternativeTokens) {
            for (token in tokens) {
                if (token == eForm || token == iForm) return token
            }
        }
        return null
    }
}

// ------------------------------------------------------------------ the grader

/**
 * The pure grading step for one live turn — the exact function the
 * live screen calls and the unit tests drive.
 *
 * Scoring adaptation (documented, no evaluator fork): a live prompt
 * carries 1–2 model replies, so the turn is scored by the UNCHANGED
 * [ConversationScorer.evaluateReply] against each reply in turn and
 * the best-scoring evaluation wins. Its expectedLine is also the
 * alignment target for the word feedback, so score and word marks
 * always describe the same model reply.
 */
object LiveGrader {

    fun grade(
        spokenText: String,
        hypotheses: List<String>,
        prompt: LivePrompt
    ): Pair<LiveTurnScore, LiveTurnFeedback> {
        var bestResult: LiveTurnScore? = null
        for (modelReply in prompt.modelReplies) {
            val candidate = ConversationScorer.evaluateReply(
                spokenText,
                LiveScoringTarget(
                    modelReply = modelReply,
                    targetKeywords = prompt.targetKeywords,
                    clarityTipEs = prompt.clarityTipEs,
                    trapNotesEs = prompt.trapNotesEs
                )
            )
            if (bestResult == null || candidate.accuracyScore > bestResult.accuracyScore) {
                bestResult = candidate
            }
        }
        val result = bestResult!!
        val feedback = LiveWordFeedbackAnalyzer.analyze(
            spokenText = spokenText,
            hypotheses = hypotheses,
            modelReply = result.expectedLine,
            trapWords = prompt.trapWords
        )
        return result to feedback
    }

    /** True when the input is a recognition failure marker, not a reply. */
    fun isSilenceMarker(spokenText: String): Boolean =
        spokenText.isBlank() || spokenText.equals("(Sin audio detectado)", ignoreCase = true)
}

// ------------------------------------------------------------------ the recognizer probe

/**
 * Aula's SpeechRecognizer wrapper for the live mode — the additive
 * plumbing the brief called "RecognitionEvidence-style": it publishes
 * the top hypothesis, the full evidence and a non-scored error line,
 * and grades NOTHING itself. Consumers (the live screen) decide what
 * a result means; an error simply closes the attempt.
 */
class LiveSpeechProbe(context: Context) {

    private val appContext: Context = context.applicationContext

    private val _lastRecognition = MutableStateFlow<String?>(null)
    val lastRecognition: StateFlow<String?> = _lastRecognition.asStateFlow()

    private val _lastEvidence = MutableStateFlow<RecognitionEvidence?>(null)
    val lastEvidence: StateFlow<RecognitionEvidence?> = _lastEvidence.asStateFlow()

    private val _micError = MutableStateFlow<String?>(null)
    val micError: StateFlow<String?> = _micError.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private var recognizer: SpeechRecognizer? = null

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            _isListening.value = false
        }

        override fun onError(error: Int) {
            _isListening.value = false
            // No result, no evidence: an error turn must leave nothing
            // behind that a consumer could mistake for a graded attempt.
            _lastEvidence.value = null
            // Recognition errors are NEVER graded: the error surfaces
            // only as this non-scored micError line. Network/server
            // failures get their own wording so the learner knows it
            // is a connection problem, not a pronunciation problem.
            _micError.value = when (error) {
                SpeechRecognizer.ERROR_NETWORK ->
                    "Sin conexión: no se pudo contactar el servicio de reconocimiento. Revisa tu conexión e intenta de nuevo."
                SpeechRecognizer.ERROR_SERVER ->
                    "Sin conexión: el servicio de reconocimiento no respondió. Intenta de nuevo más tarde."
                SpeechRecognizer.ERROR_NO_MATCH ->
                    "No se detectó voz clara. Intenta de nuevo."
                SpeechRecognizer.ERROR_AUDIO ->
                    "Error de captura de audio."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    "Se necesita permiso de micrófono para probar tu voz."
                else -> "No se pudo reconocer tu voz. Intenta de nuevo."
            }
        }

        override fun onResults(results: Bundle?) {
            _isListening.value = false
            _micError.value = null
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val spoken = matches?.firstOrNull() ?: ""
            // Publish the full evidence BEFORE the raw top hypothesis,
            // in the same synchronous callback, so any consumer
            // reacting to the raw value already sees this result's
            // alternatives — never the previous turn's.
            val confidences =
                if (results?.containsKey(RESULTS_CONFIDENCE_SCORES_KEY) == true) {
                    results.getFloatArray(RESULTS_CONFIDENCE_SCORES_KEY)?.toList()
                } else {
                    null
                }
            _lastEvidence.value = RecognitionEvidence(
                hypotheses = matches ?: emptyList(),
                confidences = confidences
            )
            _lastRecognition.value = spoken
        }

        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    // Declared AFTER `listener`: Kotlin initializes properties in
    // declaration order, and this block hands `listener` to the
    // recognizer — referencing it earlier fails compilation with
    // "Variable 'listener' must be initialized" (CI run #32).
    init {
        try {
            if (SpeechRecognizer.isRecognitionAvailable(appContext)) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
                recognizer?.setRecognitionListener(listener)
            }
        } catch (e: Exception) {
            recognizer = null
        }
    }

    /**
     * Starts one listening attempt. [promptText] is what the system
     * prompt shows the learner (the current objective — never a model
     * reply: the suggestion gate applies to the mic prompt too).
     * Clears any stale result/evidence first, so a leftover value can
     * never be recorded as this attempt's answer.
     */
    fun startListening(promptText: String) {
        _lastRecognition.value = null
        _lastEvidence.value = null
        val rec = recognizer
        if (rec == null) {
            // No recogniser on this device: never report "listening".
            _isListening.value = false
            _micError.value = "El reconocimiento de voz no está disponible en este dispositivo."
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PROMPT, promptText)
        }
        try {
            rec.startListening(intent)
            _isListening.value = true
            _micError.value = null
        } catch (e: Exception) {
            _isListening.value = false
            _micError.value = "No se pudo iniciar el micrófono. Revisa el permiso de micrófono."
        }
    }

    fun stopListening() {
        try {
            recognizer?.stopListening()
        } catch (e: Exception) {
        }
        _isListening.value = false
    }

    /** Drops any leftover recognition/evidence (e.g. before a fresh live session). */
    fun clearRecognition() {
        _lastRecognition.value = null
        _lastEvidence.value = null
    }

    fun destroy() {
        try {
            recognizer?.destroy()
        } catch (e: Exception) {
        }
        recognizer = null
    }

    companion object {
        // The documented Bundle key for per-hypothesis confidence
        // scores; this SDK exposes no compile-time constant for it
        // (the same lesson Cognata's SpeechManager recorded).
        private const val RESULTS_CONFIDENCE_SCORES_KEY = "results_confidence_scores"
    }
}
