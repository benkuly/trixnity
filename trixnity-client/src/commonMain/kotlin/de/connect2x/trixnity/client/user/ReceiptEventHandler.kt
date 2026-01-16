package de.connect2x.trixnity.client.user

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import de.connect2x.trixnity.client.store.RoomUserReceipts
import de.connect2x.trixnity.client.store.RoomUserStore
import de.connect2x.trixnity.client.store.TransactionManager
import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClient
import de.connect2x.trixnity.clientserverapi.client.SyncEvents
import de.connect2x.trixnity.core.ClientEventEmitter.Priority
import de.connect2x.trixnity.core.EventHandler
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.m.ReceiptEventContent
import de.connect2x.trixnity.core.model.events.m.ReceiptType
import de.connect2x.trixnity.core.model.events.roomIdOrNull
import de.connect2x.trixnity.core.subscribeContentList
import de.connect2x.trixnity.core.unsubscribeOnCompletion

private val log = KotlinLogging.logger("de.connect2x.trixnity.client.user.ReceiptEventHandler")

class ReceiptEventHandler(
    private val api: MatrixClientServerApiClient,
    private val roomUserStore: RoomUserStore,
    private val tm: TransactionManager,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribeContentList(Priority.STORE_EVENTS, subscriber = ::setReadReceipts)
            .unsubscribeOnCompletion(scope)
        api.sync.subscribe(Priority.STORE_EVENTS, subscriber = ::deleteReadReceiptsOnNonJoin)
            .unsubscribeOnCompletion(scope)
    }

    internal suspend fun setReadReceipts(receiptEvents: List<ClientEvent<ReceiptEventContent>>) {
        if (receiptEvents.isNotEmpty())
            tm.writeTransaction {
                receiptEvents.forEach { receiptEvent ->
                    receiptEvent.roomIdOrNull?.let { roomId ->
                        log.trace { "set read receipts of room $roomId" }
                        data class UserReceipt(
                            val userId: UserId,
                            val type: ReceiptType,
                            val receipt: RoomUserReceipts.Receipt,
                        )

                        val flattenReceipts = receiptEvent.content.events.flatMap { (eventId, receiptsByType) ->
                            receiptsByType.flatMap { (type, receiptsByUser) ->
                                receiptsByUser.map { (user, receipt) ->
                                    UserReceipt(user, type, RoomUserReceipts.Receipt(eventId, receipt))
                                }
                            }
                        }
                        flattenReceipts.groupBy { it.userId }
                            .forEach { (userId, userReceipts) ->
                                val receipts = userReceipts.groupBy { it.type }.mapValues { it.value.last().receipt }
                                roomUserStore.updateReceipts(userId, roomId) { oldRoomUserReceipts ->
                                    oldRoomUserReceipts?.copy(receipts = oldRoomUserReceipts.receipts + receipts)
                                        ?: RoomUserReceipts(roomId, userId, receipts)
                                }
                            }
                    }
                }
            }
    }

    internal suspend fun deleteReadReceiptsOnNonJoin(syncEvents: SyncEvents) {
        syncEvents.syncResponse.room?.invite?.keys?.forEach { roomId ->
            roomUserStore.deleteReceiptsByRoomId(roomId)
        }
        syncEvents.syncResponse.room?.knock?.keys?.forEach { roomId ->
            roomUserStore.deleteReceiptsByRoomId(roomId)
        }
        syncEvents.syncResponse.room?.leave?.keys?.forEach { roomId ->
            roomUserStore.deleteReceiptsByRoomId(roomId)
        }
    }
}