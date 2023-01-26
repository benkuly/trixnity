package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

interface RoomUserRepository : TwoDimensionsRepository<RoomId, UserId, RoomUser> {
    override fun serializeKey(key: RoomId): String = this::class.simpleName + key.full
    override fun serializeKey(firstKey: RoomId, secondKey: UserId): String =
        serializeKey(firstKey) + secondKey.full
}