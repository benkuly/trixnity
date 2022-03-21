package net.folivo.trixnity.clientserverapi.server

import net.folivo.trixnity.api.server.MatrixEndpointContext
import net.folivo.trixnity.clientserverapi.model.push.*
import net.folivo.trixnity.core.model.push.PushRule

interface PushApiHandler {
    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3pushers">matrix spec</a>
     */
    suspend fun getPushers(context: MatrixEndpointContext<GetPushers, Unit, GetPushers.Response>): GetPushers.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3pushersset">matrix spec</a>
     */
    suspend fun setPushers(context: MatrixEndpointContext<SetPushers, SetPushers.Request, Unit>)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3notifications">matrix spec</a>
     */
    suspend fun getNotifications(context: MatrixEndpointContext<GetNotifications, Unit, GetNotifications.Response>): GetNotifications.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3pushrules">matrix spec</a>
     */
    suspend fun getPushRules(context: MatrixEndpointContext<GetPushRules, Unit, GetPushRules.Response>): GetPushRules.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3pushrulesscopekindruleid">matrix spec</a>
     */
    suspend fun getPushRule(context: MatrixEndpointContext<GetPushRule, Unit, PushRule>): PushRule

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3pushrulesscopekindruleid">matrix spec</a>
     */
    suspend fun setPushRule(context: MatrixEndpointContext<SetPushRule, SetPushRule.Request, Unit>)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3pushrulesscopekindruleid">matrix spec</a>
     */
    suspend fun deletePushRule(context: MatrixEndpointContext<DeletePushRule, Unit, Unit>)
}