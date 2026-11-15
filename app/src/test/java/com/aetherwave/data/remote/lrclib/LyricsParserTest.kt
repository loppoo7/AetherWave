package com.aetherwave.data.remote.lrclib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsParserTest {
    @Test
    fun parsesStandardAndRepeatedTimestamps() {
        val parsed = LyricsParser.parse("[00:01.25][00:02.5]Hello\n[01:03.007]World")

        assertEquals(3, parsed.size)
        assertEquals(1_250L, parsed[0].timestampMs)
        assertEquals(2_500L, parsed[1].timestampMs)
        assertEquals("Hello", parsed[0].text)
        assertEquals(63_007L, parsed[2].timestampMs)
        assertEquals("World", parsed[2].text)
    }

    @Test
    fun appliesSignedOffsetAndIgnoresMetadata() {
        val parsed = LyricsParser.parse("[ar:Artist]\n[offset:-150]\n[00:01.00]Line")

        assertEquals(1, parsed.size)
        assertEquals(850L, parsed.single().timestampMs)
    }

    @Test
    fun blankOrUntimedLyricsReturnEmptyList() {
        assertTrue(LyricsParser.parse(null).isEmpty())
        assertTrue(LyricsParser.parse("\n[ar:Artist]\nNo timestamps").isEmpty())
    }
}
