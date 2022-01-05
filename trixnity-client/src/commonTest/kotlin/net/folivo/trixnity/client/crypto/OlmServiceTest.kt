package net.folivo.trixnity.client.crypto

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContainAnyOf
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.shareIn
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.model.keys.ClaimKeysResponse
import net.folivo.trixnity.client.crypto.KeySignatureTrustLevel.Valid
import net.folivo.trixnity.client.simpleRoom
import net.folivo.trixnity.client.store.InMemoryStore
import net.folivo.trixnity.client.store.SecureStore
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.StoredDeviceKeys
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.crypto.*
import net.folivo.trixnity.core.model.crypto.EncryptionAlgorithm.Megolm
import net.folivo.trixnity.core.model.crypto.EncryptionAlgorithm.Olm
import net.folivo.trixnity.core.model.crypto.Key.Curve25519Key
import net.folivo.trixnity.core.model.crypto.Key.Ed25519Key
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.StateEvent
import net.folivo.trixnity.core.model.events.Event.ToDeviceEvent
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData
import net.folivo.trixnity.core.model.events.m.RoomKeyEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent.Membership.*
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.olm.OlmAccount
import net.folivo.trixnity.olm.OlmOutboundGroupSession
import net.folivo.trixnity.olm.freeAfter
import kotlin.test.assertNotNull

class OlmServiceTest : ShouldSpec({
    timeout = 30_000
    val alice = UserId("alice", "server")
    val bob = UserId("bob", "server")
    val aliceDevice = "ALICEDEVICE"
    val bobDevice = "BOBDEVICE"
    lateinit var store: Store
    lateinit var storeScope: CoroutineScope
    val secureStore = mockk<SecureStore>()
    val api = mockk<MatrixApiClient>()
    val json = createMatrixJson()
    lateinit var cut: OlmService

    beforeTest {
        storeScope = CoroutineScope(Dispatchers.Default)
        store = InMemoryStore(storeScope)
        store.init()
        coEvery { secureStore.olmPickleKey } returns ""
        cut = OlmService(alice, aliceDevice, store, secureStore, api, json)
    }

    afterTest {
        clearAllMocks()
        storeScope.cancel()
    }

    afterSpec {
        cut.free()
    }

    context(OlmService::handleDeviceOneTimeKeysCount.name) {
        beforeTest {
            coEvery {
                api.keys.uploadKeys(
                    any(),
                    any(),
                    any()
                )
            } returns Result.success(mapOf(KeyAlgorithm.SignedCurve25519 to 50))
        }
        context("server has 49 one time keys") {
            should("create and upload new keys") {
                val uploadedKeys = mutableListOf<Keys>()

                cut.handleDeviceOneTimeKeysCount(mapOf(KeyAlgorithm.SignedCurve25519 to 49))
                cut.handleDeviceOneTimeKeysCount(mapOf(KeyAlgorithm.SignedCurve25519 to 49))

                coVerify { api.keys.uploadKeys(oneTimeKeys = capture(uploadedKeys)) }
                uploadedKeys[0] shouldHaveSize 26
                uploadedKeys[1] shouldHaveSize 26

                uploadedKeys[1] shouldNotContainAnyOf uploadedKeys[0]
            }
        }
        context("server has 50 one time keys") {
            should("do nothing") {
                cut.handleDeviceOneTimeKeysCount(mapOf(KeyAlgorithm.SignedCurve25519 to 50))
                coVerify { api wasNot Called }
            }
        }
    }

    context(OlmService::handleOlmEncryptedRoomKeyEventContent.name) {
        context("when ${RoomKeyEventContent::class.simpleName}") {
            should("store inbound megolm session") {
                val bobStore = InMemoryStore(storeScope).apply { init() }
                val bobOlmService = OlmService(bob, bobDevice, bobStore, secureStore, api, json)
                freeAfter(
                    OlmAccount.create()
                ) { aliceAccount ->
                    aliceAccount.generateOneTimeKeys(1)
                    store.olm.storeAccount(aliceAccount, "")
                    val cutWithAccount = OlmService(alice, aliceDevice, store, secureStore, api, json)
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
                                                bobOlmService.ownDeviceKeys.signed.get<Curve25519Key>()!!,
                                                bobOlmService.ownDeviceKeys.signed.get<Ed25519Key>()!!
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
                                                cutWithAccount.ownDeviceKeys.signed.get<Curve25519Key>()!!,
                                                cutWithAccount.ownDeviceKeys.signed.get<Ed25519Key>()!!
                                            )
                                        )
                                    ), mapOf()
                                ), Valid(true)
                            )
                        )
                    }

                    coEvery {
                        api.keys.claimKeys(mapOf(alice to mapOf(aliceDevice to KeyAlgorithm.SignedCurve25519)))
                    } returns Result.success(
                        ClaimKeysResponse(
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
                    )

                    val outboundSession = OlmOutboundGroupSession.create()
                    val eventContent = RoomKeyEventContent(
                        RoomId("room", "server"),
                        outboundSession.sessionId,
                        outboundSession.sessionKey,
                        Megolm
                    )
                    val encryptedEvent = ToDeviceEvent(
                        bobOlmService.events.encryptOlm(
                            eventContent,
                            alice,
                            aliceDevice
                        ), bob
                    )

                    cutWithAccount.handleOlmEncryptedRoomKeyEventContent(
                        OlmService.DecryptedOlmEvent(
                            encryptedEvent,
                            Event.OlmEvent(
                                eventContent,
                                bob,
                                keysOf(bobOlmService.ownDeviceKeys.signed.get<Ed25519Key>()!!),
                                alice,
                                keysOf(cutWithAccount.ownDeviceKeys.signed.get<Ed25519Key>()!!)
                            )
                        )
                    )

                    assertSoftly(
                        store.olm.getInboundMegolmSession(
                            bobOlmService.ownDeviceKeys.signed.get()!!,
                            outboundSession.sessionId,
                            RoomId("room", "server")
                        )!!
                    ) {
                        roomId shouldBe RoomId("room", "server")
                        sessionId shouldBe outboundSession.sessionId
                        senderKey shouldBe bobOlmService.ownDeviceKeys.signed.get()!!
                    }

                    bobOlmService.free()
                    cutWithAccount.free()
                }
            }
        }
    }
    context(OlmService::handleOlmEncryptedToDeviceEvents.name) {
        should("emit decrypted events") {
            val bobStore = InMemoryStore(storeScope).apply { init() }
            val bobOlmService = OlmService(bob, bobDevice, bobStore, secureStore, api, json)
            freeAfter(
                OlmAccount.create()
            ) { aliceAccount ->
                aliceAccount.generateOneTimeKeys(1)
                store.olm.storeAccount(aliceAccount, "")
                val cutWithAccount = OlmService(alice, aliceDevice, store, secureStore, api, json)
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
                                            bobOlmService.ownDeviceKeys.signed.get<Curve25519Key>()!!,
                                            bobOlmService.ownDeviceKeys.signed.get<Ed25519Key>()!!
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
                                            cutWithAccount.ownDeviceKeys.signed.get<Curve25519Key>()!!,
                                            cutWithAccount.ownDeviceKeys.signed.get<Ed25519Key>()!!
                                        )
                                    )
                                ), mapOf()
                            ), Valid(true)
                        )
                    )
                }

                coEvery {
                    api.keys.claimKeys(mapOf(alice to mapOf(aliceDevice to KeyAlgorithm.SignedCurve25519)))
                } returns Result.success(
                    ClaimKeysResponse(
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
                )

                val outboundSession = OlmOutboundGroupSession.create()
                val eventContent = RoomKeyEventContent(
                    RoomId("room", "server"),
                    outboundSession.sessionId,
                    outboundSession.sessionKey,
                    Megolm
                )
                val encryptedEvent = ToDeviceEvent(
                    bobOlmService.events.encryptOlm(
                        eventContent,
                        alice,
                        aliceDevice
                    ), bob
                )

                val scope = CoroutineScope(Dispatchers.Default)
                val emittedEvent =
                    cutWithAccount.decryptedOlmEvents.shareIn(scope, started = SharingStarted.Eagerly, replay = 1)
                cutWithAccount.handleOlmEncryptedToDeviceEvents(encryptedEvent)

                assertSoftly(
                    emittedEvent.firstOrNull()
                ) {
                    assertNotNull(this)
                    encrypted shouldBe encryptedEvent
                    decrypted shouldBe Event.OlmEvent(
                        eventContent,
                        bob,
                        keysOf(bobOlmService.ownDeviceKeys.signed.get<Ed25519Key>()!!.copy(keyId = null)),
                        alice,
                        keysOf(cutWithAccount.ownDeviceKeys.signed.get<Ed25519Key>()!!.copy(keyId = null))
                    )
                }
                scope.cancel()

                bobOlmService.free()
                cutWithAccount.free()
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
            store.olm.updateOutboundMegolmSession(room) { mockk() }
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

            store.olm.updateOutboundMegolmSession(room) { mockk() }
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
            store.keys.updateDeviceKeys(alice) { mapOf(aliceDevice to mockk()) }
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

            store.keys.updateDeviceKeys(alice) { mapOf(aliceDevice to mockk()) }
            cut.handleMemberEvents(
                StateEvent(
                    MemberEventContent(membership = MemberEventContent.Membership.BAN),
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
            store.keys.updateDeviceKeys(alice) { mapOf(aliceDevice to mockk()) }
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

            store.keys.updateDeviceKeys(alice) { mapOf(aliceDevice to mockk()) }
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
            store.keys.updateDeviceKeys(alice) { mapOf(aliceDevice to mockk()) }
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
            store.keys.updateDeviceKeys(alice) { mapOf(aliceDevice to mockk()) }
            store.keys.updateDeviceKeys(bob) { mapOf(bobDevice to mockk()) }
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