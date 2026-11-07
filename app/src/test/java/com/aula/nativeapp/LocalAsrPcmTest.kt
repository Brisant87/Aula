package com.aula.nativeapp

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the PCM decoding the in-app speech engine feeds on: the
 * capture hands it raw PCM (or a WAV), and the engine must hear
 * the exact samples recorded — a header parsing slip would
 * transcribe silence or noise. Ported from Cognata's gate, where
 * this decoder was pinned first.
 */
class LocalAsrPcmTest {

    private fun pcmBytes(samples: ShortArray): ByteArray {
        val buffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { buffer.putShort(it) }
        return buffer.array()
    }

    private fun wavBytes(pcm: ByteArray): ByteArray {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + pcm.size)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1)
        header.putShort(1)
        header.putInt(16000)
        header.putInt(32000)
        header.putShort(2)
        header.putShort(16)
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(pcm.size)
        return header.array() + pcm
    }

    @Test
    fun `raw pcm decodes little endian samples scaled to unit range`() {
        val samples = Pcm16.floatSamples(pcmBytes(shortArrayOf(0, 16384, -16384, 32767, -32768)))
        assertEquals(5, samples.size)
        assertEquals(0f, samples[0], 0.0001f)
        assertEquals(0.5f, samples[1], 0.0001f)
        assertEquals(-0.5f, samples[2], 0.0001f)
        assertEquals(32767f / 32768f, samples[3], 0.0001f)
        assertEquals(-1f, samples[4], 0.0001f)
    }

    @Test
    fun `wav input skips the header and decodes only the data chunk`() {
        val pcm = pcmBytes(shortArrayOf(1000, -1000, 2000))
        val samples = Pcm16.floatSamples(wavBytes(pcm))
        assertEquals(3, samples.size)
        assertEquals(1000f / 32768f, samples[0], 0.0001f)
        assertEquals(-1000f / 32768f, samples[1], 0.0001f)
        assertEquals(2000f / 32768f, samples[2], 0.0001f)
    }

    @Test
    fun `wav with an extra chunk before data still finds the payload`() {
        // A WAV whose fmt chunk is followed by a LIST chunk: the
        // walker must not mistake the extra chunk for audio.
        val pcm = pcmBytes(shortArrayOf(8192))
        val canonical = wavBytes(pcm)
        val listChunk = "LIST".toByteArray(Charsets.US_ASCII) +
            byteArrayOf(4, 0, 0, 0) + byteArrayOf(1, 2, 3, 4)
        val withList = canonical.copyOfRange(0, 36) + listChunk +
            canonical.copyOfRange(36, canonical.size)
        val samples = Pcm16.floatSamples(withList)
        assertEquals(1, samples.size)
        assertEquals(0.25f, samples[0], 0.0001f)
    }

    @Test
    fun `empty and truncated inputs decode to no samples`() {
        assertTrue(Pcm16.floatSamples(ByteArray(0)).isEmpty())
        assertTrue(Pcm16.floatSamples(byteArrayOf(0x52, 0x49)).isEmpty())
    }
}
