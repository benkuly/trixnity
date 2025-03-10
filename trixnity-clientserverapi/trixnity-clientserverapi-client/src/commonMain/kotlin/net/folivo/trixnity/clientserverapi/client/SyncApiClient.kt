package net.folivo.trixnity.clientserverapi.client

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import net.folivo.trixnity.clientserverapi.client.SyncState.*
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.core.ClientEventEmitter
import net.folivo.trixnity.core.ClientEventEmitterImpl
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.m.Presence
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger {}

class SyncEvents(
    val syncResponse: Sync.Response,
    allEvents: List<ClientEvent<*>>,
) : List<ClientEvent<*>> by allEvents

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
     * The sync is stopped.
     */
    STOPPED,
}

interface SyncApiClient : ClientEventEmitter<SyncEvents> {
    /**
     * This is the plain sync request. If you want to subscribe to events and more, use [start] or [startOnce].
     *
     * @see [Sync]
     */
    suspend fun sync(
        filter: String? = null,
        since: String? = null,
        fullState: Boolean = false,
        setPresence: Presence? = null,
        timeout: Duration = ZERO,
        asUserId: UserId? = null
    ): Result<Sync.Response>

    val currentSyncState: StateFlow<SyncState>

    suspend fun start(
        filter: String? = null,
        setPresence: Presence? = null,
        getBatchToken: suspend () -> String?,
        setBatchToken: suspend (String) -> Unit,
        timeout: Duration = 30.seconds,
        withTransaction: suspend (block: suspend () -> Unit) -> Unit = { it() },
        asUserId: UserId? = null,
        wait: Boolean = false,
        scope: CoroutineScope,
    )

    suspend fun <T> startOnce(
        filter: String? = null,
        setPresence: Presence? = null,
        getBatchToken: suspend () -> String?,
        setBatchToken: suspend (String) -> Unit,
        timeout: Duration = ZERO,
        withTransaction: suspend (block: suspend () -> Unit) -> Unit = { it() },
        asUserId: UserId? = null,
        runOnce: suspend (Sync.Response) -> T,
    ): Result<T>

    suspend fun stop(wait: Boolean = false)
    suspend fun cancel(wait: Boolean = false)
}

suspend fun SyncApiClient.startOnce(
    filter: String? = null,
    setPresence: Presence? = null,
    getBatchToken: suspend () -> String?,
    setBatchToken: suspend (String) -> Unit,
    timeout: Duration = ZERO,
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
    private val baseClient: MatrixClientServerApiBaseClient,
    private val syncLoopDelay: Duration,
    private val syncLoopErrorDelay: Duration,
    private val clock: Clock = Clock.System,
) : ClientEventEmitterImpl<SyncEvents>(), SyncApiClient {

    override suspend fun sync(
        filter: String?,
        since: String?,
        fullState: Boolean,
        setPresence: Presence?,
        timeout: Duration,
        asUserId: UserId?
    ): Result<Sync.Response> =
        baseClient.request(
            Sync(filter, if (fullState) fullState else null, setPresence, since, timeout.inWholeMilliseconds, asUserId),
        ) {
            timeout {
                requestTimeoutMillis = (if (timeout == ZERO) 5.minutes else timeout + 10.seconds).inWholeMilliseconds
                socketTimeoutMillis = requestTimeoutMillis
            }
        }

    private val syncMutex = Mutex()

    private data class SyncJobState(
        val job: Job,
        val stopRequest: Boolean = false,
    )

    private val syncJobState = MutableStateFlow<SyncJobState?>(null)
    private val _currentSyncState: MutableStateFlow<SyncState> = MutableStateFlow(STOPPED)
    override val currentSyncState = _currentSyncState.asStateFlow()

    override suspend fun start(
        filter: String?,
        setPresence: Presence?,
        getBatchToken: suspend () -> String?,
        setBatchToken: suspend (String) -> Unit,
        timeout: Duration,
        withTransaction: suspend (block: suspend () -> Unit) -> Unit,
        asUserId: UserId?,
        wait: Boolean,
        scope: CoroutineScope
    ) {
        syncJobState.value.also {
            if (it?.stopRequest == true) {
                log.info { "wait for old sync loop to be fully stopped before starting another sync loop" }
                it.job.join()
            }
        }
        val currentSyncJobState = syncJobState.updateAndGet {
            if (it == null) {
                val job = scope.launch(start = CoroutineStart.LAZY) {
                    syncMutex.withLock {
                        log.info { "started syncLoop" }
                        val currentBatchToken = getBatchToken()
                        val isInitialSync = currentBatchToken == null
                        _currentSyncState.value = if (isInitialSync) INITIAL_SYNC else STARTED

                        while (isActive && syncJobState.value?.stopRequest == false) {
                            try {
                                syncAndResponse(
                                    getBatchToken = getBatchToken,
                                    setBatchToken = setBatchToken,
                                    filter = filter,
                                    setPresence = setPresence,
                                    timeout = if (_currentSyncState.value == STARTED) ZERO else timeout,
                                    withTransaction = withTransaction,
                                    allowStoppingRequest = true,
                                    asUserId = asUserId,
                                    runOnce = { it }
                                )
                                delay(syncLoopDelay)
                            } catch (error: Throwable) {
                                when (error) {
                                    is HttpRequestTimeoutException, is ConnectTimeoutException, is SocketTimeoutException -> {
                                        log.info { "timeout while sync with token $currentBatchToken" }
                                        _currentSyncState.value = TIMEOUT
                                    }

                                    is CancellationException -> throw error
                                    is SyncStoppedException -> {
                                        log.info { "sync has been stopped" }
                                    }

                                    else -> {
                                        log.error(error) { "error while sync with token $currentBatchToken" }
                                        _currentSyncState.value = ERROR
                                    }
                                }
                                delay(syncLoopErrorDelay) // TODO better retry policy!
                                _currentSyncState.value = if (getBatchToken() == null) INITIAL_SYNC else STARTED
                            }
                        }
                    }
                }
                job.invokeOnCompletion {
                    log.info { "stopped syncLoop" }
                    syncJobState.value = null
                    _currentSyncState.value = STOPPED
                }
                SyncJobState(job)
            } else it
        }
        currentSyncJobState?.job?.start()
        if (wait) currentSyncJobState?.job?.join()
    }

    override suspend fun <T> startOnce(
        filter: String?,
        setPresence: Presence?,
        getBatchToken: suspend () -> String?,
        setBatchToken: suspend (String) -> Unit,
        timeout: Duration,
        withTransaction: suspend (block: suspend () -> Unit) -> Unit,
        asUserId: UserId?,
        runOnce: suspend (Sync.Response) -> T
    ): Result<T> = kotlin.runCatching {
        stop(wait = true)
        syncMutex.withLock {
            val isInitialSync = getBatchToken() == null
            log.info { "started single sync (initial=$isInitialSync)" }
            _currentSyncState.value = if (isInitialSync) INITIAL_SYNC else STARTED
            syncAndResponse(
                getBatchToken = getBatchToken,
                setBatchToken = setBatchToken,
                filter = filter,
                setPresence = setPresence,
                timeout = timeout,
                withTransaction = withTransaction,
                allowStoppingRequest = false,
                asUserId = asUserId,
                runOnce = runOnce
            )
        }
    }.onSuccess {
        log.info { "stopped single sync with success" }
        _currentSyncState.value = STOPPED
    }.onFailure {
        log.warn(it) { "stopped single sync with failure" }
        _currentSyncState.value = STOPPED
    }

    private suspend fun <T> syncAndResponse(
        getBatchToken: suspend () -> String?,
        setBatchToken: suspend (String) -> Unit,
        filter: String?,
        setPresence: Presence?,
        timeout: Duration,
        withTransaction: suspend (block: suspend () -> Unit) -> Unit,
        allowStoppingRequest: Boolean,
        asUserId: UserId?,
        runOnce: suspend (Sync.Response) -> T
    ): T {
        val batchToken = getBatchToken()
        val (response, measuredSyncDuration) = measureTime<Sync.Response> {
            coroutineScope {
                select {
                    if (allowStoppingRequest) {
                        async {
                            syncJobState.first { it?.stopRequest == true }
                            throw SyncStoppedException
                        }.onAwait { it }
                    }
                    async {
                        sync(
                            filter = filter,
                            setPresence = setPresence,
                            fullState = false,
                            since = batchToken,
                            timeout = if (batchToken == null) ZERO else timeout,
                            asUserId = asUserId
                        ).getOrThrow()
                    }.onAwait { it }
                }.also { coroutineContext.cancelChildren() }
            }
        }
        log.info { "received sync response after about $measuredSyncDuration with token $batchToken" }

        val result = runOnce(response)

        withTransaction {
            val measuredProcessDuration = measureTime {
                processSyncResponse(response)
            }
            log.info { "processed sync response in about $measuredProcessDuration with token $batchToken" }

            setBatchToken(response.nextBatch)
        }
        _currentSyncState.value = RUNNING
        return result
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

    private object SyncStoppedException : RuntimeException("sync has been stopped")

    override suspend fun stop(wait: Boolean) = coroutineScope {
        val currentSyncJobState = syncJobState.updateAndGet { it?.copy(stopRequest = true) }
        if (wait) currentSyncJobState?.job?.join()
    }

    override suspend fun cancel(wait: Boolean) {
        if (wait) syncJobState.value?.job?.cancelAndJoin()
        else syncJobState.value?.job?.cancel()
    }

    private suspend fun <T> measureTime(block: suspend () -> T): Pair<T, Duration> {
        val start = clock.now()
        val result = block()
        val stop = clock.now()
        return result to (stop - start)
    }

    private suspend fun measureTime(block: suspend () -> Unit): Duration = measureTime<Unit>(block).second
}