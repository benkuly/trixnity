package de.connect2x.trixnity.crypto.core

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import kotlin.test.Test

@OptIn(ExperimentalStdlibApi::class)
class Pbkdf2Test : TrixnityBaseTest() {

    @Test
    fun generatePbkdf2Sha512() = runTest {
        val password = "super secret. not"
        val salt = ByteArray(12) { (it + 1).toByte() }
        val iterationCount = 10_000
        val keyLength = 256
        generatePbkdf2Sha512(password, salt, iterationCount, keyLength).toHexString() shouldBe
                "456e181b5be566aa7afc51e208024a4c8401c5dbbc7d6b4e9ad1c7a21329fb75"
    }
}