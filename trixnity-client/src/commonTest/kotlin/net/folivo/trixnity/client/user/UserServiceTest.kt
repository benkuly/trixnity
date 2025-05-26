package net.folivo.trixnity.client.user

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Instant
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.EventType
import net.folivo.trixnity.core.model.events.RedactedEventContent
import net.folivo.trixnity.core.model.events.m.Presence
import net.folivo.trixnity.core.model.events.m.room.*
import net.folivo.trixnity.core.model.events.m.room.Membership.JOIN
import net.folivo.trixnity.core.model.events.m.room.Membership.LEAVE
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.test.utils.scheduleSetup
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class UserServiceTest : TrixnityBaseTest() {

    private val alice = UserId("alice", "server")
    private val me = UserId("me", "server")
    private val roomId = simpleRoom.roomId
    private val clock = ClockMock()

    private val eventByUser = TimelineEvent(
        event = MessageEvent(
            content = RoomMessageEventContent.TextBased.Text(body = "Hi"),
            id = EventId("4711"),
            sender = me,
            roomId = roomId,
            originTimestamp = 0L,
        ),
        previousEventId = null,
        nextEventId = null,
        gap = null,
    )

    private val eventByOtherUser = TimelineEvent(
        event = MessageEvent(
            content = RoomMessageEventContent.TextBased.Text(body = "Hi"),
            id = EventId("4711"),
            sender = UserId("otherUser"),
            roomId = roomId,
            originTimestamp = 0L,
        ),
        previousEventId = null,
        nextEventId = null,
        gap = null,
    )

    private val powerLevelsEvent = getPowerLevelsEvent(
        PowerLevelsEventContent(
            users = mapOf(
                me to 55,
                alice to 55
            ),
            events = mapOf(EventType(PowerLevelsEventContent::class, "m.room.power_levels") to 54),
            stateDefault = 54
        )
    )

    private val aliceRoomUserBanned = RoomUser(
        roomId, alice, "Alice", StateEvent(
            MemberEventContent(membership = Membership.BAN),
            EventId(""),
            alice,
            roomId,
            0L,
            stateKey = ""
        )
    )

    private val aliceRoomUser = RoomUser(
        roomId, alice, "Alice", StateEvent(
            MemberEventContent(membership = LEAVE),
            EventId(""),
            alice,
            roomId,
            0L,
            stateKey = ""
        )
    )

    private val roomUserStore = getInMemoryRoomUserStore()
    private val globalAccountDataStore = getInMemoryGlobalAccountDataStore()
    private val userPresenceStore = getInMemoryUserPresenceStore()
    private val roomTimelineStore = getInMemoryRoomTimelineStore()
    private val roomStore = getInMemoryRoomStore { update(roomId) { Room(roomId, membership = JOIN) } }

    private val roomStateStore = getInMemoryRoomStateStore {
        save(getPowerLevelsEvent(PowerLevelsEventContent()))
        save(getCreateEvent(UserId("creator", "server")))
    }

    private val currentSyncState = MutableStateFlow(SyncState.RUNNING).also {
        scheduleSetup {
            it.value = SyncState.RUNNING
        }
    }

    private val cut = UserServiceImpl(
        roomStore = roomStore,
        roomUserStore = roomUserStore,
        roomStateStore = roomStateStore,
        roomTimelineStore = roomTimelineStore,
        globalAccountDataStore = globalAccountDataStore,
        userPresenceStore = userPresenceStore,
        loadMembersService = { _, _ -> },
        userInfo = UserInfo(
            me, "IAmADeviceId", signingPublicKey = Key.Ed25519Key(null, ""),
            Key.Curve25519Key(null, "")
        ),
        currentSyncState = CurrentSyncState(currentSyncState),
        clock = clock,
        mappings = DefaultEventContentSerializerMappings,
        config = MatrixClientConfiguration(),
    )

    data class KickLevels(
        val kickLevel: Long,
        val myLevel: Long,
        val banLevel: Long,
    )

    private val myLevelGtKickLevelGtBanLevel = KickLevels(
        kickLevel = 60L,
        myLevel = 61L,
        banLevel = 60L,
    )

    private val myLevelGtKickLevelEqBanLevel = KickLevels(
        kickLevel = 60L,
        myLevel = 61L,
        banLevel = 61L
    )

    private val myLevelGtKickLevelLtBanLevel = KickLevels(
        kickLevel = 60L,
        myLevel = 61L,
        banLevel = 62L
    )

    private val myLevelEqKickLevelGtBanLevel = KickLevels(
        kickLevel = 61L,
        myLevel = 61L,
        banLevel = 60L,
    )

    private val myLevelEqKickLevelEqBanLevel = KickLevels(
        kickLevel = 61L,
        myLevel = 61L,
        banLevel = 61L,
    )

    private val myLevelEqKickLevelLtBanLevel = KickLevels(
        kickLevel = 61L,
        myLevel = 61L,
        banLevel = 62L,
    )

    private val myLevelLtKickLevelGtBanLevel = KickLevels(
        kickLevel = 62L,
        myLevel = 61L,
        banLevel = 60L,
    )

    private val myLevelLtKickLevelEqBanLevel = KickLevels(
        kickLevel = 62L,
        myLevel = 61L,
        banLevel = 61L,
    )

    private val myLevelLtKickLevelLtBanLevel = KickLevels(
        kickLevel = 62L,
        myLevel = 61L,
        banLevel = 62L,
    )

    @Test
    fun `getPowerLevel » the room contains a power_level event » return the value in the user_id list when I am in the user_id list`() =
        runTest {
            roomStateStore.save(getCreateEvent(me))
            val powerLevelsEvent = getPowerLevelsEvent(
                PowerLevelsEventContent(
                    users = mapOf(
                        me to 60L,
                        alice to 50
                    )
                )
            )
            roomStateStore.save(powerLevelsEvent)
            cut.getPowerLevel(roomId, me).first { it != 0L } shouldBe 60L
        }

    @Test
    fun `getPowerLevel » the room contains a power_level event » return the usersDefault value when I am not in the user_id list`() =
        runTest {
            roomStateStore.save(getCreateEvent(me))
            val powerLevelsEvent = getPowerLevelsEvent(
                PowerLevelsEventContent(
                    users = mapOf(alice to 50),
                    usersDefault = 40
                )
            )
            roomStateStore.save(powerLevelsEvent)
            cut.getPowerLevel(roomId, me).first { it != 0L } shouldBe 40
        }

    @Test
    fun `canKickUser » return false when not member of room`() =
        canKickAlice(
            meLevel = 56,
            aliceLevel = 54,
            kickLevel = 55,
            expect = false,
        ) {
            roomStore.update(roomId) { Room(roomId, membership = LEAVE) }
        }

    @Test
    fun `canKickUser » my power level lt kick level » return true when my level gt other user level`() =
        canKickAlice(
            meLevel = 56,
            aliceLevel = 54,
            kickLevel = 55,
            expect = true,
        )

    @Test
    fun `canKickUser » my power level less than kick level » return false when my level == other user level`() =
        canKickAlice(
            meLevel = 56,
            aliceLevel = 56,
            kickLevel = 55,
            expect = false,
        )

    @Test
    fun `canKickUser » my power level less than kick level » return false when my level lt other user level`() =
        canKickAlice(
            meLevel = 56,
            aliceLevel = 57,
            kickLevel = 55,
            expect = false,
        )

    @Test
    fun `canKickUser » my power level == kick level » return true when my level gt other user level`() =
        canKickAlice(
            meLevel = 55,
            aliceLevel = 54,
            kickLevel = 55,
            expect = true,
        )

    @Test
    fun `canKickUser » my power level == kick level » return false when my level == other user level`() =
        canKickAlice(
            meLevel = 55,
            aliceLevel = 55,
            kickLevel = 55,
            expect = false,
        )

    @Test
    fun `canKickUser » my power level == kick level » return false when my level lt other user level`() =
        canKickAlice(
            meLevel = 55,
            aliceLevel = 56,
            kickLevel = 55,
            expect = false,
        )

    @Test
    fun `canKickUser » my power level lt kick level » return false when my level gt other user level`() =
        canKickAlice(
            meLevel = 54,
            aliceLevel = 53,
            kickLevel = 55,
            expect = false,
        )

    @Test
    fun `canKickUser » my power level lt kick level » return false when my level == other user level`() =
        canKickAlice(
            meLevel = 54,
            aliceLevel = 54,
            kickLevel = 55,
            expect = false,
        )

    @Test
    fun `canKickUser » my power level lt kick level » return false when my level lt other user level`() =
        canKickAlice(
            meLevel = 54,
            aliceLevel = 55,
            kickLevel = 55,
            expect = false,
        )

    @Test
    fun `canBanUser » return false when not member of room`() =
        canBanAlice(
            meLevel = 56,
            aliceLevel = 54,
            banLevel = 55,
            expect = false
        ) {
            roomStore.update(roomId) { Room(roomId, membership = LEAVE) }
        }

    @Test
    fun `canBanUser » my power level gt ban level » return true when my level gt other user level`() =
        canBanAlice(
            meLevel = 56,
            aliceLevel = 54,
            banLevel = 55,
            expect = true
        )


    @Test
    fun `canBanUser » my power level gt ban level » return false when my level == other user level`() =
        canBanAlice(
            meLevel = 56,
            aliceLevel = 56,
            banLevel = 55,
            expect = false
        )

    @Test
    fun `canBanUser » my power level gt ban level » return false when my level lt other user level`() =
        canBanAlice(
            meLevel = 56,
            aliceLevel = 57,
            banLevel = 55,
            expect = false
        )

    @Test
    fun `canBanUser » my power level == ban level » return true when my level gt other user level`() =
        canBanAlice(
            meLevel = 55,
            aliceLevel = 54,
            banLevel = 55,
            expect = true
        )

    @Test
    fun `canBanUser » my power level == ban level » return false when my level == other user level`() =
        canBanAlice(
            meLevel = 55,
            aliceLevel = 55,
            banLevel = 55,
            expect = false
        )

    @Test
    fun `canBanUser » my power level == ban level » return false when my level lt other user level`() =
        canBanAlice(
            meLevel = 55,
            aliceLevel = 56,
            banLevel = 55,
            expect = false
        )

    @Test
    fun `canBanUser » my power level lt ban level » return false when my level gt other user level`() =
        canBanAlice(
            meLevel = 54,
            aliceLevel = 53,
            banLevel = 55,
            expect = false
        )

    @Test
    fun `canBanUser » my power level lt ban level » return false when my level == other user level`() =
        canBanAlice(
            meLevel = 54,
            aliceLevel = 54,
            banLevel = 55,
            expect = false
        )

    @Test
    fun `canBanUser » my power level lt ban level » return false when my level lt other user level`() =
        canBanAlice(
            meLevel = 54,
            aliceLevel = 55,
            banLevel = 55,
            expect = false
        )

    @Test
    fun `canUnbanUser » return false when not member of room`() = runTest {
        roomStore.update(roomId) { Room(roomId, membership = LEAVE) }
        val otherUserLevel = 60L
        val powerLevelsEvent = getPowerLevelsEvent(
            PowerLevelsEventContent(
                users = mapOf(
                    me to 61L,
                    alice to otherUserLevel
                ), kick = 60L,
                ban = 60L
            )
        )
        roomStateStore.save(powerLevelsEvent)
        cut.canUnbanUser(roomId, alice).first() shouldBe false
    }

    @Test
    fun `canUnbanUser » my level gt kick level » my power level gt ban level » return true when my level gt other user level`() =
        myLevelGtKickLevelGtBanLevel.canUnbanAlice(
            aliceLevel = 60L,
            expect = true,
        )

    @Test
    fun `canUnbanUser » my level gt kick level » my power level gt ban level » return false when my level == other user level`() =
        myLevelGtKickLevelGtBanLevel.canUnbanAlice(
            aliceLevel = 61L,
            expect = false,
        )

    @Test
    fun `canUnbanUser » my level gt kick level » my power level gt ban level » return false when my level lt other user level`() =
        myLevelGtKickLevelGtBanLevel.canUnbanAlice(
            aliceLevel = 62L,
            expect = false,
        )

    @Test
    fun `canUnbanUser » my level gt kick level » my power level == ban level » return true when my level gt other user level`() =
        myLevelGtKickLevelEqBanLevel.canUnbanAlice(
            aliceLevel = 60L,
            expect = true,
        )

    @Test
    fun `canUnbanUser » my level gt kick level » my power level == ban level » return false when my level == other user level`() =
        myLevelGtKickLevelEqBanLevel.canUnbanAlice(
            aliceLevel = 61L,
            expect = false,
        )

    @Test
    fun `canUnbanUser » my level gt kick level » my power level == ban level » return false when my level lt other user level`() =
        myLevelGtKickLevelEqBanLevel.canUnbanAlice(
            aliceLevel = 62L,
            expect = false,
        )

    @Test
    fun `canUnbanUser » my level gt kick level » my power level lt ban level » return false when my level gt other user level`() =
        myLevelGtKickLevelLtBanLevel.canUnbanAlice(
            aliceLevel = 60L,
            expect = false,
        )

    @Test
    fun `canUnbanUser » my level gt kick level » my power level lt ban level » return false when my level == other user level`() =
        myLevelGtKickLevelLtBanLevel.canUnbanAlice(
            aliceLevel = 61L,
            expect = false,
        )

    @Test
    fun `canUnbanUser » my level gt kick level » my power level lt ban level » return false when my level lt other user level`() =
        myLevelGtKickLevelLtBanLevel.canUnbanAlice(
            aliceLevel = 62L,
            expect = false,
        )

    @Test
    fun `canUnbanUser » my level == kick level » my power level gt ban level » return true when my level gt other user level`() =
        myLevelEqKickLevelGtBanLevel.canUnbanAlice(
            aliceLevel = 60L,
            expect = true,
        )

    @Test
    fun `canUnbanUser » my level == kick level » my power level gt ban level » return false when my level == other user level`() =
        myLevelEqKickLevelGtBanLevel.canUnbanAlice(
            aliceLevel = 61L,
            expect = false,
        )

    @Test
    fun `canUnbanUser » my level == kick level » my power level gt ban level » return false when my level lt other user level`() =
        myLevelEqKickLevelGtBanLevel.canUnbanAlice(
            aliceLevel = 62L,
            expect = false,
        )

    @Test
    fun `canUnbanUser » my level == kick level » my power level == ban level » return true when my level gt other user level`() =
        myLevelEqKickLevelEqBanLevel.canUnbanAlice(
            aliceLevel = 60L,
            expect = true,
        )

    @Test
    fun `canUnbanUser » my level == kick level » my power level == ban level » return false when my level == other user level`() =
        myLevelEqKickLevelEqBanLevel.canUnbanAlice(
            aliceLevel = 61L,
            expect = false,
        )

    @Test
    fun `canUnbanUser » my level == kick level » my power level == ban level » return false when my level lt other user level`() =
        myLevelEqKickLevelEqBanLevel.canUnbanAlice(
            aliceLevel = 62L,
            expect = false,
        )

    @Test
    fun `canUnbanUser » my level == kick level » my power level lt ban level » return false when my level gt other user level`() =
        myLevelEqKickLevelLtBanLevel.canUnbanAlice(
            aliceLevel = 60L,
            expect = false,
        )

    @Test
    fun `canUnbanUser » my level == kick level » my power level lt ban level » return false when my level == other user level`() =
        myLevelEqKickLevelLtBanLevel.canUnbanAlice(
            aliceLevel = 61L,
            expect = false,
        )

    @Test
    fun `canUnbanUser » my level == kick level » my power level lt ban level » return false when my level lt other user level`() =
        myLevelEqKickLevelLtBanLevel.canUnbanAlice(
            aliceLevel = 62L,
            expect = false,
        )

    @Test
    fun `canUnbanUser » my level lt kick level » my power level gt ban level » return false when my level gt other user level`() =
        myLevelLtKickLevelGtBanLevel.canUnbanAlice(
            aliceLevel = 60L,
            expect = false,
        )

    @Test
    fun `canUnbanUser » my level lt kick level » my power level gt ban level » return false when my level == other user level`() =
        myLevelLtKickLevelGtBanLevel.canUnbanAlice(
            aliceLevel = 61L,
            expect = false,
        )

    @Test
    fun `canUnbanUser » my level lt kick level » my power level gt ban level » return false when my level lt other user level`() =
        myLevelLtKickLevelGtBanLevel.canUnbanAlice(
            aliceLevel = 62L,
            expect = false,
        )

    @Test
    fun `canUnbanUser » my level lt kick level » my power level == ban level » return false when my level gt other user level`() =
        myLevelLtKickLevelEqBanLevel.canUnbanAlice(
            aliceLevel = 60L,
            expect = false,
        )

    @Test
    fun `canUnbanUser » my level lt kick level » my power level == ban level » return false when my level == other user level`() =
        myLevelLtKickLevelEqBanLevel.canUnbanAlice(
            aliceLevel = 61L,
            expect = false,
        )

    @Test
    fun `canUnbanUser » my level lt kick level » my power level == ban level » return false when my level lt other user level`() =
        myLevelLtKickLevelEqBanLevel.canUnbanAlice(
            aliceLevel = 62L,
            expect = false,
        )

    @Test
    fun `canUnbanUser » my level lt kick level » my power level lt ban level » return false when my level gt other user level`() =
        myLevelLtKickLevelLtBanLevel.canUnbanAlice(
            aliceLevel = 60L,
            expect = false,
        )

    @Test
    fun `canUnbanUser » my level lt kick level » my power level lt ban level return false when my level == other user level`() =
        myLevelLtKickLevelLtBanLevel.canUnbanAlice(
            aliceLevel = 61L,
            expect = false,
        )

    @Test
    fun `canUnbanUser » my level lt kick level » my power level lt ban level return false when my level lt other user level`() =
        myLevelLtKickLevelLtBanLevel.canUnbanAlice(
            aliceLevel = 62L,
            expect = false,
        )

    @Test
    fun `canInvite » return false when not member of room`() =
        canInvite(
            meLevel = 56,
            inviteLevel = 55,
            expect = false,
        ) {
            roomStore.update(roomId) { Room(roomId, membership = LEAVE) }
        }

    @Test
    fun `canInvite » return true when my power level gt invite level`() =
        canInvite(
            meLevel = 56,
            inviteLevel = 55,
            expect = true,
        )

    @Test
    fun `canInvite » return true when my power level == invite level`() =
        canInvite(
            meLevel = 55,
            inviteLevel = 55,
            expect = true,
        )

    @Test
    fun `canInvite » return false when my power level lt invite level`() =
        canInvite(
            meLevel = 54,
            inviteLevel = 55,
            expect = false,
        )

    @Test
    fun `canInviteUser » return false when not member of room`() =
        canInviteUser(
            meLevel = 56,
            inviteLevel = 55,
            expect = false,
        ) {
            roomStore.update(roomId) { Room(roomId, membership = LEAVE) }
        }

    @Test
    fun `canInviteUser » User I want to invite is banned » return false when my power level gt invite level`() =
        canInviteUser(
            meLevel = 56,
            inviteLevel = 55,
            expect = false,
        ) {
            roomUserStore.update(alice, roomId) { aliceRoomUserBanned }
        }

    @Test
    fun `canInviteUser » User I want to invite is banned » return false when my power level == invite level`() =
        canInviteUser(
            meLevel = 55,
            inviteLevel = 55,
            expect = false
        ) {
            roomUserStore.update(alice, roomId) { aliceRoomUserBanned }
        }

    @Test
    fun `canInviteUser » User I want to invite is banned » return false when my power level lt invite level`() =
        canInviteUser(
            meLevel = 54,
            inviteLevel = 55,
            expect = false
        ) {
            roomUserStore.update(alice, roomId) { aliceRoomUserBanned }
        }

    @Test
    fun `canInviteUser » User I want to invite is not banned » return true when my power level gt invite level`() =
        canInviteUser(
            meLevel = 56,
            inviteLevel = 55,
            expect = true
        ) {
            roomUserStore.update(alice, roomId) { aliceRoomUser }
        }

    @Test
    fun `canInviteUser » User I want to invite is not banned » return true when my power level == invite level`() =
        canInviteUser(
            meLevel = 55,
            inviteLevel = 55,
            expect = true
        ) {
            roomUserStore.update(alice, roomId) { aliceRoomUser }
        }

    @Test
    fun `canInviteUser » User I want to invite is not banned » return false when my power level lt invite level`() =
        canInviteUser(
            meLevel = 54,
            inviteLevel = 55,
            expect = false
        ) {
            roomUserStore.update(alice, roomId) { aliceRoomUser }
        }

    @Test
    fun `canSendEvent » return false when not member of room`() = runTest {
        roomStore.update(roomId) { Room(roomId, membership = LEAVE) }
        val powerLevelsEvent = getPowerLevelsEvent(
            PowerLevelsEventContent(
                users = mapOf(
                    me to 55,
                    alice to 50
                ),
                events = mapOf(EventType(RoomMessageEventContent::class, "m.room.message") to 55),
            )
        )
        roomStateStore.save(powerLevelsEvent)
        cut.canSendEvent<RoomMessageEventContent>(roomId).first() shouldBe false
    }

    @Test
    fun `canSendEvent » be true when allowed to send`() = runTest {
        val powerLevelsEvent = getPowerLevelsEvent(
            PowerLevelsEventContent(
                users = mapOf(
                    me to 55,
                    alice to 50
                ),
                events = mapOf(EventType(RoomMessageEventContent::class, "m.room.message") to 55),
            )
        )
        roomStateStore.save(powerLevelsEvent)
        cut.canSendEvent<RoomMessageEventContent>(roomId).first() shouldBe true
    }

    @Test
    fun `canSendEvent » be false when not allowed to send`() = runTest {
        val powerLevelsEvent = getPowerLevelsEvent(
            PowerLevelsEventContent(
                users = mapOf(
                    me to 55,
                    alice to 50
                ),
                events = mapOf(EventType(RoomMessageEventContent::class, "m.room.message") to 56),
            )
        )
        roomStateStore.save(powerLevelsEvent)
        cut.canSendEvent<RoomMessageEventContent>(roomId).first() shouldBe false
    }

    @Test
    fun `canSendEvent » use stateDefault`() = runTest {
        val powerLevelsEvent = getPowerLevelsEvent(
            PowerLevelsEventContent(
                users = mapOf(me to 50),
                events = mapOf(),
                stateDefault = 55
            )
        )
        roomStateStore.save(powerLevelsEvent)
        cut.canSendEvent<NameEventContent>(roomId).first() shouldBe false
    }

    @Test
    fun `canSendEvent » use eventDefaults`() = runTest {
        val powerLevelsEvent = getPowerLevelsEvent(
            PowerLevelsEventContent(
                users = mapOf(me to 50),
                events = mapOf(),
                eventsDefault = 55
            )
        )
        roomStateStore.save(powerLevelsEvent)
        cut.canSendEvent<RoomMessageEventContent>(roomId).first() shouldBe false
    }

    @Test
    fun `canSetPowerLevelToMax » return null when not member of room`() = runTest {
        roomStore.update(roomId) { Room(roomId, membership = LEAVE) }
        val powerLevelsEvent = getPowerLevelsEvent(
            PowerLevelsEventContent(
                users = mapOf(
                    me to 55,
                    alice to 50
                ),
                events = mapOf(EventType(PowerLevelsEventContent::class, "m.room.power_levels") to 55),
                stateDefault = 60L
            )
        )
        roomStateStore.save(powerLevelsEvent)
        cut.canSetPowerLevelToMax(roomId, alice).first() shouldBe null
    }

    @Test
    fun `canSetPowerLevelToMax » eventsMap is not null » not allow to change the power level when events power_levels value gt own power level`() =
        runTest {
            val powerLevelsEvent = getPowerLevelsEvent(
                PowerLevelsEventContent(
                    users = mapOf(
                        me to 55,
                        alice to 50
                    ),
                    events = mapOf(EventType(PowerLevelsEventContent::class, "m.room.power_levels") to 56),
                    stateDefault = 60L
                )
            )
            roomStateStore.save(powerLevelsEvent)
            cut.canSetPowerLevelToMax(roomId, alice).first() shouldBe null
        }

    @Test
    fun `canSetPowerLevelToMax » eventsMap is not null » return own power level as max power level value when events power_levels value == own power level`() =
        runTest {
            val powerLevelsEvent = getPowerLevelsEvent(
                PowerLevelsEventContent(
                    users = mapOf(
                        me to 55,
                        alice to 50
                    ),
                    events = mapOf(EventType(PowerLevelsEventContent::class, "m.room.power_levels") to 55),
                    stateDefault = 60L
                )
            )
            roomStateStore.save(powerLevelsEvent)
            cut.canSetPowerLevelToMax(roomId, alice).first() shouldBe 55
        }

    @Test
    fun `canSetPowerLevelToMax » eventsMap is null » not allow to change the power level when stateDefault value gt own power level`() =
        runTest {
            val powerLevelsEvent = getPowerLevelsEvent(
                PowerLevelsEventContent(
                    users = mapOf(
                        me to 55,
                        alice to 50
                    ),
                    stateDefault = 56
                )
            )
            roomStateStore.save(powerLevelsEvent)
            cut.canSetPowerLevelToMax(roomId, alice).first() shouldBe null
        }

    @Test
    fun `canSetPowerLevelToMax » eventsMap is null » return own power level as max power level value when stateDefault value == own power level`() =
        runTest {
            val powerLevelsEvent = getPowerLevelsEvent(
                PowerLevelsEventContent(
                    users = mapOf(
                        me to 55,
                        alice to 50
                    ),
                    stateDefault = 55
                )
            )
            roomStateStore.save(powerLevelsEvent)
            cut.canSetPowerLevelToMax(roomId, alice).first() shouldBe 55
        }


    @Test
    fun `canSetPowerLevelToMax » oldUserPowerLevel gt ownPowerLevel » not allow to change the power level when otherUserId != me`() =
        runTest {
            val powerLevelsEvent = getPowerLevelsEvent(
                PowerLevelsEventContent(
                    users = mapOf(
                        me to 55,
                        alice to 56
                    ),
                    events = mapOf(EventType(PowerLevelsEventContent::class, "m.room.power_levels") to 55),
                    stateDefault = 55
                )
            )
            roomStateStore.save(powerLevelsEvent)
            cut.canSetPowerLevelToMax(roomId, alice).first() shouldBe null
        }


    @Test
    fun `canSetPowerLevelToMax » oldUserPowerLevel == ownPowerLevel » not allow to change the power level when otherUserId != me`() =
        runTest {
            roomStateStore.save(powerLevelsEvent)
            cut.canSetPowerLevelToMax(roomId, alice).first() shouldBe null
        }

    @Test
    fun `canSetPowerLevelToMax » oldUserPowerLevel == ownPowerLevel » return own power level as max power level value when otherUserId == me`() =
        runTest {
            roomStateStore.save(powerLevelsEvent)
            cut.canSetPowerLevelToMax(roomId, me).first() shouldBe 55
        }

    @Test
    fun `canSetPowerLevelToMax » oldUserPowerLevel == ownPowerLevel » return own power level as max power level value when all criteria are met`() =
        runTest {
            val powerLevelsEvent = getPowerLevelsEvent(
                PowerLevelsEventContent(
                    users = mapOf(
                        me to 55,
                        alice to 54
                    ),
                    events = mapOf(EventType(PowerLevelsEventContent::class, "m.room.power_levels") to 52),
                    stateDefault = 52
                )
            )
            roomStateStore.save(powerLevelsEvent)
            cut.canSetPowerLevelToMax(roomId, alice).first() shouldBe 55
        }


    @Test
    fun `canRedactEvent » return false when not member of room`() = runTest {
        roomStore.update(roomId) { Room(roomId, membership = LEAVE) }
        roomStateStore.save(
            StateEvent(
                content = PowerLevelsEventContent(
                    users = mapOf(
                        me to 40,
                    ),
                    redact = 30,
                ),
                id = EventId("eventId"),
                sender = me,
                roomId = roomId,
                originTimestamp = 0L,
                stateKey = "",
            )
        )
        roomTimelineStore.addAll(listOf(eventByOtherUser))
        cut.canRedactEvent(eventByOtherUser.roomId, eventByOtherUser.eventId).firstOrNull() shouldBe false
    }

    @Test
    fun `canRedactEvent » return true if it is the event of the user and the user's power level is at least as high as the needed event redaction level`() =
        runTest {
            roomStateStore.save(
                StateEvent(
                    content = PowerLevelsEventContent(
                        users = mapOf(
                            me to 40,
                        ),
                        events = mapOf(
                            EventType(RedactionEventContent::class, "m.room.redaction") to 30,
                        )
                    ),
                    id = EventId("eventId"),
                    sender = me,
                    roomId = roomId,
                    originTimestamp = 0L,
                    stateKey = "",
                )
            )
            roomTimelineStore.addAll(listOf(eventByUser))
            cut.canRedactEvent(eventByUser.roomId, eventByUser.eventId).firstOrNull() shouldBe true
        }

    @Test
    fun `canRedactEvent » return true if it is the event of another user but the user's power level is at least as high as the needed redaction power level`() =
        runTest {
            roomStateStore.save(
                StateEvent(
                    content = PowerLevelsEventContent(
                        users = mapOf(
                            me to 40,
                        ),
                        redact = 30,
                    ),
                    id = EventId("eventId"),
                    sender = me,
                    roomId = roomId,
                    originTimestamp = 0L,
                    stateKey = "",
                )
            )
            roomTimelineStore.addAll(listOf(eventByOtherUser))
            cut.canRedactEvent(eventByOtherUser.roomId, eventByOtherUser.eventId).firstOrNull() shouldBe true
        }

    @Test
    fun `canRedactEvent » return false if the user has no high enough power level for event redactions`() =
        runTest {
            roomStateStore.save(
                StateEvent(
                    content = PowerLevelsEventContent(
                        users = mapOf(
                            me to 20,
                        ),
                        events = mapOf(
                            EventType(RedactionEventContent::class, "m.room.redaction") to 30,
                        )
                    ),
                    id = EventId("eventId"),
                    sender = me,
                    roomId = roomId,
                    originTimestamp = 0L,
                    stateKey = "",
                )
            )
            roomTimelineStore.addAll(listOf(eventByUser))
            cut.canRedactEvent(eventByUser.roomId, eventByUser.eventId).firstOrNull() shouldBe false
        }

    @Test
    fun `canRedactEvent » return false if the user has no high enough power level for redactions of events of other users`() =
        runTest {
            roomStateStore.save(
                StateEvent(
                    content = PowerLevelsEventContent(
                        users = mapOf(
                            me to 20,
                        ),
                        redact = 30,
                    ),
                    id = EventId("eventId"),
                    sender = me,
                    roomId = roomId,
                    originTimestamp = 0L,
                    stateKey = "",
                )
            )
            roomTimelineStore.addAll(listOf(eventByOtherUser))
            cut.canRedactEvent(eventByOtherUser.roomId, eventByOtherUser.eventId).firstOrNull() shouldBe false
        }

    @Test
    fun `canRedactEvent » not allow to redact an already redacted event`() = runTest {
        roomStateStore.save(
            StateEvent(
                content = PowerLevelsEventContent(
                    users = mapOf(
                        me to 40,
                    ),
                    redact = 30,
                ),
                id = EventId("eventId"),
                sender = me,
                roomId = roomId,
                originTimestamp = 0L,
                stateKey = "",
            )
        )
        val event = TimelineEvent(
            event = MessageEvent(
                content = RedactedEventContent(eventType = "redacted"),
                id = EventId("event"),
                sender = me,
                roomId = roomId,
                originTimestamp = 0L
            ),
            previousEventId = null,
            nextEventId = null,
            gap = null,
        )
        roomTimelineStore.addAll(listOf(event))
        cut.canRedactEvent(event.roomId, event.eventId).firstOrNull() shouldBe false
    }

    @Test
    fun `canRedactEvent » react to changes in the power levels » react to changes in the user's power levels`() =
        runTest {
            roomStateStore.save(
                StateEvent(
                    content = PowerLevelsEventContent(
                        users = mapOf(
                            me to 40,
                        ),
                        redact = 30,
                    ),
                    id = EventId("eventId"),
                    sender = me,
                    roomId = roomId,
                    originTimestamp = 0L,
                    stateKey = "",
                )
            )
            roomTimelineStore.addAll(listOf(eventByOtherUser))
            val resultFlow = cut.canRedactEvent(eventByOtherUser.roomId, eventByOtherUser.eventId)
            resultFlow.first() shouldBe true
            roomStateStore.save(
                StateEvent(
                    content = PowerLevelsEventContent(
                        users = mapOf(
                            me to 20,
                        ),
                        redact = 30,
                    ),
                    id = EventId("eventId"),
                    sender = me,
                    roomId = roomId,
                    originTimestamp = 0L,
                    stateKey = "",
                )
            )
            resultFlow.first() shouldBe false
        }

    @Test
    fun `getUserPresence » no presence in store » should be null`() = runTest {
        cut.getPresence(alice).first() shouldBe null
    }

    @Test
    fun `getUserPresence » sync is running » should be unavailable`() = runTest {
        val lastActive = Instant.fromEpochMilliseconds(24)
        currentSyncState.value = SyncState.STARTED
        userPresenceStore.setPresence(alice, UserPresence(Presence.ONLINE, clock.now(), lastActive))
        cut.getPresence(alice).first() shouldBe UserPresence(Presence.UNAVAILABLE, clock.now(), lastActive)
    }

    @Test
    fun `getUserPresence » is currently active » should pass from store`() = runTest {
        val presence =
            UserPresence(
                presence = Presence.ONLINE,
                lastUpdate = clock.now(),
                lastActive = Instant.fromEpochMilliseconds(24),
                isCurrentlyActive = true,
                statusMessage = "status"
            )
        userPresenceStore.setPresence(alice, presence)
        cut.getPresence(alice).first() shouldBe presence
    }

    @Test
    fun `getUserPresence » last active is below threshold » should pass from store and make unavailable later`() =
        runTest {
            val presence =
                UserPresence(
                    presence = Presence.ONLINE,
                    lastUpdate = clock.now(),
                    lastActive = clock.now() - 4.minutes,
                    statusMessage = "status"
                )
            userPresenceStore.setPresence(alice, presence)
            val result = cut.getPresence(alice).stateIn(backgroundScope)
            result.value shouldBe presence

            delay(2.minutes)
            clock.nowValue += 2.minutes

            result.value shouldBe UserPresence(Presence.UNAVAILABLE, clock.now() - 2.minutes, clock.now() - 6.minutes)

            // should reset on store update
            val presence2 =
                UserPresence(
                    presence = Presence.ONLINE,
                    lastUpdate = clock.now(),
                    lastActive = clock.now(),
                    statusMessage = "status"
                )
            userPresenceStore.setPresence(alice, presence2)
            delay(100.milliseconds)
            result.value shouldBe presence2
        }

    @Test
    fun `getUserPresence » last active is below threshold » should fallback to last update`() =
        runTest {
            val presence =
                UserPresence(
                    presence = Presence.ONLINE,
                    lastUpdate = clock.now() - 4.minutes,
                    statusMessage = "status"
                )
            userPresenceStore.setPresence(alice, presence)
            val result = cut.getPresence(alice).stateIn(backgroundScope)
            result.value shouldBe presence

            delay(2.minutes)
            clock.nowValue += 2.minutes

            result.value shouldBe UserPresence(Presence.UNAVAILABLE, clock.now() - 6.minutes)
        }

    @Test
    fun `getUserPresence » last active is above threshold » should be unavailable`() =
        runTest {
            val presence =
                UserPresence(
                    presence = Presence.ONLINE,
                    lastUpdate = clock.now() - 6.minutes,
                    statusMessage = "status"
                )
            userPresenceStore.setPresence(alice, presence)
            cut.getPresence(alice).first() shouldBe UserPresence(Presence.UNAVAILABLE, clock.now() - 6.minutes)
        }

    private fun canKickAlice(
        meLevel: Long,
        aliceLevel: Long,
        kickLevel: Long = PowerLevelsEventContent.KICK_DEFAULT,
        expect: Boolean,
        setup: suspend () -> Unit = {},
    ) = runTest {
        setup()
        val powerLevelsEvent = getPowerLevelsEvent(
            PowerLevelsEventContent(
                users = mapOf(
                    me to meLevel,
                    alice to aliceLevel
                ), kick = kickLevel
            )
        )
        roomStateStore.save(powerLevelsEvent)
        cut.canKickUser(roomId, alice).first() shouldBe expect
    }

    private fun canBanAlice(
        meLevel: Long,
        aliceLevel: Long,
        banLevel: Long = PowerLevelsEventContent.BAN_DEFAULT,
        expect: Boolean,
        setup: suspend () -> Unit = {},
    ) = runTest {
        setup()
        val powerLevelsEvent = getPowerLevelsEvent(
            PowerLevelsEventContent(
                users = mapOf(
                    me to meLevel,
                    alice to aliceLevel
                ), ban = banLevel
            )
        )
        roomStateStore.save(powerLevelsEvent)
        cut.canBanUser(roomId, alice).first() shouldBe expect
    }

    private fun canInvite(
        meLevel: Long,
        inviteLevel: Long = PowerLevelsEventContent.INVITE_DEFAULT,
        expect: Boolean,
        setup: suspend () -> Unit = {},
    ) = runTest {
        setup()
        val powerLevelsEvent = getPowerLevelsEvent(
            PowerLevelsEventContent(
                users = mapOf(
                    me to meLevel,
                ), invite = inviteLevel
            )
        )
        roomStateStore.save(powerLevelsEvent)
        cut.canInvite(roomId).first() shouldBe expect
    }

    private fun canInviteUser(
        meLevel: Long,
        inviteLevel: Long = PowerLevelsEventContent.INVITE_DEFAULT,
        expect: Boolean,
        setup: suspend () -> Unit = {}
    ) = runTest {
        setup()
        val powerLevelsEvent = getPowerLevelsEvent(
            PowerLevelsEventContent(
                users = mapOf(
                    me to meLevel,
                ), invite = inviteLevel
            )
        )
        roomStateStore.save(powerLevelsEvent)
        cut.canInviteUser(roomId, alice).first() shouldBe expect
    }

    private fun KickLevels.canUnbanAlice(
        aliceLevel: Long,
        expect: Boolean
    ) = runTest {
        val powerLevelsEvent =
            getPowerLevelsEvent(
                PowerLevelsEventContent(
                    users = mapOf(
                        me to myLevel,
                        alice to aliceLevel
                    ), kick = kickLevel,
                    ban = banLevel
                )
            )

        roomStateStore.save(powerLevelsEvent)
        cut.canUnbanUser(roomId, alice).first() shouldBe expect
    }

    private fun getCreateEvent(creator: UserId) = StateEvent(
        CreateEventContent(creator = creator),
        EventId("\$event"),
        creator,
        roomId,
        1234,
        stateKey = ""
    )

    private fun getPowerLevelsEvent(powerLevelsEventContent: PowerLevelsEventContent) = StateEvent(
        powerLevelsEventContent,
        EventId("\$event"),
        me,
        roomId,
        1234,
        stateKey = ""
    )
}