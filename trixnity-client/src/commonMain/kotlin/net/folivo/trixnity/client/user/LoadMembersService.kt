package net.folivo.trixnity.client.user

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import net.folivo.trixnity.client.CurrentSyncState
import net.folivo.trixnity.client.store.RoomStore
import net.folivo.trixnity.client.utils.retry
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncEvents
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.m.room.Membership

private val log = KotlinLogging.logger("net.folivo.trixnity.client.user.LoadMembersService")

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
                currentSyncState.retry(
                    onError = { error, delay -> log.warn(error) { "failed loading members, try again in $delay" } },
                ) {
                    val room = roomStore.get(roomId).filterNotNull().first()

                    if (!room.membersLoaded) {
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
        if (wait) roomStore.get(roomId).first { it?.membersLoaded == true }
    }
}