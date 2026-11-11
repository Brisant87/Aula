package com.aula.nativeapp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Traducción Viva (v0.19) — a Biblioteca tool, like the
 * Laboratorio: the learner writes a phrase and La Profesora
 * answers with how it is really said, with voice.
 *
 * Honesty rules, same as everywhere in Aula:
 *  - the bank answers first, offline and without any key;
 *  - outside the bank, the learner's own Google Cloud key does
 *    the translating (the same key as the cloud voice);
 *  - a failure shows a plain message and NEVER an invented
 *    translation;
 *  - the audio always speaks the English, through TtsManager's
 *    normal routing for dynamic text.
 */
@Composable
fun TraduccionScreen(
    tts: TtsManager,
    voiceKeys: VoiceKeyStore,
    onBack: () -> Unit
) {
    var entrada by remember { mutableStateOf("") }
    var estado by remember { mutableStateOf<VivaEstado?>(null) }
    var cargando by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun traducir() {
        val texto = entrada
        val clave = voiceKeys.get()
        val necesitaNube = texto.isNotBlank() &&
            buscarEnBanco(texto) == null &&
            clave.isNotBlank()
        if (!necesitaNube) {
            // Bank hit, empty input or missing key: all instant,
            // all decided locally (the resolver never calls out).
            estado = resolverViva(texto, clave, null)
        } else {
            cargando = true
            scope.launch {
                val resultado = withContext(Dispatchers.IO) {
                    resolverViva(texto, clave, peticionTraduccionReal)
                }
                estado = resultado
                cargando = false
            }
        }
    }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        ViewTitle(
            "Traducción Viva",
            "Escribe lo que quieres decir —en español o en inglés— y te lo devuelvo como se dice de verdad, con su voz. Nada de traducir palabra por palabra."
        )
        GhostButton("← Volver a la Biblioteca") { onBack() }
        Spacer(Modifier.height(12.dp))

        JournalCard {
            Stamp("La profesora traduce")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = entrada,
                onValueChange = { entrada = it },
                label = { Text("Tu frase") },
                placeholder = { Text("Ejemplo: tengo sueño") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            PrimaryButton(
                "Traducir",
                olive = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !cargando
            ) { traducir() }
            Spacer(Modifier.height(6.dp))
            NoteText("El banco de frases de la profesora responde al instante, sin internet y sin clave: son las frases de cada día, dichas como las dice la gente.")
        }
        Spacer(Modifier.height(14.dp))

        if (cargando) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                CircularProgressIndicator(Modifier.size(22.dp))
                Spacer(Modifier.size(10.dp))
                NoteText("La profesora está buscando cómo se dice…")
            }
            Spacer(Modifier.height(14.dp))
        }

        when (val e = estado) {
            is VivaEstado.Traduccion -> {
                JournalCard(oliveCard = e.origen == VivaOrigen.BANCO) {
                    Stamp(
                        if (e.origen == VivaOrigen.BANCO) {
                            "Del banco de la profesora · sin internet"
                        } else {
                            "Traducido con tu clave de Google"
                        },
                        olive = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(e.traducida, style = MaterialTheme.typography.headlineSmall, color = Ink)
                    Spacer(Modifier.height(4.dp))
                    NoteText("Tu frase: «${e.original}»")
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton("🔊 Escucharla") { tts.speak(e.paraEscuchar, slow = false) }
                    Spacer(Modifier.height(6.dp))
                    NoteText(
                        if (e.origen == VivaOrigen.BANCO) {
                            "Así se dice en la calle: la frase entera, como la usa la gente, no palabra por palabra."
                        } else {
                            "Escúchala y repítela en voz alta: así se queda."
                        }
                    )
                }
            }

            is VivaEstado.Aviso -> {
                FeedbackBox(mensajeDeFallo(e.fallo), good = false)
            }

            null -> Unit
        }
    }
}

/** La Profesora's plain, warm wording for every non-result state. */
private fun mensajeDeFallo(fallo: VivaFallo): String = when (fallo) {
    VivaFallo.VACIO ->
        "Escribe primero una frase, la que sea: la que quieres decir en la tienda, en la calle o con un amigo."
    VivaFallo.NECESITA_CLAVE ->
        "Esa frase no está en mi banco. Para traducir frases nuevas uso tu clave de Google Cloud —la misma de la voz IA—: pégala una vez en Ajustes y vuelve. Mientras tanto, las frases del banco siguen funcionando sin clave."
    VivaFallo.API_NO_ACTIVADA ->
        "Tu clave está guardada, pero tu proyecto de Google Cloud aún no tiene activada la API de traducción (Cloud Translation API). Actívala en la consola de Google Cloud y pulsa Traducir otra vez."
    VivaFallo.REINTENTAR ->
        "No pude llegar a Google esta vez. Revisa tu conexión y pulsa Traducir otra vez: tu frase sigue escrita arriba."
}
