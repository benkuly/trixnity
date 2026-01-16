package de.connect2x.trixnity.client.store.repository

import de.connect2x.trixnity.client.store.StoredNotificationState
import de.connect2x.trixnity.core.model.RoomId

interface NotificationStateRepository : FullRepository<RoomId, StoredNotificationState> {
    override fun serializeKey(key: RoomId): String = key.full
}