package com.example.privatevoice.command

enum class GlobalKind { HOME, BACK, RECENTS, NOTIFICATIONS, QUICK_SETTINGS, LOCK, SCREENSHOT }

enum class Direction { UP, DOWN, LEFT, RIGHT }

enum class PanelKind { SETTINGS, WIFI, BLUETOOTH, DISPLAY, SOUND, BATTERY_SAVER, LOCATION }

enum class InfoKind { TIME, DATE, BATTERY }

/** Everything the app can do for you. Each one is a fixed, harmless phone action. */
sealed interface Command {
    data class Global(val kind: GlobalKind) : Command

    /** [finger] is the direction the finger moves, so "scroll down" is a swipe UP. */
    data class Swipe(val finger: Direction) : Command

    data class Volume(val up: Boolean, val times: Int) : Command
    data class Mute(val mute: Boolean) : Command
    data object MaxVolume : Command
    data class Torch(val on: Boolean) : Command
    data class Panel(val kind: PanelKind) : Command

    /** Opens the dialer with the number filled in. You still press the call button yourself. */
    data class Dial(val digits: String) : Command

    data class Info(val kind: InfoKind) : Command
    data class OpenApp(val query: String) : Command
    data object StopListening : Command
}
