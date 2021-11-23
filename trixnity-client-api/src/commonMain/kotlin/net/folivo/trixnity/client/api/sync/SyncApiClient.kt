package net.folivo.trixnity.client.api.sync

import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.http.HttpMethod.Companion.Get
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.client.api.MatrixHttpClient
import net.folivo.trixnity.core.EventEmitter
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.PresenceEventContent.Presence
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

// TODO test sync state
class SyncApiClient(
    private val httpClient: MatrixHttpClient,
    loggerFactory: LoggerFactory
) : EventEmitter() {

    private val log = newLogger(loggerFactory)

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
    ): SyncResponse {
        return httpClient.request {
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
    }

    internal fun syncLoop(
        filter: String? = null,
        setPresence: Presence? = null,
        currentBatchToken: MutableStateFlow<String?> = MutableStateFlow(null),
        timeout: Long = 30000,
        asUserId: UserId? = null
    ): Flow<SyncResponse> {
        return flow {
            while (currentCoroutineContext().isActive && _currentSyncState.value != SyncState.STOPPING) {
                try {

                    val batchToken = currentBatchToken.value
                    val response = syncOnce(
                        filter = filter,
                        setPresence = setPresence,
                        fullState = false,
                        since = batchToken,
                        timeout = timeout,
                        asUserId = asUserId
                    )
                    emit(response)
                    if (_currentSyncState.value != SyncState.STOPPING) {
                        _currentSyncState.value = SyncState.RUNNING
                    }
                    // we want to be sure, that the next batch is only set, when we finished the processing of the response
                    currentBatchToken.value = response.nextBatch
                } catch (error: Throwable) {
                    when (error) {
                        is HttpRequestTimeoutException -> if (_currentSyncState.value != SyncState.STOPPING) _currentSyncState.value =
                            SyncState.TIMEOUT
                        is CancellationException -> throw error
                        else -> if (_currentSyncState.value != SyncState.STOPPING) _currentSyncState.value =
                            SyncState.ERROR
                    }
                    log.info { "error while sync to server: $error" }
                    log.debug { error.stackTraceToString() }
                    delay(5000)// TODO better retry policy!
                    val isInitialSync = currentBatchToken.value == null
                    if (_currentSyncState.value != SyncState.STOPPING) {
                        if (isInitialSync) SyncState.INITIAL_SYNC else SyncState.STARTED
                    }
                }
            }
        }
    }


    private var syncJob: Job? = null

    private val _currentSyncState: MutableStateFlow<SyncState> = MutableStateFlow(SyncState.STOPPED)
    val currentSyncState = _currentSyncState.asStateFlow()

    private val _syncResponses: MutableSharedFlow<SyncResponse> = MutableSharedFlow()
    val syncResponses = _syncResponses.asSharedFlow()

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
        syncJob = scope.launch {
            log.info { "started syncLoop" }
            val isInitialSync = currentBatchToken.value == null
            _currentSyncState.value =
                if (isInitialSync && _currentSyncState.value != SyncState.STOPPING) SyncState.INITIAL_SYNC else SyncState.STARTED

            try {
                syncLoop(filter, setPresence, currentBatchToken, timeout, asUserId)
                    .collect { response ->
                        // do it at first, to be able to decrypt stuff
                        response.toDevice?.events?.forEach { emitEvent(it) }
                        response.presence?.events?.forEach { emitEvent(it) }
                        response.accountData?.events?.forEach { emitEvent(it) }
                        response.room?.join?.forEach { (_, joinedRoom) ->
                            joinedRoom.state?.events?.forEach { emitEvent(it) }
                            joinedRoom.timeline?.events?.forEach { emitEvent(it) }
                            joinedRoom.ephemeral?.events?.forEach { emitEvent(it) }
                            joinedRoom.accountData?.events?.forEach { emitEvent(it) }
                        }
                        response.room?.invite?.forEach { (_, invitedRoom) ->
                            invitedRoom.inviteState?.events?.forEach { emitEvent(it) }
                        }
                        response.room?.knock?.forEach { (_, invitedRoom) ->
                            invitedRoom.knockState?.events?.forEach { emitEvent(it) }
                        }
                        response.room?.leave?.forEach { (_, leftRoom) ->
                            leftRoom.state?.events?.forEach { emitEvent(it) }
                            leftRoom.timeline?.events?.forEach { emitEvent(it) }
                            leftRoom.accountData?.events?.forEach { emitEvent(it) }
                        }
                        _syncResponses.emit(response)
                        log.debug { "processed sync response" }
                    }
            } catch (error: Throwable) {
                log.info { "stopped syncLoop: ${error.message}" }
            }
            _currentSyncState.value = SyncState.STOPPED
        }
        if (wait) syncJob?.join()
    }

    suspend fun stop(wait: Boolean = false) {
        if (syncJob != null) {
            _currentSyncState.value = SyncState.STOPPING
            if (wait) syncJob?.join()
        }
    }
}