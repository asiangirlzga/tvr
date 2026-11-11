package com.example.tvremote.protocol

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Minimal protobuf wire-format encoder/decoder: just enough for the Android TV remote protocol,
 * so the app needs no generated code or protobuf runtime.
 */
class PbWriter {
    private val out = ByteArrayOutputStream()

    private fun rawVarint(value: Long) {
        var v = value
        while ((v and 0x7FL.inv()) != 0L) {
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        out.write(v.toInt())
    }

    private fun tag(field: Int, wireType: Int) = rawVarint(((field shl 3) or wireType).toLong())

    fun varint(field: Int, value: Long): PbWriter {
        tag(field, 0)
        rawVarint(value)
        return this
    }

    fun varint(field: Int, value: Int): PbWriter = varint(field, value.toLong())

    fun bytes(field: Int, value: ByteArray): PbWriter {
        tag(field, 2)
        rawVarint(value.size.toLong())
        out.write(value)
        return this
    }

    fun string(field: Int, value: String): PbWriter = bytes(field, value.toByteArray(Charsets.UTF_8))

    fun message(field: Int, build: PbWriter.() -> Unit): PbWriter =
        bytes(field, PbWriter().apply(build).toByteArray())

    fun toByteArray(): ByteArray = out.toByteArray()

    companion object {
        fun encodeVarint(value: Long): ByteArray = PbWriter().also { it.rawVarint(value) }.toByteArray()
    }
}

class PbMessage private constructor(private val fields: Map<Int, List<Any>>) {
    fun has(field: Int): Boolean = fields.containsKey(field)
    fun varint(field: Int): Long? = fields[field]?.firstOrNull() as? Long
    fun bytes(field: Int): ByteArray? = fields[field]?.firstOrNull() as? ByteArray
    fun string(field: Int): String? = bytes(field)?.toString(Charsets.UTF_8)
    fun message(field: Int): PbMessage? = bytes(field)?.let { parse(it) }

    companion object {
        fun parse(data: ByteArray): PbMessage {
            val fields = HashMap<Int, MutableList<Any>>()
            var pos = 0

            fun readVarint(): Long {
                var result = 0L
                var shift = 0
                while (true) {
                    if (pos >= data.size || shift > 63) throw IOException("Malformed protobuf varint")
                    val b = data[pos++].toInt()
                    result = result or ((b and 0x7F).toLong() shl shift)
                    if ((b and 0x80) == 0) return result
                    shift += 7
                }
            }

            fun readFixed(size: Int): Long {
                if (pos + size > data.size) throw IOException("Truncated protobuf field")
                var result = 0L
                for (i in 0 until size) {
                    result = result or ((data[pos + i].toLong() and 0xFF) shl (8 * i))
                }
                pos += size
                return result
            }

            while (pos < data.size) {
                val tag = readVarint()
                val field = (tag ushr 3).toInt()
                val value: Any = when ((tag and 7L).toInt()) {
                    0 -> readVarint()
                    1 -> readFixed(8)
                    2 -> {
                        val len = readVarint()
                        if (len < 0 || len > data.size - pos) throw IOException("Truncated protobuf field")
                        val chunk = data.copyOfRange(pos, pos + len.toInt())
                        pos += len.toInt()
                        chunk
                    }
                    5 -> readFixed(4)
                    else -> throw IOException("Unsupported protobuf wire type")
                }
                fields.getOrPut(field) { mutableListOf() }.add(value)
            }
            return PbMessage(fields)
        }
    }
}

/** Length-delimited framing used on both the pairing and remote sockets. */
object PbFraming {
    private const val MAX_FRAME_BYTES = 1 shl 20

    fun write(out: OutputStream, payload: ByteArray) {
        out.write(PbWriter.encodeVarint(payload.size.toLong()) + payload)
        out.flush()
    }

    fun read(input: InputStream): ByteArray {
        var length = 0
        var shift = 0
        while (true) {
            val b = input.read()
            if (b < 0) throw EOFException("Connection closed")
            length = length or ((b and 0x7F) shl shift)
            if ((b and 0x80) == 0) break
            shift += 7
            if (shift > 28) throw IOException("Bad frame length")
        }
        if (length < 0 || length > MAX_FRAME_BYTES) throw IOException("Frame too large")
        val buffer = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val n = input.read(buffer, offset, length - offset)
            if (n < 0) throw EOFException("Connection closed")
            offset += n
        }
        return buffer
    }
}
