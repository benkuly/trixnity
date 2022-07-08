package net.folivo.trixnity.crypto

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlin.random.Random

class AesHmacSha2Test : ShouldSpec({
    timeout = 30_000

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
    should("encrypt and decrypt") {
        val content = "this is content".encodeToByteArray()
        val key = Random.nextBytes(32)
        val encrypted = encryptAesHmacSha2(content, key, "KEY")
        decryptAesHmacSha2(encrypted, key, "KEY") shouldBe content
    }
})