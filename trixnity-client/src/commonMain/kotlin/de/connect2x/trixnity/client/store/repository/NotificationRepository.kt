package de.connect2x.trixnity.client.store.repository

import de.connect2x.trixnity.client.store.StoredNotification

interface NotificationRepository : DeleteByRoomIdFullRepository<String, StoredNotification> {
    override fun serializeKey(key: String): String = key
}