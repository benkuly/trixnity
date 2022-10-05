package net.folivo.trixnity.client.mocks

import kotlinx.coroutines.flow.MutableStateFlow
import net.folivo.trixnity.client.room.TimelineEventHandler
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId

class TimelineEventHandlerMock : TimelineEventHandler {
    val unsafeFillTimelineGaps = MutableStateFlow(false)
    override suspend fun unsafeFillTimelineGaps(startEventId: EventId, roomId: RoomId, limit: Long): Result<Unit> {
        unsafeFillTimelineGaps.value = true
        return Result.success(Unit)
    }
}