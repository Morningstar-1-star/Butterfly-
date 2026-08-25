package com.example.torrent.bencode

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

object Bencode {

    fun decode(bytes: ByteArray): Any? {
        val stream = ByteArrayInputStream(bytes)
        return decodeFromStream(stream)
    }

    fun decode(stream: InputStream): Any? {
        return decodeFromStream(stream)
    }

    private fun decodeFromStream(stream: InputStream): Any? {
        val b = stream.read()
        if (b == -1) return null

        return when (b.toChar()) {
            'i' -> decodeInteger(stream)
            'l' -> decodeList(stream)
            'd' -> decodeDictionary(stream)
            in '0'..'9' -> decodeStringOrBytes(b, stream)
            else -> null
        }
    }

    private fun decodeInteger(stream: InputStream): Long {
        val sb = StringBuilder()
        while (true) {
            val b = stream.read()
            if (b == -1 || b.toChar() == 'e') break
            sb.append(b.toChar())
        }
        return sb.toString().toLongOrNull() ?: 0L
    }

    private fun decodeStringOrBytes(firstChar: Int, stream: InputStream): ByteArray {
        val lengthSb = StringBuilder()
        lengthSb.append(firstChar.toChar())
        while (true) {
            val b = stream.read()
            if (b == -1 || b.toChar() == ':') break
            lengthSb.append(b.toChar())
        }
        val length = lengthSb.toString().toIntOrNull() ?: 0
        val buffer = ByteArray(length)
        var totalRead = 0
        while (totalRead < length) {
            val read = stream.read(buffer, totalRead, length - totalRead)
            if (read == -1) break
            totalRead += read
        }
        return buffer
    }

    private fun decodeList(stream: InputStream): List<Any?> {
        val list = mutableListOf<Any?>()
        while (true) {
            val markStream = stream as? ByteArrayInputStream
            if (markStream != null) {
                markStream.mark(1)
                val next = markStream.read()
                if (next == -1 || next.toChar() == 'e') break
                markStream.reset()
            }
            val element = decodeFromStream(stream)
            list.add(element)
        }
        return list
    }

    private fun decodeDictionary(stream: InputStream): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>()
        while (true) {
            val markStream = stream as? ByteArrayInputStream
            if (markStream != null) {
                markStream.mark(1)
                val next = markStream.read()
                if (next == -1 || next.toChar() == 'e') break
                markStream.reset()
            }
            val keyBytes = decodeFromStream(stream) as? ByteArray ?: break
            val key = String(keyBytes, StandardCharsets.UTF_8)
            val value = decodeFromStream(stream)
            map[key] = value
        }
        return map
    }

    fun encode(obj: Any?): ByteArray {
        val out = ByteArrayOutputStream()
        encodeToStream(obj, out)
        return out.toByteArray()
    }

    private fun encodeToStream(obj: Any?, out: ByteArrayOutputStream) {
        when (obj) {
            is Number -> {
                out.write('i'.code)
                out.write(obj.toLong().toString().toByteArray(StandardCharsets.US_ASCII))
                out.write('e'.code)
            }
            is String -> {
                val bytes = obj.toByteArray(StandardCharsets.UTF_8)
                out.write(bytes.size.toString().toByteArray(StandardCharsets.US_ASCII))
                out.write(':'.code)
                out.write(bytes)
            }
            is ByteArray -> {
                out.write(obj.size.toString().toByteArray(StandardCharsets.US_ASCII))
                out.write(':'.code)
                out.write(obj)
            }
            is List<*> -> {
                out.write('l'.code)
                for (item in obj) {
                    encodeToStream(item, out)
                }
                out.write('e'.code)
            }
            is Map<*, *> -> {
                out.write('d'.code)
                val sortedKeys = obj.keys.map { it.toString() }.sorted()
                for (k in sortedKeys) {
                    val kBytes = k.toByteArray(StandardCharsets.UTF_8)
                    out.write(kBytes.size.toString().toByteArray(StandardCharsets.US_ASCII))
                    out.write(':'.code)
                    out.write(kBytes)
                    encodeToStream(obj[k], out)
                }
                out.write('e'.code)
            }
        }
    }

    fun getString(map: Map<String, Any?>, key: String, default: String = ""): String {
        val v = map[key] ?: return default
        return when (v) {
            is ByteArray -> String(v, StandardCharsets.UTF_8)
            is String -> v
            else -> v.toString()
        }
    }

    fun getLong(map: Map<String, Any?>, key: String, default: Long = 0L): Long {
        val v = map[key] ?: return default
        return when (v) {
            is Number -> v.toLong()
            is ByteArray -> String(v, StandardCharsets.US_ASCII).toLongOrNull() ?: default
            is String -> v.toLongOrNull() ?: default
            else -> default
        }
    }

    fun getBytes(map: Map<String, Any?>, key: String): ByteArray? {
        val v = map[key] ?: return null
        return when (v) {
            is ByteArray -> v
            is String -> v.toByteArray(StandardCharsets.UTF_8)
            else -> null
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun getList(map: Map<String, Any?>, key: String): List<Any?> {
        return (map[key] as? List<Any?>) ?: emptyList()
    }

    @Suppress("UNCHECKED_CAST")
    fun getDict(map: Map<String, Any?>, key: String): Map<String, Any?>? {
        return map[key] as? Map<String, Any?>
    }
}
