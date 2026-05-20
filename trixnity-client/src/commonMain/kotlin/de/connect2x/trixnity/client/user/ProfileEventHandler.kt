package de.connect2x.trixnity.client.user

import de.connect2x.lognity.api.logger.Logger
import de.connect2x.lognity.api.logger.warn
import de.connect2x.trixnity.client.CurrentSyncState
import de.connect2x.trixnity.client.store.AccountStore
import de.connect2x.trixnity.client.utils.retryLoop
import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClient
import de.connect2x.trixnity.core.ClientEventEmitter.Priority
import de.connect2x.trixnity.core.EventHandler
import de.connect2x.trixnity.core.UserInfo
import de.connect2x.trixnity.core.model.events.ClientEvent.StateBaseEvent
import de.connect2x.trixnity.core.model.events.m.room.MemberEventContent
import de.connect2x.trixnity.core.subscribeEventList
import de.connect2x.trixnity.core.unsubscribeOnCompletion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlin.time.Duration.Companion.minutes

private val log = Logger("de.connect2x.trixnity.client.user.ProfileEventHandler")

class ProfileEventHandler(
    private val api: MatrixClientServerApiClient,
    private val accountStore: AccountStore,
    private val userInfo: UserInfo,
    private val currentSyncState: CurrentSyncState
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribeEventList(Priority.DEFAULT, ::handleMemberEvents).unsubscribeOnCompletion(scope)
        scope.launch { updateProfile() }
    }

    private val suggestProfileUpdate = MutableStateFlow(false)

    internal suspend fun handleMemberEvents(events: List<StateBaseEvent<MemberEventContent>>) {
        if (events.any { it.stateKey == userInfo.userId.full }) {
            log.debug { "suggest reload own profile as there has been member events of us" }
            suggestProfileUpdate.value = true
        }
    }

    private val repeatDelay = 1.minutes

    @OptIn(FlowPreview::class)
    internal suspend fun updateProfile() {
        currentSyncState.retryLoop(
            onError = { error, delay -> log.warn(error) { "failed retrieve current profile, try again in $delay" } },
        ) {
            coroutineScope {
                select {
                    launch {
                        delay(repeatDelay)
                    }.onJoin {}
                    launch {
                        suggestProfileUpdate.first { it }
                        suggestProfileUpdate.value = false
                    }.onJoin {}
                }
                currentCoroutineContext().cancelChildren()
            }
            api.user.getProfile(userInfo.userId)
                .onSuccess { profile ->
                    accountStore.updateAccount { account ->
                        account?.copy(profile = profile)
                    }
                }.getOrThrow()
        }
    }
}
