package net.folivo.trixnity.olm

import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank
import kotlin.test.Test

class OlmPkEncryptionTest {

    @Test
    fun encrypt() = initTest {
        freeAfter(OlmPkDecryption.create()) { pkDecryption ->
            val key = pkDecryption.publicKey
            freeAfter(OlmPkEncryption.create(key)) { pkEncryption ->
                val message = pkEncryption.encrypt("Public key test")
                message.ephemeralKey shouldNot beBlank()
                message.mac shouldNot beBlank()
                message.cipherText shouldNot beBlank()
            }
        }
    }
}