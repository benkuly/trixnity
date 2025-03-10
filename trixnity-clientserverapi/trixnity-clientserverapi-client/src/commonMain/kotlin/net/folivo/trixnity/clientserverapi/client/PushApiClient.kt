package net.folivo.trixnity.clientserverapi.client

import net.folivo.trixnity.clientserverapi.model.push.*
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.push.PushAction
import net.folivo.trixnity.core.model.push.PushRule
import net.folivo.trixnity.core.model.push.PushRuleKind

interface PushApiClient {
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

class PushApiClientImpl(
    private val baseClient: MatrixClientServerApiBaseClient
) : PushApiClient {

    override suspend fun getPushers(asUserId: UserId?): Result<GetPushers.Response> =
        baseClient.request(GetPushers(asUserId))

    override suspend fun setPushers(request: SetPushers.Request, asUserId: UserId?): Result<Unit> =
        baseClient.request(SetPushers(asUserId), request)

    override suspend fun getNotifications(
        from: String?,
        limit: Long?,
        only: String?,
        asUserId: UserId?,
    ): Result<GetNotifications.Response> =
        baseClient.request(GetNotifications(from, limit, only, asUserId))

    override suspend fun getPushRules(asUserId: UserId?): Result<GetPushRules.Response> =
        baseClient.request(GetPushRules(asUserId))

    override suspend fun getPushRule(
        scope: String,
        kind: PushRuleKind,
        ruleId: String,
        asUserId: UserId?,
    ): Result<PushRule> =
        baseClient.request(GetPushRule(scope, kind, ruleId, asUserId))

    override suspend fun setPushRule(
        scope: String,
        kind: PushRuleKind,
        ruleId: String,
        pushRule: SetPushRule.Request,
        beforeRuleId: String?,
        afterRuleId: String?,
        asUserId: UserId?,
    ): Result<Unit> =
        baseClient.request(SetPushRule(scope, kind, ruleId, beforeRuleId, afterRuleId, asUserId), pushRule)

    override suspend fun deletePushRule(
        scope: String,
        kind: PushRuleKind,
        ruleId: String,
        asUserId: UserId?,
    ): Result<Unit> =
        baseClient.request(DeletePushRule(scope, kind, ruleId, asUserId))

    override suspend fun getPushRuleActions(
        scope: String,
        kind: PushRuleKind,
        ruleId: String,
        asUserId: UserId?,
    ): Result<Set<PushAction>> =
        baseClient.request(GetPushRuleActions(scope, kind, ruleId, asUserId)).map { it.actions }

    override suspend fun setPushRuleActions(
        scope: String,
        kind: PushRuleKind,
        ruleId: String,
        actions: Set<PushAction>,
        asUserId: UserId?,
    ): Result<Unit> =
        baseClient.request(
            SetPushRuleActions(scope, kind, ruleId, asUserId),
            SetPushRuleActions.Request(actions)
        )

    override suspend fun getPushRuleEnabled(
        scope: String,
        kind: PushRuleKind,
        ruleId: String,
        asUserId: UserId?,
    ): Result<Boolean> =
        baseClient.request(GetPushRuleEnabled(scope, kind, ruleId, asUserId)).map { it.enabled }

    override suspend fun setPushRuleEnabled(
        scope: String,
        kind: PushRuleKind,
        ruleId: String,
        enabled: Boolean,
        asUserId: UserId?,
    ): Result<Unit> =
        baseClient.request(
            SetPushRuleEnabled(scope, kind, ruleId, asUserId),
            SetPushRuleEnabled.Request(enabled)
        )
}
