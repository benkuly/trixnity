package net.folivo.trixnity.client.user

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.client.store.RoomUserStore
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.ReceiptEventContent
import net.folivo.trixnity.core.model.events.roomIdOrNull
import net.folivo.trixnity.core.subscribeContentList
import net.folivo.trixnity.core.unsubscribeOnCompletion

private val log = KotlinLogging.logger {}

class ReceiptEventHandler(
    private val api: MatrixClientServerApiClient,
    private val roomUserStore: RoomUserStore,
    private val tm: RepositoryTransactionManager,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribeContentList(subscriber = ::setReadReceipts).unsubscribeOnCompletion(scope)
    }

    internal suspend fun setReadReceipts(receiptEvents: List<Event<ReceiptEventContent>>) {
        if (receiptEvents.isNotEmpty())
            tm.writeTransaction {
                receiptEvents.forEach { receiptEvent ->
                    receiptEvent.roomIdOrNull?.let { roomId ->
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
    }
}