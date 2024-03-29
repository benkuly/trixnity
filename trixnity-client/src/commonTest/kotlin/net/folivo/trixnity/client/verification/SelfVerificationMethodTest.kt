package net.folivo.trixnity.client.verification

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.util.*
import net.folivo.trixnity.client.mocks.KeySecretServiceMock
import net.folivo.trixnity.client.mocks.KeyTrustServiceMock
import net.folivo.trixnity.client.verification.SelfVerificationMethod.AesHmacSha2RecoveryKey
import net.folivo.trixnity.client.verification.SelfVerificationMethod.AesHmacSha2RecoveryKeyWithPbkdf2Passphrase
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent.AesHmacSha2Key.SecretStorageKeyPassphrase.Pbkdf2
import net.folivo.trixnity.crypto.core.encryptAesHmacSha2
import net.folivo.trixnity.crypto.core.generatePbkdf2Sha512
import net.folivo.trixnity.crypto.key.encodeRecoveryKey
import kotlin.random.Random
import kotlin.test.assertNotNull

class SelfVerificationMethodTest : ShouldSpec({
    timeout = 120_000

    lateinit var keySecretServiceMock: KeySecretServiceMock
    lateinit var keyTrustServiceMock: KeyTrustServiceMock

    beforeTest {
        keySecretServiceMock = KeySecretServiceMock()
        keyTrustServiceMock = KeyTrustServiceMock()
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
                val cut = AesHmacSha2RecoveryKey(keySecretServiceMock, keyTrustServiceMock, "KEY", info)
                cut.verify(encodeRecoveryKey(key)).getOrThrow()
                assertSoftly(keySecretServiceMock.decryptMissingSecretsCalled.value) {
                    assertNotNull(this)
                    first shouldBe key
                    second shouldBe "KEY"
                    third shouldBe info
                }
                assertSoftly(keyTrustServiceMock.checkOwnAdvertisedMasterKeyAndVerifySelfCalled.value) {
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
                val salt = Random.nextBytes(32).encodeBase64()
                val key = generatePbkdf2Sha512(
                    password = "password",
                    salt = salt.encodeToByteArray(),
                    iterationCount = 1_000, // just a test, not secure
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
                        salt = salt,
                        iterations = 1_000, // just a test, not secure
                        bits = 32 * 8
                    ),
                    iv = iv.encodeBase64(),
                    mac = mac
                )
                val cut =
                    AesHmacSha2RecoveryKeyWithPbkdf2Passphrase(keySecretServiceMock, keyTrustServiceMock, "KEY", info)
                cut.verify("password").getOrThrow()
                assertSoftly(keySecretServiceMock.decryptMissingSecretsCalled.value) {
                    assertNotNull(this)
                    first shouldBe key
                    second shouldBe "KEY"
                    third shouldBe info
                }
                assertSoftly(keyTrustServiceMock.checkOwnAdvertisedMasterKeyAndVerifySelfCalled.value) {
                    assertNotNull(this)
                    first shouldBe key
                    second shouldBe "KEY"
                    third shouldBe info
                }
            }
        }
    }
})