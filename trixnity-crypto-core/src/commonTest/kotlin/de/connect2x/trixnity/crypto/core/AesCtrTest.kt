package de.connect2x.trixnity.crypto.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.utils.toByteArray
import de.connect2x.trixnity.utils.toByteArrayFlow
import kotlin.random.Random
import kotlin.test.Test

@OptIn(ExperimentalStdlibApi::class)
class AesCtrTest : TrixnityBaseTest() {
    private val key = ByteArray(32) { (it + 1).toByte() }
    private val nonce = ByteArray(8) { (it + 1).toByte() }
    private val initialisationVector = nonce + ByteArray(7) + ByteArray(1) { (0xff).toByte() }

    @Test
    fun shouldEncrypt() = runTest {
        val expectedResult = listOf("a61e", "093832")
        val result =
            flowOf("he".encodeToByteArray(), "llo".encodeToByteArray(), ByteArray(0)).encryptAes256Ctr(
                key,
                initialisationVector
            )
        result.map { it.toHexString() }.toList() shouldBe expectedResult
    }

    @Test
    fun shouldEncryptMultipleAesBlocks() = runTest {
        val expectedResult = listOf(
            "a61e",
            "093832",
            "e5d9669dcb23e0e3554edbb860715201"
        )
        val result =
            flowOf(
                "he".encodeToByteArray(), "llo".encodeToByteArray(),
                buildString { repeat(4) { append("dino") } }.encodeToByteArray() // 128 bit (block size)
            ).encryptAes256Ctr(key, initialisationVector)
        result.map { it.toHexString() }.toList() shouldBe expectedResult
    }

    @Test
    fun shouldEncryptEmptyContent() = runTest {
        val result = ByteArray(0).toByteArrayFlow().encryptAes256Ctr(key, initialisationVector)
        result.toByteArray().size shouldBe 0
    }

    @Test
    fun shouldDecrypt() = runTest {
        val expectedResult = listOf("he", "llo")
        flowOf("a61e".hexToByteArray(), "093832".hexToByteArray(), ByteArray(0))
            .decryptAes256Ctr(key, initialisationVector)
            .map { it.decodeToString() }.toList() shouldBe expectedResult
    }

    @Test
    fun shouldDecryptMultipleAesBlocks() = runTest {
        val expectedResult = listOf("he", "llo") +
                buildString { repeat(4) { append("dino") } } // 128 bit (block size)
        flowOf(
            "a61e".hexToByteArray(),
            "093832".hexToByteArray(),
            "e5d9669dcb23e0e3554edbb860715201".hexToByteArray()
        )
            .decryptAes256Ctr(key, initialisationVector)
            .map { it.decodeToString() }.toList() shouldBe expectedResult
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
        shouldThrow<AesDecryptionException> {
            ByteArray(0).toByteArrayFlow()
                .decryptAes256Ctr(ByteArray(0), ByteArray(0))
                .collect()
        }
    }

    @Test
    fun shouldDecryptAndHandleWrongInfos2() = runTest {
        shouldThrow<AesDecryptionException> {
            Random.Default.nextBytes(ByteArray(1)).toByteArrayFlow()
                .decryptAes256Ctr(ByteArray(0), Random.Default.nextBytes(ByteArray(256 / 8)))
                .collect()
        }
    }

    @Test
    fun shouldDecryptAndHandleWrongInfos3() = runTest {
        shouldThrow<AesDecryptionException> {
            Random.Default.nextBytes(ByteArray(1)).toByteArrayFlow().decryptAes256Ctr(
                Random.Default.nextBytes(ByteArray(128 / 8)),
                ByteArray(0)
            ).collect()
        }
    }
}
