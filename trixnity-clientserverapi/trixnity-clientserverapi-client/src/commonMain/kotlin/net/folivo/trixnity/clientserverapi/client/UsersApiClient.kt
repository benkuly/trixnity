package net.folivo.trixnity.clientserverapi.client

import com.benasher44.uuid.uuid4
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.client.e
import net.folivo.trixnity.clientserverapi.model.users.*
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent
import net.folivo.trixnity.core.model.events.ToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.PresenceEventContent
import net.folivo.trixnity.core.model.events.m.PresenceEventContent.Presence
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.contentSerializer
import net.folivo.trixnity.core.serialization.events.fromClass

interface IUsersApiClient {
    val contentMappings: EventContentSerializerMappings

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3profileuseriddisplayname">matrix spec</a>
     */
    suspend fun getDisplayName(
        userId: UserId,
    ): Result<String?>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3profileuseriddisplayname">matrix spec</a>
     */
    suspend fun setDisplayName(
        userId: UserId,
        displayName: String? = null,
        asUserId: UserId? = null
    ): Result<Unit>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3profileuseridavatar_url">matrix spec</a>
     */
    suspend fun getAvatarUrl(
        userId: UserId,
    ): Result<String?>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3profileuseridavatar_url">matrix spec</a>
     */
    suspend fun setAvatarUrl(
        userId: UserId,
        avatarUrl: String?,
        asUserId: UserId? = null,
    ): Result<Unit>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3profileuserid">matrix spec</a>
     */
    suspend fun getProfile(
        userId: UserId,
    ): Result<GetProfile.Response>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3presenceuseridstatus">matrix spec</a>
     */
    suspend fun getPresence(
        userId: UserId,
        asUserId: UserId? = null
    ): Result<PresenceEventContent>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3presenceuseridstatus">matrix spec</a>
     */
    suspend fun setPresence(
        userId: UserId,
        presence: Presence,
        statusMessage: String? = null,
        asUserId: UserId? = null
    ): Result<Unit>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3sendtodeviceeventtypetxnid">matrix spec</a>
     */
    suspend fun <C : ToDeviceEventContent> sendToDevice(
        events: Map<UserId, Map<String, C>>,
        transactionId: String = uuid4().toString(),
        asUserId: UserId? = null
    ): Result<Unit>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3sendtodeviceeventtypetxnid">matrix spec</a>
     */
    suspend fun sendToDevice(
        type: String,
        events: Map<UserId, Map<String, ToDeviceEventContent>>,
        transactionId: String = uuid4().toString(),
        asUserId: UserId? = null
    ): Result<Unit>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3useruseridfilterfilterid">matrix spec</a>
     */
    suspend fun getFilter(
        userId: UserId,
        filterId: String,
        asUserId: UserId? = null
    ): Result<Filters>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3useruseridfilter">matrix spec</a>
     */
    suspend fun setFilter(
        userId: UserId,
        filters: Filters,
        asUserId: UserId? = null
    ): Result<String>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3useruseridaccount_datatype">matrix spec</a>
     */
    suspend fun getAccountData(
        type: String,
        userId: UserId,
        key: String = "",
        asUserId: UserId? = null
    ): Result<GlobalAccountDataEventContent>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3useruseridaccount_datatype">matrix spec</a>
     */
    suspend fun setAccountData(
        content: GlobalAccountDataEventContent,
        userId: UserId,
        key: String = "",
        asUserId: UserId? = null
    ): Result<Unit>

    /**
     *  @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3user_directorysearch">matrix spec</a>
     */
    suspend fun searchUsers(
        searchTerm: String,
        acceptLanguage: String,
        limit: Long? = 10,
        asUserId: UserId? = null,
    ): Result<SearchUsers.Response>
}

class UsersApiClient(
    private val httpClient: MatrixClientServerApiHttpClient,
    private val json: Json,
    override val contentMappings: EventContentSerializerMappings
) : IUsersApiClient {

    override suspend fun getDisplayName(
        userId: UserId,
    ): Result<String?> =
        httpClient.request(GetDisplayName(userId.e())).mapCatching { it.displayName }

    override suspend fun setDisplayName(
        userId: UserId,
        displayName: String?,
        asUserId: UserId?
    ): Result<Unit> =
        httpClient.request(SetDisplayName(userId.e(), asUserId), SetDisplayName.Request(displayName))

    override suspend fun getAvatarUrl(
        userId: UserId,
    ): Result<String?> =
        httpClient.request(GetAvatarUrl(userId.e())).mapCatching { it.avatarUrl }

    override suspend fun setAvatarUrl(
        userId: UserId,
        avatarUrl: String?,
        asUserId: UserId?,
    ): Result<Unit> =
        httpClient.request(SetAvatarUrl(userId.e(), asUserId), SetAvatarUrl.Request(avatarUrl))

    override suspend fun getProfile(
        userId: UserId,
    ): Result<GetProfile.Response> =
        httpClient.request(GetProfile(userId.e()))

    override suspend fun getPresence(
        userId: UserId,
        asUserId: UserId?
    ): Result<PresenceEventContent> =
        httpClient.request(GetPresence(userId.e(), asUserId))

    override suspend fun setPresence(
        userId: UserId,
        presence: Presence,
        statusMessage: String?,
        asUserId: UserId?
    ): Result<Unit> =
        httpClient.request(SetPresence(userId.e(), asUserId), SetPresence.Request(presence, statusMessage))


    override suspend fun <C : ToDeviceEventContent> sendToDevice(
        events: Map<UserId, Map<String, C>>,
        transactionId: String,
        asUserId: UserId?
    ): Result<Unit> {
        val firstEventForType = events.entries.firstOrNull()?.value?.entries?.firstOrNull()?.value
            ?: throw IllegalArgumentException("you need to send at least on event")
        val type = contentMappings.toDevice.contentSerializer(firstEventForType).first
        return sendToDevice(type, events, transactionId, asUserId)
    }

    override suspend fun sendToDevice(
        type: String,
        events: Map<UserId, Map<String, ToDeviceEventContent>>,
        transactionId: String,
        asUserId: UserId?
    ): Result<Unit> =
        httpClient.request(SendToDevice(type, transactionId, asUserId), SendToDevice.Request(events))

    override suspend fun getFilter(
        userId: UserId,
        filterId: String,
        asUserId: UserId?
    ): Result<Filters> =
        httpClient.request(GetFilter(userId.e(), filterId.e(), asUserId))

    override suspend fun setFilter(
        userId: UserId,
        filters: Filters,
        asUserId: UserId?
    ): Result<String> =
        httpClient.request(SetFilter(userId.e(), asUserId), filters).mapCatching { it.filterId }

    override suspend fun getAccountData(
        type: String,
        userId: UserId,
        key: String,
        asUserId: UserId?
    ): Result<GlobalAccountDataEventContent> {
        val actualType = if (key.isEmpty()) type else type + key
        return httpClient.request(GetGlobalAccountData(userId.e(), actualType, asUserId))
    }

    override suspend fun setAccountData(
        content: GlobalAccountDataEventContent,
        userId: UserId,
        key: String,
        asUserId: UserId?
    ): Result<Unit> {
        val mapping = contentMappings.globalAccountData.contentSerializer(content)
        val eventType = mapping.first.let { type -> if (key.isEmpty()) type else type + key }

        return httpClient.request(SetGlobalAccountData(userId.e(), eventType, asUserId), content)
    }

    override suspend fun searchUsers(
        searchTerm: String,
        acceptLanguage: String,
        limit: Long?,
        asUserId: UserId?,
    ): Result<SearchUsers.Response> =
        httpClient.request(SearchUsers(asUserId), SearchUsers.Request(searchTerm, limit)) {
            header(HttpHeaders.AcceptLanguage, acceptLanguage)
        }
}

/**
 * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3useruseridaccount_datatype">matrix spec</a>
 */
suspend inline fun <reified C : GlobalAccountDataEventContent> IUsersApiClient.getAccountData(
    userId: UserId,
    key: String = "",
    asUserId: UserId? = null
): Result<C> {
    val type = contentMappings.globalAccountData.fromClass(C::class).type
    @Suppress("UNCHECKED_CAST")
    return getAccountData(type, userId, key, asUserId) as Result<C>
}