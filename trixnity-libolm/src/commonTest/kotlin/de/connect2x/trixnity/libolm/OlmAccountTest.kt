package de.connect2x.trixnity.libolm

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.beBlank
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class OlmAccountTest {
    @Test
    fun create_shouldCreateAndVerify() = runTest {
        val account = OlmAccount.create()
        account.free()
    }

    @Test
    fun create_shouldHaveUniqueIdentityKeys() = runTest {
        // This test validates random series are provide enough random values.
        val size = 20
        val accounts = flow { while (true) emit(OlmAccount.create()) }.take(size).toList()

        accounts.map { it.identityKeys.curve25519 }.toSet() shouldHaveSize size

        accounts.forEach { it.free() }
    }

    @Test
    fun identityKeys_shouldContainKeys() = runTest {
        freeAfter(OlmAccount.create()) { account ->
            val identityKeys = account.identityKeys
            identityKeys.ed25519 shouldNot beBlank()
            identityKeys.curve25519 shouldNot beBlank()
        }
    }

    @Test
    fun maxNumberOfOneTimeKeys_shouldBeGreaterThanZero() = runTest {
        freeAfter(OlmAccount.create()) { account ->
            account.maxNumberOfOneTimeKeys shouldBeGreaterThan 0
        }
    }

    @Test
    fun generateOneTimeKeys_shouldRun() = runTest {
        freeAfter(OlmAccount.create()) { account ->
            account.generateOneTimeKeys(2)
        }
    }

    @Test
    fun generateOneTimeKeys_shouldFailWithZero() = runTest {
        freeAfter(OlmAccount.create()) { account ->
            shouldThrow<IllegalArgumentException> {
                account.generateOneTimeKeys(0)
            }
            account.oneTimeKeys.curve25519 shouldHaveSize 0
        }
    }

    @Test
    fun generateOneTimeKeys_shouldFailWithNegative() = runTest {
        freeAfter(OlmAccount.create()) { account ->
            shouldThrow<IllegalArgumentException> {
                account.generateOneTimeKeys(-50)
            }
            account.oneTimeKeys.curve25519 shouldHaveSize 0
        }
    }

    @Test
    fun oneTimeKeys_shouldBeEmptyBeforeGeneration() = runTest {
        freeAfter(OlmAccount.create()) { account ->
            account.oneTimeKeys.curve25519 shouldHaveSize 0
        }
    }

    @Test
    fun oneTimeKeys_shouldContainKeysAfterGeneration() = runTest {
        freeAfter(OlmAccount.create()) { account ->
            account.generateOneTimeKeys(5)

            val oneTimeKeys = account.oneTimeKeys.curve25519
            oneTimeKeys shouldHaveSize 5

            oneTimeKeys.forEach {
                it.key shouldNot beBlank()
                it.value shouldNot beBlank()
            }
        }
    }

    @Test
    fun removeOneTimeKeys_shouldRemoveOneTimeKey() = runTest {
        freeAfter(OlmAccount.create(), OlmAccount.create()) { myAccount, theirAccount ->
            theirAccount.generateOneTimeKeys(1)
            val myIdentityKey = myAccount.identityKeys.curve25519
            val theirIdentityKey = theirAccount.identityKeys.curve25519
            val theirOneTimeKey = theirAccount.oneTimeKeys.curve25519.values.first()
            freeAfter(OlmSession.createOutbound(myAccount, theirIdentityKey, theirOneTimeKey)) { mySession ->
                val message = mySession.encrypt("hello").cipherText
                freeAfter(OlmSession.createInboundFrom(theirAccount, myIdentityKey, message)) {
                    theirAccount.removeOneTimeKeys(mySession)
                }
                theirAccount.oneTimeKeys.curve25519.values shouldHaveSize 0
                shouldThrow<OlmLibraryException> {
                    OlmSession.createInboundFrom(theirAccount, theirIdentityKey, message)
                }.message shouldBe "BAD_MESSAGE_KEY_ID"
            }
        }
    }

    @Test
    fun markOneTimeKeysAsPublished_shouldPreventOneTimeKeysToBePresentAgain() = runTest {
        freeAfter(OlmAccount.create()) { account ->
            account.generateOneTimeKeys(2)
            account.oneTimeKeys.curve25519 shouldHaveSize 2
            account.markKeysAsPublished()
            account.oneTimeKeys.curve25519 shouldHaveSize 0
        }
    }

    @Test
    fun sign_shouldSign() = runTest {
        freeAfter(OlmAccount.create()) { account ->
            account.sign("String to be signed by olm") shouldNot beBlank()
        }
    }

    @Test
    fun generateFallbackKey_shouldRun() = runTest {
        freeAfter(OlmAccount.create()) { account ->
            account.generateFallbackKey()
        }
    }

    @Test
    fun unpublishedFallbackKey_shouldHaveSize1AfterGeneration() = runTest {
        freeAfter(OlmAccount.create()) { account ->
            account.generateFallbackKey()
            val key1 = account.unpublishedFallbackKey.curve25519
            account.generateFallbackKey()
            val key2 = account.unpublishedFallbackKey.curve25519
            key1 shouldHaveSize 1
            key2 shouldHaveSize 1
            key1 shouldNotBe key2
        }
    }

    @Test
    fun unpublishedFallbackKey_shouldBeEmptyBeforeGeneration() = runTest {
        freeAfter(OlmAccount.create()) { account ->
            account.unpublishedFallbackKey.curve25519 shouldHaveSize 0
        }
    }

    @Test
    fun forgetOldFallbackKey_shouldRunAndRemoveFallbackKey() = runTest {
        freeAfter(OlmAccount.create()) { account ->
            account.generateFallbackKey()
            account.unpublishedFallbackKey.curve25519 shouldHaveSize 1
            account.forgetOldFallbackKey()
            account.unpublishedFallbackKey.curve25519 shouldHaveSize 1
            account.generateFallbackKey()
            account.unpublishedFallbackKey.curve25519 shouldHaveSize 1
        }
    }

    @Test
    fun pickle() = runTest {
        freeAfter(OlmAccount.create()) { account ->
            account.pickle("someKey") shouldNot beBlank()
        }
    }

    @Test
    fun pickleWithEmptyKey() = runTest {
        freeAfter(OlmAccount.create()) { account ->
            account.pickle(null) shouldNot beBlank()
        }
    }

    @Test
    fun unpickle() = runTest {
        var keys: OlmIdentityKeys? = null
        val pickle = freeAfter(OlmAccount.create()) { account ->
            keys = account.identityKeys
            account.pickle("someKey")
        }
        pickle shouldNot beBlank()
        freeAfter(OlmAccount.unpickle("someKey", pickle)) { account ->
            account.identityKeys shouldBe keys
        }
    }

    @Test
    fun unpickleWithEmptyKey() = runTest {
        var keys: OlmIdentityKeys? = null
        val pickle = freeAfter(OlmAccount.create()) { account ->
            keys = account.identityKeys
            account.pickle(null)
        }
        pickle shouldNot beBlank()
        freeAfter(OlmAccount.unpickle(null, pickle)) { account ->
            account.identityKeys shouldBe keys
        }
    }
}