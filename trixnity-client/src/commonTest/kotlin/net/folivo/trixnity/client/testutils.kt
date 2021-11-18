package net.folivo.trixnity.client

import kotlinx.datetime.Clock
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.core.model.MatrixId

val simpleRoom =
    Room(MatrixId.RoomId("room", "server"), lastMessageEventAt = Clock.System.now(), lastEventId = MatrixId.EventId("\$event"))