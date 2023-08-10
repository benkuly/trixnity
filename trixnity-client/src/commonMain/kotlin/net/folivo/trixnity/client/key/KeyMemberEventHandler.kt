package net.folivo.trixnity.client.key

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.job
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.client.user.LazyMemberEventHandler
import net.folivo.trixnity.client.utils.filter
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncProcessingData
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm

private val log = KotlinLogging.logger {}

class KeyMemberEventHandler(
    private val api: MatrixClientServerApiClient,
    private val roomStore: RoomStore,
    private val roomStateStore: RoomStateStore,
    private val keyStore: KeyStore,
    private val tm: RepositoryTransactionManager,
) : EventHandler, LazyMemberEventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.afterSyncProcessing.subscribe(::handleSyncResponse)
        scope.coroutineContext.job.invokeOnCompletion {
            api.sync.afterSyncProcessing.unsubscribe(::handleSyncResponse)
        }
    }

    internal suspend fun handleSyncResponse(syncProcessingData: SyncProcessingData) {
        updateDeviceKeysFromChangedMembership(
            syncProcessingData.allEvents.filter<MemberEventContent, Event.StateEvent<MemberEventContent>>().toList()
        )
    }

    override suspend fun handleLazyMemberEvents(memberEvents: List<Event<MemberEventContent>>) {
        updateDeviceKeysFromChangedMembership(memberEvents.filterIsInstance<Event.StateEvent<MemberEventContent>>())
    }

    internal suspend fun updateDeviceKeysFromChangedMembership(events: List<Event.StateEvent<MemberEventContent>>) {
        val deleteDeviceKeys = mutableSetOf<UserId>()
        val updateOutdatedKeys = mutableSetOf<UserId>()
        events.forEach { event ->
            if (roomStore.get(event.roomId).first()?.encryptionAlgorithm == EncryptionAlgorithm.Megolm) {
                log.trace { "handle membership change in an encrypted room" }
                val userId = UserId(event.stateKey)
                when (event.content.membership) {
                    Membership.LEAVE, Membership.BAN -> {
                        if (roomStore.encryptedJoinedRooms().find { roomId ->
                                roomStateStore.getByStateKey<MemberEventContent>(roomId, event.stateKey).first()
                                    ?.content?.membership.let { it == Membership.JOIN || it == Membership.INVITE }
                            } == null)
                            deleteDeviceKeys.add(userId)
                    }

                    Membership.JOIN, Membership.INVITE -> {
                        if (event.unsigned?.previousContent?.membership != event.content.membership
                            && keyStore.getDeviceKeys(userId).first() == null
                        ) updateOutdatedKeys.add(userId)
                    }

                    else -> {
                    }
                }
            }
        }
        if (deleteDeviceKeys.isNotEmpty() || updateOutdatedKeys.isNotEmpty())
            tm.writeTransaction {
                deleteDeviceKeys.forEach { keyStore.deleteDeviceKeys(it) }
                keyStore.updateOutdatedKeys { it + updateOutdatedKeys }
            }
    }
}