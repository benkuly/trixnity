package net.folivo.trixnity.client.key

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.mocks.KeyTrustServiceMock
import net.folivo.trixnity.client.mocks.SignServiceMock
import net.folivo.trixnity.client.mocks.TransactionManagerMock
import net.folivo.trixnity.client.store.KeySignatureTrustLevel
import net.folivo.trixnity.client.store.KeyStore
import net.folivo.trixnity.client.store.StoredCrossSigningKeys
import net.folivo.trixnity.client.store.StoredDeviceKeys
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.model.key.GetKeys
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
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.crypto.olm.StoredOutboundMegolmSession
import net.folivo.trixnity.crypto.sign.VerifyResult
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.milliseconds


class OutdatedKeysHandlerTest : TrixnityBaseTest() {

    private val room = RoomId("!room:server")
    private val alice = UserId("alice", "server")
    private val bob = UserId("bob", "server")
    private val us = UserId("us", "server")
    private val aliceDevice = "ALICEDEVICE"
    private val ourDevice = "OURDEVICE"
    private val aliceKeys = StoredDeviceKeys(
        Signed(DeviceKeys(alice, aliceDevice, setOf(), keysOf()), mapOf()),
        KeySignatureTrustLevel.Valid(false)
    )
    private val ourKeys = StoredDeviceKeys(
        Signed(DeviceKeys(us, ourDevice, setOf(), keysOf()), mapOf()),
        KeySignatureTrustLevel.Valid(true)
    )

    private val keyStore = getInMemoryKeyStore()
    private val olmCryptoStore = getInMemoryOlmStore()
    private val roomStore = getInMemoryRoomStore()
    private val roomStateStore = getInMemoryRoomStateStore()

    private val signServiceMock = SignServiceMock().apply {
        returnVerify = VerifyResult.Valid
    }
    private val keyTrustServiceMock = KeyTrustServiceMock().apply {
        returnCalculateCrossSigningKeysTrustLevel = KeySignatureTrustLevel.CrossSigned(false)
        returnCalculateDeviceKeysTrustLevel = KeySignatureTrustLevel.CrossSigned(false)
    }

    private val apiConfig = PortableMockEngineConfig()
    private val api = mockMatrixClientServerApiClient(apiConfig)

    private val currentSyncState = MutableStateFlow(SyncState.STOPPED)

    private val cut = OutdatedKeysHandler(
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
    ).apply {
        startInCoroutineScope(testScope.backgroundScope)
    }

    @Test
    fun `handleDeviceLists » device key is tracked » add changed devices to outdated keys`() = runTest {
        keyStore.updateOutdatedKeys { setOf(alice) }
        keyStore.updateDeviceKeys(bob) { mapOf() }
        cut.handleDeviceLists(Sync.Response.DeviceLists(changed = setOf(bob)), SyncState.RUNNING)
        keyStore.getOutdatedKeysFlow().first() shouldContainExactly setOf(alice, bob)
    }

    @Test
    fun `handleDeviceLists » device key is tracked » remove key when user left`() = runTest {
        keyStore.updateOutdatedKeys { setOf(alice, bob) }
        keyStore.updateDeviceKeys(alice) { mapOf() }
        cut.handleDeviceLists(Sync.Response.DeviceLists(left = setOf(alice)), SyncState.RUNNING)
        withContext(KeyStore.SkipOutdatedKeys) {
            keyStore.getDeviceKeys(alice).first() should beNull()
            keyStore.getOutdatedKeysFlow().first() shouldContainExactly setOf(bob)
        }
    }

    @Test
    fun `handleDeviceLists » device key is not tracked » not add changed devices to outdated keys`() =
        runTest {
            keyStore.updateOutdatedKeys { setOf(alice) }
            cut.handleDeviceLists(Sync.Response.DeviceLists(changed = setOf(bob)), SyncState.RUNNING)
            keyStore.getOutdatedKeysFlow().first() shouldContainExactly setOf(alice)
        }

    @Test
    fun `handleDeviceLists » do nothing on initial sync`() = runTest {
        keyStore.updateOutdatedKeys { setOf(alice) }
        keyStore.updateDeviceKeys(alice) { mapOf() }
        keyStore.updateDeviceKeys(bob) { mapOf() }
        cut.handleDeviceLists(
            Sync.Response.DeviceLists(changed = setOf(bob), left = setOf(alice)),
            SyncState.INITIAL_SYNC
        )
        keyStore.getOutdatedKeysFlow().first() shouldContainExactly setOf(alice)
    }

    @Test
    fun `handleDeviceLists » always handle own keys`() = runTest {
        cut.handleDeviceLists(
            Sync.Response.DeviceLists(changed = setOf(us, bob)),
            SyncState.RUNNING
        )
        keyStore.getOutdatedKeysFlow().first() shouldContainExactly setOf(us)
    }

    @Test
    fun `handleDeviceLists » always handle own keys in initial sync`() = runTest {
        cut.handleDeviceLists(
            Sync.Response.DeviceLists(changed = setOf(us, bob)),
            SyncState.INITIAL_SYNC
        )
        keyStore.getOutdatedKeysFlow().first() shouldContainExactly setOf(us)
    }

    @Test
    fun `handleDeviceLists » never remove own keys`() = runTest {
        keyStore.updateDeviceKeys(us) { mapOf() }
        cut.handleDeviceLists(
            Sync.Response.DeviceLists(left = setOf(us, bob)),
            SyncState.RUNNING
        )
        keyStore.getDeviceKeys(us).first() shouldBe mapOf()
    }

    @Test
    fun `updateDeviceKeysFromChangedMembership » ignore unencrypted rooms`() = runTest {
        roomStore.update(room) { simpleRoom.copy(roomId = room, encrypted = true) }
        val room2 = RoomId("!roo2:server")
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

    @Test
    fun `updateDeviceKeysFromChangedMembership » remove device keys on leave or ban of the last encrypted room`() =
        runTest {
            roomStore.update(room) { simpleRoom.copy(roomId = room, encrypted = true) }
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

    @Test
    fun `updateDeviceKeysFromChangedMembership » not remove device keys on leave or ban when there are more rooms`() =
        runTest {
            roomStore.update(room) { simpleRoom.copy(roomId = room, encrypted = true) }
            val otherRoom = RoomId("!otherRoom:server")
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

    @Test
    fun `updateDeviceKeysFromChangedMembership » not remove our device keys on leave or ban when there are no more rooms`() =
        runTest {
            roomStore.update(room) { simpleRoom.copy(roomId = room, encrypted = true) }
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

    @Test
    fun `updateDeviceKeysFromChangedMembership » ignore join when key already tracked`() = runTest {
        roomStore.update(room) { simpleRoom.copy(roomId = room, encrypted = true) }
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

    @Test
    fun `updateDeviceKeysFromChangedMembership » not mark keys as outdated when not members loaded or loading`() =
        runTest {
            roomStore.update(room) { simpleRoom.copy(roomId = room, encrypted = true) }
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

    @Test
    fun `updateDeviceKeysFromChangedMembership » not mark keys as outdated when join but keys are already tracked`() =
        runTest {
            roomStore.update(room) { simpleRoom.copy(roomId = room, encrypted = true) }
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

    private val cedric = UserId("cedric", "server")
    private val cedricDevice = "CEDRIC_DEVICE"
    private val cedricKey1 = Signed<DeviceKeys, UserId>(
        DeviceKeys(cedric, cedricDevice, setOf(), keysOf(Key.Ed25519Key("id", "value"))), mapOf()
    )
    private val aliceDevice1 = "ALICE_DEVICE_1"
    private val aliceDevice2 = "ALICE_DEVICE_2"
    private val aliceKey1 = Signed<DeviceKeys, UserId>(
        DeviceKeys(alice, aliceDevice1, setOf(), keysOf(Key.Ed25519Key("id", "value"))), mapOf()
    )
    private val aliceKey2 = Signed<DeviceKeys, UserId>(
        DeviceKeys(alice, aliceDevice2, setOf(), keysOf(Key.Ed25519Key("id", "value"))), mapOf()
    )

    @Test
    fun `updateOutdatedKeys » do nothing when no keys outdated`() = runTest {
        currentSyncState.value = SyncState.RUNNING
        var getKeysCalled = false
        apiConfig.endpoints {
            matrixJsonEndpoint(GetKeys) {
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

    @Test
    fun `updateOutdatedKeys » set to empty if there are no keys`() = runTest {
        currentSyncState.value = SyncState.RUNNING
        apiConfig.endpoints {
            matrixJsonEndpoint(GetKeys) {
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

    @Test
    fun `updateOutdatedKeys » master keys » allow missing signature`() = runTest {
        currentSyncState.value = SyncState.RUNNING
        val key = Signed<CrossSigningKeys, UserId>(
            CrossSigningKeys(
                alice,
                setOf(CrossSigningKeysUsage.MasterKey),
                keysOf(Key.Ed25519Key("id", "value"))
            ), mapOf()
        )
        signServiceMock.returnVerify = VerifyResult.MissingSignature("")
        apiConfig.endpoints {
            matrixJsonEndpoint(GetKeys) {
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

    @Test
    fun `updateOutdatedKeys » master keys » ignore when signatures are invalid`() = runTest {
        currentSyncState.value = SyncState.RUNNING
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
            matrixJsonEndpoint(GetKeys) {
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

    @Test
    fun `updateOutdatedKeys » master keys » add master key`() = runTest {
        currentSyncState.value = SyncState.RUNNING
        val key = Signed<CrossSigningKeys, UserId>(
            CrossSigningKeys(
                alice,
                setOf(CrossSigningKeysUsage.MasterKey),
                keysOf(Key.Ed25519Key("id", "value"))
            ), mapOf()
        )
        apiConfig.endpoints {
            matrixJsonEndpoint(GetKeys) {
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

    @Test
    fun `updateOutdatedKeys » self signing keys » ignore when signatures are invalid`() = runTest {
        currentSyncState.value = SyncState.RUNNING
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
            matrixJsonEndpoint(GetKeys) {
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

    @Test
    fun `updateOutdatedKeys » self signing keys » add self signing key`() = runTest {
        currentSyncState.value = SyncState.RUNNING
        val key = Signed<CrossSigningKeys, UserId>(
            CrossSigningKeys(
                alice,
                setOf(CrossSigningKeysUsage.SelfSigningKey),
                keysOf(Key.Ed25519Key("id", "value"))
            ), mapOf()
        )
        apiConfig.endpoints {
            matrixJsonEndpoint(GetKeys) {
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

    @Test
    fun `updateOutdatedKeys » self signing keys » replace self signing key`() = runTest {
        currentSyncState.value = SyncState.RUNNING
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
            matrixJsonEndpoint(GetKeys) {
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

    @Test
    fun `updateOutdatedKeys » user signing keys » ignore when signatures are invalid`() = runTest {
        currentSyncState.value = SyncState.RUNNING
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
            matrixJsonEndpoint(GetKeys) {
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

    @Test
    fun `updateOutdatedKeys » user signing keys » add user signing key`() = runTest {
        currentSyncState.value = SyncState.RUNNING
        val key = Signed<CrossSigningKeys, UserId>(
            CrossSigningKeys(
                alice,
                setOf(CrossSigningKeysUsage.UserSigningKey),
                keysOf(Key.Ed25519Key("id", "value"))
            ), mapOf()
        )
        apiConfig.endpoints {
            matrixJsonEndpoint(GetKeys) {
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

    @Test
    fun `updateOutdatedKeys » user signing keys » replace user signing key`() = runTest {
        currentSyncState.value = SyncState.RUNNING
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
            matrixJsonEndpoint(GetKeys) {
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

    @Test
    fun `updateOutdatedkey » device keys » update outdated device keys`() = runTest {
        currentSyncState.value = SyncState.RUNNING
        var call = 0
        apiConfig.endpoints {
            matrixJsonEndpoint(GetKeys) {
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

    @Test
    fun `updateOutdatedkey » device keys » look for encrypted room where the user participates and notify megolm sessions about new devices`() =
        runTest {
            currentSyncState.value = SyncState.RUNNING
            apiConfig.endpoints {
                matrixJsonEndpoint(GetKeys) {
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
            val room1 = RoomId("!room1:server")
            val room2 = RoomId("!room2:server")
            val room3 = RoomId("!room3:server")
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

    @Test
    fun `updateOutdatedkey » device keys » look for encrypted room where the user participates and reset megolm sessions when removed devices`() =
        runTest {
            currentSyncState.value = SyncState.RUNNING
            keyStore.updateDeviceKeys(alice) {
                mapOf(
                    aliceDevice1 to StoredDeviceKeys(aliceKey1, KeySignatureTrustLevel.Valid(false)),
                    aliceDevice2 to StoredDeviceKeys(aliceKey2, KeySignatureTrustLevel.Valid(false)),
                )
            }
            apiConfig.endpoints {
                matrixJsonEndpoint(GetKeys) {
                    GetKeys.Response(
                        mapOf(),
                        mapOf(
                            alice to mapOf(aliceDevice2 to aliceKey2)
                        ),
                        mapOf(), mapOf(), mapOf()
                    )
                }
            }
            val room1 = RoomId("!room1:server")
            roomStore.update(room1) {
                simpleRoom.copy(roomId = room1, encrypted = true)
            }
            olmCryptoStore.updateOutboundMegolmSession(room1) { StoredOutboundMegolmSession(room1, pickled = "") }

            keyStore.updateOutdatedKeys { setOf(alice) }
            keyStore.getOutdatedKeysFlow().first { it.isEmpty() }
            olmCryptoStore.getOutboundMegolmSession(room1) shouldBe null
        }

    private fun markMasterKeysAsNotAllDeviceKeysCrossSigned(
        levelBefore: KeySignatureTrustLevel,
        expectedVerified: Boolean
    ) = runTest {
        currentSyncState.value = SyncState.RUNNING
        currentSyncState.value = SyncState.RUNNING
        currentSyncState.value = SyncState.RUNNING
        keyTrustServiceMock.returnCalculateDeviceKeysTrustLevel =
            KeySignatureTrustLevel.NotCrossSigned
        apiConfig.endpoints {
            matrixJsonEndpoint(GetKeys) {
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

    @Test
    fun `updateOutdatedkey » device keys » master key is present » at least one device key is not cross signed » mark master key as NotAllDeviceKeysCrossSigned » levelBefore=CrossSigned true expectedVerified=true`() =
        markMasterKeysAsNotAllDeviceKeysCrossSigned(KeySignatureTrustLevel.CrossSigned(true), true)

    @Test
    fun `updateOutdatedkey » device keys » master key is present » at least one device key is not cross signed » mark master key as NotAllDeviceKeysCrossSigned » levelBefore=CrossSigned false expectedVerified=false`() =
        markMasterKeysAsNotAllDeviceKeysCrossSigned(KeySignatureTrustLevel.CrossSigned(false), false)

    private fun keepTrustLevel(levelBefore: KeySignatureTrustLevel) = runTest {
        currentSyncState.value = SyncState.RUNNING
        apiConfig.endpoints {
            matrixJsonEndpoint(GetKeys) {
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

    @Test
    fun `updateOutdatedkey » device keys » master key is present » at least one device key is not cross signed » keep trust level » Valid false`() =
        keepTrustLevel(
            KeySignatureTrustLevel.Valid(false),
        )

    @Test
    fun `updateOutdatedkey » device keys » master key is present » at least one device key is not cross signed » keep trust level » Valid true`() =
        keepTrustLevel(
            KeySignatureTrustLevel.Valid(true),
        )

    @Test
    fun `updateOutdatedkey » device keys » master key is present » at least one device key is not cross signed » keep trust level » NotAllDeviceKeysCrossSigned true`() =
        keepTrustLevel(
            KeySignatureTrustLevel.NotAllDeviceKeysCrossSigned(true),
        )

    @Test
    fun `updateOutdatedkey » device keys » master key is present » at least one device key is not cross signed » keep trust level » NotAllDeviceKeysCrossSigned false`() =
        keepTrustLevel(
            KeySignatureTrustLevel.NotAllDeviceKeysCrossSigned(false),
        )

    @Test
    fun `updateOutdatedkey » device keys » master key is present » at least one device key is not cross signed » keep trust level » NotCrossSigned`() =
        keepTrustLevel(
            KeySignatureTrustLevel.NotCrossSigned,
        )

    @Test
    fun `updateOutdatedkey » device keys » master key is present » at least one device key is not cross signed » keep trust level » Invalid`() =
        keepTrustLevel(
            KeySignatureTrustLevel.Invalid(""),
        )

    @Test
    fun `updateOutdatedkey » device keys » master key is present » at least one device key is not cross signed » keep trust level » Blocked`() =
        keepTrustLevel(KeySignatureTrustLevel.Blocked)

    private val aliceMasterKey = Signed<CrossSigningKeys, UserId>(
        CrossSigningKeys(
            alice, setOf(CrossSigningKeysUsage.MasterKey), keysOf(Key.Ed25519Key("ALICE_MSK", "..."))
        ),
        mapOf()
    )

    @Test
    fun `updateOutdatedkey » device keys » master key is present » all device keys are cross signed » do nothing when his trust level is CrossSigned`() =
        runTest {
            currentSyncState.value = SyncState.RUNNING
            apiConfig.endpoints {
                matrixJsonEndpoint(GetKeys) {
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
            keyStore.updateCrossSigningKeys(alice) {
                setOf(StoredCrossSigningKeys(aliceMasterKey, KeySignatureTrustLevel.CrossSigned(true)))
            }
            keyStore.updateOutdatedKeys { setOf(alice) }
            keyStore.getOutdatedKeysFlow().first { it.isEmpty() }
            keyStore.getCrossSigningKey(alice, CrossSigningKeysUsage.MasterKey)
                ?.trustLevel shouldBe KeySignatureTrustLevel.CrossSigned(true)
        }

    @Test
    fun `updateOutdatedkey » device keys » manipulation of » signature`() = runTest {
        currentSyncState.value = SyncState.RUNNING
        signServiceMock.returnVerify = VerifyResult.Invalid("")
        apiConfig.endpoints {
            matrixJsonEndpoint(GetKeys) {
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

    @Test
    fun `updateOutdatedkey » device keys » manipulation of » userId`() = runTest {
        currentSyncState.value = SyncState.RUNNING
        apiConfig.endpoints {
            matrixJsonEndpoint(GetKeys) {
                GetKeys.Response(
                    mapOf(), mapOf(alice to mapOf(cedricDevice to cedricKey1)), mapOf(), mapOf(), mapOf()
                )
            }
        }
        keyStore.updateOutdatedKeys { setOf(alice, cedric) }
        keyStore.getOutdatedKeysFlow().first { it.isEmpty() }
        val storedKeys = keyStore.getDeviceKeys(alice).first()
        assertNotNull(storedKeys)
        storedKeys.shouldBeEmpty()
    }

    @Test
    fun `updateOutdatedkey » device keys » manipulation of » deviceId`() = runTest {
        currentSyncState.value = SyncState.RUNNING
        apiConfig.endpoints {
            matrixJsonEndpoint(GetKeys) {
                GetKeys.Response(
                    mapOf(), mapOf(alice to mapOf(cedricDevice to aliceKey2)), mapOf(), mapOf(), mapOf()
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