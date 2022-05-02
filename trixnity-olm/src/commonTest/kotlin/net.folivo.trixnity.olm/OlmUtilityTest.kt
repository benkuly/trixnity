package net.folivo.trixnity.olm

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OlmUtilityTest {

    private val message =
        """{"algorithms":["m.megolm.v1.aes-sha2","m.olm.v1.curve25519-aes-sha2"],"device_id":"YMBYCWTWCG","keys":{"curve25519:YMBYCWTWCG":"KZFa5YUXV2EOdhK8dcGMMHWB67stdgAP4+xwiS69mCU","ed25519:YMBYCWTWCG":"0cEgQJJqjgtXUGp4ZXQQmh36RAxwxr8HJw2E9v1gvA0"},"user_id":"@mxBob14774891254276b253f42-f267-43ec-bad9-767142bfea30:localhost:8480"}"""

    @Test
    fun verifyEd25519Signing_shouldSign() = runTest {
        freeAfter(OlmAccount.create(), OlmUtility.create()) { account, utility ->
            val messageSignature = account.sign(message)
            utility.verifyEd25519(account.identityKeys.ed25519, message, messageSignature)
        }
    }

    @Test
    fun verifyEd25519Signing_shouldFailOnBadSignature() = runTest {
        freeAfter(OlmAccount.create(), OlmUtility.create()) { account, utility ->
            val badSignature = "Bad signature Bad signature Bad signature.."
            shouldThrow<OlmLibraryException> {
                utility.verifyEd25519(account.identityKeys.ed25519, message, badSignature)
            }.message shouldBe "BAD_MESSAGE_MAC"
        }
    }

    @Test
    fun verifyEd25519Signing_shouldFailOnBadFingerprint() = runTest {
        freeAfter(OlmAccount.create(), OlmUtility.create()) { account, utility ->
            val messageSignature = account.sign(message)
            val badSizeFingerPrintKey = account.identityKeys.ed25519.substring(account.identityKeys.ed25519.length / 2)
            shouldThrow<OlmLibraryException> {
                utility.verifyEd25519(badSizeFingerPrintKey, message, messageSignature)
            }.message shouldBe "INVALID_BASE64"
        }
    }

    @Test
    fun sha256() = runTest {
        freeAfter(OlmUtility.create()) { utility ->
            val msgToHash = "The quick brown fox jumps over the lazy dog"
            utility.sha256(msgToHash.encodeToByteArray()) shouldNot beBlank()
        }
    }
}