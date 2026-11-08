package com.aula.nativeapp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * La Gran Prueba (Build H) — Cognata's graded finals as the
 * Camino's capstone: after all four levels, one sitting of ten
 * unseen items spread across every pattern in the course, scored
 * exactly like the Camino itself (the first event of each item,
 * in the exam's own context). Passing (7 of 10) earns the
 * Constancia, kept in the Camino record as its "final" entry.
 *
 * Honest edges: the items are unseen the FIRST time; a retake is
 * labeled as a retake on its face, and the Constancia keeps the
 * best score rather than the latest.
 */
@Composable
fun FinalExamScreen(
    store: MirrorStore,
    settings: SettingsStore,
    tts: TtsManager,
    bump: () -> Unit,
    onExit: () -> Unit
) {
    // Ten items, round-robin across the patterns the transfer pool
    // covers, rotated by day so different days draw differently.
    val items = remember {
        val byPattern = TRANSFER.groupBy { it.pattern }.values.toList()
        val picked = mutableListOf<TransferItem>()
        var round = 0
        while (picked.size < 10) {
            var added = false
            for (group in byPattern) {
                if (picked.size >= 10) break
                val idx = (round + Dates.dayIndex()) % group.size
                val candidate = group[idx]
                if (candidate !in picked) {
                    picked.add(candidate)
                    added = true
                }
            }
            if (!added) break
            round++
        }
        picked.toList()
    }
    val previous = store.camino["final"]
    val isRetake = previous != null
    var itemIdx by remember { mutableStateOf(0) }
    var before by remember { mutableStateOf(store.events.size) }
    var correctCount by remember { mutableStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    var passedNow by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        TextButton(onClick = onExit) { Text("← Salir (puedes hacerla otro día)") }
        ViewTitle(
            "La Gran Prueba",
            "Diez piezas que nunca has visto, de todo el curso. Sin pistas y sin castigo: esto certifica lo que ya salió de ti una vez."
        )
        if (isRetake && !finished) {
            NoteText("Segunda vuelta: las piezas ya no son nuevas, y tu constancia guarda tu mejor nota (" + (previous?.score ?: 0) + " de " + items.size + ").")
            Spacer(Modifier.height(8.dp))
        }
        if (finished) {
            val best = store.camino["final"]
            JournalCard(oliveCard = passedNow) {
                if (passedNow) {
                    Stamp("🎓 Constancia de Aula", olive = true)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Gran Prueba superada: " + correctCount + " de " + items.size + ".",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Con piezas que nunca habías visto, de todos los temas del Camino. Fecha: " + Dates.label() + ". Esta constancia vive en tu diario y en tu Camino.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Stamp("Todavía no", olive = true)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Esta vuelta: " + correctCount + " de " + items.size + " (se aprueba con 7).",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Nada se borra y nada castiga: los intentos quedaron en tu Espejo, que ya sabe qué repasar. Puedes repetirla cuando quieras.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (best != null && best.done && !passedNow) {
                    Spacer(Modifier.height(6.dp))
                    NoteText("Tu constancia anterior sigue en pie: " + best.score + " de " + items.size + ", del " + best.date + ".")
                }
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Volver al Camino") { onExit() }
            }
            return@Column
        }

        val item = items[itemIdx]
        NoteText("Pieza " + (itemIdx + 1) + " de " + items.size + " — todo nuevo, sin pistas.")
        Spacer(Modifier.height(10.dp))
        key(item.id) {
            TransferItemRunner(item, store, settings, tts, bump, context = "final") {
                val evs = store.events.drop(before)
                val first = evs.firstOrNull { it.context == "final" }
                if (first?.correct == true) correctCount++
                if (itemIdx < items.size - 1) {
                    itemIdx++
                    before = store.events.size
                } else {
                    // correctCount already includes this last item.
                    // The "final" Camino entry is the Constancia: it
                    // exists only once earned, and afterwards keeps
                    // the BEST passing score, never a worse retake.
                    val total = correctCount
                    passedNow = total >= 7
                    val old = store.camino["final"]
                    if (passedNow && (old == null || total > old.score)) {
                        store.saveCamino("final", total)
                    }
                    bump()
                    finished = true
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
