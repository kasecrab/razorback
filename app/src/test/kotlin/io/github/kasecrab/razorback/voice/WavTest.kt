package io.github.kasecrab.razorback.voice

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WavTest {

    private fun wav(format: Int = 1, channels: Int = 1, rate: Int = 24000, bits: Int = 16, dataSize: Int, declared: Int = dataSize, extraChunk: Boolean = false): ByteArray {
        val out = ByteArrayOutputStream()
        fun s(v: String) = out.write(v.toByteArray(Charsets.US_ASCII))
        fun le16(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }
        fun le32(v: Int) { le16(v and 0xFFFF); le16((v ushr 16) and 0xFFFF) }
        s("RIFF"); le32(36 + dataSize); s("WAVE")
        s("fmt "); le32(16); le16(format); le16(channels); le32(rate); le32(rate * channels * bits / 8); le16(channels * bits / 8); le16(bits)
        if (extraChunk) { s("LIST"); le32(3); out.write(byteArrayOf(1, 2, 3, 0)) }
        s("data"); le32(declared)
        for (i in 0 until dataSize) out.write(i and 0xFF)
        return out.toByteArray()
    }

    @Test
    fun readsMonoPcm16() {
        val clip = Wav.parse(wav(dataSize = 4800))
        assertNotNull(clip)
        assertEquals(44, clip!!.offset)
        assertEquals(4800, clip.length)
        assertEquals(2400, clip.frames)
        assertEquals(24000, clip.rate)
        assertEquals(1, clip.channels)
    }

    @Test
    fun skipsUnknownChunksAndKeepsStereoFramesWhole() {
        val clip = Wav.parse(wav(channels = 2, rate = 44100, dataSize = 4001, extraChunk = true))
        assertNotNull(clip)
        assertEquals(44 + 12, clip!!.offset)
        assertEquals(4000, clip.length)
        assertEquals(1000, clip.frames)
        assertEquals(2, clip.channels)
    }

    @Test
    fun placeholderSizesAndCutFilesUseWhatIsThere() {
        val streamed = Wav.parse(wav(dataSize = 960, declared = 0x7fff0000))
        assertEquals(960, streamed!!.length)
        val cut = wav(dataSize = 960, declared = 100000)
        assertEquals(960, Wav.parse(cut)!!.length)
        val negative = Wav.parse(wav(dataSize = 960, declared = -1))
        assertEquals(960, negative!!.length)
    }

    @Test
    fun refusesWhatIsNotPlainPcm16() {
        assertNull(Wav.parse(wav(format = 3, dataSize = 960)))
        assertNull(Wav.parse(wav(bits = 8, dataSize = 960)))
        assertNull(Wav.parse(wav(channels = 6, dataSize = 960)))
        assertNull(Wav.parse("RIFF....WAVX".toByteArray()))
        assertNull(Wav.parse(ByteArray(3)))
        assertNull(Wav.parse(wav(dataSize = 0)))
    }
}
