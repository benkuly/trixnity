package net.folivo.trixnity.client.crypto

import io.kotest.assertions.assertSoftly
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContainAnyOf
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.mockk.*
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.keys.ClaimKeysResponse
import net.folivo.trixnity.client.api.keys.QueryKeysResponse
import net.folivo.trixnity.client.api.sync.SyncResponse
import net.folivo.trixnity.client.room.RoomManager
import net.folivo.trixnity.client.simpleRoom
import net.folivo.trixnity.client.store.InMemoryStore
import net.folivo.trixnity.client.store.StoredOutboundMegolmSession
import net.folivo.trixnity.core.model.MatrixId
import net.folivo.trixnity.core.model.MatrixId.RoomId
import net.folivo.trixnity.core.model.MatrixId.UserId
import net.folivo.trixnity.core.model.crypto.*
import net.folivo.trixnity.core.model.crypto.EncryptionAlgorithm.Megolm
import net.folivo.trixnity.core.model.crypto.EncryptionAlgorithm.Olm
import net.folivo.trixnity.core.model.crypto.Key.Curve25519Key
import net.folivo.trixnity.core.model.crypto.Key.Ed25519Key
import net.folivo.trixnity.core.model.events.Event.StateEvent
import net.folivo.trixnity.core.model.events.Event.ToDeviceEvent
import net.folivo.trixnity.core.model.events.m.RoomKeyEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent.Membership.*
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.olm.OlmAccount
import net.folivo.trixnity.olm.OlmOutboundGroupSession
import net.folivo.trixnity.olm.OlmUtility
import net.folivo.trixnity.olm.freeAfter
import org.kodein.log.LoggerFactory
import kotlin.test.assertNotNull

@OptIn(ExperimentalKotest::class)
class OlmManagerTest : ShouldSpec({
    val alice = UserId("alice", "server")
    val bob = UserId("bob", "server")
    val aliceDevice = "ALICEDEVICE"
    val bobDevice = "BOBDEVICE"
    val store = InMemoryStore()
    val api = mockk<MatrixApiClient>()
    val roomManager = mockk<RoomManager>()
    val json = createMatrixJson()
    store.account.userId.value = alice
    store.account.deviceId.value = aliceDevice
    val cut = OlmManager(store, api, json, LoggerFactory.default)

    beforeTest {
        store.account.userId.value = alice
        store.account.deviceId.value = aliceDevice
    }

    afterTest {
        clearMocks(api, roomManager)
        store.clear()
    }

    afterSpec {
        cut.free()
    }

    context(OlmManager::handleDeviceOneTimeKeysCount.name) {
        beforeTest {
            coEvery { api.keys.uploadKeys(any(), any(), any()) } returns mapOf(KeyAlgorithm.SignedCurve25519 to 50)
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

    context(OlmManager::handleDeviceLists.name) {
        context("device key is tracked") {
            should("add changed devices to outdated keys") {
                store.deviceKeys.outdatedKeys.value = setOf(alice)
                store.deviceKeys.byUserId(bob).value = mapOf(bobDevice to mockk())
                cut.handleDeviceLists(SyncResponse.DeviceLists(changed = setOf(bob)))
                store.deviceKeys.outdatedKeys.value shouldContainExactly setOf(alice, bob)
            }
            should("remove key when user left") {
                store.deviceKeys.outdatedKeys.value = setOf(alice, bob)
                store.deviceKeys.byUserId(alice).value = mockk()
                cut.handleDeviceLists(SyncResponse.DeviceLists(left = setOf(alice)))
                store.deviceKeys.byUserId(alice).value should beNull()
                store.deviceKeys.outdatedKeys.value shouldContainExactly setOf(bob)
            }
        }
        context("device key is not tracked") {
            should("not add add changed devices to outdated keys") {
                store.deviceKeys.outdatedKeys.value = setOf(alice)
                cut.handleDeviceLists(SyncResponse.DeviceLists(changed = setOf(bob)))
                store.deviceKeys.outdatedKeys.value shouldContainExactly setOf(alice)
            }
        }
    }

    context(OlmManager::handleOutdatedKeys.name) {
        val cedric = UserId("cedric", "server")
        val cedricDevice = "CEDRICDEVICE"
        val aliceDevice2 = "ALICEDEVICE2"
        val (cedricKey1, cedricKey2) = freeAfter(
            OlmAccount.create(),
            OlmAccount.create(),
            OlmUtility.create()
        ) { cedricAccount1, cedricAccount2, utility ->
            val cedricStore = InMemoryStore()
            cedricStore.account.userId.value = cedric
            cedricStore.account.deviceId.value = cedricDevice
            val cedricSignService = OlmSignService(json, cedricStore, cedricAccount1, utility)
            val cedricSignService2 = OlmSignService(json, cedricStore, cedricAccount2, utility)
            cedricSignService.sign(
                DeviceKeys(
                    cedric,
                    cedricDevice,
                    setOf(Olm, Megolm),
                    keysOf(
                        cedricSignService.signCurve25519Key(
                            Curve25519Key(cedricDevice, cedricAccount1.identityKeys.curve25519)
                        ),
                        Ed25519Key(cedricDevice, cedricAccount1.identityKeys.ed25519)
                    )
                )
            ) to cedricSignService2.sign(
                DeviceKeys(
                    cedric,
                    cedricDevice,
                    setOf(Olm, Megolm),
                    keysOf(
                        cedricSignService.signCurve25519Key(
                            Curve25519Key(cedricDevice, cedricAccount2.identityKeys.curve25519)
                        ),
                        Ed25519Key(cedricDevice, cedricAccount2.identityKeys.ed25519)
                    )
                )
            )
        }
        val aliceKey2 = freeAfter(
            OlmAccount.create(),
            OlmUtility.create()
        ) { aliceAccount2, utility ->
            val aliceStore2 = InMemoryStore()
            aliceStore2.account.userId.value = alice
            aliceStore2.account.deviceId.value = aliceDevice2
            val aliceSignService2 = OlmSignService(json, aliceStore2, aliceAccount2, utility)
            aliceSignService2.sign(
                DeviceKeys(
                    alice,
                    aliceDevice2,
                    setOf(Olm, Megolm),
                    keysOf(
                        aliceSignService2.signCurve25519Key(
                            Curve25519Key(aliceDevice2, aliceAccount2.identityKeys.curve25519)
                        ),
                        Ed25519Key(aliceDevice2, aliceAccount2.identityKeys.ed25519)
                    )
                )
            )
        }
        should("do nothing when no keys outdated") {
            cut.handleOutdatedKeys(setOf())
            verify { api wasNot Called }
        }
        should("update outdated keys") {
            coEvery { api.keys.getKeys(any(), any(), any(), any()) } returns QueryKeysResponse(
                mapOf(),
                mapOf(cedric to mapOf(cedricDevice to cedricKey1), alice to mapOf(aliceDevice2 to aliceKey2))
            )
            cut.handleOutdatedKeys(setOf(cedric, alice))
            val storedCedricKeys = store.deviceKeys.byUserId(cedric).value
            assertNotNull(storedCedricKeys)
            storedCedricKeys shouldContainExactly mapOf(cedricDevice to cedricKey1.signed)
            val storedAliceKeys = store.deviceKeys.byUserId(alice).value
            assertNotNull(storedAliceKeys)
            storedAliceKeys shouldContainExactly mapOf(aliceDevice2 to aliceKey2.signed)
        }
        should("ensure, that Ed25519 key did not change") {
            coEvery { api.keys.getKeys(any(), any(), any(), any()) } returns QueryKeysResponse(
                mapOf(),
                mapOf(cedric to mapOf(cedricDevice to cedricKey2))
            )
            store.deviceKeys.byUserId(cedric).value = mapOf(cedricDevice to cedricKey1.signed)
            cut.handleOutdatedKeys(setOf(cedric))
            val storedKeys = store.deviceKeys.byUserId(cedric).value
            assertNotNull(storedKeys)
            storedKeys shouldContainExactly mapOf(cedricDevice to cedricKey1.signed)
        }
        should("look for encrypted room, where the user participates and notify megolm sessions about new device keys") {
            coEvery { api.keys.getKeys(any(), any(), any(), any()) } returns QueryKeysResponse(
                mapOf(),
                mapOf(cedric to mapOf(cedricDevice to cedricKey1), alice to mapOf(aliceDevice2 to aliceKey2))
            )
            val room1 = RoomId("room1", "server")
            val room2 = RoomId("room2", "server")
            val room3 = RoomId("room3", "server")
            store.rooms.update(room1) { simpleRoom.copy(roomId = room1, encryptionAlgorithm = Megolm) }
            store.rooms.update(room2) { simpleRoom.copy(roomId = room2, encryptionAlgorithm = Megolm) }
            store.rooms.update(room3) { simpleRoom.copy(roomId = room3, encryptionAlgorithm = Megolm) }
            store.rooms.state.updateAll(
                listOf(
                    StateEvent(
                        MemberEventContent(membership = JOIN),
                        MatrixId.EventId("\$event1"),
                        alice,
                        room1,
                        1234,
                        stateKey = alice.full
                    ),
                    StateEvent(
                        MemberEventContent(membership = JOIN),
                        MatrixId.EventId("\$event2"),
                        alice,
                        room2,
                        1234,
                        stateKey = alice.full
                    ),
                    StateEvent(
                        MemberEventContent(membership = JOIN),
                        MatrixId.EventId("\$event3"),
                        alice,
                        room3,
                        1234,
                        stateKey = alice.full
                    ),
                    StateEvent(
                        MemberEventContent(membership = JOIN),
                        MatrixId.EventId("\$event4"),
                        cedric,
                        room1,
                        1234,
                        stateKey = cedric.full
                    ),
                )
            )

            store.olm.outboundMegolmSession(room1).value = StoredOutboundMegolmSession(room1, pickle = "")
            store.olm.outboundMegolmSession(room3).value =
                StoredOutboundMegolmSession(room3, newDevices = mapOf(cedric to setOf(cedricDevice)), pickle = "")

            cut.handleOutdatedKeys(setOf(cedric, alice))

            store.olm.outboundMegolmSession(room1).value!!.newDevices shouldBe mapOf(
                alice to setOf(aliceDevice2),
                cedric to setOf(cedricDevice)
            )
            store.olm.outboundMegolmSession(room2).value should beNull()
            store.olm.outboundMegolmSession(room3).value!!.newDevices shouldBe mapOf(
                alice to setOf(aliceDevice2),
                cedric to setOf(cedricDevice)
            )
        }
        context("manipulation of ") {
            withData<Map<UserId, Map<String, Signed<DeviceKeys, UserId>>>>(
                mapOf(
                    "userId" to mapOf(alice to mapOf(cedricDevice to cedricKey1)),
                    "deviceId" to mapOf(alice to mapOf(cedricDevice to aliceKey2)),
                    "signature" to mapOf(
                        alice to mapOf(
                            aliceDevice2 to Signed(
                                aliceKey2.signed.copy(userId = cedric),
                                aliceKey2.signatures
                            )
                        )
                    )
                )
            ) { deviceKeys ->
                coEvery { api.keys.getKeys(any(), any(), any(), any()) } returns QueryKeysResponse(mapOf(), deviceKeys)
                cut.handleOutdatedKeys(setOf(alice, cedric))
                val storedKeys = store.deviceKeys.byUserId(alice).value
                assertNotNull(storedKeys)
                storedKeys shouldHaveSize 0
            }
        }
    }

    context(OlmManager::handleOlmEncryptedToDeviceEvents.name) {
        context("handle ${RoomKeyEventContent::class.simpleName}") {
            should("store inbound megolm session") {
                val bobStore = InMemoryStore()
                bobStore.account.userId.value = bob
                bobStore.account.deviceId.value = bobDevice
                val bobOlmManager = OlmManager(bobStore, api, json, LoggerFactory.default)
                freeAfter(
                    OlmAccount.create()
                ) { aliceAccount ->
                    aliceAccount.generateOneTimeKeys(1)
                    store.olm.storeAccount(aliceAccount)
                    val cutWithAccount = OlmManager(store, api, json, LoggerFactory.default)
                    store.deviceKeys.byUserId(bob).value = mapOf(
                        bobDevice to DeviceKeys(
                            userId = bob,
                            deviceId = bobDevice,
                            algorithms = setOf(Olm, Megolm),
                            keys = Keys(
                                keysOf(
                                    bobOlmManager.myDeviceKeys.signed.get<Curve25519Key>()!!,
                                    bobOlmManager.myDeviceKeys.signed.get<Ed25519Key>()!!
                                )
                            )
                        )
                    )
                    bobStore.deviceKeys.byUserId(alice).value = mapOf(
                        aliceDevice to DeviceKeys(
                            userId = alice,
                            deviceId = aliceDevice,
                            algorithms = setOf(Olm, Megolm),
                            keys = Keys(
                                keysOf(
                                    cutWithAccount.myDeviceKeys.signed.get<Curve25519Key>()!!,
                                    cutWithAccount.myDeviceKeys.signed.get<Ed25519Key>()!!
                                )
                            )
                        )
                    )

                    coEvery {
                        api.keys.claimKeys(mapOf(alice to mapOf(aliceDevice to KeyAlgorithm.SignedCurve25519)))
                    } returns ClaimKeysResponse(
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

                    val outboundSession = OlmOutboundGroupSession.create()
                    val eventContent = bobOlmManager.events.encryptOlm(
                        RoomKeyEventContent(
                            RoomId("room", "server"),
                            outboundSession.sessionId,
                            outboundSession.sessionKey,
                            Megolm
                        ),
                        alice,
                        aliceDevice
                    )

                    cutWithAccount.handleOlmEncryptedToDeviceEvents(ToDeviceEvent(eventContent, bob))

                    assertSoftly(
                        store.olm.inboundMegolmSession(
                            RoomId("room", "server"),
                            outboundSession.sessionId,
                            bobOlmManager.myDeviceKeys.signed.get()!!
                        ).value!!
                    ) {
                        roomId shouldBe RoomId("room", "server")
                        sessionId shouldBe outboundSession.sessionId
                        senderKey shouldBe bobOlmManager.myDeviceKeys.signed.get()
                    }

                    bobOlmManager.free()
                    cutWithAccount.free()
                }
            }
        }
    }
    context(OlmManager::handleMemberEvents.name) {
        val room = RoomId("room", "server")
        beforeTest {
            store.rooms.update(room) { simpleRoom.copy(roomId = room, encryptionAlgorithm = Megolm) }
        }
        should("ignore unencrypted rooms") {
            val room2 = RoomId("roo2", "server")
            store.rooms.update(room2) { simpleRoom.copy(roomId = room2) }
            cut.handleMemberEvents(
                StateEvent(
                    MemberEventContent(membership = JOIN),
                    MatrixId.EventId("\$event"),
                    alice,
                    room2,
                    1234,
                    stateKey = alice.full
                )
            )
            store.deviceKeys.outdatedKeys.value shouldHaveSize 0
        }
        should("remove megolm session on leave or ban") {
            store.olm.outboundMegolmSession(room).value = mockk()
            cut.handleMemberEvents(
                StateEvent(
                    MemberEventContent(membership = LEAVE),
                    MatrixId.EventId("\$event"),
                    alice,
                    room,
                    1234,
                    stateKey = alice.full
                )
            )
            store.olm.outboundMegolmSession(room).value should beNull()

            store.olm.outboundMegolmSession(room).value = mockk()
            cut.handleMemberEvents(
                StateEvent(
                    MemberEventContent(membership = MemberEventContent.Membership.BAN),
                    MatrixId.EventId("\$event"),
                    alice,
                    room,
                    1234,
                    stateKey = alice.full
                )
            )
            store.olm.outboundMegolmSession(room).value should beNull()
        }
        should("remove device keys on leave or ban of the last encrypted room") {
            store.deviceKeys.byUserId(alice).value = mapOf(aliceDevice to mockk())
            cut.handleMemberEvents(
                StateEvent(
                    MemberEventContent(membership = LEAVE),
                    MatrixId.EventId("\$event"),
                    alice,
                    room,
                    1234,
                    stateKey = alice.full
                )
            )
            store.deviceKeys.byUserId(alice).value should beNull()

            store.deviceKeys.byUserId(alice).value = mapOf(aliceDevice to mockk())
            cut.handleMemberEvents(
                StateEvent(
                    MemberEventContent(membership = MemberEventContent.Membership.BAN),
                    MatrixId.EventId("\$event"),
                    alice,
                    room,
                    1234,
                    stateKey = alice.full
                )
            )
            store.deviceKeys.byUserId(alice).value should beNull()
        }
        should("not remove device keys on leave or ban when there are more rooms") {
            val otherRoom = RoomId("otherRoom", "server")
            store.deviceKeys.byUserId(alice).value = mapOf(aliceDevice to mockk())
            store.rooms.update(otherRoom) { simpleRoom.copy(roomId = otherRoom, encryptionAlgorithm = Megolm) }
            store.rooms.state.update(
                StateEvent(
                    MemberEventContent(membership = JOIN),
                    MatrixId.EventId("\$event"),
                    alice,
                    otherRoom,
                    1234,
                    stateKey = alice.full
                )
            )
            cut.handleMemberEvents(
                StateEvent(
                    MemberEventContent(membership = LEAVE),
                    MatrixId.EventId("\$event"),
                    alice,
                    room,
                    1234,
                    stateKey = alice.full
                )
            )
            store.deviceKeys.byUserId(alice).value shouldNot beNull()

            store.deviceKeys.byUserId(alice).value = mapOf(aliceDevice to mockk())
            cut.handleMemberEvents(
                StateEvent(
                    MemberEventContent(membership = MemberEventContent.Membership.BAN),
                    MatrixId.EventId("\$event"),
                    alice,
                    room,
                    1234,
                    stateKey = alice.full
                )
            )
            store.deviceKeys.byUserId(alice).value shouldNot beNull()
        }
        should("ignore join without real change (already join)") {
            cut.handleMemberEvents(
                StateEvent(
                    MemberEventContent(membership = JOIN),
                    MatrixId.EventId("\$event"),
                    alice,
                    room,
                    1234,
                    stateKey = alice.full,
                    previousContent = MemberEventContent(membership = JOIN)
                )
            )
            store.deviceKeys.outdatedKeys.value shouldHaveSize 0
        }
        should("mark keys as outdated when join or invite") {
            cut.handleMemberEvents(
                StateEvent(
                    MemberEventContent(membership = JOIN),
                    MatrixId.EventId("\$event"),
                    alice,
                    room,
                    1234,
                    stateKey = alice.full,
                )
            )
            store.deviceKeys.outdatedKeys.value shouldContain alice

            store.deviceKeys.outdatedKeys.value = setOf()

            cut.handleMemberEvents(
                StateEvent(
                    MemberEventContent(membership = INVITE),
                    MatrixId.EventId("\$event"),
                    alice,
                    room,
                    1234,
                    stateKey = alice.full,
                )
            )
            store.deviceKeys.outdatedKeys.value shouldContain alice
        }
        should("not mark keys as outdated when join, but devices are already tracked") {
            store.deviceKeys.byUserId(alice).value = mapOf(aliceDevice to mockk())
            cut.handleMemberEvents(
                StateEvent(
                    MemberEventContent(membership = JOIN),
                    MatrixId.EventId("\$event"),
                    alice,
                    room,
                    1234,
                    stateKey = alice.full,
                )
            )
            store.deviceKeys.outdatedKeys.value shouldHaveSize 0
        }
    }
    context(OlmManager::handleEncryptionEvents.name) {
        should("mark all joined and invited users as outdated") {
            store.rooms.state.updateAll(
                listOf(
                    StateEvent(
                        MemberEventContent(membership = JOIN),
                        MatrixId.EventId("\$event1"),
                        alice,
                        RoomId("room", "server"),
                        1234,
                        stateKey = alice.full
                    ),
                    StateEvent(
                        MemberEventContent(membership = INVITE),
                        MatrixId.EventId("\$event2"),
                        bob,
                        RoomId("room", "server"),
                        1234,
                        stateKey = bob.full
                    ),
                )
            )
            cut.handleEncryptionEvents(
                StateEvent(
                    EncryptionEventContent(),
                    MatrixId.EventId("\$event3"),
                    bob,
                    RoomId("room", "server"),
                    1234,
                    stateKey = ""
                ),
            )
            store.deviceKeys.outdatedKeys.value shouldContainExactly setOf(alice, bob)
        }
        should("not mark joined or invited users as outdated, when keys already tracked") {
            store.deviceKeys.byUserId(alice).value = mapOf(aliceDevice to mockk())
            store.deviceKeys.byUserId(bob).value = mapOf(bobDevice to mockk())
            store.rooms.state.updateAll(
                listOf(
                    StateEvent(
                        MemberEventContent(membership = JOIN),
                        MatrixId.EventId("\$event1"),
                        alice,
                        RoomId("room", "server"),
                        1234,
                        stateKey = alice.full
                    ),
                    StateEvent(
                        MemberEventContent(membership = INVITE),
                        MatrixId.EventId("\$event2"),
                        bob,
                        RoomId("room", "server"),
                        1234,
                        stateKey = bob.full
                    ),
                )
            )
            cut.handleEncryptionEvents(
                StateEvent(
                    EncryptionEventContent(),
                    MatrixId.EventId("\$event3"),
                    bob,
                    RoomId("room", "server"),
                    1234,
                    stateKey = ""
                ),
            )
            store.deviceKeys.outdatedKeys.value shouldHaveSize 0
        }
    }
})