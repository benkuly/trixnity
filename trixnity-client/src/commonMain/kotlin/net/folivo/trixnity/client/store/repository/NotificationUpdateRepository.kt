package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.StoredNotificationUpdate

interface NotificationUpdateRepository : DeleteByRoomIdFullRepository<String, StoredNotificationUpdate> {
    override fun serializeKey(key: String): String = key
}