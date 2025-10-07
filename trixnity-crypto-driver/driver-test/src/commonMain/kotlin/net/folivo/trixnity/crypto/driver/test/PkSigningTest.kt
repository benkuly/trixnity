package net.folivo.trixnity.crypto.driver.test

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank
import net.folivo.trixnity.crypto.driver.CryptoDriver
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import kotlin.test.Test

open class PkSigningTest(val driver: CryptoDriver) : TrixnityBaseTest() {

    @Test
    fun signing() = runTest {
        driver.key.ed25519SecretKey().use { pkSigning ->
            pkSigning.publicKey.base64 shouldNot beBlank()
            val message =
                "Space is big. You just won't believe how vastly, hugely, mind-bogglingly big it is. I mean, you may think it's a long way down the road to the chemist's, but that's just peanuts to space. - Douglas Adams, The Hitchhiker's Guide to the Galaxy "
            val signature = pkSigning.sign(message)
            signature.base64 shouldNot beBlank()

            pkSigning.publicKey.verify(message, signature)
        }
    }

    @Test
    fun createWithPrivateKey() = runTest {
        driver.key.ed25519SecretKey(
            "p/fiOzzdWmXCUUWO6XUctZP6Q0rhNz9RAJ/goUJVbwk"
        ).use { pkSigning ->
            pkSigning.base64 shouldBe "p/fiOzzdWmXCUUWO6XUctZP6Q0rhNz9RAJ/goUJVbwk"
            val message =
                "Space is big. You just won't believe how vastly, hugely, mind-bogglingly big it is. I mean, you may think it's a long way down the road to the chemist's, but that's just peanuts to space. - Douglas Adams, The Hitchhiker's Guide to the Galaxy "
            val signature = pkSigning.sign(message)
            signature.base64 shouldBe "p8uj1591/qc8qK+XuMuGRjBKim0Ll9PqnW2z/xpnA7q0NRYgNwBMgDfyVP5x3HKqJJA/md3CoGu090jbS1INBw"
            pkSigning.publicKey.verify(message, signature)
        }
    }

}