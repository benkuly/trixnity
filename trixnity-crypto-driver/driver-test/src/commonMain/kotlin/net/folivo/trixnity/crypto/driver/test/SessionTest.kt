package net.folivo.trixnity.crypto.driver.test

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank
import net.folivo.trixnity.crypto.driver.CryptoDriver
import net.folivo.trixnity.crypto.driver.olm.Message
import net.folivo.trixnity.crypto.driver.useAll
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import kotlin.random.Random
import kotlin.test.Test

open class SessionTest(val driver: CryptoDriver) : TrixnityBaseTest() {

    private val someKey = driver.key.pickleKey(Random.nextBytes(32))
    private val emptyKey = driver.key.pickleKey()

    @Test
    fun encryptWithOneTimeKey() = runTest {
        useAll({ driver.olm.account() }, { driver.olm.account() }) { bobAccount, aliceAccount ->
            val bobIdentityKey = bobAccount.curve25519Key
            bobAccount.generateOneTimeKeys(1)
            val bobOneTimeKey = bobAccount.oneTimeKeys.values.first()

            val message = aliceAccount.createOutboundSession(
                bobIdentityKey, bobOneTimeKey,
            ).use { aliceSession ->
                aliceSession.encrypt("Hello bob , this is alice!")
            }

            val (decryptedMessage, _) = bobAccount.createInboundSession(message as Message.PreKey)

            decryptedMessage shouldBe "Hello bob , this is alice!"
        }
    }


    @Test
    fun encryptAfterOneTimeKey() = runTest {
        useAll({ driver.olm.account() }, { driver.olm.account() }) { bobAccount, aliceAccount ->
            val bobIdentityKey = bobAccount.curve25519Key
            bobAccount.generateOneTimeKeys(1)
            val bobOneTimeKey = bobAccount.oneTimeKeys.values.first()

            aliceAccount.createOutboundSession(bobIdentityKey, bobOneTimeKey).use { aliceSession ->
                val message1 = aliceSession.encrypt("Hello bob, this is alice!")
                aliceSession.hasReceivedMessage shouldBe false

                val (plaintext, bobSession) = bobAccount.createInboundSession(message1 as Message.PreKey)

                bobSession.use { bobSession ->
                    plaintext shouldBe "Hello bob, this is alice!"
                    bobSession.hasReceivedMessage shouldBe true
                    val message2 = bobSession.encrypt("Hello alice, this is bob!")

                    aliceSession.decrypt(message2) shouldBe "Hello alice, this is bob!"
                    aliceSession.hasReceivedMessage shouldBe true
                    val message3 = aliceSession.encrypt("You are so cool!")

                    bobSession.decrypt(message3) shouldBe "You are so cool!"
                }
            }
        }
    }

    @Test
    fun encryptIndependentOrder() = runTest {
        useAll({ driver.olm.account() }, { driver.olm.account() }) { bobAccount, aliceAccount ->
            val bobIdentityKey = bobAccount.curve25519Key
            bobAccount.generateOneTimeKeys(1)
            val bobOneTimeKey = bobAccount.oneTimeKeys.values.first()

            aliceAccount.createOutboundSession(bobIdentityKey, bobOneTimeKey).use { aliceSession ->
                val fromAlice1 = aliceSession.encrypt("from alice 1")
                val fromAlice2 = aliceSession.encrypt("from alice 2")
                aliceSession.hasReceivedMessage shouldBe false

                val (plaintext, bobSession) = bobAccount.createInboundSession(fromAlice1 as Message.PreKey)

                bobSession.use { bobSession ->
                    plaintext shouldBe "from alice 1"

                    bobSession.decrypt(fromAlice2) shouldBe "from alice 2"

                    val fromAlice3 = aliceSession.encrypt("from alice 3")
                    val fromBob1 = bobSession.encrypt("from bob 1")

                    bobSession.decrypt(fromAlice3) shouldBe "from alice 3"
                    aliceSession.decrypt(fromBob1) shouldBe "from bob 1"
                }
            }
        }
    }

    @Test
    fun acceptLostMessageAndWrongOrder() = runTest {
        useAll({ driver.olm.account() }, { driver.olm.account() }) { bobAccount, aliceAccount ->
            val bobIdentityKey = bobAccount.curve25519Key
            bobAccount.generateOneTimeKeys(1)
            val bobOneTimeKey = bobAccount.oneTimeKeys.values.first()

            aliceAccount.createOutboundSession(bobIdentityKey, bobOneTimeKey).use { aliceSession ->
                val fromAlice1 = aliceSession.encrypt("from alice 1")
                aliceSession.hasReceivedMessage shouldBe false

                val (plaintext, bobSession) = bobAccount.createInboundSession(fromAlice1 as Message.PreKey)

                bobSession.use { bobSession ->
                    plaintext shouldBe "from alice 1"

                    val fromAlice2 = aliceSession.encrypt("from alice 2")
                    aliceSession.encrypt("from alice 3") // lost
                    aliceSession.encrypt("from alice 4") // lost
                    val fromAlice5 = aliceSession.encrypt("from alice 5")

                    bobSession.decrypt(fromAlice5) shouldBe "from alice 5" // wrong order
                    bobSession.decrypt(fromAlice2) shouldBe "from alice 2"

                    val fromBob1 = bobSession.encrypt("from bob 1")
                    bobSession.encrypt("from bob 2")// lost
                    bobSession.encrypt("from bob 3")// lost
                    val fromBob4 = bobSession.encrypt("from bob 4")
                    aliceSession.decrypt(fromBob4) shouldBe "from bob 4" // wrong order
                    aliceSession.decrypt(fromBob1) shouldBe "from bob 1"
                }
            }
        }
    }


    @Test
    fun sessionIdShouldBeSameOnBothEnds() = runTest {
        useAll({ driver.olm.account() }, { driver.olm.account() }) { bobAccount, aliceAccount ->
            val bobIdentityKey = bobAccount.curve25519Key
            bobAccount.generateOneTimeKeys(1)
            val bobOneTimeKey = bobAccount.oneTimeKeys.values.first()

            aliceAccount.createOutboundSession(bobIdentityKey, bobOneTimeKey).use { aliceSession ->
                val message = aliceSession.encrypt("Hello bob , this is alice!")

                bobAccount.createInboundSession(message as Message.PreKey).session.use { bobSession ->
                    aliceSession.sessionId shouldBe bobSession.sessionId
                }
            }
        }
    }

    @Test
    fun pickle() = runTest {
        useAll({ driver.olm.account() }, { driver.olm.account() }) { bobAccount, aliceAccount ->
            val bobIdentityKey = bobAccount.curve25519Key
            bobAccount.generateOneTimeKeys(1)
            val bobOneTimeKey = bobAccount.oneTimeKeys.values.first()
            aliceAccount.createOutboundSession(bobIdentityKey, bobOneTimeKey).use { aliceSession ->
                aliceSession.pickle(someKey) shouldNot beBlank()
            }
        }
    }

    @Test
    fun pickleWithEmptyKey() = runTest {
        useAll({ driver.olm.account() }, { driver.olm.account() }) { bobAccount, aliceAccount ->
            val bobIdentityKey = bobAccount.curve25519Key
            bobAccount.generateOneTimeKeys(1)
            val bobOneTimeKey = bobAccount.oneTimeKeys.values.first()
            aliceAccount.createOutboundSession(bobIdentityKey, bobOneTimeKey).use { aliceSession ->
                aliceSession.pickle(emptyKey) shouldNot beBlank()
            }
        }
    }

    @Test
    fun unpickle() = runTest {
        useAll({ driver.olm.account() }, { driver.olm.account() }) { bobAccount, aliceAccount ->
            val bobIdentityKey = bobAccount.curve25519Key
            bobAccount.generateOneTimeKeys(1)
            val bobOneTimeKey = bobAccount.oneTimeKeys.values.first()
            val (sessionId, pickle) = aliceAccount.createOutboundSession(bobIdentityKey, bobOneTimeKey)
                .use { aliceSession ->
                    aliceSession.sessionId to aliceSession.pickle(someKey)
                }
            driver.olm.session.fromPickle(pickle, someKey).use { aliceSession ->
                aliceSession.sessionId shouldBe sessionId
            }
        }
    }

    @Test
    fun unpickleWithEmptyKey() = runTest {
        useAll({ driver.olm.account() }, { driver.olm.account() }) { bobAccount, aliceAccount ->
            val bobIdentityKey = bobAccount.curve25519Key
            bobAccount.generateOneTimeKeys(1)
            val bobOneTimeKey = bobAccount.oneTimeKeys.values.first()
            val (sessionId, pickle) = aliceAccount.createOutboundSession(bobIdentityKey, bobOneTimeKey)
                .use { aliceSession ->
                    aliceSession.sessionId to aliceSession.pickle(emptyKey)
                }
            driver.olm.session.fromPickle(pickle, emptyKey).use { aliceSession ->
                aliceSession.sessionId shouldBe sessionId
            }
        }
    }
}