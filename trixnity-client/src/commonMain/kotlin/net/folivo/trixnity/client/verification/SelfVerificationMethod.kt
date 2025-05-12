package net.folivo.trixnity.client.verification

import net.folivo.trixnity.client.key.KeySecretService
import net.folivo.trixnity.client.key.KeyTrustService
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import net.folivo.trixnity.crypto.key.checkRecoveryKey
import net.folivo.trixnity.crypto.key.decodeRecoveryKey
import net.folivo.trixnity.crypto.key.recoveryKeyFromPassphrase

sealed interface SelfVerificationMethod {
    data class CrossSignedDeviceVerification(
        private val ownUserId: UserId,
        private val sendToDevices: Set<String>,
        private val createDeviceVerificationRequest: suspend (
            theirUserId: UserId,
            theirDeviceIds: Set<String>
        ) -> Result<ActiveDeviceVerification>
    ) : SelfVerificationMethod {
        suspend fun createDeviceVerification(): Result<ActiveDeviceVerification> {
            return createDeviceVerificationRequest(ownUserId, sendToDevices)
        }
    }

    data class AesHmacSha2RecoveryKey(
        private val keySecretService: KeySecretService,
        private val keyTrustService: KeyTrustService,
        private val keyId: String,
        private val info: SecretKeyEventContent.AesHmacSha2Key
    ) : SelfVerificationMethod {
        suspend fun verify(recoverKey: String): Result<Unit> = runCatching {
            val recoveryKey = decodeRecoveryKey(recoverKey)
            checkRecoveryKey(recoveryKey, info)
                .onSuccess {
                    keySecretService.decryptOrCreateMissingSecrets(recoveryKey, keyId, info)
                    keyTrustService.checkOwnAdvertisedMasterKeyAndVerifySelf(recoveryKey, keyId, info)
                }.getOrThrow()
        }
    }

    data class AesHmacSha2RecoveryKeyWithPbkdf2Passphrase(
        private val keySecretService: KeySecretService,
        private val keyTrustService: KeyTrustService,
        private val keyId: String,
        private val info: SecretKeyEventContent.AesHmacSha2Key,
    ) : SelfVerificationMethod {
        suspend fun verify(passphrase: String): Result<Unit> = kotlin.runCatching {
            val passphraseInfo = info.passphrase
                ?: throw IllegalArgumentException("missing passphrase")
            val recoveryKey = recoveryKeyFromPassphrase(passphrase, passphraseInfo)
            checkRecoveryKey(recoveryKey, info)
                .onSuccess {
                    keySecretService.decryptOrCreateMissingSecrets(recoveryKey, keyId, info)
                    keyTrustService.checkOwnAdvertisedMasterKeyAndVerifySelf(recoveryKey, keyId, info)
                }.getOrThrow()
        }
    }
}