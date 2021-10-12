package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.RoomTimelineKey
import net.folivo.trixnity.client.store.TimelineEvent

typealias RoomTimelineRepository = MinimalStoreRepository<RoomTimelineKey, TimelineEvent>