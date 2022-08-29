package net.folivo.trixnity.client.room

import net.folivo.trixnity.core.model.events.Event

typealias LastRelevantEventFilter = (Event.RoomEvent<*>) -> Boolean