package net.folivo.trixnity.crypto.core

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import kotlin.random.Random
import kotlin.test.Test

class AesHmacSha2Test : TrixnityBaseTest() {
    private val key = ByteArray(32) { (it + 1).toByte() }
    private val nonce = ByteArray(8) { (it + 1).toByte() }
    private val initialisationVector = nonce + ByteArray(8)

    @Test
    fun deriveKeys() = runTest {
        assertSoftly(deriveKeys("this is a key".encodeToByteArray(), "name")) {
            aesKey.size shouldBe 32
            hmacKey.size shouldBe 32
        }
    }

    @Test
    fun `deriveKeys with empty name`() = runTest {
        assertSoftly(deriveKeys("this is a key".encodeToByteArray(), "")) {
            aesKey.size shouldBe 32
            hmacKey.size shouldBe 32
        }
    }

    @Test
    fun encrypt() = runTest {
        val content = "this is really fancy content with more then 128 bit block size".encodeToByteArray()
        encryptAesHmacSha2(content, key, "KEY", initialisationVector) shouldBe
                AesHmacSha2EncryptedData(
                    iv = "AQIDBAUGBwgAAAAAAAAAAA",
                    ciphertext = "vLdCiyNTr8HhSFeOhQJsp6eS/CwbZLtfzcknG3I721SFmwLzUIrlmyhf9hzfgzCGw2cITIk6RyA3TmF6elU",
                    mac = "2Q8Bt/Y8jABla2lTi0G3j3inwxkUWX85NNdkkKxwK0I"
                )
    }

    @Test
    fun decrypt() = runTest {
        val content = "this is really fancy content with more then 128 bit block size".encodeToByteArray()
        decryptAesHmacSha2(
            AesHmacSha2EncryptedData(
                iv = "AQIDBAUGBwgAAAAAAAAAAA",
                ciphertext = "vLdCiyNTr8HhSFeOhQJsp6eS/CwbZLtfzcknG3I721SFmwLzUIrlmyhf9hzfgzCGw2cITIk6RyA3TmF6elU",
                mac = "2Q8Bt/Y8jABla2lTi0G3j3inwxkUWX85NNdkkKxwK0I"
            ), key, "KEY"
        ) shouldBe content
    }

    @Test
    fun decryptPaddedBase64() = runTest {
        val content = "this is really fancy content with more then 128 bit block size".encodeToByteArray()
        decryptAesHmacSha2(
            AesHmacSha2EncryptedData(
                iv = "AQIDBAUGBwgAAAAAAAAAAA==",
                ciphertext = "vLdCiyNTr8HhSFeOhQJsp6eS/CwbZLtfzcknG3I721SFmwLzUIrlmyhf9hzfgzCGw2cITIk6RyA3TmF6elU=",
                mac = "2Q8Bt/Y8jABla2lTi0G3j3inwxkUWX85NNdkkKxwK0I="
            ), key, "KEY"
        ) shouldBe content
    }

    @Test
    fun `encrypt and decrypt`() = runTest {
        val content = "this is content".encodeToByteArray()
        val key = Random.nextBytes(32)
        val encrypted = encryptAesHmacSha2(content, key, "KEY")
        decryptAesHmacSha2(encrypted, key, "KEY") shouldBe content
    }
}