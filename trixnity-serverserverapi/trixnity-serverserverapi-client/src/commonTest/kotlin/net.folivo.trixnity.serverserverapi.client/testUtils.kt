package de.connect2x.trixnity.serverserverapi.client

import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.PersistentDataUnit
import de.connect2x.trixnity.core.serialization.events.RoomVersionStore

fun String.trimToFlatJson() = this.trimIndent().lines().joinToString("") { it.replace(": ", ":").trim() }

class TestRoomVersionStore(val roomVersion: String) : RoomVersionStore {
    override fun getRoomVersion(roomId: RoomId): String = roomVersion

    override fun setRoomVersion(
        pdu: PersistentDataUnit.PersistentStateDataUnit<*>,
        roomVersion: String
    ) {
    }
}