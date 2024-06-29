package net.folivo.trixnity.crypto.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.utils.toByteArray
import net.folivo.trixnity.utils.toByteArrayFlow
import kotlin.random.Random
import kotlin.test.Test

@OptIn(ExperimentalStdlibApi::class)
class AesCtrTest {
    private val key = ByteArray(32) { (it + 1).toByte() }
    private val nonce = ByteArray(8) { (it + 1).toByte() }
    private val initialisationVector = nonce + ByteArray(8)

    @Test
    fun shouldEncrypt() = runTest {
        val expectedResult = listOf("14e2", "d5701d")
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
            "14e2",
            "d5701d",
            "7410fb20b43d90ff9d2c1666f6f714f0c15e8ecd10ea20f121550e397af52a02" +
                    "051d39637c49e82d57fb467fe7a3968a07e8650032271ae6b11466678a558174" +
                    "dab86357732c3c4715897e402fada1299460ce0766603824228e2ebd5b583659" +
                    "07a820246067ce4a84ad47b9ad30fc98073948a7d1a821b40479c4aa32a40e81"
        )
        val result =
            flowOf(
                "he".encodeToByteArray(), "llo".encodeToByteArray(),
                buildString { repeat(32) { append("dino") } }.encodeToByteArray() // 128 bit (block size)
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
        val expectedResult = listOf("he", "llo") + buildString { repeat(32) { append("dino") } }
        flowOf(
            "14e2".hexToByteArray(),
            "d5701d".hexToByteArray(),
            ("7410fb20b43d90ff9d2c1666f6f714f0c15e8ecd10ea20f121550e397af52a02" +
                    "051d39637c49e82d57fb467fe7a3968a07e8650032271ae6b11466678a558174" +
                    "dab86357732c3c4715897e402fada1299460ce0766603824228e2ebd5b583659" +
                    "07a820246067ce4a84ad47b9ad30fc98073948a7d1a821b40479c4aa32a40e81").hexToByteArray()
        )
            .decryptAes256Ctr(key, initialisationVector)
            .map { it.decodeToString() }.toList() shouldBe expectedResult
    }

    @Test
    fun shouldDecryptMultipleAesBlocks() = runTest {
        val expectedResult = listOf("he", "llo")
        flowOf("14e2".hexToByteArray(), "d5701d".hexToByteArray(), ByteArray(0))
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
