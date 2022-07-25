package net.folivo.trixnity.client.key

import io.kotest.assertions.timing.continually
import io.kotest.assertions.until.fixed
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.key.KeySignatureTrustLevel.*
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.mocks.KeyBackupServiceMock
import net.folivo.trixnity.client.mocks.KeySecretServiceMock
import net.folivo.trixnity.client.mocks.KeyTrustServiceMock
import net.folivo.trixnity.client.mocks.SignServiceMock
import net.folivo.trixnity.client.simpleRoom
import net.folivo.trixnity.client.store.InMemoryStore
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.StoredCrossSigningKeys
import net.folivo.trixnity.client.store.StoredDeviceKeys
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.model.keys.GetKeys
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.model.keys.CrossSigningKeysUsage.*
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.olm.StoredOutboundMegolmSession
import net.folivo.trixnity.crypto.sign.VerifyResult
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.milliseconds

class KeyServiceTest : ShouldSpec(body)

private val body: ShouldSpec.() -> Unit = {
    timeout = 15_000

    val alice = UserId("alice", "server")
    val bob = UserId("bob", "server")
    val aliceDevice = "ALICEDEVICE"
    val bobDevice = "BOBDEVICE"
    lateinit var scope: CoroutineScope
    lateinit var store: Store
    lateinit var signServiceMock: SignServiceMock
    val json = createMatrixEventJson()
    val mappings = createEventContentSerializerMappings()
    lateinit var apiConfig: PortableMockEngineConfig
    lateinit var trust: KeyTrustServiceMock

    val aliceKeys = StoredDeviceKeys(
        Signed(DeviceKeys(alice, aliceDevice, setOf(), keysOf()), mapOf()),
        Valid(false)
    )
    val bobKeys = StoredDeviceKeys(
        Signed(DeviceKeys(bob, bobDevice, setOf(), keysOf()), mapOf()),
        Valid(false)
    )

    val currentSyncState = MutableStateFlow(SyncState.STOPPED)

    lateinit var cut: KeyService

    beforeTest {
        signServiceMock = SignServiceMock()
        scope = CoroutineScope(Dispatchers.Default)
        store = InMemoryStore(scope).apply { init() }
        trust = KeyTrustServiceMock()
        val (api, newApiConfig) = mockMatrixClientServerApiClient(json)
        apiConfig = newApiConfig
        cut = KeyService(
            alice,
            aliceDevice,
            store,
            signServiceMock,
            api,
            currentSyncState,
            KeySecretServiceMock(),
            KeyBackupServiceMock(),
            trust,
            scope,
        )
        trust.returnCalculateCrossSigningKeysTrustLevel = CrossSigned(false)
        trust.returnCalculateDeviceKeysTrustLevel = CrossSigned(false)
        signServiceMock.returnVerify = VerifyResult.Valid
    }

    afterTest {
        scope.cancel()
    }

    context(KeyService::handleMemberEvents.name) {
        val room = RoomId("room", "server")
        beforeTest {
            store.room.update(room) { simpleRoom.copy(roomId = room, encryptionAlgorithm = EncryptionAlgorithm.Megolm) }
        }
        should("ignore unencrypted rooms") {
            val room2 = RoomId("roo2", "server")
            store.room.update(room2) { simpleRoom.copy(roomId = room2) }
            cut.handleMemberEvents(
                Event.StateEvent(
                    MemberEventContent(membership = Membership.JOIN),
                    EventId("\$event"),
                    alice,
                    room2,
                    1234,
                    stateKey = alice.full
                )
            )
            store.keys.outdatedKeys.value shouldHaveSize 0
        }
        should("remove device keys on leave or ban of the last encrypted room") {
            store.keys.updateDeviceKeys(alice) { mapOf(aliceDevice to aliceKeys) }
            cut.handleMemberEvents(
                Event.StateEvent(
                    MemberEventContent(membership = Membership.LEAVE),
                    EventId("\$event"),
                    alice,
                    room,
                    1234,
                    stateKey = alice.full
                )
            )
            store.keys.getDeviceKeys(alice) should beNull()

            store.keys.updateDeviceKeys(alice) { mapOf(aliceDevice to aliceKeys) }
            cut.handleMemberEvents(
                Event.StateEvent(
                    MemberEventContent(membership = Membership.BAN),
                    EventId("\$event"),
                    alice,
                    room,
                    1234,
                    stateKey = alice.full
                )
            )
            store.keys.getDeviceKeys(alice) should beNull()
        }
        should("not remove device keys on leave or ban when there are more rooms") {
            val otherRoom = RoomId("otherRoom", "server")
            store.keys.updateDeviceKeys(alice) { mapOf(aliceDevice to aliceKeys) }
            store.room.update(otherRoom) {
                simpleRoom.copy(
                    roomId = otherRoom,
                    encryptionAlgorithm = EncryptionAlgorithm.Megolm
                )
            }
            delay(500)
            store.roomState.update(
                Event.StateEvent(
                    MemberEventContent(membership = Membership.JOIN),
                    EventId("\$event"),
                    alice,
                    otherRoom,
                    1234,
                    stateKey = alice.full
                )
            )
            cut.handleMemberEvents(
                Event.StateEvent(
                    MemberEventContent(membership = Membership.LEAVE),
                    EventId("\$event"),
                    alice,
                    room,
                    1234,
                    stateKey = alice.full
                )
            )
            store.keys.getDeviceKeys(alice) shouldNot beNull()

            store.keys.updateDeviceKeys(alice) { mapOf(aliceDevice to aliceKeys) }
            cut.handleMemberEvents(
                Event.StateEvent(
                    MemberEventContent(membership = Membership.BAN),
                    EventId("\$event"),
                    alice,
                    room,
                    1234,
                    stateKey = alice.full
                )
            )
            store.keys.getDeviceKeys(alice) shouldNot beNull()
        }
        should("ignore join without real change (already join)") {
            cut.handleMemberEvents(
                Event.StateEvent(
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
            )
            store.keys.outdatedKeys.value shouldHaveSize 0
        }
        should("mark keys as outdated when join or invite") {
            cut.handleMemberEvents(
                Event.StateEvent(
                    MemberEventContent(membership = Membership.JOIN),
                    EventId("\$event"),
                    alice,
                    room,
                    1234,
                    stateKey = alice.full,
                )
            )
            store.keys.outdatedKeys.value shouldContain alice

            store.keys.outdatedKeys.value = setOf()

            cut.handleMemberEvents(
                Event.StateEvent(
                    MemberEventContent(membership = Membership.INVITE),
                    EventId("\$event"),
                    alice,
                    room,
                    1234,
                    stateKey = alice.full,
                )
            )
            store.keys.outdatedKeys.value shouldContain alice
        }
        should("not mark keys as outdated when join, but devices are already tracked") {
            store.keys.updateDeviceKeys(alice) { mapOf(aliceDevice to aliceKeys) }
            cut.handleMemberEvents(
                Event.StateEvent(
                    MemberEventContent(membership = Membership.JOIN),
                    EventId("\$event"),
                    alice,
                    room,
                    1234,
                    stateKey = alice.full,
                )
            )
            store.keys.outdatedKeys.value shouldHaveSize 0
        }
    }
    context(KeyService::handleEncryptionEvents.name) {
        should("mark all joined and invited users as outdated") {
            listOf(
                Event.StateEvent(
                    MemberEventContent(membership = Membership.JOIN),
                    EventId("\$event1"),
                    alice,
                    RoomId("room", "server"),
                    1234,
                    stateKey = alice.full
                ),
                Event.StateEvent(
                    MemberEventContent(membership = Membership.INVITE),
                    EventId("\$event2"),
                    bob,
                    RoomId("room", "server"),
                    1234,
                    stateKey = bob.full
                ),
            ).forEach { store.roomState.update(it) }
            cut.handleEncryptionEvents(
                Event.StateEvent(
                    EncryptionEventContent(),
                    EventId("\$event3"),
                    bob,
                    RoomId("room", "server"),
                    1234,
                    stateKey = ""
                ),
            )
            store.keys.outdatedKeys.value shouldContainExactly setOf(alice, bob)
        }
        should("not mark joined or invited users as outdated, when keys already tracked") {
            store.keys.updateDeviceKeys(alice) { mapOf(aliceDevice to aliceKeys) }
            store.keys.updateDeviceKeys(bob) { mapOf(bobDevice to bobKeys) }
            listOf(
                Event.StateEvent(
                    MemberEventContent(membership = Membership.JOIN),
                    EventId("\$event1"),
                    alice,
                    RoomId("room", "server"),
                    1234,
                    stateKey = alice.full
                ),
                Event.StateEvent(
                    MemberEventContent(membership = Membership.INVITE),
                    EventId("\$event2"),
                    bob,
                    RoomId("room", "server"),
                    1234,
                    stateKey = bob.full
                ),
            ).forEach { store.roomState.update(it) }
            cut.handleEncryptionEvents(
                Event.StateEvent(
                    EncryptionEventContent(),
                    EventId("\$event3"),
                    bob,
                    RoomId("room", "server"),
                    1234,
                    stateKey = ""
                ),
            )
            store.keys.outdatedKeys.value shouldHaveSize 0
        }
    }
    context(KeyService::handleDeviceLists.name) {
        context("device key is tracked") {
            should("add changed devices to outdated keys") {
                store.keys.outdatedKeys.value = setOf(alice)
                store.keys.updateDeviceKeys(bob) { mapOf() }
                cut.handleDeviceLists(Sync.Response.DeviceLists(changed = setOf(bob)))
                store.keys.outdatedKeys.value shouldContainExactly setOf(alice, bob)
            }
            should("remove key when user left") {
                store.keys.outdatedKeys.value = setOf(alice, bob)
                store.keys.updateDeviceKeys(alice) { mapOf() }
                cut.handleDeviceLists(Sync.Response.DeviceLists(left = setOf(alice)))
                store.keys.getDeviceKeys(alice) should beNull()
                store.keys.outdatedKeys.value shouldContainExactly setOf(bob)
            }
        }
        context("device key is not tracked") {
            should("not add changed devices to outdated keys") {
                store.keys.outdatedKeys.value = setOf(alice)
                cut.handleDeviceLists(Sync.Response.DeviceLists(changed = setOf(bob)))
                store.keys.outdatedKeys.value shouldContainExactly setOf(alice)
            }
        }
    }

    context(KeyService::handleOutdatedKeys.name) {
        val cedric = UserId("cedric", "server")
        val cedricDevice = "CEDRIC_DEVICE"
        val cedricKey1 = Signed<DeviceKeys, UserId>(
            DeviceKeys(cedric, cedricDevice, setOf(), keysOf(Ed25519Key("id", "value"))), mapOf()
        )
        val aliceDevice2 = "ALICE_DEVICE_2"
        val aliceKey2 = Signed<DeviceKeys, UserId>(
            DeviceKeys(alice, aliceDevice2, setOf(), keysOf(Ed25519Key("id", "value"))), mapOf()
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
            store.keys.outdatedKeys.value = setOf()
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
            store.keys.outdatedKeys.value = setOf(alice)
            store.keys.getCrossSigningKeys(alice, this).first { it?.isEmpty() == true }
            store.keys.getDeviceKeys(alice, this).first { it?.isEmpty() == true }
        }
        context("master keys") {
            should("allow missing signature") {
                val key = Signed<CrossSigningKeys, UserId>(
                    CrossSigningKeys(alice, setOf(MasterKey), keysOf(Ed25519Key("id", "value"))), mapOf()
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
                store.keys.outdatedKeys.value = setOf(alice)
                store.keys.outdatedKeys.first { it.isEmpty() }
                store.keys.getCrossSigningKeys(alice) shouldContainExactly setOf(
                    StoredCrossSigningKeys(key, CrossSigned(false))
                )
                trust.updateTrustLevelOfKeyChainSignedByCalled.value shouldBe Pair(
                    alice, Ed25519Key("id", "value")
                )
            }
            should("ignore when signatures are invalid") {
                val invalidKey = Signed(
                    CrossSigningKeys(alice, setOf(MasterKey), keysOf(Ed25519Key("id", "value"))),
                    mapOf(alice to keysOf(Ed25519Key("invalid", "invalid")))
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
                store.keys.outdatedKeys.value = setOf(alice)
                store.keys.outdatedKeys.first { it.isEmpty() }
                store.keys.getCrossSigningKeys(alice).shouldBeEmpty()
                trust.updateTrustLevelOfKeyChainSignedByCalled.value shouldBe null
            }
            should("add master key") {
                val key = Signed<CrossSigningKeys, UserId>(
                    CrossSigningKeys(alice, setOf(MasterKey), keysOf(Ed25519Key("id", "value"))), mapOf()
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
                store.keys.outdatedKeys.value = setOf(alice)
                store.keys.outdatedKeys.first { it.isEmpty() }
                store.keys.getCrossSigningKeys(alice) shouldContainExactly setOf(
                    StoredCrossSigningKeys(key, CrossSigned(false))
                )
                trust.updateTrustLevelOfKeyChainSignedByCalled.value shouldBe Pair(
                    alice, Ed25519Key("id", "value")
                )
            }
        }
        context("self signing keys") {
            should("ignore when signatures are invalid") {
                val invalidKey = Signed(
                    CrossSigningKeys(alice, setOf(SelfSigningKey), keysOf(Ed25519Key("id", "value"))),
                    mapOf(alice to keysOf(Ed25519Key("invalid", "invalid")))
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
                store.keys.outdatedKeys.value = setOf(alice)
                store.keys.outdatedKeys.first { it.isEmpty() }
                store.keys.getCrossSigningKeys(alice).shouldBeEmpty()
                trust.updateTrustLevelOfKeyChainSignedByCalled.value shouldBe null
            }
            should("add self signing key") {
                val key = Signed<CrossSigningKeys, UserId>(
                    CrossSigningKeys(alice, setOf(SelfSigningKey), keysOf(Ed25519Key("id", "value"))), mapOf()
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
                store.keys.outdatedKeys.value = setOf(alice)
                store.keys.outdatedKeys.first { it.isEmpty() }
                store.keys.getCrossSigningKeys(alice) shouldContainExactly setOf(
                    StoredCrossSigningKeys(key, CrossSigned(false))
                )
                trust.updateTrustLevelOfKeyChainSignedByCalled.value shouldBe Pair(
                    alice, Ed25519Key("id", "value")
                )
            }
            should("replace self signing key") {
                val key = Signed<CrossSigningKeys, UserId>(
                    CrossSigningKeys(alice, setOf(SelfSigningKey), keysOf(Ed25519Key("id", "value"))), mapOf()
                )
                store.keys.updateCrossSigningKeys(alice) {
                    setOf(
                        StoredCrossSigningKeys(
                            Signed(key.signed, mapOf(alice to keysOf())),
                            Valid(true)
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
                store.keys.outdatedKeys.value = setOf(alice)
                store.keys.outdatedKeys.first { it.isEmpty() }
                store.keys.getCrossSigningKeys(alice) shouldContainExactly setOf(
                    StoredCrossSigningKeys(key, CrossSigned(false))
                )
                trust.updateTrustLevelOfKeyChainSignedByCalled.value shouldBe Pair(
                    alice, Ed25519Key("id", "value")
                )
            }
        }
        context("user signing keys") {
            should("ignore when signatures are invalid") {
                val invalidKey = Signed(
                    CrossSigningKeys(alice, setOf(UserSigningKey), keysOf(Ed25519Key("id", "value"))),
                    mapOf(alice to keysOf(Ed25519Key("invalid", "invalid")))
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
                store.keys.outdatedKeys.value = setOf(alice)
                store.keys.outdatedKeys.first { it.isEmpty() }
                store.keys.getCrossSigningKeys(alice).shouldBeEmpty()
                trust.updateTrustLevelOfKeyChainSignedByCalled.value shouldBe null
            }
            should("add user signing key") {
                val key = Signed<CrossSigningKeys, UserId>(
                    CrossSigningKeys(alice, setOf(UserSigningKey), keysOf(Ed25519Key("id", "value"))), mapOf()
                )
                apiConfig.endpoints {
                    matrixJsonEndpoint(json, mappings, GetKeys()) {
                        GetKeys.Response(
                            mapOf(), mapOf(), mapOf(), mapOf(),
                            mapOf(alice to key)
                        )
                    }
                }
                store.keys.outdatedKeys.value = setOf(alice)
                store.keys.outdatedKeys.first { it.isEmpty() }
                store.keys.getCrossSigningKeys(alice) shouldContainExactly setOf(
                    StoredCrossSigningKeys(key, CrossSigned(false))
                )
                trust.updateTrustLevelOfKeyChainSignedByCalled.value shouldBe Pair(
                    alice, Ed25519Key("id", "value")
                )
            }
            should("replace user signing key") {
                val key = Signed<CrossSigningKeys, UserId>(
                    CrossSigningKeys(alice, setOf(UserSigningKey), keysOf(Ed25519Key("id", "value"))), mapOf()
                )
                store.keys.updateCrossSigningKeys(alice) {
                    setOf(
                        StoredCrossSigningKeys(
                            Signed(key.signed, mapOf(alice to keysOf())),
                            Valid(true)
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
                store.keys.outdatedKeys.value = setOf(alice)
                store.keys.outdatedKeys.first { it.isEmpty() }
                store.keys.getCrossSigningKeys(alice) shouldContainExactly setOf(
                    StoredCrossSigningKeys(key, CrossSigned(false))
                )
                trust.updateTrustLevelOfKeyChainSignedByCalled.value shouldBe Pair(
                    alice, Ed25519Key("id", "value")
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
                store.keys.outdatedKeys.value = setOf(cedric, alice)
                store.keys.outdatedKeys.first { it.isEmpty() }
                val storedCedricKeys = store.keys.getDeviceKeys(cedric)
                assertNotNull(storedCedricKeys)
                storedCedricKeys shouldContainExactly mapOf(
                    cedricDevice to StoredDeviceKeys(
                        cedricKey1,
                        CrossSigned(false)
                    )
                )
                val storedAliceKeys = store.keys.getDeviceKeys(alice)
                assertNotNull(storedAliceKeys)
                storedAliceKeys shouldContainExactly mapOf(
                    aliceDevice2 to StoredDeviceKeys(
                        aliceKey2,
                        CrossSigned(false)
                    )
                )

                // check delete of device keys
                store.keys.outdatedKeys.value = setOf(alice)
                store.keys.outdatedKeys.first { it.isEmpty() }
                val storedAliceKeysAfterDelete = store.keys.getDeviceKeys(alice)
                assertNotNull(storedAliceKeysAfterDelete)
                storedAliceKeysAfterDelete shouldContainExactly mapOf()
            }
            should("look for encrypted room, where the user participates and notify megolm sessions about new device keys") {
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
                store.room.update(room1) {
                    simpleRoom.copy(
                        roomId = room1,
                        encryptionAlgorithm = EncryptionAlgorithm.Megolm
                    )
                }
                store.room.update(room2) {
                    simpleRoom.copy(
                        roomId = room2,
                        encryptionAlgorithm = EncryptionAlgorithm.Megolm
                    )
                }
                store.room.update(room3) {
                    simpleRoom.copy(
                        roomId = room3,
                        encryptionAlgorithm = EncryptionAlgorithm.Megolm
                    )
                }
                listOf(
                    Event.StateEvent(
                        MemberEventContent(membership = Membership.JOIN),
                        EventId("\$event1"),
                        alice,
                        room1,
                        1234,
                        stateKey = alice.full
                    ),
                    Event.StateEvent(
                        MemberEventContent(membership = Membership.JOIN),
                        EventId("\$event2"),
                        alice,
                        room2,
                        1234,
                        stateKey = alice.full
                    ),
                    Event.StateEvent(
                        MemberEventContent(membership = Membership.JOIN),
                        EventId("\$event3"),
                        alice,
                        room3,
                        1234,
                        stateKey = alice.full
                    ),
                    Event.StateEvent(
                        MemberEventContent(membership = Membership.JOIN),
                        EventId("\$event4"),
                        cedric,
                        room1,
                        1234,
                        stateKey = cedric.full
                    ),
                ).forEach { store.roomState.update(it) }

                store.olm.updateOutboundMegolmSession(room1) { StoredOutboundMegolmSession(room1, pickled = "") }
                store.olm.updateOutboundMegolmSession(room3) {
                    StoredOutboundMegolmSession(
                        room3,
                        newDevices = mapOf(cedric to setOf(cedricDevice)),
                        pickled = ""
                    )
                }

                store.keys.outdatedKeys.value = setOf(cedric, alice)
                store.keys.outdatedKeys.first { it.isEmpty() }
                store.olm.getOutboundMegolmSession(room1)?.newDevices shouldBe mapOf(
                    alice to setOf(aliceDevice2),
                    cedric to setOf(cedricDevice)
                )
                store.olm.getOutboundMegolmSession(room2) should beNull()
                store.olm.getOutboundMegolmSession(room3)?.newDevices shouldBe mapOf(
                    alice to setOf(aliceDevice2),
                    cedric to setOf(cedricDevice)
                )
            }
            context("master key is present") {
                context("at least one device key is not cross signed") {
                    context("mark master key as ${NotAllDeviceKeysCrossSigned::class.simpleName}") {
                        withData(
                            CrossSigned(true) to true,
                            CrossSigned(false) to false,
                        ) { (levelBefore, expectedVerified) ->
                            trust.returnCalculateDeviceKeysTrustLevel = NotCrossSigned
                            apiConfig.endpoints {
                                matrixJsonEndpoint(json, mappings, GetKeys()) {
                                    GetKeys.Response(
                                        mapOf(),
                                        mapOf(alice to mapOf(aliceDevice2 to aliceKey2)),
                                        mapOf(), mapOf(), mapOf()
                                    )
                                }
                            }
                            store.keys.updateCrossSigningKeys(alice) {
                                setOf(
                                    StoredCrossSigningKeys(
                                        Signed(
                                            CrossSigningKeys(
                                                alice,
                                                setOf(MasterKey),
                                                keysOf(Ed25519Key("mk_id", "mk_value"))
                                            ),
                                            mapOf()
                                        ), levelBefore
                                    )
                                )
                            }
                            store.keys.outdatedKeys.value = setOf(alice)
                            store.keys.outdatedKeys.first { it.isEmpty() }
                            store.keys.getCrossSigningKey(alice, MasterKey)
                                ?.trustLevel shouldBe NotAllDeviceKeysCrossSigned(expectedVerified)
                        }
                    }
                    context("keep trust level") {
                        withData(
                            Valid(false),
                            Valid(true),
                            NotAllDeviceKeysCrossSigned(true),
                            NotAllDeviceKeysCrossSigned(false),
                            NotCrossSigned,
                            Invalid(""),
                            Blocked
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
                            store.keys.updateCrossSigningKeys(alice) {
                                setOf(
                                    StoredCrossSigningKeys(
                                        Signed(
                                            CrossSigningKeys(
                                                alice,
                                                setOf(MasterKey),
                                                keysOf(Ed25519Key("id", "value"))
                                            ),
                                            mapOf()
                                        ), levelBefore
                                    )
                                )
                            }
                            store.keys.outdatedKeys.value = setOf(alice)
                            store.keys.outdatedKeys.first { it.isEmpty() }
                            store.keys.getCrossSigningKey(alice, MasterKey)
                                ?.trustLevel shouldBe levelBefore
                        }
                    }
                }
                context("all device keys are cross signed") {
                    val aliceMasterKey = Signed<CrossSigningKeys, UserId>(
                        CrossSigningKeys(
                            alice, setOf(MasterKey), keysOf(Ed25519Key("ALICE_MSK", "..."))
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
                                                mapOf(alice to keysOf(Ed25519Key("ALICE_MSK", "...")))
                                            )
                                        )
                                    ),
                                    mapOf(), mapOf(), mapOf()
                                )
                            }
                        }
                    }
                    should("do nothing when his trust level is ${CrossSigned::class.simpleName}") {
                        store.keys.updateCrossSigningKeys(alice) {
                            setOf(StoredCrossSigningKeys(aliceMasterKey, CrossSigned(true)))
                        }
                        store.keys.outdatedKeys.value = setOf(alice)
                        store.keys.outdatedKeys.first { it.isEmpty() }
                        store.keys.getCrossSigningKey(alice, MasterKey)
                            ?.trustLevel shouldBe CrossSigned(true)
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
                    store.keys.outdatedKeys.value = setOf(cedric)
                    store.keys.outdatedKeys.first { it.isEmpty() }
                    val storedKeys = store.keys.getDeviceKeys(cedric)
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
                    store.keys.outdatedKeys.value = setOf(alice, cedric)
                    store.keys.outdatedKeys.first { it.isEmpty() }
                    val storedKeys = store.keys.getDeviceKeys(alice)
                    assertNotNull(storedKeys)
                    storedKeys shouldHaveSize 0
                }
            }
        }
    }
}