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

class UsersApiClient(
    @PublishedApi
    internal val httpClient: MatrixClientServerApiHttpClient,
    @PublishedApi
    internal val json: Json,
    @PublishedApi
    internal val contentMappings: EventContentSerializerMappings
) {

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3profileuseriddisplayname">matrix spec</a>
     */
    suspend fun getDisplayName(
        userId: UserId,
        asUserId: UserId? = null
    ): Result<String?> =
        httpClient.request(GetDisplayName(userId.e(), asUserId)).mapCatching { it.displayName }

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3profileuseriddisplayname">matrix spec</a>
     */
    suspend fun setDisplayName(
        userId: UserId,
        displayName: String? = null,
        asUserId: UserId? = null
    ): Result<Unit> =
        httpClient.request(SetDisplayName(userId.e(), asUserId), SetDisplayName.Request(displayName))

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3profileuseridavatar_url">matrix spec</a>
     */
    suspend fun getAvatarUrl(
        userId: UserId,
        asUserId: UserId? = null,
    ): Result<String?> =
        httpClient.request(GetAvatarUrl(userId.e(), asUserId)).mapCatching { it.avatarUrl }

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3profileuseridavatar_url">matrix spec</a>
     */
    suspend fun setAvatarUrl(
        userId: UserId,
        avatarUrl: String?,
        asUserId: UserId? = null,
    ): Result<Unit> =
        httpClient.request(SetAvatarUrl(userId.e(), asUserId), SetAvatarUrl.Request(avatarUrl))

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3profileuserid">matrix spec</a>
     */
    suspend fun getProfile(
        userId: UserId,
        asUserId: UserId? = null,
    ): Result<GetProfile.Response> =
        httpClient.request(GetProfile(userId.e(), asUserId))

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3accountwhoami">matrix spec</a>
     */
    suspend fun whoAmI(asUserId: UserId? = null): Result<WhoAmI.Response> =
        httpClient.request(WhoAmI(asUserId))

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3presenceuseridstatus">matrix spec</a>
     */
    suspend fun getPresence(
        userId: UserId,
        asUserId: UserId? = null
    ): Result<PresenceEventContent> =
        httpClient.request(GetPresence(userId.e(), asUserId))

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3presenceuseridstatus">matrix spec</a>
     */
    suspend fun setPresence(
        userId: UserId,
        presence: Presence,
        statusMessage: String? = null,
        asUserId: UserId? = null
    ): Result<Unit> =
        httpClient.request(SetPresence(userId.e(), asUserId), SetPresence.Request(presence, statusMessage))


    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3sendtodeviceeventtypetxnid">matrix spec</a>
     */
    suspend fun <C : ToDeviceEventContent> sendToDevice(
        events: Map<UserId, Map<String, C>>,
        transactionId: String = uuid4().toString(),
        asUserId: UserId? = null
    ): Result<Unit> {
        val firstEventForType = events.entries.firstOrNull()?.value?.entries?.firstOrNull()?.value
            ?: throw IllegalArgumentException("you need to send at least on event")
        val type = contentMappings.toDevice.contentSerializer(firstEventForType).first
        return sendToDevice(type, events, transactionId, asUserId)
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3sendtodeviceeventtypetxnid">matrix spec</a>
     */
    suspend fun sendToDevice(
        type: String,
        events: Map<UserId, Map<String, ToDeviceEventContent>>,
        transactionId: String = uuid4().toString(),
        asUserId: UserId? = null
    ): Result<Unit> =
        httpClient.request(SendToDevice(type, transactionId.e(), asUserId), SendToDevice.Request(events))

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3useruseridfilterfilterid">matrix spec</a>
     */
    suspend fun getFilter(
        userId: UserId,
        filterId: String,
        asUserId: UserId? = null
    ): Result<Filters> =
        httpClient.request(GetFilter(userId.e(), filterId.e(), asUserId))

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3useruseridfilter">matrix spec</a>
     */
    suspend fun setFilter(
        userId: UserId,
        filters: Filters,
        asUserId: UserId? = null
    ): Result<String> =
        httpClient.request(SetFilter(userId.e(), asUserId), filters).mapCatching { it.filterId }

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3useruseridaccount_datatype">matrix spec</a>
     */
    suspend inline fun <reified C : GlobalAccountDataEventContent> getAccountData(
        userId: UserId,
        key: String = "",
        asUserId: UserId? = null
    ): Result<C> {
        val type = contentMappings.globalAccountData.fromClass(C::class).type
        @Suppress("UNCHECKED_CAST")
        return getAccountData(type, userId, key, asUserId) as Result<C>
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3useruseridaccount_datatype">matrix spec</a>
     */
    suspend fun getAccountData(
        type: String,
        userId: UserId,
        key: String = "",
        asUserId: UserId? = null
    ): Result<GlobalAccountDataEventContent> {
        val actualType = if (key.isEmpty()) type else type + key
        return httpClient.request(GetGlobalAccountData(userId.e(), actualType, asUserId))
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3useruseridaccount_datatype">matrix spec</a>
     */
    suspend fun setAccountData(
        content: GlobalAccountDataEventContent,
        userId: UserId,
        key: String = "",
        asUserId: UserId? = null
    ): Result<Unit> {
        val mapping = contentMappings.globalAccountData.contentSerializer(content)
        val eventType = mapping.first.let { type -> if (key.isEmpty()) type else type + key }

        return httpClient.request(SetGlobalAccountData(userId.e(), eventType, asUserId), content)
    }

    /**
     *  @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3user_directorysearch">matrix spec</a>
     */
    suspend fun searchUsers(
        searchTerm: String,
        acceptLanguage: String,
        limit: Int? = 10,
        asUserId: UserId? = null,
    ): Result<SearchUsers.Response> =
        httpClient.request(SearchUsers(asUserId), SearchUsers.Request(searchTerm, limit)) {
            header(HttpHeaders.AcceptLanguage, acceptLanguage)
        }
}