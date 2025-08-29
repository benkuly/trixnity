package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.StoredNotificationState
import net.folivo.trixnity.core.model.RoomId

interface NotificationStateRepository : FullRepository<RoomId, StoredNotificationState> {
    override fun serializeKey(key: RoomId): String = key.full
}