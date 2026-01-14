package net.folivo.trixnity.clientserverapi.client

import net.folivo.trixnity.clientserverapi.model.push.*
import net.folivo.trixnity.core.model.push.PushAction
import net.folivo.trixnity.core.model.push.PushRule
import net.folivo.trixnity.core.model.push.PushRuleKind

interface PushApiClient {
    /**
     * @see [GetPushers]
     */
    suspend fun getPushers(): Result<GetPushers.Response>

    /**
     * @see [SetPushers]
     */
    suspend fun setPushers(request: SetPushers.Request): Result<Unit>

    /**
     * @see [GetNotifications]
     */
    suspend fun getNotifications(
        from: String? = null,
        limit: Long? = null,
        only: String? = null,
    ): Result<GetNotifications.Response>

    /**
     * @see [GetPushRules]
     */
    suspend fun getPushRules(): Result<GetPushRules.Response>

    /**
     * @see [GetPushRule]
     */
    suspend fun getPushRule(
        scope: String,
        kind: PushRuleKind,
        ruleId: String,
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
    ): Result<Unit>

    /**
     * @see [DeletePushRule]
     */
    suspend fun deletePushRule(
        scope: String,
        kind: PushRuleKind,
        ruleId: String,
    ): Result<Unit>

    /**
     * @see [GetPushRuleActions]
     */
    suspend fun getPushRuleActions(
        scope: String,
        kind: PushRuleKind,
        ruleId: String,
    ): Result<Set<PushAction>>

    /**
     * @see [SetPushRuleActions]
     */
    suspend fun setPushRuleActions(
        scope: String,
        kind: PushRuleKind,
        ruleId: String,
        actions: Set<PushAction>,
    ): Result<Unit>

    /**
     * @see [GetPushRuleEnabled]
     */
    suspend fun getPushRuleEnabled(
        scope: String,
        kind: PushRuleKind,
        ruleId: String,
    ): Result<Boolean>

    /**
     * @see [SetPushRuleEnabled]
     */
    suspend fun setPushRuleEnabled(
        scope: String,
        kind: PushRuleKind,
        ruleId: String,
        enabled: Boolean,
    ): Result<Unit>
}

class PushApiClientImpl(
    private val baseClient: MatrixClientServerApiBaseClient
) : PushApiClient {

    override suspend fun getPushers(): Result<GetPushers.Response> =
        baseClient.request(GetPushers)

    override suspend fun setPushers(request: SetPushers.Request): Result<Unit> =
        baseClient.request(SetPushers, request)

    override suspend fun getNotifications(
        from: String?,
        limit: Long?,
        only: String?,
    ): Result<GetNotifications.Response> =
        baseClient.request(GetNotifications(from, limit, only))

    override suspend fun getPushRules(): Result<GetPushRules.Response> =
        baseClient.request(GetPushRules)

    override suspend fun getPushRule(
        scope: String,
        kind: PushRuleKind,
        ruleId: String,
    ): Result<PushRule> =
        baseClient.request(GetPushRule(scope, kind, ruleId))

    override suspend fun setPushRule(
        scope: String,
        kind: PushRuleKind,
        ruleId: String,
        pushRule: SetPushRule.Request,
        beforeRuleId: String?,
        afterRuleId: String?,
    ): Result<Unit> =
        baseClient.request(SetPushRule(scope, kind, ruleId, beforeRuleId, afterRuleId), pushRule)

    override suspend fun deletePushRule(
        scope: String,
        kind: PushRuleKind,
        ruleId: String,
    ): Result<Unit> =
        baseClient.request(DeletePushRule(scope, kind, ruleId))

    override suspend fun getPushRuleActions(
        scope: String,
        kind: PushRuleKind,
        ruleId: String,
    ): Result<Set<PushAction>> =
        baseClient.request(GetPushRuleActions(scope, kind, ruleId)).map { it.actions }

    override suspend fun setPushRuleActions(
        scope: String,
        kind: PushRuleKind,
        ruleId: String,
        actions: Set<PushAction>,
    ): Result<Unit> =
        baseClient.request(
            SetPushRuleActions(scope, kind, ruleId),
            SetPushRuleActions.Request(actions)
        )

    override suspend fun getPushRuleEnabled(
        scope: String,
        kind: PushRuleKind,
        ruleId: String,
    ): Result<Boolean> =
        baseClient.request(GetPushRuleEnabled(scope, kind, ruleId)).map { it.enabled }

    override suspend fun setPushRuleEnabled(
        scope: String,
        kind: PushRuleKind,
        ruleId: String,
        enabled: Boolean,
    ): Result<Unit> =
        baseClient.request(
            SetPushRuleEnabled(scope, kind, ruleId),
            SetPushRuleEnabled.Request(enabled)
        )
}
