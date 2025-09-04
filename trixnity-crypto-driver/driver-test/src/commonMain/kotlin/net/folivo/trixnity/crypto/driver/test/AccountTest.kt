package net.folivo.trixnity.crypto.driver.test

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.beBlank
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import net.folivo.trixnity.crypto.driver.CryptoDriver
import net.folivo.trixnity.crypto.driver.CryptoDriverException
import net.folivo.trixnity.crypto.driver.keys.Curve25519PublicKey
import net.folivo.trixnity.crypto.driver.olm.Message
import net.folivo.trixnity.crypto.driver.useAll
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import kotlin.random.Random
import kotlin.test.Test

open class AccountTest(val driver: CryptoDriver) : TrixnityBaseTest() {

    private val someKey = driver.key.pickleKey(Random.nextBytes(32))
    private val emptyKey = driver.key.pickleKey()

    @Test
    fun create_shouldCreateAndVerify() = runTest {
        val account = driver.olm.account()
        account.close()
    }

    @Test
    fun create_shouldHaveUniqueIdentityKeys() = runTest {
        // This test validates random series are provide enough random values.
        val size = 20
        val accounts = flow { while (true) emit(driver.olm.account()) }.take(size).toList()

        accounts.map { it.curve25519Key }.toSet() shouldHaveSize size

        accounts.forEach { it.close() }
    }

    @Test
    fun identityKeys_shouldContainKeys() = runTest {
        driver.olm.account().use { account ->
            account.ed25519Key.base64 shouldNot beBlank()
            account.curve25519Key.base64 shouldNot beBlank()
        }
    }

    @Test
    fun maxNumberOfOneTimeKeys_shouldBeGreaterThanZero() = runTest {
        driver.olm.account().use { account ->
            account.maxNumberOfOneTimeKeys shouldBeGreaterThan 0
        }
    }

    @Test
    fun generateOneTimeKeys_shouldRun() = runTest {
        driver.olm.account().use { account ->
            account.generateOneTimeKeys(2)
        }
    }

    @Test
    fun generateOneTimeKeys_shouldFailWithZero() = runTest {
        driver.olm.account().use { account ->
            shouldThrow<IllegalArgumentException> {
                account.generateOneTimeKeys(0)
            }
            account.oneTimeKeys shouldHaveSize 0
        }
    }

    @Test
    fun generateOneTimeKeys_shouldFailWithNegative() = runTest {
        driver.olm.account().use { account ->
            shouldThrow<IllegalArgumentException> {
                account.generateOneTimeKeys(-50)
            }
            account.oneTimeKeys shouldHaveSize 0
        }
    }

    @Test
    fun oneTimeKeys_shouldBeEmptyBeforeGeneration() = runTest {
        driver.olm.account().use { account ->
            account.oneTimeKeys shouldHaveSize 0
        }
    }

    @Test
    fun oneTimeKeys_shouldContainKeysAfterGeneration() = runTest {
        driver.olm.account().use { account ->
            account.generateOneTimeKeys(5)

            val oneTimeKeys = account.oneTimeKeys
            oneTimeKeys shouldHaveSize 5

            oneTimeKeys.forEach {
                it.key shouldNot beBlank()
                it.value.base64 shouldNot beBlank()
            }
        }
    }

    @Test
    fun removeOneTimeKeys_shouldRemoveOneTimeKey() = runTest {
        useAll({ driver.olm.account() }, { driver.olm.account() }) { myAccount, theirAccount ->
            theirAccount.generateOneTimeKeys(1)
            val theirIdentityKey = theirAccount.curve25519Key
            val theirOneTimeKey = theirAccount.oneTimeKeys.values.first()

            myAccount.createOutboundSession(theirIdentityKey, theirOneTimeKey).use { mySession ->
                val message = mySession.encrypt("hello")
                theirAccount.createInboundSession(message as Message.PreKey)
                theirAccount.oneTimeKeys shouldHaveSize 0
                shouldThrow<CryptoDriverException> {
                    theirAccount.createInboundSession(message)
                }
            }
        }
    }

    @Test
    fun markOneTimeKeysAsPublished_shouldPreventOneTimeKeysToBePresentAgain() = runTest {
        driver.olm.account().use { account ->
            account.generateOneTimeKeys(2)
            account.oneTimeKeys shouldHaveSize 2
            account.markKeysAsPublished()
            account.oneTimeKeys shouldHaveSize 0
        }
    }

    @Test
    fun sign_shouldSign() = runTest {
        driver.olm.account().use { account ->
            account.sign("String to be signed by olm").base64 shouldNot beBlank()
        }
    }

    @Test
    fun generateFallbackKey_shouldRun() = runTest {
        driver.olm.account().use { account ->
            account.generateFallbackKey()
        }
    }

    @Test
    fun unpublishedFallbackKey_shouldHaveSize1AfterGeneration() = runTest {
        driver.olm.account().use { account ->
            account.generateFallbackKey()
            val key1 = account.fallbackKey
            account.generateFallbackKey()
            val key2 = account.fallbackKey
            key1 shouldNotBe null
            key2 shouldNotBe null
            key1 shouldNotBe key2
        }
    }

    @Test
    fun unpublishedFallbackKey_shouldBeEmptyBeforeGeneration() = runTest {
        driver.olm.account().use { account ->
            account.fallbackKey shouldBe null
        }
    }

    @Test
    fun forgetOldFallbackKey_shouldRunAndRemoveFallbackKey() = runTest {
        driver.olm.account().use { account ->
            account.generateFallbackKey()
            account.fallbackKey shouldNotBe null
            account.forgetFallbackKey()
            account.fallbackKey shouldNotBe null
            account.generateFallbackKey()
            account.fallbackKey shouldNotBe null
        }
    }

    @Test
    fun pickle() = runTest {
        driver.olm.account().use { account ->
            account.pickle(someKey) shouldNot beBlank()
        }
    }

    @Test
    fun pickleWithEmptyKey() = runTest {
        driver.olm.account().use { account ->
            account.pickle(emptyKey) shouldNot beBlank()
        }
    }

    @Test
    fun unpickle() = runTest {
        var keys: Curve25519PublicKey? = null
        val pickle = driver.olm.account().use { account ->
            keys = account.curve25519Key
            account.pickle(someKey)
        }
        pickle shouldNot beBlank()
        driver.olm.account.fromPickle(pickle, someKey).use { account ->
            account.curve25519Key.base64 shouldBe keys?.base64
        }
    }

    @Test
    fun unpickleWithEmptyKey() = runTest {
        var keys: Curve25519PublicKey? = null
        val pickle = driver.olm.account().use { account ->
            keys = account.curve25519Key
            account.pickle(emptyKey)
        }
        pickle shouldNot beBlank()
        driver.olm.account.fromPickle(pickle, emptyKey).use { account ->
            account.curve25519Key.base64 shouldBe keys?.base64
        }
    }
}