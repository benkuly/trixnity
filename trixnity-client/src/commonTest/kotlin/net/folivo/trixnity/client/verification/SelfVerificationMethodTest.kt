package net.folivo.trixnity.client.verification

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.util.*
import net.folivo.trixnity.client.mocks.KeyServiceMock
import net.folivo.trixnity.client.verification.SelfVerificationMethod.AesHmacSha2RecoveryKey
import net.folivo.trixnity.client.verification.SelfVerificationMethod.AesHmacSha2RecoveryKeyWithPbkdf2Passphrase
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent.AesHmacSha2Key.SecretStorageKeyPassphrase.Pbkdf2
import net.folivo.trixnity.crypto.encryptAesHmacSha2
import net.folivo.trixnity.crypto.generatePbkdf2Sha512
import net.folivo.trixnity.crypto.key.encodeRecoveryKey
import kotlin.random.Random
import kotlin.test.assertNotNull

class SelfVerificationMethodTest : ShouldSpec({
    timeout = 60_000

    lateinit var keyService: KeyServiceMock

    beforeTest {
        keyService = KeyServiceMock()
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
                assertSoftly(keyService.secret.decryptMissingSecretsCalled.value) {
                    assertNotNull(this)
                    first shouldBe key
                    second shouldBe "KEY"
                    third shouldBe info
                }
                assertSoftly(keyService.checkOwnAdvertisedMasterKeyAndVerifySelfCalled.value) {
                    assertNotNull(this)
                    first shouldBe key
                    second shouldBe "KEY"
                    third shouldBe info
                }
            }
        }
    }
    context("${AesHmacSha2RecoveryKeyWithPbkdf2Passphrase::class.simpleName}") {
        context(AesHmacSha2RecoveryKeyWithPbkdf2Passphrase::verify.name) {
            should("decode pbkdf2 recovery key and decrypt missing keys with it") {
                val iv = Random.nextBytes(16)
                val salt = Random.nextBytes(32)
                val key = generatePbkdf2Sha512(
                    password = "password",
                    salt = salt,
                    iterationCount = 10_000,  // just a test, not secure
                    keyBitLength = 32 * 8
                )
                val mac = encryptAesHmacSha2(
                    content = ByteArray(32),
                    key = key,
                    name = "",
                    initialisationVector = iv
                ).mac
                val info = SecretKeyEventContent.AesHmacSha2Key(
                    passphrase = Pbkdf2(
                        salt = salt.encodeBase64(),
                        iterations = 10_000,
                        bits = 32 * 8
                    ),
                    iv = iv.encodeBase64(),
                    mac = mac
                )
                val cut = AesHmacSha2RecoveryKeyWithPbkdf2Passphrase(keyService, "KEY", info)
                cut.verify("password").getOrThrow()
                assertSoftly(keyService.secret.decryptMissingSecretsCalled.value) {
                    assertNotNull(this)
                    first shouldBe key
                    second shouldBe "KEY"
                    third shouldBe info
                }
                assertSoftly(keyService.checkOwnAdvertisedMasterKeyAndVerifySelfCalled.value) {
                    assertNotNull(this)
                    first shouldBe key
                    second shouldBe "KEY"
                    third shouldBe info
                }
            }
        }
    }
})