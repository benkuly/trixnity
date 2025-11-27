package net.folivo.trixnity.libolm

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class OlmSASTest {
    @Test
    fun testSASCode() = runTest {
        freeAfter(OlmSAS.create(), OlmSAS.create()) { aliceSAS, bobSAS ->
            val alicePKey = aliceSAS.publicKey
            val bobPKey = bobSAS.publicKey

            alicePKey shouldNot beBlank()
            bobPKey shouldNot beBlank()

            aliceSAS.setTheirPublicKey(bobPKey)
            bobSAS.setTheirPublicKey(alicePKey)

            val codeLength = 6
            val aliceShortCode = aliceSAS.generateShortCode("SAS", codeLength)
            val bobShortCode = bobSAS.generateShortCode("SAS", codeLength)

            aliceShortCode.size shouldBe codeLength
            bobShortCode.size shouldBe codeLength

            aliceShortCode.forEachIndexed { index, aliceByte ->
                aliceByte shouldBe bobShortCode[index]
            }

            val aliceMac = aliceSAS.calculateMac("Hello world!", "SAS")
            val bobMac = bobSAS.calculateMac("Hello world!", "SAS")

            aliceMac shouldBe bobMac
        }
    }

    @Test
    fun testSASCodeFixedBase64() = runTest {
        freeAfter(OlmSAS.create(), OlmSAS.create()) { aliceSAS, bobSAS ->
            val alicePKey = aliceSAS.publicKey
            val bobPKey = bobSAS.publicKey

            alicePKey shouldNot beBlank()
            bobPKey shouldNot beBlank()

            aliceSAS.setTheirPublicKey(bobPKey)
            bobSAS.setTheirPublicKey(alicePKey)

            val codeLength = 6
            val aliceShortCode = aliceSAS.generateShortCode("SAS", codeLength)
            val bobShortCode = bobSAS.generateShortCode("SAS", codeLength)

            aliceShortCode.size shouldBe codeLength
            bobShortCode.size shouldBe codeLength

            aliceShortCode.forEachIndexed { index, aliceByte ->
                aliceByte shouldBe bobShortCode[index]
            }

            val aliceMac = aliceSAS.calculateMacFixedBase64("Hello world!", "SAS")
            val bobMac = bobSAS.calculateMacFixedBase64("Hello world!", "SAS")

            aliceMac shouldBe bobMac
        }
    }
}