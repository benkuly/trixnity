package de.connect2x.trixnity.client.user

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import de.connect2x.trixnity.client.*
import de.connect2x.trixnity.client.store.*
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.core.UserInfo
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import de.connect2x.trixnity.core.model.events.EventType
import de.connect2x.trixnity.core.model.events.RedactedEventContent
import de.connect2x.trixnity.core.model.events.m.Presence
import de.connect2x.trixnity.core.model.events.m.room.*
import de.connect2x.trixnity.core.model.events.m.room.Membership.*
import de.connect2x.trixnity.core.model.keys.Key
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import de.connect2x.trixnity.core.serialization.events.default
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import de.connect2x.trixnity.test.utils.scheduleSetup
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

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

    private val powerLevelsEvent = powerLevelsEvent(
        PowerLevelsEventContent(
            users = mapOf(
                me to 55,
                alice to 55
            ),
            events = mapOf(EventType(PowerLevelsEventContent::class, "m.room.power_levels") to 54),
            stateDefault = 54
        )
    )

    private fun aliceRoomUser(membership: Membership = JOIN) = RoomUser(
        roomId, alice, "Alice", StateEvent(
            MemberEventContent(membership = membership),
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
        save(powerLevelsEvent(PowerLevelsEventContent()))
        save(createEvent(UserId("creator", "server")))
    }

    private val currentSyncState = MutableStateFlow(SyncState.RUNNING).also {
        scheduleSetup {
            it.value = SyncState.RUNNING
        }
    }

    private val userInfo = UserInfo(
        me, "IAmADeviceId", signingPublicKey = Key.Ed25519Key(null, ""),
        Key.Curve25519Key(null, "")
    )
    private val cut = UserServiceImpl(
        roomStore = roomStore,
        roomUserStore = roomUserStore,
        roomStateStore = roomStateStore,
        roomTimelineStore = roomTimelineStore,
        globalAccountDataStore = globalAccountDataStore,
        userPresenceStore = userPresenceStore,
        loadMembersService = { _, _ -> },
        userInfo = userInfo,
        currentSyncState = CurrentSyncState(currentSyncState),
        canDoAction = CanDoActionImpl(userInfo, GetPowerLevelImpl()),
        getPowerLevelDelegate = GetPowerLevelImpl(),
        clock = clock,
        mappings = EventContentSerializerMappings.default,
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
    fun `canKickUser - return false when not member of room`() = runTest {
        canKickAlice(
            ownLevel = 56,
            otherLevel = 54,
            kickLevel = 55,
            ownMembership = LEAVE,
            expect = false,
        )
    }

    @Test
    fun `canKickUser - other user is already LEAVE - return false`() = runTest {
        canKickAlice(
            ownLevel = 56,
            otherLevel = 54,
            kickLevel = 55,
            otherMembership = LEAVE,
            expect = false,
        )
    }

    @Test
    fun `canKickUser - my power level lt kick level - return true when my level gt other user level`() = runTest {
        canKickAlice(
            ownLevel = 56,
            otherLevel = 54,
            kickLevel = 55,
            expect = true,
        )
    }

    @Test
    fun `canKickUser - my power level less than kick level - return false when my level == other user level`() =
        runTest {
            canKickAlice(
                ownLevel = 56,
                otherLevel = 56,
                kickLevel = 55,
                expect = false,
            )
        }

    @Test
    fun `canKickUser - my power level less than kick level - return false when my level lt other user level`() =
        runTest {
            canKickAlice(
                ownLevel = 56,
                otherLevel = 57,
                kickLevel = 55,
                expect = false,
            )
        }

    @Test
    fun `canKickUser - my power level == kick level - return true when my level gt other user level`() = runTest {
        canKickAlice(
            ownLevel = 55,
            otherLevel = 54,
            kickLevel = 55,
            expect = true,
        )
    }

    @Test
    fun `canKickUser - my power level == kick level - return false when my level == other user level`() = runTest {
        canKickAlice(
            ownLevel = 55,
            otherLevel = 55,
            kickLevel = 55,
            expect = false,
        )
    }

    @Test
    fun `canKickUser - my power level == kick level - return false when my level lt other user level`() = runTest {
        canKickAlice(
            ownLevel = 55,
            otherLevel = 56,
            kickLevel = 55,
            expect = false,
        )
    }

    @Test
    fun `canKickUser - my power level lt kick level - return false when my level gt other user level`() = runTest {
        canKickAlice(
            ownLevel = 54,
            otherLevel = 53,
            kickLevel = 55,
            expect = false,
        )
    }

    @Test
    fun `canKickUser - my power level lt kick level - return false when my level == other user level`() = runTest {
        canKickAlice(
            ownLevel = 54,
            otherLevel = 54,
            kickLevel = 55,
            expect = false,
        )
    }

    @Test
    fun `canKickUser - my power level lt kick level - return false when my level lt other user level`() = runTest {
        canKickAlice(
            ownLevel = 54,
            otherLevel = 55,
            kickLevel = 55,
            expect = false,
        )
    }

    @Test
    fun `canBanUser - not member of room - return false`() = runTest {
        canBanAlice(
            ownLevel = 56,
            otherLevel = 54,
            banLevel = 55,
            ownMembership = LEAVE,
            expect = false
        )
    }

    @Test
    fun `canBanUser - other user already ban - return false`() = runTest {
        canBanAlice(
            ownLevel = 56,
            otherLevel = 54,
            banLevel = 55,
            otherMembership = BAN,
            expect = false
        )
    }

    @Test
    fun `canBanUser - my power level gt ban level - return true when my level gt other user level`() = runTest {
        canBanAlice(
            ownLevel = 56,
            otherLevel = 54,
            banLevel = 55,
            expect = true
        )
    }


    @Test
    fun `canBanUser - my power level gt ban level - return false when my level == other user level`() = runTest {
        canBanAlice(
            ownLevel = 56,
            otherLevel = 56,
            banLevel = 55,
            expect = false
        )
    }

    @Test
    fun `canBanUser - my power level gt ban level - return false when my level lt other user level`() = runTest {
        canBanAlice(
            ownLevel = 56,
            otherLevel = 57,
            banLevel = 55,
            expect = false
        )
    }

    @Test
    fun `canBanUser - my power level == ban level - return true when my level gt other user level`() = runTest {
        canBanAlice(
            ownLevel = 55,
            otherLevel = 54,
            banLevel = 55,
            expect = true
        )
    }

    @Test
    fun `canBanUser - my power level == ban level - return false when my level == other user level`() = runTest {
        canBanAlice(
            ownLevel = 55,
            otherLevel = 55,
            banLevel = 55,
            expect = false
        )
    }

    @Test
    fun `canBanUser - my power level == ban level - return false when my level lt other user level`() = runTest {
        canBanAlice(
            ownLevel = 55,
            otherLevel = 56,
            banLevel = 55,
            expect = false
        )
    }

    @Test
    fun `canBanUser - my power level lt ban level - return false when my level gt other user level`() = runTest {
        canBanAlice(
            ownLevel = 54,
            otherLevel = 53,
            banLevel = 55,
            expect = false
        )
    }

    @Test
    fun `canBanUser - my power level lt ban level - return false when my level == other user level`() = runTest {
        canBanAlice(
            ownLevel = 54,
            otherLevel = 54,
            banLevel = 55,
            expect = false
        )
    }

    @Test
    fun `canBanUser - my power level lt ban level - return false when my level lt other user level`() = runTest {
        canBanAlice(
            ownLevel = 54,
            otherLevel = 55,
            banLevel = 55,
            expect = false
        )
    }

    @Test
    fun `canUnbanUser - not member of room - return false`() = runTest {
        myLevelGtKickLevelGtBanLevel.canUnbanAlice(
            otherLevel = 60L,
            ownMembership = LEAVE,
            expect = false,
        )
    }

    @Test
    fun `canUnbanUser - other user not ban - return false`() = runTest {
        (Membership.entries - BAN).forEach { membership ->
            myLevelGtKickLevelGtBanLevel.canUnbanAlice(
                otherLevel = 60L,
                otherMembership = membership,
                expect = false,
            )
        }
    }

    @Test
    fun `canUnbanUser - my level gt kick level - my power level gt ban level - return true when my level gt other user level`() =
        runTest {
            myLevelGtKickLevelGtBanLevel.canUnbanAlice(
                otherLevel = 60L,
                otherMembership = BAN,
                expect = true,
            )
        }

    @Test
    fun `canUnbanUser - my level gt kick level - my power level gt ban level - return false when my level == other user level`() =
        runTest {
            myLevelGtKickLevelGtBanLevel.canUnbanAlice(
                otherLevel = 61L,
                otherMembership = BAN,
                expect = false,
            )
        }

    @Test
    fun `canUnbanUser - my level gt kick level - my power level gt ban level - return false when my level lt other user level`() =
        runTest {
            myLevelGtKickLevelGtBanLevel.canUnbanAlice(
                otherLevel = 62L,
                otherMembership = BAN,
                expect = false,
            )
        }

    @Test
    fun `canUnbanUser - my level gt kick level - my power level == ban level - return true when my level gt other user level`() =
        runTest {
            myLevelGtKickLevelEqBanLevel.canUnbanAlice(
                otherLevel = 60L,
                otherMembership = BAN,
                expect = true,
            )
        }

    @Test
    fun `canUnbanUser - my level gt kick level - my power level == ban level - return false when my level == other user level`() =
        runTest {
            myLevelGtKickLevelEqBanLevel.canUnbanAlice(
                otherLevel = 61L,
                otherMembership = BAN,
                expect = false,
            )
        }

    @Test
    fun `canUnbanUser - my level gt kick level - my power level == ban level - return false when my level lt other user level`() =
        runTest {
            myLevelGtKickLevelEqBanLevel.canUnbanAlice(
                otherLevel = 62L,
                expect = false,
            )
        }

    @Test
    fun `canUnbanUser - my level gt kick level - my power level lt ban level - return false when my level gt other user level`() =
        runTest {
            myLevelGtKickLevelLtBanLevel.canUnbanAlice(
                otherLevel = 60L,
                otherMembership = BAN,
                expect = false,
            )
        }

    @Test
    fun `canUnbanUser - my level gt kick level - my power level lt ban level - return false when my level == other user level`() =
        runTest {
            myLevelGtKickLevelLtBanLevel.canUnbanAlice(
                otherLevel = 61L,
                otherMembership = BAN,
                expect = false,
            )
        }

    @Test
    fun `canUnbanUser - my level gt kick level - my power level lt ban level - return false when my level lt other user level`() =
        runTest {
            myLevelGtKickLevelLtBanLevel.canUnbanAlice(
                otherLevel = 62L,
                otherMembership = BAN,
                expect = false,
            )
        }

    @Test
    fun `canUnbanUser - my level == kick level - my power level gt ban level - return true when my level gt other user level`() =
        runTest {
            myLevelEqKickLevelGtBanLevel.canUnbanAlice(
                otherLevel = 60L,
                otherMembership = BAN,
                expect = true,
            )
        }

    @Test
    fun `canUnbanUser - my level == kick level - my power level gt ban level - return false when my level == other user level`() =
        runTest {
            myLevelEqKickLevelGtBanLevel.canUnbanAlice(
                otherLevel = 61L,
                otherMembership = BAN,
                expect = false,
            )
        }

    @Test
    fun `canUnbanUser - my level == kick level - my power level gt ban level - return false when my level lt other user level`() =
        runTest {
            myLevelEqKickLevelGtBanLevel.canUnbanAlice(
                otherLevel = 62L,
                otherMembership = BAN,
                expect = false,
            )
        }

    @Test
    fun `canUnbanUser - my level == kick level - my power level == ban level - return true when my level gt other user level`() =
        runTest {
            myLevelEqKickLevelEqBanLevel.canUnbanAlice(
                otherLevel = 60L,
                otherMembership = BAN,
                expect = true,
            )
        }

    @Test
    fun `canUnbanUser - my level == kick level - my power level == ban level - return false when my level == other user level`() =
        runTest {
            myLevelEqKickLevelEqBanLevel.canUnbanAlice(
                otherLevel = 61L,
                otherMembership = BAN,
                expect = false,
            )
        }

    @Test
    fun `canUnbanUser - my level == kick level - my power level == ban level - return false when my level lt other user level`() =
        runTest {
            myLevelEqKickLevelEqBanLevel.canUnbanAlice(
                otherLevel = 62L,
                otherMembership = BAN,
                expect = false,
            )
        }

    @Test
    fun `canUnbanUser - my level == kick level - my power level lt ban level - return false when my level gt other user level`() =
        runTest {
            myLevelEqKickLevelLtBanLevel.canUnbanAlice(
                otherLevel = 60L,
                otherMembership = BAN,
                expect = false,
            )
        }

    @Test
    fun `canUnbanUser - my level == kick level - my power level lt ban level - return false when my level == other user level`() =
        runTest {
            myLevelEqKickLevelLtBanLevel.canUnbanAlice(
                otherLevel = 61L,
                otherMembership = BAN,
                expect = false,
            )
        }

    @Test
    fun `canUnbanUser - my level == kick level - my power level lt ban level - return false when my level lt other user level`() =
        runTest {
            myLevelEqKickLevelLtBanLevel.canUnbanAlice(
                otherLevel = 62L,
                otherMembership = BAN,
                expect = false,
            )
        }

    @Test
    fun `canUnbanUser - my level lt kick level - my power level gt ban level - return false when my level gt other user level`() =
        runTest {
            myLevelLtKickLevelGtBanLevel.canUnbanAlice(
                otherLevel = 60L,
                otherMembership = BAN,
                expect = false,
            )
        }

    @Test
    fun `canUnbanUser - my level lt kick level - my power level gt ban level - return false when my level == other user level`() =
        runTest {
            myLevelLtKickLevelGtBanLevel.canUnbanAlice(
                otherLevel = 61L,
                otherMembership = BAN,
                expect = false,
            )
        }

    @Test
    fun `canUnbanUser - my level lt kick level - my power level gt ban level - return false when my level lt other user level`() =
        runTest {
            myLevelLtKickLevelGtBanLevel.canUnbanAlice(
                otherLevel = 62L,
                otherMembership = BAN,
                expect = false,
            )
        }

    @Test
    fun `canUnbanUser - my level lt kick level - my power level == ban level - return false when my level gt other user level`() =
        runTest {
            myLevelLtKickLevelEqBanLevel.canUnbanAlice(
                otherLevel = 60L,
                otherMembership = BAN,
                expect = false,
            )
        }

    @Test
    fun `canUnbanUser - my level lt kick level - my power level == ban level - return false when my level == other user level`() =
        runTest {
            myLevelLtKickLevelEqBanLevel.canUnbanAlice(
                otherLevel = 61L,
                otherMembership = BAN,
                expect = false,
            )
        }

    @Test
    fun `canUnbanUser - my level lt kick level - my power level == ban level - return false when my level lt other user level`() =
        runTest {
            myLevelLtKickLevelEqBanLevel.canUnbanAlice(
                otherLevel = 62L,
                otherMembership = BAN,
                expect = false,
            )
        }

    @Test
    fun `canUnbanUser - my level lt kick level - my power level lt ban level - return false when my level gt other user level`() =
        runTest {
            myLevelLtKickLevelLtBanLevel.canUnbanAlice(
                otherLevel = 60L,
                otherMembership = BAN,
                expect = false,
            )
        }

    @Test
    fun `canUnbanUser - my level lt kick level - my power level lt ban level return false when my level == other user level`() =
        runTest {
            myLevelLtKickLevelLtBanLevel.canUnbanAlice(
                otherLevel = 61L,
                otherMembership = BAN,
                expect = false,
            )
        }

    @Test
    fun `canUnbanUser - my level lt kick level - my power level lt ban level return false when my level lt other user level`() =
        runTest {
            myLevelLtKickLevelLtBanLevel.canUnbanAlice(
                otherLevel = 62L,
                otherMembership = BAN,
                expect = false,
            )
        }

    @Test
    fun `canInvite - not member of room - return false`() = runTest {
        canInvite(
            ownLevel = 56,
            inviteLevel = 55,
            ownMembership = LEAVE,
            expect = false,
        )
    }

    @Test
    fun `canInvite - return true when my power level gt invite level`() = runTest {
        canInvite(
            ownLevel = 56,
            inviteLevel = 55,
            expect = true,
        )
    }

    @Test
    fun `canInvite - return true when my power level == invite level`() = runTest {
        canInvite(
            ownLevel = 55,
            inviteLevel = 55,
            expect = true,
        )
    }

    @Test
    fun `canInvite - return false when my power level lt invite level`() = runTest {
        canInvite(
            ownLevel = 54,
            inviteLevel = 55,
            expect = false,
        )
    }

    @Test
    fun `canInviteUser - not member of room - return false`() = runTest {
        canInviteUser(
            ownLevel = 56,
            inviteLevel = 55,
            otherMembership = BAN,
            expect = false,
        )
    }

    @Test
    fun `canInviteUser - other is INVITE JOIN or BAN - return false`() =
        runTest {
            listOf(BAN, INVITE, JOIN).forEach { membership ->
                canInviteUser(
                    ownLevel = 56,
                    inviteLevel = 55,
                    otherMembership = membership,
                    expect = false
                )
            }
        }

    @Test
    fun `canInviteUser - User I want to invite is banned - return false when my power level gt invite level`() =
        runTest {
            canInviteUser(
                ownLevel = 56,
                inviteLevel = 55,
                otherMembership = BAN,
                expect = false,
            )
        }

    @Test
    fun `canInviteUser - User I want to invite is banned - return false when my power level == invite level`() =
        runTest {
            canInviteUser(
                ownLevel = 55,
                inviteLevel = 55,
                otherMembership = BAN,
                expect = false
            )
        }

    @Test
    fun `canInviteUser - User I want to invite is banned - return false when my power level lt invite level`() =
        runTest {
            canInviteUser(
                ownLevel = 54,
                inviteLevel = 55,
                otherMembership = BAN,
                expect = false
            )
        }

    @Test
    fun `canInviteUser - User I want to invite is not banned - return true when my power level gt invite level`() =
        runTest {
            canInviteUser(
                ownLevel = 56,
                inviteLevel = 55,
                otherMembership = LEAVE,
                expect = true
            )
        }

    @Test
    fun `canInviteUser - User I want to invite is not banned - return true when my power level == invite level`() =
        runTest {
            canInviteUser(
                ownLevel = 55,
                inviteLevel = 55,
                otherMembership = LEAVE,
                expect = true
            )
        }

    @Test
    fun `canInviteUser - User I want to invite is not banned - return false when my power level lt invite level`() =
        runTest {
            canInviteUser(
                ownLevel = 54,
                inviteLevel = 55,
                expect = false
            )
        }

    @Test
    fun `canSendEvent - return false when not member of room`() = runTest {
        roomStore.update(roomId) { Room(roomId, membership = LEAVE) }
        val powerLevelsEvent = powerLevelsEvent(
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
    fun `canSendEvent - be true when allowed to send`() = runTest {
        val powerLevelsEvent = powerLevelsEvent(
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
    fun `canSendEvent - be false when not allowed to send`() = runTest {
        val powerLevelsEvent = powerLevelsEvent(
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
    fun `canSendEvent - use stateDefault`() = runTest {
        val powerLevelsEvent = powerLevelsEvent(
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
    fun `canSendEvent - use eventDefaults`() = runTest {
        val powerLevelsEvent = powerLevelsEvent(
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
    fun `canSetPowerLevelToMax - other user not member`() =
        runTest {
            roomStateStore.save(powerLevelsEvent)
            cut.canSetPowerLevelToMax(roomId, alice).first() shouldBe null
        }

    @Test
    fun `canSetPowerLevelToMax - return null when not member of room`() = runTest {
        roomStore.update(roomId) { Room(roomId, membership = LEAVE) }
        roomUserStore.update(alice, roomId) { aliceRoomUser() }
        val powerLevelsEvent = powerLevelsEvent(
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
    fun `canSetPowerLevelToMax - eventsMap is not null - not allow to change the power level when events power_levels value gt own power level`() =
        runTest {
            roomUserStore.update(alice, roomId) { aliceRoomUser() }
            val powerLevelsEvent = powerLevelsEvent(
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
    fun `canSetPowerLevelToMax - eventsMap is not null - return own power level as max power level value when events power_levels value == own power level`() =
        runTest {
            roomUserStore.update(alice, roomId) { aliceRoomUser() }
            val powerLevelsEvent = powerLevelsEvent(
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
            cut.canSetPowerLevelToMax(roomId, alice).first() shouldBe PowerLevel.User(55)
        }

    @Test
    fun `canSetPowerLevelToMax - eventsMap is null - not allow to change the power level when stateDefault value gt own power level`() =
        runTest {
            roomUserStore.update(alice, roomId) { aliceRoomUser() }
            val powerLevelsEvent = powerLevelsEvent(
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
    fun `canSetPowerLevelToMax - eventsMap is null - return own power level as max power level value when stateDefault value == own power level`() =
        runTest {
            roomUserStore.update(alice, roomId) { aliceRoomUser() }
            val powerLevelsEvent = powerLevelsEvent(
                PowerLevelsEventContent(
                    users = mapOf(
                        me to 55,
                        alice to 50
                    ),
                    stateDefault = 55
                )
            )
            roomStateStore.save(powerLevelsEvent)
            cut.canSetPowerLevelToMax(roomId, alice).first() shouldBe PowerLevel.User(55)
        }


    @Test
    fun `canSetPowerLevelToMax - oldUserPowerLevel gt ownPowerLevel - not allow to change the power level when otherUserId != me`() =
        runTest {
            roomUserStore.update(alice, roomId) { aliceRoomUser() }
            val powerLevelsEvent = powerLevelsEvent(
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
    fun `canSetPowerLevelToMax - oldUserPowerLevel == ownPowerLevel - not allow to change the power level when otherUserId != me`() =
        runTest {
            roomUserStore.update(alice, roomId) { aliceRoomUser() }
            roomStateStore.save(powerLevelsEvent)
            cut.canSetPowerLevelToMax(roomId, alice).first() shouldBe null
        }

    @Test
    fun `canSetPowerLevelToMax - oldUserPowerLevel == ownPowerLevel - return own power level as max power level value when otherUserId == me`() =
        runTest {
            roomUserStore.update(me, roomId) { aliceRoomUser() }
            roomStateStore.save(powerLevelsEvent)
            cut.canSetPowerLevelToMax(roomId, me).first() shouldBe PowerLevel.User(55)
        }

    @Test
    fun `canSetPowerLevelToMax - oldUserPowerLevel == ownPowerLevel - return own power level as max power level value when all criteria are met`() =
        runTest {
            roomUserStore.update(alice, roomId) { aliceRoomUser() }
            val powerLevelsEvent = powerLevelsEvent(
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
            cut.canSetPowerLevelToMax(roomId, alice).first() shouldBe PowerLevel.User(55)
        }


    @Test
    fun `canRedactEvent - return false when not member of room`() = runTest {
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
    fun `canRedactEvent - return true if it is the event of the user and the user's power level is at least as high as the needed event redaction level`() =
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
    fun `canRedactEvent - return true if it is the event of another user but the user's power level is at least as high as the needed redaction power level`() =
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
    fun `canRedactEvent - return false if the user has no high enough power level for event redactions`() =
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
    fun `canRedactEvent - return false if the user has no high enough power level for redactions of events of other users`() =
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
    fun `canRedactEvent - not allow to redact an already redacted event`() = runTest {
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
    fun `canRedactEvent - react to changes in the power levels - react to changes in the user's power levels`() =
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
    fun `getUserPresence - no presence in store - should be null`() = runTest {
        cut.getPresence(alice).first() shouldBe null
    }

    @Test
    fun `getUserPresence - sync is not running - should be null`() = runTest {
        val lastActive = Instant.fromEpochMilliseconds(24)
        currentSyncState.value = SyncState.STARTED
        userPresenceStore.setPresence(alice, UserPresence(Presence.ONLINE, clock.now(), lastActive))
        cut.getPresence(alice).first() shouldBe null
    }

    @Test
    fun `getUserPresence - is currently active - should pass from store`() = runTest {
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
    fun `getUserPresence - last active is below threshold - should pass from store and make null later`() =
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

            result.value shouldBe null

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
    fun `getUserPresence - last active is below threshold - should fallback to last update`() =
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

            result.value shouldBe null
        }

    @Test
    fun `getUserPresence - last active is above threshold - should be null`() =
        runTest {
            val presence =
                UserPresence(
                    presence = Presence.ONLINE,
                    lastUpdate = clock.now() - 6.minutes,
                    statusMessage = "status"
                )
            userPresenceStore.setPresence(alice, presence)
            cut.getPresence(alice).first() shouldBe null
        }

    private suspend fun canKickAlice(
        ownLevel: Long,
        otherLevel: Long,
        kickLevel: Long = PowerLevelsEventContent.KICK_DEFAULT,
        ownMembership: Membership = JOIN,
        otherMembership: Membership = JOIN,
        expect: Boolean,
    ) {
        val powerLevelsEvent = powerLevelsEvent(
            PowerLevelsEventContent(
                users = mapOf(
                    me to ownLevel,
                    alice to otherLevel
                ), kick = kickLevel
            )
        )
        roomStateStore.save(powerLevelsEvent)
        roomStore.update(roomId) { Room(roomId, membership = ownMembership) }
        roomUserStore.update(alice, roomId) { aliceRoomUser(otherMembership) }
        cut.canKickUser(roomId, alice).first() shouldBe expect
    }

    private suspend fun canBanAlice(
        ownLevel: Long,
        otherLevel: Long,
        banLevel: Long = PowerLevelsEventContent.BAN_DEFAULT,
        ownMembership: Membership = JOIN,
        otherMembership: Membership = JOIN,
        expect: Boolean,
    ) {
        val powerLevelsEvent = powerLevelsEvent(
            PowerLevelsEventContent(
                users = mapOf(
                    me to ownLevel,
                    alice to otherLevel
                ), ban = banLevel
            )
        )
        roomStateStore.save(powerLevelsEvent)
        roomStore.update(roomId) { Room(roomId, membership = ownMembership) }
        roomUserStore.update(alice, roomId) { aliceRoomUser(otherMembership) }
        cut.canBanUser(roomId, alice).first() shouldBe expect
    }

    private suspend fun canInvite(
        ownLevel: Long,
        ownMembership: Membership = JOIN,
        inviteLevel: Long = PowerLevelsEventContent.INVITE_DEFAULT,
        expect: Boolean,
    ) {
        val powerLevelsEvent = powerLevelsEvent(
            PowerLevelsEventContent(
                users = mapOf(
                    me to ownLevel,
                ), invite = inviteLevel
            )
        )
        roomStateStore.save(powerLevelsEvent)
        roomStore.update(roomId) { Room(roomId, membership = ownMembership) }
        cut.canInvite(roomId).first() shouldBe expect
    }

    private suspend fun canInviteUser(
        ownLevel: Long,
        inviteLevel: Long = PowerLevelsEventContent.INVITE_DEFAULT,
        ownMembership: Membership = JOIN,
        otherMembership: Membership = JOIN,
        expect: Boolean,
    ) {
        val powerLevelsEvent = powerLevelsEvent(
            PowerLevelsEventContent(
                users = mapOf(
                    me to ownLevel,
                ), invite = inviteLevel
            )
        )
        roomStateStore.save(powerLevelsEvent)
        roomStore.update(roomId) { Room(roomId, membership = ownMembership) }
        roomUserStore.update(alice, roomId) { aliceRoomUser(otherMembership) }
        cut.canInviteUser(roomId, alice).first() shouldBe expect
    }

    private suspend fun KickLevels.canUnbanAlice(
        otherLevel: Long,
        ownMembership: Membership = JOIN,
        otherMembership: Membership = JOIN,
        expect: Boolean
    ) {
        roomUserStore.update(alice, roomId) { aliceRoomUser(BAN) }

        val powerLevelsEvent =
            powerLevelsEvent(
                PowerLevelsEventContent(
                    users = mapOf(
                        me to myLevel,
                        alice to otherLevel
                    ), kick = kickLevel,
                    ban = banLevel
                )
            )

        roomStateStore.save(powerLevelsEvent)
        roomStore.update(roomId) { Room(roomId, membership = ownMembership) }
        roomUserStore.update(alice, roomId) { aliceRoomUser(otherMembership) }
        cut.canUnbanUser(roomId, alice).first() shouldBe expect
    }

    private fun createEvent(
        creator: UserId,
        roomVersion: String? = null,
        additionalCreators: Set<UserId> = emptySet()
    ) =
        StateEvent(
            CreateEventContent(roomVersion = roomVersion, additionalCreators = additionalCreators),
            EventId("\$event"),
            creator,
            roomId,
            1234,
            stateKey = ""
        )

    private fun powerLevelsEvent(powerLevelsEventContent: PowerLevelsEventContent) = StateEvent(
        powerLevelsEventContent,
        EventId("\$event"),
        me,
        roomId,
        1234,
        stateKey = ""
    )
}