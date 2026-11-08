package com.aula.nativeapp

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

/*
 * Conversación en vivo — the live chat screen inside Conversaciones.
 *
 * Mediterranean Journal clothes, Aula idiom: a scrolling journal page
 * (like every other Aula screen), partner bubbles on cream, the
 * learner's replies on the olive wash with the problem words marked,
 * a "Puedes mejorar" chip presented by La Profesora, and her inline
 * word-practice card (hear it through TtsManager, see the mouth in
 * MouthFigure, retry the word by voice).
 *
 * The palette literals below mirror MainActivity's private theme
 * values (README documents them): terracotta deep #A34A24, olive
 * #66713F / deep #4E5A30, ink #2C2620, journal line #D9C9A8, and the
 * Good/Bad washes #E7EDD8 / #F7E3D5. The shared composables
 * (JournalCard, Stamp, PrimaryButton…) come from MainActivity.kt.
 *
 * Mirror wiring follows Conversaciones' own semantics — per-turn
 * events in the "conversaciones" context (the exact shapes are
 * [liveMirrorEvents], unit-tested as data):
 *  - one "conversation_turn" event per graded reply (correct = the
 *    scorer's GREEN band), the same pattern the guided scenes log;
 *  - one event for the phantom-E FAMILY pattern
 *    ([PHANTOM_E_PATTERN_KEY], correct = false) per evidence-backed
 *    PRONUNCIATION_SUSPECT word. The family is a single pattern, not
 *    one key per word: a per-word key could never be named (Mirror
 *    needs >= 3 errors across >= 2 contexts, and no other surface
 *    logs stray words). The family's second context is the Guide —
 *    GuideCard ratings on the s-trap guide word "sister" log the
 *    same key in the Guide's contexts — and its transfer items
 *    (Data.kt pe1–pe4) give the Espejo a working transfer check.
 * Recognizer errors log NOTHING: they only close the attempt and show
 * the probe's error line. Blank/silence results close the attempt
 * ungraded. The word retry is display-only (see LiveSessionState).
 * There is no completion record to write in Aula — the guided scenes
 * have none either — so wrap-up writes nothing extra, and leaving
 * mid-session writes nothing: per-turn events already logged stay,
 * exactly as guided picks do.
 *
 * The two mic funnels (reply / word retry) share ONE recognizer:
 * a single ownership state ([activeListen]) means starting one
 * funnel supersedes the other — the superseded attempt closes
 * ungraded and error-free, and each funnel's button is disabled
 * while the other owns the mic. A permission denial is never
 * silent either: the funnel that asked shows why and how to
 * recover (re-ask, or the app's page in system Settings when the
 * system will not ask again).
 *
 * The suggestion gate (Cognata's rule, carried over): the current
 * prompt's model replies stay hidden until the learner's first real
 * attempt at that prompt or an explicit "Ver sugerencia" tap; the
 * Spanish objective is always visible.
 *
 * Process-death restore is excluded for v1 (documented with the
 * design): session state lives in `remember`, so an interrupted
 * session simply restarts — a clean reset, never a half-restored chat.
 */

private val LiveTerracottaDeep = Color(0xFF8F3B18)
private val LiveOlive = Color(0xFF5E6B34)
private val LiveOliveDeep = Color(0xFF44512A)
private val LiveInk = Color(0xFF28211B)
private val LiveLine = Color(0xFFDCCBA6)
private val LiveGoodBg = Color(0xFFE4ECD2)
private val LiveBadBg = Color(0xFFF9E4D4)

/** Mouth shape for the practice card: the Guide's own steps when the word is a guide word. */
private fun mouthShapeFor(word: String): String {
    GUIDE_WORDS[word.lowercase()]?.let { return it.steps.first().shape }
    val w = word.lowercase()
    return when {
        w.contains("th") -> "th"
        w.startsWith("sh") -> "round"
        w.contains("ee") -> "wide"
        w.startsWith("s") -> "teeth"
        w.contains("oo") || w.startsWith("w") -> "round"
        else -> "relaxed"
    }
}

@Composable
fun LiveConversationScreen(
    conversation: Conversation,
    store: MirrorStore,
    tts: TtsManager,
    bump: () -> Unit,
    onExit: () -> Unit
) {
    val bank = remember(conversation.id) { LiveConversationBanks.bankFor(conversation.id) }
    val context = LocalContext.current
    val probe = remember { LiveSpeechProbe(context) }
    DisposableEffect(Unit) {
        onDispose { probe.destroy() }
    }

    // System back leaves the live conversation, not the app (Mi3) —
    // the same exit as the on-screen button: voice and recognizer
    // stop, and the per-turn events already logged stay in the diary.
    BackHandler {
        tts.stop()
        probe.stopListening()
        onExit()
    }

    if (bank == null) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            TextButton(onClick = onExit) { Text("← Volver a las conversaciones") }
            JournalCard {
                Text("Esta conversación todavía no tiene versión en vivo. La escena guiada sigue disponible.")
            }
        }
        return
    }

    val partnerName = remember(conversation.id) { LiveConversationBanks.partnerNameFor(conversation) }
    var session by remember(conversation.id) { mutableStateOf<LiveSessionState?>(null) }
    var replyArmed by remember { mutableStateOf(false) }
    var retryArmed by remember { mutableStateOf(false) }
    var suggestionRevealed by remember { mutableStateOf(false) }

    // Mic ownership (Mo1): exactly one funnel owns the recognizer at
    // a time — "reply", "retry", or none. Starting one supersedes
    // the other; a superseded attempt's late error is swallowed, not
    // shown (being replaced was not a recognition failure).
    var activeListen by remember { mutableStateOf<String?>(null) }
    var swallowNextError by remember { mutableStateOf(false) }
    // Build E badge: the last graded transcript came from the app's
    // own on-device engine, not the phone's recognition service.
    var lastHeardViaApp by remember { mutableStateOf(false) }

    // Permission flow (M2): which funnel is waiting on the system
    // prompt, and which funnel was denied (with whether the denial
    // is permanent — the system will not ask again). A denial always
    // surfaces a note + a recovery path in the funnel that asked.
    var pendingPermission by remember { mutableStateOf<String?>(null) }
    var denialFunnel by remember { mutableStateOf<String?>(null) }
    var denialPermanent by remember { mutableStateOf(false) }

    val rawRecognition by probe.lastRecognition.collectAsState()
    val micError by probe.micError.collectAsState()
    val isListening by probe.isListening.collectAsState()

    fun startSession() {
        probe.clearRecognition()
        val opening = LiveConversationEngine.openingPrompt(bank)
        session = LiveSessionState(
            conversation = conversation,
            bank = bank,
            partnerName = partnerName,
            entries = listOf(
                LiveChatEntry(id = 0, fromPartner = true, text = opening.partnerLine, promptId = opening.id)
            ),
            currentPrompt = opening
        )
        suggestionRevealed = false
        tts.speak(opening.partnerLine, slow = false)
    }

    fun recordReply(spoken: String, evidence: RecognitionEvidence?) {
        val state = session ?: return
        if (state.isFinished || !state.awaitingReply) return
        val prompt = state.currentPrompt ?: return
        if (LiveGrader.isSilenceMarker(spoken)) {
            // A recognition failure, not a reply: close the attempt
            // WITHOUT a score, a chat entry or a Mirror event.
            session = state.copy(awaitingReply = false)
            return
        }

        val (result, feedback) = LiveGrader.grade(
            spokenText = spoken,
            hypotheses = evidence?.hypotheses ?: listOf(spoken),
            prompt = prompt
        )

        val exchange = LiveExchange(
            promptId = prompt.id,
            partnerLine = prompt.partnerLine,
            spokenText = spoken,
            result = result,
            feedback = feedback
        )
        val learnerEntry = LiveChatEntry(
            id = state.entries.size,
            fromPartner = false,
            text = spoken,
            exchangeIndex = state.exchanges.size
        )
        var updated = state.copy(
            exchanges = state.exchanges + exchange,
            entries = state.entries + learnerEntry,
            awaitingReply = false,
            practiceWord = null,
            practiceExchangeIndex = null,
            practiceFeedback = null,
            practiceScore = null
        )

        // Mirror: the turn's events, exactly the shapes
        // liveMirrorEvents defines (see the header comment).
        for ((pattern, eventContext, ok) in liveMirrorEvents(result, feedback)) {
            store.logEvent(pattern, eventContext, ok)
        }
        bump()

        // Flow: the engine picks the next prompt from THIS transcript.
        val nextPrompt = LiveConversationEngine.nextPrompt(
            bank = state.bank,
            usedBranchIds = updated.usedBranchIds,
            usedGenericIds = updated.usedGenericIds,
            exchangesDone = updated.exchanges.size,
            lastTranscript = spoken
        )
        if (nextPrompt == null) {
            val wrapEntry = LiveChatEntry(
                id = updated.entries.size,
                fromPartner = true,
                text = state.bank.wrapUpLine
            )
            updated = updated.copy(
                entries = updated.entries + wrapEntry,
                currentPrompt = null,
                isFinished = true
            )
            session = updated
            tts.speak(state.bank.wrapUpLine, slow = false)
        } else {
            val isBranch = state.bank.branches.any { it.prompt.id == nextPrompt.id }
            val partnerEntry = LiveChatEntry(
                id = updated.entries.size,
                fromPartner = true,
                text = nextPrompt.partnerLine,
                promptId = nextPrompt.id
            )
            updated = updated.copy(
                entries = updated.entries + partnerEntry,
                currentPrompt = nextPrompt,
                usedBranchIds = if (isBranch) updated.usedBranchIds + nextPrompt.id else updated.usedBranchIds,
                usedGenericIds = if (!isBranch) updated.usedGenericIds + nextPrompt.id else updated.usedGenericIds
            )
            session = updated
            suggestionRevealed = false
            tts.speak(nextPrompt.partnerLine, slow = false)
        }
    }

    // Start (or restart) the session when the screen opens.
    LaunchedEffect(conversation.id) {
        if (session == null) startSession()
    }

    // The mic funnel: only an armed attempt may record, and only while
    // the session awaits a reply for the current prompt — a stale
    // recognition value can never be recorded twice.
    LaunchedEffect(rawRecognition) {
        val spoken = rawRecognition ?: return@LaunchedEffect
        // A result settles whichever attempt produced it: any pending
        // error-swallow belongs to a replaced attempt and dies here.
        swallowNextError = false
        lastHeardViaApp = probe.lastRecognitionSource.value == "app"
        val state = session ?: return@LaunchedEffect
        if (retryArmed) {
            retryArmed = false
            activeListen = null
            val word = state.practiceWord ?: return@LaunchedEffect
            val evidence = probe.lastEvidence.value
            val score = PronunciationBoundary.scoreSingleWord(
                hypotheses = evidence?.hypotheses ?: listOf(spoken),
                confidences = evidence?.confidences,
                expectedWord = word
            )
            // Display-only: the retry never writes to the diary.
            session = state.copy(practiceScore = score)
            return@LaunchedEffect
        }
        if (replyArmed && state.awaitingReply) {
            replyArmed = false
            activeListen = null
            recordReply(spoken, probe.lastEvidence.value)
        }
    }

    // A recognizer error closes the armed attempt ungraded — the
    // error line below is its only trace. An error from a SUPERSEDED
    // attempt is not an error at all: it is swallowed silently.
    LaunchedEffect(micError) {
        if (micError == null) return@LaunchedEffect
        if (swallowNextError) {
            swallowNextError = false
            probe.clearMicError()
            return@LaunchedEffect
        }
        if (retryArmed) retryArmed = false
        if (replyArmed) {
            replyArmed = false
            session = session?.copy(awaitingReply = false)
        }
        activeListen = null
    }

    // -- Mic ownership + permission helpers (Mo1 / M2) ---------------

    fun disarmAll() {
        replyArmed = false
        retryArmed = false
        activeListen = null
    }

    /**
     * The other funnel is taking the recognizer: the armed attempt
     * closes WITHOUT a verdict and WITHOUT an error line — being
     * replaced is not a recognition failure. Its late error, if the
     * service sends one, is swallowed by the error effect above.
     */
    fun supersedeActiveListen() {
        if (activeListen == null) return
        val wasReply = replyArmed
        disarmAll()
        swallowNextError = true
        probe.stopListening()
        if (wasReply) {
            session = session?.copy(awaitingReply = false)
        }
    }

    fun startReplyListen() {
        val state = session ?: return
        val prompt = state.currentPrompt ?: return
        if (state.isFinished) return
        replyArmed = true
        activeListen = "reply"
        denialFunnel = null
        session = state.copy(awaitingReply = true)
        probe.startListening(prompt.objectiveEs)
    }

    fun startRetryListen() {
        val state = session ?: return
        val word = state.practiceWord ?: return
        retryArmed = true
        activeListen = "retry"
        denialFunnel = null
        probe.startListening(word)
    }

    fun hasMicPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // No settings page reachable: the denial note already
            // spells out the manual path.
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val funnel = pendingPermission
        pendingPermission = null
        if (granted) {
            denialFunnel = null
            denialPermanent = false
            when (funnel) {
                "retry" -> startRetryListen()
                "reply" -> startReplyListen()
            }
        } else {
            // A denial is never silent (M2): the funnel that asked
            // shows why nothing happened and how to recover. Tapping
            // its mic again re-asks while the system allows; when no
            // rationale remains after a denial, the system will not
            // ask again and the note points to the app's Settings
            // page instead.
            denialFunnel = funnel ?: "reply"
            val activity = context.findActivity()
            denialPermanent = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity, Manifest.permission.RECORD_AUDIO
                )
        }
    }

    fun armReplyMic() {
        val state = session ?: return
        if (state.isFinished || state.currentPrompt == null) return
        if (activeListen == "reply") return
        tts.stop()
        if (activeListen == "retry") supersedeActiveListen()
        if (hasMicPermission()) {
            startReplyListen()
        } else {
            pendingPermission = "reply"
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun armRetryMic() {
        val state = session ?: return
        if (state.practiceWord == null) return
        if (activeListen == "retry") return
        tts.stop()
        if (activeListen == "reply") supersedeActiveListen()
        if (hasMicPermission()) {
            startRetryListen()
        } else {
            // The retry funnel asks through the same prompt and shows
            // its own denial note — no silent dead button.
            pendingPermission = "retry"
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val state = session
    val scrollState = rememberScrollState()
    LaunchedEffect(state?.entries?.size, state?.isFinished) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }
    // Opening the word-practice card scrolls it into view (Mo2): the
    // card renders below the whole transcript, so without this the
    // chip tap appears to do nothing. The card reports its own top
    // position in the page; when it changes while a word is open,
    // scroll to it (with a small margin above).
    var practiceCardTop by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(state?.practiceWord, practiceCardTop) {
        val top = practiceCardTop
        if (state?.practiceWord != null && top != null) {
            scrollState.animateScrollTo(maxOf(0, top - 24))
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        TextButton(onClick = {
            tts.stop()
            probe.stopListening()
            onExit()
        }) { Text("← Volver a las conversaciones") }

        ViewTitle(
            "Conversación en vivo",
            "${conversation.title} · con $partnerName. Habla de verdad: la conversación sigue lo que TÚ digas."
        )
        TeacherStrip(
            "Yo escucho cada respuesta y anoto tus patrones en tu diario, como siempre. Si una palabra sale marcada, tócala en «Puedes mejorar» y la practicamos juntos: oírla, ver la boca y repetirla.",
            "Let's talk for real!",
            tts
        )
        Spacer(Modifier.height(12.dp))

        if (state == null) {
            JournalCard { Text("Preparando la conversación…") }
            return@Column
        }

        // Exchange progress: one dot per goal exchange.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Intercambio ${minOf(state.exchangesDone + (if (state.isFinished) 0 else 1), LiveConversationEngine.EXCHANGE_GOAL)} de ${LiveConversationEngine.EXCHANGE_GOAL}",
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(10.dp))
            for (i in 0 until LiveConversationEngine.EXCHANGE_GOAL) {
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (i < state.exchangesDone) 10.dp else 8.dp)
                        .background(
                            when {
                                i < state.exchangesDone -> LiveOlive
                                i == state.exchangesDone && !state.isFinished -> Color(0xFFBC5325)
                                else -> LiveLine
                            },
                            CircleShape
                        )
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        // The chat transcript.
        for (entry in state.entries) {
            if (entry.fromPartner) {
                JournalCard {
                    Text("🧑 $partnerName", fontWeight = FontWeight.Bold, color = LiveTerracottaDeep)
                    Spacer(Modifier.height(4.dp))
                    Text(entry.text, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = { tts.speak(entry.text, slow = false) }) {
                        Text("🔊 Escuchar de nuevo", color = LiveTerracottaDeep)
                    }
                }
            } else {
                val exchange = entry.exchangeIndex?.let { state.exchanges.getOrNull(it) }
                if (exchange != null) {
                    LearnerExchangeBlock(
                        exchange = exchange,
                        exchangeIndex = entry.exchangeIndex,
                        practiceWord = state.practiceWord,
                        practiceExchangeIndex = state.practiceExchangeIndex,
                        onPracticeWord = { feedback, index ->
                            session = if (state.practiceWord == feedback.word &&
                                state.practiceExchangeIndex == index
                            ) {
                                state.copy(
                                    practiceWord = null,
                                    practiceExchangeIndex = null,
                                    practiceFeedback = null,
                                    practiceScore = null
                                )
                            } else {
                                state.copy(
                                    practiceWord = feedback.word,
                                    practiceExchangeIndex = index,
                                    practiceFeedback = feedback,
                                    practiceScore = null
                                )
                            }
                        }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Inline word practice (La Profesora's card). The reason it
        // shows is the tapped chip's OWN turn feedback (Mi4), carried
        // in the session state — never another turn's verdict on the
        // same word.
        val practice = state.practiceWord
        if (practice != null) {
            Box(
                Modifier.onGloballyPositioned {
                    practiceCardTop = it.positionInParent().y.toInt()
                }
            ) {
                LiveWordPracticeCard(
                    word = practice,
                    reasonEs = state.practiceFeedback?.reasonEs,
                    retryScore = state.practiceScore,
                    isListening = isListening && retryArmed,
                    retryEnabled = activeListen != "reply",
                    denialPermanent = if (denialFunnel == "retry") denialPermanent else null,
                    onOpenSettings = { openAppSettings() },
                    tts = tts,
                    onHear = { tts.speak(practice, slow = false) },
                    onHearSlow = { tts.speak(practice, slow = true) },
                    onRetry = { armRetryMic() },
                    onClose = {
                        session = state.copy(
                            practiceWord = null,
                            practiceExchangeIndex = null,
                            practiceFeedback = null,
                            practiceScore = null
                        )
                    }
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        if (state.isFinished) {
            JournalCard(oliveCard = true) {
                Stamp("Conversación completa")
                Spacer(Modifier.height(8.dp))
                Text(
                    "¡Conversación terminada! ${state.exchangesDone} intercambios con $partnerName.",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(6.dp))
                Text("Tu promedio de la conversación: ${state.runningAverageScore}%.")
                if (state.weakWords.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Palabras para seguir puliendo:", fontWeight = FontWeight.Bold)
                    for (w in state.weakWords) {
                        Text("· «${w.word}» — ${w.reasonEs}")
                    }
                }
                Spacer(Modifier.height(6.dp))
                NoteText("Tus respuestas quedaron en tu diario: si un mismo error se repite en varias actividades, el Espejo lo nombrará con su evidencia.")
                Spacer(Modifier.height(12.dp))
                Row {
                    PrimaryButton("Otra conversación") {
                        tts.stop()
                        onExit()
                    }
                    Spacer(Modifier.width(8.dp))
                    GhostButton("Repetir en vivo") { startSession() }
                }
            }
            return@Column
        }

        // The current prompt panel: objective always visible, the
        // suggestion behind the gate, the big push-to-talk button.
        val prompt = state.currentPrompt
        if (prompt != null) {
            JournalCard {
                Stamp("Tu turno", olive = true)
                Spacer(Modifier.height(8.dp))
                Text(prompt.objectiveEs, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                NoteText("Consejo de claridad: ${prompt.clarityTipEs}")
                for (trap in prompt.trapNotesEs) {
                    NoteText("⚠ $trap")
                }
                Spacer(Modifier.height(10.dp))
                val hasAttempt = state.exchanges.any { it.promptId == prompt.id }
                if (suggestionRevealed || hasAttempt) {
                    Text("Sugerencia:", fontWeight = FontWeight.Bold)
                    for (reply in prompt.modelReplies) {
                        Text("«$reply»")
                    }
                    Spacer(Modifier.height(6.dp))
                    GhostButton("🔊 Escuchar la sugerencia") {
                        tts.speak(prompt.modelReplies.first(), slow = false)
                    }
                } else {
                    GhostButton("Ver sugerencia") { suggestionRevealed = true }
                }
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    when {
                        activeListen == "retry" -> "🎙 Tu práctica tiene el micrófono…"
                        isListening -> "🎙 Escuchando… toca para terminar"
                        state.awaitingReply -> "🎙 Escuchando…"
                        else -> "🎙 Hablar"
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = activeListen != "retry"
                ) {
                    if (isListening) probe.stopListening() else armReplyMic()
                }
                if (micError != null) {
                    Spacer(Modifier.height(8.dp))
                    FeedbackBox(micError!!, good = false)
                }
                if (lastHeardViaApp) {
                    Spacer(Modifier.height(8.dp))
                    NoteText("Esa respuesta la transcribió el motor de voz de la app, dentro de tu teléfono — el servicio del teléfono no escuchó nada.")
                }
                if (denialFunnel == "reply") {
                    Spacer(Modifier.height(8.dp))
                    MicDenialNote(
                        permanent = denialPermanent,
                        onOpenSettings = { openAppSettings() }
                    )
                }
                Spacer(Modifier.height(6.dp))
                NoteText("Sin micrófono también vale: la escena guiada de esta conversación sigue arriba, con respuestas para elegir.")
            }
        }
    }
}

/**
 * One graded learner turn: the reply bubble with its words marked,
 * the turn score, La Profesora's verdict line, and the "Puedes
 * mejorar" chip for the weakest word.
 */
@Composable
private fun LearnerExchangeBlock(
    exchange: LiveExchange,
    exchangeIndex: Int?,
    practiceWord: String?,
    practiceExchangeIndex: Int?,
    onPracticeWord: (LiveWordFeedback, Int?) -> Unit
) {
    JournalCard(oliveCard = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🗣 Tú", fontWeight = FontWeight.Bold, color = LiveOliveDeep)
            Spacer(Modifier.width(10.dp))
            val score = exchange.result.accuracyScore
            Box(
                Modifier
                    .background(
                        if (score >= 60) LiveGoodBg else LiveBadBg,
                        RoundedCornerShape(10.dp)
                    )
                    .border(
                        1.dp,
                        if (score >= 85) LiveOlive else Color(0xFFBC5325),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("$score%", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(6.dp))
        // The marked transcript: OK words in ink, wrong/missing in
        // terracotta, evidence-backed pronunciation suspects washed.
        val marked = buildAnnotatedString {
            exchange.feedback.words.forEachIndexed { i, w ->
                if (i > 0) append(" ")
                when (w.status) {
                    LiveWordStatus.OK -> append(w.displayWord)
                    LiveWordStatus.WRONG_OR_MISSING -> withStyle(
                        SpanStyle(color = LiveTerracottaDeep, fontWeight = FontWeight.Bold)
                    ) { append(w.displayWord) }
                    LiveWordStatus.PRONUNCIATION_SUSPECT -> withStyle(
                        SpanStyle(
                            color = LiveTerracottaDeep,
                            fontWeight = FontWeight.Bold,
                            background = LiveBadBg
                        )
                    ) { append(w.displayWord) }
                }
            }
        }
        Text(marked, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(4.dp))
        NoteText("Escuché: «${exchange.spokenText}»")
        Spacer(Modifier.height(6.dp))
        FeedbackBox(
            exchange.result.feedbackMessageEs,
            good = exchange.result.scoreCategory != LiveScoreCategory.RED
        )
        for (trap in exchange.result.detectedTraps) {
            Spacer(Modifier.height(4.dp))
            NoteText("⚠ $trap")
        }
        val weakest = exchange.feedback.weakestWord
        if (weakest != null) {
            Spacer(Modifier.height(10.dp))
            // Expanded only when THIS exchange's chip opened the
            // card — the same word can be weak in several turns.
            val expanded = practiceWord == weakest.word && practiceExchangeIndex == exchangeIndex
            PrimaryButton(
                if (expanded) "Cerrar práctica" else "Puedes mejorar: «${weakest.word}» · ${weakest.score}%",
                olive = !expanded,
                modifier = Modifier.fillMaxWidth()
            ) { onPracticeWord(weakest, exchangeIndex) }
            Spacer(Modifier.height(4.dp))
            NoteText(weakest.reasonEs + " Toca el botón para practicar esta palabra con la profesora.")
        }
    }
}

/**
 * La Profesora's inline word-practice card: hear the word (normal and
 * slow, through TtsManager), see the mouth (MouthFigure — the Guide's
 * own steps when the word is one of her four guide words, a best-fit
 * shape otherwise), and retry the word by voice. The retry verdict is
 * display-only: it never writes to the diary (the turn that surfaced
 * the word already counted).
 */
@Composable
private fun LiveWordPracticeCard(
    word: String,
    reasonEs: String?,
    retryScore: Int?,
    isListening: Boolean,
    retryEnabled: Boolean,
    denialPermanent: Boolean?,
    onOpenSettings: () -> Unit,
    tts: TtsManager,
    onHear: () -> Unit,
    onHearSlow: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit
) {
    JournalCard {
        Row(verticalAlignment = Alignment.Top) {
            TeacherAvatar(tts, 52.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("La profesora · practica «$word»", fontWeight = FontWeight.Bold, color = LiveTerracottaDeep)
                if (reasonEs != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(reasonEs, style = MaterialTheme.typography.bodyMedium)
                }
            }
            TextButton(onClick = onClose) { Text("✕", fontSize = 18.sp) }
        }
        Spacer(Modifier.height(10.dp))
        MouthFigure(mouthShapeFor(word))
        GUIDE_WORDS[word.lowercase()]?.let { gw ->
            Spacer(Modifier.height(6.dp))
            NoteText("💡 ${gw.tip} En la Guía (Biblioteca) la ves sonido por sonido.")
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PrimaryButton("🔊 Escucharla", modifier = Modifier.weight(1f)) { onHear() }
            GhostButton("🐢 Lento", modifier = Modifier.weight(1f)) { onHearSlow() }
        }
        Spacer(Modifier.height(8.dp))
        PrimaryButton(
            if (isListening) "🎙 Escuchando…" else "🎙 Repetir la palabra",
            olive = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = retryEnabled
        ) { onRetry() }
        if (denialPermanent != null) {
            Spacer(Modifier.height(8.dp))
            MicDenialNote(
                permanent = denialPermanent,
                onOpenSettings = onOpenSettings
            )
        }
        if (retryScore != null) {
            Spacer(Modifier.height(10.dp))
            FeedbackBox(
                when {
                    retryScore >= 85 -> "Ahora sí: «$word» sonó clara ($retryScore%). Esta práctica no cambia tu diario: el turno ya contó; esto es para tu oído."
                    retryScore >= 60 -> "Casi: «$word» llegó a $retryScore%. Escúchala lento una vez más y repite. (Práctica sin nota: tu diario ya registró el turno.)"
                    else -> "Todavía no: «$word» quedó en $retryScore%. Mira la boca, escucha el modelo y prueba otra vez. Sin castigo: esto no se anota."
                },
                good = retryScore >= 60
            )
        }
    }
}

/** The hosting Activity behind a Compose context, for the permission rationale check. */
private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * The permission-denial note (M2), shown by whichever mic funnel was
 * denied. Never silent, always with a recovery path: re-ask by
 * tapping the mic again while the system allows, or — when the
 * denial is permanent — a button to this app's page in system
 * Settings, where the mic can be switched back on.
 */
@Composable
private fun MicDenialNote(
    permanent: Boolean,
    onOpenSettings: () -> Unit
) {
    FeedbackBox(
        if (permanent) {
            "El micrófono está desactivado para Aula y el sistema ya no volverá a preguntar. " +
                "Para recuperarlo: Ajustes del teléfono → Aplicaciones → Aula → Permisos → Micrófono."
        } else {
            "Sin permiso de micrófono no puedo escucharte, y sin escucharte no hay conversación en vivo. " +
                "Toca el botón de hablar cuando quieras y te lo pido otra vez."
        },
        good = false
    )
    if (permanent) {
        Spacer(Modifier.height(8.dp))
        GhostButton("Abrir los ajustes de la app") { onOpenSettings() }
    }
}
