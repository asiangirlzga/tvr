package com.example.privatevoice

import com.example.privatevoice.command.AppEntry
import com.example.privatevoice.command.AppMatcher
import com.example.privatevoice.command.Command
import com.example.privatevoice.command.CommandParser
import com.example.privatevoice.command.Direction
import com.example.privatevoice.command.GlobalKind
import com.example.privatevoice.command.InfoKind
import com.example.privatevoice.command.PanelKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommandParserTest {
    private fun p(s: String) = CommandParser.parse(s)

    @Test fun volume() {
        assertEquals(Command.Volume(up = true, times = 1), p("volume up"))
        assertEquals(Command.Volume(up = false, times = 3), p("turn the volume down three times"))
        assertEquals(Command.Volume(up = true, times = 1), p("louder"))
        assertEquals(Command.Volume(up = true, times = 2), p("volume up twice"))
    }

    @Test fun muteAndMax() {
        assertEquals(Command.Mute(true), p("mute"))
        assertEquals(Command.Mute(false), p("unmute"))
        assertEquals(Command.MaxVolume, p("max volume"))
    }

    @Test fun buttons() {
        assertEquals(Command.Global(GlobalKind.HOME), p("go home"))
        assertEquals(Command.Global(GlobalKind.BACK), p("go back"))
        assertEquals(Command.Global(GlobalKind.RECENTS), p("show recent apps"))
        assertEquals(Command.Global(GlobalKind.NOTIFICATIONS), p("open notifications"))
        assertEquals(Command.Global(GlobalKind.QUICK_SETTINGS), p("quick settings"))
        assertEquals(Command.Global(GlobalKind.SCREENSHOT), p("take a screenshot"))
        assertEquals(Command.Global(GlobalKind.LOCK), p("lock the phone"))
    }

    @Test fun scrollMeansOppositeFingerDirection() {
        assertEquals(Command.Swipe(Direction.UP), p("scroll down"))
        assertEquals(Command.Swipe(Direction.DOWN), p("scroll up"))
        assertEquals(Command.Swipe(Direction.LEFT), p("swipe left"))
    }

    @Test fun openApp() {
        assertEquals(Command.OpenApp("youtube"), p("open youtube"))
        assertEquals(Command.OpenApp("play store"), p("please open the play store app"))
        assertEquals(Command.OpenApp("camera"), p("launch camera"))
        assertEquals(Command.OpenApp("whatsapp"), p("go to whatsapp"))
    }

    @Test fun settingsScreens() {
        assertEquals(Command.Panel(PanelKind.SETTINGS), p("open settings"))
        assertEquals(Command.Panel(PanelKind.WIFI), p("open Wi-Fi"))
        assertEquals(Command.Panel(PanelKind.BLUETOOTH), p("bluetooth settings"))
        assertEquals(Command.Panel(PanelKind.BATTERY_SAVER), p("battery saver"))
    }

    @Test fun torch() {
        assertEquals(Command.Torch(true), p("turn on the flashlight"))
        assertEquals(Command.Torch(false), p("flashlight off"))
    }

    @Test fun dial() {
        assertEquals(Command.Dial("9876543210"), p("call nine eight seven six five four three two one zero"))
        assertEquals(Command.Dial("112"), p("dial 112"))
        assertNull(p("call mom"))
    }

    @Test fun info() {
        assertEquals(Command.Info(InfoKind.TIME), p("what time is it"))
        assertEquals(Command.Info(InfoKind.BATTERY), p("battery level"))
        assertEquals(Command.Info(InfoKind.DATE), p("what's the date"))
    }

    @Test fun stop() = assertEquals(Command.StopListening, p("stop listening"))

    @Test fun unknownIsIgnored() {
        assertNull(p("the weather is nice today"))
        assertNull(p(""))
    }

    @Test fun wakeWord() {
        assertEquals("volume up", CommandParser.stripWakeWord("Hey phone, volume up"))
        assertEquals("go home", CommandParser.stripWakeWord("okay phone go home"))
        assertNull(CommandParser.stripWakeWord("volume up"))
        assertNull(CommandParser.stripWakeWord("hey there volume up"))
    }

    @Test fun appMatching() {
        val apps = listOf(
            AppEntry("WhatsApp", "com.whatsapp"),
            AppEntry("YouTube", "com.google.android.youtube"),
            AppEntry("Play Store", "com.android.vending"),
            AppEntry("Samsung Camera", "com.sec.camera"),
        )
        assertEquals("com.whatsapp", AppMatcher.best("whatsapp", apps)?.packageName)
        assertEquals("com.whatsapp", AppMatcher.best("whats up", apps)?.packageName)
        assertEquals("com.google.android.youtube", AppMatcher.best("you tube", apps)?.packageName)
        assertEquals("com.android.vending", AppMatcher.best("play store", apps)?.packageName)
        assertEquals("com.sec.camera", AppMatcher.best("camera", apps)?.packageName)
        assertNull(AppMatcher.best("zzzzqq", apps))
    }
}
