package net.folivo.trixnity.appservice.rest.api.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.MatrixId.RoomId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.*

@Serializable
data class SyncResponse(
    @SerialName("next_batch") val nextBatch: String,
    @SerialName("rooms") val room: Rooms,
    @SerialName("presence") val presence: Presence,
    @SerialName("account_data") val accountData: AccountData,
    @SerialName("to_device") val toDevice: ToDevice,
    @SerialName("device_lists") val deviceLists: DeviceLists,
    @SerialName("device_one_time_keys_count") val deviceOneTimeKeysCount: Map<String, Int>
) {
    @Serializable
    data class Rooms(
        @SerialName("join") val join: Map<RoomId, JoinedRoom>,
        @SerialName("invite") val invite: Map<RoomId, InvitedRoom>,
        @SerialName("leave") val leave: Map<RoomId, LeftRoom>
    ) {
        @Serializable
        data class JoinedRoom(
            @SerialName("summary") val summary: RoomSummary,
            @SerialName("state") val state: State,
            @SerialName("timeline") val timeline: Timeline,
            @SerialName("ephemeral") val ephemeral: Ephemeral,
            @SerialName("account_data") val accountData: AccountData,
            @SerialName("unread_notifications") val unreadNotifications: UnreadNotificationCounts
        ) {
            @Serializable
            data class RoomSummary(
                @SerialName("m.heroes") val heroes: List<String>? = null,
                @SerialName("m.joined_member_count") val joinedMemberCount: Int,
                @SerialName("m.invited_member_count") val invitedMemberCount: Int
            )

            @Serializable
            data class Ephemeral(
                @SerialName("events") val events: List<Event<*>>
            )

            @Serializable
            data class UnreadNotificationCounts(
                @SerialName("highlight_count") val highlightCount: Int,
                @SerialName("notification_count") val notificationCount: Int
            )
        }

        @Serializable
        data class InvitedRoom(
            @SerialName("invite_state") val inviteState: InviteState
        ) {
            @Serializable
            data class InviteState(
                @SerialName("events") val events: List<StrippedStateEvent<*>>
            )
        }

        @Serializable
        data class LeftRoom(
            @SerialName("state") val state: State,
            @SerialName("timeline") val timeline: Timeline,
            @SerialName("account_data") val accountData: AccountData
        )

        @Serializable
        data class State(
            @SerialName("events") val events: List<StateEvent<*>>
        )

        @Serializable
        data class Timeline(
            @SerialName("events") val events: List<RoomEvent<*>>,
            @SerialName("limited") val limited: Boolean,
            @SerialName("prev_batch") val previousBatch: String
        )
    }

    @Serializable
    data class Presence(
        @SerialName("events") val events: List<Event<*>>
    )

    @Serializable
    data class AccountData(
        @SerialName("events") val events: List<Event<*>>
    )

    @Serializable
    data class DeviceLists(
        @SerialName("changed") val changed: List<String>,
        @SerialName("left") val left: List<String>
    )

    @Serializable
    data class ToDevice(
        @SerialName("events") val events: List<Event<*>>
    )
}