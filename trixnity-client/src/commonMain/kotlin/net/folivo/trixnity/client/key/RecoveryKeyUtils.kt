package net.folivo.trixnity.client.key

import io.ktor.util.*
import net.folivo.trixnity.client.crypto.createAesHmacSha2MacFromKey
import net.folivo.trixnity.client.crypto.decodeBase58
import net.folivo.trixnity.client.crypto.encodeBase58
import net.folivo.trixnity.client.crypto.generatePbkdf2Sha512
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent.AesHmacSha2Key

private val recoveryKeyPrefix = listOf(0x8B.toByte(), 0x01.toByte())

internal fun encodeRecoveryKey(recoveryKey: ByteArray): String {
    val recoveryKeyWithPrefix = recoveryKeyPrefix + recoveryKey.toList()
    return (recoveryKeyWithPrefix +
            recoveryKeyWithPrefix.fold(0x00) { parity, byte -> parity xor byte.toInt() }.toByte())
        .toByteArray().encodeBase58().chunked(4).joinToString(" ")
}

internal fun decodeRecoveryKey(encodedRecoveryKey: String): Result<ByteArray> {
    val recoveryKey = try {
        encodedRecoveryKey.filterNot { it.isWhitespace() }.decodeBase58()
    } catch (exc: Throwable) {
        return Result.failure(exc)
    }
    recoveryKeyPrefix.forEachIndexed { index, prefix ->
        if (recoveryKey.getOrNull(index) != prefix)
            return Result.failure(RecoveryKeyInvalidException("wrong prefix"))
    }
    if (recoveryKey.fold(0x00) { parity, byte -> parity xor byte.toInt() } != 0)
        return Result.failure(RecoveryKeyInvalidException("wrong parity"))
    val recoveryKeyLength = 32
    if (recoveryKey.size != recoveryKeyLength + recoveryKeyPrefix.size + 1)
        return Result.failure(RecoveryKeyInvalidException("wrong recovery key length"))
    return Result.success(recoveryKey.copyOfRange(recoveryKeyPrefix.size, recoveryKey.size - 1))
}

@OptIn(InternalAPI::class)
internal suspend fun recoveryKeyFromPassphrase(
    passphrase: String,
    info: AesHmacSha2Key.SecretStorageKeyPassphrase
): Result<ByteArray> {
    return when (info) {
        is AesHmacSha2Key.SecretStorageKeyPassphrase.Pbkdf2 -> {
            runCatching {
                generatePbkdf2Sha512(
                    password = passphrase,
                    salt = info.salt.decodeBase64Bytes(),
                    iterationCount = info.iterations,
                    keyBitLength = info.bits ?: (32 * 8)
                )
            }
        }
        is AesHmacSha2Key.SecretStorageKeyPassphrase.Unknown ->
            Result.failure(IllegalArgumentException("unknown algorithm not supported"))
    }
}

@OptIn(InternalAPI::class)
internal suspend fun checkRecoveryKey(key: ByteArray, info: AesHmacSha2Key): Result<ByteArray> {
    val mac = createAesHmacSha2MacFromKey(
        key, info.iv?.decodeBase64Bytes()
            ?: return Result.failure(IllegalArgumentException("iv was null"))
    )
    return if (info.mac != mac) Result.failure(RecoveryKeyInvalidException("expected mac ${mac}, but got ${info.mac}"))
    else Result.success(key)
}