package com.example.tvremote

import com.example.tvremote.protocol.TvKey
import com.example.tvremote.voice.TvAction
import com.example.tvremote.voice.VoiceCommandParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandParserTest {
    private fun key(text: String): TvAction.Key = VoiceCommandParser.parse(text) as TvAction.Key

    @Test fun volumeUp() = assertEquals(TvKey.VOLUME_UP, key("Volume up").code)

    @Test fun volumeUpIsNotDpadUp() = assertEquals(TvKey.VOLUME_UP, key("turn the volume up").code)

    @Test fun repeatCount() {
        val action = key("volume down 5 times")
        assertEquals(TvKey.VOLUME_DOWN, action.code)
        assertEquals(5, action.repeat)
    }

    @Test fun numberWordsAndTwice() {
        assertEquals(3, key("volume up three times").repeat)
        assertEquals(2, key("volume up twice").repeat)
    }

    @Test fun dpadNeedsWholeCommand() {
        assertEquals(TvKey.DPAD_UP, key("go up").code)
        assertEquals(TvKey.DPAD_CENTER, key("OK").code)
    }

    @Test fun goHomeBeatsBack() = assertEquals(TvKey.HOME, key("go back to the home screen").code)

    @Test fun channelNumber() {
        val action = VoiceCommandParser.parse("go to channel 12") as TvAction.Digits
        assertEquals("12", action.digits)
    }

    @Test fun channelUpIsNotNext() = assertEquals(TvKey.CHANNEL_UP, key("next channel").code)

    @Test fun openApp() {
        val action = VoiceCommandParser.parse("open Netflix") as TvAction.OpenLink
        assertTrue(action.link.contains("com.netflix.ninja"))
        assertTrue(VoiceCommandParser.parse("play netflix") is TvAction.OpenLink)
        assertTrue(VoiceCommandParser.parse("prime video") is TvAction.OpenLink)
    }

    @Test fun youtubeSearch() {
        val action = VoiceCommandParser.parse("search funny cats on YouTube") as TvAction.OpenLink
        assertTrue(action.link.endsWith("search_query=funny+cats"))
    }

    @Test fun playAndPause() {
        assertEquals(TvKey.MEDIA_PLAY, key("play").code)
        assertEquals(TvKey.MEDIA_PAUSE, key("pause").code)
    }

    @Test fun hindiPhrases() {
        assertEquals(TvKey.VOLUME_UP, key("आवाज़ बढ़ाओ").code)
        assertEquals(TvKey.VOLUME_DOWN, key("आवाज कम").code)
        assertTrue(VoiceCommandParser.parse("यूट्यूब खोलो") is TvAction.OpenLink)
    }

    @Test fun gibberishIsIgnored() {
        assertNull(VoiceCommandParser.parse("what is the weather like"))
        assertNull(VoiceCommandParser.parse("   "))
    }
}
