package net.folivo.trixnity.clientserverapi.client

import com.benasher44.uuid.uuid4
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.client.e
import net.folivo.trixnity.clientserverapi.model.users.*
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent
import net.folivo.trixnity.core.model.events.ToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.PresenceEventContent
import net.folivo.trixnity.core.model.events.m.PresenceEventContent.Presence
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

class UsersApiClient(
    val httpClient: MatrixClientServerApiHttpClient,
    val json: Json,
    val contentMappings: EventContentSerializerMappings
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
        httpClient.request(SetPresence(userId.e(), asUserId), SetPresence.Request(presence.value, statusMessage))


    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3sendtodeviceeventtypetxnid">matrix spec</a>
     */
    suspend inline fun <reified C : ToDeviceEventContent> sendToDevice(
        events: Map<UserId, Map<String, C>>,
        transactionId: String = uuid4().toString(),
        asUserId: UserId? = null
    ): Result<Unit> {
        val firstEventForType = events.entries.firstOrNull()?.value?.entries?.firstOrNull()?.value
            ?: throw IllegalArgumentException("you need to send at least on event")
        val mapping = contentMappings.toDevice.find { it.kClass.isInstance(firstEventForType) }
            ?: throw IllegalArgumentException(unsupportedEventType(firstEventForType::class))

        @Suppress("UNCHECKED_CAST")
        val serializer = mapping.serializer as KSerializer<C>
        return httpClient.request(
            SendToDevice(mapping.type, transactionId.e(), asUserId),
            SendToDevice.Request(events),
            SendToDevice.Request.serializer(serializer),
        )
    }

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
    @OptIn(ExperimentalSerializationApi::class)
    suspend inline fun <reified C : GlobalAccountDataEventContent> getAccountData(
        userId: UserId,
        key: String = "",
        asUserId: UserId? = null
    ): Result<C> {
        val mapping = contentMappings.globalAccountData.find { it.kClass == C::class }
            ?: throw IllegalArgumentException(unsupportedEventType(C::class))
        val eventType = if (key.isEmpty()) mapping.type else mapping.type + key

        @Suppress("UNCHECKED_CAST")
        val serializer = mapping.serializer as KSerializer<C>
        return httpClient.request(
            GetGlobalAccountData(userId.e(), eventType, asUserId),
            serializer
        )
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3useruseridaccount_datatype">matrix spec</a>
     */
    suspend inline fun <reified C : GlobalAccountDataEventContent> setAccountData(
        content: C,
        userId: UserId,
        key: String = "",
        asUserId: UserId? = null
    ): Result<Unit> {
        val mapping = contentMappings.globalAccountData.find { it.kClass.isInstance(content) }
        val eventType = mapping?.type
            ?.let { type -> if (key.isEmpty()) type else type + key }
            ?: throw IllegalArgumentException(unsupportedEventType(content::class))

        @Suppress("UNCHECKED_CAST")
        val serializer = mapping.serializer as KSerializer<C>
        return httpClient.request(
            SetGlobalAccountData(userId.e(), eventType, asUserId),
            content,
            serializer,
        )
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