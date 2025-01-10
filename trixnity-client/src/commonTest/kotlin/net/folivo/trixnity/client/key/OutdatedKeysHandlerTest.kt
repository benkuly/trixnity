package net.folivo.trixnity.client.key

import io.kotest.assertions.nondeterministic.continually
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import net.folivo.trixnity.client.CurrentSyncState
import net.folivo.trixnity.client.getInMemoryKeyStore
import net.folivo.trixnity.client.getInMemoryOlmStore
import net.folivo.trixnity.client.getInMemoryRoomStateStore
import net.folivo.trixnity.client.getInMemoryRoomStore
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.mocks.KeyTrustServiceMock
import net.folivo.trixnity.client.mocks.SignServiceMock
import net.folivo.trixnity.client.mocks.TransactionManagerMock
import net.folivo.trixnity.client.simpleRoom
import net.folivo.trixnity.client.store.KeySignatureTrustLevel
import net.folivo.trixnity.client.store.KeyStore
import net.folivo.trixnity.client.store.OlmCryptoStore
import net.folivo.trixnity.client.store.RoomStateStore
import net.folivo.trixnity.client.store.RoomStore
import net.folivo.trixnity.client.store.StoredCrossSigningKeys
import net.folivo.trixnity.client.store.StoredDeviceKeys
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.model.keys.GetKeys
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData
import net.folivo.trixnity.core.model.events.m.room.HistoryVisibilityEventContent
import net.folivo.trixnity.core.model.events.m.room.HistoryVisibilityEventContent.HistoryVisibility
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.keys.CrossSigningKeys
import net.folivo.trixnity.core.model.keys.CrossSigningKeysUsage
import net.folivo.trixnity.core.model.keys.DeviceKeys
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.model.keys.Signed
import net.folivo.trixnity.core.model.keys.keysOf
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
    val bob = UserId("bob", "server")
    val us = UserId("us", "server")
    val aliceDevice = "ALICEDEVICE"
    val ourDevice = "OURDEVICE"
    val aliceKeys = StoredDeviceKeys(
        Signed(DeviceKeys(alice, aliceDevice, setOf(), keysOf()), mapOf()),
        KeySignatureTrustLevel.Valid(false)
    )
    val ourKeys = StoredDeviceKeys(
        Signed(DeviceKeys(us, ourDevice, setOf(), keysOf()), mapOf()),
        KeySignatureTrustLevel.Valid(true)
    )

    lateinit var scope: CoroutineScope
    lateinit var keyStore: KeyStore
    lateinit var olmCryptoStore: OlmCryptoStore
    lateinit var roomStore: RoomStore
    lateinit var roomStateStore: RoomStateStore
    lateinit var signServiceMock: SignServiceMock
    lateinit var keyTrustServiceMock: KeyTrustServiceMock
    val json = createMatrixEventJson()
    lateinit var apiConfig: PortableMockEngineConfig

    val currentSyncState = MutableStateFlow(SyncState.STOPPED)

    lateinit var cut: OutdatedKeysHandler

    beforeTest {
        signServiceMock = SignServiceMock()
        scope = CoroutineScope(Dispatchers.Default)
        keyStore = getInMemoryKeyStore(scope)
        olmCryptoStore = getInMemoryOlmStore(scope)
        roomStore = getInMemoryRoomStore(scope)
        roomStateStore = getInMemoryRoomStateStore(scope)
        signServiceMock = SignServiceMock()
        keyTrustServiceMock = KeyTrustServiceMock()
        val (api, newApiConfig) = mockMatrixClientServerApiClient(json)
        apiConfig = newApiConfig
        cut = OutdatedKeysHandler(
            api,
            olmCryptoStore,
            roomStore,
            roomStateStore,
            keyStore,
            signServiceMock,
            keyTrustServiceMock,
            CurrentSyncState(currentSyncState),
            UserInfo(us, ourDevice, Key.Ed25519Key(null, ""), Key.Curve25519Key(null, "")),
            TransactionManagerMock(),
        )
        cut.startInCoroutineScope(scope)
        keyTrustServiceMock.returnCalculateCrossSigningKeysTrustLevel = KeySignatureTrustLevel.CrossSigned(false)
        keyTrustServiceMock.returnCalculateDeviceKeysTrustLevel = KeySignatureTrustLevel.CrossSigned(false)
        signServiceMock.returnVerify = VerifyResult.Valid
    }

    afterTest {
        scope.cancel()
    }

    context(OutdatedKeysHandler::handleDeviceLists.name) {
        context("device key is tracked") {
            should("add changed devices to outdated keys") {
                keyStore.updateOutdatedKeys { setOf(alice) }
                keyStore.updateDeviceKeys(bob) { mapOf() }
                cut.handleDeviceLists(Sync.Response.DeviceLists(changed = setOf(bob)), SyncState.RUNNING)
                keyStore.getOutdatedKeysFlow().first() shouldContainExactly setOf(alice, bob)
            }
            should("remove key when user left") {
                keyStore.updateOutdatedKeys { setOf(alice, bob) }
                keyStore.updateDeviceKeys(alice) { mapOf() }
                cut.handleDeviceLists(Sync.Response.DeviceLists(left = setOf(alice)), SyncState.RUNNING)
                withContext(KeyStore.SkipOutdatedKeys) {
                    keyStore.getDeviceKeys(alice).first() should beNull()
                    keyStore.getOutdatedKeysFlow().first() shouldContainExactly setOf(bob)
                }
            }
        }
        context("device key is not tracked") {
            should("not add changed devices to outdated keys") {
                keyStore.updateOutdatedKeys { setOf(alice) }
                cut.handleDeviceLists(Sync.Response.DeviceLists(changed = setOf(bob)), SyncState.RUNNING)
                keyStore.getOutdatedKeysFlow().first() shouldContainExactly setOf(alice)
            }
        }
        should("do nothing on initial sync") {
            keyStore.updateOutdatedKeys { setOf(alice) }
            keyStore.updateDeviceKeys(alice) { mapOf() }
            keyStore.updateDeviceKeys(bob) { mapOf() }
            cut.handleDeviceLists(
                Sync.Response.DeviceLists(changed = setOf(bob), left = setOf(alice)),
                SyncState.INITIAL_SYNC
            )
            keyStore.getOutdatedKeysFlow().first() shouldContainExactly setOf(alice)
        }
        should("always handle own keys") {
            cut.handleDeviceLists(
                Sync.Response.DeviceLists(changed = setOf(us, bob)),
                SyncState.RUNNING
            )
            keyStore.getOutdatedKeysFlow().first() shouldContainExactly setOf(us)
        }
        should("always handle own keys in initial sync") {
            cut.handleDeviceLists(
                Sync.Response.DeviceLists(changed = setOf(us, bob)),
                SyncState.INITIAL_SYNC
            )
            keyStore.getOutdatedKeysFlow().first() shouldContainExactly setOf(us)
        }
        should("never remove own keys") {
            keyStore.updateDeviceKeys(us) { mapOf() }
            cut.handleDeviceLists(
                Sync.Response.DeviceLists(left = setOf(us, bob)),
                SyncState.RUNNING
            )
            keyStore.getDeviceKeys(us).first() shouldBe mapOf()
        }
    }
    context(OutdatedKeysHandler::updateDeviceKeysFromChangedMembership.name) {
        val room = RoomId("room", "server")
        beforeTest {
            roomStore.update(room) { simpleRoom.copy(roomId = room, encrypted = true) }
        }
        should("ignore unencrypted rooms") {
            val room2 = RoomId("roo2", "server")
            roomStore.update(room2) { simpleRoom.copy(roomId = room2) }
            cut.updateDeviceKeysFromChangedMembership(
                listOf(
                    StateEvent(
                        MemberEventContent(membership = Membership.JOIN),
                        EventId("\$event"),
                        alice,
                        room2,
                        1234,
                        stateKey = alice.full
                    )
                ),
                SyncState.RUNNING
            )
            keyStore.getOutdatedKeysFlow().first().shouldBeEmpty()
        }
        should("remove device keys on leave or ban of the last encrypted room") {
            keyStore.updateDeviceKeys(alice) { mapOf(aliceDevice to aliceKeys) }
            cut.updateDeviceKeysFromChangedMembership(
                listOf(
                    StateEvent(
                        MemberEventContent(membership = Membership.LEAVE),
                        EventId("\$event"),
                        alice,
                        room,
                        1234,
                        stateKey = alice.full
                    )
                ),
                SyncState.RUNNING
            )
            withContext(KeyStore.SkipOutdatedKeys) {
                keyStore.getDeviceKeys(alice).first() should beNull()
            }

            keyStore.updateDeviceKeys(alice) { mapOf(aliceDevice to aliceKeys) }
            cut.updateDeviceKeysFromChangedMembership(
                listOf(
                    StateEvent(
                        MemberEventContent(membership = Membership.BAN),
                        EventId("\$event"),
                        alice,
                        room,
                        1234,
                        stateKey = alice.full
                    )
                ),
                SyncState.RUNNING
            )
            withContext(KeyStore.SkipOutdatedKeys) {
                keyStore.getDeviceKeys(alice).first() should beNull()
            }
        }
        should("not remove device keys on leave or ban when there are more rooms") {
            val otherRoom = RoomId("otherRoom", "server")
            keyStore.updateDeviceKeys(alice) { mapOf(aliceDevice to aliceKeys) }
            roomStore.update(otherRoom) {
                simpleRoom.copy(roomId = otherRoom, encrypted = true)
            }
            roomStateStore.save(
                StateEvent(
                    MemberEventContent(membership = Membership.JOIN),
                    EventId("\$event"),
                    alice,
                    otherRoom,
                    1234,
                    stateKey = alice.full
                )
            )
            cut.updateDeviceKeysFromChangedMembership(
                listOf(
                    StateEvent(
                        MemberEventContent(membership = Membership.LEAVE),
                        EventId("\$event"),
                        alice,
                        room,
                        1234,
                        stateKey = alice.full
                    )
                ),
                SyncState.RUNNING
            )
            withContext(KeyStore.SkipOutdatedKeys) {
                keyStore.getDeviceKeys(alice).first() shouldNot beNull()
            }

            keyStore.updateDeviceKeys(alice) { mapOf(aliceDevice to aliceKeys) }
            cut.updateDeviceKeysFromChangedMembership(
                listOf(
                    StateEvent(
                        MemberEventContent(membership = Membership.BAN),
                        EventId("\$event"),
                        alice,
                        room,
                        1234,
                        stateKey = alice.full
                    )
                ),
                SyncState.RUNNING
            )
            withContext(KeyStore.SkipOutdatedKeys) {
                keyStore.getDeviceKeys(alice).first() shouldNot beNull()
            }
        }
        should("not remove our device keys on leave or ban when there are no more rooms") {
            keyStore.updateDeviceKeys(us) { mapOf(ourDevice to ourKeys) }

            cut.updateDeviceKeysFromChangedMembership(
                listOf(
                    StateEvent(
                        MemberEventContent(membership = Membership.LEAVE),
                        EventId("\$event"),
                        us,
                        room,
                        1234,
                        stateKey = us.full
                    )
                ),
                SyncState.RUNNING
            )
            roomStore.delete(room)
            withContext(KeyStore.SkipOutdatedKeys) {
                keyStore.getDeviceKeys(us).first() shouldNot beNull()
            }

            keyStore.updateDeviceKeys(us) { mapOf(ourDevice to ourKeys) }
            cut.updateDeviceKeysFromChangedMembership(
                listOf(
                    StateEvent(
                        MemberEventContent(membership = Membership.BAN),
                        EventId("\$event"),
                        us,
                        room,
                        1234,
                        stateKey = us.full
                    )
                ),
                SyncState.RUNNING
            )
            withContext(KeyStore.SkipOutdatedKeys) {
                keyStore.getDeviceKeys(us).first() shouldNot beNull()
            }
        }
        should("ignore join when key already tracked") {
            keyStore.updateDeviceKeys(alice) { mapOf(aliceDevice to aliceKeys) }
            cut.updateDeviceKeysFromChangedMembership(
                listOf(
                    StateEvent(
                        MemberEventContent(membership = Membership.JOIN),
                        EventId("\$event"),
                        alice,
                        room,
                        1234,
                        stateKey = alice.full,
                        unsigned = UnsignedRoomEventData.UnsignedStateEventData(
                            previousContent = MemberEventContent(membership = Membership.JOIN)
                        )
                    )
                ),
                SyncState.RUNNING
            )
            keyStore.getOutdatedKeysFlow().first().shouldBeEmpty()
        }
        should("not mark keys as outdated when not members loaded or loading") {
            cut.updateDeviceKeysFromChangedMembership(
                listOf(
                    StateEvent(
                        MemberEventContent(membership = Membership.JOIN),
                        EventId("\$event"),
                        alice,
                        room,
                        1234,
                        stateKey = alice.full,
                    )
                ),
                SyncState.RUNNING
            )
            keyStore.getOutdatedKeysFlow().first().shouldBeEmpty()
        }
        should("not mark keys as outdated when join, but keys are already tracked") {
            keyStore.updateDeviceKeys(alice) { mapOf(aliceDevice to aliceKeys) }
            cut.updateDeviceKeysFromChangedMembership(
                listOf(
                    StateEvent(
                        MemberEventContent(membership = Membership.JOIN),
                        EventId("\$event"),
                        alice,
                        room,
                        1234,
                        stateKey = alice.full,
                    )
                ),
                SyncState.RUNNING
            )
            keyStore.getOutdatedKeysFlow().first().shouldBeEmpty()
        }
    }
    context(OutdatedKeysHandler::updateOutdatedKeys.name) {
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
                matrixJsonEndpoint(GetKeys()) {
                    getKeysCalled = true
                    GetKeys.Response(
                        mapOf(), mapOf(),
                        mapOf(),
                        mapOf(), mapOf()
                    )
                }
            }
            keyStore.updateOutdatedKeys { setOf() }
            continually(500.milliseconds) {
                getKeysCalled shouldBe false
            }
        }
        should("set to empty if there are no keys") {
            apiConfig.endpoints {
                matrixJsonEndpoint(GetKeys()) {
                    GetKeys.Response(
                        mapOf(), mapOf(),
                        mapOf(),
                        mapOf(), mapOf()
                    )
                }
            }
            keyStore.updateOutdatedKeys { setOf(alice) }
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
                    matrixJsonEndpoint(GetKeys()) {
                        GetKeys.Response(
                            mapOf(), mapOf(),
                            mapOf(alice to key),
                            mapOf(), mapOf()
                        )
                    }
                }
                keyStore.updateOutdatedKeys { setOf(alice) }
                keyStore.getOutdatedKeysFlow().first { it.isEmpty() }
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
                    matrixJsonEndpoint(GetKeys()) {
                        GetKeys.Response(
                            mapOf(), mapOf(),
                            mapOf(alice to invalidKey),
                            mapOf(), mapOf()
                        )
                    }
                }
                keyStore.updateOutdatedKeys { setOf(alice) }
                keyStore.getOutdatedKeysFlow().first { it.isEmpty() }
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
                    matrixJsonEndpoint(GetKeys()) {
                        GetKeys.Response(
                            mapOf(), mapOf(),
                            mapOf(alice to key),
                            mapOf(), mapOf()
                        )
                    }
                }
                keyStore.updateOutdatedKeys { setOf(alice) }
                keyStore.getOutdatedKeysFlow().first { it.isEmpty() }
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
                    matrixJsonEndpoint(GetKeys()) {
                        GetKeys.Response(
                            mapOf(), mapOf(), mapOf(),
                            mapOf(alice to invalidKey),
                            mapOf()
                        )
                    }
                }
                keyStore.updateOutdatedKeys { setOf(alice) }
                keyStore.getOutdatedKeysFlow().first { it.isEmpty() }
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
                    matrixJsonEndpoint(GetKeys()) {
                        GetKeys.Response(
                            mapOf(), mapOf(), mapOf(),
                            mapOf(alice to key),
                            mapOf()
                        )
                    }
                }
                keyStore.updateOutdatedKeys { setOf(alice) }
                keyStore.getOutdatedKeysFlow().first { it.isEmpty() }
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
                    matrixJsonEndpoint(GetKeys()) {
                        GetKeys.Response(
                            mapOf(), mapOf(), mapOf(),
                            mapOf(alice to key),
                            mapOf()
                        )
                    }
                }
                keyStore.updateOutdatedKeys { setOf(alice) }
                keyStore.getOutdatedKeysFlow().first { it.isEmpty() }
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
                    matrixJsonEndpoint(GetKeys()) {
                        GetKeys.Response(
                            mapOf(), mapOf(), mapOf(), mapOf(),
                            mapOf(alice to invalidKey)
                        )
                    }
                }
                keyStore.updateOutdatedKeys { setOf(alice) }
                keyStore.getOutdatedKeysFlow().first { it.isEmpty() }
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
                    matrixJsonEndpoint(GetKeys()) {
                        GetKeys.Response(
                            mapOf(), mapOf(), mapOf(), mapOf(),
                            mapOf(alice to key)
                        )
                    }
                }
                keyStore.updateOutdatedKeys { setOf(alice) }
                keyStore.getOutdatedKeysFlow().first { it.isEmpty() }
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
                    matrixJsonEndpoint(GetKeys()) {
                        GetKeys.Response(
                            mapOf(), mapOf(), mapOf(), mapOf(),
                            mapOf(alice to key)
                        )
                    }
                }
                keyStore.updateOutdatedKeys { setOf(alice) }
                keyStore.getOutdatedKeysFlow().first { it.isEmpty() }
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
                var call = 0
                apiConfig.endpoints {
                    matrixJsonEndpoint(GetKeys()) {
                        when (call) {
                            0 -> GetKeys.Response(
                                mapOf(),
                                mapOf(
                                    cedric to mapOf(cedricDevice to cedricKey1),
                                    alice to mapOf(aliceDevice2 to aliceKey2)
                                ),
                                mapOf(), mapOf(), mapOf()
                            )

                            else -> GetKeys.Response(
                                mapOf(),
                                mapOf(alice to mapOf()),
                                mapOf(), mapOf(), mapOf()
                            )
                        }.also { call++ }
                    }
                }
                keyStore.updateOutdatedKeys { setOf(cedric, alice) }
                keyStore.getOutdatedKeysFlow().first { it.isEmpty() }
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
                keyStore.updateOutdatedKeys { setOf(alice) }
                keyStore.getOutdatedKeysFlow().first { it.isEmpty() }
                val storedAliceKeysAfterDelete = keyStore.getDeviceKeys(alice).first()
                assertNotNull(storedAliceKeysAfterDelete)
                storedAliceKeysAfterDelete shouldContainExactly mapOf()
            }
            should("look for encrypted room, where the user participates and notify megolm sessions about new devices") {
                apiConfig.endpoints {
                    matrixJsonEndpoint(GetKeys()) {
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
                    simpleRoom.copy(roomId = room1, encrypted = true)
                }
                roomStore.update(room2) {
                    simpleRoom.copy(roomId = room2, encrypted = true)
                }
                roomStore.update(room3) {
                    simpleRoom.copy(roomId = room3, encrypted = true)
                }
                listOf(
                    // room1
                    StateEvent(
                        HistoryVisibilityEventContent(HistoryVisibility.INVITED),
                        EventId("\$event1"),
                        alice,
                        room1,
                        1234,
                        stateKey = ""
                    ),
                    StateEvent(
                        MemberEventContent(membership = Membership.INVITE),
                        EventId("\$event1"),
                        alice,
                        room1,
                        1234,
                        stateKey = alice.full
                    ),
                    StateEvent(
                        MemberEventContent(membership = Membership.LEAVE),
                        EventId("\$event4"),
                        cedric,
                        room1,
                        1234,
                        stateKey = cedric.full
                    ),
                    // room2
                    StateEvent(
                        MemberEventContent(membership = Membership.JOIN),
                        EventId("\$event2"),
                        alice,
                        room2,
                        1234,
                        stateKey = alice.full
                    ),
                    // room3
                    StateEvent(
                        MemberEventContent(membership = Membership.JOIN),
                        EventId("\$event3"),
                        alice,
                        room3,
                        1234,
                        stateKey = alice.full
                    ),
                ).forEach { roomStateStore.save(it) }

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

                keyStore.updateOutdatedKeys { setOf(cedric, alice) }
                keyStore.getOutdatedKeysFlow().first { it.isEmpty() }
                olmCryptoStore.getOutboundMegolmSession(room1)?.newDevices shouldBe mapOf(
                    alice to setOf(aliceDevice2),
                )
                olmCryptoStore.getOutboundMegolmSession(room2) should beNull() // there is no session
                olmCryptoStore.getOutboundMegolmSession(room3)?.newDevices.shouldNotBeNull().shouldContainExactly(
                    mapOf(
                        alice to setOf(aliceDevice1, aliceDevice2), // aliceDevice1 was already present
                        cedric to setOf(cedricDevice), // cedricDevice was already present
                    )
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
                    matrixJsonEndpoint(GetKeys()) {
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
                    simpleRoom.copy(roomId = room1, encrypted = true)
                }
                olmCryptoStore.updateOutboundMegolmSession(room1) { StoredOutboundMegolmSession(room1, pickled = "") }

                keyStore.updateOutdatedKeys { setOf(alice) }
                keyStore.getOutdatedKeysFlow().first { it.isEmpty() }
                olmCryptoStore.getOutboundMegolmSession(room1) shouldBe null
            }
            context("master key is present") {
                context("at least one device key is not cross signed") {
                    context("mark master key as ${KeySignatureTrustLevel.NotAllDeviceKeysCrossSigned::class.simpleName}") {
                        mapOf(
                            KeySignatureTrustLevel.CrossSigned(true) to true,
                            KeySignatureTrustLevel.CrossSigned(false) to false,
                        ).forEach { (levelBefore, expectedVerified) ->
                            should("levelBefore=$levelBefore, expectedVerified=$expectedVerified") {
                                keyTrustServiceMock.returnCalculateDeviceKeysTrustLevel =
                                    KeySignatureTrustLevel.NotCrossSigned
                                apiConfig.endpoints {
                                    matrixJsonEndpoint(GetKeys()) {
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
                                keyStore.updateOutdatedKeys { setOf(alice) }
                                keyStore.getOutdatedKeysFlow().first { it.isEmpty() }
                                keyStore.getCrossSigningKey(alice, CrossSigningKeysUsage.MasterKey)
                                    ?.trustLevel shouldBe KeySignatureTrustLevel.NotAllDeviceKeysCrossSigned(
                                    expectedVerified
                                )
                            }
                        }
                    }
                    context("keep trust level") {
                        withData(
                            KeySignatureTrustLevel.Valid(false),
                            KeySignatureTrustLevel.Valid(true),
                            KeySignatureTrustLevel.NotAllDeviceKeysCrossSigned(true),
                            KeySignatureTrustLevel.NotAllDeviceKeysCrossSigned(false),
                            KeySignatureTrustLevel.NotCrossSigned,
                            KeySignatureTrustLevel.Invalid(""),
                            KeySignatureTrustLevel.Blocked
                        ) { levelBefore ->
                            apiConfig.endpoints {
                                matrixJsonEndpoint(GetKeys()) {
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
                            keyStore.updateOutdatedKeys { setOf(alice) }
                            keyStore.getOutdatedKeysFlow().first { it.isEmpty() }
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
                            matrixJsonEndpoint(GetKeys()) {
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
                        keyStore.updateOutdatedKeys { setOf(alice) }
                        keyStore.getOutdatedKeysFlow().first { it.isEmpty() }
                        keyStore.getCrossSigningKey(alice, CrossSigningKeysUsage.MasterKey)
                            ?.trustLevel shouldBe KeySignatureTrustLevel.CrossSigned(true)
                    }
                }
            }
            context("manipulation of ") {
                should("signature") {
                    signServiceMock.returnVerify = VerifyResult.Invalid("")
                    apiConfig.endpoints {
                        matrixJsonEndpoint(GetKeys()) {
                            GetKeys.Response(
                                mapOf(), mapOf(cedric to mapOf(cedricDevice to cedricKey1)), mapOf(), mapOf(), mapOf()
                            )
                        }
                    }
                    keyStore.updateOutdatedKeys { setOf(cedric) }
                    keyStore.getOutdatedKeysFlow().first { it.isEmpty() }
                    val storedKeys = keyStore.getDeviceKeys(cedric).first()
                    assertNotNull(storedKeys)
                    storedKeys.shouldBeEmpty()
                }
                withData<Map<UserId, Map<String, Signed<DeviceKeys, UserId>>>>(
                    mapOf(
                        "userId" to mapOf(alice to mapOf(cedricDevice to cedricKey1)),
                        "deviceId" to mapOf(alice to mapOf(cedricDevice to aliceKey2)),
                    )
                ) { deviceKeys ->
                    apiConfig.endpoints {
                        matrixJsonEndpoint(GetKeys()) {
                            GetKeys.Response(
                                mapOf(), deviceKeys, mapOf(), mapOf(), mapOf()
                            )
                        }
                    }
                    keyStore.updateOutdatedKeys { setOf(alice, cedric) }
                    keyStore.getOutdatedKeysFlow().first { it.isEmpty() }
                    val storedKeys = keyStore.getDeviceKeys(alice).first()
                    assertNotNull(storedKeys)
                    storedKeys.shouldBeEmpty()
                }
            }
        }
    }
}