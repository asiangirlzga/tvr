package com.example.tvremote.protocol

import android.content.Context
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Holds this phone's identity: a self-signed client certificate created on first run.
 * The TV remembers it after pairing, which is how it recognises the app on later connections.
 * The key lives in app-private storage and is excluded from backups.
 */
class CertStore(context: Context) {
    private val keyFile = File(context.filesDir, "client_key.der")
    private val certFile = File(context.filesDir, "client_cert.der")
    private var cached: Pair<PrivateKey, X509Certificate>? = null

    @Synchronized
    private fun identity(): Pair<PrivateKey, X509Certificate> {
        cached?.let { return it }
        val id = runCatching { load() }.getOrNull() ?: generate()
        cached = id
        return id
    }

    private fun load(): Pair<PrivateKey, X509Certificate>? {
        if (!keyFile.exists() || !certFile.exists()) return null
        val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
        val cert = certFile.inputStream().use {
            CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
        }
        return key to cert
    }

    private fun generate(): Pair<PrivateKey, X509Certificate> {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val name = X500Name("CN=atvremote")
        val now = System.currentTimeMillis()
        val builder = JcaX509v3CertificateBuilder(
            name,
            BigInteger.valueOf(now),
            Date(now - DAY_MS),
            Date(now + 20L * 365 * DAY_MS),
            name,
            keyPair.public,
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        val cert = JcaX509CertificateConverter().getCertificate(builder.build(signer))
        keyFile.writeBytes(keyPair.private.encoded)
        certFile.writeBytes(cert.encoded)
        return keyPair.private to cert
    }

    fun clientCertificate(): X509Certificate = identity().second

    fun sslContext(trustManager: X509TrustManager): SSLContext {
        val (key, cert) = identity()
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setKeyEntry("client", key, PASSWORD, arrayOf<Certificate>(cert))
        }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, PASSWORD)
        return SSLContext.getInstance("TLS").apply {
            init(kmf.keyManagers, arrayOf(trustManager), null)
        }
    }

    companion object {
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private val PASSWORD = "tvremote".toCharArray() // in-memory keystore only

        fun fingerprint(cert: Certificate): String =
            MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { "%02x".format(it) }
    }
}

/**
 * TVs present self-signed certificates, so normal CA validation can't work. Instead we pin:
 * the first connection (pairing) records the TV's certificate fingerprint, and every later
 * connection must present the same one. Pass null only while pairing.
 */
class PinningTrustManager(private val expectedFingerprint: String?) : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CertificateException("Client certificates are not accepted")
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("The TV sent no certificate")
        if (expectedFingerprint != null && CertStore.fingerprint(leaf) != expectedFingerprint) {
            throw CertificateException("The TV's certificate changed")
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
