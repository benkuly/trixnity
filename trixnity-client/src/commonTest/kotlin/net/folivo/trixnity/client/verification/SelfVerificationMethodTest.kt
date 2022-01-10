package net.folivo.trixnity.client.verification

import io.kotest.core.spec.style.ShouldSpec
import io.ktor.util.*
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import net.folivo.trixnity.client.key.KeyService
import net.folivo.trixnity.client.key.encodeRecoveryKey
import net.folivo.trixnity.client.key.encryptAesHmacSha2
import net.folivo.trixnity.client.key.generatePbkdf2Sha512
import net.folivo.trixnity.client.verification.SelfVerificationMethod.AesHmacSha2RecoveryKey
import net.folivo.trixnity.client.verification.SelfVerificationMethod.AesHmacSha2RecoveryKeyWithPbkdf2Passphrase
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent.SecretStorageKeyPassphrase.Pbkdf2
import kotlin.random.Random

@OptIn(InternalAPI::class)
class SelfVerificationMethodTest : ShouldSpec({
    timeout = 30_000

    val keyService = mockk<KeyService>(relaxUnitFun = true)

    beforeTest {
        coEvery {
            keyService.checkOwnAdvertisedMasterKeyAndVerifySelf(any(), any(), any())
        } returns Result.success(Unit)
    }
    afterTest {
        clearAllMocks()
    }
    context("${AesHmacSha2RecoveryKey::class.simpleName}") {
        context(AesHmacSha2RecoveryKey::verify.name) {
            should("decode recovery key and decrypt missing keys with it") {
                val key = Random.nextBytes(32)
                val iv = Random.nextBytes(16)
                val mac = encryptAesHmacSha2(
                    content = ByteArray(32),
                    key = key,
                    name = "",
                    initialisationVector = iv
                ).mac
                val info = SecretKeyEventContent.AesHmacSha2Key(
                    name = "",
                    passphrase = null,
                    mac = mac,
                    iv = iv.encodeBase64()
                )
                val cut = AesHmacSha2RecoveryKey(keyService, "KEY", info)
                cut.verify(encodeRecoveryKey(key)).getOrThrow()
                coVerify { keyService.decryptMissingSecrets(key, "KEY", info) }
                coVerify { keyService.checkOwnAdvertisedMasterKeyAndVerifySelf(key, "KEY", info) }
            }
        }
    }
    context("${AesHmacSha2RecoveryKeyWithPbkdf2Passphrase::class.simpleName}") {
        context(AesHmacSha2RecoveryKeyWithPbkdf2Passphrase::verify.name) {
            should("decode recovery key and decrypt missing keys with it") {
                val iv = Random.nextBytes(16)
                val salt = Random.nextBytes(32)
                val key = generatePbkdf2Sha512(
                    password = "password",
                    salt = salt,
                    iterationCount = 300_000,
                    keyBitLength = 32 * 8
                )
                val mac = encryptAesHmacSha2(
                    content = ByteArray(32),
                    key = key,
                    name = "",
                    initialisationVector = iv
                ).mac
                val info = SecretKeyEventContent.AesHmacSha2Key(
                    passphrase = Pbkdf2(salt = salt.encodeBase64(), iterations = 300_000, bits = 32 * 8),
                    iv = iv.encodeBase64(),
                    mac = mac
                )
                val cut = AesHmacSha2RecoveryKeyWithPbkdf2Passphrase(keyService, "KEY", info)
                cut.verify("password").getOrThrow()
                coVerify { keyService.decryptMissingSecrets(key, "KEY", info) }
                coVerify { keyService.checkOwnAdvertisedMasterKeyAndVerifySelf(key, "KEY", info) }
            }
        }
    }
})