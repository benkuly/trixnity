package net.folivo.trixnity.clientserverapi.model.server

import io.ktor.resources.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.clientserverapi.model.user.Filters
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#post_matrixclientv3search">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/search")
@HttpMethod(POST)
data class Search(
    @SerialName("next_batch") val nextBatch: String? = null,
) : MatrixEndpoint<Search.Request, Search.Response> {
    @Serializable
    data class Request(
        @SerialName("search_categories")
        val searchCategories: Categories,
    ) {
        @Serializable
        data class Categories(
            @SerialName("room_events") val roomEvents: RoomEventsCriteria? = null,
        ) {
            @Serializable
            data class RoomEventsCriteria(
                @SerialName("event_context") val eventContext: IncludeEventContext? = null,
                @SerialName("filter") val filter: Filters.RoomFilter.RoomEventFilter? = null,
                @SerialName("groupings") val groupings: Groupings? = null,
                @SerialName("include_state") val includeState: Boolean? = null,
                @SerialName("keys") val keys: Set<String>? = null,
                @SerialName("order_by") val orderBy: Ordering? = null,
                @SerialName("search_term") val searchTerm: String
            ) {
                @Serializable
                data class IncludeEventContext(
                    @SerialName("after_limit") val afterLimit: Long? = null,
                    @SerialName("before_limit") val beforeLimit: Long? = null,
                    @SerialName("include_profile") val include_profile: Long? = null,
                )

                @Serializable
                data class Groupings(
                    @SerialName("group_by") val groupBy: Set<Groups>? = null
                ) {
                    @Serializable
                    data class Groups(
                        @SerialName("key") val key: String? = null
                    )
                }

                @Serializable
                enum class Ordering {
                    @SerialName("recent")
                    RECENT,

                    @SerialName("rank")
                    RANK,
                }
            }
        }
    }

    @Serializable
    data class Response(
        @SerialName("search_categories") val searchCategories: ResultCategories
    ) {
        @Serializable
        data class ResultCategories(
            @SerialName("room_events") val roomEvents: RoomEventsResult? = null,
        ) {
            @Serializable
            data class RoomEventsResult(
                @SerialName("count") val count: Long? = null,
                @SerialName("groups") val groups: Map<String, Map<String, GroupValue>>? = null,
                @SerialName("highlights") val highlights: Set<String>? = null,
                @SerialName("next_batch") val nextBatch: String? = null,
                @SerialName("results") val results: List<Results>? = null,
                @SerialName("state") val state: Map<RoomId, Set<@Contextual StateEvent<*>>>? = null,
            ) {
                @Serializable
                data class GroupValue(
                    @SerialName("next_batch") val nextBatch: String? = null,
                    @SerialName("order") val order: Long? = null,
                    @SerialName("results") val results: List<String>? = null
                )

                @Serializable
                data class Results(
                    @SerialName("context") val context: EventContext? = null,
                    @SerialName("rank") val rank: Double? = null,
                    @SerialName("result") val result: @Contextual RoomEvent<*>? = null
                ) {
                    @Serializable
                    data class EventContext(
                        @SerialName("end") val end: String? = null,
                        @SerialName("events_after") val eventsAfter: List<@Contextual RoomEvent<*>>? = null,
                        @SerialName("events_before") val eventsBefore: List<@Contextual RoomEvent<*>>? = null,
                        @SerialName("profile_info") val profileInfo: Map<UserId, UserProfile>? = null,
                        @SerialName("start") val start: String? = null
                    ) {
                        @Serializable
                        data class UserProfile(
                            @SerialName("avatar_url") val avatarUrl: String? = null,
                            @SerialName("displayname") val displayName: String? = null
                        )
                    }
                }
            }
        }
    }
}