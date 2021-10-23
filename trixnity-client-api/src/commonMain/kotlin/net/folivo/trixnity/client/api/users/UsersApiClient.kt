package net.folivo.trixnity.client.api.users

import com.benasher44.uuid.uuid4
import io.ktor.client.*
import io.ktor.client.request.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.api.e
import net.folivo.trixnity.client.api.unsupportedEventType
import net.folivo.trixnity.core.model.MatrixId.UserId
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent
import net.folivo.trixnity.core.model.events.ToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.PresenceEventContent
import net.folivo.trixnity.core.model.events.m.PresenceEventContent.Presence
import net.folivo.trixnity.core.serialization.event.EventContentSerializerMappings
import kotlin.reflect.KClass

class UsersApiClient(
    private val httpClient: HttpClient,
    val json: Json,
    private val contentMappings: EventContentSerializerMappings
) {

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#get-matrix-client-r0-profile-userid-displayname">matrix spec</a>
     */
    suspend fun getDisplayName(
        userId: UserId,
        asUserId: UserId? = null
    ): String {
        return httpClient.get<GetDisplayNameResponse> {
            url("/_matrix/client/r0/profile/${userId.e()}/displayname")
            parameter("user_id", asUserId)
        }.displayName
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#put-matrix-client-r0-profile-userid-displayname">matrix spec</a>
     */
    suspend fun setDisplayName(
        userId: UserId,
        displayName: String? = null,
        asUserId: UserId? = null
    ) {
        return httpClient.put {
            url("/_matrix/client/r0/profile/${userId.e()}/displayname")
            parameter("user_id", asUserId)
            body = mapOf("displayname" to displayName)
        }
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#get-matrix-client-r0-account-whoami">matrix spec</a>
     */
    suspend fun whoAmI(asUserId: UserId? = null): UserId {
        return httpClient.get<WhoAmIResponse> {
            url("/_matrix/client/r0/account/whoami")
            parameter("user_id", asUserId)
        }.userId
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#put-matrix-client-r0-presence-userid-status">matrix spec</a>
     */
    suspend fun setPresence(
        userId: UserId,
        presence: Presence,
        statusMessage: String? = null,
        asUserId: UserId? = null
    ) {
        return httpClient.put {
            url("/_matrix/client/r0/presence/${userId.e()}/status")
            parameter("user_id", asUserId)
            body = mapOf("presence" to presence.value, "status_msg" to statusMessage)
        }
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#get-matrix-client-r0-presence-userid-status">matrix spec</a>
     */
    suspend fun getPresence(
        userId: UserId,
        asUserId: UserId? = null
    ): PresenceEventContent {
        return httpClient.get {
            url("/_matrix/client/r0/presence/${userId.e()}/status")
            parameter("user_id", asUserId)
        }
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#put-matrix-client-r0-sendtodevice-eventtype-txnid">matrix spec</a>
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
        return httpClient.put {
            url("/_matrix/client/r0/sendToDevice/$eventType/$transactionId")
            parameter("user_id", asUserId)
            body = mapOf("messages" to events)
        }
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#post-matrix-client-r0-user-userid-filter">matrix spec</a>
     */
    suspend fun setFilter(
        userId: UserId,
        filters: Filters,
        asUserId: UserId? = null
    ): String {
        return httpClient.post<SetFilterResponse> {
            url("/_matrix/client/r0/user/${userId.e()}/filter")
            parameter("user_id", asUserId)
            body = filters
        }.filterId
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#get-matrix-client-r0-user-userid-filter-filterid">matrix spec</a>
     */
    suspend fun getFilter(
        userId: UserId,
        filterId: String,
        asUserId: UserId? = null
    ): Filters {
        return httpClient.get {
            url("/_matrix/client/r0/user/${userId.e()}/filter/$filterId")
            parameter("user_id", asUserId)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun <C : GlobalAccountDataEventContent> getAccountData(
        accountDataEventContentClass: KClass<C>,
        userId: UserId,
        asUserId: UserId? = null
    ): C {
        val eventType =
            contentMappings.globalAccountData.find { it.kClass == accountDataEventContentClass }?.type
                ?: throw IllegalArgumentException(unsupportedEventType(accountDataEventContentClass))
        val responseBody = httpClient.get<String> {
            url("/_matrix/client/r0/user/${userId.e()}/account_data/$eventType")
            parameter("user_id", asUserId)
        }
        val serializer = json.serializersModule.getContextual(accountDataEventContentClass)
        requireNotNull(serializer)
        return json.decodeFromString(serializer, responseBody)
    }

    suspend fun <C : GlobalAccountDataEventContent> setAccountData(
        content: C,
        userId: UserId,
        asUserId: UserId? = null
    ) {
        val eventType =
            contentMappings.globalAccountData.find { it.kClass.isInstance(content) }?.type
                ?: throw IllegalArgumentException(unsupportedEventType(content::class))
        httpClient.put<String> {
            url("/_matrix/client/r0/user/${userId.e()}/account_data/$eventType")
            parameter("user_id", asUserId)
            body = content
        }
    }
}