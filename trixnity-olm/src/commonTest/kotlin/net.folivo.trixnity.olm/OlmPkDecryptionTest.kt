package net.folivo.trixnity.olm

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OlmPkDecryptionTest {

    @Test
    fun decrypt() = runTest {
        freeAfter(OlmPkDecryption.create()) { pkDecryption ->
            val key = pkDecryption.publicKey
            key shouldNot beBlank()
            pkDecryption.privateKey shouldNot beBlank()
            freeAfter(OlmPkEncryption.create(key)) { pkEncryption ->
                val message = pkEncryption.encrypt("Public key test")
                pkDecryption.decrypt(message) shouldBe "Public key test"
            }
        }
    }

    @Test
    fun createWithPrivateKey() = runTest {
        freeAfter(OlmPkDecryption.create("W69V7atpH+HldmtexIZSEg51sNITai/Yut3pOw1pON4")) { pkDecryption ->
            pkDecryption.privateKey shouldBe "W69V7atpH+HldmtexIZSEg51sNITai/Yut3pOw1pON4"
            freeAfter(OlmPkEncryption.create(pkDecryption.publicKey)) { pkEncryption ->
                val message = pkEncryption.encrypt("Public key test")
                pkDecryption.decrypt(message) shouldBe "Public key test"
            }
        }
    }

    @Test
    fun pickle() = runTest {
        freeAfter(OlmPkDecryption.create()) { pkDecryption ->
            pkDecryption.pickle("someKey") shouldNot beBlank()
        }
    }

    @Test
    fun unpickle() = runTest {
        val pickle = freeAfter(OlmPkDecryption.create("W69V7atpH+HldmtexIZSEg51sNITai/Yut3pOw1pON4")) { pkDecryption ->
            pkDecryption.pickle("someKey")
        }
        freeAfter(OlmPkDecryption.unpickle("someKey", pickle)) { pkDecryption ->
            pkDecryption.privateKey shouldBe "W69V7atpH+HldmtexIZSEg51sNITai/Yut3pOw1pON4"
        }
    }

}