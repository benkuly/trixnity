package net.folivo.trixnity.crypto.core

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlin.random.Random

class AesHmacSha2Test : ShouldSpec({
    timeout = 30_000

    val key = ByteArray(32) { (it + 1).toByte() }
    val nonce = ByteArray(8) { (it + 1).toByte() }
    val initialisationVector = nonce + ByteArray(8)

    should("deriveKeys") {
        assertSoftly(deriveKeys("this is a key".encodeToByteArray(), "name")) {
            aesKey.size shouldBe 32
            hmacKey.size shouldBe 32
        }
    }
    should("deriveKeys with empty name") {
        assertSoftly(deriveKeys("this is a key".encodeToByteArray(), "")) {
            aesKey.size shouldBe 32
            hmacKey.size shouldBe 32
        }
    }
    should("encrypt") {
        val content = "this is really fancy content with more then 128 bit block size".encodeToByteArray()
        encryptAesHmacSha2(content, key, "KEY", initialisationVector) shouldBe
                AesHmacSha2EncryptedData(
                    iv = "AQIDBAUGBwgAAAAAAAAAAA==",
                    ciphertext = "vLdCiyNTr8HhSFeOhQJsp6eS/CwbZLtfzcknG3I721SFmwLzUIrlmyhf9hzfgzCGw2cITIk6RyA3TmF6elU=",
                    mac = "2Q8Bt/Y8jABla2lTi0G3j3inwxkUWX85NNdkkKxwK0I="
                )
    }
    should("decrypt") {
        val content = "this is really fancy content with more then 128 bit block size".encodeToByteArray()
        decryptAesHmacSha2(
            AesHmacSha2EncryptedData(
                iv = "AQIDBAUGBwgAAAAAAAAAAA==",
                ciphertext = "vLdCiyNTr8HhSFeOhQJsp6eS/CwbZLtfzcknG3I721SFmwLzUIrlmyhf9hzfgzCGw2cITIk6RyA3TmF6elU=",
                mac = "2Q8Bt/Y8jABla2lTi0G3j3inwxkUWX85NNdkkKxwK0I="
            ), key, "KEY"
        ) shouldBe content

    }
    should("encrypt and decrypt") {
        val content = "this is content".encodeToByteArray()
        val key = Random.nextBytes(32)
        val encrypted = encryptAesHmacSha2(content, key, "KEY")
        decryptAesHmacSha2(encrypted, key, "KEY") shouldBe content
    }
})