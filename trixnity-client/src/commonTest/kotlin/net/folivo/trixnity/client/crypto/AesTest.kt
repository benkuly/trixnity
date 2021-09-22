package net.folivo.trixnity.client.crypto

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlin.random.Random

class AesTest : ShouldSpec({
    context("aes256Ctr") {
        context("encrypt") {
            should("encrypt") {
                val result = encryptAes256Ctr("hello".encodeToByteArray())
                assertSoftly(result) {
                    encryptedContent.size shouldBeGreaterThan 0
                    initialisationVector.size shouldBeGreaterThan 0
                    key.size shouldBeGreaterThan 0
                }
            }
            should("handle empty content") {
                val result = encryptAes256Ctr(ByteArray(0))
                assertSoftly(result) {
                    encryptedContent.size shouldBe 0
                    initialisationVector.size shouldBeGreaterThan 0
                    key.size shouldBeGreaterThan 0
                }
            }
        }
        context("decrypt") {
            should("decrypt") {
                val encrypted = encryptAes256Ctr("hello".encodeToByteArray())
                decryptAes256Ctr(encrypted).decodeToString() shouldBe "hello"
            }
            should("handle wrong infos") {
                shouldThrow<DecryptionException.OtherException> {
                    decryptAes256Ctr(
                        Aes256CtrInfo(
                            ByteArray(0),
                            ByteArray(0),
                            ByteArray(0)
                        )
                    )
                }
                shouldThrow<DecryptionException.OtherException> {
                    decryptAes256Ctr(
                        Aes256CtrInfo(
                            Random.Default.nextBytes(ByteArray(1)),
                            ByteArray(0),
                            Random.Default.nextBytes(ByteArray(256 / 8))
                        )
                    )
                }
                shouldThrow<DecryptionException.OtherException> {
                    decryptAes256Ctr(
                        Aes256CtrInfo(
                            Random.Default.nextBytes(ByteArray(1)),
                            Random.Default.nextBytes(ByteArray(128 / 8)),
                            ByteArray(0)
                        )
                    )
                }
            }
        }
    }
})
