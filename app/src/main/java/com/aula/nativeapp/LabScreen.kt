package com.aula.nativeapp

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/**
 * El Laboratorio de la profesora (Build F) — Cognata's Voice Lab
 * lessons, Aula style: graded pronunciation series living in the
 * Biblioteca, practiced Mira · Escucha · Copia, with the take card
 * written the way Cognata finally learned to write it — plain
 * words, what your mouth should do, ONE fix per take, and "Tu voz
 * sí se midió" whenever a real measurement happened.
 *
 * Honesty rules, same as everywhere in Aula:
 *  - a recognizer error measures NOTHING and logs NOTHING;
 *  - only the FIRST verdict per item per visit can reach the
 *    Mirror, and only for the set that has a real pattern behind
 *    it (the phantom-E family); the rest is pure practice;
 *  - when the app's own engine transcribed the take, the card
 *    says so.
 */

@Composable
fun LabScreen(
    store: MirrorStore,
    tts: TtsManager,
    bump: () -> Unit,
    onBack: () -> Unit
) {
    var current by remember { mutableStateOf<LabSet?>(null) }
    val set = current
    if (set == null) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            ViewTitle(
                "El Laboratorio",
                "Series cortas de pronunciación con la Profesora: un sonido por serie, a tu ritmo."
            )
            GhostButton("← Volver a la Biblioteca") { onBack() }
            Spacer(Modifier.height(12.dp))
            for (s in LAB_SETS) {
                JournalCard {
                    Stamp("Serie · " + s.items.size + " piezas")
                    Spacer(Modifier.height(8.dp))
                    Text(s.title, style = MaterialTheme.typography.titleMedium, color = Ink)
                    Spacer(Modifier.height(4.dp))
                    Text(s.focusEs, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton("Empezar la serie →", olive = true) { current = s }
                }
                Spacer(Modifier.height(12.dp))
            }
            NoteText("Cada pieza: mira qué hace tu boca, escucha el modelo y copia con tu voz. La app te dice qué escuchó — y un solo arreglo por toma.")
        }
    } else {
        LabSetRunner(set, store, tts, bump, onExit = { current = null })
    }
}

@Composable
private fun LabSetRunner(
    set: LabSet,
    store: MirrorStore,
    tts: TtsManager,
    bump: () -> Unit,
    onExit: () -> Unit
) {
    val ctx = LocalContext.current
    var idx by remember(set.id) { mutableStateOf(0) }
    val firsts = remember(set.id) { mutableMapOf<Int, Boolean>() }
    var takeNote by remember(set.id) { mutableStateOf<String?>(null) }
    var takeGood by remember(set.id) { mutableStateOf(false) }
    var checkArmed by remember(set.id) { mutableStateOf(false) }

    val probe = remember(set.id) { LiveSpeechProbe(ctx) }
    val srListening by probe.isListening.collectAsState()
    val srHeard by probe.lastRecognition.collectAsState()
    val srError by probe.micError.collectAsState()
    val srSource by probe.lastRecognitionSource.collectAsState()
    val srAsrBusy by probe.asrWorking.collectAsState()
    DisposableEffect(probe) { onDispose { probe.destroy() } }

    val finished = idx >= set.items.size
    val item = if (finished) null else set.items[idx]

    fun recordFirst(ok: Boolean) {
        if (!firsts.containsKey(idx)) {
            firsts[idx] = ok
            if (set.patternKey != null) {
                store.logEvent(set.patternKey, "lab", ok)
                bump()
            }
        }
    }

    fun tokensOf(s: String): List<String> =
        s.lowercase().split(Regex("[^a-z']+")).filter { it.isNotBlank() }

    LaunchedEffect(srHeard) {
        val heard = srHeard
        if (heard != null && checkArmed && item != null) {
            checkArmed = false
            val target = tokensOf(item.text)
            val heardTokens = tokensOf(heard)
            val missing = target.filter { it !in heardTokens }
            val ok = missing.isEmpty()
            recordFirst(ok)
            takeGood = ok
            takeNote = if (ok) {
                "Tu voz sí se midió ✓ Escuché «" + heard.trim() + "»: completa, con todo lo que pide."
            } else if (missing.isNotEmpty()) {
                "Tu voz sí se midió. Escuché «" + heard.trim() + "». Falta «" + missing.first() +
                    "»: " + item.mouth + " Un solo arreglo por toma: ese."
            } else {
                "Tu voz sí se midió. Escuché «" + heard.trim() + "», pero no era esto. Mira la boca y reintenta."
            } + if (srSource == "app") {
                " (Lo transcribió el motor de voz de la app, dentro de tu teléfono.)"
            } else {
                ""
            }
        }
    }
    LaunchedEffect(srError) {
        val err = srError
        if (err != null && checkArmed) {
            checkArmed = false
            takeGood = false
            takeNote = err + " No se midió nada, así que no cuenta: reintenta o júzgate tú abajo."
        }
    }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && item != null) {
            probe.clearRecognition()
            probe.clearMicError()
            checkArmed = true
            probe.startListening("Di en inglés: " + item.text)
        } else if (!granted) {
            takeGood = false
            takeNote = "Sin permiso de micrófono la app no puede escucharte. Júzgate tú con los botones de abajo: eso sí cuenta."
        }
    }

    fun startCheck() {
        if (item == null) return
        tts.stop()
        takeNote = null
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            probe.clearRecognition()
            probe.clearMicError()
            checkArmed = true
            probe.startListening("Di en inglés: " + item.text)
        } else {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        GhostButton("← Salir de la serie") { onExit() }
        Spacer(Modifier.height(10.dp))
        if (finished) {
            JournalCard(oliveCard = true) {
                Stamp("Serie terminada")
                Spacer(Modifier.height(8.dp))
                val oks = firsts.values.count { it }
                Text(
                    "A la primera: " + oks + " de " + set.items.size + ".",
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (set.patternKey != null) {
                        "Tus primeras notas de esta serie ya están en tu Espejo."
                    } else {
                        "Esta serie es práctica pura: nada se anota en tu Espejo, todo queda en tu oído."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(10.dp))
                PrimaryButton("Volver a las series", olive = true) { onExit() }
            }
            return@Column
        }
        val it = item!!
        JournalCard {
            Stamp(set.title + " · " + (idx + 1) + " de " + set.items.size)
            Spacer(Modifier.height(10.dp))
            Text(
                it.text,
                style = MaterialTheme.typography.headlineMedium,
                color = Ink,
                fontWeight = FontWeight.Bold
            )
            Text(it.glossEs, style = MaterialTheme.typography.bodyMedium, color = Ink.copy(alpha = 0.7f))
            Spacer(Modifier.height(10.dp))
            Text("👄 Tu boca: " + it.mouth, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text("👂 Escucha: " + it.listenFor, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            PrimaryButton("🔊 Escuchar el modelo") { tts.speak(it.text, slow = false) }
            Spacer(Modifier.height(8.dp))
            PrimaryButton(
                if (srListening) "👂 Escuchando… toca para parar" else "🎙 Copia con tu voz",
                olive = true
            ) {
                if (srListening) probe.stopListening() else startCheck()
            }
            if (srAsrBusy && checkArmed) {
                Spacer(Modifier.height(8.dp))
                NoteText("Un momento: el motor de voz de la app está escuchando tu toma, aquí dentro…")
            }
            takeNote?.let { note ->
                Spacer(Modifier.height(10.dp))
                FeedbackBox(note, good = takeGood)
            }
            if (!firsts.containsKey(idx)) {
                Spacer(Modifier.height(12.dp))
                NoteText("¿Sin micrófono? Júzgate tú — tu primera nota es la que cuenta:")
                Spacer(Modifier.height(6.dp))
                Row {
                    PrimaryButton("Me salió ✓", olive = true, modifier = Modifier.weight(1f)) {
                        recordFirst(true)
                        takeGood = true
                        takeNote = "Anotado por ti: te salió. Sigue con la siguiente."
                    }
                    Spacer(Modifier.width(8.dp))
                    GhostButton("Todavía no", modifier = Modifier.weight(1f)) {
                        recordFirst(false)
                        takeGood = false
                        takeNote = "Anotado por ti: todavía no. Mira la boca otra vez y sigue cuando quieras."
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row {
            if (idx > 0) {
                GhostButton("← Anterior", modifier = Modifier.weight(1f)) {
                    idx--
                    takeNote = null
                }
                Spacer(Modifier.width(8.dp))
            }
            PrimaryButton(
                if (idx == set.items.size - 1) "Terminar la serie →" else "Siguiente →",
                modifier = Modifier.weight(1f)
            ) {
                idx++
                takeNote = null
            }
        }
        Spacer(Modifier.height(8.dp))
        NoteText("Puedes saltar piezas y volver cuando quieras: el Laboratorio no castiga, entrena.")
        Spacer(Modifier.height(16.dp))
    }
}
