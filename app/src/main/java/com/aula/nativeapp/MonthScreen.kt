package com.aula.nativeapp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Tu mes en el diario (Build G) — Cognata's month review, written
 * as what Aula already is: a journal. One page per month, derived
 * entirely from the Mirror's own record (events + pattern
 * progress): days practiced, notes taken, what actually moved,
 * and the one focus the record itself picks for next month.
 * Nothing here is estimated or invented: a quiet month reads as a
 * quiet month.
 */
@Composable
fun MonthScreen(
    store: MirrorStore,
    onBack: () -> Unit
) {
    val stats = remember {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val monthStart = cal.timeInMillis
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        val monthEvents = store.events.filter { it.ts >= monthStart }
        val days = monthEvents.map { dayFmt.format(Date(it.ts)) }.distinct().size
        val correct = monthEvents.count { it.correct }
        val pct = if (monthEvents.isEmpty()) 0 else correct * 100 / monthEvents.size

        // Patterns that reached Dominado with activity this month.
        val moved = store.progress.entries
            .filter { (_, p) ->
                p.status == "dominado" &&
                    (p.days + p.retentionDays).any { Dates.parseMs(it) >= monthStart }
            }
            .map { it.key }

        // Next month's focus: the pattern with the most errors in
        // this month's own record (ties broken by recency — the
        // list is chronological, so later errors weigh the same;
        // maxByOrNull keeps the first max, which is fine).
        val errorsByPattern = monthEvents.filter { !it.correct }
            .groupingBy { it.pattern }.eachCount()
        val focus = errorsByPattern.maxByOrNull { it.value }?.key

        val monthName = SimpleDateFormat("MMMM", Locale("es", "ES")).format(Date())
        object {
            val events = monthEvents.size
            val daysPracticed = days
            val accuracy = pct
            val movedKeys = moved
            val focusKey = focus
            val title = monthName.replaceFirstChar { it.uppercase() }
        }
    }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        GhostButton("← Volver al Espejo") { onBack() }
        Spacer(Modifier.height(10.dp))
        ViewTitle(
            "Tu mes en el diario",
            stats.title + ", en una página: lo que el espejo vio, sin adornos."
        )
        JournalCard(oliveCard = true) {
            Stamp("El mes en números")
            Spacer(Modifier.height(8.dp))
            if (stats.events == 0) {
                Text(
                    "Este mes todavía no hay notas en tu diario. La primera práctica del mes abre la página.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    "Días de práctica: " + stats.daysPracticed + ".",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    "Notas en el diario: " + stats.events + " · aciertos: " + stats.accuracy + "%.",
                    style = MaterialTheme.typography.bodyLarge
                )
                NoteText("Cada nota es un intento real contado una vez — ni los fallos del teléfono ni los reintentos inflan estos números.")
            }
        }
        Spacer(Modifier.height(12.dp))
        JournalCard {
            Stamp("Lo que se movió")
            Spacer(Modifier.height(8.dp))
            if (stats.movedKeys.isEmpty()) {
                Text(
                    "Ningún patrón llegó a «Dominado» este mes. No es un fracaso: Dominado exige acertar con piezas nuevas en días distintos, y eso lleva su tiempo.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                for (key in stats.movedKeys) {
                    Text(
                        "✓ " + patternInfo(key).name + " — dominado.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Spacer(Modifier.height(4.dp))
                NoteText("Dominado significa que salió bien con material que nunca habías visto, en días distintos — no que repetiste lo mismo.")
            }
        }
        Spacer(Modifier.height(12.dp))
        JournalCard {
            Stamp("Tu foco para el mes que viene")
            Spacer(Modifier.height(8.dp))
            val focus = stats.focusKey
            if (focus == null) {
                Text(
                    "Sin errores este mes no hay foco que elegir: sigue con tu clase de Hoy y el diario dirá.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                val info = patternInfo(focus)
                Text(info.name, style = MaterialTheme.typography.titleMedium, color = Ink)
                Spacer(Modifier.height(4.dp))
                Text(info.explain, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                NoteText("Lo elige tu propio registro: es el punto con más errores de este mes. Practícalo desde tu Espejo o déjalo venir en la clase de Hoy.")
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
