package com.example.tvremote.protocol

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import kotlin.concurrent.thread

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data object Ready : ConnectionState
    data class Failed(val message: String, val needsPairing: Boolean = false) : ConnectionState
}

/**
 * Talks to a paired TV on port 6466. Create one per connection attempt; call [close] when done.
 * Sending is non-blocking and safe to call from any thread.
 */
class RemoteClient(
    private val certStore: CertStore,
    private val host: String,
    private val pinnedFingerprint: String?,
) {
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val outbox = Channel<ByteArray>(Channel.UNLIMITED)

    @Volatile private var socket: SSLSocket? = null
    @Volatile private var ready = false
    @Volatile private var closed = false
    private var job: Job? = null

    fun connect() {
        if (job != null) return
        _state.value = ConnectionState.Connecting
        job = scope.launch {
            try {
                runSession()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!closed) _state.value = describe(e)
            } finally {
                closeSocket()
            }
        }
    }

    fun sendKey(code: Int, direction: Int = DIRECTION_SHORT) {
        outbox.trySend(
            PbWriter().message(F_KEY_INJECT) { varint(1, code); varint(2, direction) }.toByteArray(),
        )
    }

    /** Launches an app or deep link, e.g. "https://www.youtube.com" or "market://launch?id=<package>". */
    fun launchApp(link: String) {
        outbox.trySend(PbWriter().message(F_APP_LINK) { string(1, link) }.toByteArray())
    }

    fun close() {
        closed = true
        outbox.close()
        job?.cancel()
        scope.cancel()
        closeSocket()
        _state.value = ConnectionState.Disconnected
    }

    private suspend fun runSession() {
        val s = certStore.sslContext(PinningTrustManager(pinnedFingerprint))
            .socketFactory.createSocket() as SSLSocket
        socket = s
        s.connect(InetSocketAddress(host, REMOTE_PORT), CONNECT_TIMEOUT_MS)
        // The TV pings every few seconds, so a long silence means the connection is dead.
        s.soTimeout = READ_TIMEOUT_MS
        s.startHandshake()
        val input = s.inputStream
        val output = s.outputStream

        coroutineScope {
            val writer = launch {
                for (frame in outbox) PbFraming.write(output, frame)
            }
            try {
                while (true) handle(PbMessage.parse(PbFraming.read(input)))
            } finally {
                writer.cancel()
            }
        }
    }

    private fun handle(msg: PbMessage) {
        when {
            msg.has(F_CONFIGURE) -> outbox.trySend(
                PbWriter().message(F_CONFIGURE) {
                    varint(1, CODE_622)
                    message(2) {
                        string(1, "Voice TV Remote") // model
                        string(2, "Open Source")      // vendor
                        varint(3, 1)
                        string(4, "1")
                        string(5, "com.example.tvremote")
                        string(6, "1.0")
                    }
                }.toByteArray(),
            )
            msg.has(F_SET_ACTIVE) -> {
                outbox.trySend(PbWriter().message(F_SET_ACTIVE) { varint(1, CODE_622) }.toByteArray())
                markReady()
            }
            msg.has(F_PING_REQUEST) -> {
                val value = msg.message(F_PING_REQUEST)?.varint(1) ?: 0L
                outbox.trySend(PbWriter().message(F_PING_RESPONSE) { varint(1, value) }.toByteArray())
            }
            msg.has(F_ERROR) -> throw IOException("The TV reported an error.")
            msg.has(F_START) -> markReady()
        }
    }

    private fun markReady() {
        ready = true
        _state.value = ConnectionState.Ready
    }

    private fun closeSocket() {
        val s = socket
        socket = null
        if (s != null) thread { runCatching { s.close() } }
    }

    private fun describe(e: Exception): ConnectionState.Failed {
        val rejected = !ready && (
            e is SSLException ||
                e is EOFException ||
                (e is SocketException && e !is ConnectException && e !is NoRouteToHostException)
            )
        return when {
            rejected -> ConnectionState.Failed("The TV didn't accept this phone. Pair it again.", needsPairing = true)
            e is SocketTimeoutException || e is ConnectException || e is NoRouteToHostException ->
                ConnectionState.Failed("Couldn't reach the TV. Check that it's on and on the same Wi-Fi.")
            e is EOFException || e is SocketException -> ConnectionState.Failed("Lost connection to the TV.")
            else -> ConnectionState.Failed(e.message ?: "Connection error.")
        }
    }

    companion object {
        const val DIRECTION_SHORT = 3
        private const val REMOTE_PORT = 6466
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 40_000
        private const val CODE_622 = 622

        private const val F_CONFIGURE = 1
        private const val F_SET_ACTIVE = 2
        private const val F_ERROR = 3
        private const val F_PING_REQUEST = 8
        private const val F_PING_RESPONSE = 9
        private const val F_KEY_INJECT = 10
        private const val F_START = 40
        private const val F_APP_LINK = 90
    }
}
