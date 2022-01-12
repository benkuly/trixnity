package net.folivo.trixnity.client.key

import arrow.core.flatMap
import io.ktor.util.*
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent

private val recoveryKeyPrefix = listOf(0x8B.toByte(), 0x01.toByte())

internal fun encodeRecoveryKey(recoveryKey: ByteArray): String {
    val recoveryKeyWithPrefix = recoveryKeyPrefix + recoveryKey.toList()
    return (recoveryKeyWithPrefix +
            recoveryKeyWithPrefix.fold(0x00) { parity, byte -> parity xor byte.toInt() }.toByte())
        .toByteArray().encodeBase58().chunked(4).joinToString(" ")
}

internal suspend fun decodeRecoveryKey(encodedRecoveryKey: String, info: SecretKeyEventContent): Result<ByteArray> {
    when (info) {
        is SecretKeyEventContent.AesHmacSha2Key -> {
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
            val result = recoveryKey.copyOfRange(recoveryKeyPrefix.size, recoveryKey.size - 1)
            return checkRecoveryKey(result, info)
        }
        is SecretKeyEventContent.Unknown -> return Result.failure(IllegalArgumentException("unknown algorithm not supported"))
    }
}

@OptIn(InternalAPI::class)
internal suspend fun recoveryKeyFromPassphrase(passphrase: String, info: SecretKeyEventContent): Result<ByteArray> {
    return when (info) {
        is SecretKeyEventContent.AesHmacSha2Key -> when (val passphraseInfo = info.passphrase) {
            is SecretKeyEventContent.SecretStorageKeyPassphrase.Pbkdf2 -> {
                runCatching {
                    generatePbkdf2Sha512(
                        password = passphrase,
                        salt = passphraseInfo.salt.decodeBase64Bytes(),
                        iterationCount = passphraseInfo.iterations,
                        keyBitLength = passphraseInfo.bits ?: (32 * 8)
                    )
                }.flatMap { checkRecoveryKey(it, info) }
            }
            is SecretKeyEventContent.SecretStorageKeyPassphrase.Unknown ->
                Result.failure(IllegalArgumentException("unknown algorithm not supported"))
            null -> Result.failure(IllegalArgumentException("recovery key from passphrase not supported"))
        }
        is SecretKeyEventContent.Unknown -> Result.failure(IllegalArgumentException("unknown algorithm not supported"))
    }
}

@OptIn(InternalAPI::class)
private suspend fun checkRecoveryKey(key: ByteArray, info: SecretKeyEventContent.AesHmacSha2Key): Result<ByteArray> {
    val encrypted = encryptAesHmacSha2(
        content = ByteArray(32),
        key = key,
        name = "",
        initialisationVector = info.iv?.decodeBase64Bytes()
            ?: return Result.failure(IllegalArgumentException("iv was null"))
    )
    return if (info.mac != encrypted.mac) Result.failure(RecoveryKeyInvalidException("expected mac ${encrypted.mac}, but got ${info.mac}"))
    else Result.success(key)
}

