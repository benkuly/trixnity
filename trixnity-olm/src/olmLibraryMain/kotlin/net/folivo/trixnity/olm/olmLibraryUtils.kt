package net.folivo.trixnity.olm

import kotlin.random.Random

internal inline fun <T> checkError(
    ptr: T,
    result: ULong,
    getLastError: (T) -> String?
): ULong {
    if (result == OlmLibrary.error()) {
        throw OlmLibraryException(getLastError(ptr) ?: "UNKNOWN")
    }
    return result
}

internal inline fun <T> withRandom(
    size: ULong,
    random: Random = Random.Default,
    block: (randomBytes: ByteArray?) -> T
): T {
    val randomBytes = if (size > 0u) random.nextBytes(ByteArray(size.toInt())) else null
    return try {
        block(randomBytes)
    } finally {
        randomBytes?.fill(0)
    }
}

internal inline fun <T> pickle(
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