package io.github.kasecrab.razorback.voice

/** One Aura-2 voice as Deepgram lists it. */
class Voice(val id: String, val name: String, val feminine: Boolean, val accent: String, val traits: String) {
    /** Language code from the model id, e.g. "en" from "aura-2-thalia-en". */
    val language: String get() = id.substringAfterLast('-')
}

/** Every Aura-2 voice, grouped by language, in Deepgram's order. */
object Voices {

    val languages = linkedMapOf(
        "en" to "English",
        "es" to "Spanish",
        "nl" to "Dutch",
        "fr" to "French",
        "de" to "German",
        "it" to "Italian",
        "ja" to "Japanese",
    )

    /** What a voice says when previewed, in its own language. */
    fun sample(v: Voice): String = when (v.language) {
        "es" -> "Hola, soy ${v.name}. Así suena mi voz."
        "nl" -> "Hoi, ik ben ${v.name}. Zo klinkt mijn stem."
        "fr" -> "Bonjour, je suis ${v.name}. Voici ma voix."
        "de" -> "Hallo, ich bin ${v.name}. So klingt meine Stimme."
        "it" -> "Ciao, sono ${v.name}. Questa è la mia voce."
        "ja" -> "こんにちは、${v.name}です。これが私の声です。"
        else -> "Hi, I'm ${v.name}. This is what I sound like."
    }

    fun byId(id: String): Voice? = all.firstOrNull { it.id == id }

    private fun v(id: String, feminine: Boolean, accent: String, traits: String) =
        Voice(id, id.removePrefix("aura-2-").substringBefore('-').replaceFirstChar { it.uppercase() }, feminine, accent, traits)

    val all: List<Voice> = listOf(
        v("aura-2-thalia-en", true, "American", "Clear, confident, energetic"),
        v("aura-2-andromeda-en", true, "American", "Casual, expressive, comfortable"),
        v("aura-2-helena-en", true, "American", "Caring, natural, friendly, raspy"),
        v("aura-2-apollo-en", false, "American", "Confident, comfortable, casual"),
        v("aura-2-arcas-en", false, "American", "Natural, smooth, clear"),
        v("aura-2-aries-en", false, "American", "Warm, energetic, caring"),
        v("aura-2-amalthea-en", true, "Filipino", "Engaging, natural, cheerful"),
        v("aura-2-asteria-en", true, "American", "Clear, confident, knowledgeable"),
        v("aura-2-athena-en", true, "American", "Calm, smooth, professional"),
        v("aura-2-atlas-en", false, "American", "Enthusiastic, confident, friendly"),
        v("aura-2-aurora-en", true, "American", "Cheerful, expressive, energetic"),
        v("aura-2-callista-en", true, "American", "Clear, energetic, professional"),
        v("aura-2-cora-en", true, "American", "Smooth, melodic, caring"),
        v("aura-2-cordelia-en", true, "American", "Approachable, warm, polite"),
        v("aura-2-delia-en", true, "American", "Casual, friendly, breathy"),
        v("aura-2-draco-en", false, "British", "Warm, trustworthy, baritone"),
        v("aura-2-electra-en", true, "American", "Professional, engaging, knowledgeable"),
        v("aura-2-harmonia-en", true, "American", "Empathetic, clear, calm"),
        v("aura-2-hera-en", true, "American", "Smooth, warm, professional"),
        v("aura-2-hermes-en", false, "American", "Expressive, engaging, professional"),
        v("aura-2-hyperion-en", false, "Australian", "Caring, warm, empathetic"),
        v("aura-2-iris-en", true, "American", "Cheerful, positive, approachable"),
        v("aura-2-janus-en", true, "American", "Southern, smooth, trustworthy"),
        v("aura-2-juno-en", true, "American", "Natural, engaging, breathy"),
        v("aura-2-jupiter-en", false, "American", "Expressive, knowledgeable, baritone"),
        v("aura-2-luna-en", true, "American", "Friendly, natural, engaging"),
        v("aura-2-mars-en", false, "American", "Smooth, patient, baritone"),
        v("aura-2-minerva-en", true, "American", "Positive, friendly, natural"),
        v("aura-2-neptune-en", false, "American", "Professional, patient, polite"),
        v("aura-2-odysseus-en", false, "American", "Calm, smooth, professional"),
        v("aura-2-ophelia-en", true, "American", "Expressive, enthusiastic, cheerful"),
        v("aura-2-orion-en", false, "American", "Approachable, calm, polite"),
        v("aura-2-orpheus-en", false, "American", "Professional, clear, trustworthy"),
        v("aura-2-pandora-en", true, "British", "Smooth, calm, breathy"),
        v("aura-2-phoebe-en", true, "American", "Energetic, warm, casual"),
        v("aura-2-pluto-en", false, "American", "Smooth, calm, baritone"),
        v("aura-2-saturn-en", false, "American", "Knowledgeable, confident, baritone"),
        v("aura-2-selene-en", true, "American", "Expressive, engaging, energetic"),
        v("aura-2-theia-en", true, "Australian", "Expressive, polite, sincere"),
        v("aura-2-vesta-en", true, "American", "Natural, patient, empathetic"),
        v("aura-2-zeus-en", false, "American", "Deep, trustworthy, smooth"),
        v("aura-2-celeste-es", true, "Colombian", "Clear, energetic, friendly"),
        v("aura-2-estrella-es", true, "Mexican", "Approachable, calm, expressive"),
        v("aura-2-nestor-es", false, "Peninsular", "Calm, professional, clear"),
        v("aura-2-sirio-es", false, "Mexican", "Calm, empathetic, baritone"),
        v("aura-2-carina-es", true, "Peninsular", "Professional, raspy, energetic"),
        v("aura-2-alvaro-es", false, "Peninsular", "Calm, clear, knowledgeable"),
        v("aura-2-diana-es", true, "Peninsular", "Confident, expressive, polite"),
        v("aura-2-aquila-es", false, "Latin American", "Expressive, enthusiastic, casual"),
        v("aura-2-selena-es", true, "Latin American", "Approachable, friendly, calm"),
        v("aura-2-javier-es", false, "Mexican", "Approachable, friendly, calm"),
        v("aura-2-agustina-es", true, "Peninsular", "Calm, clear, professional"),
        v("aura-2-antonia-es", true, "Argentine", "Enthusiastic, friendly, natural"),
        v("aura-2-gloria-es", true, "Colombian", "Casual, expressive, smooth"),
        v("aura-2-luciano-es", false, "Mexican", "Charismatic, cheerful, energetic"),
        v("aura-2-olivia-es", true, "Mexican", "Breathy, calm, warm"),
        v("aura-2-silvia-es", true, "Peninsular", "Charismatic, clear, warm"),
        v("aura-2-valerio-es", false, "Mexican", "Deep, natural, professional"),
        v("aura-2-beatrix-nl", true, "Dutch", "Cheerful, friendly, warm"),
        v("aura-2-daphne-nl", true, "Dutch", "Calm, confident, professional"),
        v("aura-2-cornelia-nl", true, "Dutch", "Approachable, polite, warm"),
        v("aura-2-sander-nl", false, "Dutch", "Calm, deep, professional"),
        v("aura-2-hestia-nl", true, "Dutch", "Caring, expressive, friendly"),
        v("aura-2-lars-nl", false, "Dutch", "Breathy, casual, sincere"),
        v("aura-2-roman-nl", false, "Dutch", "Calm, deep, patient"),
        v("aura-2-rhea-nl", true, "Dutch", "Caring, positive, warm"),
        v("aura-2-leda-nl", true, "Dutch", "Caring, empathetic, sincere"),
        v("aura-2-agathe-fr", true, "French", "Charismatic, cheerful, natural"),
        v("aura-2-hector-fr", false, "French", "Confident, expressive, patient"),
        v("aura-2-elara-de", true, "German", "Calm, clear, trustworthy"),
        v("aura-2-aurelia-de", true, "German", "Casual, natural, sincere"),
        v("aura-2-lara-de", true, "German", "Caring, cheerful, warm"),
        v("aura-2-julius-de", false, "German", "Casual, engaging, friendly"),
        v("aura-2-fabian-de", false, "German", "Confident, polite, professional"),
        v("aura-2-kara-de", true, "German", "Caring, expressive, professional"),
        v("aura-2-viktoria-de", true, "German", "Charismatic, enthusiastic, warm"),
        v("aura-2-melia-it", true, "Italian", "Clear, engaging, natural"),
        v("aura-2-elio-it", false, "Italian", "Breathy, calm, smooth"),
        v("aura-2-flavio-it", false, "Italian", "Confident, deep, trustworthy"),
        v("aura-2-maia-it", true, "Italian", "Caring, energetic, warm"),
        v("aura-2-cinzia-it", true, "Italian", "Approachable, smooth, warm"),
        v("aura-2-cesare-it", false, "Italian", "Clear, knowledgeable, smooth"),
        v("aura-2-livia-it", true, "Italian", "Cheerful, clear, expressive"),
        v("aura-2-dionisio-it", false, "Italian", "Confident, melodic, positive"),
        v("aura-2-demetra-it", true, "Italian", "Calm, comfortable, patient"),
        v("aura-2-uzume-ja", true, "Japanese", "Approachable, polite, professional"),
        v("aura-2-ebisu-ja", false, "Japanese", "Calm, deep, sincere"),
        v("aura-2-fujin-ja", false, "Japanese", "Confident, knowledgeable, smooth"),
        v("aura-2-izanami-ja", true, "Japanese", "Clear, polite, professional"),
        v("aura-2-ama-ja", true, "Japanese", "Casual, confident, natural"),
    )
}
