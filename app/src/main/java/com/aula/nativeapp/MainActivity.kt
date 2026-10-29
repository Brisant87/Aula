package com.aula.nativeapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil

/*
 * Aula native (Stage 2 scaffold) — Mediterranean Journal.
 *
 * A faithful first native version of the Aula web app: Hoy (guided class),
 * Espejo (Mirror), Conversaciones (+ Escucha), Biblioteca, Camino, Ajustes.
 * Local-first, deterministic Mirror rules, no network, no Firebase, no AI APIs.
 */

// ------------------------------------------------------------------ theme

private val Sand = Color(0xFFF5EDDC)
private val Cream = Color(0xFFFBF7EC)
private val Terracotta = Color(0xFFC05B2E)
private val TerracottaDeep = Color(0xFFA34A24)
private val Olive = Color(0xFF66713F)
private val OliveDeep = Color(0xFF4E5A30)
private val Ink = Color(0xFF2C2620)
private val JournalLine = Color(0xFFD9C9A8)
private val GoodBg = Color(0xFFE7EDD8)
private val BadBg = Color(0xFFF7E3D5)

@Composable
fun AulaTheme(content: @Composable () -> Unit) {
    val scheme = lightColorScheme(
        primary = Terracotta,
        onPrimary = Cream,
        secondary = Olive,
        onSecondary = Cream,
        background = Sand,
        onBackground = Ink,
        surface = Cream,
        onSurface = Ink,
        surfaceVariant = Sand,
        onSurfaceVariant = Ink
    )
    val serif = FontFamily.Serif
    val type = Typography(
        headlineMedium = TextStyle(fontFamily = serif, fontWeight = FontWeight.Bold, fontSize = 26.sp),
        headlineSmall = TextStyle(fontFamily = serif, fontWeight = FontWeight.Bold, fontSize = 21.sp),
        titleLarge = TextStyle(fontFamily = serif, fontWeight = FontWeight.Bold, fontSize = 20.sp),
        titleMedium = TextStyle(fontFamily = serif, fontWeight = FontWeight.Bold, fontSize = 17.sp),
        bodyLarge = TextStyle(fontFamily = serif, fontSize = 16.sp, lineHeight = 23.sp),
        bodyMedium = TextStyle(fontFamily = serif, fontSize = 15.sp, lineHeight = 21.sp),
        bodySmall = TextStyle(fontFamily = serif, fontSize = 13.sp, lineHeight = 18.sp),
        labelLarge = TextStyle(fontFamily = serif, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    )
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}

// ------------------------------------------------------------------ activity

class MainActivity : ComponentActivity() {

    private lateinit var store: MirrorStore
    private lateinit var settings: SettingsStore
    private lateinit var tts: TtsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = MirrorStore(this)
        settings = SettingsStore(this)
        tts = TtsManager(this) { settings.speed }
        setContent {
            AulaTheme {
                AulaApp(store = store, settings = settings, tts = tts)
            }
        }
    }

    override fun onDestroy() {
        if (::tts.isInitialized) tts.shutdown()
        super.onDestroy()
    }
}

private data class TabDef(val id: String, val label: String, val icon: String)

private val TABS = listOf(
    TabDef("hoy", "Hoy", "✎"),
    TabDef("espejo", "Espejo", "◉"),
    TabDef("conversaciones", "Conversar", "✉"),
    TabDef("biblioteca", "Biblioteca", "▤"),
    TabDef("camino", "Camino", "✦"),
    TabDef("ajustes", "Ajustes", "⚙")
)

@Composable
fun AulaApp(store: MirrorStore, settings: SettingsStore, tts: TtsManager) {
    var tab by remember { mutableStateOf("hoy") }
    var tick by remember { mutableStateOf(0) }
    val bump: () -> Unit = { tick++ }

    Scaffold(
        containerColor = Sand,
        bottomBar = {
            NavigationBar(containerColor = Cream) {
                for (t in TABS) {
                    NavigationBarItem(
                        selected = tab == t.id,
                        onClick = {
                            tab = t.id
                            tts.stop()
                        },
                        icon = { Text(t.icon, fontSize = 17.sp) },
                        label = { Text(t.label, fontSize = 10.sp) }
                    )
                }
            }
        }
    ) { pad ->
        Box(
            Modifier
                .padding(pad)
                .fillMaxSize()
        ) {
            // Reading `tick` here re-runs the current screen whenever the
            // Mirror store changes, so lists and statuses stay fresh.
            val currentTick = tick
            if (currentTick < -1) Text("") // never true; keeps the read obvious
            when (tab) {
                "hoy" -> HoyScreen(store, settings, tts, bump) { tab = "espejo" }
                "espejo" -> EspejoScreen(store, settings, tts, bump)
                "conversaciones" -> ConversacionesScreen(store, settings, tts, bump)
                "biblioteca" -> BibliotecaScreen(store, settings, tts, bump)
                "camino" -> CaminoScreen(store, settings, tts, bump)
                "ajustes" -> AjustesScreen(store, settings, bump)
            }
        }
    }
}

// ------------------------------------------------------------------ shared UI

@Composable
fun Stamp(text: String, olive: Boolean = false) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = if (olive) OliveDeep else TerracottaDeep,
        modifier = Modifier
            .border(1.dp, if (olive) Olive else Terracotta, RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
fun JournalCard(
    oliveCard: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (oliveCard) Color(0xFFF1EEDC) else Cream
        ),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, JournalLine),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun ViewTitle(title: String, sub: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium, color = Ink)
    Spacer(Modifier.height(2.dp))
    Text(sub, style = MaterialTheme.typography.bodyMedium, color = Ink.copy(alpha = 0.75f))
    Spacer(Modifier.height(14.dp))
}

@Composable
fun FeedbackBox(text: String, good: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(if (good) GoodBg else BadBg, RoundedCornerShape(8.dp))
            .border(1.dp, if (good) Olive else Terracotta, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun NoteText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = Ink.copy(alpha = 0.7f)
    )
}

@Composable
fun WordChip(text: String, used: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = !used,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (used) JournalLine else TerracottaDeep),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (used) Ink.copy(alpha = 0.35f) else TerracottaDeep,
            containerColor = Cream
        ),
        modifier = Modifier.padding(end = 6.dp, bottom = 6.dp)
    ) {
        Text(text, fontSize = 15.sp)
    }
}

@Composable
fun ChipRows(items: List<String>, usedIdx: Set<Int>, onPick: (Int) -> Unit) {
    Column {
        items.indices.chunked(4).forEach { rowIdx ->
            Row {
                for (i in rowIdx) {
                    WordChip(text = items[i], used = i in usedIdx) { onPick(i) }
                }
            }
        }
    }
}

@Composable
fun OptionButton(text: String, state: Int, onClick: () -> Unit) {
    // state: 0 normal, 1 picked-good, 2 picked-bad, 3 dimmed
    val bg = when (state) {
        1 -> GoodBg
        2 -> BadBg
        else -> Cream
    }
    val fg = when (state) {
        3 -> Ink.copy(alpha = 0.4f)
        else -> Ink
    }
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (state == 1) Olive else if (state == 2) Terracotta else JournalLine),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = fg, containerColor = bg),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Text(text, fontSize = 15.sp, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun PrimaryButton(text: String, olive: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (olive) Olive else Terracotta,
            contentColor = Cream
        )
    ) {
        Text(text)
    }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, TerracottaDeep),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TerracottaDeep, containerColor = Cream)
    ) {
        Text(text)
    }
}

// ------------------------------------------------------------------ exercises

@Composable
fun RecallQuestion(
    item: Cognate,
    distractors: List<Cognate>,
    context: String,
    store: MirrorStore,
    bump: () -> Unit,
    onDone: () -> Unit
) {
    val options = remember(item.en) { (distractors + item).shuffled() }
    var picked by remember(item.en) { mutableStateOf<Cognate?>(null) }
    JournalCard {
        Stamp("Calentamiento", olive = true)
        Spacer(Modifier.height(8.dp))
        Text("¿Qué significa «${item.en}»?", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))
        for (o in options) {
            val state = when {
                picked == null -> 0
                o.en == item.en && picked?.en == o.en -> 1
                picked?.en == o.en -> 2
                else -> 3
            }
            OptionButton(o.es, state) {
                if (picked == null) {
                    picked = o
                    val ok = o.en == item.en
                    store.logEvent("cognates", context, ok)
                    bump()
                }
            }
        }
        if (picked != null) {
            val ok = picked?.en == item.en
            Spacer(Modifier.height(4.dp))
            FeedbackBox(
                (if (ok) "¡Sí! " else "No: ") + "${item.en} = ${item.es}. ${item.rule}",
                good = ok
            )
            Spacer(Modifier.height(10.dp))
            PrimaryButton("Seguir →", olive = true) { onDone() }
        }
    }
}

/**
 * Word-order builder with the web app's pedagogy: the answer is never printed
 * before the attempt; the first mistake names WHAT is wrong (a word that does
 * not belong, or the first wrong position); the full sentence appears only
 * after success or a second failed attempt. One Mirror event per exercise,
 * logged at the first check, exactly like the web app.
 */
@Composable
fun SyntaxBuilder(
    item: SyntaxItem,
    context: String,
    store: MirrorStore,
    tts: TtsManager,
    bump: () -> Unit,
    onDone: () -> Unit
) {
    var placedIdx by remember(item.id) { mutableStateOf<List<Int>>(emptyList()) }
    var attempts by remember(item.id) { mutableStateOf(0) }
    var logged by remember(item.id) { mutableStateOf(false) }
    var done by remember(item.id) { mutableStateOf(false) }
    var feedback by remember(item.id) { mutableStateOf<String?>(null) }
    var feedbackGood by remember(item.id) { mutableStateOf(false) }

    val placedWords = placedIdx.map { item.tokens[it] }

    fun check() {
        if (done) return
        if (placedWords.isEmpty()) {
            // An empty check burns no attempt and logs no error — guidance only.
            feedback = "Todavía no pusiste ninguna palabra. Empieza por el sujeto: ¿quién hace la acción?"
            feedbackGood = false
            return
        }
        attempts++
        val ok = placedWords == item.words
        if (!logged) {
            store.logEvent(item.pattern, context, ok)
            logged = true
            bump()
        }
        if (ok) {
            done = true
            feedbackGood = true
            feedback = "¡Exacto! ${item.words.joinToString(" ")}\n${item.trap}"
            tts.speak(item.words.joinToString(" "), slow = false)
        } else if (attempts >= 2) {
            done = true
            feedbackGood = false
            feedback = "Todavía no. La frase es: ${item.words.joinToString(" ")}\n${item.trap}"
        } else {
            feedbackGood = false
            val wrongWord = placedWords.firstOrNull { it !in item.words }
            feedback = when {
                wrongWord != null ->
                    "Ojo con «$wrongWord»: esa palabra no pertenece a esta frase.\n${item.trap}"
                placedWords.size < item.words.size -> {
                    var posOk = true
                    var pi = 0
                    while (pi < placedWords.size) {
                        if (placedWords[pi] != item.words[pi]) { posOk = false; break }
                        pi++
                    }
                    if (posOk) {
                        "Vas bien, pero la frase no está completa: te faltan ${item.words.size - placedWords.size} palabra(s).\n${item.trap}"
                    } else {
                        "El orden falla: la palabra número ${pi + 1} («${placedWords[pi]}») no va en ese lugar.\n${item.trap}"
                    }
                }
                else -> {
                    var pj = 0
                    while (pj < item.words.size && placedWords[pj] == item.words[pj]) pj++
                    "El orden falla: la palabra número ${pj + 1} («${placedWords[pj]}») no va en ese lugar.\n${item.trap}"
                }
            }
        }
    }

    JournalCard(oliveCard = true) {
        Stamp("Construye la frase")
        Spacer(Modifier.height(8.dp))
        Text(item.prompt, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        NoteText("Constrúyela tú: toca las palabras en el orden del inglés. La respuesta no está escrita en ninguna parte — primero tu intento, después la explicación.")
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .background(Sand, RoundedCornerShape(8.dp))
                .border(1.dp, JournalLine, RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Text(
                if (placedWords.isEmpty()) "Tu frase aparecerá aquí…" else placedWords.joinToString(" "),
                style = MaterialTheme.typography.bodyLarge,
                color = if (placedWords.isEmpty()) Ink.copy(alpha = 0.45f) else Ink
            )
        }
        Spacer(Modifier.height(12.dp))
        ChipRows(item.tokens, placedIdx.toSet()) { i ->
            if (!done && i !in placedIdx) placedIdx = placedIdx + i
        }
        feedback?.let {
            Spacer(Modifier.height(6.dp))
            FeedbackBox(it, good = feedbackGood)
        }
        Spacer(Modifier.height(10.dp))
        Row {
            PrimaryButton("Revisar") { check() }
            Spacer(Modifier.width(8.dp))
            GhostButton("Borrar") { if (!done) placedIdx = emptyList() }
            if (done) {
                Spacer(Modifier.width(8.dp))
                PrimaryButton("Seguir →", olive = true) { onDone() }
            }
        }
    }
}

@Composable
fun ChoiceQuestion(
    item: TransferItem,
    context: String,
    store: MirrorStore,
    bump: () -> Unit,
    onDone: () -> Unit
) {
    val order = remember(item.id) { item.options.indices.shuffled() }
    var picked by remember(item.id) { mutableStateOf<Int?>(null) }
    JournalCard(oliveCard = true) {
        Stamp("Elige la forma correcta")
        Spacer(Modifier.height(8.dp))
        Text(item.prompt, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))
        for (i in order) {
            val state = when {
                picked == null -> 0
                picked == i && i == item.answer -> 1
                picked == i -> 2
                else -> 3
            }
            OptionButton(item.options[i], state) {
                if (picked == null) {
                    picked = i
                    store.logEvent(item.pattern, context, i == item.answer)
                    bump()
                }
            }
        }
        if (picked != null) {
            val ok = picked == item.answer
            FeedbackBox((if (ok) "¡Exacto! " else "Todavía no. ") + item.trap, good = ok)
            Spacer(Modifier.height(10.dp))
            PrimaryButton("Seguir →", olive = true) { onDone() }
        }
    }
}

@Composable
fun ListeningExercise(
    item: ListeningItem,
    context: String,
    store: MirrorStore,
    tts: TtsManager,
    support: Boolean,
    bump: () -> Unit,
    onDone: () -> Unit
) {
    val order = remember(item.id) { item.options.indices.shuffled() }
    var picked by remember(item.id) { mutableStateOf<Int?>(null) }
    JournalCard {
        Stamp("Escucha · el oído manda", olive = true)
        Spacer(Modifier.height(8.dp))
        Text("¿Qué oíste?", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        NoteText("Toca Escuchar las veces que quieras. La frase no está escrita: tus opciones son parecidas a propósito.")
        if (support && item.hint.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            NoteText("Pista: ${item.hint}")
        }
        Spacer(Modifier.height(10.dp))
        Row {
            PrimaryButton("🔊 Escuchar") { tts.speak(item.audio, slow = false) }
            Spacer(Modifier.width(8.dp))
            GhostButton("🐢 Lento") { tts.speak(item.audio, slow = true) }
        }
        Spacer(Modifier.height(12.dp))
        for (i in order) {
            val state = when {
                picked == null -> 0
                picked == i && i == item.answer -> 1
                picked == i -> 2
                else -> 3
            }
            OptionButton(item.options[i], state) {
                if (picked == null) {
                    picked = i
                    store.logEvent("listening", context, i == item.answer)
                    bump()
                }
            }
        }
        if (picked != null) {
            val ok = picked == item.answer
            FeedbackBox(
                if (ok) "¡Eso mismo sonó! ${item.note}"
                else "Eso no fue lo que sonó. Lo que sonó fue: ${item.audio} ${item.note}",
                good = ok
            )
            Spacer(Modifier.height(10.dp))
            PrimaryButton("Seguir →", olive = true) { onDone() }
        }
    }
}

// ------------------------------------------------------------------ La Guía

@Composable
fun MouthCanvas(shape: MouthShape, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(300f / 220f)
    ) {
        val sx = size.width / 300f
        val sy = size.height / 220f
        fun X(v: Float) = v * sx
        fun Y(v: Float) = v * sy
        val cx = X(150f)
        val cy = Y(108f)
        // Face with soft radial shading (the web guide's gradient face).
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFFFDF3DC), Color(0xFFFBEFD8), Color(0xFFEDD9AC)),
                center = Offset(cx, Y(84f)),
                radius = X(98f)
            ),
            center = Offset(cx, cy),
            radius = X(98f)
        )
        drawCircle(
            color = JournalLine,
            center = Offset(cx, cy),
            radius = X(98f),
            style = Stroke(width = 2f * sx)
        )
        // Cheek blush + chin shade.
        drawOval(Color(0xFFE8A37C).copy(alpha = 0.30f), topLeft = Offset(X(71f), Y(118f)), size = Size(X(34f), Y(20f)))
        drawOval(Color(0xFFE8A37C).copy(alpha = 0.30f), topLeft = Offset(X(195f), Y(118f)), size = Size(X(34f), Y(20f)))
        drawOval(Color(0xFFD9BE8E).copy(alpha = 0.35f), topLeft = Offset(X(116f), Y(161f)), size = Size(X(68f), Y(14f)))
        // Lips.
        drawOval(
            brush = Brush.verticalGradient(listOf(Color(0xFFD9713F), Terracotta, Color(0xFF9C4522))),
            topLeft = Offset(cx - X(shape.lipW / 2f), cy - Y(shape.lipH / 2f)),
            size = Size(X(shape.lipW), Y(shape.lipH))
        )
        // Mouth cavity.
        if (shape.innerW > 0f) {
            drawOval(
                brush = Brush.radialGradient(
                    listOf(Color(0xFF93381F), Color(0xFF571C0E)),
                    center = Offset(cx, cy),
                    radius = X(shape.innerW / 2f)
                ),
                topLeft = Offset(cx - X(shape.innerW / 2f), cy - Y(shape.innerH / 2f)),
                size = Size(X(shape.innerW), Y(shape.innerH))
            )
        }
        // Upper teeth.
        if (shape.teeth && shape.innerW > 0f) {
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFFFFFDF5), Color(0xFFEBDCB4))),
                topLeft = Offset(cx - X(shape.innerW * 0.46f), cy - Y(shape.lipH * 0.32f)),
                size = Size(X(shape.innerW * 0.92f), Y(shape.lipH * 0.30f)),
                cornerRadius = CornerRadius(X(3f), Y(3f))
            )
        }
        // Tongue.
        when (shape.tongue) {
            1 -> drawOval(
                brush = Brush.verticalGradient(listOf(Color(0xFFE59A8B), Color(0xFFC05B4C))),
                topLeft = Offset(cx - X(shape.innerW * 0.36f), cy + Y(shape.innerH * 0.08f)),
                size = Size(X(shape.innerW * 0.72f), Y(shape.innerH * 0.42f))
            )
            2 -> drawOval(
                brush = Brush.verticalGradient(listOf(Color(0xFFE59A8B), Color(0xFFC05B4C))),
                topLeft = Offset(cx - X(shape.innerW * 0.38f), cy - Y(shape.innerH * 0.46f)),
                size = Size(X(shape.innerW * 0.76f), Y(shape.innerH * 0.44f))
            )
            3 -> drawOval(
                brush = Brush.verticalGradient(listOf(Color(0xFFE59A8B), Color(0xFFC05B4C))),
                topLeft = Offset(cx - X(22f), cy + Y(shape.lipH * 0.10f)),
                size = Size(X(44f), Y(30f))
            )
        }
    }
}

/**
 * La Guía · Mira · Escucha · Copia — without the Copia recorder in this first
 * native scaffold: the mouth steps through the sounds WHILE the voice says the
 * word (one combined "Escuchar y ver" button), and the learner self-rates.
 * Ratings log pronunciation:<word> events; "Casi" logs nothing, like the web app.
 */
@Composable
fun GuideCard(
    wordKey: String,
    context: String,
    store: MirrorStore,
    tts: TtsManager,
    bump: () -> Unit,
    onRated: (() -> Unit)?
) {
    val gw = GUIDE_WORDS[wordKey] ?: return
    var step by remember(wordKey) { mutableStateOf(0) }
    var rated by remember(wordKey) { mutableStateOf(false) }
    var ratedNote by remember(wordKey) { mutableStateOf<String?>(null) }
    var playing by remember(wordKey) { mutableStateOf(false) }
    var playJob by remember(wordKey) { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    DisposableEffect(wordKey) {
        onDispose { playJob?.cancel() }
    }

    fun stopPlay() {
        playJob?.cancel()
        playJob = null
        playing = false
    }

    fun playWithVoice(slow: Boolean) {
        if (playing) {
            stopPlay()
            return
        }
        playJob = scope.launch {
            playing = true
            step = 0
            tts.speak(gw.word, slow = slow)
            val ms = if (slow) 2600L else 1500L
            for (i in 1 until gw.steps.size) {
                delay(ms)
                step = i
            }
            delay(ms)
            playing = false
        }
    }

    fun rate(kind: String) {
        if (rated) return
        rated = true
        when (kind) {
            "good" -> {
                store.logEvent("pronunciation:" + gw.word, context, true)
                ratedNote = "Anotado en tu diario: esta palabra salió."
            }
            "bad" -> {
                store.logEvent("pronunciation:" + gw.word, context, false)
                ratedNote = "Anotado con honestidad: todavía no. El Espejo solo lo nombrará si se repite en varias actividades."
            }
            else -> {
                ratedNote = "Anotado: casi. La práctica corta y seguida es la que la fija."
            }
        }
        bump()
        if (onRated != null) {
            scope.launch {
                delay(900)
                onRated()
            }
        }
    }

    JournalCard {
        Stamp("La Guía · Mira · Escucha · Copia")
        Spacer(Modifier.height(8.dp))
        Text(
            "${gw.word}  ${gw.ipa} · ${gw.es}",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(8.dp))
        val shape = MOUTH_SHAPES[gw.steps[step].shape] ?: MOUTH_SHAPES.getValue("rest")
        MouthCanvas(shape)
        Spacer(Modifier.height(6.dp))
        Text(
            "${gw.steps[step].s}  ·  ${gw.steps[step].ipa}",
            style = MaterialTheme.typography.titleMedium,
            color = TerracottaDeep,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            for (i in gw.steps.indices) {
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (i == step) 10.dp else 8.dp)
                        .background(if (i == step) Terracotta else JournalLine, CircleShape)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            gw.steps[step].cap,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            GhostButton("← Anterior") {
                stopPlay()
                step = maxOf(0, step - 1)
            }
            Spacer(Modifier.width(8.dp))
            PrimaryButton(if (playing) "⏸ Pausar" else "▶ Escuchar y ver") { playWithVoice(false) }
            Spacer(Modifier.width(8.dp))
            GhostButton("Siguiente →") {
                stopPlay()
                step = minOf(gw.steps.size - 1, step + 1)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            PrimaryButton("🐢 Lento: voz y boca", olive = true) { playWithVoice(true) }
        }
        Spacer(Modifier.height(14.dp))
        NoteText("Copia: escucha el modelo, repite en voz alta y júzgate tú. Sin notas inventadas: tu oído manda. (La grabación de voz llega en una próxima versión nativa.)")
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            PrimaryButton("Así es ✓", olive = true) { rate("good") }
            Spacer(Modifier.width(8.dp))
            GhostButton("Casi") { rate("mid") }
            Spacer(Modifier.width(8.dp))
            GhostButton("Todavía no") { rate("bad") }
        }
        ratedNote?.let {
            Spacer(Modifier.height(10.dp))
            FeedbackBox(it, good = true)
        }
        Spacer(Modifier.height(10.dp))
        NoteText("💡 ${gw.tip}")
    }
}

// ------------------------------------------------------------------ transfer & retention

@Composable
private fun TransferItemRunner(
    item: TransferItem,
    store: MirrorStore,
    settings: SettingsStore,
    tts: TtsManager,
    bump: () -> Unit,
    context: String = "transfer",
    onItemDone: () -> Unit
) {
    when (item.kind) {
        "guide" -> GuideCard(item.word, context, store, tts, bump, onRated = { onItemDone() })
        "choice" -> ChoiceQuestion(item, context, store, bump) { onItemDone() }
        "listening" -> {
            val li = LISTENING.firstOrNull { it.id == item.id }
            if (li != null) {
                ListeningExercise(li, context, store, tts, support = false, bump = bump) { onItemDone() }
            } else {
                PrimaryButton("Seguir →") { onItemDone() }
            }
        }
        else -> SyntaxBuilder(item.asSyntax(), context, store, tts, bump) { onItemDone() }
    }
}

/**
 * The real test: an unseen item, no hints. The result is read from the Mirror
 * event log (first transfer-context event for the pattern), then recorded
 * with recordTransfer — exactly the web app's runTransferCheck.
 */
@Composable
fun TransferCheck(
    patternKey: String,
    store: MirrorStore,
    settings: SettingsStore,
    tts: TtsManager,
    bump: () -> Unit,
    onDone: (Boolean?) -> Unit
) {
    val items = remember(patternKey) { transferItemsFor(patternKey) }
    val before = remember(patternKey) { store.events.size }
    var stage by remember(patternKey) { mutableStateOf(0) } // 0 exercise, 1 verdict
    var result by remember(patternKey) { mutableStateOf<Boolean?>(null) }

    if (items.isEmpty() || (items[0].kind == "guide" && !settings.guide)) {
        JournalCard {
            Text(
                "Prueba de transferencia: hoy no hay un ítem nuevo disponible para este punto. " +
                    "Lo practicado ya quedó anotado; la prueba llegará con material fresco.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(10.dp))
            PrimaryButton("Seguir →") { onDone(null) }
        }
        return
    }

    val item = items[Dates.dayIndex() % items.size]

    fun finish() {
        val res = store.lastTransferResultSince(patternKey, before)
        if (res != null) store.recordTransfer(patternKey, res)
        result = res
        stage = 1
        bump()
    }

    if (stage == 0) {
        Column {
            JournalCard {
                Text(
                    "Esto es la prueba de verdad: la misma idea, en una situación que no has visto. Sin pistas.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(12.dp))
            key(item.id) {
                TransferItemRunner(item, store, settings, tts, bump) { finish() }
            }
        }
    } else {
        val prog = store.progressOf(patternKey)
        JournalCard {
            when (result) {
                true -> {
                    Stamp("Prueba superada ✓")
                    Spacer(Modifier.height(8.dp))
                    Text("Lo lograste en una situación nueva, sin practicarla antes. Eso es aprender de verdad.")
                    Spacer(Modifier.height(6.dp))
                    if (prog != null && prog.status == "dominado") {
                        Text("Este patrón pasa a tu página de «Ya dominas» en el Espejo.")
                    } else {
                        NoteText("Estado en tu Espejo: Mejorando. Una prueba más, en otro día, y pasa a «Ya dominas».")
                    }
                }
                false -> {
                    Stamp("Todavía no", olive = true)
                    Spacer(Modifier.height(8.dp))
                    Text("Esta vez no salió — y no pasa nada. Sin castigo: la próxima práctica de este punto vuelve con pista, y la prueba se intenta de nuevo cuando toque.")
                }
                null -> {
                    Stamp("Sin resultado claro")
                    Spacer(Modifier.height(8.dp))
                    Text("No hubo una respuesta que contar: esta prueba no cuenta ni a favor ni en contra en tu Espejo.")
                }
            }
            Spacer(Modifier.height(10.dp))
            PrimaryButton("Seguir →") { onDone(result) }
        }
    }
}

/** Retention: a mastered pattern is quietly re-tested days later. */
@Composable
fun RetentionCheck(
    patternKey: String,
    store: MirrorStore,
    settings: SettingsStore,
    tts: TtsManager,
    bump: () -> Unit,
    onDone: (Boolean?) -> Unit
) {
    val items = remember(patternKey) { transferItemsFor(patternKey) }
    val before = remember(patternKey) { store.events.size }
    var stage by remember(patternKey) { mutableStateOf(0) }
    var result by remember(patternKey) { mutableStateOf<Boolean?>(null) }

    if (items.isEmpty() || (items[0].kind == "guide" && !settings.guide)) {
        JournalCard {
            Text(
                "Repaso de retención: hoy no hay un ítem nuevo para este punto. Se intentará otro día.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(10.dp))
            PrimaryButton("Seguir →") { onDone(null) }
        }
        return
    }

    val item = items[(Dates.dayIndex() + 1) % items.size]

    fun finish() {
        val res = store.lastTransferResultSince(patternKey, before)
        if (res != null) store.recordRetention(patternKey, res)
        result = res
        stage = 1
        bump()
    }

    if (stage == 0) {
        key(item.id) {
            TransferItemRunner(item, store, settings, tts, bump) { finish() }
        }
    } else {
        JournalCard {
            when (result) {
                true -> {
                    Stamp("Retención confirmada ✓")
                    Spacer(Modifier.height(8.dp))
                    Text("Esto era un repaso de retención: un patrón que ya dominabas, probado otra vez días después y sin aviso. Sigue ahí. Tu página de «Ya dominas» quedó confirmada hoy.")
                }
                false -> {
                    Stamp("Retención: hoy no salió", olive = true)
                    Spacer(Modifier.height(8.dp))
                    Text("Esto era un repaso de retención de un patrón que ya dominabas. Vuelve a Mejorando: la pista regresa y podrás dominarlo de nuevo. Sin castigo — olvidar un poco es parte de aprender; por eso existe el repaso.")
                }
                null -> {
                    Stamp("Sin resultado claro")
                    Spacer(Modifier.height(8.dp))
                    Text("El repaso de retención no contó: ni a favor ni en contra.")
                }
            }
            Spacer(Modifier.height(10.dp))
            PrimaryButton("Seguir →") { onDone(result) }
        }
    }
}

// ------------------------------------------------------------------ helpers

private fun distractFor(item: Cognate, seed: Int): List<Cognate> {
    val all = COGNATES.filter { it.en != item.en }
    return listOf(all[seed % all.size], all[(seed + 13) % all.size])
}

// ------------------------------------------------------------------ HOY

@Composable
fun HoyScreen(
    store: MirrorStore,
    settings: SettingsStore,
    tts: TtsManager,
    bump: () -> Unit,
    goEspejo: () -> Unit
) {
    val di = remember { Dates.dayIndex() }
    val plan = remember { DAY_PLANS[di % DAY_PLANS.size] }
    val hasActive = remember { store.activePatternsRanked().isNotEmpty() }
    val focusKey = remember { store.activePatternsRanked().firstOrNull()?.key ?: plan.focus }
    val info = remember { patternInfo(focusKey) }
    val focusMode = when {
        focusKey == "cognates" -> "cognates"
        focusKey == "listening" -> "listening"
        else -> "syntax"
    }
    val warmPool = remember {
        val rp = plan.ruleFocus?.let { rf -> COGNATES.filter { it.cat == rf } }
        if (rp != null && rp.size >= 4) rp else COGNATES
    }
    val wc1 = remember { warmPool[(di * 3) % warmPool.size] }
    val wc2 = remember {
        var w = warmPool[(di * 3 + 7) % warmPool.size]
        if (w.en == wc1.en) w = warmPool[(di * 3 + 11) % warmPool.size]
        w
    }
    val focusSyntax = remember {
        if (focusMode == "syntax") {
            val pool = poolFor(focusKey)
            listOf(pool[di % pool.size], pool[(di + 2) % pool.size]).distinctBy { it.id }
        } else {
            emptyList()
        }
    }
    val focusCognates = remember {
        if (focusMode == "cognates") {
            var cp = COGNATES.filter { it.cat == (plan.ruleFocus ?: "cion") }
            if (cp.size < 2) cp = COGNATES
            listOf(cp[di % cp.size], cp[(di + 3) % cp.size])
        } else {
            emptyList()
        }
    }
    val focusListening = remember {
        if (focusMode == "listening") {
            val lp = LISTENING.filter { !it.transfer }
            listOf(lp[di % lp.size], lp[(di + 1) % lp.size])
        } else {
            emptyList()
        }
    }
    val guideWord = remember {
        when {
            info.kind == "guide" -> info.word
            focusKey == "syntax_past_tense" -> "went"
            GUIDE_WORDS.containsKey(plan.guideWord) -> plan.guideWord
            else -> "ship"
        }
    }
    val retentionKey = remember { store.retentionDueKeys().firstOrNull() }
    val steps = remember {
        buildList {
            add("recall1"); add("recall2")
            focusSyntax.indices.forEach { add("syntax$it") }
            focusCognates.indices.forEach { add("cog$it") }
            focusListening.indices.forEach { add("lis$it") }
            add("guide"); add("transfer")
            if (retentionKey != null) add("retention")
            add("fin")
        }
    }

    var started by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(0) }
    fun advance() {
        phase++
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ViewTitle("Hoy", Dates.label() + " · una página nueva de tu diario.")
        if (!started) {
            JournalCard {
                if (hasActive) {
                    Text("Patrón de hoy: ${info.name}.", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Tu Espejo lo está mirando, así que la clase de hoy lo trabaja primero.")
                } else {
                    Text("Enfoque de hoy: ${plan.label} (día ${(di % 7) + 1} de la rotación semanal).", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    NoteText("Tu Espejo todavía no nombra patrones activos: necesita evidencia repetida, no un solo error.")
                }
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Empezar la clase de hoy →") { started = true }
            }
            Spacer(Modifier.height(14.dp))
            JournalCard {
                Stamp("La semana en tu diario", olive = true)
                Spacer(Modifier.height(8.dp))
                DAY_PLANS.forEachIndexed { i, d ->
                    val isToday = i == di % 7
                    Text(
                        (if (isToday) "Hoy: " else "") + "Día ${i + 1} · ${d.label}",
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            val step = steps.getOrElse(phase) { "fin" }
            val label = when {
                step.startsWith("recall") -> "Calentamiento"
                step.startsWith("syntax") || step.startsWith("cog") || step.startsWith("lis") ->
                    "Practica: ${info.name}"
                step == "guide" -> "La Guía"
                step == "transfer" -> "Prueba de transferencia — algo NUEVO, sin practicar"
                step == "retention" -> "Repaso sorpresa"
                else -> "Fin de la clase"
            }
            NoteText("Paso ${phase + 1} de ${steps.size} · $label")
            Spacer(Modifier.height(10.dp))
            key(step + phase) {
                when {
                    step == "recall1" -> RecallQuestion(wc1, distractFor(wc1, di + 1), "hoy", store, bump) { advance() }
                    step == "recall2" -> RecallQuestion(wc2, distractFor(wc2, di + 9), "hoy", store, bump) { advance() }
                    step.startsWith("syntax") -> {
                        val idx = step.removePrefix("syntax").toInt()
                        SyntaxBuilder(focusSyntax[idx], "hoy", store, tts, bump) { advance() }
                    }
                    step.startsWith("cog") -> {
                        val idx = step.removePrefix("cog").toInt()
                        RecallQuestion(focusCognates[idx], distractFor(focusCognates[idx], di + 4 + idx * 13), "hoy", store, bump) { advance() }
                    }
                    step.startsWith("lis") -> {
                        val idx = step.removePrefix("lis").toInt()
                        ListeningExercise(focusListening[idx], "hoy", store, tts, support = true, bump = bump) { advance() }
                    }
                    step == "guide" -> {
                        if (settings.guide) {
                            GuideCard(guideWord, "hoy", store, tts, bump, onRated = { advance() })
                        } else {
                            JournalCard {
                                Text("La Guía está apagada en Ajustes. Seguimos con la clase.")
                                Spacer(Modifier.height(10.dp))
                                PrimaryButton("Seguir →") { advance() }
                            }
                        }
                    }
                    step == "transfer" -> TransferCheck(focusKey, store, settings, tts, bump) { advance() }
                    step == "retention" -> RetentionCheck(retentionKey!!, store, settings, tts, bump) { advance() }
                    else -> {
                        JournalCard(oliveCard = true) {
                            Stamp("Página completa")
                            Spacer(Modifier.height(8.dp))
                            Text("Clase de hoy, terminada ✎", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(6.dp))
                            Text("Tus respuestas quedaron en tu diario. El Espejo solo nombrará un patrón cuando haya evidencia repetida en varias actividades — nunca por un solo error.")
                            Spacer(Modifier.height(12.dp))
                            Row {
                                PrimaryButton("Ver mi Espejo →", olive = true) { goEspejo() }
                                Spacer(Modifier.width(8.dp))
                                GhostButton("Repetir la clase") {
                                    started = false
                                    phase = 0
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ ESPEJO

@Composable
fun EspejoScreen(
    store: MirrorStore,
    settings: SettingsStore,
    tts: TtsManager,
    bump: () -> Unit
) {
    var practiceKey by remember { mutableStateOf<String?>(null) }
    var practiceStage by remember { mutableStateOf(0) } // 0 = mini-clase, 1 = prueba
    var miniIndex by remember { mutableStateOf(0) }
    var retentionKey by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ViewTitle("Espejo", "Esto es lo que veo en tu trabajo — evidencia primero, etiquetas después.")

        val pk = practiceKey
        if (pk != null) {
            val info = patternInfo(pk)
            TextButton(onClick = {
                practiceKey = null
                practiceStage = 0
                miniIndex = 0
            }) { Text("← Volver al Espejo") }
            JournalCard {
                val sup = store.supportLevel(pk)
                if (sup >= 1) {
                    Stamp("Mini-clase enfocada · Con apoyo nivel 1")
                    Spacer(Modifier.height(8.dp))
                    Text("Pista: ${info.explain}", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    NoteText("Primero practicas con esta ayuda; después viene la prueba, sin pista.")
                } else {
                    Stamp("Mini-clase enfocada · Sin apoyo")
                    Spacer(Modifier.height(8.dp))
                    Text("Ya lo lograste una vez en una situación nueva, así que esta práctica va sin pista. Después, la prueba.")
                }
            }
            Spacer(Modifier.height(12.dp))
            if (practiceStage == 0) {
                key(pk + miniIndex) {
                    when (info.kind) {
                        "guide" -> {
                            if (settings.guide) {
                                GuideCard(info.word, "espejo", store, tts, bump, onRated = { practiceStage = 1 })
                            } else {
                                JournalCard {
                                    Text("La Guía está apagada en Ajustes; vamos directo a la prueba.")
                                    Spacer(Modifier.height(10.dp))
                                    PrimaryButton("Ir a la prueba →") { practiceStage = 1 }
                                }
                            }
                        }
                        "cognates" -> {
                            val di = Dates.dayIndex()
                            val items = listOf(
                                COGNATES[(di + 2) % COGNATES.size],
                                COGNATES[(di + 11) % COGNATES.size]
                            )
                            RecallQuestion(
                                items[miniIndex],
                                distractFor(items[miniIndex], if (miniIndex == 0) 3 else 21),
                                "espejo", store, bump
                            ) {
                                if (miniIndex < items.size - 1) miniIndex++ else practiceStage = 1
                            }
                        }
                        "listening" -> {
                            val lp = LISTENING.filter { !it.transfer }
                            val di = Dates.dayIndex()
                            val items = listOf(lp[di % lp.size], lp[(di + 1) % lp.size])
                            ListeningExercise(
                                items[miniIndex], "espejo", store, tts,
                                support = store.supportLevel(pk) >= 1, bump = bump
                            ) {
                                if (miniIndex < items.size - 1) miniIndex++ else practiceStage = 1
                            }
                        }
                        else -> {
                            // Snapshotted once per practice session: the item set must not
                            // shift when new events are logged mid-practice.
                            val items = remember(pk) {
                                val pool = poolFor(pk)
                                val start = (Dates.dayIndex() + store.events.size) % pool.size
                                listOf(pool[start], pool[(start + 1) % pool.size]).distinctBy { it.id }
                            }
                            SyntaxBuilder(items[miniIndex], "espejo", store, tts, bump) {
                                if (miniIndex < items.size - 1) miniIndex++ else practiceStage = 1
                            }
                        }
                    }
                }
            } else {
                NoteText("Y ahora la prueba: una situación NUEVA, sin pistas.")
                Spacer(Modifier.height(10.dp))
                TransferCheck(pk, store, settings, tts, bump) {
                    practiceKey = null
                    practiceStage = 0
                    miniIndex = 0
                    bump()
                }
            }
            return@Column
        }

        val rk = retentionKey
        if (rk != null) {
            TextButton(onClick = { retentionKey = null }) { Text("← Volver al Espejo") }
            JournalCard {
                Stamp("Retención")
                Spacer(Modifier.height(8.dp))
                Text("Un ítem nuevo, sin pistas", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                NoteText("Si sale, tu dominio queda confirmado hoy. Si no, el punto vuelve a Mejorando con su pista — sin castigo.")
            }
            Spacer(Modifier.height(12.dp))
            RetentionCheck(rk, store, settings, tts, bump) {
                retentionKey = null
                bump()
            }
            return@Column
        }

        val pats = store.qualifiedPatterns()
        val active = pats.filter { store.statusOf(it.key) != "dominado" }
        val mastered = pats.filter { store.statusOf(it.key) == "dominado" }

        if (pats.isEmpty()) {
            JournalCard {
                Stamp("Diario en curso", olive = true)
                Spacer(Modifier.height(8.dp))
                Text("Sigue practicando — todavía no veo un patrón claro.", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text("El Espejo nombra un patrón solo cuando un mismo error aparece 3 veces o más en 2 actividades distintas. Un error suelto no dice nada de ti.")
                Spacer(Modifier.height(6.dp))
                NoteText("Respuestas registradas en tu diario: ${store.events.size}")
            }
            return@Column
        }

        if (active.isEmpty()) {
            JournalCard {
                Stamp("Todo en calma", olive = true)
                Spacer(Modifier.height(8.dp))
                Text("Sin patrones activos ahora mismo.", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text("Lo que antes te costaba está en tu página de «Ya dominas», aquí abajo.")
            }
            Spacer(Modifier.height(14.dp))
        }

        for (p in active) {
            val info = patternInfo(p.key)
            val st = store.statusOf(p.key)
            val sup = store.supportLevel(p.key)
            JournalCard {
                Stamp(if (st == "mejorando") "Mejorando" else "Patrón activo", olive = st == "mejorando")
                Spacer(Modifier.height(8.dp))
                Text(info.name, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(info.explain)
                Spacer(Modifier.height(6.dp))
                NoteText("Evidencia: ${p.errors} errores en ${p.contexts} actividades distintas.")
                if (st == "mejorando") {
                    NoteText("Pruebas en situaciones nuevas: ✓ (1 de 2) — la segunda debe ser en otro día.")
                } else {
                    NoteText("Aún sin una prueba superada en una situación nueva.")
                }
                NoteText(
                    if (sup == 0) "Tu próxima práctica va sin pista: ya demostraste que puedes."
                    else "Tu próxima práctica incluye una pista."
                )
                Spacer(Modifier.height(12.dp))
                Row {
                    PrimaryButton("Practicar esto") {
                        practiceKey = p.key
                        practiceStage = 0
                        miniIndex = 0
                    }
                    Spacer(Modifier.width(8.dp))
                    GhostButton("No es mi problema") {
                        store.dismissPattern(p.key)
                        bump()
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        if (mastered.isNotEmpty()) {
            Text(
                "Ya dominas — tu página de logros, con su evidencia:",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(10.dp))
            for (p in mastered) {
                val info = patternInfo(p.key)
                val prog = store.progressOf(p.key)
                JournalCard(oliveCard = true) {
                    Stamp("Ya dominas ✓✓")
                    Spacer(Modifier.height(8.dp))
                    Text(info.name, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Text("Lo probaste en situaciones nuevas: ✓✓" + (prog?.let { " (${it.days.size} días distintos)" } ?: "") + ".")
                    Spacer(Modifier.height(6.dp))
                    NoteText("Antes: ${p.errors} errores en ${p.contexts} actividades. La evidencia queda en tu diario: no fue suerte, fue práctica.")
                    if (prog != null && prog.retentionDays.isNotEmpty()) {
                        NoteText("Retención confirmada ${prog.retentionDays.size} vez/veces — la última: ${prog.retentionDays.last()}.")
                    } else {
                        NoteText("Retención: en unos días, Hoy traerá un repaso sorpresa de este punto, sin avisar.")
                    }
                    Spacer(Modifier.height(12.dp))
                    if (store.retentionDue(p.key)) {
                        PrimaryButton("Confirmar ahora que sigue ahí →", olive = true) {
                            retentionKey = p.key
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    GhostButton("Archivar esta entrada") {
                        store.dismissPattern(p.key)
                        bump()
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

// ------------------------------------------------------------------ CONVERSACIONES

@Composable
fun ConversacionesScreen(
    store: MirrorStore,
    settings: SettingsStore,
    tts: TtsManager,
    bump: () -> Unit
) {
    var convId by remember { mutableStateOf<String?>(null) }
    var turnIdx by remember { mutableStateOf(0) }
    var picked by remember { mutableStateOf<Int?>(null) }
    var doneTurns by remember { mutableStateOf<List<ConvTurn>>(emptyList()) }
    var listenIdx by remember { mutableStateOf(0) }
    val practiceItems = remember { LISTENING.filter { !it.transfer } }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ViewTitle("Conversaciones", "Escenas cortas de la vida real. Tú eliges las respuestas; el inglés te responde de verdad.")

        val cid = convId
        if (cid != null) {
            val conv = CONVERSATIONS.first { it.id == cid }
            if (turnIdx < conv.turns.size) {
                val turn = conv.turns[turnIdx]
                val order = remember(cid, turnIdx) { turn.opts.indices.shuffled() }
                TextButton(onClick = {
                    convId = null
                    turnIdx = 0
                    picked = null
                    doneTurns = emptyList()
                }) { Text("← Volver a las conversaciones") }
                if (doneTurns.isNotEmpty()) {
                    JournalCard {
                        doneTurns.forEachIndexed { di2, dt ->
                            if (di2 > 0) Spacer(Modifier.height(10.dp))
                            Text("🧑 " + dt.who, fontWeight = FontWeight.Bold, color = TerracottaDeep)
                            Text(dt.en)
                            Spacer(Modifier.height(4.dp))
                            Text("🗣 Tú: " + dt.opts.first { it.ok }.t, color = OliveDeep)
                            NoteText(dt.note)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
                JournalCard {
                    Text("🧑 ${turn.who}", fontWeight = FontWeight.Bold, color = TerracottaDeep)
                    Spacer(Modifier.height(4.dp))
                    Text(turn.en, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row {
                        PrimaryButton("🔊 Escuchar") { tts.speak(turn.en, slow = false) }
                        Spacer(Modifier.width(8.dp))
                        GhostButton("🐢 Lento") { tts.speak(turn.en, slow = true) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Tu respuesta:", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    for (i in order) {
                        val o = turn.opts[i]
                        val state = when {
                            picked == null -> 0
                            picked == i && o.ok -> 1
                            picked == i -> 2
                            else -> 3
                        }
                        OptionButton(o.t, state) {
                            if (picked == null) {
                                picked = i
                                store.logEvent(o.pattern, "conversaciones", o.ok)
                                bump()
                            }
                        }
                    }
                    if (picked != null) {
                        val o = turn.opts[picked!!]
                        FeedbackBox(turn.note, good = o.ok)
                        Spacer(Modifier.height(10.dp))
                        PrimaryButton("Seguir →", olive = true) {
                            doneTurns = doneTurns + turn
                            turnIdx++
                            picked = null
                        }
                    }
                }
            } else {
                JournalCard(oliveCard = true) {
                    Stamp("Postal completa")
                    Spacer(Modifier.height(8.dp))
                    Text("¡Conversación terminada! ${conv.title}", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text("Tus respuestas quedaron anotadas: si un mismo error se repite en varias actividades, el Espejo lo nombrará con su evidencia.")
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("Otra conversación") {
                        convId = null
                        turnIdx = 0
                        picked = null
                        doneTurns = emptyList()
                    }
                }
            }
            return@Column
        }

        JournalCard(oliveCard = true) {
            Stamp("Escucha · practica tu oído", olive = true)
            Spacer(Modifier.height(8.dp))
            if (listenIdx < practiceItems.size) {
                Text(
                    "Ítem ${listenIdx + 1} de ${practiceItems.size} — escucha y elige lo que oíste.",
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
                key(practiceItems[listenIdx].id) {
                    ListeningExercise(
                        practiceItems[listenIdx], "conversaciones", store, tts,
                        support = true, bump = bump
                    ) { listenIdx++ }
                }
            } else {
                Stamp("Oído entrenado ✓")
                Spacer(Modifier.height(8.dp))
                Text("Escucha completa por hoy. Tu oído queda registrado en el diario: si se repite un error de escucha en varias actividades, el Espejo lo nombrará — y la prueba final siempre será una frase que nunca has visto.")
                Spacer(Modifier.height(10.dp))
                GhostButton("Escuchar de nuevo") { listenIdx = 0 }
            }
        }
        Spacer(Modifier.height(14.dp))

        val recId = remember {
            store.activePatternsRanked().firstOrNull()
                ?.let { convForPattern(it.key) }
        }
        val recTitle = CONVERSATIONS.firstOrNull { it.id == recId }?.title
        if (recTitle != null) {
            NoteText("Tu Espejo sugiere practicar primero: $recTitle — trabaja directamente tu patrón activo.")
            Spacer(Modifier.height(8.dp))
        }
        for (c in CONVERSATIONS) {
            JournalCard {
                Text(c.title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(c.stamp)
                Spacer(Modifier.height(6.dp))
                NoteText("Con ${c.turns.first().who} · ${c.turns.size} turnos")
                if (c.id == recId) {
                    Spacer(Modifier.height(6.dp))
                    NoteText("★ Recomendada por tu Espejo")
                }
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Empezar →") {
                    convId = c.id
                    turnIdx = 0
                    picked = null
                    doneTurns = emptyList()
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

// ------------------------------------------------------------------ BIBLIOTECA

@Composable
fun BibliotecaScreen(
    store: MirrorStore,
    settings: SettingsStore,
    tts: TtsManager,
    bump: () -> Unit
) {
    var wordKey by remember { mutableStateOf<String?>(null) }
    var ruleKey by remember { mutableStateOf<String?>(null) }
    var ruleQ by remember { mutableStateOf<Cognate?>(null) }
    val di = remember { Dates.dayIndex() }
    val dayWord = remember { COGNATES[di % COGNATES.size] }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ViewTitle("Biblioteca", "Tu estantería: la Guía, las palabras puente y las fórmulas que abren el inglés.")

        JournalCard(oliveCard = true) {
            Stamp("Palabra del día")
            Spacer(Modifier.height(8.dp))
            Text("${dayWord.en} — ${dayWord.es}", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(dayWord.rule)
            Spacer(Modifier.height(10.dp))
            PrimaryButton("🔊 Escucharla") { tts.speak(dayWord.en, slow = false) }
        }
        Spacer(Modifier.height(14.dp))

        if (settings.guide) {
            JournalCard {
                Stamp("La Guía · Mira · Escucha · Copia")
                Spacer(Modifier.height(8.dp))
                val wk = wordKey
                if (wk == null) {
                    GUIDE_GROUPS.forEachIndexed { gi, g ->
                        if (gi > 0) Spacer(Modifier.height(10.dp))
                        Text(g.first, fontWeight = FontWeight.Bold, color = TerracottaDeep)
                        Spacer(Modifier.height(8.dp))
                        ChipRows(
                            g.second.map { GUIDE_WORDS[it]?.word ?: it },
                            emptySet()
                        ) { i -> wordKey = g.second[i] }
                    }
                    Spacer(Modifier.height(8.dp))
                    NoteText("También puedes practicar sonidos sueltos tocando cualquiera de las palabras.")
                } else {
                    TextButton(onClick = { wordKey = null }) { Text("← Volver al índice") }
                    GuideCard(wk, "biblioteca", store, tts, bump, onRated = null)
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        JournalCard {
            Stamp("Reglas puente · de cognados", olive = true)
            Spacer(Modifier.height(8.dp))
            ChipRows(RULES.map { it.chip }, emptySet()) { i ->
                ruleKey = RULES[i].id
                ruleQ = COGNATES.firstOrNull { it.cat == RULES[i].id }
            }
            val rk = ruleKey
            if (rk != null) {
                val rule = RULES.first { it.id == rk }
                Spacer(Modifier.height(12.dp))
                Text(rule.title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text("Fórmula: ${rule.formula}", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(rule.explain)
                Spacer(Modifier.height(8.dp))
                for ((es, en) in rule.examples) {
                    Text("$es → $en", style = MaterialTheme.typography.bodyMedium)
                }
                val q = ruleQ
                if (q != null) {
                    Spacer(Modifier.height(12.dp))
                    NoteText("Práctica rápida de esta regla:")
                    Spacer(Modifier.height(8.dp))
                    key(q.en + rk) {
                        RecallQuestion(q, distractFor(q, 15), "biblioteca", store, bump) {
                            ruleQ = null
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        JournalCard {
            Stamp("Puente de cognados · el catálogo", olive = true)
            Spacer(Modifier.height(8.dp))
            Text(
                "Los cognados son regalos: palabras que ya conoces con otra ropa. Tócalas para escucharlas.",
                style = MaterialTheme.typography.bodyMedium
            )
            (COGNATES.map { it.cat }.distinct()).forEach { cat ->
                val rule = RULES.firstOrNull { it.id == cat }
                Spacer(Modifier.height(12.dp))
                Text(rule?.title ?: cat, fontWeight = FontWeight.Bold, color = TerracottaDeep)
                Spacer(Modifier.height(6.dp))
                for (c in COGNATES.filter { it.cat == cat }) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(c.en, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(c.es, color = Ink.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
                        TextButton(onClick = { tts.speak(c.en, slow = false) }) { Text("🔊") }
                    }
                    HorizontalDivider(color = JournalLine)
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        JournalCard {
            Stamp("Fórmulas · pregunta y pasado", olive = true)
            Spacer(Modifier.height(8.dp))
            FORMULAS.forEachIndexed { i, f ->
                if (i > 0) Spacer(Modifier.height(10.dp))
                Text(f.first, fontWeight = FontWeight.Bold)
                NoteText(f.second)
            }
        }
    }
}

// ------------------------------------------------------------------ CAMINO

@Composable
fun CaminoScreen(
    store: MirrorStore,
    settings: SettingsStore,
    tts: TtsManager,
    bump: () -> Unit
) {
    var levelId by remember { mutableStateOf<String?>(null) }
    var itemIdx by remember { mutableStateOf(0) }
    var before by remember { mutableStateOf(0) }
    var finished by remember { mutableStateOf<Boolean?>(null) }
    var finishScore by remember { mutableStateOf(0) }

    fun unlocked(idx: Int): Boolean = idx == 0 || store.camino.contains(LEVELS[idx - 1].id)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ViewTitle("Camino", "Cuatro niveles, en orden. Cada prueba usa ítems que nunca has visto: pasarla es la prueba de que el nivel es tuyo.")

        val lid = levelId
        if (lid == null) {
            JournalCard(oliveCard = true) {
                Stamp("Tu progreso")
                Spacer(Modifier.height(8.dp))
                Text(
                    "${store.camino.size} de ${LEVELS.size} niveles completados",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(6.dp))
                NoteText("Regla de la casa: para pasar, acierta al menos 2 de 3 en el primer intento. Sin trampas y sin castigo: la prueba se puede repetir otro día con ítems distintos de la rotación.")
            }
            Spacer(Modifier.height(14.dp))
            LEVELS.forEachIndexed { i, lvl ->
                val isOpen = unlocked(i)
                val isDone = store.camino.contains(lvl.id)
                JournalCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(38.dp)
                                .background(
                                    if (isDone) Olive else if (isOpen) Terracotta else Color(0xFFB9A87F),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (isDone) "✓" else if (isOpen) "${i + 1}" else "🔒",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(lvl.title, style = MaterialTheme.typography.titleMedium)
                            NoteText("Nivel ${i + 1} · ${lvl.sub}")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when {
                            isDone -> "Completado ✓ — la prueba quedó en tu diario."
                            isOpen -> "Abierto. La prueba usa ítems nuevos, sin practicar."
                            else -> "Bloqueado: completa primero el nivel ${i}."
                        }
                    )
                    if (isOpen && !isDone) {
                        Spacer(Modifier.height(12.dp))
                        PrimaryButton("Hacer la prueba del Nivel ${i + 1} →") {
                            levelId = lvl.id
                            itemIdx = 0
                            before = store.events.size
                            finished = null
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            return@Column
        }

        val lvl = LEVELS.first { it.id == lid }
        TextButton(onClick = {
            levelId = null
            finished = null
        }) { Text("← Volver al Camino") }

        val f = finished
        if (f == null) {
            val item = TRANSFER.first { it.id == lvl.items[itemIdx] }
            NoteText("Prueba del Nivel ${lvl.n} · ítem ${itemIdx + 1} de ${lvl.items.size} — todo nuevo, sin pistas.")
            Spacer(Modifier.height(10.dp))
            key(item.id) {
                TransferItemRunner(item, store, settings, tts, bump, context = "camino") {
                    if (itemIdx < lvl.items.size - 1) {
                        itemIdx++
                    } else {
                        // First attempt per pattern since the test started.
                        val evs = store.events.drop(before)
                        var score = 0
                        val seen = mutableSetOf<String>()
                        for (e in evs) {
                            if (e.context == "camino" && seen.add(e.pattern)) {
                                if (e.correct) score++
                            }
                        }
                        finishScore = score
                        val pass = score >= (lvl.items.size * 2 + 2) / 3
                        if (pass && !store.camino.contains(lvl.id)) {
                            store.saveCamino(lvl.id, score)
                        }
                        finished = pass
                        bump()
                    }
                }
            }
        } else {
            JournalCard(oliveCard = f) {
                if (f) {
                    Stamp("Nivel ${lvl.n} completado ✓")
                    Spacer(Modifier.height(8.dp))
                    Text("Aciertos en el primer intento: $finishScore de ${lvl.items.size}.", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text("Con ítems que nunca habías visto: eso es transferencia, la prueba real de que el nivel es tuyo. El siguiente nivel quedó abierto.")
                } else {
                    Stamp("Todavía no", olive = true)
                    Spacer(Modifier.height(8.dp))
                    Text("Aciertos en el primer intento: $finishScore de ${lvl.items.size}. Se necesitan al menos ${(lvl.items.size * 2 + 2) / 3}.", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text("Sin castigo y sin perder lo aprendido: practica en Hoy y en el Espejo, y vuelve. Los errores de esta prueba ya quedaron anotados en tu diario — tu Espejo los está mirando.")
                }
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Volver al Camino") {
                    levelId = null
                    finished = null
                }
            }
        }
    }
}

// ------------------------------------------------------------------ AJUSTES

@Composable
fun AjustesScreen(
    store: MirrorStore,
    settings: SettingsStore,
    bump: () -> Unit
) {
    var speed by remember { mutableStateOf(settings.speed) }
    var guideOn by remember { mutableStateOf(settings.guide) }
    var showReset by remember { mutableStateOf(false) }
    var resetDone by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ViewTitle("Ajustes", "Todo vive en este teléfono. Nada sale de aquí: sin cuentas, sin nube, sin registro.")

        JournalCard {
            Stamp("La voz", olive = true)
            Spacer(Modifier.height(8.dp))
            Text("La voz es la de tu teléfono (motor de Google, si está instalado).", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text("Velocidad de la voz: ${"%.2f".format(speed)}×")
            Slider(
                value = speed,
                onValueChange = {
                    speed = it
                    settings.speed = it
                },
                valueRange = 0.7f..1.3f,
                steps = 5
            )
            NoteText("0.70× es muy lenta, para oír cada sonido; 1.30× es conversación real.")
        }
        Spacer(Modifier.height(14.dp))

        JournalCard {
            Stamp("La Guía")
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Guía de la boca (Mira · Escucha · Copia)", fontWeight = FontWeight.Bold)
                    NoteText("Si te distrae o te cansa la vista, apágala: el resto de la app sigue igual.")
                }
                Switch(
                    checked = guideOn,
                    onCheckedChange = {
                        guideOn = it
                        settings.guide = it
                    }
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        JournalCard {
            Stamp("Tu diario", olive = true)
            Spacer(Modifier.height(8.dp))
            Text("Borrar progreso y empezar de cero", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text("Borra tu diario completo: evidencia, patrones, «Ya dominas» y Camino. Esto no se puede deshacer.")
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Borrar todo mi progreso") { showReset = true }
            if (resetDone) {
                Spacer(Modifier.height(10.dp))
                FeedbackBox("Diario borrado. Aula empieza de cero, como el primer día.", good = true)
            }
        }
        Spacer(Modifier.height(14.dp))

        JournalCard {
            Text("Aula · versión nativa 0.1", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            NoteText("Versión nativa inicial: misma evidencia, mismas reglas, sin conexión a internet. La voz en la nube y la grabación de tu voz (Copia) llegan en versiones futuras.")
        }
    }

    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text("¿Borrar todo tu progreso?") },
            text = { Text("Se borra tu diario completo y no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    store.reset()
                    settings.reset()
                    speed = 1f
                    guideOn = true
                    showReset = false
                    resetDone = true
                    bump()
                }) { Text("Sí, borrar todo") }
            },
            dismissButton = {
                TextButton(onClick = { showReset = false }) { Text("Cancelar") }
            }
        )
    }
}
