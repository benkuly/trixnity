package net.folivo.trixnity.serverserverapi.server

import net.folivo.trixnity.api.server.MatrixEndpointContext
import net.folivo.trixnity.serverserverapi.model.transaction.SendTransaction

interface TransactionApiHandler {
    /**
     * @see <a href="https://spec.matrix.org/v1.2/server-server-api/#put_matrixfederationv1sendtxnid">matrix spec</a>
     */
    suspend fun sendTransaction(context: MatrixEndpointContext<SendTransaction, SendTransaction.Request, SendTransaction.Response>): SendTransaction.Response

}