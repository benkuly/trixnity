package net.folivo.trixnity.serverserverapi.server

import net.folivo.trixnity.api.server.MatrixEndpointContext
import net.folivo.trixnity.core.model.events.PersistentDataUnit.PersistentStateDataUnit
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.keys.Signed
import net.folivo.trixnity.serverserverapi.model.federation.*

interface FederationApiHandler {
    /**
     * @see <a href="https://spec.matrix.org/v1.2/server-server-api/#put_matrixfederationv1sendtxnid">matrix spec</a>
     */
    suspend fun sendTransaction(context: MatrixEndpointContext<SendTransaction, SendTransaction.Request, SendTransaction.Response>): SendTransaction.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/server-server-api/#get_matrixfederationv1event_authroomideventid">matrix spec</a>
     */
    suspend fun getEventAuthChain(context: MatrixEndpointContext<GetEventAuthChain, Unit, GetEventAuthChain.Response>): GetEventAuthChain.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/server-server-api/#get_matrixfederationv1backfillroomid">matrix spec</a>
     */
    suspend fun backfillRoom(context: MatrixEndpointContext<BackfillRoom, Unit, PduTransaction>): PduTransaction

    /**
     * @see <a href="https://spec.matrix.org/v1.2/server-server-api/#post_matrixfederationv1get_missing_eventsroomid">matrix spec</a>
     */
    suspend fun getMissingEvents(context: MatrixEndpointContext<GetMissingEvents, GetMissingEvents.Request, PduTransaction>): PduTransaction

    /**
     * @see <a href="https://spec.matrix.org/v1.2/server-server-api/#get_matrixfederationv1eventeventid">matrix spec</a>
     */
    suspend fun getEvent(context: MatrixEndpointContext<GetEvent, Unit, PduTransaction>): PduTransaction

    /**
     * @see <a href="https://spec.matrix.org/v1.2/server-server-api/#get_matrixfederationv1stateroomid">matrix spec</a>
     */
    suspend fun getState(context: MatrixEndpointContext<GetState, Unit, GetState.Response>): GetState.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/server-server-api/#get_matrixfederationv1state_idsroomid">matrix spec</a>
     */
    suspend fun getStateIds(context: MatrixEndpointContext<GetStateIds, Unit, GetStateIds.Response>): GetStateIds.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/server-server-api/#get_matrixfederationv1make_joinroomiduserid">matrix spec</a>
     */
    suspend fun makeJoin(context: MatrixEndpointContext<MakeJoin, Unit, MakeJoin.Response>): MakeJoin.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/server-server-api/#put_matrixfederationv2send_joinroomideventid">matrix spec</a>
     */
    suspend fun sendJoin(context: MatrixEndpointContext<SendJoin, Signed<PersistentStateDataUnit<MemberEventContent>, String>, SendJoin.Response>): SendJoin.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/server-server-api/#get_matrixfederationv1make_knockroomiduserid">matrix spec</a>
     */
    suspend fun makeKnock(context: MatrixEndpointContext<MakeKnock, Unit, MakeKnock.Response>): MakeKnock.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/server-server-api/#put_matrixfederationv1send_knockroomideventid">matrix spec</a>
     */
    suspend fun sendKnock(context: MatrixEndpointContext<SendKnock, Signed<PersistentStateDataUnit<MemberEventContent>, String>, SendKnock.Response>): SendKnock.Response
}