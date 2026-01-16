package de.connect2x.trixnity.clientserverapi.server

import de.connect2x.trixnity.api.server.MatrixEndpointContext
import de.connect2x.trixnity.clientserverapi.model.push.*
import de.connect2x.trixnity.core.model.push.PushRule

interface PushApiHandler {
    /**
     * @see [GetPushers]
     */
    suspend fun getPushers(context: MatrixEndpointContext<GetPushers, Unit, GetPushers.Response>): GetPushers.Response

    /**
     * @see [SetPushers]
     */
    suspend fun setPushers(context: MatrixEndpointContext<SetPushers, SetPushers.Request, Unit>)

    /**
     * @see [GetNotifications]
     */
    suspend fun getNotifications(context: MatrixEndpointContext<GetNotifications, Unit, GetNotifications.Response>): GetNotifications.Response

    /**
     * @see [GetPushRules]
     */
    suspend fun getPushRules(context: MatrixEndpointContext<GetPushRules, Unit, GetPushRules.Response>): GetPushRules.Response

    /**
     * @see [GetPushRule]
     */
    suspend fun getPushRule(context: MatrixEndpointContext<GetPushRule, Unit, PushRule>): PushRule

    /**
     * @see [SetPushRule]
     */
    suspend fun setPushRule(context: MatrixEndpointContext<SetPushRule, SetPushRule.Request, Unit>)

    /**
     * @see [DeletePushRule]
     */
    suspend fun deletePushRule(context: MatrixEndpointContext<DeletePushRule, Unit, Unit>)

    /**
     * @see [GetPushRuleActions]
     */
    suspend fun getPushRuleActions(context: MatrixEndpointContext<GetPushRuleActions, Unit, GetPushRuleActions.Response>): GetPushRuleActions.Response

    /**
     * @see [SetPushRuleActions]
     */
    suspend fun setPushRuleActions(context: MatrixEndpointContext<SetPushRuleActions, SetPushRuleActions.Request, Unit>)

    /**
     * @see [GetPushRuleEnabled]
     */
    suspend fun getPushRuleEnabled(context: MatrixEndpointContext<GetPushRuleEnabled, Unit, GetPushRuleEnabled.Response>): GetPushRuleEnabled.Response

    /**
     * @see [SetPushRuleEnabled]
     */
    suspend fun setPushRuleEnabled(context: MatrixEndpointContext<SetPushRuleEnabled, SetPushRuleEnabled.Request, Unit>)
}