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
     * @see [GetProfileField]
     */
    suspend fun getProfileField(
        userId: UserId,
        key: ProfileField.Key<*>,
    ): Result<ProfileField>

    /**
     * @see [SetProfileField]
     */
    suspend fun setProfileField(
        userId: UserId,
        field: ProfileField,
    ): Result<Unit>

    /**
     * @see [DeleteProfileField]
     */
    suspend fun deleteProfileField(
        userId: UserId,
        key: ProfileField.Key<*>,
    ): Result<Unit>

    /**
     * @see [GetProfile]
     */
    suspend fun getProfile(
        userId: UserId,
    ): Result<Profile>

    /**
     * @see [GetPresence]
     */
    suspend fun getPresence(
        userId: UserId,
    ): Result<PresenceEventContent>

    /**
     * @see [SetPresence]
     */
    suspend fun setPresence(
        userId: UserId,
        presence: Presence,
        statusMessage: String? = null,
    ): Result<Unit>

    /**
     * @see [SendToDevice]
     */
    suspend fun <C : ToDeviceEventContent> sendToDeviceUnsafe(
        events: Map<UserId, Map<String, C>>,
        transactionId: String = Random.nextString(22),
    ): Result<Unit>

    /**
     * @see [SendToDevice]
     */
    suspend fun sendToDeviceUnsafe(
        type: String,
        events: Map<UserId, Map<String, ToDeviceEventContent>>,
        transactionId: String = Random.nextString(22),
    ): Result<Unit>

    /**
     * This splits [events] into multiple requests, when they have a different type
     * (for example a mix of encrypted and unencrypted events).
     *
     * @see [SendToDevice]
     */
    suspend fun sendToDevice(
        events: Map<UserId, Map<String, ToDeviceEventContent>>,
    ): Result<Unit>

    /**
     * @see [GetFilter]
     */
    suspend fun getFilter(
        userId: UserId,
        filterId: String,
    ): Result<Filters>

    /**
     * @see [SetFilter]
     */
    suspend fun setFilter(
        userId: UserId,
        filters: Filters,
    ): Result<String>

    /**
     * @see [GetGlobalAccountData]
     */
    suspend fun getAccountData(
        type: String,
        userId: UserId,
        key: String = "",
    ): Result<GlobalAccountDataEventContent>

    /**
     * @see [SetGlobalAccountData]
     */
    suspend fun setAccountData(
        content: GlobalAccountDataEventContent,
        userId: UserId,
        key: String = "",
    ): Result<Unit>

    /**
     *  @see [SearchUsers]
     */
    suspend fun searchUsers(
        searchTerm: String,
        acceptLanguage: String,
        limit: Long? = 10,
    ): Result<SearchUsers.Response>

    /**
     * @see [ReportUser]
     */
    suspend fun reportUser(
        userId: UserId,
        reason: String,
    ): Result<Unit>
}

class UserApiClientImpl(
    private val baseClient: MatrixClientServerApiBaseClient,
    override val contentMappings: EventContentSerializerMappings
) : UserApiClient {

    override suspend fun getProfileField(
        userId: UserId,
        key: ProfileField.Key<*>,
    ): Result<ProfileField> =
        baseClient.request(GetProfileField(userId, key))

    override suspend fun setProfileField(
        userId: UserId,
        field: ProfileField,
    ): Result<Unit> =
        baseClient.request(SetProfileField(userId, field.key), field)

    override suspend fun deleteProfileField(
        userId: UserId,
        key: ProfileField.Key<*>,
    ): Result<Unit> =
        baseClient.request(DeleteProfileField(userId, key))

    override suspend fun getProfile(
        userId: UserId,
    ): Result<Profile> =
        baseClient.request(GetProfile(userId))

    override suspend fun getPresence(
        userId: UserId,
    ): Result<PresenceEventContent> =
        baseClient.request(GetPresence(userId))

    override suspend fun setPresence(
        userId: UserId,
        presence: Presence,
        statusMessage: String?,
    ): Result<Unit> =
        baseClient.request(SetPresence(userId), SetPresence.Request(presence, statusMessage))


    override suspend fun <C : ToDeviceEventContent> sendToDeviceUnsafe(
        events: Map<UserId, Map<String, C>>,
        transactionId: String,
    ): Result<Unit> {
        val firstEventForType = events.entries.firstOrNull()?.value?.entries?.firstOrNull()?.value
        requireNotNull(firstEventForType) { "you need to send at least on event" }
        require(events.flatMap { it.value.values }
            .all { it.instanceOf(firstEventForType::class) }) { "all events must be of the same type" }
        val type = contentMappings.toDevice.contentType(firstEventForType)
        return sendToDeviceUnsafe(type, events, transactionId)
    }

    override suspend fun sendToDeviceUnsafe(
        type: String,
        events: Map<UserId, Map<String, ToDeviceEventContent>>,
        transactionId: String,
    ): Result<Unit> =
        baseClient.request(SendToDevice(type, transactionId), SendToDevice.Request(events))

    override suspend fun sendToDevice(
        events: Map<UserId, Map<String, ToDeviceEventContent>>,
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
                        sendToDeviceUnsafe(it).getOrThrow()
                    }
                }
            }
        }
    }

    override suspend fun getFilter(
        userId: UserId,
        filterId: String,
    ): Result<Filters> =
        baseClient.request(GetFilter(userId, filterId))

    override suspend fun setFilter(
        userId: UserId,
        filters: Filters,
    ): Result<String> =
        baseClient.request(SetFilter(userId), filters).mapCatching { it.filterId }

    override suspend fun getAccountData(
        type: String,
        userId: UserId,
        key: String,
    ): Result<GlobalAccountDataEventContent> {
        val actualType = if (key.isEmpty()) type else type.removeSuffix("*") + key
        return baseClient.request(GetGlobalAccountData(userId, actualType))
    }

    override suspend fun setAccountData(
        content: GlobalAccountDataEventContent,
        userId: UserId,
        key: String,
    ): Result<Unit> {
        val eventType = contentMappings.globalAccountData.contentType(content)
            .let { type -> if (key.isEmpty()) type else type.removeSuffix("*") + key }

        return baseClient.request(SetGlobalAccountData(userId, eventType), content)
    }

    override suspend fun searchUsers(
        searchTerm: String,
        acceptLanguage: String,
        limit: Long?,
    ): Result<SearchUsers.Response> =
        baseClient.request(SearchUsers, SearchUsers.Request(searchTerm, limit)) {
            header(HttpHeaders.AcceptLanguage, acceptLanguage)
        }

    override suspend fun reportUser(
        userId: UserId,
        reason: String,
    ): Result<Unit> =
        baseClient.request(ReportUser(userId), ReportUser.Request(reason))
}

/**
 * @see [GetGlobalAccountData]
 */
suspend inline fun <reified C : GlobalAccountDataEventContent> UserApiClient.getAccountData(
    userId: UserId,
    key: String = "",
): Result<C> {
    val type = contentMappings.globalAccountData.contentType(C::class)
    @Suppress("UNCHECKED_CAST")
    return getAccountData(type, userId, key) as Result<C>
}

/**
 * @see [GetProfileField]
 */
suspend inline fun <reified T : ProfileField> UserApiClient.getProfileField(
    userId: UserId,
    key: ProfileField.Key<T>,
): Result<T> =
    getProfileField(userId, key).map { it as? T ?: error("unexpected type") }