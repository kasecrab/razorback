package io.github.kasecrab.razorback.voice

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCatalogTest {

    private val reply = """
        {"stt":[{"name":"nova-3"}],
         "tts":[
          {"name":"asteria","canonical_name":"aura-asteria-en","architecture":"aura","languages":["en","en-US"],
           "metadata":{"accent":"American","age":"Adult","color":"#ff8800","display_name":"Asteria","tags":["feminine","clear","confident"]}},
          {"name":"thalia","canonical_name":"aura-2-thalia-en","architecture":"aura-2","languages":["en","en-US"],
           "metadata":{"accent":"American","age":"Adult","color":"#6b8afd","display_name":"Thalia",
                       "sample":"https://cdn.example/thalia.wav","tags":["feminine","clear","confident","energetic"]}},
          {"name":"apollo","canonical_name":"aura-2-apollo-en","architecture":"aura-2","languages":["en-US","en"],
           "metadata":{"accent":"American","age":"Young Adult","tags":["Masculine","Confident"," casual "]}},
          {"name":"agathe","canonical_name":"aura-2-agathe-fr","architecture":"aura-2","languages":["fr","fr-FR"],
           "metadata":{"accent":"French","color":"not-a-colour","tags":["feminine","cheerful"]}},
          {"name":"mystery","canonical_name":"aura-2-mystery-xx","architecture":"aura-2"},
          {"name":"broken","architecture":"aura-2","canonical_name":null}
         ]}
    """.trimIndent()

    @Test
    fun parsesEveryNamedVoiceWithMissingMetadataLeftBlank() {
        val voices = VoiceCatalog.parse(JSONObject(reply))
        assertEquals(listOf("aura-2-agathe-fr", "aura-2-apollo-en", "aura-2-mystery-xx", "aura-2-thalia-en", "aura-asteria-en"), voices.map { it.id })
        val mystery = voices.first { it.id == "aura-2-mystery-xx" }
        assertEquals("Mystery", mystery.name)
        assertNull(mystery.feminine)
        assertEquals("", mystery.accent)
        assertEquals("xx", mystery.language)
        assertEquals(0, mystery.color)
    }

    @Test
    fun genderComesFromTagsAndLeavesTheOtherDescriptors() {
        val apollo = VoiceCatalog.parse(JSONObject(reply)).first { it.id == "aura-2-apollo-en" }
        assertEquals(false, apollo.feminine)
        assertEquals(listOf("confident", "casual"), apollo.tags)
        assertEquals("Confident, casual", apollo.traits)
        assertEquals("Young Adult", apollo.age)
        assertEquals("en", apollo.language)
    }

    @Test
    fun colourIsOptional() {
        val voices = VoiceCatalog.parse(JSONObject(reply))
        assertEquals(0xFF6B8AFD.toInt(), voices.first { it.id == "aura-2-thalia-en" }.color)
        assertEquals(0, voices.first { it.id == "aura-2-agathe-fr" }.color)
        assertEquals(0, VoiceCatalog.hexColor("#12345"))
        assertEquals(0, VoiceCatalog.hexColor("#zzzzzz"))
    }

    @Test
    fun newestArchitectureLeads() {
        val voices = VoiceCatalog.parse(JSONObject(reply))
        assertTrue(voices.indexOfFirst { it.architecture == "aura" } > voices.indexOfLast { it.architecture == "aura-2" })
    }

    @Test
    fun languageNamesFallBackToTheCode() {
        assertEquals("English", VoiceCatalog.languageName("en"))
        assertEquals("XX", VoiceCatalog.languageName("xx"))
    }

    @Test
    fun emptyCatalogueParsesToNothing() {
        assertTrue(VoiceCatalog.parse(JSONObject("{}")).isEmpty())
        assertFalse(VoiceCatalog.parse(JSONObject(reply)).isEmpty())
    }
}
