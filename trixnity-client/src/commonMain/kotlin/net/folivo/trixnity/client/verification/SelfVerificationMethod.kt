package net.folivo.trixnity.client.verification

import net.folivo.trixnity.client.key.KeyService
import net.folivo.trixnity.client.key.decodeRecoveryKey
import net.folivo.trixnity.client.key.recoveryKeyFromPassphrase
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent

sealed interface SelfVerificationMethod {
    data class CrossSignedDeviceVerification(
        val createDeviceVerification: suspend () -> Unit
    ) : SelfVerificationMethod

    data class AesHmacSha2RecoveryKey(
        private val keyService: KeyService,
        private val keyId: String,
        private val info: SecretKeyEventContent.AesHmacSha2Key
    ) : SelfVerificationMethod {
        suspend fun verify(recoverKey: String): Result<Unit> {
            return decodeRecoveryKey(recoverKey, info).onSuccess {
                keyService.decryptMissingSecrets(it, keyId, info)
            }.map { }
        }
    }

    data class AesHmacSha2RecoveryKeyWithPbkdf2Passphrase(
        private val keyService: KeyService,
        private val keyId: String,
        private val info: SecretKeyEventContent.AesHmacSha2Key,
    ) : SelfVerificationMethod {
        suspend fun verify(passphrase: String): Result<Unit> {
            return recoveryKeyFromPassphrase(passphrase, info).onSuccess {
                keyService.decryptMissingSecrets(it, keyId, info)
            }.map { }
        }
    }
}