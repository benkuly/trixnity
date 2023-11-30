package net.folivo.trixnity.client.room

import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.utils.KeyedMutex

class TimelineMutex : KeyedMutex<RoomId>()