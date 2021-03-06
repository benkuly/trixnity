package net.folivo.trixnity.client.rest.api.sync

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.MatrixId.RoomId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.*

@Serializable
data class SyncResponse(
    @SerialName("next_batch") val nextBatch: String,
    @SerialName("rooms") val room: Rooms? = null,
    @SerialName("presence") val presence: Presence? = null,
    @SerialName("account_data") val accountData: AccountData? = null,
    @SerialName("to_device") val toDevice: ToDevice? = null,
    @SerialName("device_lists") val deviceLists: DeviceLists? = null,
    @SerialName("device_one_time_keys_count") val deviceOneTimeKeysCount: Map<String, Int>? = null
) {
    @Serializable
    data class Rooms(
        @SerialName("join") val join: Map<RoomId, JoinedRoom>? = null,
        @SerialName("invite") val invite: Map<RoomId, InvitedRoom>? = null,
        @SerialName("leave") val leave: Map<RoomId, LeftRoom>? = null
    ) {
        @Serializable
        data class JoinedRoom(
            @SerialName("summary") val summary: RoomSummary? = null,
            @SerialName("state") val state: State? = null,
            @SerialName("timeline") val timeline: Timeline? = null,
            @SerialName("ephemeral") val ephemeral: Ephemeral? = null,
            @SerialName("account_data") val accountData: AccountData? = null,
            @SerialName("unread_notifications") val unreadNotifications: UnreadNotificationCounts? = null
        ) {
            @Serializable
            data class RoomSummary(
                @SerialName("m.heroes") val heroes: List<String>? = null,
                @SerialName("m.joined_member_count") val joinedMemberCount: Int? = null,
                @SerialName("m.invited_member_count") val invitedMemberCount: Int? = null
            )

            @Serializable
            data class Ephemeral(
                @SerialName("events") val events: List<@Contextual Event<*>>? = null
            )

            @Serializable
            data class UnreadNotificationCounts(
                @SerialName("highlight_count") val highlightCount: Int? = null,
                @SerialName("notification_count") val notificationCount: Int? = null
            )
        }

        @Serializable
        data class InvitedRoom(
            @SerialName("invite_state") val inviteState: InviteState? = null
        ) {
            @Serializable
            data class InviteState(
                @SerialName("events") val events: List<@Contextual StrippedStateEvent<*>>? = null
            )
        }

        @Serializable
        data class LeftRoom(
            @SerialName("state") val state: State? = null,
            @SerialName("timeline") val timeline: Timeline? = null,
            @SerialName("account_data") val accountData: AccountData? = null
        )

        @Serializable
        data class State(
            @SerialName("events") val events: List<@Contextual StateEvent<*>>? = null
        )

        @Serializable
        data class Timeline(
            @SerialName("events") val events: List<@Contextual RoomEvent<*>>? = null,
            @SerialName("limited") val limited: Boolean? = null,
            @SerialName("prev_batch") val previousBatch: String? = null
        )
    }

    @Serializable
    data class Presence(
        @SerialName("events") val events: List<@Contextual Event<*>>? = null
    )

    @Serializable
    data class AccountData(
        @SerialName("events") val events: List<@Contextual Event<*>>? = null
    )

    @Serializable
    data class DeviceLists(
        @SerialName("changed") val changed: List<String>? = null,
        @SerialName("left") val left: List<String>? = null
    )

    @Serializable
    data class ToDevice(
        @SerialName("events") val events: List<@Contextual Event<*>>? = null
    )
}