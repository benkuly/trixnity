package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.StoredNotification

interface NotificationRepository : DeleteByRoomIdFullRepository<String, StoredNotification> {
    override fun serializeKey(key: String): String = key
}