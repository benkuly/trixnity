package net.folivo.trixnity.crypto

import com.soywiz.krypto.encoding.hex
import com.soywiz.krypto.encoding.unhex
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.collect
import net.folivo.trixnity.core.toByteArray
import net.folivo.trixnity.core.toByteFlow
import net.folivo.trixnity.crypto.olm.DecryptionException
import kotlin.random.Random

class AesTest : ShouldSpec({
    timeout = 60_000
    val key = ByteArray(32) { (it + 1).toByte() }
    val nonce = ByteArray(8) { (it + 1).toByte() }
    val initialisationVector = nonce + ByteArray(8)
    should("encrypt") {
        val result = "hello".encodeToByteArray().toByteFlow().encryptAes256Ctr(key, initialisationVector)
        result.toByteArray().hex shouldBe "14e2d5701d"
    }
    should("encrypt empty content") {
        val result = ByteArray(0).toByteFlow().encryptAes256Ctr(key, initialisationVector)
        result.toByteArray().size shouldBe 0
    }
    should("decrypt") {
        "14e2d5701d".unhex.toByteFlow()
            .decryptAes256Ctr(key, initialisationVector).toByteArray().decodeToString() shouldBe "hello"
    }
    should("encrypt and decrypt") {
        "hello".encodeToByteArray().toByteFlow()
            .encryptAes256Ctr(key, initialisationVector)
            .decryptAes256Ctr(key, initialisationVector).toByteArray()
            .decodeToString() shouldBe "hello"
    }
    should("decrypt and handle wrong infos 1") {
        shouldThrow<DecryptionException.OtherException> {
            ByteArray(0).toByteFlow().decryptAes256Ctr(
                ByteArray(0),
                ByteArray(0)
            ).collect()
        }
    }
    should("decrypt and handle wrong infos 2") {
        shouldThrow<DecryptionException.OtherException> {
            Random.Default.nextBytes(ByteArray(1)).toByteFlow().decryptAes256Ctr(
                ByteArray(0),
                Random.Default.nextBytes(ByteArray(256 / 8))
            ).collect()
        }
    }
    should("decrypt and handle wrong infos 3") {
        shouldThrow<DecryptionException.OtherException> {
            Random.Default.nextBytes(ByteArray(1)).toByteFlow().decryptAes256Ctr(
                Random.Default.nextBytes(ByteArray(128 / 8)),
                ByteArray(0)
            ).collect()
        }
    }
})
