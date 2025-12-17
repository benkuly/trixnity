package net.folivo.trixnity.libolm

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class OlmSessionTest {

    @Test
    fun encryptWithOneTimeKey() = runTest {
        freeAfter(OlmAccount.create(), OlmAccount.create()) { bobAccount, aliceAccount ->
            val bobIdentityKey = bobAccount.identityKeys.curve25519
            bobAccount.generateOneTimeKeys(1)
            val bobOneTimeKey = bobAccount.oneTimeKeys.curve25519.values.first()

            val message =
                freeAfter(OlmSession.createOutbound(aliceAccount, bobIdentityKey, bobOneTimeKey)) { aliceSession ->
                    aliceSession.encrypt("Hello bob , this is alice!")
                }
            message.cipherText shouldNot beBlank()

            val decryptedMessage =
                freeAfter(OlmSession.createInbound(bobAccount, message.cipherText)) { bobSession ->
                    bobSession.decrypt(message)
                }

            decryptedMessage shouldBe "Hello bob , this is alice!"
        }
    }


    @Test
    fun encryptAfterOneTimeKey() = runTest {
        freeAfter(OlmAccount.create(), OlmAccount.create()) { bobAccount, aliceAccount ->
            val bobIdentityKey = bobAccount.identityKeys.curve25519
            bobAccount.generateOneTimeKeys(1)
            val bobOneTimeKey = bobAccount.oneTimeKeys.curve25519.values.first()

            freeAfter(OlmSession.createOutbound(aliceAccount, bobIdentityKey, bobOneTimeKey)) { aliceSession ->
                val message1 = aliceSession.encrypt("Hello bob, this is alice!")
                aliceSession.hasReceivedMessage shouldBe false

                freeAfter(OlmSession.createInbound(bobAccount, message1.cipherText)) { bobSession ->
                    bobSession.hasReceivedMessage shouldBe false
                    bobSession.decrypt(message1) shouldBe "Hello bob, this is alice!"
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
        freeAfter(OlmAccount.create(), OlmAccount.create()) { bobAccount, aliceAccount ->
            val bobIdentityKey = bobAccount.identityKeys.curve25519
            bobAccount.generateOneTimeKeys(1)
            val bobOneTimeKey = bobAccount.oneTimeKeys.curve25519.values.first()

            freeAfter(OlmSession.createOutbound(aliceAccount, bobIdentityKey, bobOneTimeKey)) { aliceSession ->
                val fromAlice1 = aliceSession.encrypt("from alice 1")
                val fromAlice2 = aliceSession.encrypt("from alice 2")
                aliceSession.hasReceivedMessage shouldBe false

                freeAfter(OlmSession.createInbound(bobAccount, fromAlice1.cipherText)) { bobSession ->
                    bobSession.decrypt(fromAlice1) shouldBe "from alice 1"
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
        freeAfter(OlmAccount.create(), OlmAccount.create()) { bobAccount, aliceAccount ->
            val bobIdentityKey = bobAccount.identityKeys.curve25519
            bobAccount.generateOneTimeKeys(1)
            val bobOneTimeKey = bobAccount.oneTimeKeys.curve25519.values.first()

            freeAfter(OlmSession.createOutbound(aliceAccount, bobIdentityKey, bobOneTimeKey)) { aliceSession ->
                val fromAlice1 = aliceSession.encrypt("from alice 1")
                aliceSession.hasReceivedMessage shouldBe false

                freeAfter(OlmSession.createInbound(bobAccount, fromAlice1.cipherText)) { bobSession ->
                    bobSession.decrypt(fromAlice1) shouldBe "from alice 1"

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
        freeAfter(OlmAccount.create(), OlmAccount.create()) { bobAccount, aliceAccount ->
            val bobIdentityKey = bobAccount.identityKeys.curve25519
            bobAccount.generateOneTimeKeys(1)
            val bobOneTimeKey = bobAccount.oneTimeKeys.curve25519.values.first()

            freeAfter(OlmSession.createOutbound(aliceAccount, bobIdentityKey, bobOneTimeKey)) { aliceSession ->
                val message = aliceSession.encrypt("Hello bob , this is alice!")
                freeAfter(OlmSession.createInbound(bobAccount, message.cipherText)) { bobSession ->
                    bobSession.decrypt(message)

                    aliceSession.sessionId shouldBe bobSession.sessionId
                }
            }
        }
    }

    @Test
    fun matchesInboundSession() = runTest {
        freeAfter(OlmAccount.create(), OlmAccount.create()) { bobAccount, aliceAccount ->
            val bobIdentityKey = bobAccount.identityKeys.curve25519
            val aliceIdentityKey = aliceAccount.identityKeys.curve25519

            bobAccount.generateOneTimeKeys(1)
            val bobOneTimeKey = bobAccount.oneTimeKeys.curve25519.values.first()

            freeAfter(OlmSession.createOutbound(aliceAccount, bobIdentityKey, bobOneTimeKey)) { aliceSession ->
                val message = aliceSession.encrypt("Hello bob , this is alice!")
                freeAfter(
                    OlmSession.createInboundFrom(
                        bobAccount,
                        aliceIdentityKey,
                        message.cipherText
                    )
                ) { bobSession ->
                    bobSession.matchesInboundSession(message.cipherText) shouldBe true
                    bobSession.matchesInboundSessionFrom(aliceIdentityKey, message.cipherText)
                }
            }
        }
    }

    @Test
    fun description_shouldNotBeBlank() = runTest {
        freeAfter(OlmAccount.create(), OlmAccount.create()) { bobAccount, aliceAccount ->
            val bobIdentityKey = bobAccount.identityKeys.curve25519
            bobAccount.generateOneTimeKeys(1)
            val bobOneTimeKey = bobAccount.oneTimeKeys.curve25519.values.first()
            freeAfter(OlmSession.createOutbound(aliceAccount, bobIdentityKey, bobOneTimeKey)) { aliceSession ->
                aliceSession.description shouldNot beBlank()
            }
        }
    }

    @Test
    fun pickle() = runTest {
        freeAfter(OlmAccount.create(), OlmAccount.create()) { bobAccount, aliceAccount ->
            val bobIdentityKey = bobAccount.identityKeys.curve25519
            bobAccount.generateOneTimeKeys(1)
            val bobOneTimeKey = bobAccount.oneTimeKeys.curve25519.values.first()
            freeAfter(OlmSession.createOutbound(aliceAccount, bobIdentityKey, bobOneTimeKey)) { aliceSession ->
                aliceSession.pickle("someKey") shouldNot beBlank()
            }
        }
    }

    @Test
    fun pickleWithEmptyKey() = runTest {
        freeAfter(OlmAccount.create(), OlmAccount.create()) { bobAccount, aliceAccount ->
            val bobIdentityKey = bobAccount.identityKeys.curve25519
            bobAccount.generateOneTimeKeys(1)
            val bobOneTimeKey = bobAccount.oneTimeKeys.curve25519.values.first()
            freeAfter(OlmSession.createOutbound(aliceAccount, bobIdentityKey, bobOneTimeKey)) { aliceSession ->
                aliceSession.pickle(null) shouldNot beBlank()
            }
        }
    }

    @Test
    fun unpickle() = runTest {
        freeAfter(OlmAccount.create(), OlmAccount.create()) { bobAccount, aliceAccount ->
            val bobIdentityKey = bobAccount.identityKeys.curve25519
            bobAccount.generateOneTimeKeys(1)
            val bobOneTimeKey = bobAccount.oneTimeKeys.curve25519.values.first()
            var sessionId: String? = null
            val pickle =
                freeAfter(OlmSession.createOutbound(aliceAccount, bobIdentityKey, bobOneTimeKey)) { aliceSession ->
                    sessionId = aliceSession.sessionId
                    aliceSession.pickle("someKey")
                }
            freeAfter(OlmSession.unpickle("someKey", pickle)) { aliceSession ->
                aliceSession.sessionId shouldBe sessionId
            }
        }
    }

    @Test
    fun unpickleWithEmptyKey() = runTest {
        freeAfter(OlmAccount.create(), OlmAccount.create()) { bobAccount, aliceAccount ->
            val bobIdentityKey = bobAccount.identityKeys.curve25519
            bobAccount.generateOneTimeKeys(1)
            val bobOneTimeKey = bobAccount.oneTimeKeys.curve25519.values.first()
            var sessionId: String? = null
            val pickle =
                freeAfter(OlmSession.createOutbound(aliceAccount, bobIdentityKey, bobOneTimeKey)) { aliceSession ->
                    sessionId = aliceSession.sessionId
                    aliceSession.pickle(null)
                }
            freeAfter(OlmSession.unpickle(null, pickle)) { aliceSession ->
                aliceSession.sessionId shouldBe sessionId
            }
        }
    }
}