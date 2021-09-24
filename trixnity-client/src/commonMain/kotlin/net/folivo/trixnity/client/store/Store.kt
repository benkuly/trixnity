package net.folivo.trixnity.client.store

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.folivo.trixnity.core.model.MatrixId.*
import net.folivo.trixnity.core.model.crypto.DeviceKeys
import net.folivo.trixnity.core.model.crypto.Key.Curve25519Key
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent.Membership
import kotlin.reflect.KClass

interface Store {
    suspend fun clear()

    val server: ServerStore
    val account: AccountStore
    val rooms: RoomsStore
    val deviceKeys: DeviceKeysStores
    val olm: OlmStore
    val media: MediaStore

    interface ServerStore {
        val hostname: String
        val port: Int
        val secure: Boolean
    }

    interface AccountStore {
        val userId: MutableStateFlow<UserId?>
        val deviceId: MutableStateFlow<String?>
        val accessToken: MutableStateFlow<String?>
        val syncBatchToken: MutableStateFlow<String?>
        val filterId: MutableStateFlow<String?>
    }

    interface RoomsStore {
        val state: RoomStateStore
        val timeline: RoomTimelineStore
        val users: RoomUserStore

        suspend fun all(): StateFlow<Set<Room>>
        suspend fun byId(roomId: RoomId): StateFlow<Room?>
        suspend fun update(roomId: RoomId, updater: suspend (oldRoom: Room?) -> Room?): StateFlow<Room?>

        interface RoomStateStore {
            suspend fun update(event: Event<out StateEventContent>)
            suspend fun updateAll(events: List<Event.StateEvent<out StateEventContent>>)
            suspend fun <C : StateEventContent> allById(
                roomId: RoomId,
                eventContentClass: KClass<C>
            ): StateFlow<Map<String, Event<C>>>

            suspend fun <C : StateEventContent> allById(
                roomId: RoomId,
                stateKey: String,
                eventContentClass: KClass<C>
            ): StateFlow<Event<C>?>
        }

        interface RoomTimelineStore {
            suspend fun update(
                eventId: EventId,
                roomId: RoomId,
                updater: suspend (oldTimelineEvent: TimelineEvent?) -> TimelineEvent?
            ): TimelineEvent?

            suspend fun updateAll(events: List<TimelineEvent>)
            suspend fun byId(eventId: EventId, roomId: RoomId): StateFlow<TimelineEvent?>
        }

        interface RoomUserStore {
            suspend fun all(): StateFlow<Set<RoomUser>>
            suspend fun byId(userId: UserId, roomId: RoomId): StateFlow<RoomUser?>
            suspend fun update(
                userId: UserId,
                roomId: RoomId,
                updater: suspend (oldRoomUser: RoomUser?) -> RoomUser?
            ): StateFlow<RoomUser?>

            suspend fun byOriginalNameAndMembership(
                originalName: String,
                membership: Set<Membership>,
                roomId: RoomId
            ): Set<UserId>
        }
    }

    interface DeviceKeysStores {
        suspend fun byUserId(userId: UserId): MutableStateFlow<Map<String, DeviceKeys>?>
        val outdatedKeys: MutableStateFlow<Set<UserId>>
    }

    interface OlmStore {
        // it is important, that this key is stored in secure location! Changing this value is not that easy, because
        // we need to encrypt every pickled object with the new key.
        val pickleKey: String
        val account: MutableStateFlow<String?>
        suspend fun olmSessions(senderKey: Curve25519Key): MutableStateFlow<Set<StoredOlmSession>>
        suspend fun inboundMegolmSession(
            roomId: RoomId,
            sessionId: String,
            senderKey: Curve25519Key
        ): MutableStateFlow<StoredOlmInboundMegolmSession?>

        suspend fun inboundMegolmMessageIndex(
            roomId: RoomId,
            sessionId: String,
            senderKey: Curve25519Key,
            messageIndex: Long
        ): MutableStateFlow<StoredMegolmMessageIndex?>

        suspend fun outboundMegolmSession(roomId: RoomId): MutableStateFlow<StoredOutboundMegolmSession?>
    }

    interface MediaStore {
        // TODO this should use Source or something similar streaming bytes.
        suspend fun add(uri: String, media: ByteArray)
        suspend fun byUri(uri: String): ByteArray?
    }
}