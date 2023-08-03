package net.folivo.trixnity.client.key

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldNot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.mocks.KeyTrustServiceMock
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.keys.DeviceKeys
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm
import net.folivo.trixnity.core.model.keys.Signed
import net.folivo.trixnity.core.model.keys.keysOf
import net.folivo.trixnity.core.serialization.createMatrixEventJson

class MemberEventHandlerTest : ShouldSpec(body)

private val body: ShouldSpec.() -> Unit = {
    timeout = 15_000

    val alice = UserId("alice", "server")
    val aliceDevice = "ALICEDEVICE"
    lateinit var scope: CoroutineScope
    lateinit var keyStore: KeyStore
    lateinit var roomStore: RoomStore
    lateinit var roomStateStore: RoomStateStore
    lateinit var keyTrustServiceMock: KeyTrustServiceMock
    val json = createMatrixEventJson()

    val aliceKeys = StoredDeviceKeys(
        Signed(DeviceKeys(alice, aliceDevice, setOf(), keysOf()), mapOf()),
        KeySignatureTrustLevel.Valid(false)
    )

    lateinit var cut: KeyMemberEventHandler

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        keyStore = getInMemoryKeyStore(scope)
        roomStore = getInMemoryRoomStore(scope)
        roomStateStore = getInMemoryRoomStateStore(scope)
        keyTrustServiceMock = KeyTrustServiceMock()
        cut = KeyMemberEventHandler(
            mockMatrixClientServerApiClient(json).first,
            roomStore, roomStateStore, keyStore
        )
        keyTrustServiceMock.returnCalculateCrossSigningKeysTrustLevel = KeySignatureTrustLevel.CrossSigned(false)
        keyTrustServiceMock.returnCalculateDeviceKeysTrustLevel = KeySignatureTrustLevel.CrossSigned(false)
    }

    afterTest {
        scope.cancel()
    }

    context(KeyMemberEventHandler::handleMemberEvents.name) {
        val room = RoomId("room", "server")
        beforeTest {
            roomStore.update(room) { simpleRoom.copy(roomId = room, encryptionAlgorithm = EncryptionAlgorithm.Megolm) }
        }
        should("ignore unencrypted rooms") {
            val room2 = RoomId("roo2", "server")
            roomStore.update(room2) { simpleRoom.copy(roomId = room2) }
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
            keyStore.getOutdatedKeysFlow().first() shouldHaveSize 0
        }
        should("remove device keys on leave or ban of the last encrypted room") {
            keyStore.updateDeviceKeys(alice) { mapOf(aliceDevice to aliceKeys) }
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
            keyStore.getDeviceKeys(alice).first() should beNull()

            keyStore.updateDeviceKeys(alice) { mapOf(aliceDevice to aliceKeys) }
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
            keyStore.getDeviceKeys(alice).first() should beNull()
        }
        should("not remove device keys on leave or ban when there are more rooms") {
            val otherRoom = RoomId("otherRoom", "server")
            keyStore.updateDeviceKeys(alice) { mapOf(aliceDevice to aliceKeys) }
            roomStore.update(otherRoom) {
                simpleRoom.copy(
                    roomId = otherRoom,
                    encryptionAlgorithm = EncryptionAlgorithm.Megolm
                )
            }
            delay(500)
            roomStateStore.save(
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
            keyStore.getDeviceKeys(alice) shouldNot beNull()

            keyStore.updateDeviceKeys(alice) { mapOf(aliceDevice to aliceKeys) }
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
            keyStore.getDeviceKeys(alice) shouldNot beNull()
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
            keyStore.getOutdatedKeysFlow().first() shouldHaveSize 0
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
            keyStore.getOutdatedKeysFlow().first() shouldContain alice

            keyStore.updateOutdatedKeys { setOf() }

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
            keyStore.getOutdatedKeysFlow().first() shouldContain alice
        }
        should("not mark keys as outdated when join, but devices are already tracked") {
            keyStore.updateDeviceKeys(alice) { mapOf(aliceDevice to aliceKeys) }
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
            keyStore.getOutdatedKeysFlow().first() shouldHaveSize 0
        }
    }
}