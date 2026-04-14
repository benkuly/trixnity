package de.connect2x.trixnity.client.room

import de.connect2x.trixnity.client.store.NotificationStore
import de.connect2x.trixnity.client.store.RoomAccountDataStore
import de.connect2x.trixnity.client.store.RoomOutboxMessageStore
import de.connect2x.trixnity.client.store.RoomStateStore
import de.connect2x.trixnity.client.store.RoomStore
import de.connect2x.trixnity.client.store.RoomTimelineStore
import de.connect2x.trixnity.client.store.RoomUserStore
import de.connect2x.trixnity.client.store.StickyEventStore
import de.connect2x.trixnity.client.store.TransactionManager
import de.connect2x.trixnity.core.MSC4354
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.room.Membership
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

fun interface ForgetRoomService {
    suspend operator fun invoke(roomId: RoomId, force: Boolean)
}

@OptIn(MSC4354::class)
class ForgetRoomServiceImpl(
    private val roomStore: RoomStore,
    private val roomUserStore: RoomUserStore,
    private val roomStateStore: RoomStateStore,
    private val roomAccountDataStore: RoomAccountDataStore,
    private val roomTimelineStore: RoomTimelineStore,
    private val stickyEventStore: StickyEventStore,
    private val roomOutboxMessageStore: RoomOutboxMessageStore,
    private val notificationStore: NotificationStore,
    private val transactionManager: TransactionManager,
) : ForgetRoomService {
    override suspend fun invoke(roomId: RoomId, force: Boolean) {
        if (force || roomStore.get(roomId).first()?.membership == Membership.LEAVE) {
            withContext(NonCancellable) {
                transactionManager.writeTransaction {
                    roomStore.delete(roomId)
                    roomTimelineStore.deleteByRoomId(roomId)
                    roomStateStore.deleteByRoomId(roomId)
                    roomAccountDataStore.deleteByRoomId(roomId)
                    roomUserStore.deleteByRoomId(roomId)
                    stickyEventStore.deleteByRoomId(roomId)
                    roomOutboxMessageStore.deleteByRoomId(roomId)
                    notificationStore.deleteByRoomId(roomId)
                }
            }
        }
    }
}
