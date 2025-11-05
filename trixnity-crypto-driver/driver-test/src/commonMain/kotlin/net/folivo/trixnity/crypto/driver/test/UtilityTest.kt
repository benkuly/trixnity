package net.folivo.trixnity.crypto.driver.test

import io.kotest.assertions.throwables.shouldThrow
import net.folivo.trixnity.crypto.driver.CryptoDriver
import net.folivo.trixnity.crypto.driver.CryptoDriverException
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import kotlin.random.Random
import kotlin.test.Test

open class UtilityTest(val driver: CryptoDriver) : TrixnityBaseTest() {

    private val message =
        """{"algorithms":["m.megolm.v1.aes-sha2","m.olm.v1.curve25519-aes-sha2"],"device_id":"YMBYCWTWCG","keys":{"curve25519:YMBYCWTWCG":"KZFa5YUXV2EOdhK8dcGMMHWB67stdgAP4+xwiS69mCU","ed25519:YMBYCWTWCG":"0cEgQJJqjgtXUGp4ZXQQmh36RAxwxr8HJw2E9v1gvA0"},"user_id":"@mxBob14774891254276b253f42-f267-43ec-bad9-767142bfea30:localhost:8480"}"""

    private val badSignature = driver.key.ed25519Signature(Random.nextBytes(64))

    @Test
    fun verifyEd25519Signing_shouldSign() = runTest {
        driver.olm.account().use { account ->
            val messageSignature = account.sign(message)

            account.ed25519Key.verify(message, messageSignature)
        }
    }

    @Test
    fun verifyEd25519Signing_shouldFailOnBadSignature() = runTest {
        driver.olm.account().use { account ->
            shouldThrow<CryptoDriverException> {
                account.ed25519Key.verify(message, badSignature)
            }
        }
    }
}