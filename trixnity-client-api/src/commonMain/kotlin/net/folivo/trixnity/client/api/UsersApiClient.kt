package net.folivo.trixnity.client.api

import com.benasher44.uuid.uuid4
import io.ktor.client.request.*
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.HttpMethod.Companion.Put
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.api.model.users.*
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent
import net.folivo.trixnity.core.model.events.ToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.PresenceEventContent
import net.folivo.trixnity.core.model.events.m.PresenceEventContent.Presence
import net.folivo.trixnity.core.serialization.event.EventContentSerializerMappings
import kotlin.reflect.KClass

class UsersApiClient(
    private val httpClient: MatrixHttpClient,
    val json: Json,
    private val contentMappings: EventContentSerializerMappings
) {

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#get_matrixclientv3profileuseriddisplayname">matrix spec</a>
     */
    suspend fun getDisplayName(
        userId: UserId,
        asUserId: UserId? = null
    ): Result<String?> =
        httpClient.request<GetDisplayNameResponse> {
            method = Get
            url("/_matrix/client/v3/profile/${userId.e()}/displayname")
            parameter("user_id", asUserId)
        }.mapCatching { it.displayName }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#put_matrixclientv3profileuseriddisplayname">matrix spec</a>
     */
    suspend fun setDisplayName(
        userId: UserId,
        displayName: String? = null,
        asUserId: UserId? = null
    ): Result<Unit> =
        httpClient.request {
            method = Put
            url("/_matrix/client/v3/profile/${userId.e()}/displayname")
            parameter("user_id", asUserId)
            body = SetDisplayNameRequest(displayName)
        }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#get_matrixclientv3profileuseridavatar_url">matrix spec</a>
     */
    suspend fun getAvatarUrl(
        userId: UserId,
        asUserId: UserId? = null,
    ): Result<String?> =
        httpClient.request<GetAvatarUrlResponse> {
            method = Get
            url("/_matrix/client/v3/profile/${userId.e()}/avatar_url")
            parameter("user_id", asUserId)
        }.mapCatching { it.avatarUrl }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#put_matrixclientv3profileuseridavatar_url">matrix spec</a>
     */
    suspend fun setAvatarUrl(
        userId: UserId,
        avatarUrl: String,
        asUserId: UserId? = null,
    ): Result<Unit> =
        httpClient.request {
            method = Put
            url("/_matrix/client/v3/profile/${userId.e()}/avatar_url")
            parameter("user_id", asUserId)
            body = SetAvatarUrlRequest(avatarUrl)
        }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#get_matrixclientv3profileuserid">matrix spec</a>
     */
    suspend fun getProfile(
        userId: UserId,
        asUserId: UserId? = null,
    ): Result<GetProfileResponse> =
        httpClient.request {
            method = Get
            url("/_matrix/client/v3/profile/${userId.e()}")
            parameter("user_id", asUserId)
        }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#get_matrixclientv3accountwhoami">matrix spec</a>
     */
    suspend fun whoAmI(asUserId: UserId? = null): Result<WhoAmIResponse> =
        httpClient.request {
            method = Get
            url("/_matrix/client/v3/account/whoami")
            parameter("user_id", asUserId)
        }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#put_matrixclientv3presenceuseridstatus">matrix spec</a>
     */
    suspend fun setPresence(
        userId: UserId,
        presence: Presence,
        statusMessage: String? = null,
        asUserId: UserId? = null
    ): Result<Unit> =
        httpClient.request {
            method = Put
            url("/_matrix/client/v3/presence/${userId.e()}/status")
            parameter("user_id", asUserId)
            body = SetPresenceRequest(presence.value, statusMessage)
        }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#get_matrixclientv3presenceuseridstatus">matrix spec</a>
     */
    suspend fun getPresence(
        userId: UserId,
        asUserId: UserId? = null
    ): Result<PresenceEventContent> =
        httpClient.request {
            method = Get
            url("/_matrix/client/v3/presence/${userId.e()}/status")
            parameter("user_id", asUserId)
        }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#put_matrixclientv3sendtodeviceeventtypetxnid">matrix spec</a>
     */
    suspend fun <C : ToDeviceEventContent> sendToDevice(
        events: Map<UserId, Map<String, C>>,
        transactionId: String = uuid4().toString(),
        asUserId: UserId? = null
    ): Result<Unit> {
        val firstEventForType = events.entries.firstOrNull()?.value?.entries?.firstOrNull()?.value
            ?: throw IllegalArgumentException("you need to send at least on event")
        val eventType = contentMappings.toDevice.find { it.kClass.isInstance(firstEventForType) }
            ?: throw IllegalArgumentException(unsupportedEventType(firstEventForType::class))

        @Suppress("UNCHECKED_CAST")
        val serializer = eventType.serializer as KSerializer<C>
        return httpClient.request {
            method = Put
            url("/_matrix/client/v3/sendToDevice/${eventType.type}/$transactionId")
            parameter("user_id", asUserId)
            body = json.encodeToJsonElement(SendToDeviceRequest.serializer(serializer), SendToDeviceRequest(events))
        }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#post_matrixclientv3useruseridfilter">matrix spec</a>
     */
    suspend fun setFilter(
        userId: UserId,
        filters: Filters,
        asUserId: UserId? = null
    ): Result<String> =
        httpClient.request<SetFilterResponse> {
            method = Post
            url("/_matrix/client/v3/user/${userId.e()}/filter")
            parameter("user_id", asUserId)
            body = filters
        }.mapCatching { it.filterId }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#get_matrixclientv3useruseridfilterfilterid">matrix spec</a>
     */
    suspend fun getFilter(
        userId: UserId,
        filterId: String,
        asUserId: UserId? = null
    ): Result<Filters> =
        httpClient.request {
            method = Get
            url("/_matrix/client/v3/user/${userId.e()}/filter/$filterId")
            parameter("user_id", asUserId)
        }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#put_matrixclientv3useruseridaccount_datatype">matrix spec</a>
     */
    suspend fun <C : GlobalAccountDataEventContent> setAccountData(
        content: C,
        userId: UserId,
        asUserId: UserId? = null
    ): Result<Unit> {
        val eventType =
            contentMappings.globalAccountData.find { it.kClass.isInstance(content) }?.type
                ?: throw IllegalArgumentException(unsupportedEventType(content::class))
        return httpClient.request {
            method = Put
            url("/_matrix/client/v3/user/${userId.e()}/account_data/$eventType")
            parameter("user_id", asUserId)
            body = content
        }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#get_matrixclientv3useruseridaccount_datatype">matrix spec</a>
     */
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun <C : GlobalAccountDataEventContent> getAccountData(
        accountDataEventContentClass: KClass<C>,
        userId: UserId,
        asUserId: UserId? = null
    ): Result<C> {
        val mapping = contentMappings.globalAccountData.find { it.kClass == accountDataEventContentClass }
            ?: throw IllegalArgumentException(unsupportedEventType(accountDataEventContentClass))
        return httpClient.request<String> {
            method = Get
            url("/_matrix/client/v3/user/${userId.e()}/account_data/${mapping.type}")
            parameter("user_id", asUserId)
        }.mapCatching {
            @Suppress("UNCHECKED_CAST")
            val serializer = mapping.serializer as KSerializer<C>
            json.decodeFromString(serializer, it)
        }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#get_matrixclientv3useruseridaccount_datatype">matrix spec</a>
     */
    suspend inline fun <reified C : GlobalAccountDataEventContent> getAccountData(
        userId: UserId,
        asUserId: UserId? = null
    ): Result<C> = getAccountData(C::class, userId, asUserId)

    /**
     *  @see <a href="https://spec.matrix.org/v1.1/client-server-api/#post_matrixclientv3user_directorysearch">matrix spec</a>
     */
    suspend fun searchUsers(
        searchTerm: String,
        acceptLanguage: String,
        limit: Int? = 10,
        asUserId: UserId? = null,
    ): Result<SearchUsersResponse> =
        httpClient.request {
            method = Post
            url("/_matrix/client/v3/user_directory/search")
            parameter("user_id", asUserId)
            header("Accept-Language", acceptLanguage)
            body = SearchUsersRequest(searchTerm, limit)
        }
}