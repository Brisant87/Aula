package com.aula.nativeapp

/*
 * Aula native — content data.
 *
 * Ported from the web app (~/workspace/aula-apk/app-assets/index.html).
 * Compact subset: all 17 syntax items, all 12 transfer items, 18 cognates,
 * 4 guide words, 3 conversations, all 6 listening items, 13 formulas,
 * 6 cognate rules, 7 day plans and the 4 Camino levels.
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
    Cognate("Library", "Biblioteca", "falsos", "LÁI-bre-ri", "I study at the library.", "¡Trampa! «Librería» (tienda) es «bookstore».")
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
        ))
).associateBy { it.key }

val GUIDE_GROUPS: List<Pair<String, List<String>>> = listOf(
    "Contraste: i corta / ee larga" to listOf("ship", "sheep"),
    "La «th» y sonidos nuevos" to listOf("three"),
    "La «e» fantasma: la /s/ directa" to listOf("sister"),
    "Palabras de todos los días" to listOf("went")
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
