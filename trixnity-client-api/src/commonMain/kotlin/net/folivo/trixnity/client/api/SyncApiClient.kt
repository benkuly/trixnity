package net.folivo.trixnity.client.api

import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.http.HttpMethod.Companion.Get
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import net.folivo.trixnity.client.api.SyncApiClient.SyncState.*
import net.folivo.trixnity.client.api.model.sync.SyncResponse
import net.folivo.trixnity.core.EventEmitter
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.PresenceEventContent.Presence
import kotlin.coroutines.cancellation.CancellationException

typealias SyncResponseSubscriber = suspend (SyncResponse) -> Unit

typealias AfterSyncResponseSubscriber = suspend () -> Unit

private val log = KotlinLogging.logger {}

class SyncApiClient(
    private val httpClient: MatrixHttpClient,
) : EventEmitter() {

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#get_matrixclientv3sync">matrix spec</a>
     */
    suspend fun syncOnce(
        filter: String? = null,
        since: String? = null,
        fullState: Boolean = false,
        setPresence: Presence? = null,
        timeout: Long = 0,
        asUserId: UserId? = null
    ): Result<SyncResponse> =
        httpClient.request {
            method = Get
            url("/_matrix/client/v3/sync")
            parameter("filter", filter)
            if (fullState) parameter("full_state", true)
            parameter("set_presence", setPresence?.value)
            parameter("since", since)
            if (timeout > 0) parameter("timeout", timeout)
            parameter("user_id", asUserId)
            timeout {
                requestTimeoutMillis = timeout + 5000
            }
        }

    internal fun syncLoop(
        filter: String? = null,
        setPresence: Presence? = null,
        currentBatchToken: MutableStateFlow<String?> = MutableStateFlow(null),
        timeout: Long = 30000,
        asUserId: UserId? = null
    ): Flow<SyncResponse> {
        return flow {
            while (currentCoroutineContext().isActive && _currentSyncState.value != STOPPING) {
                try {
                    val batchToken = currentBatchToken.value
                    val response = syncOnce(
                        filter = filter,
                        setPresence = setPresence,
                        fullState = false,
                        since = batchToken,
                        timeout = if (currentBatchToken.value == null) 0L else timeout,
                        asUserId = asUserId
                    ).getOrThrow()
                    emit(response)
                    currentBatchToken.value = response.nextBatch
                    updateSyncState(RUNNING)
                } catch (error: Throwable) {
                    when (error) {
                        is HttpRequestTimeoutException -> updateSyncState(TIMEOUT)
                        is CancellationException -> throw error
                        else -> updateSyncState(ERROR)
                    }
                    log.info { "error while sync to server: $error" }
                    log.debug { error.stackTraceToString() }
                    delay(5000)// TODO better retry policy!
                    if (currentBatchToken.value == null) updateSyncState(INITIAL_SYNC) else updateSyncState(STARTED)
                }
            }
        }
    }


    private var syncJob: Job? = null
    private val startStopMutex = Mutex()

    private val _currentSyncState: MutableStateFlow<SyncState> = MutableStateFlow(SyncState.STOPPED)
    val currentSyncState = _currentSyncState.asStateFlow()

    private val syncResponseSubscribers: MutableStateFlow<Set<SyncResponseSubscriber>> = MutableStateFlow(setOf())

    fun subscribeSyncResponse(subscriber: SyncResponseSubscriber) {
        syncResponseSubscribers.update { it + subscriber }
    }

    fun unsubscribeSyncResponse(subscriber: SyncResponseSubscriber) {
        syncResponseSubscribers.update { it - subscriber }
    }

    private val afterSyncResponseSubscribers: MutableStateFlow<Set<AfterSyncResponseSubscriber>> =
        MutableStateFlow(setOf())

    fun subscribeAfterSyncResponse(subscriber: AfterSyncResponseSubscriber) {
        afterSyncResponseSubscribers.update { it + subscriber }
    }

    fun unsubscribeAfterSyncResponse(subscriber: AfterSyncResponseSubscriber) {
        afterSyncResponseSubscribers.update { it - subscriber }
    }

    enum class SyncState {
        INITIAL_SYNC,
        STARTED,
        RUNNING,
        ERROR,
        TIMEOUT,
        STOPPING,
        STOPPED,
    }

    suspend fun start(
        filter: String? = null,
        setPresence: Presence? = null,
        currentBatchToken: MutableStateFlow<String?> = MutableStateFlow(null),
        timeout: Long = 30000,
        asUserId: UserId? = null,
        wait: Boolean = false,
        scope: CoroutineScope
    ) {
        stop(wait = true)
        startStopMutex.withLock {
            syncJob = scope.launch {
                log.info { "started syncLoop" }
                val isInitialSync = currentBatchToken.value == null
                if (isInitialSync) updateSyncState(INITIAL_SYNC) else updateSyncState(STARTED)

                try {
                    syncLoop(filter, setPresence, currentBatchToken, timeout, asUserId)
                        .collect { response ->
                            // this scope forces, that we wait for processing events of all subscribers
                            coroutineScope {
                                // do it at first, to be able to decrypt stuff
                                response.toDevice?.events?.forEach { emitEvent(it) }
                                response.accountData?.events?.forEach { emitEvent(it) }
                                coroutineScope {
                                    launch { response.presence?.events?.forEach { emitEvent(it) } }
                                    launch {
                                        response.room?.join?.forEach { (_, joinedRoom) ->
                                            joinedRoom.state?.events?.forEach { emitEvent(it) }
                                            joinedRoom.timeline?.events?.forEach { emitEvent(it) }
                                            joinedRoom.ephemeral?.events?.forEach { emitEvent(it) }
                                            joinedRoom.accountData?.events?.forEach { emitEvent(it) }
                                        }
                                    }
                                    launch {
                                        response.room?.invite?.forEach { (_, invitedRoom) ->
                                            invitedRoom.inviteState?.events?.forEach { emitEvent(it) }
                                        }
                                    }
                                    launch {
                                        response.room?.knock?.forEach { (_, invitedRoom) ->
                                            invitedRoom.knockState?.events?.forEach { emitEvent(it) }
                                        }
                                    }
                                    launch {
                                        response.room?.leave?.forEach { (_, leftRoom) ->
                                            leftRoom.state?.events?.forEach { emitEvent(it) }
                                            leftRoom.timeline?.events?.forEach { emitEvent(it) }
                                            leftRoom.accountData?.events?.forEach { emitEvent(it) }
                                        }
                                    }
                                }
                                coroutineScope {
                                    syncResponseSubscribers.value.forEach { launch { it.invoke(response) } }
                                }
                                afterSyncResponseSubscribers.value.forEach { launch { it.invoke() } }
                            }
                            log.debug { "processed sync response" }
                        }
                } catch (error: Throwable) {
                    log.info { "stopped syncLoop: ${error.message}" }
                }
            }
            syncJob?.invokeOnCompletion {
                _currentSyncState.value = STOPPED
            }
        }
        if (wait) syncJob?.join()
    }

    suspend fun stop(wait: Boolean = false) {
        startStopMutex.withLock {
            if (syncJob != null) {
                _currentSyncState.value = STOPPING
                if (wait) syncJob?.join()
            }
        }
    }

    private fun updateSyncState(newSyncState: SyncState) {
        _currentSyncState.update {
            if (it != STOPPING) newSyncState else it
        }
    }
}