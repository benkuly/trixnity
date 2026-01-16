package de.connect2x.trixnity.crypto.driver.test

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank
import de.connect2x.trixnity.crypto.driver.CryptoDriver
import de.connect2x.trixnity.crypto.driver.useAll
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import kotlin.test.Test

open class SasTest(val driver: CryptoDriver) : TrixnityBaseTest() {
    @Test
    fun testSASCode() = runTest {
        useAll({ driver.sas.invoke() }, { driver.sas.invoke() }) { aliceSAS, bobSAS ->
            val alicePKey = aliceSAS.publicKey
            val bobPKey = bobSAS.publicKey

            alicePKey.base64 shouldNot beBlank()
            bobPKey.base64 shouldNot beBlank()

            val aliceEstablishedSas = aliceSAS.diffieHellman(bobPKey)
            val bobEstablishedSas = bobSAS.diffieHellman(alicePKey)

            val aliceBytes = aliceEstablishedSas.generateBytes("SAS")
            val bobBytes = bobEstablishedSas.generateBytes("SAS")

            aliceBytes.bytes shouldHaveSize 6
            bobBytes.bytes shouldHaveSize 6

            aliceBytes.emojiIndices.asList shouldHaveSize 7
            bobBytes.emojiIndices.asList shouldHaveSize 7

            aliceBytes.decimals.asList shouldHaveSize 3
            bobBytes.decimals.asList shouldHaveSize 3

            aliceBytes.bytes shouldBe bobBytes.bytes
            aliceBytes.emojiIndices shouldBeEqual bobBytes.emojiIndices
            aliceBytes.decimals shouldBeEqual bobBytes.decimals

            val aliceInvalidMac = aliceEstablishedSas.calculateMacInvalidBase64("Hello world!", "SAS")
            val bobInvalidMac = bobEstablishedSas.calculateMacInvalidBase64("Hello world!", "SAS")

            aliceInvalidMac shouldBe bobInvalidMac

            val aliceMac = aliceEstablishedSas.calculateMac("Hello world!", "SAS")
            val bobMac = bobEstablishedSas.calculateMac("Hello world!", "SAS")

            aliceMac.base64 shouldBe bobMac.base64
        }
    }
}