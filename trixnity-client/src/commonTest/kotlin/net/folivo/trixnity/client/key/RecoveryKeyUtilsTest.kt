package net.folivo.trixnity.client.key

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.util.*
import net.folivo.trixnity.client.crypto.generatePbkdf2Sha512
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent.AesHmacSha2Key.SecretStorageKeyPassphrase.Pbkdf2
import kotlin.random.Random

class RecoveryKeyUtilsTest : ShouldSpec({
    timeout = 30_000

    val curve25519Key = listOf(
        0x77, 0x07, 0x6D, 0x0A, 0x73, 0x18, 0xA5, 0x7D,
        0x3C, 0x16, 0xC1, 0x72, 0x51, 0xB2, 0x66, 0x45,
        0xDF, 0x4C, 0x2F, 0x87, 0xEB, 0xC0, 0x99, 0x2A,
        0xB1, 0x77, 0xFB, 0xA5, 0x1D, 0xB9, 0x2C, 0x2A
    ).map { it.toByte() }.toByteArray()

    context(::encodeRecoveryKey.name) {
        should("encode") {
            encodeRecoveryKey(curve25519Key) shouldBe "EsTc LW2K PGiF wKEA 3As5 g5c4 BXwk qeeJ ZJV8 Q9fu gUMN UE4d"
        }
    }
    context(::decodeRecoveryKey.name) {
        should("decode") {
            decodeRecoveryKey(
                "EsTc LW2K PGiF wKEA 3As5 g5c4 BXwk qeeJ ZJV8 Q9fu gUMN UE4d",
            ).getOrThrow() shouldBe curve25519Key
            decodeRecoveryKey(
                "EsTcLW2KPGiFwKEA3As5g5c4BXwkqeeJZJV8Q9fugUMNUE4d",
            ).getOrThrow() shouldBe curve25519Key
            decodeRecoveryKey(
                " EsTc LW2K PGiF wKEA 3As5 g5c4       BXwk qeeJ ZJV8 Q9fu gUMN UE4d   ",
            ).getOrThrow() shouldBe curve25519Key
        }
        should("fail on wrong prefix") {
            decodeRecoveryKey(
                "FsTc LW2K PGiF wKEA 3As5 g5c4 BXwk qeeJ ZJV8 Q9fu gUMN UE4d",
            ).isFailure shouldBe true
            decodeRecoveryKey(
                "EqTc LW2K PGiF wKEA 3As5 g5c4 BXwk qeeJ ZJV8 Q9fu gUMN UE4d",
            ).isFailure shouldBe true
        }
        should("fail on wrong parity") {
            decodeRecoveryKey(
                "EsTc LW2K PGiF wKEA 3As5 g5c4 BXwk qeeJ ZJV8 Q9fu gUMN UE4e",
            ).isFailure shouldBe true
        }
        should("fail on wrong key length") {
            decodeRecoveryKey(
                "abc",
            ).isFailure shouldBe true
        }
    }
    context(::recoveryKeyFromPassphrase.name) {
        should("create from pbkdf2 passphrase") {
            val salt = Random.nextBytes(32)
            val key = generatePbkdf2Sha512(
                password = "super secret passphrase",
                salt = salt,
                iterationCount = 10_000, // just a test, not secure
                keyBitLength = 32 * 8
            )
            recoveryKeyFromPassphrase(
                "super secret passphrase",
                Pbkdf2(salt = salt.encodeBase64(), iterations = 10_000, bits = 32 * 8)
            ).getOrThrow() shouldBe key
        }
    }
})