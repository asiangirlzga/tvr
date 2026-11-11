package com.example.tvremote.protocol

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLSocket
import kotlin.concurrent.thread

class PairingException(message: String, val retryable: Boolean = false) : Exception(message)

/**
 * Pairs with an Android TV / Google TV on port 6467.
 *
 * Flow: [begin] connects and walks the handshake until the TV shows a 6-character code,
 * then [finish] proves we know that code. Returns the TV's certificate fingerprint on success.
 */
class PairingClient(
    private val certStore: CertStore,
    private val host: String,
) {
    private var socket: SSLSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private var serverCert: X509Certificate? = null

    suspend fun begin(): Unit = withContext(Dispatchers.IO) {
        val s = certStore.sslContext(PinningTrustManager(null)).socketFactory.createSocket() as SSLSocket
        socket = s
        s.connect(InetSocketAddress(host, PAIRING_PORT), CONNECT_TIMEOUT_MS)
        s.soTimeout = READ_TIMEOUT_MS
        s.startHandshake()
        serverCert = s.session.peerCertificates.firstOrNull() as? X509Certificate
            ?: throw PairingException("The TV didn't present a certificate.")
        input = s.inputStream
        output = s.outputStream

        send { message(F_PAIRING_REQUEST) { string(1, SERVICE_NAME); string(2, CLIENT_NAME) } }
        expect(F_PAIRING_REQUEST_ACK)

        send {
            message(F_OPTIONS) {
                message(1) { varint(1, ENCODING_HEX); varint(2, CODE_LENGTH) }
                varint(3, ROLE_INPUT)
            }
        }
        expect(F_OPTIONS)

        send {
            message(F_CONFIGURATION) {
                message(1) { varint(1, ENCODING_HEX); varint(2, CODE_LENGTH) }
                varint(2, ROLE_INPUT)
            }
        }
        expect(F_CONFIGURATION_ACK)
        // The TV is now displaying the code.
        Unit
    }

    suspend fun finish(rawCode: String): String = withContext(Dispatchers.IO) {
        val code = rawCode.trim().uppercase()
        if (!CODE_REGEX.matches(code)) {
            throw PairingException("Enter the 6-character code shown on the TV.", retryable = true)
        }
        val secret = computeSecret(code)
        // The first byte of the hash must equal the first byte of the code; catches typos locally.
        if ((secret[0].toInt() and 0xFF) != code.substring(0, 2).toInt(16)) {
            throw PairingException("That code doesn't match what's on the TV. Check it and try again.", retryable = true)
        }
        send { message(F_SECRET) { bytes(1, secret) } }
        expect(F_SECRET_ACK)
        CertStore.fingerprint(serverCert ?: throw PairingException("Pairing wasn't started."))
    }

    fun close() {
        val s = socket
        socket = null
        if (s != null) thread { runCatching { s.close() } }
    }

    private fun computeSecret(code: String): ByteArray {
        val clientKey = certStore.clientCertificate().publicKey as? RSAPublicKey
            ?: throw PairingException("Unsupported client key.")
        val serverKey = serverCert?.publicKey as? RSAPublicKey
            ?: throw PairingException("This TV uses a certificate type the app doesn't support.")
        val md = MessageDigest.getInstance("SHA-256")
        md.update(unsigned(clientKey.modulus))
        md.update(unsigned(clientKey.publicExponent))
        md.update(unsigned(serverKey.modulus))
        md.update(unsigned(serverKey.publicExponent))
        md.update(hexToBytes(code.substring(2)))
        return md.digest()
    }

    private fun send(build: PbWriter.() -> Unit) {
        val payload = PbWriter().varint(1, PROTOCOL_VERSION).varint(2, STATUS_OK).apply(build).toByteArray()
        PbFraming.write(output ?: throw PairingException("Not connected."), payload)
    }

    private fun expect(field: Int): PbMessage {
        val msg = PbMessage.parse(PbFraming.read(input ?: throw PairingException("Not connected.")))
        val status = msg.varint(2)
        if (status == STATUS_BAD_SECRET.toLong()) throw PairingException("The TV rejected the code. Start pairing again.")
        if (status != STATUS_OK.toLong()) throw PairingException("The TV declined pairing (status $status).")
        if (!msg.has(field)) throw PairingException("The TV sent an unexpected reply.")
        return msg
    }

    private fun unsigned(n: BigInteger): ByteArray {
        val bytes = n.toByteArray()
        return if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
    }

    private fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private companion object {
        const val PAIRING_PORT = 6467
        const val CONNECT_TIMEOUT_MS = 5_000
        const val READ_TIMEOUT_MS = 15_000
        const val SERVICE_NAME = "atvremote"
        const val CLIENT_NAME = "Voice TV Remote"
        const val PROTOCOL_VERSION = 2
        const val STATUS_OK = 200
        const val STATUS_BAD_SECRET = 402
        const val ENCODING_HEX = 3
        const val CODE_LENGTH = 6
        const val ROLE_INPUT = 1

        const val F_PAIRING_REQUEST = 10
        const val F_PAIRING_REQUEST_ACK = 11
        const val F_OPTIONS = 20
        const val F_CONFIGURATION = 30
        const val F_CONFIGURATION_ACK = 31
        const val F_SECRET = 40
        const val F_SECRET_ACK = 41

        val CODE_REGEX = Regex("[0-9A-F]{6}")
    }
}
