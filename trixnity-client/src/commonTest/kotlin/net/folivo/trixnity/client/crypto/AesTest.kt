package net.folivo.trixnity.client.crypto

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlin.random.Random

class AesTest : ShouldSpec({
    val key = Random.nextBytes(256 / 8)
    val nonce = Random.nextBytes(64 / 8)
    val initialisationVector = nonce + ByteArray(64 / 8)
    should("encrypt") {
        val result = encryptAes256Ctr("hello".encodeToByteArray(), key, initialisationVector)
        result.size shouldBeGreaterThan 0
    }
    should("encrypt empty content") {
        val result = encryptAes256Ctr(ByteArray(0), key, initialisationVector)
        result.size shouldBe 0
    }
    should("decrypt") {
        val encrypted = encryptAes256Ctr("hello".encodeToByteArray(), key, initialisationVector)
        decryptAes256Ctr(encrypted, key, initialisationVector).decodeToString() shouldBe "hello"
    }
    should("decrypt and handle wrong infos") {
        shouldThrow<DecryptionException.OtherException> {
            decryptAes256Ctr(
                ByteArray(0),
                ByteArray(0),
                ByteArray(0)
            )
        }
        shouldThrow<DecryptionException.OtherException> {
            decryptAes256Ctr(
                Random.Default.nextBytes(ByteArray(1)),
                ByteArray(0),
                Random.Default.nextBytes(ByteArray(256 / 8))
            )
        }
        shouldThrow<DecryptionException.OtherException> {
            decryptAes256Ctr(
                Random.Default.nextBytes(ByteArray(1)),
                Random.Default.nextBytes(ByteArray(128 / 8)),
                ByteArray(0)
            )
        }
    }
})
