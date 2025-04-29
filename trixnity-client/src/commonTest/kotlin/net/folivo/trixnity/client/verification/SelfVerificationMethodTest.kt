package net.folivo.trixnity.client.verification

import io.kotest.assertions.assertSoftly
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
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertNotNull

class SelfVerificationMethodTest : TrixnityBaseTest() {

    private val keySecretServiceMock = KeySecretServiceMock()
    private val keyTrustServiceMock = KeyTrustServiceMock()

    @Test
    fun `AesHmacSha2RecoveryKey » verify » decode recovery key and decrypt missing keys with it`() = runTest {
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

    @Test
    fun `AesHmacSha2RecoveryKeyWithPbkdf2Passphrase » verify » decode pbkdf2 recovery key and decrypt missing keys with it`() = runTest {
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