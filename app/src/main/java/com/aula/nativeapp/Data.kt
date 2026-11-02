package com.aula.nativeapp

/*
 * Aula native — content data.
 *
 * Ported from the web app (~/workspace/aula-apk/app-assets/index.html).
 * Content: all 17 syntax items, the transfer pool (incl. la familia de la
 * «e» fantasma), all 46 cognates, 21 guide words (the web app's 20 + sister),
 * all 9 conversations, all 6 listening items, 13 formulas, 6 cognate rules,
 * 7 day plans and the 4 Camino levels.
 */

data class Cognate(
    val en: String, val es: String, val cat: String,
    val ph: String, val ex: String, val rule: String
)

data class SyntaxItem(
    val id: String, val pattern: String, val prompt: String,
    val words: List<String>, val tokens: List<String>,
    val rule: String, val trap: String
)

data class TransferItem(
    val id: String, val pattern: String,
    val kind: String = "syntax", // "syntax" | "choice" | "guide" | "listening"
    val prompt: String = "",
    val words: List<String> = emptyList(),
    val tokens: List<String> = emptyList(),
    val rule: String = "",
    val trap: String = "",
    val options: List<String> = emptyList(),
    val answer: Int = 0,
    val word: String = ""
) {
    fun asSyntax(): SyntaxItem = SyntaxItem(id, pattern, prompt, words, tokens, rule, trap)
}

data class GuideStep(val s: String, val ipa: String, val shape: String, val cap: String)

data class GuideWord(
    val key: String, val word: String, val ipa: String, val es: String,
    val tip: String, val steps: List<GuideStep>
)

/** Simplified front-mouth geometry per sound shape (the web app uses SVG paths;
 *  the native scaffold draws equivalent stylized shapes with Canvas primitives). */
data class MouthShape(
    val lipW: Float, val lipH: Float,
    val innerW: Float, val innerH: Float,
    val teeth: Boolean,
    val tongue: Int // 0 = none, 1 = bottom, 2 = up, 3 = out (th)
)

val MOUTH_SHAPES: Map<String, MouthShape> = mapOf(
    "rest" to MouthShape(176f, 22f, 96f, 8f, false, 0),
    "closed" to MouthShape(184f, 12f, 0f, 0f, false, 0),
    "round" to MouthShape(96f, 72f, 52f, 40f, false, 1),
    "wide" to MouthShape(216f, 56f, 132f, 20f, true, 0),
    "relaxed" to MouthShape(164f, 44f, 96f, 20f, false, 0),
    "open" to MouthShape(168f, 82f, 116f, 54f, true, 1),
    "teeth" to MouthShape(172f, 50f, 116f, 20f, true, 0),
    "th" to MouthShape(172f, 54f, 112f, 20f, true, 3),
    "tongueup" to MouthShape(160f, 50f, 104f, 26f, false, 2)
)

data class ConvOption(val t: String, val ok: Boolean, val pattern: String)
data class ConvTurn(val who: String, val en: String, val opts: List<ConvOption>, val note: String)
data class Conversation(val id: String, val title: String, val stamp: String, val turns: List<ConvTurn>)

data class ListeningItem(
    val id: String, val audio: String, val options: List<String>,
    val answer: Int, val hint: String, val note: String, val transfer: Boolean = false
)

data class RuleInfo(
    val id: String, val chip: String, val title: String,
    val formula: String, val explain: String, val examples: List<Pair<String, String>>
)

data class DayPlan(val focus: String, val label: String, val guideWord: String, val ruleFocus: String? = null)

data class LevelDef(val id: String, val n: Int, val title: String, val sub: String, val items: List<String>)

// ---------------------------------------------------------------- COGNATES

val COGNATES: List<Cognate> = listOf(
    Cognate("Hospital", "Hospital", "perfectos", "HÁS-pi-tol", "The nearest hospital is open 24 hours.", "Se escribe igual en los dos idiomas."),
    Cognate("Animal", "Animal", "perfectos", "Á-ni-mol", "The wild animal was safe.", "Misma palabra; cambia el énfasis."),
    Cognate("Idea", "Idea", "perfectos", "ai-DÍ-uh", "That is an excellent idea.", "Idéntica, con pronunciación nueva."),
    Cognate("Problem", "Problema", "perfectos", "PRÁ-blem", "That is not a problem at all.", "Solo quita la «a» final."),
    Cognate("Decision", "Decisión", "cion", "di-SÍ-zhon", "It was a tough decision.", "-ción se convierte en -tion."),
    Cognate("Action", "Acción", "cion", "ÁK-shon", "Take action immediately.", "-ción se convierte en -tion."),
    Cognate("Information", "Información", "cion", "in-for-MÉI-shon", "I need more information.", "-ción se convierte en -tion. Nunca lleva «s»."),
    Cognate("Attention", "Atención", "cion", "uh-TÉN-shon", "Pay close attention.", "-ción se convierte en -tion."),
    Cognate("Community", "Comunidad", "dad", "kuh-MIÚ-ni-ti", "Our community is very supportive.", "-dad se convierte en -ty."),
    Cognate("Activity", "Actividad", "dad", "ak-TÍ-vi-ti", "Physical activity is essential.", "-dad se convierte en -ty."),
    Cognate("Opportunity", "Oportunidad", "dad", "á-per-TÚ-ni-ti", "This is a golden opportunity.", "-dad se convierte en -ty."),
    Cognate("Finally", "Finalmente", "mente", "FÁI-nuh-li", "Finally, the train arrived.", "-mente se convierte en -ly."),
    Cognate("Rapidly", "Rápidamente", "mente", "RÁ-pid-li", "The city grew rapidly.", "-mente se convierte en -ly."),
    Cognate("Famous", "Famoso", "oso", "FÉI-mos", "He is a famous musician.", "-oso se convierte en -ous."),
    Cognate("Delicious", "Delicioso", "oso", "di-LÍ-shos", "The dinner was delicious.", "-oso se convierte en -ous."),
    Cognate("Actually", "En realidad / de hecho", "falsos", "ÁK-shu-uh-li", "Actually, I disagree with that point.", "¡Trampa! NO significa «actualmente»; eso es «currently»."),
    Cognate("Exit", "Salida", "falsos", "ÉK-sit", "Take the emergency exit.", "¡Trampa! NO significa «éxito»; eso es «success»."),
    Cognate("Library", "Biblioteca", "falsos", "LÁI-bre-ri", "I study at the library.", "¡Trampa! «Librería» (tienda) es «bookstore»."),
    Cognate("Conversation", "Conversación", "cion", "kon-ver-SÉI-shon", "We had a long conversation.", "-ción se convierte en -tion."),
    Cognate("Reality", "Realidad", "dad", "ri-Á-li-ti", "Face reality with confidence.", "-dad se convierte en -ty."),
    Cognate("Directly", "Directamente", "mente", "di-RÉKT-li", "Speak to me directly.", "-mente se convierte en -ly."),
    Cognate("Curious", "Curioso", "oso", "KIÚ-ri-os", "Cats are naturally curious.", "-oso se convierte en -ous."),
    Cognate("Nervous", "Nervioso", "oso", "NÉR-vos", "Don’t feel nervous on stage.", "-oso se convierte en -ous."),
    Cognate("Embarrassed", "Avergonzado", "falsos", "em-BÁ-rast", "I felt embarrassed when I dropped the tray.", "¡Trampa! NO significa «embarazada»; eso es «pregnant»."),
    Cognate("Carpet", "Alfombra", "falsos", "KÁR-pet", "The living room has a soft carpet.", "¡Trampa! NO significa «carpeta»; eso es «folder»."),
    Cognate("Doctor", "Doctor / médico", "perfectos", "DÓK-tor", "The doctor is very kind.", "Se escribe igual; en inglés suena más corto."),
    Cognate("Actor", "Actor", "perfectos", "ÁK-tor", "The actor is famous in Spain.", "Idéntica en los dos idiomas."),
    Cognate("Hotel", "Hotel", "perfectos", "hou-TÉL", "The hotel is near the beach.", "Igual escrita; el énfasis va al final."),
    Cognate("Taxi", "Taxi", "perfectos", "TÁK-si", "We need a taxi to the airport.", "La misma palabra en casi todo el mundo."),
    Cognate("Radio", "Radio", "perfectos", "RÉI-di-ou", "Turn on the radio, please.", "Se escribe igual; se lee «réidiou»."),
    Cognate("Piano", "Piano", "perfectos", "pi-Á-nou", "She plays the piano every day.", "Idéntica; cambia solo el ritmo."),
    Cognate("Video", "Video / vídeo", "perfectos", "VÍ-di-ou", "Watch this video carefully.", "Igual escrita en los dos idiomas."),
    Cognate("Chocolate", "Chocolate", "perfectos", "CHÓ-ko-let", "I love dark chocolate.", "En inglés tiene una sílaba menos: suena «chóklet»."),
    Cognate("Nation", "Nación", "cion", "NÉI-shon", "It is a proud nation.", "-ción se convierte en -tion."),
    Cognate("Education", "Educación", "cion", "e-yu-KÉI-shon", "Education opens doors.", "-ción se convierte en -tion."),
    Cognate("Vacation", "Vacaciones", "cion", "vei-KÉI-shon", "We are on vacation in July.", "En inglés suele ir en singular: «vacation»."),
    Cognate("University", "Universidad", "dad", "iu-ni-VÉR-si-ti", "The university is very old.", "-dad se convierte en -ty."),
    Cognate("City", "Ciudad", "dad", "SÍ-ti", "This city is beautiful.", "-dad se convierte en -ty; la «c» suena «s»."),
    Cognate("Quality", "Calidad", "dad", "KUÓ-li-ti", "Quality matters more than speed.", "-dad se convierte en -ty."),
    Cognate("Normally", "Normalmente", "mente", "NÓR-muh-li", "I normally walk to work.", "-mente se convierte en -ly."),
    Cognate("Totally", "Totalmente", "mente", "TÓU-tuh-li", "I totally agree with you.", "-mente se convierte en -ly."),
    Cognate("Possibly", "Posiblemente", "mente", "PÓ-si-bli", "Possibly it rains tomorrow.", "-mente se convierte en -ly."),
    Cognate("Generous", "Generoso", "oso", "YÉ-ne-ros", "He is a generous friend.", "-oso se convierte en -ous."),
    Cognate("Precious", "Precioso", "oso", "PRÉ-shos", "Time is precious.", "-oso se convierte en -ous."),
    Cognate("Large", "Grande", "falsos", "LARCH", "It is a large house.", "¡Trampa! NO significa «largo»; eso es «long»."),
    Cognate("Success", "Éxito", "falsos", "sok-SÉS", "The plan was a success.", "¡Trampa! Es «éxito», no «salida» (eso es «exit»).")
)

val RULES: List<RuleInfo> = listOf(
    RuleInfo("perfectos", "Iguales", "Cognados perfectos", "hospital → hospital",
        "Miles de palabras se escriben igual en español e inglés; solo cambia la pronunciación.",
        listOf("Hospital" to "Hospital", "Animal" to "Animal", "Idea" to "Idea")),
    RuleInfo("cion", "-ción → -tion", "La regla de oro: -ción → -tion", "-ción → -tion",
        "Casi cualquier palabra en -ción se convierte cambiando la «c» por «t». Se pronuncia como «shon» suave.",
        listOf("Información" to "Information", "Acción" to "Action", "Atención" to "Attention", "Decisión" to "Decision")),
    RuleInfo("dad", "-dad → -ty", "De -dad a -ty", "-dad → -ty",
        "Las palabras de cualidad que terminan en -dad pasan a -ty en inglés de forma sistemática.",
        listOf("Comunidad" to "Community", "Actividad" to "Activity", "Oportunidad" to "Opportunity")),
    RuleInfo("mente", "-mente → -ly", "Adverbios: -mente → -ly", "-mente → -ly",
        "El español añade -mente y el inglés añade -ly a la raíz del adjetivo.",
        listOf("Finalmente" to "Finally", "Rápidamente" to "Rapidly")),
    RuleInfo("oso", "-oso → -ous", "Adjetivos: -oso → -ous", "-oso → -ous",
        "Los adjetivos en -oso se convierten en -ous; suena como «as» relajado.",
        listOf("Famoso" to "Famous", "Delicioso" to "Delicious")),
    RuleInfo("falsos", "Falsos amigos", "Falsos amigos (¡cuidado!)", "parecen ≠ significan",
        "Palabras que parecen cognados pero significan otra cosa. Memoriza las trampas.",
        listOf("Actually" to "En realidad", "Exit" to "Salida", "Library" to "Biblioteca"))
)

val FORMULAS: List<Pair<String, String>> = listOf(
    "I would like ___ , please." to "Me gustaría ___, por favor.",
    "I am looking for ___." to "Estoy buscando ___.",
    "How do you say ___ in English?" to "¿Cómo se dice ___ en inglés?",
    "What does ___ mean?" to "¿Qué significa ___?",
    "Can you say that slowly, please?" to "¿Puedes decirlo despacio, por favor?",
    "Yesterday, I ___ (verbo en pasado)." to "Ayer, yo ___ (go → went, buy → bought).",
    "Last night, we ___." to "Anoche, nosotros ___.",
    "Do you have ___?" to "¿Tienes ___? — la pregunta abre con «Do».",
    "Do you ___ every day?" to "¿... todos los días? — rutina en presente: Do + verbo base.",
    "Does he/she ___?" to "¿Él/ella...? — con he/she la pregunta abre con «Does» y el verbo va en base.",
    "Did you ___ yesterday?" to "¿... ayer? — «Did» ya marca el pasado: Did you go…? (verbo en base).",
    "Where do you ___?" to "¿Dónde...? — palabra Wh + do + sujeto + verbo.",
    "I don't ___ on Sundays." to "No... los domingos — negación en presente: don't + verbo base."
)

// ---------------------------------------------------------------- SYNTAX

val SYNTAX: List<SyntaxItem> = listOf(
    SyntaxItem("s1", "syntax_word_order", "Tengo un carro rojo.",
        listOf("I", "have", "a", "red", "car"), listOf("car", "red", "I", "a", "have", "the"),
        "Adjetivo ANTES del sustantivo", "En español el color va después («carro rojo»); en inglés el adjetivo va siempre antes: «red car»."),
    SyntaxItem("s2", "syntax_word_order", "No me gusta el café frío.",
        listOf("I", "do", "not", "like", "cold", "coffee"), listOf("coffee", "not", "like", "I", "cold", "do", "no"),
        "Auxiliar «do not» obligatorio", "En inglés nunca se dice «I no like»; la negación necesita el auxiliar «do not»."),
    SyntaxItem("s3", "syntax_word_order", "¿Tienes una pregunta importante?",
        listOf("Do", "you", "have", "an", "important", "question"), listOf("an", "Do", "question", "have", "you", "important", "a"),
        "La pregunta abre con «Do»", "En español basta subir el tono; en inglés la pregunta empieza con el auxiliar «Do»."),
    SyntaxItem("s5", "syntax_word_order", "Me gustaría una ensalada fresca, por favor.",
        listOf("I", "would", "like", "a", "fresh", "salad", "please"), listOf("salad", "like", "a", "would", "fresh", "I", "please", "want"),
        "Cortesía natural: «I would like»", "«I want» suena rudo; se usa «I would like», y «fresh» va antes de «salad»."),
    SyntaxItem("s4", "syntax_word_order", "Yo trabajo en una oficina grande.",
        listOf("I", "work", "in", "a", "big", "office"), listOf("office", "big", "a", "work", "in", "I", "the"),
        "Adjetivo ANTES del sustantivo: «big office»", "«Oficina grande» se dice «big office»: en inglés el tamaño va antes de la cosa."),
    SyntaxItem("s7", "syntax_past_tense", "Ayer fui al trabajo.",
        listOf("Yesterday", "I", "went", "to", "work"), listOf("work", "went", "Yesterday", "to", "I", "go"),
        "Pasado con «yesterday»: go → went", "Con «yesterday» el verbo DEBE cambiar: «go» se convierte en «went». Nunca «Yesterday I go»."),
    SyntaxItem("s8", "syntax_past_tense", "Ella visitó a su familia la semana pasada.",
        listOf("She", "visited", "her", "family", "last", "week"), listOf("week", "visits", "her", "She", "last", "family", "visited"),
        "Pasado regular (-ed): visit → visited", "Con «last week» el verbo va en pasado: «visit» + «-ed» = «visited». «She visits» sería presente."),
    SyntaxItem("s9", "syntax_past_tense", "Anoche vimos una película.",
        listOf("Last", "night", "we", "watched", "a", "movie"), listOf("movie", "watched", "we", "a", "Last", "night", "watch"),
        "Pasado con «last night»: watch → watched", "«Last night» manda el verbo al pasado: «watch» se convierte en «watched». La ficha «watch» es el presente disfrazado."),
    SyntaxItem("s10", "syntax_past_tense", "Ellos compraron pan ayer.",
        listOf("They", "bought", "bread", "yesterday"), listOf("yesterday", "buy", "bread", "They", "bought"),
        "Pasado irregular: buy → bought", "«Buy» es irregular: su pasado NO es «buyed», es «bought»."),
    SyntaxItem("s16", "syntax_past_tense", "Él llegó tarde ayer.",
        listOf("He", "arrived", "late", "yesterday"), listOf("yesterday", "arrive", "late", "He", "arrived"),
        "Pasado regular (-ed): arrive → arrived", "Con «yesterday» el verbo cambia: «arrive» + «-ed» = «arrived» (la «e» final se cae)."),
    SyntaxItem("s17", "syntax_past_tense", "Yo no fui a la fiesta.",
        listOf("I", "did", "not", "go", "to", "the", "party"), listOf("party", "go", "the", "to", "I", "did", "went", "not"),
        "Negación en pasado: «did not» + verbo base", "Después de «did not» el verbo vuelve a su forma base: se dice «did not go», NUNCA «did not went»."),
    SyntaxItem("s11", "syntax_present_questions", "¿Trabajas los sábados?",
        listOf("Do", "you", "work", "on", "Saturdays"), listOf("Saturdays", "work", "Do", "on", "you", "works"),
        "Pregunta en presente: «Do» + verbo base", "Después de «Do» el verbo va en su forma base: «work», jamás «works»."),
    SyntaxItem("s12", "syntax_present_questions", "¿Ella vive cerca de aquí?",
        listOf("Does", "she", "live", "near", "here"), listOf("here", "Does", "live", "near", "she", "lives"),
        "Con he/she, la pregunta abre con «Does»", "Con «Does» el verbo pierde su -s: «lives» se convierte en «live»."),
    SyntaxItem("s13", "syntax_present_questions", "¿Dónde viven ellos?",
        listOf("Where", "do", "they", "live"), listOf("live", "they", "Where", "do", "does"),
        "Orden fijo: Wh + do + sujeto + verbo", "La pregunta con «dónde» mantiene el orden: «Where do they live?» — pregunta primero, verbo base después."),
    SyntaxItem("s14", "syntax_present_questions", "Él no toma café.",
        listOf("He", "does", "not", "drink", "coffee"), listOf("coffee", "drink", "He", "not", "does", "drinks"),
        "Negación con he/she: «does not» + verbo base", "Con «does not» el verbo va en base: «drink», no «drinks». La -s ya la lleva «does»."),
    SyntaxItem("s15", "syntax_present_questions", "Nosotros estudiamos inglés cada día.",
        listOf("We", "study", "English", "every", "day"), listOf("day", "study", "We", "every", "English", "studies"),
        "Presente de rutina: sujeto + verbo base", "Con «we» el verbo va sin -s: «We study». La -s es solo para he/she/it.")
)

/** Transfer items: NEVER used in guided practice (same as the web app). */
val TRANSFER: List<TransferItem> = listOf(
    TransferItem("t1", "syntax_past_tense", prompt = "Anoche comí pizza.",
        words = listOf("Last", "night", "I", "ate", "pizza"), tokens = listOf("pizza", "eat", "I", "ate", "Last", "night"),
        rule = "Pasado: eat → ate", trap = "«Eat» también es irregular: su pasado es «ate»."),
    TransferItem("t2", "syntax_past_tense", prompt = "Ella compró un libro ayer.",
        words = listOf("She", "bought", "a", "book", "yesterday"), tokens = listOf("yesterday", "buy", "book", "She", "a", "bought"),
        rule = "Pasado: buy → bought", trap = "Con «yesterday» el verbo cambia: «buy» → «bought»."),
    TransferItem("t3", "syntax_past_tense", prompt = "Ayer jugamos fútbol.",
        words = listOf("Yesterday", "we", "played", "soccer"), tokens = listOf("soccer", "play", "Yesterday", "we", "played"),
        rule = "Pasado regular: play → played", trap = "«Play» es regular: pasado = «played»."),
    TransferItem("t8", "syntax_past_tense", prompt = "Anoche ellos cenaron temprano.",
        words = listOf("Last", "night", "they", "had", "dinner", "early"), tokens = listOf("early", "had", "have", "they", "dinner", "Last", "night"),
        rule = "Pasado: have → had", trap = "«Have» también es irregular: su pasado es «had»."),
    TransferItem("t4", "syntax_word_order", prompt = "Quiero la camisa azul.",
        words = listOf("I", "want", "the", "blue", "shirt"), tokens = listOf("shirt", "blue", "I", "the", "want", "azul"),
        rule = "El color va ANTES: «blue shirt»", trap = "En inglés se dice «blue shirt»: el adjetivo siempre antes del sustantivo."),
    TransferItem("t5", "syntax_present_questions", prompt = "¿María juega al tenis?",
        words = listOf("Does", "Maria", "play", "tennis"), tokens = listOf("tennis", "play", "Does", "Maria", "plays"),
        rule = "Pregunta con «Does» + verbo base", trap = "Con «Does», el verbo vuelve a su forma base: «play», no «plays»."),
    TransferItem("t6", "cognates", kind = "choice", prompt = "«Información» en inglés se dice…",
        options = listOf("Informations", "Information", "Informatics"), answer = 1,
        rule = "-ción → -tion, sin «s» final", trap = "«Information» nunca lleva «s» en inglés, aunque hables de muchas informaciones."),
    TransferItem("t9", "syntax_past_tense", prompt = "Anoche vimos una película.",
        words = listOf("Last", "night", "we", "saw", "a", "movie"), tokens = listOf("movie", "see", "we", "saw", "a", "Last", "night"),
        rule = "Pasado: see → saw", trap = "«See» es irregular: su pasado es «saw», nunca «seed» ni «saw» con «see»."),
    TransferItem("t10", "syntax_present_questions", kind = "choice",
        prompt = "¿Cuál es la pregunta correcta para «Él trabaja aquí»?",
        options = listOf("Does he works here?", "Is he work here?", "Does he work here?"), answer = 2,
        rule = "«Does» + verbo base", trap = "Con «Does», el verbo pierde la «s»: «work», no «works». La «s» ya la lleva «does»."),
    TransferItem("t11", "syntax_word_order", prompt = "El perro pequeño es feliz.",
        words = listOf("The", "small", "dog", "is", "happy"), tokens = listOf("dog", "happy", "The", "is", "small", "little"),
        rule = "El adjetivo va ANTES: «small dog»", trap = "«Perro pequeño» se invierte: «small dog». Y «happy» cierra la frase, después de «is»."),
    TransferItem("t12", "conversation_turn", kind = "choice",
        prompt = "En recepción te dicen: «Your room is ready». ¿Qué respondes?",
        options = listOf("I ready, room thank.", "Thank you very much.", "Room ready is, thanks."), answer = 1,
        rule = "Respuesta completa y cortés", trap = "En conversación gana la frase completa y cortés: «Thank you very much». Traducir palabra por palabra desordena todo."),
    TransferItem("t13", "conversation_turn", kind = "choice",
        prompt = "Un desconocido amable te dice: «Can I help you?» ¿Qué respondes?",
        options = listOf("Yes, please. I am looking for the station.", "Yes, help I can, station looking.", "Looking for station, I am yes."), answer = 0,
        rule = "Aceptar ayuda con la frase completa", trap = "«Yes, please» + la frase completa: «I am looking for the station». El orden sujeto–verbo no se mueve."),
    // The phantom-E family's transfer items: new situations built
    // from the live banks' s-words (soccer, salad, sister, store) —
    // the words are the curriculum's, the questions were never
    // practiced anywhere.
    TransferItem("pe1", "pronunciation_phantom_e", kind = "choice",
        prompt = "Vas a jugar fútbol con amigos. ¿Cuál frase suena bien en inglés?",
        options = listOf("I play esoccer on Saturdays.", "I play soccer on Saturdays.", "I play shoccer on Saturdays."), answer = 1,
        rule = "La /s/ arranca directa: «soccer»", trap = "«Esoccer» lleva la «e» fantasma: en inglés el silbido /s/ va primero, sin vocal antes."),
    TransferItem("pe2", "pronunciation_phantom_e", kind = "choice",
        prompt = "En la cafetería pides una ensalada. ¿Cómo suena bien?",
        options = listOf("A esalad, please.", "A shalad, please.", "A salad, please."), answer = 2,
        rule = "«Salad» empieza con /s/ directa", trap = "Nada de «esalad»: la «e» fantasma no existe en inglés."),
    TransferItem("pe3", "pronunciation_phantom_e", kind = "choice",
        prompt = "Presentas a tu hermana: «This is my…». ¿Qué palabra suena bien?",
        options = listOf("esister", "sister", "shister"), answer = 1,
        rule = "«Sister» con /s/ directa", trap = "«Esister» es la trampa clásica: la /s/ no necesita vocal de arranque."),
    TransferItem("pe4", "pronunciation_phantom_e", kind = "choice",
        prompt = "Compraste zapatos en una tienda. ¿Cuál frase suena bien?",
        options = listOf("I went to the estore.", "I went to the store.", "I went to the shore."), answer = 1,
        rule = "«Store» empieza con /s/ directa", trap = "«Estore» lleva la «e» fantasma; y «shore» es otra palabra: la orilla.")
)

// ---------------------------------------------------------------- LA GUÍA

val GUIDE_WORDS: Map<String, GuideWord> = listOf(
    GuideWord("ship", "ship", "/ʃɪp/", "barco",
        "La «i» de ship es corta y relajada. Si la estiras, dices otra palabra: sheep.",
        listOf(
            GuideStep("sh", "ʃ", "round", "«sh»: labios redondeados, como pedir silencio: shhh."),
            GuideStep("i", "ɪ", "relaxed", "«i» corta y relajada, entre la i y la e. Sin estirarla."),
            GuideStep("p", "p", "closed", "«p»: cierra los labios y suelta el aire de golpe.")
        )),
    GuideWord("sheep", "sheep", "/ʃiːp/", "oveja",
        "La «ee» de sheep es larga y con sonrisa: sheeeep.",
        listOf(
            GuideStep("sh", "ʃ", "round", "«sh»: labios redondeados: shhh."),
            GuideStep("ee", "iː", "wide", "«ee» larga: labios como una sonrisa amplia y estírala: sheeep."),
            GuideStep("p", "p", "closed", "«p»: cierra los labios y suelta el aire.")
        )),
    GuideWord("went", "went", "/went/", "fui / fue (pasado de go)",
        "«went» es el pasado de «go»: Yesterday I went…",
        listOf(
            GuideStep("w", "w", "round", "«w»: labios en círculo pequeño, como dar un beso al aire."),
            GuideStep("e", "e", "open", "«e» breve y abierta, como en «mesa» pero más corta."),
            GuideStep("n", "n", "tongueup", "«n»: la punta de la lengua toca detrás de los dientes de arriba."),
            GuideStep("t", "t", "tongueup", "«t»: misma posición, soltando el aire con un toque seco.")
        )),
    GuideWord("three", "three", "/θriː/", "tres",
        "La «th» sorda no existe en español neutro: la lengua asoma entre los dientes.",
        listOf(
            GuideStep("th", "θ", "th", "«th»: la punta de la lengua asoma entre los dientes y soplas suave."),
            GuideStep("r", "r", "tongueup", "«r» inglesa: lengua atrás, sin tocar nada, sin vibrar."),
            GuideStep("ee", "iː", "wide", "«ee» larga con sonrisa: threeee.")
        )),
    GuideWord("sister", "sister", "/ˈsɪs.tər/", "hermana",
        "La trampa es arrancar con una vocal que no va: «esister». La /s/ inglesa empieza directa, con el silbido.",
        listOf(
            GuideStep("s", "s", "teeth", "«s» inicial DIRECTA: el silbido sale sin ninguna vocal antes. Nada de «e» de arranque."),
            GuideStep("i", "ɪ", "relaxed", "«i» corta y relajada, como la de ship."),
            GuideStep("s", "s", "teeth", "La segunda «s» también silba: sis-ter."),
            GuideStep("t", "t", "tongueup", "«t»: la punta de la lengua toca detrás de los dientes y suelta el aire."),
            GuideStep("er", "ər", "relaxed", "«er» final suave y relajada.")
        )),
    GuideWord("work", "work", "/wɜːrk/", "trabajo / trabajar",
        "El corazón de «work» es el sonido /ɜː/: un sonido central que no existe en español. Boca casi quieta, como pensar «mmm».",
        listOf(
            GuideStep("w", "w", "round", "«w»: labios en círculo pequeño, como dar un beso al aire."),
            GuideStep("or", "ɜːr", "relaxed", "«or» suena /ɜːr/: boca relajada, lengua en el centro, sin abrir. No es «or» española."),
            GuideStep("k", "k", "open", "«k»: un golpe seco de aire desde el fondo de la garganta.")
        )),
    GuideWord("bird", "bird", "/bɜːrd/", "pájaro",
        "«Bird» lleva el mismo /ɜː/ de «work»: buuurd. Si dices «berd», suena a otra cosa.",
        listOf(
            GuideStep("b", "b", "closed", "«b»: labios cerrados que se abren con un pequeño estallido."),
            GuideStep("ir", "ɜːr", "relaxed", "«ir» suena /ɜːr/: boca casi quieta, sonido largo y central."),
            GuideStep("d", "d", "tongueup", "«d»: la punta de la lengua toca detrás de los dientes de arriba.")
        )),
    GuideWord("cat", "cat", "/kæt/", "gato",
        "La «a» de «cat» (/æ/) vive entre la a y la e: abre la boca como para decir «e» pero di «a».",
        listOf(
            GuideStep("c", "k", "open", "«c» suena «k»: golpe de aire desde la garganta, boca abierta."),
            GuideStep("a", "æ", "open", "«a» /æ/: abre bien la mandíbula, entre «a» y «e»: caaat."),
            GuideStep("t", "t", "tongueup", "«t»: lengua detrás de los dientes de arriba, toque seco.")
        )),
    GuideWord("bed", "bed", "/bed/", "cama",
        "«Bed» es corta y seca; no la estires o sonará «bade». Compárala con «bad» en tu oído, poco a poco.",
        listOf(
            GuideStep("b", "b", "closed", "«b»: labios cerrados, apertura breve con voz."),
            GuideStep("e", "e", "open", "«e» breve y abierta, como en «mesa» pero más corta."),
            GuideStep("d", "d", "tongueup", "«d»: punta de la lengua detrás de los dientes de arriba.")
        )),
    GuideWord("book", "book", "/bʊk/", "libro",
        "La «oo» de «book» es CORTA (/ʊ/). La larga (/uː/) es la de «food»: book ≠ fuuud.",
        listOf(
            GuideStep("b", "b", "closed", "«b»: labios cerrados, apertura breve."),
            GuideStep("oo", "ʊ", "round", "«oo» corta y relajada: labios apenas redondeados, sonido breve."),
            GuideStep("k", "k", "open", "«k»: golpe seco de aire desde la garganta.")
        )),
    GuideWord("food", "food", "/fuːd/", "comida",
        "La «oo» de «food» es LARGA (/uː/): fuuud. Contraste directo con la corta de «book».",
        listOf(
            GuideStep("f", "f", "teeth", "«f»: los dientes de arriba muerden suave el labio de abajo y soplas."),
            GuideStep("oo", "uː", "round", "«oo» larga: labios bien redondos y estira el sonido: fuuud."),
            GuideStep("d", "d", "tongueup", "«d»: punta de la lengua detrás de los dientes de arriba.")
        )),
    GuideWord("day", "day", "/deɪ/", "día",
        "«Day» termina en un deslizamiento: de «e» a «i» sin cortar. La sonrisa hace el trabajo.",
        listOf(
            GuideStep("d", "d", "tongueup", "«d»: punta de la lengua detrás de los dientes de arriba."),
            GuideStep("ay", "eɪ", "wide", "«ay» /eɪ/: empieza como «e» y desliza hacia «i» con una sonrisa: deeei.")
        )),
    GuideWord("night", "night", "/naɪt/", "noche",
        "La «gh» de «night» no se pronuncia: suena «nait». La «i» se desliza como en «day».",
        listOf(
            GuideStep("n", "n", "tongueup", "«n»: punta de la lengua detrás de los dientes de arriba, aire por la nariz."),
            GuideStep("igh", "aɪ", "wide", "«igh» suena /aɪ/: de «a» abierta desliza a «i»: naaait. La «gh» es muda."),
            GuideStep("t", "t", "tongueup", "«t»: misma posición de la «n», soltando el aire seco.")
        )),
    GuideWord("water", "water", "/ˈwɔːtər/", "agua",
        "En EE. UU. la «t» del centro se suaviza y suena casi como una «d» rápida: «uóder». No pasa nada si la marcas: también se entiende.",
        listOf(
            GuideStep("w", "w", "round", "«w»: labios en círculo pequeño."),
            GuideStep("a", "ɔː", "open", "«a» suena /ɔː/: boca abierta y sonido largo, parecido a una «o» abierta."),
            GuideStep("t", "t", "tongueup", "«t» del centro: toque rápido y suave, casi una «d»."),
            GuideStep("er", "ər", "relaxed", "«er» final: sonido débil y relajado, el más flojo de la palabra.")
        )),
    GuideWord("think", "think", "/θɪŋk/", "pensar",
        "«Think» junta dos sonidos nuevos: la «th» sorda del inicio y el «ng» nasal del final (como en «banco», pero sostenido).",
        listOf(
            GuideStep("th", "θ", "th", "«th»: la punta de la lengua asoma entre los dientes y soplas suave."),
            GuideStep("i", "ɪ", "relaxed", "«i» corta y relajada, entre la i y la e."),
            GuideStep("nk", "ŋk", "tongueup", "«nk»: el fondo de la lengua sube y el sonido sale por la nariz, cerrado con una «k» seca.")
        )),
    GuideWord("time", "time", "/taɪm/", "tiempo / hora",
        "«Time» es un diptongo: empieza en «a» y resbala a «i» en un solo golpe de voz: «taim».",
        listOf(
            GuideStep("t", "t", "tongueup", "«t»: la punta de la lengua toca detrás de los dientes y suelta el aire seco."),
            GuideStep("i", "aɪ", "wide", "«i» larga: abre con «a» y resbala sonriendo hacia «i», un solo movimiento."),
            GuideStep("m", "m", "closed", "«m»: labios cerrados y el sonido vibra por la nariz.")
        )),
    GuideWord("make", "make", "/meɪk/", "hacer",
        "La «a» de make no es «a»: es «ei», como en «rey» pero más breve. Y la «e» final no suena.",
        listOf(
            GuideStep("m", "m", "closed", "«m»: labios cerrados, zumbido nasal."),
            GuideStep("a", "eɪ", "wide", "«a» suena «ei»: empieza en «e» y resbala a «i», con la boca estirada."),
            GuideStep("k", "k", "open", "«k»: el fondo de la lengua sube y corta el aire de golpe. La «e» final no existe.")
        )),
    GuideWord("park", "park", "/pɑːrk/", "parque / estacionar",
        "La «ar» es larga y abierta, y la «r» inglesa nunca vibra: la lengua se curva hacia atrás sin tocar nada.",
        listOf(
            GuideStep("p", "p", "closed", "«p»: labios cerrados y un soplo corto al abrir."),
            GuideStep("ar", "ɑːr", "open", "«ar» larga y abierta, como un «a» que dura; la lengua se curva atrás sin vibrar."),
            GuideStep("k", "k", "open", "«k»: corte seco con el fondo de la lengua.")
        )),
    GuideWord("sun", "sun", "/sʌn/", "sol",
        "La «u» de sun es el sonido central «ʌ»: ni «a» ni «o», un golpe corto y flojo desde la garganta.",
        listOf(
            GuideStep("s", "s", "teeth", "«s»: dientes casi juntos y el aire silba."),
            GuideStep("u", "ʌ", "relaxed", "«u» central: boca apenas abierta, sonido corto y relajado."),
            GuideStep("n", "n", "tongueup", "«n»: la punta de la lengua toca arriba y el sonido sale por la nariz.")
        )),
    GuideWord("love", "love", "/lʌv/", "amar / amor",
        "«Love» engaña por escrito: se dice «lav» corto, con la «v» vibrando entre labio y dientes.",
        listOf(
            GuideStep("l", "l", "tongueup", "«l»: la punta de la lengua toca detrás de los dientes de arriba."),
            GuideStep("o", "ʌ", "relaxed", "«o» suena «ʌ»: el golpe corto y central, como en «sun»."),
            GuideStep("ve", "v", "teeth", "«v»: el labio de abajo toca los dientes de arriba y vibra. La «e» final no suena.")
        )),
    GuideWord("home", "home", "/hoʊm/", "casa / hogar",
        "«Home» resbala de «o» a «u» en un solo sonido: «joum». Empieza con una «h» aspirada, como un suspiro.",
        listOf(
            GuideStep("h", "h", "open", "«h»: un suspiro suave desde la garganta, sin tocar nada."),
            GuideStep("o", "oʊ", "round", "«o» resbala a «u»: los labios se cierran en círculo al final."),
            GuideStep("m", "m", "closed", "«m»: labios cerrados, zumbido nasal para terminar.")
        ))
).associateBy { it.key }

val GUIDE_GROUPS: List<Pair<String, List<String>>> = listOf(
    "Contraste: i corta / ee larga" to listOf("ship", "sheep"),
    "Contraste: u corta / uu larga" to listOf("book", "food"),
    "La «th» y sonidos nuevos" to listOf("three", "think"),
    "La «e» fantasma: la /s/ directa" to listOf("sister"),
    "Palabras de todos los días" to listOf("went", "cat", "bed", "day", "night", "sun", "home"),
    "Más palabras de siempre" to listOf("work", "bird", "water", "time", "make", "park", "love")
)

// ---------------------------------------------------------------- CONVERSACIONES

val CONVERSATIONS: List<Conversation> = listOf(
    Conversation("cafe", "En la cafetería", "Postal · Nivel 1", listOf(
        ConvTurn("Mesera", "Good morning! What would you like?", listOf(
            ConvOption("I would like a coffee, please.", true, "conversation_turn"),
            ConvOption("I coffee want please.", false, "syntax_word_order"),
            ConvOption("Me gustaría un café.", false, "conversation_turn")), "«I would like» es la forma cortés de pedir."),
        ConvTurn("Mesera", "Hot or iced?", listOf(
            ConvOption("Hot, please.", true, "conversation_turn"),
            ConvOption("Yes, hot is yesterday.", false, "conversation_turn"),
            ConvOption("Coffee hot I.", false, "syntax_word_order")), "Respuestas cortas y completas: «Hot, please»."),
        ConvTurn("Mesera", "Anything else?", listOf(
            ConvOption("That is all, thank you.", true, "conversation_turn"),
            ConvOption("No more, thanks you.", false, "conversation_turn"),
            ConvOption("Finish now.", false, "conversation_turn")), "«That is all» = eso es todo.")
    )),
    Conversation("ayer", "¿Qué hiciste ayer?", "Postal · Pasado", listOf(
        ConvTurn("Diego", "Hi! What did you do yesterday?", listOf(
            ConvOption("Yesterday I went to the park.", true, "syntax_past_tense"),
            ConvOption("Yesterday I go to the park.", false, "syntax_past_tense"),
            ConvOption("I go yesterday to park.", false, "syntax_past_tense")), "Con «yesterday», el verbo cambia: go → went."),
        ConvTurn("Diego", "Nice! And in the evening?", listOf(
            ConvOption("I watched a movie with my sister.", true, "syntax_past_tense"),
            ConvOption("I watch a movie with my sister.", false, "syntax_past_tense"),
            ConvOption("Movie watched I.", false, "syntax_word_order")), "watch → watched: el pasado regular añade -ed."),
        ConvTurn("Diego", "Did you buy anything?", listOf(
            ConvOption("Yes, I bought a book.", true, "syntax_past_tense"),
            ConvOption("Yes, I buy a book.", false, "syntax_past_tense"),
            ConvOption("Yes, book I buyed.", false, "syntax_past_tense")), "buy → bought (nunca «buyed»).")
    )),
    Conversation("hotel", "En el hotel", "Postal · Viaje", listOf(
        ConvTurn("Recepcionista", "Good evening! Do you have a reservation?", listOf(
            ConvOption("Yes, I have a reservation.", true, "conversation_turn"),
            ConvOption("Yes, reservation I have.", false, "syntax_word_order"),
            ConvOption("Reservation yes mine is.", false, "syntax_word_order")), "Frase completa y tranquila: «I have a reservation»."),
        ConvTurn("Recepcionista", "Perfect. What is your name?", listOf(
            ConvOption("My name is Alex.", true, "conversation_turn"),
            ConvOption("Name my is Alex.", false, "syntax_word_order"),
            ConvOption("I name Alex is.", false, "syntax_word_order")), "«My name is…» — el orden no se negocia."),
        ConvTurn("Recepcionista", "Here is your key. Breakfast is at eight.", listOf(
            ConvOption("Thank you very much.", true, "conversation_turn"),
            ConvOption("Thank very much you.", false, "syntax_word_order"),
            ConvOption("Key thanks, room go I.", false, "syntax_word_order")), "Cerrar con cortesía completa siempre funciona.")
    )),
    Conversation("mercado", "En el mercado", "Postal · Nivel 1", listOf(
        ConvTurn("Vendedora", "Hello! Can I help you?", listOf(
            ConvOption("Yes, I am looking for fresh bread.", true, "conversation_turn"),
            ConvOption("I looking bread fresh.", false, "syntax_word_order"),
            ConvOption("Bread I want fresh is.", false, "syntax_word_order")), "«I am looking for» = estoy buscando; «fresh» va antes de «bread»."),
        ConvTurn("Vendedora", "Of course. How many?", listOf(
            ConvOption("Two, please.", true, "conversation_turn"),
            ConvOption("Please two me give.", false, "syntax_word_order"),
            ConvOption("Two of those is.", false, "conversation_turn")), "En el mercado, breve y cortés funciona."),
        ConvTurn("Vendedora", "Here you are. Anything else?", listOf(
            ConvOption("No, thank you very much.", true, "conversation_turn"),
            ConvOption("No, thank very much you.", false, "syntax_word_order"),
            ConvOption("Is all.", false, "conversation_turn")), "«Thank you very much» — el orden es fijo.")
    )),
    Conversation("trabajo", "Primer día de trabajo", "Postal · Trabajo", listOf(
        ConvTurn("La jefa", "Welcome to the team! Do you speak English?", listOf(
            ConvOption("Yes, I speak a little English.", true, "conversation_turn"),
            ConvOption("Yes, I does speak English.", false, "syntax_present_questions"),
            ConvOption("English I speak yes.", false, "syntax_word_order")), "Con «I» el verbo va sin -s y sin «does»: «I speak»."),
        ConvTurn("La jefa", "Does the office open at nine?", listOf(
            ConvOption("Yes, it opens at nine.", true, "conversation_turn"),
            ConvOption("Yes, it does opens at nine.", false, "syntax_present_questions"),
            ConvOption("Opens nine at it yes.", false, "syntax_word_order")), "En la respuesta corta vuelve la -s: «it opens». El «does» era solo para la pregunta."),
        ConvTurn("La jefa", "Do you have any questions for me?", listOf(
            ConvOption("Yes. Where do I start?", true, "syntax_present_questions"),
            ConvOption("Where I do start?", false, "syntax_present_questions"),
            ConvOption("Questions I have yes, start where?", false, "syntax_word_order")), "Pregunta Wh completa: «Where do I start?» — palabra, do, sujeto, verbo.")
    )),
    Conversation("direcciones", "Pidiendo direcciones", "Postal · Ciudad", listOf(
        ConvTurn("Una vecina", "Hello! Can I help you?", listOf(
            ConvOption("Yes, please. Where is the museum?", true, "syntax_present_questions"),
            ConvOption("Where the museum is?", false, "syntax_present_questions"),
            ConvOption("Museum where is?", false, "syntax_word_order")), "Con el verbo «is» la pregunta invierte: «Where is the museum?»"),
        ConvTurn("Una vecina", "It is next to the library, two blocks away.", listOf(
            ConvOption("Is it far from here?", true, "syntax_present_questions"),
            ConvOption("It far is from here?", false, "syntax_present_questions"),
            ConvOption("Far is it here from?", false, "syntax_word_order")), "Pregunta corta con «is»: el verbo va primero: «Is it far…?»"),
        ConvTurn("Una vecina", "No, just five minutes on foot.", listOf(
            ConvOption("Thank you! Have a nice day.", true, "conversation_turn"),
            ConvOption("Thank you, day nice have.", false, "syntax_word_order"),
            ConvOption("I very thank you much.", false, "syntax_word_order")), "Despedida completa: «Have a nice day».")
    )),
    Conversation("medico", "En el médico", "Postal · Salud", listOf(
        ConvTurn("La recepcionista", "Good morning. How can I help you?", listOf(
            ConvOption("Good morning. I have an appointment at ten.", true, "conversation_turn"),
            ConvOption("I appointment have at ten.", false, "syntax_word_order"),
            ConvOption("Morning. Appointment I ten have.", false, "syntax_word_order")), "Frase completa y tranquila: sujeto + verbo + el dato: «I have an appointment at ten»."),
        ConvTurn("El médico", "What happened? When did it start?", listOf(
            ConvOption("I fell down yesterday and my arm hurts.", true, "syntax_past_tense"),
            ConvOption("I fall yesterday and arm hurt.", false, "syntax_past_tense"),
            ConvOption("Yesterday I fall and my arm is hurt.", false, "syntax_past_tense")), "Con «yesterday», el verbo va en pasado: «fell», «hurt»."),
        ConvTurn("El médico", "Take this twice a day, and rest.", listOf(
            ConvOption("Thank you, doctor. How many days?", true, "syntax_present_questions"),
            ConvOption("How many days I take?", false, "syntax_present_questions"),
            ConvOption("Days how many, doctor?", false, "syntax_word_order")), "Pregunta completa: «How many days?» abre y ordena todo.")
    )),
    Conversation("aeropuerto", "En el aeropuerto", "Postal · Viaje", listOf(
        ConvTurn("El agente", "Good afternoon. Where are you traveling to?", listOf(
            ConvOption("To Tampa, please. Here is my passport.", true, "conversation_turn"),
            ConvOption("Tampa. Passport here is my.", false, "syntax_word_order"),
            ConvOption("I traveling Tampa to.", false, "syntax_word_order")), "Respuesta completa con cortesía: destino + «please» + el documento."),
        ConvTurn("El agente", "Do you have any bags to check?", listOf(
            ConvOption("Yes, I do. Just this one suitcase.", true, "syntax_present_questions"),
            ConvOption("Yes, I have one suitcase just.", false, "syntax_word_order"),
            ConvOption("Yes I do bags one check.", false, "syntax_word_order")), "A «Do you…?» se responde con el auxiliar: «Yes, I do»."),
        ConvTurn("El agente", "Your gate is B7. Boarding starts at six.", listOf(
            ConvOption("Thank you very much. Have a good day!", true, "conversation_turn"),
            ConvOption("Thanks you very many.", false, "syntax_word_order"),
            ConvOption("Good day have you too.", false, "syntax_word_order")), "Cierre completo: «Thank you very much» — el orden no se negocia.")
    )),
    Conversation("restaurante", "En el restaurante", "Postal · Nivel 1", listOf(
        ConvTurn("El mesero", "Good evening. Are you ready to order?", listOf(
            ConvOption("Yes, please. I would like the grilled chicken.", true, "syntax_word_order"),
            ConvOption("I would like chicken the grilled.", false, "syntax_word_order"),
            ConvOption("Chicken grilled I like would.", false, "syntax_word_order")), "«I would like» + el plato: el adjetivo antes del sustantivo, «grilled chicken»."),
        ConvTurn("El mesero", "And to drink?", listOf(
            ConvOption("A glass of water, please.", true, "conversation_turn"),
            ConvOption("Water glass, please a.", false, "syntax_word_order"),
            ConvOption("Please water of glass.", false, "syntax_word_order")), "«A glass of water»: artículo + cosa + «of» + contenido."),
        ConvTurn("El mesero", "Would you like anything else?", listOf(
            ConvOption("That is all, thank you. The check, please.", true, "conversation_turn"),
            ConvOption("That all is. Check the please.", false, "syntax_word_order"),
            ConvOption("Nothing more, checks please.", false, "syntax_word_order")), "«That is all» cierra con elegancia; «the check» pide la cuenta.")
    ))
)

// ---------------------------------------------------------------- ESCUCHA

val LISTENING: List<ListeningItem> = listOf(
    ListeningItem("l1", "She visited her family last week.",
        listOf("She visits her family every week.", "She visited her family last week.", "She is visiting her family now."),
        1, "Habla de la semana pasada.", "Sonó el pasado: «visited» + «last week». Las otras dos son presente."),
    ListeningItem("l2", "I bought a book yesterday.",
        listOf("I buy a book every day.", "I am buying a book now.", "I bought a book yesterday."),
        2, "Habla de ayer.", "«Bought» es el pasado de «buy», y «yesterday» lo confirma."),
    ListeningItem("l3", "Does he work here?",
        listOf("Does he work here?", "Does he works here?", "He does work here?"),
        0, "Es una pregunta sobre él.", "Con «Does», el verbo va en base: «work», sin «s». Se oye la diferencia."),
    ListeningItem("l4", "The small dog is happy.",
        listOf("The dog small is happy.", "The small dog is happy.", "The happy dog is small."),
        1, "", "El adjetivo va antes: «small dog». La tercera frase dice otra cosa (el perro feliz es pequeño).", transfer = true),
    ListeningItem("l5", "We saw a movie last night.",
        listOf("We see a movie last night.", "We saw a movie tonight.", "We saw a movie last night."),
        2, "", "«Saw» (pasado de see) + «last night»: anoche, no esta noche.", transfer = true),
    ListeningItem("l6", "Thank you very much.",
        listOf("Thank you very many.", "Thanks you very much.", "Thank you very much."),
        2, "", "La fórmula exacta es «Thank you very much» — ni «many» ni «Thanks you».", transfer = true)
)

// ---------------------------------------------------------------- HOY / CAMINO

val DAY_PLANS: List<DayPlan> = listOf(
    DayPlan("syntax_past_tense", "el pasado", "went"),
    DayPlan("syntax_word_order", "el orden de las palabras", "ship"),
    DayPlan("syntax_present_questions", "preguntas en presente (Do / Does)", "three"),
    DayPlan("cognates", "la regla -ción → -tion", "sheep", ruleFocus = "cion"),
    DayPlan("syntax_past_tense", "el pasado, otra vuelta", "went"),
    DayPlan("syntax_word_order", "el orden de las palabras, otra vuelta", "ship"),
    DayPlan("syntax_present_questions", "preguntas en presente, otra vuelta", "three")
)

val LEVELS: List<LevelDef> = listOf(
    LevelDef("n1", 1, "Primeros pasos", "Cognados y el orden básico de la frase", listOf("t6", "t4", "t11")),
    LevelDef("n2", 2, "El pasado y la ciudad", "Contar lo que ya pasó, sin traducir palabra por palabra", listOf("t1", "t9", "t2")),
    LevelDef("n3", 3, "Preguntas y viajes", "Preguntar bien y responder en recepción", listOf("t5", "t10", "t12")),
    LevelDef("n4", 4, "Conversación real", "Responder como una persona, no como un diccionario", listOf("t13", "t3", "t8"))
)
