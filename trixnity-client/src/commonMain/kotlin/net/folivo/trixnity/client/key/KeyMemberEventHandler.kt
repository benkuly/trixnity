package net.folivo.trixnity.client.key

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.client.user.LazyMemberEventHandler
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.ClientEventEmitter.Priority
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.subscribeEventList
import net.folivo.trixnity.core.unsubscribeOnCompletion

private val log = KotlinLogging.logger {}

class KeyMemberEventHandler(
    private val api: MatrixClientServerApiClient,
    private val roomStore: RoomStore,
    private val roomStateStore: RoomStateStore,
    private val keyStore: KeyStore,
    private val tm: RepositoryTransactionManager,
) : EventHandler, LazyMemberEventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribeEventList(Priority.AFTER_DEFAULT, subscriber = ::updateDeviceKeysFromChangedMembership)
            .unsubscribeOnCompletion(scope)
    }

    override suspend fun handleLazyMemberEvents(memberEvents: List<StateEvent<MemberEventContent>>) {
        updateDeviceKeysFromChangedMembership(memberEvents)
    }

    internal suspend fun updateDeviceKeysFromChangedMembership(events: List<StateEvent<MemberEventContent>>) =
        coroutineScope {
            val deleteDeviceKeys = mutableSetOf<UserId>()
            val updateOutdatedKeys = mutableSetOf<UserId>()
            val joinedEncryptedRooms = async(start = CoroutineStart.LAZY) { roomStore.encryptedJoinedRooms() }

            events.forEach { event ->
                if (roomStore.get(event.roomId).first()?.encrypted == true) {
                    log.trace { "update device keys from changed membership (event=$event)" }
                    val userId = UserId(event.stateKey)
                    when (event.content.membership) {
                        Membership.LEAVE, Membership.BAN -> {
                            if (keyStore.isTracked(userId)) {
                                val isActiveMemberOfAnyOtherEncryptedRoom =
                                    roomStateStore.getByRooms<MemberEventContent>(
                                        joinedEncryptedRooms.await(),
                                        userId.full
                                    )
                                        .any {
                                            val membership = it.content.membership
                                            membership == Membership.JOIN || membership == Membership.INVITE
                                        }
                                if (!isActiveMemberOfAnyOtherEncryptedRoom) {
                                    deleteDeviceKeys.add(userId)
                                }
                            }
                        }

                        Membership.JOIN, Membership.INVITE -> {
                            if (!keyStore.isTracked(userId))
                                updateOutdatedKeys.add(userId)
                        }

                        else -> {
                        }
                    }
                }
            }
            if (deleteDeviceKeys.isNotEmpty() || updateOutdatedKeys.isNotEmpty()) {
                tm.writeTransaction {
                    deleteDeviceKeys.forEach { keyStore.deleteDeviceKeys(it) }
                    keyStore.updateOutdatedKeys { it + updateOutdatedKeys }
                }
            }
            joinedEncryptedRooms.cancelAndJoin()
        }
}