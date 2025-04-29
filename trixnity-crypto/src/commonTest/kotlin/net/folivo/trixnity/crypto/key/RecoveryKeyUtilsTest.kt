package net.folivo.trixnity.crypto.key

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.shouldBe
import io.ktor.util.*
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent.AesHmacSha2Key.SecretStorageKeyPassphrase.Pbkdf2
import net.folivo.trixnity.crypto.core.generatePbkdf2Sha512
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import kotlin.random.Random
import kotlin.test.Test

class RecoveryKeyUtilsTest : TrixnityBaseTest() {
    private val curve25519Key = listOf(
        0x77, 0x07, 0x6D, 0x0A, 0x73, 0x18, 0xA5, 0x7D,
        0x3C, 0x16, 0xC1, 0x72, 0x51, 0xB2, 0x66, 0x45,
        0xDF, 0x4C, 0x2F, 0x87, 0xEB, 0xC0, 0x99, 0x2A,
        0xB1, 0x77, 0xFB, 0xA5, 0x1D, 0xB9, 0x2C, 0x2A
    ).map { it.toByte() }.toByteArray()

    @Test
    fun encodeRecoveryKey() = runTest {
        encodeRecoveryKey(curve25519Key) shouldBe "EsTc LW2K PGiF wKEA 3As5 g5c4 BXwk qeeJ ZJV8 Q9fu gUMN UE4d"
    }

    @Test
    fun decodeRecoveryKey() = runTest {
        decodeRecoveryKey(
            "EsTc LW2K PGiF wKEA 3As5 g5c4 BXwk qeeJ ZJV8 Q9fu gUMN UE4d",
        ) shouldBe curve25519Key
        decodeRecoveryKey(
            "EsTcLW2KPGiFwKEA3As5g5c4BXwkqeeJZJV8Q9fugUMNUE4d",
        ) shouldBe curve25519Key
        decodeRecoveryKey(
            " EsTc LW2K PGiF wKEA 3As5 g5c4       BXwk qeeJ ZJV8 Q9fu gUMN UE4d   ",
        ) shouldBe curve25519Key
    }

    @Test
    fun `fail decodeRecoveryKey on wrong prefix`() = runTest {
        shouldThrowAny {
            decodeRecoveryKey(
                "FsTc LW2K PGiF wKEA 3As5 g5c4 BXwk qeeJ ZJV8 Q9fu gUMN UE4d",
            )
        }
        shouldThrowAny {
            decodeRecoveryKey(
                "EqTc LW2K PGiF wKEA 3As5 g5c4 BXwk qeeJ ZJV8 Q9fu gUMN UE4d",
            )
        }
    }

    @Test
    fun `fail decodeRecoveryKey on wrong parity`() = runTest {
        shouldThrowAny {
            decodeRecoveryKey(
                "EsTc LW2K PGiF wKEA 3As5 g5c4 BXwk qeeJ ZJV8 Q9fu gUMN UE4e",
            )
        }
    }

    @Test
    fun `fail decodeRecoveryKey on wrong key length`() = runTest {
        shouldThrowAny {
            decodeRecoveryKey(
                "abc",
            )
        }
    }

    @Test
    fun `create recoveryKeyFromPassphrase`() = runTest {
        val salt = Random.nextBytes(32).encodeBase64()
        val key = generatePbkdf2Sha512(
            password = "super secret passphrase",
            salt = salt.encodeToByteArray(),
            iterationCount = 10_000, // just a test, not secure
            keyBitLength = 32 * 8
        )
        recoveryKeyFromPassphrase(
            "super secret passphrase",
            Pbkdf2(salt = salt, iterations = 10_000, bits = 32 * 8)
        ) shouldBe key
    }
}