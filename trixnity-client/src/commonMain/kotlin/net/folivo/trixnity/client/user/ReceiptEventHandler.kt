package net.folivo.trixnity.client.user

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import net.folivo.trixnity.client.getRoomId
import net.folivo.trixnity.client.store.RoomUserStore
import net.folivo.trixnity.clientserverapi.client.IMatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.ReceiptEventContent
import net.folivo.trixnity.core.subscribe
import net.folivo.trixnity.core.unsubscribe

class ReceiptEventHandler(
    private val api: IMatrixClientServerApiClient,
    private val roomUserStore: RoomUserStore,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribe(::setReadReceipts)
        scope.coroutineContext.job.invokeOnCompletion {
            api.sync.unsubscribe(::setReadReceipts)
        }
    }

    internal suspend fun setReadReceipts(receiptEvent: Event<ReceiptEventContent>) {
        receiptEvent.getRoomId()?.let { roomId ->
            receiptEvent.content.events.forEach { (eventId, receipts) ->
                receipts
                    .filterIsInstance<ReceiptEventContent.Receipt.ReadReceipt>()
                    .forEach { receipt ->
                        receipt.read.keys.forEach { userId ->
                            roomUserStore.update(userId, roomId) { oldRoomUser ->
                                oldRoomUser?.copy(lastReadMessage = eventId)
                            }
                        }
                    }
            }
        }
    }
}