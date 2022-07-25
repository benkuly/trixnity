package net.folivo.trixnity.crypto

import com.soywiz.krypto.encoding.hex
import com.soywiz.krypto.encoding.unhex
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import net.folivo.trixnity.crypto.olm.DecryptionException
import kotlin.random.Random

class AesTest : ShouldSpec({
    timeout = 60_000
    val key = ByteArray(32) { (it + 1).toByte() }
    val nonce = ByteArray(8) { (it + 1).toByte() }
    val initialisationVector = nonce + ByteArray(8)
    should("encrypt") {
        val result = encryptAes256Ctr("hello".encodeToByteArray(), key, initialisationVector)
        result.hex shouldBe "14e2d5701d"
    }
    should("encrypt empty content") {
        val result = encryptAes256Ctr(ByteArray(0), key, initialisationVector)
        result.size shouldBe 0
    }
    should("decrypt") {
        decryptAes256Ctr("14e2d5701d".unhex, key, initialisationVector).decodeToString() shouldBe "hello"
    }
    should("encrypt and decrypt") {
        val encrypted = encryptAes256Ctr("hello".encodeToByteArray(), key, initialisationVector)
        decryptAes256Ctr(encrypted, key, initialisationVector).decodeToString() shouldBe "hello"
    }
    should("decrypt and handle wrong infos 1") {
        shouldThrow<DecryptionException.OtherException> {
            decryptAes256Ctr(
                ByteArray(0),
                ByteArray(0),
                ByteArray(0)
            )
        }
    }
    should("decrypt and handle wrong infos 2") {
        shouldThrow<DecryptionException.OtherException> {
            decryptAes256Ctr(
                Random.Default.nextBytes(ByteArray(1)),
                ByteArray(0),
                Random.Default.nextBytes(ByteArray(256 / 8))
            )
        }
    }
    should("decrypt and handle wrong infos 3") {
        shouldThrow<DecryptionException.OtherException> {
            decryptAes256Ctr(
                Random.Default.nextBytes(ByteArray(1)),
                Random.Default.nextBytes(ByteArray(128 / 8)),
                ByteArray(0)
            )
        }
    }
})
