package net.folivo.trixnity.clientserverapi.client

import net.folivo.trixnity.api.client.e
import net.folivo.trixnity.clientserverapi.model.push.*
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.push.PushAction
import net.folivo.trixnity.core.model.push.PushRule
import net.folivo.trixnity.core.model.push.PushRuleKind

class PushApiClient(
    private val httpClient: MatrixClientServerApiHttpClient
) {

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3pushers">matrix spec</a>
     */
    suspend fun getPushers(asUserId: UserId? = null): Result<GetPushers.Response> =
        httpClient.request(GetPushers(asUserId))

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3pushersset">matrix spec</a>
     */
    suspend fun setPushers(request: SetPushers.Request, asUserId: UserId? = null): Result<Unit> =
        httpClient.request(SetPushers(asUserId), request)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3notifications">matrix spec</a>
     */
    suspend fun getNotifications(
        from: String? = null,
        limit: Long? = null,
        only: String? = null,
        asUserId: UserId? = null,
    ): Result<GetNotifications.Response> =
        httpClient.request(GetNotifications(from, limit, only, asUserId))

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3pushrules">matrix spec</a>
     */
    suspend fun getPushRules(asUserId: UserId? = null): Result<GetPushRules.Response> =
        httpClient.request(GetPushRules(asUserId))

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3pushrulesscopekindruleid">matrix spec</a>
     */
    suspend fun getPushRule(
        scope: String,
        kind: PushRuleKind,
        ruleId: String,
        asUserId: UserId? = null,
    ): Result<PushRule> =
        httpClient.request(GetPushRule(scope.e(), kind, ruleId.e(), asUserId))

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3pushrulesscopekindruleid">matrix spec</a>
     */
    suspend fun setPushRule(
        scope: String,
        kind: PushRuleKind,
        ruleId: String,
        pushRule: SetPushRule.Request,
        beforeRuleId: String? = null,
        afterRuleId: String? = null,
        asUserId: UserId? = null,
    ): Result<Unit> =
        httpClient.request(SetPushRule(scope.e(), kind, ruleId.e(), beforeRuleId, afterRuleId, asUserId), pushRule)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3pushrulesscopekindruleid">matrix spec</a>
     */
    suspend fun deletePushRule(
        scope: String,
        kind: PushRuleKind,
        ruleId: String,
        asUserId: UserId? = null,
    ): Result<Unit> =
        httpClient.request(DeletePushRule(scope.e(), kind, ruleId.e(), asUserId))

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3pushrulesscopekindruleidactions">matrix spec</a>
     */
    suspend fun getPushRuleActions(
        scope: String,
        kind: PushRuleKind,
        ruleId: String,
        asUserId: UserId? = null,
    ): Result<Set<PushAction>> =
        httpClient.request(GetPushRuleActions(scope.e(), kind, ruleId.e(), asUserId)).map { it.actions }

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3pushrulesscopekindruleidactions">matrix spec</a>
     */
    suspend fun setPushRuleActions(
        scope: String,
        kind: PushRuleKind,
        ruleId: String,
        actions: Set<PushAction>,
        asUserId: UserId? = null,
    ): Result<Unit> =
        httpClient.request(
            SetPushRuleActions(scope.e(), kind, ruleId.e(), asUserId),
            SetPushRuleActions.Request(actions)
        )

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3pushrulesscopekindruleidenabled">matrix spec</a>
     */
    suspend fun getPushRuleEnabled(
        scope: String,
        kind: PushRuleKind,
        ruleId: String,
        asUserId: UserId? = null,
    ): Result<Boolean> =
        httpClient.request(GetPushRuleEnabled(scope.e(), kind, ruleId.e(), asUserId)).map { it.enabled }

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3pushrulesscopekindruleidenabled">matrix spec</a>
     */
    suspend fun setPushRuleEnabled(
        scope: String,
        kind: PushRuleKind,
        ruleId: String,
        enabled: Boolean,
        asUserId: UserId? = null,
    ): Result<Unit> =
        httpClient.request(
            SetPushRuleEnabled(scope.e(), kind, ruleId.e(), asUserId),
            SetPushRuleEnabled.Request(enabled)
        )
}
