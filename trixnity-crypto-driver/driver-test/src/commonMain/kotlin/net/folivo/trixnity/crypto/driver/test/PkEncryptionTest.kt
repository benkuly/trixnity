package net.folivo.trixnity.crypto.driver.test

import io.kotest.assertions.print.print
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.neverNullMatcher
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank
import net.folivo.trixnity.crypto.driver.CryptoDriver
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import kotlin.test.Test

open class PkEncryptionTest(val driver: CryptoDriver) : TrixnityBaseTest() {

    @Test
    fun encrypt() = runTest {
        driver.pk.decryption.invoke().use { pkDecryption ->
            driver.pk.encryption.invoke(pkDecryption.publicKey.base64).use { pkEncryption ->
                val message = pkEncryption.encrypt("Public key test")
                message.ephemeralKey.base64 shouldNot beBlank()
                message.mac shouldNot beZero()
                message.ciphertext shouldNot beZero()
            }
        }
    }
}

private fun beZero() = neverNullMatcher<ByteArray> { value ->
    MatcherResult(
        value.all { it == 0.toByte() },
        { "${value.print().value} should contain only zeros" },
        { "${value.print().value} should not contain only zeros" })
}