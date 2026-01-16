package de.connect2x.trixnity.client.store.repository

import de.connect2x.trixnity.client.store.StoredNotificationUpdate

interface NotificationUpdateRepository : DeleteByRoomIdFullRepository<String, StoredNotificationUpdate> {
    override fun serializeKey(key: String): String = key
}