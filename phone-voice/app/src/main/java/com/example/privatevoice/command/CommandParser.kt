package com.example.privatevoice.command

import java.util.Locale

/** Turns recognised text into a [Command]. Pure Kotlin: no Android, no network, easy to test. */
object CommandParser {

    private val numberWords = mapOf(
        "zero" to 0, "oh" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12,
        "fifteen" to 15, "twenty" to 20, "twice" to 2, "thrice" to 3,
    )
    private val digitWords = mapOf(
        "zero" to "0", "oh" to "0", "one" to "1", "two" to "2", "three" to "3", "four" to "4",
        "five" to "5", "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9",
    )

    fun normalize(raw: String): List<String> =
        raw.lowercase(Locale.ROOT)
            .replace("wi-fi", "wifi")
            .replace("wi fi", "wifi")
            .replace("'", "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .split(' ')
            .filter { it.isNotEmpty() }

    private val wakeStarters = setOf("hey", "hi", "ok", "okay", "hello")
    private val wakeWords = setOf("phone", "phones", "fone")

    /** Returns the words after "hey phone", or null if the wake phrase wasn't said. */
    fun stripWakeWord(raw: String): String? {
        val t = normalize(raw)
        if (t.size < 2) return null
        if (t[0] in wakeStarters && t[1] in wakeWords) return t.drop(2).joinToString(" ")
        return null
    }

    fun parse(raw: String): Command? {
        val t = normalize(raw)
        if (t.isEmpty()) return null
        val text = t.joinToString(" ")
        val padded = " $text "
        fun has(vararg phrases: String) = phrases.any { padded.contains(" $it ") }
        fun hasWord(vararg words: String) = words.any { it in t }

        // Stop listening
        if (has("stop listening", "stop voice control", "stop voice", "turn off listening", "stop hands free")) {
            return Command.StopListening
        }

        // Flashlight
        if (hasWord("flashlight", "torch") || has("flash light")) {
            val off = hasWord("off", "disable", "stop")
            return Command.Torch(on = !off)
        }

        // Dial a number
        if (t.first() == "call" || t.first() == "dial") {
            val digits = t.drop(1).joinToString("") { digitWords[it] ?: if (it.all(Char::isDigit)) it else "?" }
            return if (digits.length >= 3 && '?' !in digits) Command.Dial(digits) else null
        }

        // Specific settings screens
        when {
            hasWord("wifi") -> return Command.Panel(PanelKind.WIFI)
            hasWord("bluetooth") -> return Command.Panel(PanelKind.BLUETOOTH)
            hasWord("brightness") || has("display settings") -> return Command.Panel(PanelKind.DISPLAY)
            has("sound settings") -> return Command.Panel(PanelKind.SOUND)
            has("battery saver", "power saving") -> return Command.Panel(PanelKind.BATTERY_SAVER)
            has("location settings") -> return Command.Panel(PanelKind.LOCATION)
        }

        // Information shown on screen (no speech output, nothing leaves the phone)
        when {
            has("battery", "battery level", "battery percentage", "how much battery") -> return Command.Info(InfoKind.BATTERY)
            has("what time is it", "whats the time", "what is the time", "tell me the time", "current time", "time") ->
                return Command.Info(InfoKind.TIME)
            has("whats the date", "what is the date", "todays date", "what day is it", "date") ->
                return Command.Info(InfoKind.DATE)
        }

        // Volume
        if (has("unmute")) return Command.Mute(mute = false)
        if (has("mute")) return Command.Mute(mute = true)
        if (has("max volume", "maximum volume", "full volume", "volume to max", "volume maximum")) return Command.MaxVolume
        val times = extractCount(t)
        val up = has("louder", "turn it up", "turn up", "increase", "raise", "higher", "up")
        val down = has("quieter", "softer", "turn it down", "turn down", "decrease", "reduce", "lower", "down")
        if (hasWord("volume") || has("louder", "quieter", "softer")) {
            if (down && !up) return Command.Volume(up = false, times = times)
            if (up && !down) return Command.Volume(up = true, times = times)
            if (has("louder")) return Command.Volume(up = true, times = times)
            if (has("quieter", "softer")) return Command.Volume(up = false, times = times)
        }

        // Phone buttons and panels
        when {
            has("quick settings") -> return Command.Global(GlobalKind.QUICK_SETTINGS)
            has("notifications", "notification panel", "notification shade", "pull down notifications") ->
                return Command.Global(GlobalKind.NOTIFICATIONS)
            has("recent apps", "recents", "overview", "show recent", "app switcher") ->
                return Command.Global(GlobalKind.RECENTS)
            has("lock screen", "lock the screen", "lock phone", "lock the phone", "lock my phone", "lock") ->
                return Command.Global(GlobalKind.LOCK)
            has("take screenshot", "take a screenshot", "screenshot", "screen shot") ->
                return Command.Global(GlobalKind.SCREENSHOT)
            has("go home", "go to home", "home screen", "home") -> return Command.Global(GlobalKind.HOME)
            has("go back", "back") -> return Command.Global(GlobalKind.BACK)
        }

        // Plain "settings"
        if ("settings" in t && t.size <= 4 && hasWord("open", "go", "phone", "system", "settings")) {
            return Command.Panel(PanelKind.SETTINGS)
        }

        // Scrolling: "scroll down" moves the page down, which means the finger swipes up.
        val scrollDir = listOf("up" to Direction.UP, "down" to Direction.DOWN, "left" to Direction.LEFT, "right" to Direction.RIGHT)
            .firstOrNull { (w, _) -> w in t }?.second
        if (scrollDir != null) {
            if (hasWord("scroll", "page")) return Command.Swipe(opposite(scrollDir))
            if (hasWord("swipe")) return Command.Swipe(scrollDir)
        }

        // Open an app
        val openIdx = t.indexOfFirst { it in openVerbs }
        if (openIdx == 0 || (openIdx == 1 && t[0] == "please")) {
            val name = t.drop(openIdx + 1)
                .dropWhile { it == "the" || it == "my" }
                .filterNot { it == "app" || it == "application" }
                .joinToString(" ")
            if (name.isNotEmpty()) return Command.OpenApp(name)
        }
        if (t.size >= 2 && t[0] == "go" && t[1] == "to") {
            val name = t.drop(2).filterNot { it == "the" || it == "app" }.joinToString(" ")
            if (name.isNotEmpty()) return Command.OpenApp(name)
        }
        return null
    }

    private val openVerbs = setOf("open", "launch", "start", "run", "show")

    private fun opposite(d: Direction) = when (d) {
        Direction.UP -> Direction.DOWN
        Direction.DOWN -> Direction.UP
        Direction.LEFT -> Direction.RIGHT
        Direction.RIGHT -> Direction.LEFT
    }

    private fun extractCount(tokens: List<String>): Int {
        for (w in tokens) {
            val n = w.toIntOrNull() ?: numberWords[w] ?: continue
            if (n in 1..20) return n
        }
        return 1
    }
}
