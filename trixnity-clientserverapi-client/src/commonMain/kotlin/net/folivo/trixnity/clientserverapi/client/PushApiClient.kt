package net.folivo.trixnity.clientserverapi.client

import io.ktor.client.request.*
import io.ktor.http.HttpMethod.Companion.Delete
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.HttpMethod.Companion.Put
import net.folivo.trixnity.clientserverapi.model.push.*
import net.folivo.trixnity.core.model.push.PushRule

class PushApiClient(
    private val httpClient: MatrixHttpClient
) {

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3pushers">matrix spec</a>
     */
    suspend fun getPushers(): Result<GetPushersResponse> =
        httpClient.request {
            method = Get
            url("/_matrix/client/v3/pushers")
        }

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3pushersset">matrix spec</a>
     */
    suspend fun setPushers(request: SetPushersRequest): Result<Unit> =
        httpClient.request {
            method = Post
            url("/_matrix/client/v3/pushers/set")
            setBody(request)
        }

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3notifications">matrix spec</a>
     */
    suspend fun getNotifications(
        from: String? = null,
        limit: Long? = null,
        only: String? = null
    ): Result<GetNotificationsResponse> =
        httpClient.request {
            method = Get
            url("/_matrix/client/v3/notifications")
            parameter("from", from)
            parameter("limit", limit)
            parameter("only", only)
        }

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3pushrules">matrix spec</a>
     */
    suspend fun getPushRules(): Result<GetPushRulesResponse> =
        httpClient.request {
            method = Get
            url("/_matrix/client/v3/pushrules/")
        }

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3pushrulesscopekindruleid">matrix spec</a>
     */
    suspend fun getPushRule(
        scope: String,
        kind: String,
        ruleId: String
    ): Result<PushRule> =
        httpClient.request {
            method = Get
            url(" /_matrix/client/v3/pushrules/${scope}/${kind}/${ruleId}")
        }

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3pushrulesscopekindruleid">matrix spec</a>
     */
    suspend fun setPushRule(
        scope: String,
        kind: String,
        ruleId: String,
        pushRule: SetPushRuleRequest,
        beforeRuleId: String? = null,
        afterRuleId: String? = null
    ): Result<Unit> =
        httpClient.request {
            method = Put
            url(" /_matrix/client/v3/pushrules/${scope}/${kind}/${ruleId}")
            parameter("before", beforeRuleId)
            parameter("after", afterRuleId)
            setBody(pushRule)
        }

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3pushrulesscopekindruleid">matrix spec</a>
     */
    suspend fun deletePushRule(
        scope: String,
        kind: String,
        ruleId: String,
    ): Result<Unit> =
        httpClient.request {
            method = Delete
            url(" /_matrix/client/v3/pushrules/${scope}/${kind}/${ruleId}")
        }
}
