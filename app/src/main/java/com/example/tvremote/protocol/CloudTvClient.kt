package com.example.tvremote.protocol

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URLEncoder

/**
 * Controls a CloudWalker "Cloud TV" over its local web API (found by looking at the official
 * Cloud TV Remote app): no pairing, just web requests on the port the TV advertises.
 *
 *   GET /tvInfo                      TV details (used to check the TV is reachable)
 *   GET /apps                        installed apps
 *   GET /input/?keycode=<key>        press a button
 *   GET /play?packageName=<package>  open an app
 *
 * The official app's code doesn't show whether the TV uses http or https, or whether buttons are
 * sent as Android key numbers or key names, so this client works that out on the first connection:
 * it tries http, then https, and tries key numbers, then key names. [forceKeyNames] overrides the
 * key format from the app's settings.
 */
class CloudTvClient(
    private val host: String,
    private val port: Int,
    private val forceKeyNames: () -> Boolean,
) : TvController {
    private enum class KeyFormat { UNKNOWN, NUMBERS, NAMES }

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _diagnostic = MutableStateFlow("")
    override val diagnostic: StateFlow<String> = _diagnostic.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tasks = Channel<() -> Unit>(Channel.UNLIMITED)
    private var job: Job? = null

    @Volatile private var closed = false
    @Volatile private var tls = false
    @Volatile private var keyFormat = KeyFormat.UNKNOWN
    private var apps: List<TvAppInfo>? = null

    override fun connect() {
        if (job != null) return
        _state.value = ConnectionState.Connecting
        job = scope.launch {
            try {
                probe()
                _state.value = ConnectionState.Ready
                // Buttons are sent one at a time, in order.
                for (task in tasks) task()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!closed) _state.value = describe(e)
            }
        }
    }

    override fun sendKey(code: Int) {
        tasks.trySend { press(code) }
    }

    override fun launchApp(link: String, label: String?) {
        tasks.trySend {
            val pkg = resolvePackage(link, label)
            if (pkg != null) {
                get("/play?packageName=" + enc(pkg), readBody = false)
            } else {
                _diagnostic.value = "Couldn't work out which app to open" + (label?.let { " for “$it”" } ?: "")
            }
        }
    }

    override fun close() {
        closed = true
        tasks.close()
        job?.cancel()
        scope.cancel()
        _state.value = ConnectionState.Disconnected
    }

    /** Finds out whether the TV speaks http or https. Succeeds when /tvInfo answers with a 2xx. */
    private fun probe() {
        var lastError: Exception? = null
        var lastStatus: Int? = null
        for (secure in listOf(false, true)) {
            try {
                val r = MiniHttp.get(host, port, "/tvInfo", tls = secure)
                _diagnostic.value = "GET /tvInfo → ${r.status}" + if (secure) " (https)" else ""
                if (r.status in 200..299) {
                    tls = secure
                    return
                }
                lastStatus = r.status
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }
        if (lastStatus != null) throw IOException("The TV answered with error $lastStatus.")
        throw lastError ?: IOException("No reply from the TV.")
    }

    private fun press(code: Int) {
        val number = code.toString()
        val name = TvKey.nameOf(code)
        val candidates = when {
            forceKeyNames() || keyFormat == KeyFormat.NAMES -> listOf(name)
            keyFormat == KeyFormat.NUMBERS -> listOf(number)
            else -> listOf(number, name).distinct() // not known yet: numbers first, then names
        }
        for (key in candidates) {
            val r = get("/input/?keycode=" + enc(key), readBody = false)
            if (r.status in 200..299) {
                if (keyFormat == KeyFormat.UNKNOWN && !forceKeyNames()) {
                    keyFormat = if (key == number) KeyFormat.NUMBERS else KeyFormat.NAMES
                }
                return
            }
        }
    }

    /** One request, retried once on a network hiccup. Records the result for the on-screen note. */
    private fun get(path: String, readBody: Boolean = true): HttpReply {
        var attempt = 0
        while (true) {
            try {
                val r = MiniHttp.get(host, port, path, readBody, tls = tls)
                _diagnostic.value = "GET $path → ${r.status}" + if (tls) " (https)" else ""
                return r
            } catch (e: IOException) {
                if (++attempt >= 2 || closed) throw e
            }
        }
    }

    private fun resolvePackage(link: String, label: String?): String? {
        val name = label ?: if (link.startsWith("https://www.youtube.com")) "YouTube" else null
        if (name != null) {
            val list = apps ?: runCatching { TvAppList.parse(get("/apps").body) }
                .getOrDefault(emptyList()).also { if (it.isNotEmpty()) apps = it }
            TvAppList.findPackage(name, list)?.let { return it }
        }
        // Fall back to the usual Android TV package for the app.
        Regex("[?&]id=([^&]+)").find(link)?.groupValues?.get(1)?.let { return it }
        return if (link.startsWith("https://www.youtube.com")) "com.google.android.youtube.tv" else null
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun describe(e: Exception): ConnectionState.Failed = when (e) {
        is SocketTimeoutException, is ConnectException, is NoRouteToHostException ->
            ConnectionState.Failed("Couldn't reach the TV. Check that it's on and on the same Wi-Fi.")
        is IOException -> ConnectionState.Failed(e.message?.takeIf { it.isNotBlank() } ?: "Lost connection to the TV.")
        else -> ConnectionState.Failed(e.message ?: "Connection error.")
    }
}
