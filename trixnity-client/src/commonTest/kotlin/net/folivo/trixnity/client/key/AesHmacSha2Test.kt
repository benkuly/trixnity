package net.folivo.trixnity.client.key

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlin.random.Random

class AesHmacSha2Test : ShouldSpec({
    timeout = 30_000

    context(::hmacSha256.name) {
        should("create mac") {
            hmacSha256(
                "this is a key".encodeToByteArray(),
                "this should be maced".encodeToByteArray()
            ) shouldBe "f5ab5a64c568f2393ed7a1c5bc84ae82d2ecb847b968bab2057fb99190e52d75".chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()
        }
    }
    context(::deriveKeys.name) {
        should("generate keys") {
            assertSoftly(deriveKeys("this is a key".encodeToByteArray(), "")) {
                aesKey.size shouldBe 32
                hmacKey.size shouldBe 32
            }
        }
        should("generate keys with empty name") {
            assertSoftly(deriveKeys("this is a key".encodeToByteArray(), "")) {
                aesKey.size shouldBe 32
                hmacKey.size shouldBe 32
            }
        }
    }
    context("${::encryptAesHmacSha2.name} and ${::decryptAesHmacSha2.name}") {
        should("encrypt and decrypt") {
            val content = "this is content".encodeToByteArray()
            val key = Random.nextBytes(32)
            val encrypted = encryptAesHmacSha2(content, key, "KEY")
            decryptAesHmacSha2(encrypted, key, "KEY") shouldBe content
        }
    }
})