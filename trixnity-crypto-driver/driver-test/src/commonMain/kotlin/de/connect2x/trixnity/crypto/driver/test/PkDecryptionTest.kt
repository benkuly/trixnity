package de.connect2x.trixnity.crypto.driver.test

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank
import de.connect2x.trixnity.crypto.driver.CryptoDriver
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import kotlin.test.Test

open class PkDecryptionTest(val driver: CryptoDriver) : TrixnityBaseTest() {

    @Test
    fun decrypt() = runTest {
        driver.pk.decryption.invoke().use { pkDecryption ->
            pkDecryption.publicKey.base64 shouldNot beBlank()
            pkDecryption.secretKey.base64 shouldNot beBlank()

            driver.pk.encryption.invoke(pkDecryption.publicKey.base64).use { pkEncryption ->
                val message = pkEncryption.encrypt("Public key test")
                pkDecryption.decrypt(message) shouldBe "Public key test"
            }
        }
    }

    @Test
    fun createWithPrivateKey() = runTest {
        driver.pk.decryption.invoke("W69V7atpH+HldmtexIZSEg51sNITai/Yut3pOw1pON4").use { pkDecryption ->
            pkDecryption.secretKey.base64 shouldBe "W69V7atpH+HldmtexIZSEg51sNITai/Yut3pOw1pON4"

            driver.pk.encryption.invoke(pkDecryption.publicKey.base64).use { pkEncryption ->
                val message = pkEncryption.encrypt("Public key test")
                pkDecryption.decrypt(message) shouldBe "Public key test"
            }
        }
    }
}