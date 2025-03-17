package net.folivo.trixnity.clientserverapi.client

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

private val log = KotlinLogging.logger {}

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
    allEvents: List<ClientEvent<*>>,
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
        asUserId: UserId? = null
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
    private val syncCoroutineScope: CoroutineScope,
    private val syncBatchTokenStore: SyncBatchTokenStore,
    private val syncLoopDelay: Duration,
    private val syncLoopErrorDelay: Duration,
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

    internal fun testOnlySyncOnceSize() : Int = syncOnceRequests.value.size

    private object SyncStoppedException : RuntimeException("sync has been stopped")

    private val _currentSyncState: MutableStateFlow<SyncState> = MutableStateFlow(STOPPED)
    override val currentSyncState = _currentSyncState.asStateFlow()

    private var syncLoopJob: Job? = null
    private val syncLoopStartMutex = Mutex()

    init {
        syncCoroutineScope.launch { syncLoop() }
        if (log.isInfoEnabled()) {
            syncCoroutineScope.launch {
                currentSyncState.collect {
                    log.info { "current sync state: $it" }
                }
            }
        }
    }

    private suspend fun syncLoop() {
        syncLoopStartMutex.withLock {
            syncLoopJob?.cancelAndJoin()
            syncLoopJob = syncCoroutineScope.launch {
                log.info { "started syncLoop" }
                while (currentCoroutineContext().isActive) {
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
                            is HttpRequestTimeoutException, is ConnectTimeoutException, is SocketTimeoutException -> {
                                log.info { "timeout while sync with token $currentBatchToken" }
                                _currentSyncState.value = TIMEOUT
                            }

                            is CancellationException -> throw error
                            is SyncStoppedException -> continue // skip syncLoopErrorDelay

                            else -> {
                                log.error(error) { "error while sync with token $currentBatchToken" }
                                _currentSyncState.value = ERROR
                            }
                        }
                        delay(syncLoopErrorDelay) // TODO better retry policy!
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
                    }
                    async {
                        if (currentSyncState.value == RUNNING) {
                            log.trace { "wait sync loop delay of $syncLoopDelay before starting the next sync request" }
                            delay(syncLoopDelay)
                        }
                        log.trace { "do sync request $queueItem" }
                        measureTimedValue {
                            sync(
                                filter = queueItem.filter,
                                setPresence = queueItem.setPresence,
                                fullState = false,
                                since = batchToken,
                                timeout = if (batchToken == null || _currentSyncState.value == STARTED) ZERO else queueItem.timeout,
                            ).getOrThrow()
                        }
                    }.onAwait { it }
                }.also { coroutineContext.cancelChildren() }
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
        stop()
        syncCoroutineScope.launch { syncLoop() }
    }
}