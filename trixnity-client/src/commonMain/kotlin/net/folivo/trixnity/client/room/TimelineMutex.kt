package net.folivo.trixnity.client.room

import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.utils.KeyedMutex
import net.folivo.trixnity.utils.KeyedMutexImpl

class TimelineMutex : KeyedMutex<RoomId> by KeyedMutexImpl()