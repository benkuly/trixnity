package net.folivo.trixnity.client.user

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import net.folivo.trixnity.client.store.RoomUserReceipts
import net.folivo.trixnity.client.store.RoomUserStore
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.m.ReceiptEventContent
import net.folivo.trixnity.core.model.events.m.ReceiptType
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

    internal suspend fun setReadReceipts(receiptEvents: List<ClientEvent<ReceiptEventContent>>) {
        if (receiptEvents.isNotEmpty())
            tm.writeTransaction {
                receiptEvents.forEach { receiptEvent ->
                    receiptEvent.roomIdOrNull?.let { roomId ->
                        log.debug { "set read receipts of room $roomId" }
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
}