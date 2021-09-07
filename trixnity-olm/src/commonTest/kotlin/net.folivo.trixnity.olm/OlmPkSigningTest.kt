package net.folivo.trixnity.olm

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank
import kotlin.test.Test

class OlmPkSigningTest {

    @Test
    fun signing() = initTest {
        freeAfter(OlmPkSigning.create(), OlmUtility.create()) { pkSigning, utility ->
            val publicKey = pkSigning.publicKey
            publicKey shouldNot beBlank()
            val message =
                "Space is big. You just won't believe how vastly, hugely, mind-bogglingly big it is. I mean, you may think it's a long way down the road to the chemist's, but that's just peanuts to space. - Douglas Adams, The Hitchhiker's Guide to the Galaxy "
            val signature = pkSigning.sign(message)
            signature shouldNot beBlank()
            utility.verifyEd25519(publicKey, message, signature)
        }
    }

    @Test
    fun createWithPrivateKey() = initTest {
        freeAfter(
            OlmPkSigning.create("p/fiOzzdWmXCUUWO6XUctZP6Q0rhNz9RAJ/goUJVbwk"),
            OlmUtility.create()
        ) { pkSigning, utility ->
            val privateKey = pkSigning.privateKey
            privateKey shouldBe "p/fiOzzdWmXCUUWO6XUctZP6Q0rhNz9RAJ/goUJVbwk"
            val message =
                "Space is big. You just won't believe how vastly, hugely, mind-bogglingly big it is. I mean, you may think it's a long way down the road to the chemist's, but that's just peanuts to space. - Douglas Adams, The Hitchhiker's Guide to the Galaxy "
            val signature = pkSigning.sign(message)
            signature shouldBe "p8uj1591/qc8qK+XuMuGRjBKim0Ll9PqnW2z/xpnA7q0NRYgNwBMgDfyVP5x3HKqJJA/md3CoGu090jbS1INBw"
            utility.verifyEd25519(pkSigning.publicKey, message, signature)
        }
    }

}