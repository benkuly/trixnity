package de.connect2x.trixnity.clientserverapi.client

import de.connect2x.lognity.api.logger.Level
import de.connect2x.lognity.api.logger.Logger
import de.connect2x.lognity.api.logger.error
import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import de.connect2x.trixnity.clientserverapi.client.SyncState.*
import de.connect2x.trixnity.clientserverapi.model.sync.Sync
import de.connect2x.trixnity.clientserverapi.model.sync.allEvents
import de.connect2x.trixnity.core.ClientEventEmitter
import de.connect2x.trixnity.core.ClientEventEmitterImpl
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.m.Presence
import de.connect2x.trixnity.utils.RetryFlowDelayConfig
import de.connect2x.trixnity.utils.retryLoop
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

private val log = Logger("de.connect2x.trixnity.clientserverapi.client.SyncApiClient")

interface SyncBatchTokenStore {
    suspend fun getSyncBatchToken(): String?
    suspend fun setSyncBatchToken(token: String)

    companion object {
        fun inMemory(): SyncBatchTokenStore = object : SyncBatchTokenStore {
            private var token: String? = null
            override suspend fun getSyncBatchToken(): String? = token
            override suspend fun setSyncBatchToken(token: String) {
                this.token = token
            }
        }
    }
}

class SyncEvents(
    val syncResponse: Sync.Response,
    allEvents: List<ClientEvent<*>> = syncResponse.allEvents(),
    internal val epoch: Long? = null
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
        useStateAfter: Boolean? = null,
    ): Result<Sync.Response>

    val currentSyncState: StateFlow<SyncState>

    suspend fun start(
        filter: String? = null,
        setPresence: Presence? = null,
        timeout: Duration = 30.seconds,
    )

    suspend fun <T> startOnce(
        filter: String? = null,
        setPresence: Presence? = null,
        timeout: Duration = ZERO,
        runOnce: suspend (SyncEvents) -> T,
    ): Result<T>

    suspend fun stop()
    suspend fun cancel()
}

suspend fun SyncApiClient.startOnce(
    filter: String? = null,
    setPresence: Presence? = null,
    timeout: Duration = ZERO,
): Result<Unit> =
    startOnce(
        filter = filter,
        setPresence = setPresence,
        timeout = timeout,
        runOnce = {}
    )


class SyncApiClientImpl(
    private val baseClient: MatrixClientServerApiBaseClient,
    private val coroutineScope: CoroutineScope,
    private val syncBatchTokenStore: SyncBatchTokenStore,
    private val syncErrorDelayConfig: RetryFlowDelayConfig,
) : ClientEventEmitterImpl<SyncEvents>(), SyncApiClient {

    override suspend fun sync(
        filter: String?,
        since: String?,
        fullState: Boolean,
        setPresence: Presence?,
        timeout: Duration,
        useStateAfter: Boolean?,
    ): Result<Sync.Response> =
        baseClient.request(
            Sync(
                filter = filter,
                fullState = if (fullState) fullState else null,
                setPresence = setPresence,
                since = since,
                timeout = timeout.inWholeMilliseconds,
                useStateAfter = useStateAfter,
            ),
        ) {
            timeout {
                requestTimeoutMillis = (if (timeout == ZERO) 5.minutes else timeout + 10.seconds).inWholeMilliseconds
                socketTimeoutMillis = requestTimeoutMillis
            }
        }

    private val syncQueueItemEpoch = MutableStateFlow(0L)
    private fun nextSyncQueueItem(
        filter: String?,
        setPresence: Presence?,
        timeout: Duration,
        doOnce: Boolean,
    ): SyncQueueItem = SyncQueueItem(
        filter = filter,
        setPresence = setPresence,
        timeout = timeout,
        doOnce = doOnce,
        epoch = syncQueueItemEpoch.updateAndGet { it + 1 },
    )

    private data class SyncQueueItem(
        val filter: String?,
        val setPresence: Presence?,
        val timeout: Duration,
        val doOnce: Boolean,
        val epoch: Long,
    )

    private val syncOnceRequests = MutableStateFlow<List<SyncQueueItem>>(listOf())
    private val syncLoopRequest = MutableStateFlow<SyncQueueItem?>(null)

    internal fun testOnlySyncOnceSize(): Int = syncOnceRequests.value.size

    private object SyncStoppedException : RuntimeException("sync has been stopped")

    private val _currentSyncState: MutableStateFlow<SyncState> = MutableStateFlow(STOPPED)
    override val currentSyncState = _currentSyncState.asStateFlow()

    private var syncLoopJob: Job? = null
    private val syncLoopStartMutex = Mutex()

    init {
        coroutineScope.launch { syncLoop() }
        if (log.level <= Level.INFO) {
            coroutineScope.launch {
                currentSyncState.collect {
                    log.info { "current sync state: $it" }
                }
            }
        }
    }

    private suspend fun syncLoop() {
        syncLoopStartMutex.withLock {
            syncLoopJob?.cancelAndJoin()
            syncLoopJob = coroutineScope.launch {
                log.info { "started syncLoop" }
                retryLoop(
                    delayConfig = flowOf(syncErrorDelayConfig),
                ) {
                    val (syncParameter, currentBatchToken) =
                        combine(syncOnceRequests, syncLoopRequest) { syncOnceRequests, syncLoopRequest ->
                            syncOnceRequests.firstOrNull() ?: syncLoopRequest
                        }.onEach { req ->
                            if (req == null) {
                                _currentSyncState.value = STOPPED
                            }
                        }.filterNotNull()
                            .map { req ->
                                val currentBatchToken = syncBatchTokenStore.getSyncBatchToken()
                                _currentSyncState.update { prev ->
                                    when {
                                        currentBatchToken == null -> INITIAL_SYNC
                                        prev != RUNNING -> STARTED
                                        else -> prev
                                    }
                                }
                                req to currentBatchToken
                            }
                            .first()
                    try {
                        syncAndResponse(syncParameter, currentBatchToken)
                        if (syncParameter.doOnce) {
                            log.trace { "remove sync once request from queue" }
                            syncOnceRequests.update { it.filter { it.epoch != syncParameter.epoch } }
                        }
                        _currentSyncState.value = RUNNING
                    } catch (error: Throwable) {
                        when (error) {
                            is SyncStoppedException -> return@retryLoop // skip syncLoopErrorDelay
                            is HttpRequestTimeoutException, is ConnectTimeoutException, is SocketTimeoutException -> {
                                log.info { "timeout while sync with token $currentBatchToken" }
                                _currentSyncState.value = TIMEOUT
                            }

                            is CancellationException -> throw error

                            else -> {
                                log.error(error) { "error while sync with token $currentBatchToken" }
                                _currentSyncState.value = ERROR
                            }
                        }
                        throw error
                    }
                }
            }
        }
    }

    private suspend fun syncAndResponse(queueItem: SyncQueueItem, batchToken: String?) {
        val (response, measuredSyncDuration) =
            coroutineScope {
                select {
                    if (queueItem.doOnce) {
                        async {
                            syncOnceRequests.first { it.none { it.epoch == queueItem.epoch } }
                            log.debug { "stop sync once because stop requested" }
                            throw SyncStoppedException
                        }.onAwait { it }
                    } else {
                        async {
                            syncOnceRequests.first { it.isNotEmpty() }
                            log.debug { "stop sync loop because of a sync once request" }
                            throw SyncStoppedException
                        }.onAwait { it }
                        async {
                            syncLoopRequest.first { it == null }
                            log.debug { "stop sync loop because stop requested" }
                            throw SyncStoppedException
                        }.onAwait { it }
                        async {
                            syncLoopRequest.first { it != null && it != queueItem }
                            log.debug { "stop sync loop because settings have changed" }
                            throw SyncStoppedException
                        }.onAwait { it }
                    }
                    async {
                        log.trace { "do sync request $queueItem" }
                        measureTimedValue {
                            sync(
                                filter = queueItem.filter,
                                setPresence = queueItem.setPresence,
                                fullState = false,
                                useStateAfter = true,
                                since = batchToken,
                                timeout = if (batchToken == null || _currentSyncState.value == STARTED) ZERO else queueItem.timeout,
                            ).getOrThrow()
                        }
                    }.onAwait { it }
                }.also { currentCoroutineContext().cancelChildren() }
            }
        log.info { "received sync response after about $measuredSyncDuration with token $batchToken" }

        val measuredProcessDuration = measureTime {
            processSyncResponse(queueItem.epoch, response)
        }
        log.info { "processed sync response in about $measuredProcessDuration with token $batchToken" }

        syncBatchTokenStore.setSyncBatchToken(response.nextBatch)
    }

    private suspend fun processSyncResponse(
        epoch: Long,
        response: Sync.Response,
    ) {
        log.trace { "process syncResponse" }
        val syncEvents =
            SyncEvents(
                epoch = epoch,
                syncResponse = response,
            )
        emit(syncEvents)
    }

    override suspend fun start(
        filter: String?,
        setPresence: Presence?,
        timeout: Duration,
    ) {
        syncLoopRequest.value = nextSyncQueueItem(
            filter = filter,
            setPresence = setPresence,
            timeout = timeout,
            doOnce = false,
        )
    }

    override suspend fun <T> startOnce(
        filter: String?,
        setPresence: Presence?,
        timeout: Duration,
        runOnce: suspend (SyncEvents) -> T
    ): Result<T> =
        callbackFlow {
            val syncQueueItem = nextSyncQueueItem(
                filter = filter,
                setPresence = setPresence,
                timeout = timeout,
                doOnce = true,
            )
            val unsubscribe = subscribe(Int.MIN_VALUE) {
                if (it.epoch == syncQueueItem.epoch)
                    send(kotlin.runCatching { runOnce(it) })
            }

            syncOnceRequests.update { it + syncQueueItem }

            launch {
                syncOnceRequests.first { it.none { it.epoch == syncQueueItem.epoch } }
                this@callbackFlow.cancel("syncLoop was stopped")
            }

            awaitClose {
                unsubscribe()
                syncOnceRequests.update { it.filter { it.epoch != syncQueueItem.epoch } }
            }
        }.first()

    override suspend fun stop() {
        syncLoopRequest.value = null
        syncOnceRequests.value = emptyList()
        currentSyncState.first { it == STOPPED }
    }

    override suspend fun cancel() {
        syncLoopRequest.value = null
        syncOnceRequests.value = emptyList()
        coroutineScope.launch { syncLoop() }
    }
}

val RetryFlowDelayConfig.Companion.sync: RetryFlowDelayConfig
    get() = RetryFlowDelayConfig.default.copy(
        scheduleBase = 200.milliseconds,
        scheduleFactor = 2.0,
        scheduleLimit = 5.seconds,
    )