package net.folivo.trixnity.client.key

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.getInMemoryKeyStore
import net.folivo.trixnity.client.getInMemoryRoomStateStore
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.store.KeySignatureTrustLevel
import net.folivo.trixnity.client.store.KeyStore
import net.folivo.trixnity.client.store.RoomStateStore
import net.folivo.trixnity.client.store.StoredDeviceKeys
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.keys.DeviceKeys
import net.folivo.trixnity.core.model.keys.Signed
import net.folivo.trixnity.core.model.keys.keysOf
import net.folivo.trixnity.core.serialization.createMatrixEventJson

class EncryptionEventHandlerTest : ShouldSpec(body)

private val body: ShouldSpec.() -> Unit = {
    timeout = 15_000

    val alice = UserId("alice", "server")
    val bob = UserId("bob", "server")
    val aliceDevice = "ALICEDEVICE"
    val bobDevice = "BOBDEVICE"
    lateinit var scope: CoroutineScope
    lateinit var keyStore: KeyStore
    lateinit var roomStateStore: RoomStateStore
    val json = createMatrixEventJson()

    val aliceKeys = StoredDeviceKeys(
        Signed(DeviceKeys(alice, aliceDevice, setOf(), keysOf()), mapOf()),
        KeySignatureTrustLevel.Valid(false)
    )
    val bobKeys = StoredDeviceKeys(
        Signed(DeviceKeys(bob, bobDevice, setOf(), keysOf()), mapOf()),
        KeySignatureTrustLevel.Valid(false)
    )

    lateinit var cut: KeyEncryptionEventHandler

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        keyStore = getInMemoryKeyStore(scope)
        roomStateStore = getInMemoryRoomStateStore(scope)
        cut = KeyEncryptionEventHandler(
            mockMatrixClientServerApiClient(json).first, roomStateStore, keyStore
        )
    }

    afterTest {
        scope.cancel()
    }

    context(KeyEncryptionEventHandler::updateDeviceKeysFromChangedEncryption.name) {
        should("mark users as outdated dependent on history visibility") {
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
            ).forEach { roomStateStore.save(it) }
            cut.updateDeviceKeysFromChangedEncryption(
                listOf(
                    Event.StateEvent(
                        EncryptionEventContent(),
                        EventId("\$event3"),
                        bob,
                        RoomId("room", "server"),
                        1234,
                        stateKey = ""
                    ),
                )
            )
            keyStore.getOutdatedKeysFlow().first() shouldContainExactly setOf(alice)
        }
        should("not mark joined or invited users as outdated, when keys already tracked") {
            keyStore.updateDeviceKeys(alice) { mapOf(aliceDevice to aliceKeys) }
            keyStore.updateDeviceKeys(bob) { mapOf(bobDevice to bobKeys) }
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
            ).forEach { roomStateStore.save(it) }
            cut.updateDeviceKeysFromChangedEncryption(
                listOf(
                    Event.StateEvent(
                        EncryptionEventContent(),
                        EventId("\$event3"),
                        bob,
                        RoomId("room", "server"),
                        1234,
                        stateKey = ""
                    ),
                )
            )
            keyStore.getOutdatedKeysFlow().first() shouldHaveSize 0
        }
    }
}