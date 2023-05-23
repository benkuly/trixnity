package net.folivo.trixnity.olm

import net.folivo.trixnity.crypto.core.SecureRandom
import kotlin.random.Random

internal inline fun <T : Any> checkError(
    ptr: T,
    result: ULong,
    getLastError: (T) -> String?
): ULong {
    if (result == OlmLibrary.error()) {
        throw OlmLibraryException(getLastError(ptr))
    }
    return result
}

internal inline fun <T : Any> withRandom(
    size: ULong,
    random: Random = SecureRandom,
    block: (randomBytes: ByteArray?) -> T
): T {
    val randomBytes = if (size > 0u) random.nextBytes(size.toInt()) else null
    return try {
        block(randomBytes)
    } finally {
        randomBytes?.fill(0)
    }
}

internal inline fun <T : Any> pickle(
    ptr: T,
    key: String,
    length: (T) -> ULong, pickle: (account: T, key: ByteArray, pickled: ByteArray) -> ULong,
    getLastError: (T) -> String?
): String {
    val pickled = ByteArray(length(ptr).toInt())
    val result = pickle(ptr, key.encodeToByteArray(), pickled)
    checkError(ptr, result, getLastError)
    return pickled.decodeToString(endIndex = result.toInt())
}