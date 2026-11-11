package com.example.tvremote.voice

import com.example.tvremote.protocol.TvKey
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale

sealed interface TvAction {
    /** Short text shown to the person after a command, e.g. "Volume up ×3". */
    val description: String

    data class Key(override val description: String, val code: Int, val repeat: Int = 1) : TvAction

    data class Digits(val digits: String) : TvAction {
        override val description: String get() = "Channel $digits"
    }

    data class OpenLink(val link: String, override val description: String) : TvAction
}

private val noiseRegex = Regex("[^\\p{L}\\p{M}\\p{N}\\s]")
private val spacesRegex = Regex("\\s+")

/** Lower-cases, drops punctuation and the Devanagari nukta so spelling variants compare equal. */
internal fun normalizeSpeech(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFC)
        .lowercase(Locale.ROOT)
        .replace("़", "")
        .replace(noiseRegex, " ")
        .replace(spacesRegex, " ")
        .trim()

/**
 * Turns what the person said into a TV action. Runs entirely on the phone.
 * Understands English plus a small set of Hindi / Hinglish phrases.
 */
object VoiceCommandParser {
    fun normalize(s: String): String = normalizeSpeech(s)

    private val numberWords = mapOf(
        "zero" to "0", "one" to "1", "two" to "2", "three" to "3", "four" to "4", "five" to "5",
        "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9", "ten" to "10",
        "twice" to "2", "thrice" to "3",
    )
    private val countFillers = setOf("times", "time", "by", "x")
    private val fillers = setOf(
        "go", "move", "press", "the", "please", "button", "key", "tv", "scroll", "navigate", "to", "hey", "now", "कृपया",
    )
    private val openWords = setOf(
        "open", "launch", "start", "run", "play", "watch", "go", "to", "on", "please", "the", "app",
        "खोलो", "खोलें", "खोलिए", "खोल", "चलाओ", "चलाइए", "चालू", "करो", "कर", "दो", "लगाओ", "ऐप", "एप",
        "kholo", "chalao", "lagao", "karo",
    )

    private class KeyRule(val label: String, val code: Int, raw: List<String>) {
        val phrases: List<String> = raw.map { normalizeSpeech(it) }
    }

    // Order matters: the first rule whose phrase appears as whole words wins.
    private val rules = listOf(
        KeyRule("Volume up", TvKey.VOLUME_UP, listOf(
            "volume up", "increase volume", "raise volume", "louder", "turn it up", "turn up the volume",
            "turn the volume up", "sound up", "volume badhao", "awaaz badhao", "awaz badhao",
            "आवाज बढ़ाओ", "आवाज़ बढ़ाओ", "आवाज बढ़ाइए", "आवाज तेज",
        )),
        KeyRule("Volume down", TvKey.VOLUME_DOWN, listOf(
            "volume down", "decrease volume", "reduce volume", "lower volume", "lower the volume",
            "turn volume down", "turn it down", "turn down the volume", "quieter", "softer",
            "volume kam", "awaaz kam", "awaz kam", "आवाज कम", "आवाज़ कम", "आवाज घटाओ", "आवाज धीमी",
        )),
        KeyRule("Mute", TvKey.VOLUME_MUTE, listOf("mute", "unmute", "silence", "म्यूट", "अनम्यूट")),
        KeyRule("Channel up", TvKey.CHANNEL_UP, listOf("channel up", "next channel", "channel plus", "चैनल ऊपर", "चैनल अप")),
        KeyRule("Channel down", TvKey.CHANNEL_DOWN, listOf("channel down", "previous channel", "channel minus", "चैनल नीचे", "चैनल डाउन")),
        KeyRule("Fast forward", TvKey.MEDIA_FAST_FORWARD, listOf("fast forward", "forward", "skip ahead")),
        KeyRule("Rewind", TvKey.MEDIA_REWIND, listOf("rewind", "fast backward")),
        KeyRule("Next", TvKey.MEDIA_NEXT, listOf("next", "next episode", "next video", "next song", "skip", "अगला")),
        KeyRule("Previous", TvKey.MEDIA_PREVIOUS, listOf("previous", "previous episode", "previous video", "previous song", "prev", "पिछला")),
        KeyRule("Play/pause", TvKey.MEDIA_PLAY_PAUSE, listOf("play pause", "play or pause", "toggle playback")),
        KeyRule("Pause", TvKey.MEDIA_PAUSE, listOf("pause", "stop", "hold on", "रुको", "रोको", "रोक दो", "ruko", "roko")),
        KeyRule("Play", TvKey.MEDIA_PLAY, listOf("play", "resume", "continue", "चलाओ", "चलाइए", "शुरू करो", "chalao", "shuru karo")),
        KeyRule("Home", TvKey.HOME, listOf("home", "home screen", "main screen", "होम", "होम स्क्रीन")),
        KeyRule("Back", TvKey.BACK, listOf("back", "go back", "return", "वापस", "पीछे", "wapas", "peeche")),
        KeyRule("Menu", TvKey.MENU, listOf("menu", "options", "मेनू", "मेन्यू")),
        KeyRule("Settings", TvKey.SETTINGS, listOf("settings", "setting", "सेटिंग", "सेटिंग्स")),
        KeyRule("Input", TvKey.TV_INPUT, listOf("input", "change input", "source", "hdmi", "इनपुट")),
        KeyRule("Info", TvKey.INFO, listOf("info", "information")),
        KeyRule("Guide", TvKey.GUIDE, listOf("guide", "tv guide")),
        KeyRule("Power", TvKey.POWER, listOf(
            "power off", "power on", "power", "turn off", "turn on", "switch off", "switch on", "shut down",
            "standby", "go to sleep", "sleep", "बंद करो", "बंद कर दो", "चालू करो", "चालू कर दो", "band karo", "chalu karo",
        )),
    )

    // Short navigation words only count when they are the whole command, so "volume up" never means "up".
    private val exactRules = listOf(
        KeyRule("Up", TvKey.DPAD_UP, listOf("up", "upward", "ऊपर", "oopar")),
        KeyRule("Down", TvKey.DPAD_DOWN, listOf("down", "downward", "नीचे", "neeche")),
        KeyRule("Left", TvKey.DPAD_LEFT, listOf("left", "बाएं", "बायें")),
        KeyRule("Right", TvKey.DPAD_RIGHT, listOf("right", "दाएं", "दायें")),
        KeyRule("OK", TvKey.DPAD_CENTER, listOf("ok", "okay", "select", "enter", "click", "center", "confirm", "ठीक है", "ओके", "सेलेक्ट")),
        KeyRule("Search", TvKey.SEARCH, listOf("search")),
    )

    private val aliasIndex: List<Pair<String, TvApp>> =
        TvApps.all.flatMap { app -> app.aliases.map { normalizeSpeech(it) to app } }.sortedByDescending { it.first.length }

    private val youtubeOn = Regex("^(?:search|search for|find|play|watch|show me) (.+) (?:on|in) (?:youtube|you tube|यूट्यूब)$")
    private val youtubeSearch = Regex("^(?:search|find) (?:youtube|you tube|यूट्यूब) (?:for )?(.+)$")
    private val channelNumber = Regex("^(?:(?:go|switch|change|tune) to )?(?:channel|चैनल)(?: number| नंबर)? (\\d{1,4})$")

    /** Returns the action for [raw] speech, or null if nothing matched. */
    fun parse(raw: String): TvAction? {
        val base = normalize(raw)
        if (base.isEmpty()) return null
        val tokens = base.split(' ').map { numberWords[it] ?: it }
        val text = tokens.joinToString(" ")

        searchYoutube(text)?.let { return it }
        openApp(text)?.let { return it }
        channelNumber.find(text)?.let { return TvAction.Digits(it.groupValues[1]) }

        var count = 1
        var words = tokens
        val numberIndex = tokens.indexOfFirst { it.length <= 2 && it.all { c -> c.isDigit() } }
        if (numberIndex >= 0) {
            count = tokens[numberIndex].toInt().coerceIn(1, 20)
            words = tokens.filterIndexed { i, w -> i != numberIndex && w !in countFillers }
        }

        val padded = " ${words.joinToString(" ")} "
        for (rule in rules) {
            if (rule.phrases.any { padded.contains(" $it ") }) return key(rule, count)
        }
        val bare = words.filter { it !in fillers }.joinToString(" ")
        for (rule in exactRules) {
            if (bare in rule.phrases) return key(rule, count)
        }
        return null
    }

    private fun key(rule: KeyRule, count: Int) =
        TvAction.Key(if (count > 1) "${rule.label} ×$count" else rule.label, rule.code, count)

    private fun searchYoutube(text: String): TvAction? {
        val query = (youtubeOn.find(text) ?: youtubeSearch.find(text))?.groupValues?.get(1)?.trim()
        if (query.isNullOrEmpty()) return null
        val encoded = URLEncoder.encode(query, "UTF-8")
        return TvAction.OpenLink("https://www.youtube.com/results?search_query=$encoded", "YouTube: $query")
    }

    private fun openApp(text: String): TvAction? {
        val padded = " $text "
        for ((alias, app) in aliasIndex) {
            if (!padded.contains(" $alias ")) continue
            val rest = padded.replace(" $alias ", " ").trim().split(' ').filter { it.isNotEmpty() }
            if (rest.all { it in openWords }) return TvAction.OpenLink(app.link, "Opening ${app.label}")
        }
        return null
    }
}
