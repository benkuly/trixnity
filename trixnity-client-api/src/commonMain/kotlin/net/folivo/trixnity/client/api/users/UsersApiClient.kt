package net.folivo.trixnity.client.api.users

import com.benasher44.uuid.uuid4
import io.ktor.client.request.*
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.HttpMethod.Companion.Put
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.api.MatrixHttpClient
import net.folivo.trixnity.client.api.e
import net.folivo.trixnity.client.api.unsupportedEventType
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
    ): String? {
        return httpClient.request<GetDisplayNameResponse> {
            method = Get
            url("/_matrix/client/v3/profile/${userId.e()}/displayname")
            parameter("user_id", asUserId)
        }.displayName
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#put_matrixclientv3profileuseriddisplayname">matrix spec</a>
     */
    suspend fun setDisplayName(
        userId: UserId,
        displayName: String? = null,
        asUserId: UserId? = null
    ) {
        httpClient.request<Unit> {
            method = Put
            url("/_matrix/client/v3/profile/${userId.e()}/displayname")
            parameter("user_id", asUserId)
            body = mapOf("displayname" to displayName)
        }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#get_matrixclientv3profileuseridavatar_url">matrix spec</a>
     */
    suspend fun getAvatarUrl(
        userId: UserId,
        asUserId: UserId? = null,
    ): String? {
        return httpClient.request<GetAvatarUrlResponse> {
            method = Get
            url("/_matrix/client/v3/profile/${userId.e()}/avatar_url")
            parameter("user_id", asUserId)
        }.avatarUrl
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#put_matrixclientv3profileuseridavatar_url">matrix spec</a>
     */
    suspend fun setAvatarUrl(
        userId: UserId,
        avatarUrl: String,
        asUserId: UserId? = null,
    ) {
        httpClient.request<Unit> {
            method = Put
            url("/_matrix/client/v3/profile/${userId.e()}/avatar_url")
            parameter("user_id", asUserId)
            body = mapOf("avatar_url" to avatarUrl)
        }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#get_matrixclientv3profileuserid">matrix spec</a>
     */
    suspend fun getProfile(
        userId: UserId,
        asUserId: UserId? = null,
    ): GetProfileResponse {
        return httpClient.request {
            method = Get
            url("/_matrix/client/v3/profile/${userId.e()}")
            parameter("user_id", asUserId)
        }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#get_matrixclientv3accountwhoami">matrix spec</a>
     */
    suspend fun whoAmI(asUserId: UserId? = null): UserId {
        return httpClient.request<WhoAmIResponse> {
            method = Get
            url("/_matrix/client/v3/account/whoami")
            parameter("user_id", asUserId)
        }.userId
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#put_matrixclientv3presenceuseridstatus">matrix spec</a>
     */
    suspend fun setPresence(
        userId: UserId,
        presence: Presence,
        statusMessage: String? = null,
        asUserId: UserId? = null
    ) {
        httpClient.request<Unit> {
            method = Put
            url("/_matrix/client/v3/presence/${userId.e()}/status")
            parameter("user_id", asUserId)
            body = mapOf("presence" to presence.value, "status_msg" to statusMessage)
        }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#get_matrixclientv3presenceuseridstatus">matrix spec</a>
     */
    suspend fun getPresence(
        userId: UserId,
        asUserId: UserId? = null
    ): PresenceEventContent {
        return httpClient.request {
            method = Get
            url("/_matrix/client/v3/presence/${userId.e()}/status")
            parameter("user_id", asUserId)
        }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#put_matrixclientv3sendtodeviceeventtypetxnid">matrix spec</a>
     */
    suspend fun <C : ToDeviceEventContent> sendToDevice(
        events: Map<UserId, Map<String, C>>,
        transactionId: String = uuid4().toString(),
        asUserId: UserId? = null
    ) {
        val firstEventForType = events.entries.firstOrNull()?.value?.entries?.firstOrNull()?.value
            ?: throw IllegalArgumentException("you need to send at least on event")
        val eventType = contentMappings.toDevice.find { it.kClass.isInstance(firstEventForType) }?.type
            ?: throw IllegalArgumentException(unsupportedEventType(firstEventForType::class))
        httpClient.request<Unit> {
            method = Put
            url("/_matrix/client/v3/sendToDevice/$eventType/$transactionId")
            parameter("user_id", asUserId)
            body = mapOf("messages" to events)
        }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#post_matrixclientv3useruseridfilter">matrix spec</a>
     */
    suspend fun setFilter(
        userId: UserId,
        filters: Filters,
        asUserId: UserId? = null
    ): String {
        return httpClient.request<SetFilterResponse> {
            method = Post
            url("/_matrix/client/v3/user/${userId.e()}/filter")
            parameter("user_id", asUserId)
            body = filters
        }.filterId
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#get_matrixclientv3useruseridfilterfilterid">matrix spec</a>
     */
    suspend fun getFilter(
        userId: UserId,
        filterId: String,
        asUserId: UserId? = null
    ): Filters {
        return httpClient.request {
            method = Get
            url("/_matrix/client/v3/user/${userId.e()}/filter/$filterId")
            parameter("user_id", asUserId)
        }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#put_matrixclientv3useruseridaccount_datatype">matrix spec</a>
     */
    suspend fun <C : GlobalAccountDataEventContent> setAccountData(
        content: C,
        userId: UserId,
        asUserId: UserId? = null
    ) {
        val eventType =
            contentMappings.globalAccountData.find { it.kClass.isInstance(content) }?.type
                ?: throw IllegalArgumentException(unsupportedEventType(content::class))
        httpClient.request<String> {
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
    ): C {
        val mapping = contentMappings.globalAccountData.find { it.kClass == accountDataEventContentClass }
            ?: throw IllegalArgumentException(unsupportedEventType(accountDataEventContentClass))
        val responseBody = httpClient.request<String> {
            method = Get
            url("/_matrix/client/v3/user/${userId.e()}/account_data/${mapping.type}")
            parameter("user_id", asUserId)
        }

        @Suppress("UNCHECKED_CAST")
        val serializer = mapping.serializer as KSerializer<C>
        return json.decodeFromString(serializer, responseBody)
    }
}