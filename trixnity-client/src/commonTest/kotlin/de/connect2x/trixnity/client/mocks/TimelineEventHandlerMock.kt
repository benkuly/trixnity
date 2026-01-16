package de.connect2x.trixnity.client.mocks

import kotlinx.coroutines.flow.MutableStateFlow
import de.connect2x.trixnity.client.room.TimelineEventHandler
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId

class TimelineEventHandlerMock : TimelineEventHandler {
    val unsafeFillTimelineGaps = MutableStateFlow(false)
    override suspend fun unsafeFillTimelineGaps(startEventId: EventId, roomId: RoomId, limit: Long): Result<Unit> {
        unsafeFillTimelineGaps.value = true
        return Result.success(Unit)
    }
}