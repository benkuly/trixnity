package net.folivo.trixnity.clientserverapi.client

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.reflect.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import net.folivo.trixnity.clientserverapi.model.users.*
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent
import net.folivo.trixnity.core.model.events.ToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.Presence
import net.folivo.trixnity.core.model.events.m.PresenceEventContent
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.contentType
import net.folivo.trixnity.utils.nextString
import kotlin.random.Random

interface UserApiClient {
    val contentMappings: EventContentSerializerMappings

    /**
     * @see [GetDisplayName]
     */
    suspend fun getDisplayName(
        userId: UserId,
    ): Result<String?>

    /**
     * @see [SetDisplayName]
     */
    suspend fun setDisplayName(
        userId: UserId,
        displayName: String? = null,
        asUserId: UserId? = null
    ): Result<Unit>

    /**
     * @see [GetAvatarUrl]
     */
    suspend fun getAvatarUrl(
        userId: UserId,
    ): Result<String?>

    /**
     * @see [SetAvatarUrl]
     */
    suspend fun setAvatarUrl(
        userId: UserId,
        avatarUrl: String?,
        asUserId: UserId? = null,
    ): Result<Unit>

    /**
     * @see [GetProfile]
     */
    suspend fun getProfile(
        userId: UserId,
    ): Result<GetProfile.Response>

    /**
     * @see [GetPresence]
     */
    suspend fun getPresence(
        userId: UserId,
        asUserId: UserId? = null
    ): Result<PresenceEventContent>

    /**
     * @see [SetPresence]
     */
    suspend fun setPresence(
        userId: UserId,
        presence: Presence,
        statusMessage: String? = null,
        asUserId: UserId? = null
    ): Result<Unit>

    /**
     * @see [SendToDevice]
     */
    suspend fun <C : ToDeviceEventContent> sendToDeviceUnsafe(
        events: Map<UserId, Map<String, C>>,
        transactionId: String = Random.nextString(22),
        asUserId: UserId? = null
    ): Result<Unit>

    /**
     * @see [SendToDevice]
     */
    suspend fun sendToDeviceUnsafe(
        type: String,
        events: Map<UserId, Map<String, ToDeviceEventContent>>,
        transactionId: String = Random.nextString(22),
        asUserId: UserId? = null
    ): Result<Unit>

    /**
     * This splits [events] into multiple requests, when they have a different type
     * (for example a mix of encrypted and unencrypted events).
     *
     * @see [SendToDevice]
     */
    suspend fun sendToDevice(
        events: Map<UserId, Map<String, ToDeviceEventContent>>,
        asUserId: UserId? = null
    ): Result<Unit>

    /**
     * @see [GetFilter]
     */
    suspend fun getFilter(
        userId: UserId,
        filterId: String,
        asUserId: UserId? = null
    ): Result<Filters>

    /**
     * @see [SetFilter]
     */
    suspend fun setFilter(
        userId: UserId,
        filters: Filters,
        asUserId: UserId? = null
    ): Result<String>

    /**
     * @see [GetGlobalAccountData]
     */
    suspend fun getAccountData(
        type: String,
        userId: UserId,
        key: String = "",
        asUserId: UserId? = null
    ): Result<GlobalAccountDataEventContent>

    /**
     * @see [SetGlobalAccountData]
     */
    suspend fun setAccountData(
        content: GlobalAccountDataEventContent,
        userId: UserId,
        key: String = "",
        asUserId: UserId? = null
    ): Result<Unit>

    /**
     *  @see [SearchUsers]
     */
    suspend fun searchUsers(
        searchTerm: String,
        acceptLanguage: String,
        limit: Long? = 10,
        asUserId: UserId? = null,
    ): Result<SearchUsers.Response>
}

class UserApiClientImpl(
    private val baseClient: MatrixClientServerApiBaseClient,
    override val contentMappings: EventContentSerializerMappings
) : UserApiClient {

    override suspend fun getDisplayName(
        userId: UserId,
    ): Result<String?> =
        baseClient.request(GetDisplayName(userId)).mapCatching { it.displayName }

    override suspend fun setDisplayName(
        userId: UserId,
        displayName: String?,
        asUserId: UserId?
    ): Result<Unit> =
        baseClient.request(SetDisplayName(userId, asUserId), SetDisplayName.Request(displayName))

    override suspend fun getAvatarUrl(
        userId: UserId,
    ): Result<String?> =
        baseClient.request(GetAvatarUrl(userId)).mapCatching { it.avatarUrl }

    override suspend fun setAvatarUrl(
        userId: UserId,
        avatarUrl: String?,
        asUserId: UserId?,
    ): Result<Unit> =
        baseClient.request(SetAvatarUrl(userId, asUserId), SetAvatarUrl.Request(avatarUrl))

    override suspend fun getProfile(
        userId: UserId,
    ): Result<GetProfile.Response> =
        baseClient.request(GetProfile(userId))

    override suspend fun getPresence(
        userId: UserId,
        asUserId: UserId?
    ): Result<PresenceEventContent> =
        baseClient.request(GetPresence(userId, asUserId))

    override suspend fun setPresence(
        userId: UserId,
        presence: Presence,
        statusMessage: String?,
        asUserId: UserId?
    ): Result<Unit> =
        baseClient.request(SetPresence(userId, asUserId), SetPresence.Request(presence, statusMessage))


    override suspend fun <C : ToDeviceEventContent> sendToDeviceUnsafe(
        events: Map<UserId, Map<String, C>>,
        transactionId: String,
        asUserId: UserId?
    ): Result<Unit> {
        val firstEventForType = events.entries.firstOrNull()?.value?.entries?.firstOrNull()?.value
        requireNotNull(firstEventForType) { "you need to send at least on event" }
        require(events.flatMap { it.value.values }
            .all { it.instanceOf(firstEventForType::class) }) { "all events must be of the same type" }
        val type = contentMappings.toDevice.contentType(firstEventForType)
        return sendToDeviceUnsafe(type, events, transactionId, asUserId)
    }

    override suspend fun sendToDeviceUnsafe(
        type: String,
        events: Map<UserId, Map<String, ToDeviceEventContent>>,
        transactionId: String,
        asUserId: UserId?
    ): Result<Unit> =
        baseClient.request(SendToDevice(type, transactionId, asUserId), SendToDevice.Request(events))

    override suspend fun sendToDevice(
        events: Map<UserId, Map<String, ToDeviceEventContent>>,
        asUserId: UserId?,
    ): Result<Unit> = runCatching {
        data class FlatEntry(
            val userId: UserId,
            val deviceId: String,
            val event: ToDeviceEventContent,
        )

        val flatEvents = events.flatMap { (userId, deviceEvents) ->
            deviceEvents.map { (deviceId, deviceEvent) ->
                FlatEntry(userId, deviceId, deviceEvent)
            }
        }
        if (flatEvents.isNotEmpty()) {
            val eventsByType = flatEvents
                .groupBy { it.event::class }
                .mapValues { (_, flatEntryByUserId) ->
                    flatEntryByUserId.groupBy { it.userId }
                        .mapValues { (_, flatEntryByDeviceId) ->
                            flatEntryByDeviceId.associate { it.deviceId to it.event }
                        }
                }
            coroutineScope {
                eventsByType.values.forEach {
                    launch {
                        sendToDeviceUnsafe(it, asUserId = asUserId).getOrThrow()
                    }
                }
            }
        }
    }

    override suspend fun getFilter(
        userId: UserId,
        filterId: String,
        asUserId: UserId?
    ): Result<Filters> =
        baseClient.request(GetFilter(userId, filterId, asUserId))

    override suspend fun setFilter(
        userId: UserId,
        filters: Filters,
        asUserId: UserId?
    ): Result<String> =
        baseClient.request(SetFilter(userId, asUserId), filters).mapCatching { it.filterId }

    override suspend fun getAccountData(
        type: String,
        userId: UserId,
        key: String,
        asUserId: UserId?
    ): Result<GlobalAccountDataEventContent> {
        val actualType = if (key.isEmpty()) type else type + key
        return baseClient.request(GetGlobalAccountData(userId, actualType, asUserId))
    }

    override suspend fun setAccountData(
        content: GlobalAccountDataEventContent,
        userId: UserId,
        key: String,
        asUserId: UserId?
    ): Result<Unit> {
        val eventType = contentMappings.globalAccountData.contentType(content)
            .let { type -> if (key.isEmpty()) type else type + key }

        return baseClient.request(SetGlobalAccountData(userId, eventType, asUserId), content)
    }

    override suspend fun searchUsers(
        searchTerm: String,
        acceptLanguage: String,
        limit: Long?,
        asUserId: UserId?,
    ): Result<SearchUsers.Response> =
        baseClient.request(SearchUsers(asUserId), SearchUsers.Request(searchTerm, limit)) {
            header(HttpHeaders.AcceptLanguage, acceptLanguage)
        }
}

/**
 * @see [GetGlobalAccountData]
 */
suspend inline fun <reified C : GlobalAccountDataEventContent> UserApiClient.getAccountData(
    userId: UserId,
    key: String = "",
    asUserId: UserId? = null
): Result<C> {
    val type = contentMappings.globalAccountData.contentType(C::class)
    @Suppress("UNCHECKED_CAST")
    return getAccountData(type, userId, key, asUserId) as Result<C>
}