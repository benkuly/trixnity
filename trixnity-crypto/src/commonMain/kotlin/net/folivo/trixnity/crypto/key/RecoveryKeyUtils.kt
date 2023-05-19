package net.folivo.trixnity.crypto.key

import io.ktor.util.*
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent.AesHmacSha2Key
import net.folivo.trixnity.crypto.core.createAesHmacSha2MacFromKey
import net.folivo.trixnity.crypto.core.decodeBase58
import net.folivo.trixnity.crypto.core.encodeBase58
import net.folivo.trixnity.crypto.core.generatePbkdf2Sha512

private val recoveryKeyPrefix = listOf(0x8B.toByte(), 0x01.toByte())

fun encodeRecoveryKey(recoveryKey: ByteArray): String {
    val recoveryKeyWithPrefix = recoveryKeyPrefix + recoveryKey.toList()
    return (recoveryKeyWithPrefix +
            recoveryKeyWithPrefix.fold(0x00) { parity, byte -> parity xor byte.toInt() }.toByte())
        .toByteArray().encodeBase58().chunked(4).joinToString(" ")
}

fun decodeRecoveryKey(encodedRecoveryKey: String): ByteArray {
    val recoveryKey = encodedRecoveryKey.filterNot { it.isWhitespace() }.decodeBase58()
    recoveryKeyPrefix.forEachIndexed { index, prefix ->
        if (recoveryKey.getOrNull(index) != prefix)
            throw RecoveryKeyInvalidException("wrong prefix")
    }
    if (recoveryKey.fold(0x00) { parity, byte -> parity xor byte.toInt() } != 0)
        throw RecoveryKeyInvalidException("wrong parity")
    val recoveryKeyLength = 32
    if (recoveryKey.size != recoveryKeyLength + recoveryKeyPrefix.size + 1)
        throw RecoveryKeyInvalidException("wrong recovery key length")
    return recoveryKey.copyOfRange(recoveryKeyPrefix.size, recoveryKey.size - 1)
}

suspend fun recoveryKeyFromPassphrase(
    passphrase: String,
    info: AesHmacSha2Key.SecretStorageKeyPassphrase
): ByteArray {
    return when (info) {
        is AesHmacSha2Key.SecretStorageKeyPassphrase.Pbkdf2 -> {
            generatePbkdf2Sha512(
                password = passphrase,
                salt = info.salt.encodeToByteArray(),
                iterationCount = info.iterations,
                keyBitLength = info.bits ?: (32 * 8)
            )
        }

        is AesHmacSha2Key.SecretStorageKeyPassphrase.Unknown ->
            throw IllegalArgumentException("unknown algorithm not supported")
    }
}

suspend fun checkRecoveryKey(key: ByteArray, info: AesHmacSha2Key): Result<Unit> {
    val mac = createAesHmacSha2MacFromKey(
        key, info.iv?.decodeBase64Bytes()
            ?: throw IllegalArgumentException("iv was null")
    )
    return if (info.mac != mac) Result.failure(RecoveryKeyInvalidException("expected mac ${mac}, but got ${info.mac}"))
    else Result.success(Unit)
}