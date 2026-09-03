package com.apex.files.core

import java.io.InputStream
import java.security.MessageDigest

enum class HashAlgorithm(val id: String) {
    SHA256("SHA-256"),
    MD5("MD5");

    val hexLength: Int
        get() = when (this) {
            SHA256 -> 64
            MD5 -> 32
        }
}

/** Streaming digest computation, cancellable via [isActive]. */
object HashUtil {

    suspend fun hash(stream: InputStream, algorithm: HashAlgorithm, isActive: suspend () -> Boolean = { true }): String {
        val digest = MessageDigest.getInstance(algorithm.id)
        val buffer = ByteArray(64 * 1024)
        while (true) {
            if (!isActive()) throw kotlinx.coroutines.CancellationException("hash cancelled")
            val read = stream.read(buffer)
            if (read < 0) break
            if (read > 0) digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}