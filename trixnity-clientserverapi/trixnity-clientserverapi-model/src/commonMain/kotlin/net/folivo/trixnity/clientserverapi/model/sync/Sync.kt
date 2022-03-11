package net.folivo.trixnity.clientserverapi.model.sync

import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.resources.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.PresenceEventContent
import net.folivo.trixnity.core.model.keys.KeyAlgorithm

@Serializable
@Resource("/_matrix/client/v3/sync")
data class Sync(
    @SerialName("filter") val filter: String? = null,
    @SerialName("full_state") val fullState: Boolean? = null,
    @SerialName("set_presence") val setPresence: PresenceEventContent.Presence? = null,
    @SerialName("since") val since: String? = null,
    @SerialName("timeout") val timeout: Long? = null,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixJsonEndpoint<Unit, Sync.Response>() {
    @Transient
    override val method = Get

    @Serializable
    data class Response(
        @SerialName("next_batch") val nextBatch: String,
        @SerialName("rooms") val room: Rooms? = null,
        @SerialName("presence") val presence: Presence? = null,
        @SerialName("account_data") val accountData: GlobalAccountData? = null,
        @SerialName("to_device") val toDevice: ToDevice? = null,
        @SerialName("device_lists") val deviceLists: DeviceLists? = null,
        @SerialName("device_one_time_keys_count") val deviceOneTimeKeysCount: DeviceOneTimeKeysCount? = null
    ) {
        @Serializable
        data class Rooms(
            @SerialName("knock") val knock: Map<RoomId, KnockedRoom>? = null,
            @SerialName("join") val join: Map<RoomId, JoinedRoom>? = null,
            @SerialName("invite") val invite: Map<RoomId, InvitedRoom>? = null,
            @SerialName("leave") val leave: Map<RoomId, LeftRoom>? = null
        ) {
            @Serializable
            data class KnockedRoom(
                @SerialName("knock_state") val knockState: InviteState? = null
            ) {
                @Serializable
                data class InviteState(
                    @SerialName("events") val events: List<@Contextual Event.StrippedStateEvent<*>>? = null
                )
            }

            @Serializable
            data class JoinedRoom(
                @SerialName("summary") val summary: RoomSummary? = null,
                @SerialName("state") val state: State? = null,
                @SerialName("timeline") val timeline: Timeline? = null,
                @SerialName("ephemeral") val ephemeral: Ephemeral? = null,
                @SerialName("account_data") val accountData: RoomAccountData? = null,
                @SerialName("unread_notifications") val unreadNotifications: UnreadNotificationCounts? = null
            ) {
                @Serializable
                data class RoomSummary(
                    @SerialName("m.heroes") val heroes: List<UserId>? = null,
                    @SerialName("m.joined_member_count") val joinedMemberCount: Int? = null,
                    @SerialName("m.invited_member_count") val invitedMemberCount: Int? = null
                )

                @Serializable
                data class Ephemeral(
                    @SerialName("events") val events: List<@Contextual Event.EphemeralEvent<*>>? = null
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
                    @SerialName("events") val events: List<@Contextual Event.StrippedStateEvent<*>>? = null
                )
            }

            @Serializable
            data class LeftRoom(
                @SerialName("state") val state: State? = null,
                @SerialName("timeline") val timeline: Timeline? = null,
                @SerialName("account_data") val accountData: RoomAccountData? = null
            )

            @Serializable
            data class State(
                @SerialName("events") val events: List<@Contextual Event.StateEvent<*>>? = null
            )

            @Serializable
            data class Timeline(
                @SerialName("events") val events: List<@Contextual Event.RoomEvent<*>>? = null,
                @SerialName("limited") val limited: Boolean? = null,
                @SerialName("prev_batch") val previousBatch: String? = null
            )

            @Serializable
            data class RoomAccountData(
                @SerialName("events") val events: List<@Contextual Event.RoomAccountDataEvent<*>>? = null
            )
        }

        @Serializable
        data class Presence(
            @SerialName("events") val events: List<@Contextual Event.EphemeralEvent<PresenceEventContent>>? = null
        )

        @Serializable
        data class GlobalAccountData(
            @SerialName("events") val events: List<@Contextual Event.GlobalAccountDataEvent<*>>? = null
        )

        @Serializable
        data class DeviceLists(
            @SerialName("changed") val changed: Set<UserId>? = null,
            @SerialName("left") val left: Set<UserId>? = null
        )

        @Serializable
        data class ToDevice(
            @SerialName("events") val events: List<@Contextual Event.ToDeviceEvent<*>>? = null
        )
    }
}

typealias DeviceOneTimeKeysCount = Map<KeyAlgorithm, Int>
