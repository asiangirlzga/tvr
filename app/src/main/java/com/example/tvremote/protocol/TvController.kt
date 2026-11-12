package com.example.tvremote.protocol

import kotlinx.coroutines.flow.StateFlow

/** What the rest of the app needs from a TV connection, whichever kind of TV it is. */
interface TvController {
    val state: StateFlow<ConnectionState>

    /** Short note about the last request (e.g. "GET /input/?keycode=24 → 200"), shown to help fix problems. */
    val diagnostic: StateFlow<String>

    fun connect()
    fun sendKey(code: Int)

    /**
     * Opens an app. [link] is a deep link (may be empty) and [label] the app's display name when known
     * (e.g. "YouTube"). Controllers use whichever they can work with.
     */
    fun launchApp(link: String, label: String?)

    fun close()
}
