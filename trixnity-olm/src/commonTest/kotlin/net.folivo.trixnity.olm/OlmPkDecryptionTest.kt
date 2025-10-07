package net.folivo.trixnity.olm

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import kotlin.test.Test

class OlmPkDecryptionTest : TrixnityBaseTest() {

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
    fun pickleWithEmptyKey() = runTest {
        freeAfter(OlmPkDecryption.create()) { pkDecryption ->
            pkDecryption.pickle(null) shouldNot beBlank()
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

    @Test
    fun unpickleWithEmptyKey() = runTest {
        val pickle = freeAfter(OlmPkDecryption.create("W69V7atpH+HldmtexIZSEg51sNITai/Yut3pOw1pON4")) { pkDecryption ->
            pkDecryption.pickle(null)
        }
        freeAfter(OlmPkDecryption.unpickle(null, pickle)) { pkDecryption ->
            pkDecryption.privateKey shouldBe "W69V7atpH+HldmtexIZSEg51sNITai/Yut3pOw1pON4"
        }
    }
}