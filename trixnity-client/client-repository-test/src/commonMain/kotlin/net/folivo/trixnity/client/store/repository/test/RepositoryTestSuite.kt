package net.folivo.trixnity.client.store.repository.test

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.createDefaultEventContentSerializerMappingsModule
import net.folivo.trixnity.client.createDefaultMatrixJsonModule
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.store.KeyVerificationState.Blocked
import net.folivo.trixnity.client.store.KeyVerificationState.Verified
import net.folivo.trixnity.client.store.repository.*
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.GlobalAccountDataEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomAccountDataEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.RedactedEventContent
import net.folivo.trixnity.core.model.events.UnknownEventContent
import net.folivo.trixnity.core.model.events.m.*
import net.folivo.trixnity.core.model.events.m.crosssigning.SelfSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.UserSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.NameEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.FileBased
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextBased
import net.folivo.trixnity.core.model.events.m.secret.SecretKeyRequestEventContent
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.model.keys.KeyAlgorithm
import net.folivo.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import net.folivo.trixnity.core.model.keys.KeyValue.Ed25519KeyValue
import net.folivo.trixnity.crypto.SecretType
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmMessageIndex
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmSession
import net.folivo.trixnity.crypto.olm.StoredOlmSession
import net.folivo.trixnity.crypto.olm.StoredOutboundMegolmSession
import org.koin.core.Koin
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

abstract class RepositoryTestSuite(
    private val customRepositoryTransactionManager: suspend () -> RepositoryTransactionManager? = { null },
    private val repositoriesModuleBuilder: suspend () -> Module
) {
    lateinit var di: Koin
    lateinit var rtm: RepositoryTransactionManager
    lateinit var coroutineScope: CoroutineScope

    @AfterTest
    fun afterTest() {
        coroutineScope.cancel()
        di.close()
    }

    private fun runTestWithSetup(testBody: suspend TestScope.() -> Unit) = runTest {
        val repositoriesModule = repositoriesModuleBuilder()
        coroutineScope = CoroutineScope(Dispatchers.Default)
        di = koinApplication {
            modules(
                listOf(
                    repositoriesModule,
                    module {
                        single { MatrixClientConfiguration(storeTimelineEventContentUnencrypted = true) }
                        single { coroutineScope }
                    },
                    createDefaultEventContentSerializerMappingsModule(),
                    createDefaultMatrixJsonModule()
                )
            )
        }.koin
        rtm = di.get()
        testBody()
    }

    private suspend fun rtmTestWrite(key: Int) {
        val cut = di.get<AccountRepository>()
        cut.save(
            key.toLong(), Account(
                olmPickleKey = null,
                baseUrl = "",
                userId = UserId("userId"),
                deviceId = "",
                accessToken = null,
                refreshToken = null,
                syncBatchToken = null,
                filterId = null,
                backgroundFilterId = null,
                displayName = null,
                avatarUrl = null,
                isLocked = false
            )
        )
    }

    private suspend fun rtmTestRead(key: Int): Account? {
        val cut = di.get<AccountRepository>()
        return cut.get(key.toLong())
    }

    @Test
    fun `RepositoryTransactionManager - write does not lock`() = runTestWithSetup {
        rtm = customRepositoryTransactionManager() ?: di.get()
        rtm.writeTransaction {
            rtmTestWrite(0)
            rtmTestRead(0)
            rtm.writeTransaction {
                rtmTestWrite(1)
                rtmTestRead(1)
                rtm.writeTransaction {
                    rtmTestWrite(2)
                    rtmTestRead(2)
                }
            }
        }
    }

    @Test
    fun `RepositoryTransactionManager - write allows simultaneous transactions`() = runTestWithSetup {
        rtm = customRepositoryTransactionManager() ?: di.get()
        val calls = 10
        val callCount = MutableStateFlow(0)
        repeat(calls) { i ->
            launch {
                callCount.update { it + 1 }
                callCount.first { it == calls }
                rtm.writeTransaction {
                    rtmTestWrite(i)
                }
            }
        }
    }

    @Test
    fun `RepositoryTransactionManager - write allows simultaneous writes`() = runTestWithSetup {
        rtm = customRepositoryTransactionManager() ?: di.get()
        val calls = 10
        val callCount = MutableStateFlow(0)
        rtm.writeTransaction {
            coroutineScope {
                repeat(calls) { i ->
                    launch {
                        callCount.update { it + 1 }
                        callCount.first { it == calls }
                        rtmTestWrite(i)
                    }
                }
            }
        }
    }

    @Test
    fun `RepositoryTransactionManager - write allows simultaneous reads`() = runTestWithSetup {
        rtm = customRepositoryTransactionManager() ?: di.get()
        val calls = 10
        val callCount = MutableStateFlow(0)
        rtm.writeTransaction {
            coroutineScope {
                repeat(calls) { i ->
                    launch {
                        callCount.update { it + 1 }
                        callCount.first { it == calls }
                        rtmTestRead(i)
                    }
                }
            }
        }
    }

    @Test
    fun `RepositoryTransactionManager - read does not lock`() = runTestWithSetup {
        rtm = customRepositoryTransactionManager() ?: di.get()
        rtm.readTransaction {
            rtmTestRead(0)
            rtm.readTransaction {
                rtmTestRead(0)
                rtm.readTransaction {
                    rtmTestRead(0)
                }
            }
        }
    }

    @Test
    fun `RepositoryTransactionManager - allow read in write transaction`() = runTestWithSetup {
        rtm = customRepositoryTransactionManager() ?: di.get()
        rtm.writeTransaction {
            rtmTestRead(0)
        }
    }

    @Test
    fun `RepositoryTransactionManager - allow read in parallel to write transaction`() = runTestWithSetup {
        rtm = customRepositoryTransactionManager() ?: di.get()
        val startWrite = MutableStateFlow(false)
        val finishedRead = MutableStateFlow(false)
        launch {
            rtm.writeTransaction {
                startWrite.value = true
                finishedRead.first { it }
                rtmTestWrite(0)
            }
        }
        startWrite.first { it }
        rtm.readTransaction {
            rtmTestRead(0)
        }
        finishedRead.value = true
    }

    @Test
    fun `RepositoryTransactionManager - read allows simultaneous reads`() = runTestWithSetup {
        rtm = customRepositoryTransactionManager() ?: di.get()
        val calls = 10
        val callCount = MutableStateFlow(0)
        rtm.readTransaction {
            coroutineScope {
                repeat(calls) { i ->
                    launch {
                        callCount.update { it + 1 }
                        callCount.first { it == calls }
                        rtmTestRead(i)
                    }
                }
            }
        }
    }

    @Test
    fun `RepositoryTransactionManager - allow work within write transaction`() = runTestWithSetup {
        rtm = customRepositoryTransactionManager() ?: di.get()
        val dummy = MutableStateFlow(listOf<Int>())
        suspend fun work() = coroutineScope {
            repeat(10) { i ->
                launch {
                    delay(10.milliseconds)
                    dummy.update { it + i }
                }
            }
        }
        rtm.writeTransaction {
            rtmTestWrite(0)
            work()
            rtmTestWrite(1)
        }
        rtm.writeTransaction {
            work()
            rtmTestWrite(2)
        }
    }

    @Test
    fun `RepositoryTransactionManager - allow work within read transaction`() = runTestWithSetup {
        rtm = customRepositoryTransactionManager() ?: di.get()
        val dummy = MutableStateFlow(listOf<Int>())
        suspend fun work() = coroutineScope {
            repeat(10) { i ->
                launch {
                    delay(10.milliseconds)
                    dummy.update { it + i }
                }
            }
        }
        rtm.writeTransaction {
            rtmTestWrite(0)
        }
        rtm.readTransaction {
            rtmTestRead(0)
            work()
            rtmTestRead(0)
        }
        rtm.readTransaction {
            work()
            rtmTestRead(0)
        }
    }

    @Test
    fun `RepositoryTransactionManager - rollback on exception`() = runTestWithSetup {
        rtm = customRepositoryTransactionManager() ?: di.get()
        val thrownException = CancellationException("dino")
        var caughtException: Exception? = null
        try {
            rtm.writeTransaction {
                rtmTestWrite(0)
                throw thrownException
            }
        } catch (e: Exception) {
            caughtException = e
        }
        caughtException shouldBe thrownException
        rtm.readTransaction {
            rtmTestRead(0)
        } shouldBe null
    }

    @Test
    fun `AccountRepository - save get and delete`() = runTestWithSetup {
        val cut = di.get<AccountRepository>()
        val account = Account(
            olmPickleKey = null,
            baseUrl = "http://host",
            userId = UserId("alice", "server"),
            deviceId = "aliceDevice",
            accessToken = "accessToken",
            refreshToken = "refreshToken",
            syncBatchToken = "syncToken",
            filterId = "filterId",
            backgroundFilterId = "backgroundFilterId",
            displayName = "displayName",
            avatarUrl = "mxc://localhost/123456",
        )
        rtm.writeTransaction {
            cut.save(1, account)
            cut.get(1) shouldBe account
            val accountCopy = account.copy(syncBatchToken = "otherSyncToken")
            cut.save(1, accountCopy)
            cut.get(1) shouldBe accountCopy
            cut.delete(1)
            cut.get(1) shouldBe null
        }
    }

    @Test
    fun `GlobalAccountDataRepository - save get and delete`() = runTestWithSetup {
        val cut = di.get<GlobalAccountDataRepository>()
        val key1 = "m.direct"
        val key2 = "org.example.mynamespace"
        val accountDataEvent1 = GlobalAccountDataEvent(
            DirectEventContent(
                mapOf(
                    UserId(
                        "alice",
                        "server.org"
                    ) to setOf(RoomId("!!room:server"))
                )
            ), ""
        )
        val accountDataEvent2 = GlobalAccountDataEvent(
            UnknownEventContent(
                JsonObject(mapOf("value" to JsonPrimitive("unicorn"))),
                "org.example.mynamespace"
            ),
            ""
        )
        val accountDataEvent3 = GlobalAccountDataEvent(
            UnknownEventContent(
                JsonObject(mapOf("value" to JsonPrimitive("unicorn"))),
                "org.example.mynamespace.2"
            ),
            ""
        )
        val accountDataEvent1Copy = accountDataEvent1.copy(
            content = DirectEventContent(
                mapOf(
                    UserId(
                        "alice",
                        "server.org"
                    ) to null
                )
            )
        )

        rtm.writeTransaction {
            cut.save(key1, "", accountDataEvent1)
            cut.save(key2, "", accountDataEvent2)
            cut.save(key2, "3", accountDataEvent3)
            cut.get(key1, "") shouldBe accountDataEvent1
            cut.get(key2, "") shouldBe accountDataEvent2
            cut.save(key1, "", accountDataEvent1Copy)
            cut.get(key1, "") shouldBe accountDataEvent1Copy
            cut.delete(key1, "")
            cut.get(key1) shouldHaveSize 0
            cut.get(key2) shouldBe mapOf(
                "" to accountDataEvent2,
                "3" to accountDataEvent3
            )
        }
    }

    @Test
    fun `InboundMegolmMessageIndexRepository - save get and delete`() = runTestWithSetup {
        val cut = di.get<InboundMegolmMessageIndexRepository>()
        val roomId = RoomId("!room:server")
        val messageIndexKey1 =
            InboundMegolmMessageIndexRepositoryKey("session1", roomId, 24)
        val messageIndexKey2 =
            InboundMegolmMessageIndexRepositoryKey("session2", roomId, 12)
        val messageIndex1 = StoredInboundMegolmMessageIndex(
            "session1", roomId, 24,
            EventId("event"),
            1234
        )
        val messageIndex2 = StoredInboundMegolmMessageIndex(
            "session2", roomId, 12,
            EventId("event"),
            1234
        )
        val messageIndex2Copy = messageIndex2.copy(originTimestamp = 1235)

        rtm.writeTransaction {
            cut.save(messageIndexKey1, messageIndex1)
            cut.save(messageIndexKey2, messageIndex2)
            cut.get(messageIndexKey1) shouldBe messageIndex1
            cut.get(messageIndexKey2) shouldBe messageIndex2
            cut.save(messageIndexKey2, messageIndex2Copy)
            cut.get(messageIndexKey2) shouldBe messageIndex2Copy
            cut.delete(messageIndexKey1)
            cut.get(messageIndexKey1) shouldBe null
        }
    }

    @Test
    fun `InboundMegolmSessionRepository - save get and delete`() = runTestWithSetup {
        val cut = di.get<InboundMegolmSessionRepository>()
        val roomId = RoomId("!room:server")
        val inboundSessionKey1 = InboundMegolmSessionRepositoryKey("session1", roomId)
        val inboundSessionKey2 = InboundMegolmSessionRepositoryKey("session2", roomId)
        val inboundSession1 =
            StoredInboundMegolmSession(
                senderKey = Curve25519KeyValue("curve1"),
                sessionId = "session1",
                roomId = roomId,
                firstKnownIndex = 1,
                hasBeenBackedUp = false,
                isTrusted = false,
                senderSigningKey = Ed25519KeyValue("ed1"),
                forwardingCurve25519KeyChain = listOf(
                    Curve25519KeyValue("curveExt1"),
                    Curve25519KeyValue("curveExt2")
                ),
                pickled = "pickle1"
            )
        val inboundSession2 =
            StoredInboundMegolmSession(
                senderKey = Curve25519KeyValue("curve2"),
                sessionId = "session2",
                roomId = roomId,
                firstKnownIndex = 1,
                hasBeenBackedUp = true,
                isTrusted = false,
                senderSigningKey = Ed25519KeyValue("ed2"),
                forwardingCurve25519KeyChain = listOf(),
                pickled = "pickle2"
            )
        val inboundSession2Copy = inboundSession2.copy(pickled = "pickle2Copy")

        rtm.writeTransaction {
            cut.save(inboundSessionKey1, inboundSession1)
            cut.save(inboundSessionKey2, inboundSession2)
            cut.get(inboundSessionKey1) shouldBe inboundSession1
            cut.get(inboundSessionKey2) shouldBe inboundSession2
            cut.getByNotBackedUp() shouldBe setOf(inboundSession1)
            cut.save(inboundSessionKey2, inboundSession2Copy)
            cut.get(inboundSessionKey2) shouldBe inboundSession2Copy
            cut.delete(inboundSessionKey1)
            cut.get(inboundSessionKey1) shouldBe null
        }
    }

    @Test
    fun `KeyChainLinkRepository - save get and delete`() = runTestWithSetup {
        val cut = di.get<KeyChainLinkRepository>()
        val link1 = KeyChainLink(
            signingUserId = UserId("bob", "server"),
            signingKey = Key.Ed25519Key("BOB_DEVICE", "keyValueB"),
            signedUserId = UserId("alice", "server"),
            signedKey = Key.Ed25519Key("ALICE_DEVICE", "keyValueA")
        )
        val link2 = KeyChainLink(
            signingUserId = UserId("cedric", "server"),
            signingKey = Key.Ed25519Key("CEDRIC_DEVICE", "keyValueC"),
            signedUserId = UserId("alice", "server"),
            signedKey = Key.Ed25519Key("ALICE_DEVICE", "keyValueA")
        )
        val link3 = KeyChainLink(
            signingUserId = UserId("bob", "server"),
            signingKey = Key.Ed25519Key("BOB_DEVICE", "keyValueB"),
            signedUserId = UserId("cedric", "server"),
            signedKey = Key.Ed25519Key("CEDRIC_DEVICE", "keyValueC")
        )

        rtm.writeTransaction {
            cut.save(link1)
            cut.save(link2)
            cut.save(link3)
            cut.getBySigningKey(
                UserId("bob", "server"),
                Key.Ed25519Key("BOB_DEVICE", "keyValueB")
            ) shouldBe setOf(link1, link3)
            cut.deleteBySignedKey(
                UserId("alice", "server"),
                Key.Ed25519Key("ALICE_DEVICE", "keyValueA")
            )
            cut.getBySigningKey(
                UserId("bob", "server"),
                Key.Ed25519Key("BOB_DEVICE", "keyValueB")
            ) shouldBe setOf(link3)
        }
    }

    @Test
    fun `KeyVerificationStateRepository - save get and delete`() = runTestWithSetup {
        val cut = di.get<KeyVerificationStateRepository>()
        val verifiedKey1Key = KeyVerificationStateKey(
            keyId = "key1",
            keyAlgorithm = KeyAlgorithm.Ed25519
        )
        val verifiedKey2Key = KeyVerificationStateKey(
            keyId = "key2",
            keyAlgorithm = KeyAlgorithm.Ed25519
        )

        rtm.writeTransaction {
            cut.save(verifiedKey1Key, Verified("keyValue1"))
            cut.save(verifiedKey2Key, Blocked("keyValue2"))
            cut.get(verifiedKey1Key) shouldBe Verified("keyValue1")
            cut.get(verifiedKey2Key) shouldBe Blocked("keyValue2")
            cut.save(verifiedKey1Key, Verified("keyValueChanged"))
            cut.get(verifiedKey1Key) shouldBe Verified("keyValueChanged")
            cut.delete(verifiedKey1Key)
            cut.get(verifiedKey1Key) shouldBe null
        }
    }

    @Test
    fun `MediaCacheMappingRepository - save get and delete`() = runTestWithSetup {
        val cut = di.get<MediaCacheMappingRepository>()
        val key1 = "uri1"
        val key2 = "uri2"
        val mediaCacheMapping1 = MediaCacheMapping(key1, "mxcUri1", 2, ContentType.Text.Plain.toString())
        val mediaCacheMapping2 = MediaCacheMapping(key2, null, 3, ContentType.Image.PNG.toString())
        val uploadMedia2Copy = mediaCacheMapping2.copy(mxcUri = "mxcUri2")

        rtm.writeTransaction {
            cut.save(key1, mediaCacheMapping1)
            cut.save(key2, mediaCacheMapping2)
            cut.get(key1) shouldBe mediaCacheMapping1
            cut.get(key2) shouldBe mediaCacheMapping2
            cut.save(key2, uploadMedia2Copy)
            cut.get(key2) shouldBe uploadMedia2Copy
            cut.delete(key1)
            cut.get(key1) shouldBe null
        }
    }

    @Test
    fun `OlmAccountRepository - save get and delete`() = runTestWithSetup {
        val cut = di.get<OlmAccountRepository>()
        rtm.writeTransaction {
            cut.save(1, "olm")
            cut.get(1) shouldBe "olm"
            cut.save(1, "newOlm")
            cut.get(1) shouldBe "newOlm"
            cut.delete(1)
            cut.get(1) shouldBe null
        }
    }

    @Test
    fun `OlmForgetFallbackKeyAfterRepository - save get and delete`() = runTestWithSetup {
        val cut = di.get<OlmForgetFallbackKeyAfterRepository>()
        rtm.writeTransaction {
            cut.save(1, Instant.fromEpochMilliseconds(24))
            cut.get(1) shouldBe Instant.fromEpochMilliseconds(24)
            cut.save(1, Instant.fromEpochMilliseconds(2424))
            cut.get(1) shouldBe Instant.fromEpochMilliseconds(2424)
            cut.delete(1)
            cut.get(1) shouldBe null
        }
    }

    @Test
    fun `OlmSessionRepository - save get and delete`() = runTestWithSetup {
        val cut = di.get<OlmSessionRepository>()
        val key1 = Curve25519KeyValue("curve1")
        val key2 = Curve25519KeyValue("curve2")
        val session1 =
            StoredOlmSession(
                key1,
                "session1",
                Instant.fromEpochMilliseconds(1234),
                Instant.fromEpochMilliseconds(1234),
                pickled = "1"
            )
        val session2 =
            StoredOlmSession(
                key2,
                "session2",
                Instant.fromEpochMilliseconds(1234),
                Instant.fromEpochMilliseconds(1234),
                pickled = "2"
            )
        val session3 =
            StoredOlmSession(
                key2,
                "session3",
                Instant.fromEpochMilliseconds(1234),
                Instant.fromEpochMilliseconds(1234),
                pickled = "2"
            )

        rtm.writeTransaction {
            cut.save(key1, setOf(session1))
            cut.save(key2, setOf(session2))
            cut.get(key1) shouldContainExactly setOf(session1)
            cut.get(key2) shouldContainExactly setOf(session2)
            cut.save(key2, setOf(session2, session3))
            cut.get(key2) shouldContainExactly setOf(session2, session3)
            cut.delete(key1)
            cut.get(key1) shouldBe null
        }
    }

    @Test
    fun `OutboundMegolmSessionRepository - save get and delete`() = runTestWithSetup {
        val cut = di.get<OutboundMegolmSessionRepository>()
        val key1 = RoomId("!room1:server")
        val key2 = RoomId("!room2:server")
        val session1 = StoredOutboundMegolmSession(key1, pickled = "1")
        val session2 = StoredOutboundMegolmSession(key2, pickled = "2")
        val session2Copy = session2.copy(
            newDevices = mapOf(
                UserId("bob", "server") to setOf("Device1"),
                UserId("alice", "server") to setOf("Device2", "Device3")
            )
        )

        rtm.writeTransaction {
            cut.save(key1, session1)
            cut.save(key2, session2)
            cut.get(key1) shouldBe session1
            cut.get(key2) shouldBe session2
            cut.save(key2, session2Copy)
            cut.get(key2) shouldBe session2Copy
            cut.delete(key1)
            cut.get(key1) shouldBe null
        }
    }

    @Test
    fun `OutdatedKeysRepository - save get and delete`() = runTestWithSetup {
        val cut = di.get<OutdatedKeysRepository>()
        val alice = UserId("alice", "server")
        val bob = UserId("bob", "server")

        rtm.writeTransaction {
            cut.save(1, setOf(alice))
            cut.get(1) shouldContainExactly setOf(alice)
            cut.save(1, setOf(alice, bob))
            cut.get(1) shouldContainExactly setOf(alice, bob)
            cut.delete(1)
            cut.get(1) shouldBe null
        }
    }

    @Test
    fun `RoomAccountDataRepository - save get and delete`() = runTestWithSetup {
        val cut = di.get<RoomAccountDataRepository>()
        val roomId1 = RoomId("!room1:server")
        val roomId2 = RoomId("!room2:server")
        val key1 = RoomAccountDataRepositoryKey(roomId1, "m.fully_read")
        val key2 = RoomAccountDataRepositoryKey(roomId2, "org.example.mynamespace")
        val accountDataEvent1 = RoomAccountDataEvent(FullyReadEventContent(EventId("event1")), roomId1, "")
        val accountDataEvent2 = RoomAccountDataEvent(
            UnknownEventContent(
                JsonObject(mapOf("value" to JsonPrimitive("unicorn"))),
                "org.example.mynamespace"
            ),
            roomId2,
            ""
        )
        val accountDataEvent3 = RoomAccountDataEvent(FullyReadEventContent(EventId("event2")), roomId1, "bla")
        val accountDataEvent2Copy = accountDataEvent2.copy(roomId = roomId1)

        rtm.writeTransaction {
            cut.save(key1, "", accountDataEvent1)
            cut.save(key2, "", accountDataEvent2)
            cut.save(key2, "bla", accountDataEvent3)
            cut.get(key1, "") shouldBe accountDataEvent1
            cut.get(key2, "") shouldBe accountDataEvent2
            cut.save(key2, "", accountDataEvent2Copy)
            cut.get(key2, "") shouldBe accountDataEvent2Copy
            cut.delete(key1, "")
            cut.get(key1) shouldHaveSize 0
            cut.get(key2) shouldBe mapOf(
                "" to accountDataEvent2Copy,
                "bla" to accountDataEvent3
            )
        }
    }

    @Test
    fun `RoomAccountDataRepository - deleteByRoomId`() = runTestWithSetup {
        val cut = di.get<RoomAccountDataRepository>()
        val roomId1 = RoomId("!room1:server")
        val roomId2 = RoomId("!room2:server")
        val key1 = RoomAccountDataRepositoryKey(roomId1, "m.fully_read")
        val key2 = RoomAccountDataRepositoryKey(roomId2, "org.example.mynamespace")
        val key3 = RoomAccountDataRepositoryKey(roomId1, "org.example.mynamespace")
        val accountDataEvent1 = RoomAccountDataEvent(FullyReadEventContent(EventId("event1")), roomId1, "")
        val accountDataEvent2 = RoomAccountDataEvent(FullyReadEventContent(EventId("event2")), roomId2, "")
        val accountDataEvent3 = RoomAccountDataEvent(FullyReadEventContent(EventId("event3")), roomId1, "")
        rtm.writeTransaction {
            cut.save(key1, "", accountDataEvent1)
            cut.save(key2, "", accountDataEvent2)
            cut.save(key3, "", accountDataEvent3)
            cut.deleteByRoomId(roomId1)
            cut.get(key1) shouldHaveSize 0
            cut.get(key2) shouldBe mapOf("" to accountDataEvent2)
            cut.get(key3) shouldHaveSize 0
        }
    }

    @Test
    fun `RoomKeyRequestRepository - save get and delete`() = runTestWithSetup {
        val cut = di.get<RoomKeyRequestRepository>()
        val key1 = "key1"
        val key2 = "key2"
        val roomKeyRequest1 = StoredRoomKeyRequest(
            RoomKeyRequestEventContent(KeyRequestAction.REQUEST, "A", "r1"),
            setOf("DEV1", "DEV2"),
            Instant.fromEpochMilliseconds(1234)
        )
        val roomKeyRequest2 = StoredRoomKeyRequest(
            RoomKeyRequestEventContent(KeyRequestAction.REQUEST, "A", "r2"),
            setOf("DEV1"),
            Instant.fromEpochMilliseconds(23)
        )
        val roomKeyRequest2Copy = roomKeyRequest2.copy(createdAt = Instant.fromEpochMilliseconds(24))

        rtm.writeTransaction {
            cut.save(key1, roomKeyRequest1)
            cut.save(key2, roomKeyRequest2)
            cut.get(key1) shouldBe roomKeyRequest1
            cut.get(key2) shouldBe roomKeyRequest2
            cut.save(key2, roomKeyRequest2Copy)
            cut.get(key2) shouldBe roomKeyRequest2Copy
            cut.delete(key1)
            cut.get(key1) shouldBe null
        }
    }

    @Test
    fun `RoomKeyRequestRepository - get all`() = runTestWithSetup {
        val cut = di.get<RoomKeyRequestRepository>()
        val key1 = "key1"
        val key2 = "key2"
        val roomKeyRequest1 = StoredRoomKeyRequest(
            RoomKeyRequestEventContent(KeyRequestAction.REQUEST, "A", "r1"),
            setOf("DEV1", "DEV2"),
            Instant.fromEpochMilliseconds(1234)
        )
        val roomKeyRequest2 = StoredRoomKeyRequest(
            RoomKeyRequestEventContent(KeyRequestAction.REQUEST, "A", "r2"),
            setOf("DEV1"),
            Instant.fromEpochMilliseconds(23)
        )

        rtm.writeTransaction {
            cut.save(key1, roomKeyRequest1)
            cut.save(key2, roomKeyRequest2)
            cut.getAll() shouldContainAll listOf(roomKeyRequest1, roomKeyRequest2)
        }
    }

    @Test
    fun `RoomOutboxMessageRepository - save get and delete`() = runTestWithSetup {
        val cut = di.get<RoomOutboxMessageRepository>()
        val key1 = RoomOutboxMessageRepositoryKey(RoomId("!room:server"), "transaction1")
        val key2 = RoomOutboxMessageRepositoryKey(RoomId("!room:server"), "transaction2")
        val message1 = RoomOutboxMessage(
            key1.roomId,
            key1.transactionId,
            TextBased.Text("hi"),
            Clock.System.now()
        )
        val message2 = RoomOutboxMessage(
            key2.roomId,
            key2.transactionId,
            FileBased.Image("hi"),
            Clock.System.now()
        )
        val message2Copy = message2.copy(sentAt = Instant.fromEpochMilliseconds(24))

        rtm.writeTransaction {
            cut.save(key1, message1)
            cut.save(key2, message2)
            val get1 = cut.get(key1)
            get1.shouldNotBeNull()
            get1 shouldBe message1
            val get2 = cut.get(key2)
            get2.shouldNotBeNull()
            get2 shouldBe message2
            cut.save(key2, message2Copy)
            val get2Copy = cut.get(key2)
            get2Copy.shouldNotBeNull()
            get2Copy shouldBe message2Copy
            cut.delete(key1)
            cut.get(key1) shouldBe null
        }
    }

    @Test
    fun `RoomOutboxMessageRepository - get all`() = runTestWithSetup {
        val cut = di.get<RoomOutboxMessageRepository>()
        val key1 = RoomOutboxMessageRepositoryKey(RoomId("!room1:server"), "transaction1")
        val key2 = RoomOutboxMessageRepositoryKey(RoomId("!room2:server"), "transaction2")
        val message1 = RoomOutboxMessage(
            key1.roomId,
            key1.transactionId,
            TextBased.Text("hi"),
            Clock.System.now()
        )
        val message2 = RoomOutboxMessage(
            key2.roomId,
            key2.transactionId,
            FileBased.Image("hi"),
            Clock.System.now()
        )

        rtm.writeTransaction {
            cut.save(key1, message1)
            cut.save(key2, message2)
            cut.getAll().size shouldBe 2
        }
    }

    @Test
    fun `RoomOutboxMessageRepository - delete by roomId`() = runTestWithSetup {
        val cut = di.get<RoomOutboxMessageRepository>()
        val key1 = RoomOutboxMessageRepositoryKey(RoomId("!room1:server"), "transaction1")
        val key2 = RoomOutboxMessageRepositoryKey(RoomId("!room2:server"), "transaction2")
        val key3 = RoomOutboxMessageRepositoryKey(RoomId("!room2:server"), "transaction3")
        val message1 = RoomOutboxMessage(
            key1.roomId,
            key1.transactionId,
            TextBased.Text("hi"),
            Clock.System.now()
        )
        val message2 = RoomOutboxMessage(
            key2.roomId,
            key2.transactionId,
            FileBased.Image("hi"),
            Clock.System.now()
        )
        val message3 = RoomOutboxMessage(
            key3.roomId,
            key3.transactionId,
            FileBased.Image("hi"),
            Clock.System.now()
        )

        rtm.writeTransaction {
            cut.save(key1, message1)
            cut.save(key2, message2)
            cut.save(key3, message3)
            cut.deleteByRoomId(key2.roomId)
            cut.getAll().map { it.transactionId } shouldBe listOf(key1.transactionId)
        }
    }

    @Test
    fun `RoomStateRepository - save get and delete`() = runTestWithSetup {
        val cut = di.get<RoomStateRepository>()
        val key1 = RoomStateRepositoryKey(RoomId("!room1:server"), "m.room.member")
        val key2 = RoomStateRepositoryKey(RoomId("!room2:server"), "m.room.name")
        val state1 = StateEvent(
            MemberEventContent(membership = Membership.JOIN),
            EventId("$1event"),
            UserId("alice", "server"),
            RoomId("!room1:server"),
            1234,
            stateKey = "@alice:server"
        )
        val state1Copy = state1.copy(id = EventId("$2event"))
        val state2 = StateEvent(
            NameEventContent("room name"),
            EventId("$2eventId"),
            UserId("bob", "server"),
            RoomId("!room2:server"),
            originTimestamp = 24,
            stateKey = ""
        )
        val state3 = StateEvent(
            NameEventContent("room name"),
            EventId("$2eventId"),
            UserId("celina", "server"),
            RoomId("!room2:server"),
            originTimestamp = 24,
            stateKey = ""
        )

        rtm.writeTransaction {
            cut.save(key1, "@alice:server", state1)
            cut.save(key2, "@bob:server", state2)
            cut.save(key2, "@celina:server", state3)
            cut.get(key1, "@alice:server") shouldBe state1
            cut.get(key2, "@bob:server") shouldBe state2
            cut.save(key1, "@alice:server", state1Copy)
            cut.get(key1, "@alice:server") shouldBe state1Copy
            cut.delete(key1, "@alice:server")
            cut.get(key1) shouldHaveSize 0
            cut.get(key2) shouldBe mapOf(
                "@bob:server" to state2,
                "@celina:server" to state3
            )
        }
    }

    @Test
    fun `RoomStateRepository - save and get by second key`() = runTestWithSetup {
        val cut = di.get<RoomStateRepository>()
        val key = RoomStateRepositoryKey(RoomId("!room3:server"), "m.room.member")
        val event = StateEvent(
            MemberEventContent(membership = Membership.JOIN),
            EventId("\$event"),
            UserId("alice", "server"),
            RoomId("!room1:server"),
            1234,
            stateKey = "@cedric:server"
        )

        rtm.writeTransaction {
            cut.save(key, "@cedric:server", event)
            cut.get(key, "@cedric:server") shouldBe event
        }
    }

    @Test
    fun `RoomStateRepository - getByRoomIds`() = runTestWithSetup {
        val cut = di.get<RoomStateRepository>()
        val key1 = RoomStateRepositoryKey(RoomId("!room1:server"), "m.room.member")
        val key2 = RoomStateRepositoryKey(RoomId("!room2:server"), "m.room.name")
        val key3 = RoomStateRepositoryKey(RoomId("!room2:server"), "m.room.member")
        val state1 = StateEvent(
            MemberEventContent(membership = Membership.JOIN),
            EventId("$1event"),
            UserId("alice", "server"),
            RoomId("!room1:server"),
            1234,
            stateKey = "@alice:server"
        )
        val state2 = StateEvent(
            NameEventContent("room name"),
            EventId("$2event"),
            UserId("bob", "server"),
            RoomId("!room2:server"),
            originTimestamp = 24,
            stateKey = ""
        )
        val state3 = StateEvent(
            MemberEventContent(membership = Membership.INVITE),
            EventId("$1event"),
            UserId("alice", "server"),
            RoomId("!room2:server"),
            1234,
            stateKey = "@alice:server"
        )

        rtm.writeTransaction {
            cut.save(key1, "@alice:server", state1)
            cut.save(key2, "@bob:server", state2)
            cut.save(key3, "@alice:server", state3)
            cut.getByRooms(
                setOf(RoomId("!room2:server")),
                "m.room.member", "@alice:server"
            ) shouldContainExactly setOf(state3)
            cut.getByRooms(
                setOf(RoomId("!room1:server"), RoomId("!room2:server")),
                "m.room.member",
                "@alice:server"
            ) shouldContainExactly setOf(state1, state3)
        }
    }

    @Test
    fun `RoomStateRepository - deleteByRoomId`() = runTestWithSetup {
        val cut = di.get<RoomStateRepository>()
        val key1 = RoomStateRepositoryKey(RoomId("!room1:server"), "m.room.member")
        val key2 = RoomStateRepositoryKey(RoomId("!room2:server"), "m.room.name")
        val key3 = RoomStateRepositoryKey(RoomId("!room1:server"), "m.room.name")

        val state1 = StateEvent(
            MemberEventContent(membership = Membership.JOIN),
            EventId("$1event"),
            UserId("alice", "server"),
            RoomId("!room1:server"),
            1234,
            stateKey = "@alice:server"
        )
        val state2 = StateEvent(
            NameEventContent("room name"),
            EventId("$2eventId"),
            UserId("bob", "server"),
            RoomId("!room2:server"),
            originTimestamp = 24,
            stateKey = ""
        )
        val state3 = StateEvent(
            NameEventContent("room name"),
            EventId("$2eventId"),
            UserId("bob", "server"),
            RoomId("!room1:server"),
            originTimestamp = 24,
            stateKey = ""
        )

        rtm.writeTransaction {
            cut.save(key1, "@alice:server", state1)
            cut.save(key2, "", state2)
            cut.save(key3, "", state3)
            cut.deleteByRoomId(RoomId("!room1:server"))
            cut.get(key1) shouldHaveSize 0
            cut.get(key2) shouldBe mapOf("" to state2)
            cut.get(key3) shouldHaveSize 0
        }
    }

    @Test
    fun `RoomUserReceiptsRepository - save get and delete`() = runTestWithSetup {
        val cut = di.get<RoomUserReceiptsRepository>()
        val key1 = RoomId("!room1:server")
        val key2 = RoomId("!room2:server")
        val userReceipt1 = RoomUserReceipts(
            key1, UserId("alice", "server"), mapOf(
                ReceiptType.FullyRead to RoomUserReceipts.Receipt(
                    EventId("event"),
                    ReceiptEventContent.Receipt(1L)
                )
            )
        )
        val userReceipt2 = RoomUserReceipts(
            key1, UserId("bob", "server"), mapOf(
                ReceiptType.Unknown("bla") to RoomUserReceipts.Receipt(
                    EventId("event"),
                    ReceiptEventContent.Receipt(1L)
                )
            )
        )
        val userReceipt3 = RoomUserReceipts(
            key1, UserId("cedric", "server"), mapOf(
                ReceiptType.FullyRead to RoomUserReceipts.Receipt(
                    EventId("event"),
                    ReceiptEventContent.Receipt(1L)
                )
            )
        )

        rtm.writeTransaction {
            cut.save(key1, userReceipt1.userId, userReceipt1)
            cut.save(key2, userReceipt2.userId, userReceipt2)
            cut.get(key1) shouldBe mapOf(userReceipt1.userId to userReceipt1)
            cut.get(key1, userReceipt1.userId) shouldBe userReceipt1
            cut.get(key2) shouldBe mapOf(userReceipt2.userId to userReceipt2)
            cut.save(key2, userReceipt3.userId, userReceipt3)
            cut.get(key2) shouldBe mapOf(userReceipt2.userId to userReceipt2, userReceipt3.userId to userReceipt3)
            cut.delete(key1, userReceipt1.userId)
            cut.get(key1) shouldHaveSize 0
        }
    }

    @Test
    fun `RoomUserReceiptsRepository - save and get by second key`() = runTestWithSetup {
        val cut = di.get<RoomUserReceiptsRepository>()
        val key = RoomId("!room1:server")
        val userReceipt = RoomUserReceipts(
            key, UserId("alice", "server"), mapOf(
                ReceiptType.FullyRead to RoomUserReceipts.Receipt(
                    EventId("event"),
                    ReceiptEventContent.Receipt(1L)
                )
            )
        )

        rtm.writeTransaction {
            cut.save(key, userReceipt.userId, userReceipt)
            cut.get(key, userReceipt.userId) shouldBe userReceipt
        }
    }

    @Test
    fun `RoomUserRepository - save get and delete`() = runTestWithSetup {
        val cut = di.get<RoomUserRepository>()
        val key1 = RoomId("!room1:server")
        val key2 = RoomId("!room2:server")
        val user1 = RoomUser(
            key1, UserId("alice", "server"), "ALIC", StateEvent(
                MemberEventContent(membership = Membership.JOIN),
                EventId("\$event1"),
                UserId("alice", "server"),
                key1,
                1234,
                stateKey = "@alice:server"
            )
        )
        val user2 = RoomUser(
            key1, UserId("bob", "server"), "BO", StateEvent(
                MemberEventContent(membership = Membership.LEAVE),
                EventId("\$event2"),
                UserId("alice", "server"),
                key2,
                1234,
                stateKey = "@bob:server"
            )
        )
        val user3 = RoomUser(
            key1, UserId("cedric", "server"), "CEDRIC", StateEvent(
                MemberEventContent(membership = Membership.JOIN),
                EventId("\$event3"),
                UserId("cedric", "server"),
                key2,
                1234,
                stateKey = "@cedric:server"
            )
        )

        rtm.writeTransaction {
            cut.save(key1, user1.userId, user1)
            cut.save(key2, user2.userId, user2)
            cut.get(key1) shouldBe mapOf(user1.userId to user1)
            cut.get(key1, user1.userId) shouldBe user1
            cut.get(key2) shouldBe mapOf(user2.userId to user2)
            cut.save(key2, user3.userId, user3)
            cut.get(key2) shouldBe mapOf(user2.userId to user2, user3.userId to user3)
            cut.delete(key1, user1.userId)
            cut.get(key1) shouldHaveSize 0
        }
    }

    @Test
    fun `RoomUserRepository - save and get by second key`() = runTestWithSetup {
        val cut = di.get<RoomUserRepository>()
        val key = RoomId("!room1:server")
        val user = RoomUser(
            key, UserId("alice", "server"), "ALIC", StateEvent(
                MemberEventContent(membership = Membership.JOIN),
                EventId("\$event1"),
                UserId("alice", "server"),
                key,
                1234,
                stateKey = "@alice:server"
            )
        )

        rtm.writeTransaction {
            cut.save(key, user.userId, user)
            cut.get(key, user.userId) shouldBe user
        }
    }

    @Test
    fun `SecretKeyRequestRepository - save get and delete`() = runTestWithSetup {
        val cut = di.get<SecretKeyRequestRepository>()
        val key1 = "key1"
        val key2 = "key2"
        val secretKeyRequest1 = StoredSecretKeyRequest(
            SecretKeyRequestEventContent("1", KeyRequestAction.REQUEST, "A", "r1"),
            setOf("DEV1", "DEV2"),
            Instant.fromEpochMilliseconds(1234)
        )
        val secretKeyRequest2 = StoredSecretKeyRequest(
            SecretKeyRequestEventContent("2", KeyRequestAction.REQUEST, "A", "r2"),
            setOf("DEV1"),
            Instant.fromEpochMilliseconds(23)
        )
        val secretKeyRequest2Copy = secretKeyRequest2.copy(createdAt = Instant.fromEpochMilliseconds(24))

        rtm.writeTransaction {
            cut.save(key1, secretKeyRequest1)
            cut.save(key2, secretKeyRequest2)
            cut.get(key1) shouldBe secretKeyRequest1
            cut.get(key2) shouldBe secretKeyRequest2
            cut.save(key2, secretKeyRequest2Copy)
            cut.get(key2) shouldBe secretKeyRequest2Copy
            cut.delete(key1)
            cut.get(key1) shouldBe null
        }
    }

    @Test
    fun `SecretKeyRequestRepository - get all`() = runTestWithSetup {
        val cut = di.get<SecretKeyRequestRepository>()
        val key1 = "key1"
        val key2 = "key2"
        val secretKeyRequest1 = StoredSecretKeyRequest(
            SecretKeyRequestEventContent("1", KeyRequestAction.REQUEST, "A", "r1"),
            setOf("DEV1", "DEV2"),
            Instant.fromEpochMilliseconds(1234)
        )
        val secretKeyRequest2 = StoredSecretKeyRequest(
            SecretKeyRequestEventContent("2", KeyRequestAction.REQUEST, "A", "r2"),
            setOf("DEV1"),
            Instant.fromEpochMilliseconds(23)
        )

        rtm.writeTransaction {
            cut.save(key1, secretKeyRequest1)
            cut.save(key2, secretKeyRequest2)
            cut.getAll() shouldContainAll listOf(secretKeyRequest1, secretKeyRequest2)
        }
    }

    @Test
    fun `SecretsRepository - save get and delete`() = runTestWithSetup {
        val cut = di.get<SecretsRepository>()
        val secret1 = SecretType.M_CROSS_SIGNING_SELF_SIGNING to StoredSecret(
            GlobalAccountDataEvent(SelfSigningKeyEventContent(mapOf("a" to JsonObject(mapOf())))),
            "priv1"
        )
        val secret2 = SecretType.M_CROSS_SIGNING_USER_SIGNING to StoredSecret(
            GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf("b" to JsonObject(mapOf())))),
            "priv2"
        )

        rtm.writeTransaction {
            cut.save(1, mapOf(secret1))
            cut.get(1) shouldBe mapOf(secret1)
            cut.save(1, mapOf(secret1, secret2))
            cut.get(1) shouldBe mapOf(secret1, secret2)
            cut.delete(1)
            cut.get(1) shouldBe null
        }
    }


    @Test
    fun `TimelineEventRelationRepository - save get and delete`() = runTestWithSetup {
        val cut = di.get<TimelineEventRelationRepository>()
        val relation1 = TimelineEventRelation(
            RoomId("!room1:server"),
            EventId("$1event"),
            RelationType.Reference,
            EventId("\$relatedEvent1")
        )
        val relation2 = TimelineEventRelation(
            RoomId("!room1:server"),
            EventId("$2event"),
            RelationType.Unknown("bla"),
            EventId("\$relatedEvent1"),
        )
        val relation3 = TimelineEventRelation(
            RoomId("!room1:server"),
            EventId("$3event"),
            RelationType.Unknown("bla"),
            EventId("\$relatedEvent1"),
        )

        rtm.writeTransaction {
            cut.save(
                TimelineEventRelationKey(relation1.relatedEventId, relation1.roomId, relation1.relationType),
                relation1.eventId,
                relation1
            )
            cut.save(
                TimelineEventRelationKey(relation2.relatedEventId, relation2.roomId, relation2.relationType),
                relation2.eventId,
                relation2
            )
            cut.save(
                TimelineEventRelationKey(relation3.relatedEventId, relation3.roomId, relation3.relationType),
                relation3.eventId,
                relation3
            )

            cut.get(
                TimelineEventRelationKey(
                    relation1.relatedEventId,
                    relation1.roomId,
                    relation1.relationType
                )
            ) shouldBe mapOf(
                relation1.eventId to relation1
            )
            cut.get(
                TimelineEventRelationKey(
                    relation2.relatedEventId,
                    relation2.roomId,
                    relation2.relationType
                )
            ) shouldBe mapOf(
                relation2.eventId to relation2,
                relation3.eventId to relation3,
            )
            cut.get(
                TimelineEventRelationKey(relation1.relatedEventId, relation1.roomId, relation1.relationType),
                relation1.eventId
            ) shouldBe relation1

            cut.delete(
                TimelineEventRelationKey(relation2.relatedEventId, relation2.roomId, relation2.relationType),
                relation2.eventId
            )
            cut.get(
                TimelineEventRelationKey(
                    relation2.relatedEventId,
                    relation2.roomId,
                    relation2.relationType
                )
            ) shouldBe mapOf(
                relation3.eventId to relation3,
            )

            cut.delete(
                TimelineEventRelationKey(relation1.relatedEventId, relation1.roomId, relation1.relationType),
                relation1.eventId
            )
            cut.get(
                TimelineEventRelationKey(relation1.relatedEventId, relation1.roomId, relation1.relationType),
                relation1.eventId
            ) shouldBe null
        }
    }

    @Test
    fun `TimelineEventRelationRepository - deleteByRoomId`() = runTestWithSetup {
        val cut = di.get<TimelineEventRelationRepository>()
        val relation1 =
            TimelineEventRelation(
                RoomId("!room1:server"),
                EventId("$1event"),
                RelationType.Reference,
                EventId("\$relatedEvent1")
            )
        val relation2 =
            TimelineEventRelation(
                RoomId("!room2:server"),
                EventId("$1event"),
                RelationType.Reference,
                EventId("\$relatedEvent2")
            )
        val relation3 =
            TimelineEventRelation(
                RoomId("!room1:server"),
                EventId("$1event"),
                RelationType.Reference,
                EventId("\$relatedEvent3")
            )
        val relation4 =
            TimelineEventRelation(
                RoomId("!room1:server"),
                EventId("$1event"),
                RelationType.Reference,
                EventId("\$relatedEvent24")
            )

        rtm.writeTransaction {
            cut.save(
                TimelineEventRelationKey(relation1.relatedEventId, relation1.roomId, relation1.relationType),
                relation1.eventId,
                relation1
            )
            cut.save(
                TimelineEventRelationKey(relation2.relatedEventId, relation2.roomId, relation2.relationType),
                relation2.eventId,
                relation2
            )
            cut.save(
                TimelineEventRelationKey(relation3.relatedEventId, relation3.roomId, relation3.relationType),
                relation3.eventId,
                relation3
            )
            cut.save(
                TimelineEventRelationKey(relation4.relatedEventId, relation4.roomId, relation4.relationType),
                relation4.eventId,
                relation4
            )

            cut.deleteByRoomId(RoomId("!room1:server"))
            cut.get(
                TimelineEventRelationKey(relation1.relatedEventId, relation1.roomId, relation1.relationType),
                relation1.eventId,
            ) shouldBe null
            cut.get(
                TimelineEventRelationKey(relation3.relatedEventId, relation3.roomId, relation3.relationType),
                relation3.eventId,
            ) shouldBe null
            cut.get(
                TimelineEventRelationKey(relation4.relatedEventId, relation4.roomId, relation4.relationType),
                relation4.eventId,
            ) shouldBe null

            cut.get(
                TimelineEventRelationKey(relation2.relatedEventId, relation2.roomId, relation2.relationType),
                relation2.eventId,
            ) shouldBe relation2
        }
    }

    @Test
    fun `TimelineEventRepository - save get and delete`() = runTestWithSetup {
        val cut = di.get<TimelineEventRepository>()
        val key1 = TimelineEventKey(EventId("\$event1"), RoomId("!room1:server"))
        val key2 = TimelineEventKey(EventId("\$event2"), RoomId("!room1:server"))
        val event1 = TimelineEvent(
            MessageEvent(
                TextBased.Text("message"),
                EventId("\$event1"),
                UserId("sender", "server"),
                RoomId("!room1:server"),
                1234
            ),
            previousEventId = null,
            nextEventId = null,
            gap = TimelineEvent.Gap.GapBefore("batch")
        )
        val event2 = TimelineEvent(
            MessageEvent(
                TextBased.Text("message"),
                EventId("\$event2"),
                UserId("sender", "server"),
                RoomId("!room1:server"),
                1234
            ),
            previousEventId = null,
            nextEventId = null,
            gap = null
        )
        val session2Copy = event2.copy(nextEventId = EventId("\$superfancy"))

        rtm.writeTransaction {
            cut.save(key1, event1)
            cut.save(key2, event2)
            cut.get(key1) shouldBe event1
            cut.get(key2) shouldBe event2
            cut.save(key2, session2Copy)
            cut.get(key2) shouldBe session2Copy
            cut.delete(key1)
            cut.get(key1) shouldBe null
        }
    }

    @Test
    fun `TimelineEventRepository - redacted events`() = runTestWithSetup {
        val cut = di.get<TimelineEventRepository>()
        val key = TimelineEventKey(EventId("\$event1"), RoomId("!room1:server"))
        val event = TimelineEvent(
            MessageEvent(
                RedactedEventContent("m.room.message"),
                EventId("\$event1"),
                UserId("sender", "server"),
                RoomId("!room1:server"),
                1234
            ),
            content = Result.success(RedactedEventContent("m.room.message")),
            previousEventId = null,
            nextEventId = null,
            gap = TimelineEvent.Gap.GapBefore("batch")
        )
        rtm.writeTransaction {
            cut.save(key, event)
            cut.get(key) shouldBe event
        }
    }

    @Test
    fun `TimelineEventRepository - deleteByRoomId`() = runTestWithSetup {
        val cut = di.get<TimelineEventRepository>()
        val key1 = TimelineEventKey(EventId("\$event1"), RoomId("!room1:server"))
        val key2 = TimelineEventKey(EventId("\$event2"), RoomId("!room2:server"))
        val key3 = TimelineEventKey(EventId("\$event3"), RoomId("!room1:server"))
        val event1 = TimelineEvent(
            MessageEvent(
                TextBased.Text("message"),
                EventId("\$event1"),
                UserId("sender", "server"),
                RoomId("!room1:server"),
                1234
            ),
            previousEventId = null,
            nextEventId = null,
            gap = TimelineEvent.Gap.GapBefore("batch")
        )
        val event2 = TimelineEvent(
            MessageEvent(
                TextBased.Text("message"),
                EventId("\$event2"),
                UserId("sender", "server"),
                RoomId("!room2:server"),
                1234
            ),
            previousEventId = null,
            nextEventId = null,
            gap = null
        )
        val event3 = TimelineEvent(
            MessageEvent(
                TextBased.Text("message"),
                EventId("\$event2"),
                UserId("sender", "server"),
                RoomId("!room1:server"),
                1234
            ),
            previousEventId = null,
            nextEventId = null,
            gap = null
        )

        rtm.writeTransaction {
            cut.save(key1, event1)
            cut.save(key2, event2)
            cut.save(key3, event3)

            cut.deleteByRoomId(RoomId("!room1:server"))
            cut.get(key1) shouldBe null
            cut.get(key2) shouldBe event2
            cut.get(key3) shouldBe null
        }
    }

    @Test
    fun `UserPresenceRepository - save get and delete`() = runTestWithSetup {
        val cut = di.get<UserPresenceRepository>()
        val key1 = UserId("user1", "server")
        val key2 = UserId("user2", "server")
        val userPresence1 = UserPresence(Presence.OFFLINE, Clock.System.now())
        val userPresence2 = UserPresence(Presence.ONLINE, Clock.System.now())
        val userPresence2Copy = userPresence2.copy(statusMessage = "status")

        rtm.writeTransaction {
            cut.save(key1, userPresence1)
            cut.save(key2, userPresence2)
            cut.get(key1) shouldBe userPresence1
            cut.get(key2) shouldBe userPresence2
            cut.save(key2, userPresence2Copy)
            cut.get(key2) shouldBe userPresence2Copy
            cut.delete(key1)
            cut.get(key1) shouldBe null
        }
    }

    @Test
    fun `MigrationRepository - save get and delete`() = runTestWithSetup {
        val cut = di.get<MigrationRepository>()
        val name = "myMigration"
        val metadata1 = "myMetadata"
        val metadata2 = "myOtherMetadata"
        rtm.writeTransaction {
            cut.save(name, metadata1)
            cut.get(name) shouldBe metadata1
            cut.save(name, metadata2)
            cut.get(name) shouldBe metadata2
            cut.delete(name)
            cut.get(name) shouldBe null
        }
    }

    @Test
    fun `MigrationRepository - delete all`() = runTestWithSetup {
        val cut = di.get<MigrationRepository>()
        val data = listOf(
            "migration1" to "metadata1",
            "migration2" to "metadata2",
            "migration3" to "metadata3",
        )
        rtm.writeTransaction {
            data.forEach { cut.save(it.first, it.second) }
            data.forEach { cut.get(it.first) shouldBe it.second }
            cut.deleteAll()
            data.forEach { cut.get(it.first) shouldBe null }
        }
    }
}
