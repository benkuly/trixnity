package net.folivo.trixnity.client.user

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import net.folivo.trixnity.client.CurrentSyncState
import net.folivo.trixnity.client.store.RoomStore
import net.folivo.trixnity.client.utils.retryWhenSyncIs
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncEvents
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.m.room.Membership

private val log = KotlinLogging.logger {}

fun interface LoadMembersService {
    suspend operator fun invoke(roomId: RoomId, wait: Boolean)
}

class LoadMembersServiceImpl(
    private val roomStore: RoomStore,
    private val lazyMemberEventHandlers: List<LazyMemberEventHandler>,
    private val currentSyncState: CurrentSyncState,
    private val api: MatrixClientServerApiClient,
    private val scope: CoroutineScope,
) : LoadMembersService {
    private val currentlyLoadingMembers = MutableStateFlow<Set<RoomId>>(setOf())
    override suspend fun invoke(roomId: RoomId, wait: Boolean) {
        if (currentlyLoadingMembers.getAndUpdate { it + roomId }.contains(roomId).not()) {
            scope.launch {
                currentSyncState.retryWhenSyncIs(
                    SyncState.RUNNING,
                    onError = { log.warn(it) { "failed loading members" } },
                ) {
                    val room = roomStore.get(roomId).first()

                    if (room?.membersLoaded == false) {
                        log.debug { "load members of room $roomId" }
                        val memberEvents = api.room.getMembers(
                            roomId = roomId,
                            notMembership = Membership.LEAVE
                        ).getOrThrow()
                        memberEvents.chunked(50).forEach { chunk ->
                            lazyMemberEventHandlers.forEach {
                                it.handleLazyMemberEvents(chunk)
                            }
                            // TODO is there a nicer way? Maybe some sort of merged EventEmitter (including lazy members)
                            api.sync.emit(SyncEvents(Sync.Response(""), chunk))
                            yield()
                        }
                        roomStore.update(roomId) { it?.copy(membersLoaded = true) }
                    }
                }
                currentlyLoadingMembers.update { it - roomId }
            }
        }
        if (wait) roomStore.get(roomId).first { it == null || it.membersLoaded }
    }
}