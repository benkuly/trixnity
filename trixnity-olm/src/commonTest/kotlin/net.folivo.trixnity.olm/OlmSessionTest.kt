package net.folivo.trixnity.olm

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
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
                aliceSession.pickle("") shouldNot beBlank()
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
                    aliceSession.pickle("")
                }
            freeAfter(OlmSession.unpickle("", pickle)) { aliceSession ->
                aliceSession.sessionId shouldBe sessionId
            }
        }
    }
}