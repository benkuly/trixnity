package net.folivo.trixnity.client.crypto

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.*
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import net.folivo.trixnity.client.crypto.KeySignatureTrustLevel.Valid
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.simpleRoom
import net.folivo.trixnity.client.store.InMemoryStore
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.StoredDeviceKeys
import net.folivo.trixnity.client.store.StoredOutboundMegolmSession
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.keys.ClaimKeys
import net.folivo.trixnity.clientserverapi.model.keys.SetKeys
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.DecryptedOlmEvent
import net.folivo.trixnity.core.model.events.Event.StateEvent
import net.folivo.trixnity.core.model.events.Event.ToDeviceEvent
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData
import net.folivo.trixnity.core.model.events.m.RoomKeyEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.Membership.*
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.Megolm
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.Olm
import net.folivo.trixnity.core.model.keys.Key.Curve25519Key
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.olm.OlmAccount
import net.folivo.trixnity.olm.OlmOutboundGroupSession
import net.folivo.trixnity.olm.freeAfter
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint

class OlmServiceTest : ShouldSpec({
    timeout = 30_000
    val alice = UserId("alice", "server")
    val bob = UserId("bob", "server")
    val aliceDevice = "ALICEDEVICE"
    val bobDevice = "BOBDEVICE"
    val aliceKeys = StoredDeviceKeys(Signed(DeviceKeys(alice, aliceDevice, setOf(), keysOf()), mapOf()), Valid(false))
    val bobKeys = StoredDeviceKeys(Signed(DeviceKeys(bob, bobDevice, setOf(), keysOf()), mapOf()), Valid(false))

    lateinit var store: Store
    lateinit var storeScope: CoroutineScope
    lateinit var api: MatrixClientServerApiClient
    lateinit var apiConfig: PortableMockEngineConfig
    val json = createMatrixJson()
    val contentMappings = createEventContentSerializerMappings()
    lateinit var cut: OlmService

    beforeTest {
        storeScope = CoroutineScope(Dispatchers.Default)
        store = InMemoryStore(storeScope)
        store.init()
        val (newApi, newApiConfig) = mockMatrixClientServerApiClient(json)
        api = newApi
        apiConfig = newApiConfig
        cut = OlmService("", alice, aliceDevice, store, api, json)
    }

    afterTest {
        storeScope.cancel()
    }

    afterSpec {
        cut.free()
    }

    context(OlmService::handleDeviceOneTimeKeysCount.name) {
        lateinit var setKeys: MutableList<SetKeys.Request>
        beforeTest {
            setKeys = mutableListOf()
            apiConfig.endpoints {
                matrixJsonEndpoint(json, contentMappings, SetKeys()) {
                    setKeys.add(it)
                    SetKeys.Response(mapOf(KeyAlgorithm.SignedCurve25519 to 50))
                }
                matrixJsonEndpoint(json, contentMappings, SetKeys()) {
                    setKeys.add(it)
                    SetKeys.Response(mapOf(KeyAlgorithm.SignedCurve25519 to 50))
                }
            }
        }
        context("server has 49 one time keys") {
            should("create and upload new keys") {
                cut.handleDeviceOneTimeKeysCount(mapOf(KeyAlgorithm.SignedCurve25519 to 49))
                cut.handleDeviceOneTimeKeysCount(mapOf(KeyAlgorithm.SignedCurve25519 to 49))

                setKeys.size shouldBe 2
                setKeys[0].oneTimeKeys?.size shouldBe 26
                setKeys[1].oneTimeKeys?.size shouldBe 26

                setKeys[0].oneTimeKeys!! shouldNotContainAnyOf setKeys[1].oneTimeKeys!!
            }
        }
        context("server has 50 one time keys") {
            should("do nothing") {
                cut.handleDeviceOneTimeKeysCount(mapOf(KeyAlgorithm.SignedCurve25519 to 50))
                setKeys should beEmpty()
            }
        }
    }

    context(OlmService::handleOlmEncryptedRoomKeyEventContent.name) {
        context("when ${RoomKeyEventContent::class.simpleName}") {
            should("store inbound megolm session") {
                val bobStore = InMemoryStore(storeScope).apply { init() }
                val bobOlmService = OlmService("", bob, bobDevice, bobStore, api, json)
                freeAfter(
                    OlmAccount.create()
                ) { aliceAccount ->
                    aliceAccount.generateOneTimeKeys(1)
                    store.olm.storeAccount(aliceAccount, "")
                    val cutWithAccount = OlmService("", alice, aliceDevice, store, api, json)
                    store.keys.updateDeviceKeys(bob) {
                        mapOf(
                            bobDevice to StoredDeviceKeys(
                                Signed(
                                    DeviceKeys(
                                        userId = bob,
                                        deviceId = bobDevice,
                                        algorithms = setOf(Olm, Megolm),
                                        keys = Keys(
                                            keysOf(
                                                bobOlmService.getSelfSignedDeviceKeys().signed.get<Curve25519Key>()!!,
                                                bobOlmService.getSelfSignedDeviceKeys().signed.get<Ed25519Key>()!!
                                            )
                                        )
                                    ), mapOf()
                                ), Valid(true)
                            )
                        )
                    }
                    bobStore.keys.updateDeviceKeys(alice) {
                        mapOf(
                            aliceDevice to StoredDeviceKeys(
                                Signed(
                                    DeviceKeys(
                                        userId = alice,
                                        deviceId = aliceDevice,
                                        algorithms = setOf(Olm, Megolm),
                                        keys = Keys(
                                            keysOf(
                                                cutWithAccount.getSelfSignedDeviceKeys().signed.get<Curve25519Key>()!!,
                                                cutWithAccount.getSelfSignedDeviceKeys().signed.get<Ed25519Key>()!!
                                            )
                                        )
                                    ), mapOf()
                                ), Valid(true)
                            )
                        )
                    }

                    apiConfig.endpoints {
                        matrixJsonEndpoint(json, contentMappings, ClaimKeys()) {
                            it.oneTimeKeys shouldBe mapOf(alice to mapOf(aliceDevice to KeyAlgorithm.SignedCurve25519))
                            ClaimKeys.Response(
                                emptyMap(),
                                mapOf(
                                    alice to mapOf(
                                        aliceDevice to keysOf(
                                            cutWithAccount.sign.signCurve25519Key(
                                                Curve25519Key(
                                                    aliceDevice,
                                                    aliceAccount.oneTimeKeys.curve25519.values.first()
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        }
                    }

                    val outboundSession = OlmOutboundGroupSession.create()
                    val eventContent = RoomKeyEventContent(
                        RoomId("room", "server"),
                        outboundSession.sessionId,
                        outboundSession.sessionKey,
                        Megolm
                    )
                    val encryptedEvent = ToDeviceEvent(
                        bobOlmService.event.encryptOlm(
                            eventContent,
                            alice,
                            aliceDevice
                        ), bob
                    )

                    cutWithAccount.handleOlmEncryptedRoomKeyEventContent(
                        IOlmService.DecryptedOlmEventContainer(
                            encryptedEvent,
                            DecryptedOlmEvent(
                                eventContent,
                                bob,
                                keysOf(bobOlmService.getSelfSignedDeviceKeys().signed.get<Ed25519Key>()!!),
                                alice,
                                keysOf(cutWithAccount.getSelfSignedDeviceKeys().signed.get<Ed25519Key>()!!)
                            )
                        )
                    )

                    assertSoftly(
                        store.olm.getInboundMegolmSession(
                            bobOlmService.getSelfSignedDeviceKeys().signed.get()!!,
                            outboundSession.sessionId,
                            RoomId("room", "server")
                        )!!
                    ) {
                        roomId shouldBe RoomId("room", "server")
                        sessionId shouldBe outboundSession.sessionId
                        senderKey shouldBe bobOlmService.getSelfSignedDeviceKeys().signed.get()!!
                    }

                    bobOlmService.free()
                    cutWithAccount.free()
                }
            }
        }
    }
    context(OlmService::handleMemberEvents.name) {
        val room = RoomId("room", "server")
        beforeTest {
            store.room.update(room) { simpleRoom.copy(roomId = room, encryptionAlgorithm = Megolm) }
        }
        should("ignore unencrypted rooms") {
            val room2 = RoomId("roo2", "server")
            store.room.update(room2) { simpleRoom.copy(roomId = room2) }
            cut.handleMemberEvents(
                StateEvent(
                    MemberEventContent(membership = JOIN),
                    EventId("\$event"),
                    alice,
                    room2,
                    1234,
                    stateKey = alice.full
                )
            )
            store.keys.outdatedKeys.value shouldHaveSize 0
        }
        should("remove megolm session on leave or ban") {
            store.olm.updateOutboundMegolmSession(room) { StoredOutboundMegolmSession(room, pickled = "") }
            cut.handleMemberEvents(
                StateEvent(
                    MemberEventContent(membership = LEAVE),
                    EventId("\$event"),
                    alice,
                    room,
                    1234,
                    stateKey = alice.full
                )
            )
            store.olm.getOutboundMegolmSession(room) should beNull()

            store.olm.updateOutboundMegolmSession(room) { StoredOutboundMegolmSession(room, pickled = "") }
            cut.handleMemberEvents(
                StateEvent(
                    MemberEventContent(membership = BAN),
                    EventId("\$event"),
                    alice,
                    room,
                    1234,
                    stateKey = alice.full
                )
            )
            store.olm.getOutboundMegolmSession(room) should beNull()
        }
        should("remove device keys on leave or ban of the last encrypted room") {
            store.keys.updateDeviceKeys(alice) { mapOf(aliceDevice to aliceKeys) }
            cut.handleMemberEvents(
                StateEvent(
                    MemberEventContent(membership = LEAVE),
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
                StateEvent(
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
            store.room.update(otherRoom) { simpleRoom.copy(roomId = otherRoom, encryptionAlgorithm = Megolm) }
            delay(500)
            store.roomState.update(
                StateEvent(
                    MemberEventContent(membership = JOIN),
                    EventId("\$event"),
                    alice,
                    otherRoom,
                    1234,
                    stateKey = alice.full
                )
            )
            cut.handleMemberEvents(
                StateEvent(
                    MemberEventContent(membership = LEAVE),
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
                StateEvent(
                    MemberEventContent(membership = BAN),
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
                StateEvent(
                    MemberEventContent(membership = JOIN),
                    EventId("\$event"),
                    alice,
                    room,
                    1234,
                    stateKey = alice.full,
                    unsigned = UnsignedRoomEventData.UnsignedStateEventData(
                        previousContent = MemberEventContent(membership = JOIN)
                    )
                )
            )
            store.keys.outdatedKeys.value shouldHaveSize 0
        }
        should("mark keys as outdated when join or invite") {
            cut.handleMemberEvents(
                StateEvent(
                    MemberEventContent(membership = JOIN),
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
                StateEvent(
                    MemberEventContent(membership = INVITE),
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
                StateEvent(
                    MemberEventContent(membership = JOIN),
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
    context(OlmService::handleEncryptionEvents.name) {
        should("mark all joined and invited users as outdated") {
            listOf(
                StateEvent(
                    MemberEventContent(membership = JOIN),
                    EventId("\$event1"),
                    alice,
                    RoomId("room", "server"),
                    1234,
                    stateKey = alice.full
                ),
                StateEvent(
                    MemberEventContent(membership = INVITE),
                    EventId("\$event2"),
                    bob,
                    RoomId("room", "server"),
                    1234,
                    stateKey = bob.full
                ),
            ).forEach { store.roomState.update(it) }
            cut.handleEncryptionEvents(
                StateEvent(
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
                StateEvent(
                    MemberEventContent(membership = JOIN),
                    EventId("\$event1"),
                    alice,
                    RoomId("room", "server"),
                    1234,
                    stateKey = alice.full
                ),
                StateEvent(
                    MemberEventContent(membership = INVITE),
                    EventId("\$event2"),
                    bob,
                    RoomId("room", "server"),
                    1234,
                    stateKey = bob.full
                ),
            ).forEach { store.roomState.update(it) }
            cut.handleEncryptionEvents(
                StateEvent(
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
})