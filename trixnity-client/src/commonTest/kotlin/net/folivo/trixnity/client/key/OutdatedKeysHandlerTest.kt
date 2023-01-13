package net.folivo.trixnity.client.key

import io.kotest.assertions.timing.continually
import io.kotest.assertions.until.fixed
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.mocks.KeyTrustServiceMock
import net.folivo.trixnity.client.mocks.SignServiceMock
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.model.keys.GetKeys
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.HistoryVisibilityEventContent
import net.folivo.trixnity.core.model.events.m.room.HistoryVisibilityEventContent.HistoryVisibility
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.Megolm
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.olm.StoredOutboundMegolmSession
import net.folivo.trixnity.crypto.sign.VerifyResult
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.milliseconds

class OutdatedKeysHandlerTest : ShouldSpec(body)

private val body: ShouldSpec.() -> Unit = {
    timeout = 15_000

    val alice = UserId("alice", "server")
    lateinit var scope: CoroutineScope
    lateinit var keyStore: KeyStore
    lateinit var olmCryptoStore: OlmCryptoStore
    lateinit var accountStore: AccountStore
    lateinit var roomStore: RoomStore
    lateinit var roomStateStore: RoomStateStore
    lateinit var signServiceMock: SignServiceMock
    lateinit var keyTrustServiceMock: KeyTrustServiceMock
    val json = createMatrixEventJson()
    val mappings = createEventContentSerializerMappings()
    lateinit var apiConfig: PortableMockEngineConfig

    val currentSyncState = MutableStateFlow(SyncState.STOPPED)

    lateinit var cut: OutdatedKeysHandler

    beforeTest {
        signServiceMock = SignServiceMock()
        scope = CoroutineScope(Dispatchers.Default)
        keyStore = getInMemoryKeyStore(scope)
        olmCryptoStore = getInMemoryOlmStore(scope)
        accountStore = getInMemoryAccountStore(scope)
        roomStore = getInMemoryRoomStore(scope)
        roomStateStore = getInMemoryRoomStateStore(scope)
        signServiceMock = SignServiceMock()
        keyTrustServiceMock = KeyTrustServiceMock()
        val (api, newApiConfig) = mockMatrixClientServerApiClient(json)
        apiConfig = newApiConfig
        cut = OutdatedKeysHandler(
            api,
            accountStore,
            olmCryptoStore,
            roomStore,
            roomStateStore,
            keyStore,
            signServiceMock,
            keyTrustServiceMock,
            CurrentSyncState(currentSyncState)
        )
        cut.startInCoroutineScope(scope)
        keyTrustServiceMock.returnCalculateCrossSigningKeysTrustLevel = KeySignatureTrustLevel.CrossSigned(false)
        keyTrustServiceMock.returnCalculateDeviceKeysTrustLevel = KeySignatureTrustLevel.CrossSigned(false)
        signServiceMock.returnVerify = VerifyResult.Valid
    }

    afterTest {
        scope.cancel()
    }

    context(OutdatedKeysHandler::handleOutdatedKeys.name) {
        val cedric = UserId("cedric", "server")
        val cedricDevice = "CEDRIC_DEVICE"
        val cedricKey1 = Signed<DeviceKeys, UserId>(
            DeviceKeys(cedric, cedricDevice, setOf(), keysOf(Key.Ed25519Key("id", "value"))), mapOf()
        )
        val aliceDevice1 = "ALICE_DEVICE_1"
        val aliceDevice2 = "ALICE_DEVICE_2"
        val aliceKey1 = Signed<DeviceKeys, UserId>(
            DeviceKeys(alice, aliceDevice1, setOf(), keysOf(Key.Ed25519Key("id", "value"))), mapOf()
        )
        val aliceKey2 = Signed<DeviceKeys, UserId>(
            DeviceKeys(alice, aliceDevice2, setOf(), keysOf(Key.Ed25519Key("id", "value"))), mapOf()
        )
        beforeTest {
            currentSyncState.value = SyncState.RUNNING
        }
        should("do nothing when no keys outdated") {
            var getKeysCalled = false
            apiConfig.endpoints {
                matrixJsonEndpoint(json, mappings, GetKeys()) {
                    getKeysCalled = true
                    GetKeys.Response(
                        mapOf(), mapOf(),
                        mapOf(),
                        mapOf(), mapOf()
                    )
                }
            }
            keyStore.outdatedKeys.value = setOf()
            continually(500.milliseconds, 50.milliseconds.fixed()) {
                getKeysCalled shouldBe false
            }
        }
        should("set to empty if there are no keys") {
            apiConfig.endpoints {
                matrixJsonEndpoint(json, mappings, GetKeys()) {
                    GetKeys.Response(
                        mapOf(), mapOf(),
                        mapOf(),
                        mapOf(), mapOf()
                    )
                }
            }
            keyStore.outdatedKeys.value = setOf(alice)
            keyStore.getCrossSigningKeys(alice).first { it?.isEmpty() == true }
            keyStore.getDeviceKeys(alice).first { it?.isEmpty() == true }
        }
        context("master keys") {
            should("allow missing signature") {
                val key = Signed<CrossSigningKeys, UserId>(
                    CrossSigningKeys(
                        alice,
                        setOf(CrossSigningKeysUsage.MasterKey),
                        keysOf(Key.Ed25519Key("id", "value"))
                    ), mapOf()
                )
                signServiceMock.returnVerify = VerifyResult.MissingSignature("")
                apiConfig.endpoints {
                    matrixJsonEndpoint(json, mappings, GetKeys()) {
                        GetKeys.Response(
                            mapOf(), mapOf(),
                            mapOf(alice to key),
                            mapOf(), mapOf()
                        )
                    }
                }
                keyStore.outdatedKeys.value = setOf(alice)
                keyStore.outdatedKeys.first { it.isEmpty() }
                keyStore.getCrossSigningKeys(alice).first() shouldContainExactly setOf(
                    StoredCrossSigningKeys(key, KeySignatureTrustLevel.CrossSigned(false))
                )
                keyTrustServiceMock.updateTrustLevelOfKeyChainSignedByCalled.value shouldBe Pair(
                    alice, Key.Ed25519Key("id", "value")
                )
            }
            should("ignore when signatures are invalid") {
                val invalidKey = Signed(
                    CrossSigningKeys(
                        alice,
                        setOf(CrossSigningKeysUsage.MasterKey),
                        keysOf(Key.Ed25519Key("id", "value"))
                    ),
                    mapOf(alice to keysOf(Key.Ed25519Key("invalid", "invalid")))
                )
                signServiceMock.returnVerify = VerifyResult.Invalid("")
                apiConfig.endpoints {
                    matrixJsonEndpoint(json, mappings, GetKeys()) {
                        GetKeys.Response(
                            mapOf(), mapOf(),
                            mapOf(alice to invalidKey),
                            mapOf(), mapOf()
                        )
                    }
                }
                keyStore.outdatedKeys.value = setOf(alice)
                keyStore.outdatedKeys.first { it.isEmpty() }
                keyStore.getCrossSigningKeys(alice).first().shouldBeEmpty()
                keyTrustServiceMock.updateTrustLevelOfKeyChainSignedByCalled.value shouldBe null
            }
            should("add master key") {
                val key = Signed<CrossSigningKeys, UserId>(
                    CrossSigningKeys(
                        alice,
                        setOf(CrossSigningKeysUsage.MasterKey),
                        keysOf(Key.Ed25519Key("id", "value"))
                    ), mapOf()
                )
                apiConfig.endpoints {
                    matrixJsonEndpoint(json, mappings, GetKeys()) {
                        GetKeys.Response(
                            mapOf(), mapOf(),
                            mapOf(alice to key),
                            mapOf(), mapOf()
                        )
                    }
                }
                keyStore.outdatedKeys.value = setOf(alice)
                keyStore.outdatedKeys.first { it.isEmpty() }
                keyStore.getCrossSigningKeys(alice).first() shouldContainExactly setOf(
                    StoredCrossSigningKeys(key, KeySignatureTrustLevel.CrossSigned(false))
                )
                keyTrustServiceMock.updateTrustLevelOfKeyChainSignedByCalled.value shouldBe Pair(
                    alice, Key.Ed25519Key("id", "value")
                )
            }
        }
        context("self signing keys") {
            should("ignore when signatures are invalid") {
                val invalidKey = Signed(
                    CrossSigningKeys(
                        alice,
                        setOf(CrossSigningKeysUsage.SelfSigningKey),
                        keysOf(Key.Ed25519Key("id", "value"))
                    ),
                    mapOf(alice to keysOf(Key.Ed25519Key("invalid", "invalid")))
                )
                signServiceMock.returnVerify = VerifyResult.Invalid("")
                apiConfig.endpoints {
                    matrixJsonEndpoint(json, mappings, GetKeys()) {
                        GetKeys.Response(
                            mapOf(), mapOf(), mapOf(),
                            mapOf(alice to invalidKey),
                            mapOf()
                        )
                    }
                }
                keyStore.outdatedKeys.value = setOf(alice)
                keyStore.outdatedKeys.first { it.isEmpty() }
                keyStore.getCrossSigningKeys(alice).first().shouldBeEmpty()
                keyTrustServiceMock.updateTrustLevelOfKeyChainSignedByCalled.value shouldBe null
            }
            should("add self signing key") {
                val key = Signed<CrossSigningKeys, UserId>(
                    CrossSigningKeys(
                        alice,
                        setOf(CrossSigningKeysUsage.SelfSigningKey),
                        keysOf(Key.Ed25519Key("id", "value"))
                    ), mapOf()
                )
                apiConfig.endpoints {
                    matrixJsonEndpoint(json, mappings, GetKeys()) {
                        GetKeys.Response(
                            mapOf(), mapOf(), mapOf(),
                            mapOf(alice to key),
                            mapOf()
                        )
                    }
                }
                keyStore.outdatedKeys.value = setOf(alice)
                keyStore.outdatedKeys.first { it.isEmpty() }
                keyStore.getCrossSigningKeys(alice).first() shouldContainExactly setOf(
                    StoredCrossSigningKeys(key, KeySignatureTrustLevel.CrossSigned(false))
                )
                keyTrustServiceMock.updateTrustLevelOfKeyChainSignedByCalled.value shouldBe Pair(
                    alice, Key.Ed25519Key("id", "value")
                )
            }
            should("replace self signing key") {
                val key = Signed<CrossSigningKeys, UserId>(
                    CrossSigningKeys(
                        alice,
                        setOf(CrossSigningKeysUsage.SelfSigningKey),
                        keysOf(Key.Ed25519Key("id", "value"))
                    ), mapOf()
                )
                keyStore.updateCrossSigningKeys(alice) {
                    setOf(
                        StoredCrossSigningKeys(
                            Signed(key.signed, mapOf(alice to keysOf())),
                            KeySignatureTrustLevel.Valid(true)
                        )
                    )
                }
                apiConfig.endpoints {
                    matrixJsonEndpoint(json, mappings, GetKeys()) {
                        GetKeys.Response(
                            mapOf(), mapOf(), mapOf(),
                            mapOf(alice to key),
                            mapOf()
                        )
                    }
                }
                keyStore.outdatedKeys.value = setOf(alice)
                keyStore.outdatedKeys.first { it.isEmpty() }
                keyStore.getCrossSigningKeys(alice).first() shouldContainExactly setOf(
                    StoredCrossSigningKeys(key, KeySignatureTrustLevel.CrossSigned(false))
                )
                keyTrustServiceMock.updateTrustLevelOfKeyChainSignedByCalled.value shouldBe Pair(
                    alice, Key.Ed25519Key("id", "value")
                )
            }
        }
        context("user signing keys") {
            should("ignore when signatures are invalid") {
                val invalidKey = Signed(
                    CrossSigningKeys(
                        alice,
                        setOf(CrossSigningKeysUsage.UserSigningKey),
                        keysOf(Key.Ed25519Key("id", "value"))
                    ),
                    mapOf(alice to keysOf(Key.Ed25519Key("invalid", "invalid")))
                )
                signServiceMock.returnVerify = VerifyResult.Invalid("")
                apiConfig.endpoints {
                    matrixJsonEndpoint(json, mappings, GetKeys()) {
                        GetKeys.Response(
                            mapOf(), mapOf(), mapOf(), mapOf(),
                            mapOf(alice to invalidKey)
                        )
                    }
                }
                keyStore.outdatedKeys.value = setOf(alice)
                keyStore.outdatedKeys.first { it.isEmpty() }
                keyStore.getCrossSigningKeys(alice).first().shouldBeEmpty()
                keyTrustServiceMock.updateTrustLevelOfKeyChainSignedByCalled.value shouldBe null
            }
            should("add user signing key") {
                val key = Signed<CrossSigningKeys, UserId>(
                    CrossSigningKeys(
                        alice,
                        setOf(CrossSigningKeysUsage.UserSigningKey),
                        keysOf(Key.Ed25519Key("id", "value"))
                    ), mapOf()
                )
                apiConfig.endpoints {
                    matrixJsonEndpoint(json, mappings, GetKeys()) {
                        GetKeys.Response(
                            mapOf(), mapOf(), mapOf(), mapOf(),
                            mapOf(alice to key)
                        )
                    }
                }
                keyStore.outdatedKeys.value = setOf(alice)
                keyStore.outdatedKeys.first { it.isEmpty() }
                keyStore.getCrossSigningKeys(alice).first() shouldContainExactly setOf(
                    StoredCrossSigningKeys(key, KeySignatureTrustLevel.CrossSigned(false))
                )
                keyTrustServiceMock.updateTrustLevelOfKeyChainSignedByCalled.value shouldBe Pair(
                    alice, Key.Ed25519Key("id", "value")
                )
            }
            should("replace user signing key") {
                val key = Signed<CrossSigningKeys, UserId>(
                    CrossSigningKeys(
                        alice,
                        setOf(CrossSigningKeysUsage.UserSigningKey),
                        keysOf(Key.Ed25519Key("id", "value"))
                    ), mapOf()
                )
                keyStore.updateCrossSigningKeys(alice) {
                    setOf(
                        StoredCrossSigningKeys(
                            Signed(key.signed, mapOf(alice to keysOf())),
                            KeySignatureTrustLevel.Valid(true)
                        )
                    )
                }
                apiConfig.endpoints {
                    matrixJsonEndpoint(json, mappings, GetKeys()) {
                        GetKeys.Response(
                            mapOf(), mapOf(), mapOf(), mapOf(),
                            mapOf(alice to key)
                        )
                    }
                }
                keyStore.outdatedKeys.value = setOf(alice)
                keyStore.outdatedKeys.first { it.isEmpty() }
                keyStore.getCrossSigningKeys(alice).first() shouldContainExactly setOf(
                    StoredCrossSigningKeys(key, KeySignatureTrustLevel.CrossSigned(false))
                )
                keyTrustServiceMock.updateTrustLevelOfKeyChainSignedByCalled.value shouldBe Pair(
                    alice, Key.Ed25519Key("id", "value")
                )
            }
        }
        context("device keys") {
            should("update outdated device keys") {
                apiConfig.endpoints {
                    matrixJsonEndpoint(json, mappings, GetKeys()) {
                        GetKeys.Response(
                            mapOf(),
                            mapOf(
                                cedric to mapOf(cedricDevice to cedricKey1),
                                alice to mapOf(aliceDevice2 to aliceKey2)
                            ),
                            mapOf(), mapOf(), mapOf()
                        )
                    }
                    matrixJsonEndpoint(json, mappings, GetKeys()) {
                        GetKeys.Response(
                            mapOf(),
                            mapOf(alice to mapOf()),
                            mapOf(), mapOf(), mapOf()
                        )
                    }
                }
                keyStore.outdatedKeys.value = setOf(cedric, alice)
                keyStore.outdatedKeys.first { it.isEmpty() }
                val storedCedricKeys = keyStore.getDeviceKeys(cedric).first()
                assertNotNull(storedCedricKeys)
                storedCedricKeys shouldContainExactly mapOf(
                    cedricDevice to StoredDeviceKeys(
                        cedricKey1,
                        KeySignatureTrustLevel.CrossSigned(false)
                    )
                )
                val storedAliceKeys = keyStore.getDeviceKeys(alice).first()
                assertNotNull(storedAliceKeys)
                storedAliceKeys shouldContainExactly mapOf(
                    aliceDevice2 to StoredDeviceKeys(
                        aliceKey2,
                        KeySignatureTrustLevel.CrossSigned(false)
                    )
                )

                // check delete of device keys
                keyStore.outdatedKeys.value = setOf(alice)
                keyStore.outdatedKeys.first { it.isEmpty() }
                val storedAliceKeysAfterDelete = keyStore.getDeviceKeys(alice).first()
                assertNotNull(storedAliceKeysAfterDelete)
                storedAliceKeysAfterDelete shouldContainExactly mapOf()
            }
            should("look for encrypted room, where the user participates and notify megolm sessions about new devices") {
                apiConfig.endpoints {
                    matrixJsonEndpoint(json, mappings, GetKeys()) {
                        GetKeys.Response(
                            mapOf(),
                            mapOf(
                                cedric to mapOf(cedricDevice to cedricKey1),
                                alice to mapOf(aliceDevice2 to aliceKey2)
                            ),
                            mapOf(), mapOf(), mapOf()
                        )
                    }
                }
                val room1 = RoomId("room1", "server")
                val room2 = RoomId("room2", "server")
                val room3 = RoomId("room3", "server")
                roomStore.update(room1) {
                    simpleRoom.copy(roomId = room1, encryptionAlgorithm = Megolm)
                }
                roomStore.update(room2) {
                    simpleRoom.copy(roomId = room2, encryptionAlgorithm = Megolm)
                }
                roomStore.update(room3) {
                    simpleRoom.copy(roomId = room3, encryptionAlgorithm = Megolm)
                }
                listOf(
                    // room1
                    Event.StateEvent(
                        HistoryVisibilityEventContent(HistoryVisibility.INVITED),
                        EventId("\$event1"),
                        alice,
                        room1,
                        1234,
                        stateKey = ""
                    ),
                    Event.StateEvent(
                        MemberEventContent(membership = Membership.INVITE),
                        EventId("\$event1"),
                        alice,
                        room1,
                        1234,
                        stateKey = alice.full
                    ),
                    Event.StateEvent(
                        MemberEventContent(membership = Membership.LEAVE),
                        EventId("\$event4"),
                        cedric,
                        room1,
                        1234,
                        stateKey = cedric.full
                    ),
                    // room2
                    Event.StateEvent(
                        MemberEventContent(membership = Membership.JOIN),
                        EventId("\$event2"),
                        alice,
                        room2,
                        1234,
                        stateKey = alice.full
                    ),
                    // room3
                    Event.StateEvent(
                        MemberEventContent(membership = Membership.JOIN),
                        EventId("\$event3"),
                        alice,
                        room3,
                        1234,
                        stateKey = alice.full
                    ),
                ).forEach { roomStateStore.update(it) }

                olmCryptoStore.updateOutboundMegolmSession(room1) { StoredOutboundMegolmSession(room1, pickled = "") }
                olmCryptoStore.updateOutboundMegolmSession(room3) {
                    StoredOutboundMegolmSession(
                        room3,
                        newDevices = mapOf(
                            cedric to setOf(cedricDevice),
                            alice to setOf(aliceDevice1),
                        ),
                        pickled = ""
                    )
                }

                keyStore.outdatedKeys.value = setOf(cedric, alice)
                keyStore.outdatedKeys.first { it.isEmpty() }
                olmCryptoStore.getOutboundMegolmSession(room1)?.newDevices shouldBe mapOf(
                    alice to setOf(aliceDevice2),
                )
                olmCryptoStore.getOutboundMegolmSession(room2) should beNull() // there is no session
                olmCryptoStore.getOutboundMegolmSession(room3)?.newDevices shouldBe mapOf(
                    alice to setOf(aliceDevice1, aliceDevice2),
                    cedric to setOf(cedricDevice), // was already present
                )
            }
            should("look for encrypted room, where the user participates and reset megolm sessions when removed devices") {
                keyStore.updateDeviceKeys(alice) {
                    mapOf(
                        aliceDevice1 to StoredDeviceKeys(aliceKey1, KeySignatureTrustLevel.Valid(false)),
                        aliceDevice2 to StoredDeviceKeys(aliceKey2, KeySignatureTrustLevel.Valid(false)),
                    )
                }
                apiConfig.endpoints {
                    matrixJsonEndpoint(json, mappings, GetKeys()) {
                        GetKeys.Response(
                            mapOf(),
                            mapOf(
                                alice to mapOf(aliceDevice2 to aliceKey2)
                            ),
                            mapOf(), mapOf(), mapOf()
                        )
                    }
                }
                val room1 = RoomId("room1", "server")
                roomStore.update(room1) {
                    simpleRoom.copy(roomId = room1, encryptionAlgorithm = Megolm)
                }
                olmCryptoStore.updateOutboundMegolmSession(room1) { StoredOutboundMegolmSession(room1, pickled = "") }

                keyStore.outdatedKeys.value = setOf(alice)
                keyStore.outdatedKeys.first { it.isEmpty() }
                olmCryptoStore.getOutboundMegolmSession(room1) shouldBe null
            }
            context("master key is present") {
                context("at least one device key is not cross signed") {
                    context("mark master key as ${KeySignatureTrustLevel.NotAllDeviceKeysCrossSigned::class.simpleName}") {
                        withData(
                            KeySignatureTrustLevel.CrossSigned(true) to true,
                            KeySignatureTrustLevel.CrossSigned(false) to false,
                        ) { (levelBefore, expectedVerified) ->
                            keyTrustServiceMock.returnCalculateDeviceKeysTrustLevel =
                                KeySignatureTrustLevel.NotCrossSigned()
                            apiConfig.endpoints {
                                matrixJsonEndpoint(json, mappings, GetKeys()) {
                                    GetKeys.Response(
                                        mapOf(),
                                        mapOf(alice to mapOf(aliceDevice2 to aliceKey2)),
                                        mapOf(), mapOf(), mapOf()
                                    )
                                }
                            }
                            keyStore.updateCrossSigningKeys(alice) {
                                setOf(
                                    StoredCrossSigningKeys(
                                        Signed(
                                            CrossSigningKeys(
                                                alice,
                                                setOf(CrossSigningKeysUsage.MasterKey),
                                                keysOf(Key.Ed25519Key("mk_id", "mk_value"))
                                            ),
                                            mapOf()
                                        ), levelBefore
                                    )
                                )
                            }
                            keyStore.outdatedKeys.value = setOf(alice)
                            keyStore.outdatedKeys.first { it.isEmpty() }
                            keyStore.getCrossSigningKey(alice, CrossSigningKeysUsage.MasterKey)
                                ?.trustLevel shouldBe KeySignatureTrustLevel.NotAllDeviceKeysCrossSigned(
                                expectedVerified
                            )
                        }
                    }
                    context("keep trust level") {
                        withData(
                            KeySignatureTrustLevel.Valid(false),
                            KeySignatureTrustLevel.Valid(true),
                            KeySignatureTrustLevel.NotAllDeviceKeysCrossSigned(true),
                            KeySignatureTrustLevel.NotAllDeviceKeysCrossSigned(false),
                            KeySignatureTrustLevel.NotCrossSigned(),
                            KeySignatureTrustLevel.Invalid(""),
                            KeySignatureTrustLevel.Blocked()
                        ) { levelBefore ->
                            apiConfig.endpoints {
                                matrixJsonEndpoint(json, mappings, GetKeys()) {
                                    GetKeys.Response(
                                        mapOf(),
                                        mapOf(alice to mapOf(aliceDevice2 to aliceKey2)),
                                        mapOf(), mapOf(), mapOf()
                                    )
                                }
                            }
                            keyStore.updateCrossSigningKeys(alice) {
                                setOf(
                                    StoredCrossSigningKeys(
                                        Signed(
                                            CrossSigningKeys(
                                                alice,
                                                setOf(CrossSigningKeysUsage.MasterKey),
                                                keysOf(Key.Ed25519Key("id", "value"))
                                            ),
                                            mapOf()
                                        ), levelBefore
                                    )
                                )
                            }
                            keyStore.outdatedKeys.value = setOf(alice)
                            keyStore.outdatedKeys.first { it.isEmpty() }
                            keyStore.getCrossSigningKey(alice, CrossSigningKeysUsage.MasterKey)
                                ?.trustLevel shouldBe levelBefore
                        }
                    }
                }
                context("all device keys are cross signed") {
                    val aliceMasterKey = Signed<CrossSigningKeys, UserId>(
                        CrossSigningKeys(
                            alice, setOf(CrossSigningKeysUsage.MasterKey), keysOf(Key.Ed25519Key("ALICE_MSK", "..."))
                        ),
                        mapOf()
                    )
                    beforeTest {
                        apiConfig.endpoints {
                            matrixJsonEndpoint(json, mappings, GetKeys()) {
                                GetKeys.Response(
                                    mapOf(),
                                    mapOf(
                                        alice to mapOf(
                                            aliceDevice2 to Signed(
                                                aliceKey2.signed,
                                                mapOf(alice to keysOf(Key.Ed25519Key("ALICE_MSK", "...")))
                                            )
                                        )
                                    ),
                                    mapOf(), mapOf(), mapOf()
                                )
                            }
                        }
                    }
                    should("do nothing when his trust level is ${KeySignatureTrustLevel.CrossSigned::class.simpleName}") {
                        keyStore.updateCrossSigningKeys(alice) {
                            setOf(StoredCrossSigningKeys(aliceMasterKey, KeySignatureTrustLevel.CrossSigned(true)))
                        }
                        keyStore.outdatedKeys.value = setOf(alice)
                        keyStore.outdatedKeys.first { it.isEmpty() }
                        keyStore.getCrossSigningKey(alice, CrossSigningKeysUsage.MasterKey)
                            ?.trustLevel shouldBe KeySignatureTrustLevel.CrossSigned(true)
                    }
                }
            }
            context("manipulation of ") {
                should("signature") {
                    signServiceMock.returnVerify = VerifyResult.Invalid("")
                    apiConfig.endpoints {
                        matrixJsonEndpoint(json, mappings, GetKeys()) {
                            GetKeys.Response(
                                mapOf(), mapOf(cedric to mapOf(cedricDevice to cedricKey1)), mapOf(), mapOf(), mapOf()
                            )
                        }
                    }
                    keyStore.outdatedKeys.value = setOf(cedric)
                    keyStore.outdatedKeys.first { it.isEmpty() }
                    val storedKeys = keyStore.getDeviceKeys(cedric).first()
                    assertNotNull(storedKeys)
                    storedKeys shouldHaveSize 0
                }
                withData<Map<UserId, Map<String, Signed<DeviceKeys, UserId>>>>(
                    mapOf(
                        "userId" to mapOf(alice to mapOf(cedricDevice to cedricKey1)),
                        "deviceId" to mapOf(alice to mapOf(cedricDevice to aliceKey2)),
                    )
                ) { deviceKeys ->
                    apiConfig.endpoints {
                        matrixJsonEndpoint(json, mappings, GetKeys()) {
                            GetKeys.Response(
                                mapOf(), deviceKeys, mapOf(), mapOf(), mapOf()
                            )
                        }
                    }
                    keyStore.outdatedKeys.value = setOf(alice, cedric)
                    keyStore.outdatedKeys.first { it.isEmpty() }
                    val storedKeys = keyStore.getDeviceKeys(alice).first()
                    assertNotNull(storedKeys)
                    storedKeys shouldHaveSize 0
                }
            }
        }
    }
}