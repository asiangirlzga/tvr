package com.example.tvremote.protocol

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.cert.X509Certificate
import java.util.Locale
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class HttpReply(val status: Int, val body: String)

/**
 * A tiny HTTP/1.1 GET over a plain socket, used only to talk to a TV on the local network.
 * Using a raw socket means the app doesn't have to switch on Android's app-wide cleartext HTTP setting.
 *
 * With [tls] set it speaks https but does not check the TV's certificate: TVs use self-signed ones, and
 * the official Cloud TV Remote does the same. Nothing secret is sent (only button presses), and the
 * connection is only ever made to the TV's own address on the local network.
 */
object MiniHttp {
    private const val MAX_BODY = 1 shl 20
    private const val MAX_LINE = 8192

    private val trustAnyTv: SSLSocketFactory by lazy {
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(trustAll), null) }.socketFactory
    }

    fun get(
        host: String,
        port: Int,
        path: String,
        readBody: Boolean = true,
        timeoutMs: Int = 4000,
        tls: Boolean = false,
    ): HttpReply {
        val raw = Socket()
        try {
            raw.connect(InetSocketAddress(host, port), timeoutMs)
            raw.soTimeout = timeoutMs
        } catch (e: IOException) {
            raw.close()
            throw e
        }
        val socket: Socket = if (tls) {
            (trustAnyTv.createSocket(raw, host, port, true) as SSLSocket).also { it.startHandshake() }
        } else {
            raw
        }
        socket.use { s ->
            s.soTimeout = timeoutMs
            val hostHeader = if (host.contains(':')) "[${host.substringBefore('%')}]:$port" else "$host:$port"
            val request = "GET $path HTTP/1.1\r\nHost: $hostHeader\r\nUser-Agent: VoiceTvRemote\r\n" +
                "Accept: */*\r\nConnection: close\r\n\r\n"
            s.getOutputStream().apply { write(request.toByteArray(Charsets.US_ASCII)); flush() }

            val input = BufferedInputStream(s.getInputStream())
            val statusLine = readLine(input) ?: throw EOFException("No reply from the TV")
            val status = statusLine.split(' ').getOrNull(1)?.toIntOrNull() ?: throw IOException("Unexpected reply from the TV")
            var length = -1
            var chunked = false
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val i = line.indexOf(':')
                if (i <= 0) continue
                val key = line.substring(0, i).trim().lowercase(Locale.ROOT)
                val value = line.substring(i + 1).trim()
                if (key == "content-length") length = value.toIntOrNull() ?: -1
                if (key == "transfer-encoding" && value.lowercase(Locale.ROOT).contains("chunked")) chunked = true
            }
            if (!readBody) return HttpReply(status, "")
            val body = when {
                chunked -> readChunked(input)
                length >= 0 -> readExact(input, minOf(length, MAX_BODY))
                else -> readToEnd(input)
            }
            return HttpReply(status, body.toString(Charsets.UTF_8))
        }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString().trimEnd('\r')
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            if (sb.length >= MAX_LINE) throw IOException("Header too long")
            sb.append(b.toChar())
        }
    }

    private fun readExact(input: InputStream, n: Int): ByteArray {
        val buf = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = input.read(buf, off, n - off)
            if (r < 0) break
            off += r
        }
        return buf.copyOf(off)
    }

    private fun readChunked(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        while (true) {
            val sizeLine = readLine(input) ?: break
            val size = sizeLine.substringBefore(';').trim().toIntOrNull(16) ?: throw IOException("Bad chunk")
            if (size == 0) break
            if (out.size() + size > MAX_BODY) throw IOException("Reply too large")
            out.write(readExact(input, size))
            readLine(input) // CRLF after each chunk
        }
        return out.toByteArray()
    }

    private fun readToEnd(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        try {
            while (out.size() < MAX_BODY) {
                val r = input.read(buf)
                if (r < 0) break
                out.write(buf, 0, r)
            }
        } catch (e: SocketTimeoutException) {
            // Server kept the connection open; use what arrived.
        }
        return out.toByteArray()
    }
}

data class TvAppInfo(val label: String, val packageName: String)

object TvAppList {
    /** Reads the TV's `/apps` reply. Accepts a JSON array, or an object that contains one. */
    fun parse(body: String): List<TvAppInfo> {
        val root: Any = try {
            JSONTokener(body).nextValue()
        } catch (e: JSONException) {
            return emptyList()
        }
        val out = mutableListOf<TvAppInfo>()
        fun walk(v: Any?) {
            when (v) {
                is JSONArray -> for (i in 0 until v.length()) walk(v.opt(i))
                is JSONObject -> {
                    val pkg = v.optString("packageName", "")
                    if (pkg.isNotEmpty() && pkg != "null") {
                        val label = listOf("appName", "label", "name", "title")
                            .map { v.optString(it, "") }
                            .firstOrNull { it.isNotEmpty() && it != "null" } ?: pkg
                        out += TvAppInfo(label, pkg)
                    } else {
                        v.keys().forEach { walk(v.opt(it)) }
                    }
                }
            }
        }
        walk(root)
        return out.distinctBy { it.packageName }
    }

    private fun key(s: String) = s.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    /** Picks the installed app whose name best matches [label] ("YouTube", "Prime Video"…). */
    fun findPackage(label: String, apps: List<TvAppInfo>): String? {
        val q = key(label)
        if (q.length < 2) return null
        apps.firstOrNull { key(it.label) == q }?.let { return it.packageName }
        return apps
            .filter { val l = key(it.label); l.isNotEmpty() && (l.contains(q) || (l.length >= 4 && q.contains(l))) }
            .minByOrNull { key(it.label).length }?.packageName
    }
}
