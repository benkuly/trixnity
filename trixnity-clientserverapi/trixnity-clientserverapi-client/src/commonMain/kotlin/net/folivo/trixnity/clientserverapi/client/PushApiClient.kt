package net.folivo.trixnity.clientserverapi.client

import net.folivo.trixnity.api.client.e
import net.folivo.trixnity.clientserverapi.model.push.*
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.push.PushAction
import net.folivo.trixnity.core.model.push.PushRule
import net.folivo.trixnity.core.model.push.PushRuleKind

interface IPushApiClient {
    /**
     * @see [GetPushers]
     */
    suspend fun getPushers(asUserId: UserId? = null): Result<GetPushers.Response>

    /**
     * @see [SetPushers]
     */
    suspend fun setPushers(request: SetPushers.Request, asUserId: UserId? = null): Result<Unit>

    /**
     * @see [GetNotifications]
     */
    suspend fun getNotifications(
        from: String? = null,
        limit: Long? = null,
        only: String? = null,
        asUserId: UserId? = null,
    ): Result<GetNotifications.Response>

    /**
     * @see [GetPushRules]
     */
    suspend fun getPushRules(asUserId: UserId? = null): Result<GetPushRules.Response>

    /**
     * @see [GetPushRule]
     */
    suspend fun getPushRule(
        scope: String,
        kind: PushRuleKind,
        ruleId: String,
        asUserId: UserId? = null,
    ): Result<PushRule>

    /**
     * @see [SetPushRule]
     */
    suspend fun setPushRule(
        scope: String,
        kind: PushRuleKind,
        ruleId: String,
        pushRule: SetPushRule.Request,
        beforeRuleId: String? = null,
        afterRuleId: String? = null,
        asUserId: UserId? = null,
    ): Result<Unit>

    /**
     * @see [DeletePushRule]
     */
    suspend fun deletePushRule(
        scope: String,
        kind: PushRuleKind,
        ruleId: String,
        asUserId: UserId? = null,
    ): Result<Unit>

    /**
     * @see [GetPushRuleActions]
     */
    suspend fun getPushRuleActions(
        scope: String,
        kind: PushRuleKind,
        ruleId: String,
        asUserId: UserId? = null,
    ): Result<Set<PushAction>>

    /**
     * @see [SetPushRuleActions]
     */
    suspend fun setPushRuleActions(
        scope: String,
        kind: PushRuleKind,
        ruleId: String,
        actions: Set<PushAction>,
        asUserId: UserId? = null,
    ): Result<Unit>

    /**
     * @see [GetPushRuleEnabled]
     */
    suspend fun getPushRuleEnabled(
        scope: String,
        kind: PushRuleKind,
        ruleId: String,
        asUserId: UserId? = null,
    ): Result<Boolean>

    /**
     * @see [SetPushRuleEnabled]
     */
    suspend fun setPushRuleEnabled(
        scope: String,
        kind: PushRuleKind,
        ruleId: String,
        enabled: Boolean,
        asUserId: UserId? = null,
    ): Result<Unit>
}

class PushApiClient(
    private val httpClient: MatrixClientServerApiHttpClient
) : IPushApiClient {

    override suspend fun getPushers(asUserId: UserId?): Result<GetPushers.Response> =
        httpClient.request(GetPushers(asUserId))

    override suspend fun setPushers(request: SetPushers.Request, asUserId: UserId?): Result<Unit> =
        httpClient.request(SetPushers(asUserId), request)

    override suspend fun getNotifications(
        from: String?,
        limit: Long?,
        only: String?,
        asUserId: UserId?,
    ): Result<GetNotifications.Response> =
        httpClient.request(GetNotifications(from, limit, only, asUserId))

    override suspend fun getPushRules(asUserId: UserId?): Result<GetPushRules.Response> =
        httpClient.request(GetPushRules(asUserId))

    override suspend fun getPushRule(
        scope: String,
        kind: PushRuleKind,
        ruleId: String,
        asUserId: UserId?,
    ): Result<PushRule> =
        httpClient.request(GetPushRule(scope.e(), kind, ruleId.e(), asUserId))

    override suspend fun setPushRule(
        scope: String,
        kind: PushRuleKind,
        ruleId: String,
        pushRule: SetPushRule.Request,
        beforeRuleId: String?,
        afterRuleId: String?,
        asUserId: UserId?,
    ): Result<Unit> =
        httpClient.request(SetPushRule(scope.e(), kind, ruleId.e(), beforeRuleId, afterRuleId, asUserId), pushRule)

    override suspend fun deletePushRule(
        scope: String,
        kind: PushRuleKind,
        ruleId: String,
        asUserId: UserId?,
    ): Result<Unit> =
        httpClient.request(DeletePushRule(scope.e(), kind, ruleId.e(), asUserId))

    override suspend fun getPushRuleActions(
        scope: String,
        kind: PushRuleKind,
        ruleId: String,
        asUserId: UserId?,
    ): Result<Set<PushAction>> =
        httpClient.request(GetPushRuleActions(scope.e(), kind, ruleId.e(), asUserId)).map { it.actions }

    override suspend fun setPushRuleActions(
        scope: String,
        kind: PushRuleKind,
        ruleId: String,
        actions: Set<PushAction>,
        asUserId: UserId?,
    ): Result<Unit> =
        httpClient.request(
            SetPushRuleActions(scope.e(), kind, ruleId.e(), asUserId),
            SetPushRuleActions.Request(actions)
        )

    override suspend fun getPushRuleEnabled(
        scope: String,
        kind: PushRuleKind,
        ruleId: String,
        asUserId: UserId?,
    ): Result<Boolean> =
        httpClient.request(GetPushRuleEnabled(scope.e(), kind, ruleId.e(), asUserId)).map { it.enabled }

    override suspend fun setPushRuleEnabled(
        scope: String,
        kind: PushRuleKind,
        ruleId: String,
        enabled: Boolean,
        asUserId: UserId?,
    ): Result<Unit> =
        httpClient.request(
            SetPushRuleEnabled(scope.e(), kind, ruleId.e(), asUserId),
            SetPushRuleEnabled.Request(enabled)
        )
}
