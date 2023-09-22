package net.folivo.trixnity.clientserverapi.client

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import net.folivo.trixnity.clientserverapi.client.SyncState.*
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.core.EventEmitter
import net.folivo.trixnity.core.EventEmitterImpl
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.Presence
import kotlin.time.Duration

private val log = KotlinLogging.logger {}

class SyncEvents(
    val syncResponse: Sync.Response,
    allEvents: List<Event<*>>,
) : List<Event<*>> by allEvents

enum class SyncState {
    /**
     * The fist sync has been started.
     */
    INITIAL_SYNC,

    /**
     * A normal sync has been started.
     */
    STARTED,

    /**
     * A normal sync has been finished. It is normally set when the sync is run in a loop.
     */
    RUNNING,

    /**
     * The sync has been aborted because of an internal or external error.
     */
    ERROR,

    /**
     * The sync request is timed out.
     */
    TIMEOUT,

    /**
     * The sync loop is going to be stopped.
     */
    STOPPING,

    /**
     * The sync is stopped.
     */
    STOPPED,
}

interface SyncApiClient : EventEmitter<SyncEvents> {
    /**
     * This is the plain sync request. If you want to subscribe to events and ore, use [start] or [startOnce].
     *
     * @see [Sync]
     */
    suspend fun sync(
        filter: String? = null,
        since: String? = null,
        fullState: Boolean = false,
        setPresence: Presence? = null,
        timeout: Long = 0,
        asUserId: UserId? = null
    ): Result<Sync.Response>

    val currentSyncState: StateFlow<SyncState>

    suspend fun start(
        filter: String? = null,
        setPresence: Presence? = null,
        getBatchToken: suspend () -> String?,
        setBatchToken: suspend (String) -> Unit,
        timeout: Long = 30000,
        withTransaction: suspend (block: suspend () -> Unit) -> Unit,
        asUserId: UserId? = null,
        wait: Boolean = false,
        scope: CoroutineScope,
    )

    suspend fun <T> startOnce(
        filter: String? = null,
        setPresence: Presence? = null,
        getBatchToken: suspend () -> String?,
        setBatchToken: suspend (String) -> Unit,
        timeout: Long = 0,
        withTransaction: suspend (block: suspend () -> Unit) -> Unit,
        asUserId: UserId? = null,
        runOnce: suspend (Sync.Response) -> T,
    ): Result<T>

    suspend fun stop(wait: Boolean = false)
    suspend fun cancel(wait: Boolean = false)
}

suspend fun SyncApiClient.start(
    filter: String? = null,
    setPresence: Presence? = null,
    getBatchToken: suspend () -> String?,
    setBatchToken: suspend (String) -> Unit,
    timeout: Long = 30000,
    asUserId: UserId? = null,
    wait: Boolean = false,
    scope: CoroutineScope,
) = start(filter, setPresence, getBatchToken, setBatchToken, timeout, { it() }, asUserId, wait, scope)

suspend fun <T> SyncApiClient.startOnce(
    filter: String? = null,
    setPresence: Presence? = null,
    getBatchToken: suspend () -> String?,
    setBatchToken: suspend (String) -> Unit,
    timeout: Long = 0,
    asUserId: UserId? = null,
    runOnce: suspend (Sync.Response) -> T,
): Result<T> = startOnce(filter, setPresence, getBatchToken, setBatchToken, timeout, { it() }, asUserId, runOnce)

suspend fun SyncApiClient.startOnce(
    filter: String? = null,
    setPresence: Presence? = null,
    getBatchToken: suspend () -> String?,
    setBatchToken: suspend (String) -> Unit,
    timeout: Long = 0,
    withTransaction: suspend (block: suspend () -> Unit) -> Unit = { it() },
    asUserId: UserId? = null,
): Result<Unit> =
    startOnce(
        filter = filter,
        setPresence = setPresence,
        getBatchToken = getBatchToken,
        setBatchToken = setBatchToken,
        timeout = timeout,
        withTransaction = withTransaction,
        asUserId = asUserId,
        runOnce = {}
    )


class SyncApiClientImpl(
    private val httpClient: MatrixClientServerApiHttpClient,
    private val syncLoopDelay: Duration,
    private val syncLoopErrorDelay: Duration,
) : EventEmitterImpl<SyncEvents>(), SyncApiClient {

    override suspend fun sync(
        filter: String?,
        since: String?,
        fullState: Boolean,
        setPresence: Presence?,
        timeout: Long,
        asUserId: UserId?
    ): Result<Sync.Response> =
        httpClient.request(
            Sync(filter, if (fullState) fullState else null, setPresence, since, timeout, asUserId),
        ) {
            timeout {
                requestTimeoutMillis = if (timeout == 0L) 300_000 else timeout + 5000
            }
        }

    private var syncJob: Job? = null
    private val startStopMutex = Mutex()
    private val syncMutex = Mutex()

    private val _currentSyncState: MutableStateFlow<SyncState> = MutableStateFlow(STOPPED)
    override val currentSyncState = _currentSyncState.asStateFlow()

    override suspend fun start(
        filter: String?,
        setPresence: Presence?,
        getBatchToken: suspend () -> String?,
        setBatchToken: suspend (String) -> Unit, timeout: Long,
        withTransaction: suspend (block: suspend () -> Unit) -> Unit,
        asUserId: UserId?,
        wait: Boolean,
        scope: CoroutineScope,
    ) {
        stop(wait = true)
        startStopMutex.withLock {
            if (syncJob == null) {
                syncJob = scope.launch {
                    syncMutex.withLock {
                        log.info { "started syncLoop" }
                        val currentBatchToken = getBatchToken()
                        val isInitialSync = currentBatchToken == null
                        if (isInitialSync) updateSyncState(INITIAL_SYNC) else updateSyncState(STARTED)

                        while (isActive && _currentSyncState.value != STOPPING) {
                            try {
                                syncAndResponse(
                                    getBatchToken = getBatchToken,
                                    setBatchToken = setBatchToken,
                                    filter = filter,
                                    setPresence = setPresence,
                                    timeout = if (_currentSyncState.value == STARTED) 0 else timeout,
                                    withTransaction = withTransaction,
                                    asUserId = asUserId
                                )
                                delay(syncLoopDelay)
                            } catch (error: Throwable) {
                                when (error) {
                                    is HttpRequestTimeoutException, is ConnectTimeoutException, is SocketTimeoutException -> {
                                        log.info { "timeout while sync with token $currentBatchToken" }
                                        updateSyncState(TIMEOUT)
                                    }

                                    is CancellationException -> throw error
                                    else -> {
                                        log.error(error) { "error while sync with token $currentBatchToken" }
                                        updateSyncState(ERROR)
                                    }
                                }
                                delay(syncLoopErrorDelay) // TODO better retry policy!
                                if (getBatchToken() == null) updateSyncState(INITIAL_SYNC)
                                else updateSyncState(STARTED)
                            }
                        }
                    }
                }
                syncJob?.invokeOnCompletion {
                    log.info { "stopped syncLoop" }
                    _currentSyncState.value = STOPPED
                    syncJob = null
                }
            }
        }
        if (wait) syncJob?.join()
    }

    override suspend fun <T> startOnce(
        filter: String?,
        setPresence: Presence?,
        getBatchToken: suspend () -> String?,
        setBatchToken: suspend (String) -> Unit,
        timeout: Long,
        withTransaction: suspend (block: suspend () -> Unit) -> Unit,
        asUserId: UserId?,
        runOnce: suspend (Sync.Response) -> T,
    ): Result<T> = kotlin.runCatching {
        stop(wait = true)
        syncMutex.withLock {
            val isInitialSync = getBatchToken() == null
            log.info { "started single sync (initial=$isInitialSync)" }
            if (isInitialSync) updateSyncState(INITIAL_SYNC) else updateSyncState(STARTED)
            val syncResponse =
                syncAndResponse(getBatchToken, setBatchToken, filter, setPresence, timeout, withTransaction, asUserId)
            runOnce(syncResponse)
        }
    }.onSuccess {
        log.info { "stopped single sync with success" }
        _currentSyncState.value = STOPPED
    }.onFailure {
        log.info { "stopped single sync with failure" }
        _currentSyncState.value = STOPPED
    }

    private suspend fun syncAndResponse(
        getBatchToken: suspend () -> String?,
        setBatchToken: suspend (String) -> Unit,
        filter: String?,
        setPresence: Presence?,
        timeout: Long,
        withTransaction: suspend (block: suspend () -> Unit) -> Unit,
        asUserId: UserId?,
    ): Sync.Response {
        val batchToken = getBatchToken()
        val (response, measuredSyncDuration) = measureTime<Sync.Response> {
            sync(
                filter = filter,
                setPresence = setPresence,
                fullState = false,
                since = batchToken,
                timeout = if (batchToken == null) 0L else timeout,
                asUserId = asUserId
            ).getOrThrow()
        }
        log.debug { "received sync response after about $measuredSyncDuration with token $batchToken" }

        withTransaction {
            val measuredProcessDuration = measureTime {
                processSyncResponse(response)
            }
            log.debug { "processed sync response in about $measuredProcessDuration with token $batchToken" }

            setBatchToken(response.nextBatch)
        }
        updateSyncState(RUNNING)
        return response
    }

    private suspend fun processSyncResponse(response: Sync.Response) {
        log.trace { "process syncResponse" }
        val syncEvents =
            SyncEvents(
                syncResponse = response,
                allEvents = buildList {
                    response.toDevice?.events?.forEach { add(it) }
                    response.accountData?.events?.forEach { add(it) }
                    response.presence?.events?.forEach { add(it) }
                    response.room?.join?.forEach { (_, joinedRoom) ->
                        joinedRoom.state?.events?.forEach { add(it) }
                        joinedRoom.timeline?.events?.forEach { add(it) }
                        joinedRoom.ephemeral?.events?.forEach { add(it) }
                        joinedRoom.accountData?.events?.forEach { add(it) }
                    }
                    response.room?.invite?.forEach { (_, invitedRoom) ->
                        invitedRoom.inviteState?.events?.forEach { add(it) }
                    }
                    response.room?.knock?.forEach { (_, invitedRoom) ->
                        invitedRoom.knockState?.events?.forEach { add(it) }
                    }
                    response.room?.leave?.forEach { (_, leftRoom) ->
                        leftRoom.state?.events?.forEach { add(it) }
                        leftRoom.timeline?.events?.forEach { add(it) }
                        leftRoom.accountData?.events?.forEach { add(it) }
                    }
                }
            )
        emit(syncEvents)
        log.trace { "finished process syncResponse" }
    }

    override suspend fun stop(wait: Boolean) {
        startStopMutex.withLock {
            if (syncJob != null) {
                _currentSyncState.value = STOPPING
                if (wait) syncJob?.join()
            }
        }
    }

    override suspend fun cancel(wait: Boolean) {
        if (wait) syncJob?.cancelAndJoin()
        else syncJob?.cancel()
    }

    private fun updateSyncState(newSyncState: SyncState) {
        _currentSyncState.update {
            if (it != STOPPING) newSyncState else it
        }
    }

    private suspend fun <T> measureTime(block: suspend () -> T): Pair<T, Duration> {
        val start = Clock.System.now()
        val result = block()
        val stop = Clock.System.now()
        return result to (stop - start)
    }

    private suspend fun measureTime(block: suspend () -> Unit): Duration = measureTime<Unit>(block).second
}