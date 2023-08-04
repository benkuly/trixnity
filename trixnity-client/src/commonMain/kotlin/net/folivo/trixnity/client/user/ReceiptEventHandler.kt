package net.folivo.trixnity.client.user

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import net.folivo.trixnity.client.getRoomId
import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.client.store.RoomUserStore
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.client.utils.filter
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.ReceiptEventContent

private val log = KotlinLogging.logger {}

class ReceiptEventHandler(
    private val api: MatrixClientServerApiClient,
    private val roomUserStore: RoomUserStore,
    private val tm: RepositoryTransactionManager,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribeLastInSyncProcessing(::setState)
        scope.coroutineContext.job.invokeOnCompletion {
            api.sync.unsubscribeLastInSyncProcessing(::setState)
        }
    }

    internal suspend fun setState(syncResponse: Sync.Response) = tm.writeTransaction {
        syncResponse.filter<ReceiptEventContent>().collect {
            setReadReceipts(it)
        }
    }

    internal suspend fun setReadReceipts(receiptEvent: Event<ReceiptEventContent>) {
        receiptEvent.getRoomId()?.let { roomId ->
            log.debug { "set read receipts of room $roomId" }
            receiptEvent.content.events.forEach { (eventId, receipts) ->
                receipts
                    .forEach { (receiptType, receipts) ->
                        receipts.forEach { (userId, receipt) ->
                            roomUserStore.update(userId, roomId) { oldRoomUser ->
                                oldRoomUser?.copy(
                                    receipts = oldRoomUser.receipts +
                                            (receiptType to RoomUser.RoomUserReceipt(eventId, receipt))
                                )
                            }
                        }
                    }
            }
        }
    }
}