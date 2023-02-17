package net.folivo.trixnity.crypto

import com.soywiz.krypto.encoding.hex
import com.soywiz.krypto.encoding.unhex
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.core.toByteArray
import net.folivo.trixnity.core.toByteArrayFlow
import net.folivo.trixnity.crypto.olm.DecryptionException
import kotlin.random.Random
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AesTest {
    private val key = ByteArray(32) { (it + 1).toByte() }
    private val nonce = ByteArray(8) { (it + 1).toByte() }
    private val initialisationVector = nonce + ByteArray(8)

    @Test
    fun shouldEncrypt() = runTest {
        val result = "hello".encodeToByteArray().toByteArrayFlow().encryptAes256Ctr(key, initialisationVector)
        result.toByteArray().hex shouldBe "14e2d5701d"
    }

    @Test
    fun shouldEncryptEmptyContent() = runTest {
        val result = ByteArray(0).toByteArrayFlow().encryptAes256Ctr(key, initialisationVector)
        result.toByteArray().size shouldBe 0
    }

    @Test
    fun shouldDecrypt() = runTest {
        "14e2d5701d".unhex.toByteArrayFlow()
            .decryptAes256Ctr(key, initialisationVector).toByteArray().decodeToString() shouldBe "hello"
    }

    @Test
    fun shouldEncryptAndDecrypt() = runTest {
        "hello".encodeToByteArray().toByteArrayFlow()
            .encryptAes256Ctr(key, initialisationVector)
            .decryptAes256Ctr(key, initialisationVector).toByteArray()
            .decodeToString() shouldBe "hello"
    }

    @Test
    fun shouldDecryptAndHandleWrongInfos1() = runTest {
        shouldThrow<DecryptionException.OtherException> {
            ByteArray(0).toByteArrayFlow().decryptAes256Ctr(
                ByteArray(0),
                ByteArray(0)
            ).collect()
        }
    }

    @Test
    fun shouldDecryptAndHandleWrongInfos2() = runTest {
        shouldThrow<DecryptionException.OtherException> {
            Random.Default.nextBytes(ByteArray(1)).toByteArrayFlow().decryptAes256Ctr(
                ByteArray(0),
                Random.Default.nextBytes(ByteArray(256 / 8))
            ).collect()
        }
    }

    @Test
    fun shouldDecryptAndHandleWrongInfos3() = runTest {
        shouldThrow<DecryptionException.OtherException> {
            Random.Default.nextBytes(ByteArray(1)).toByteArrayFlow().decryptAes256Ctr(
                Random.Default.nextBytes(ByteArray(128 / 8)),
                ByteArray(0)
            ).collect()
        }
    }
}
