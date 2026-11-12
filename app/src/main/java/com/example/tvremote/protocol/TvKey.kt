package com.example.tvremote.protocol

/** Android KeyEvent codes understood by Android TV / Google TV. */
object TvKey {
    const val HOME = 3
    const val BACK = 4
    const val DPAD_UP = 19
    const val DPAD_DOWN = 20
    const val DPAD_LEFT = 21
    const val DPAD_RIGHT = 22
    const val DPAD_CENTER = 23
    const val VOLUME_UP = 24
    const val VOLUME_DOWN = 25
    const val POWER = 26
    const val MENU = 82
    const val SEARCH = 84
    const val MEDIA_PLAY_PAUSE = 85
    const val MEDIA_NEXT = 87
    const val MEDIA_PREVIOUS = 88
    const val MEDIA_REWIND = 89
    const val MEDIA_FAST_FORWARD = 90
    const val MEDIA_PLAY = 126
    const val MEDIA_PAUSE = 127
    const val VOLUME_MUTE = 164
    const val INFO = 165
    const val CHANNEL_UP = 166
    const val CHANNEL_DOWN = 167
    const val GUIDE = 172
    const val SETTINGS = 176
    const val TV_INPUT = 178

    /** KEYCODE_0 is 7, so digits 0-9 map to 7-16. */
    fun digit(d: Int): Int = 7 + d

    private val names = mapOf(
        HOME to "HOME", BACK to "BACK", DPAD_UP to "DPAD_UP", DPAD_DOWN to "DPAD_DOWN",
        DPAD_LEFT to "DPAD_LEFT", DPAD_RIGHT to "DPAD_RIGHT", DPAD_CENTER to "DPAD_CENTER",
        VOLUME_UP to "VOLUME_UP", VOLUME_DOWN to "VOLUME_DOWN", POWER to "POWER", MENU to "MENU",
        SEARCH to "SEARCH", MEDIA_PLAY_PAUSE to "MEDIA_PLAY_PAUSE", MEDIA_NEXT to "MEDIA_NEXT",
        MEDIA_PREVIOUS to "MEDIA_PREVIOUS", MEDIA_REWIND to "MEDIA_REWIND",
        MEDIA_FAST_FORWARD to "MEDIA_FAST_FORWARD", MEDIA_PLAY to "MEDIA_PLAY", MEDIA_PAUSE to "MEDIA_PAUSE",
        VOLUME_MUTE to "VOLUME_MUTE", INFO to "INFO", CHANNEL_UP to "CHANNEL_UP",
        CHANNEL_DOWN to "CHANNEL_DOWN", GUIDE to "GUIDE", SETTINGS to "SETTINGS", TV_INPUT to "TV_INPUT",
    )

    /** Android's name for a key (without the KEYCODE_ prefix), or the number if there is none. */
    fun nameOf(code: Int): String = names[code] ?: if (code in 7..16) "KEYCODE_${code - 7}" else code.toString()
}
