package net.folivo.trixnity.serverserverapi.server

import net.folivo.trixnity.api.server.MatrixEndpointContext
import net.folivo.trixnity.serverserverapi.model.federation.BackfillRoom
import net.folivo.trixnity.serverserverapi.model.federation.GetEventAuthChain
import net.folivo.trixnity.serverserverapi.model.federation.PduTransaction
import net.folivo.trixnity.serverserverapi.model.federation.SendTransaction

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

}