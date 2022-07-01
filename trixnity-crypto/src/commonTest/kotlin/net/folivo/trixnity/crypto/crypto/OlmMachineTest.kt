package net.folivo.trixnity.crypto.crypto

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldNotContainAnyOf
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import net.folivo.trixnity.core.EventEmitter
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.DecryptedOlmEvent
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.RoomKeyEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.olm.*
import net.folivo.trixnity.olm.OlmAccount
import net.folivo.trixnity.olm.OlmOutboundGroupSession
import org.kodein.mock.Mocker
import org.kodein.mock.UsesMocks

@UsesMocks(OlmMachineRequestHandler::class)
class OlmMachineTest : ShouldSpec({
    timeout = 30_000

    val mocker = Mocker()

    lateinit var cut: OlmMachine

    val alice = UserId("alice", "server")
    val bob = UserId("bob", "server")
    val aliceDevice = "ALICEDEVICE"
    val bobDevice = "BOBDEVICE"
    val roomId = RoomId("room", "server")

    lateinit var mockStore: OlmMachineStoreMock
    val mockRequestHandler = MockOlmMachineRequestHandler(mocker)
    val json = createMatrixEventJson()
    val contentMappings = createEventContentSerializerMappings()

    lateinit var eventEmitter: EventEmitter
    lateinit var oneTimeKeysCountEmitter: OneTimeKeysCountEmitter
    lateinit var oneTimeKeysCountEmitterSubscriber: DeviceOneTimeKeysCountSubscriber

    lateinit var olmAccount: OlmAccount

    beforeEach {
        olmAccount = OlmAccount.create()
        mockStore = OlmMachineStoreMock()

        mockStore.olmAccount = olmAccount.pickle("")

        eventEmitter = object : EventEmitter() {
            suspend fun testEmitEvent(event: Event<*>) {
                this.emitEvent(event)
            }
        }
        oneTimeKeysCountEmitter = object : OneTimeKeysCountEmitter {
            override fun subscribeDeviceOneTimeKeysCount(subscriber: DeviceOneTimeKeysCountSubscriber) {
                oneTimeKeysCountEmitterSubscriber = subscriber
            }

            override fun unsubscribeDeviceOneTimeKeysCount(subscriber: DeviceOneTimeKeysCountSubscriber) {
                throw NotImplementedError()
            }
        }

        cut = OlmMachine.create(
            alice,
            aliceDevice,
            eventEmitter,
            oneTimeKeysCountEmitter,
            mockRequestHandler,
            SignServiceMock().apply { signCurve25519Key = Key.SignedCurve25519Key(null, "", mapOf()) },
            mockStore,
            json,
            ""
        )
    }

    afterEach {
        olmAccount.free()
        mocker.reset()
    }

    // ##########################
    // handleDeviceOneTimeKeysCount
    // ##########################
    should("create and upload new keys when server has 49 one time keys") {
        val captureOneTimeKeys = mutableListOf<Keys>()
        mocker.everySuspending { mockRequestHandler.setOneTimeKeys(isAny(capture = captureOneTimeKeys)) } returns
                Result.success(Unit)
        cut.handleDeviceOneTimeKeysCount(mapOf(KeyAlgorithm.SignedCurve25519 to 49))
        cut.handleDeviceOneTimeKeysCount(mapOf(KeyAlgorithm.SignedCurve25519 to 0))

        println(captureOneTimeKeys)
        captureOneTimeKeys.size shouldBe 2
        captureOneTimeKeys[0].keys.size shouldBe 26
        captureOneTimeKeys[1].keys.size shouldBe 75

        captureOneTimeKeys[0].keys shouldNotContainAnyOf captureOneTimeKeys[1].keys
    }
    should("do nothing when server has 50 one time keys") {
        val captureOneTimeKeys = mutableListOf<Keys>()
        mocker.everySuspending { mockRequestHandler.setOneTimeKeys(isAny(capture = captureOneTimeKeys)) }
        cut.handleDeviceOneTimeKeysCount(mapOf(KeyAlgorithm.SignedCurve25519 to 50))
        captureOneTimeKeys should beEmpty()
    }

    // ##########################
    // handleOlmEncryptedRoomKeyEventContent
    // ##########################
    should("store inbound megolm session") {
        val outboundSession = OlmOutboundGroupSession.create()

        val eventContent = RoomKeyEventContent(
            roomId,
            outboundSession.sessionId,
            outboundSession.sessionKey,
            EncryptionAlgorithm.Megolm
        )
        val encryptedEvent = Event.ToDeviceEvent(
            EncryptedEventContent.OlmEncryptedEventContent(
                ciphertext = mapOf(),
                senderKey = Key.Curve25519Key(null, "BOB_IDEN"),
            ), bob
        )

        cut.handleOlmEncryptedRoomKeyEventContent(
            DecryptedOlmEventContainer(
                encryptedEvent,
                DecryptedOlmEvent(
                    eventContent,
                    bob,
                    keysOf(Key.Ed25519Key(null, "BOB_SIGN")),
                    alice,
                    keysOf()
                )
            )
        )

        assertSoftly(
            mockStore.inboundMegolmSession[outboundSession.sessionId to roomId]
                .shouldNotBeNull()
        ) {
            roomId shouldBe roomId
            sessionId shouldBe outboundSession.sessionId
            senderKey shouldBe Key.Curve25519Key(null, "BOB_IDEN")
            senderSigningKey shouldBe Key.Ed25519Key(null, "BOB_SIGN")
        }
    }

    // ##########################
    // handleMemberEvents
    // ##########################
    should("remove megolm session on leave or ban") {
        mockStore.roomEncryptionAlgorithm[roomId] = EncryptionAlgorithm.Megolm

        mockStore.outboundMegolmSession[roomId] = StoredOutboundMegolmSession(roomId, pickled = "")
        cut.handleMemberEvents(
            Event.StateEvent(
                MemberEventContent(membership = Membership.LEAVE),
                EventId("\$event"),
                alice,
                roomId,
                1234,
                stateKey = alice.full
            )
        )
        mockStore.outboundMegolmSession[roomId] shouldBe null

        mockStore.outboundMegolmSession[roomId] = StoredOutboundMegolmSession(roomId, pickled = "")
        cut.handleMemberEvents(
            Event.StateEvent(
                MemberEventContent(membership = Membership.BAN),
                EventId("\$event"),
                alice,
                roomId,
                1234,
                stateKey = alice.full
            )
        )
        mockStore.outboundMegolmSession[roomId] shouldBe null
    }
})