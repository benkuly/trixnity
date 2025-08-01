package net.folivo.trixnity.serverserverapi.client

import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.PersistentDataUnit
import net.folivo.trixnity.core.serialization.events.RoomVersionStore

fun String.trimToFlatJson() = this.trimIndent().lines().joinToString("") { it.replace(": ", ":").trim() }

class TestRoomVersionStore(val roomVersion: String) : RoomVersionStore {
    override fun getRoomVersion(roomId: RoomId): String = roomVersion

    override fun setRoomVersion(
        pdu: PersistentDataUnit.PersistentStateDataUnit<*>,
        roomVersion: String
    ) {
    }
}