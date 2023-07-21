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
import net.folivo.trixnity.clientserverapi.model.sync.OneTimeKeysCount
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.clientserverapi.model.sync.UnusedFallbackKeyTypes
import net.folivo.trixnity.core.EventEmitter
import net.folivo.trixnity.core.EventEmitterImpl
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.Presence
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

typealias SyncResponseSubscriber = suspend (Sync.Response) -> Unit
typealias DeviceListsSubscriber = suspend (Sync.Response.DeviceLists?) -> Unit
typealias OlmKeysChangeSubscriber = suspend (OlmKeysChange) -> Unit

data class OlmKeysChange(
    val oneTimeKeysCount: OneTimeKeysCount?,
    val fallbackKeyTypes: UnusedFallbackKeyTypes?,
)

typealias AfterSyncResponseSubscriber = suspend (Sync.Response) -> Unit

private val log = KotlinLogging.logger {}

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

interface SyncApiClient : EventEmitter {
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

    fun subscribeDeviceLists(subscriber: DeviceListsSubscriber)
    fun unsubscribeDeviceLists(subscriber: DeviceListsSubscriber)
    fun subscribeOlmKeysChange(subscriber: OlmKeysChangeSubscriber)
    fun unsubscribeOlmKeysChange(subscriber: OlmKeysChangeSubscriber)
    fun subscribeSyncResponse(subscriber: SyncResponseSubscriber)
    fun unsubscribeSyncResponse(subscriber: SyncResponseSubscriber)
    fun subscribeAfterSyncResponse(subscriber: AfterSyncResponseSubscriber)
    fun unsubscribeAfterSyncResponse(subscriber: AfterSyncResponseSubscriber)

    val currentSyncState: StateFlow<SyncState>

    suspend fun start(
        filter: String? = null,
        setPresence: Presence? = null,
        currentBatchToken: MutableStateFlow<String?> = MutableStateFlow(null),
        timeout: Long = 30000,
        withTransaction: suspend (block: suspend () -> Unit) -> Unit,
        asUserId: UserId? = null,
        wait: Boolean = false,
        scope: CoroutineScope,
    )

    suspend fun <T> startOnce(
        filter: String? = null,
        setPresence: Presence? = null,
        currentBatchToken: MutableStateFlow<String?> = MutableStateFlow(null),
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
    currentBatchToken: MutableStateFlow<String?> = MutableStateFlow(null),
    timeout: Long = 30000,
    asUserId: UserId? = null,
    wait: Boolean = false,
    scope: CoroutineScope,
) = start(filter, setPresence, currentBatchToken, timeout, { it() }, asUserId, wait, scope)

suspend fun <T> SyncApiClient.startOnce(
    filter: String? = null,
    setPresence: Presence? = null,
    currentBatchToken: MutableStateFlow<String?> = MutableStateFlow(null),
    timeout: Long = 0,
    asUserId: UserId? = null,
    runOnce: suspend (Sync.Response) -> T,
): Result<T> = startOnce(filter, setPresence, currentBatchToken, timeout, { it() }, asUserId, runOnce)

suspend fun SyncApiClient.startOnce(
    filter: String? = null,
    setPresence: Presence? = null,
    currentBatchToken: MutableStateFlow<String?> = MutableStateFlow(null),
    timeout: Long = 0,
    withTransaction: suspend (block: suspend () -> Unit) -> Unit = { it() },
    asUserId: UserId? = null,
): Result<Unit> =
    startOnce(
        filter = filter,
        setPresence = setPresence,
        currentBatchToken = currentBatchToken,
        timeout = timeout,
        withTransaction = withTransaction,
        asUserId = asUserId,
        runOnce = {}
    )

class SyncApiClientImpl(
    private val httpClient: MatrixClientServerApiHttpClient,
    private val syncLoopDelay: Duration,
    private val syncLoopErrorDelay: Duration,
) : EventEmitterImpl(), SyncApiClient {

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

    private val _currentSyncState: MutableStateFlow<SyncState> = MutableStateFlow(SyncState.STOPPED)
    override val currentSyncState = _currentSyncState.asStateFlow()

    private val deviceListsSubscribers: MutableStateFlow<Set<DeviceListsSubscriber>> = MutableStateFlow(setOf())
    override fun subscribeDeviceLists(subscriber: DeviceListsSubscriber) =
        deviceListsSubscribers.update { it + subscriber }

    override fun unsubscribeDeviceLists(subscriber: DeviceListsSubscriber) =
        deviceListsSubscribers.update { it - subscriber }

    private val deviceOneTimeKeysCountSubscribers: MutableStateFlow<Set<OlmKeysChangeSubscriber>> =
        MutableStateFlow(setOf())

    override fun subscribeOlmKeysChange(subscriber: OlmKeysChangeSubscriber) =
        deviceOneTimeKeysCountSubscribers.update { it + subscriber }

    override fun unsubscribeOlmKeysChange(subscriber: OlmKeysChangeSubscriber) =
        deviceOneTimeKeysCountSubscribers.update { it - subscriber }

    private val syncResponseSubscribers: MutableStateFlow<Set<SyncResponseSubscriber>> = MutableStateFlow(setOf())
    override fun subscribeSyncResponse(subscriber: SyncResponseSubscriber) =
        syncResponseSubscribers.update { it + subscriber }

    override fun unsubscribeSyncResponse(subscriber: SyncResponseSubscriber) =
        syncResponseSubscribers.update { it - subscriber }

    private val afterSyncResponseSubscribers: MutableStateFlow<Set<AfterSyncResponseSubscriber>> =
        MutableStateFlow(setOf())

    override fun subscribeAfterSyncResponse(subscriber: AfterSyncResponseSubscriber) =
        afterSyncResponseSubscribers.update { it + subscriber }

    override fun unsubscribeAfterSyncResponse(subscriber: AfterSyncResponseSubscriber) =
        afterSyncResponseSubscribers.update { it - subscriber }

    override suspend fun start(
        filter: String?,
        setPresence: Presence?,
        currentBatchToken: MutableStateFlow<String?>,
        timeout: Long,
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
                        val isInitialSync = currentBatchToken.value == null
                        if (isInitialSync) updateSyncState(INITIAL_SYNC) else updateSyncState(STARTED)

                        while (isActive && _currentSyncState.value != STOPPING) {
                            try {
                                syncAndResponse(
                                    currentBatchToken = currentBatchToken,
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
                                        log.info { "timeout while sync with token ${currentBatchToken.value}" }
                                        updateSyncState(TIMEOUT)
                                    }

                                    is CancellationException -> throw error
                                    else -> {
                                        log.error(error) { "error while sync with token ${currentBatchToken.value}" }
                                        updateSyncState(ERROR)
                                    }
                                }
                                delay(syncLoopErrorDelay)
                                if (currentBatchToken.value == null) updateSyncState(INITIAL_SYNC)
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
        currentBatchToken: MutableStateFlow<String?>,
        timeout: Long,
        withTransaction: suspend (block: suspend () -> Unit) -> Unit,
        asUserId: UserId?,
        runOnce: suspend (Sync.Response) -> T,
    ): Result<T> = kotlin.runCatching {
        stop(wait = true)
        syncMutex.withLock {
            val isInitialSync = currentBatchToken.value == null
            log.info { "started single sync (initial=$isInitialSync)" }
            if (isInitialSync) updateSyncState(INITIAL_SYNC) else updateSyncState(STARTED)
            val syncResponse =
                syncAndResponse(currentBatchToken, filter, setPresence, timeout, withTransaction, asUserId)
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
        currentBatchToken: MutableStateFlow<String?>,
        filter: String?,
        setPresence: Presence?,
        timeout: Long,
        withTransaction: suspend (block: suspend () -> Unit) -> Unit,
        asUserId: UserId?,
    ): Sync.Response {
        val batchToken = currentBatchToken.value
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
        val measuredProcessDuration = measureTime {
            withTransaction {
                processSyncResponse(response)
            }
        }
        log.debug { "processed sync response in about $measuredProcessDuration with token $batchToken" }
        currentBatchToken.value = response.nextBatch
        updateSyncState(RUNNING)
        return response
    }

    private suspend fun processSyncResponse(response: Sync.Response) {
        coroutineScope {
            deviceOneTimeKeysCountSubscribers.value.forEach {
                launch {
                    it.invoke(OlmKeysChange(response.oneTimeKeysCount, response.unusedFallbackKeyTypes))
                }
            }
        }
        coroutineScope {
            deviceListsSubscribers.value.forEach { launch { it.invoke(response.deviceLists) } }
        }

        // do it at first, to be able to decrypt stuff
        response.toDevice?.events?.forEach { emitEvent(it) }
        // do it at first, to be able to decrypt stuff
        response.accountData?.events?.forEach { emitEvent(it) }

        coroutineScope {
            syncResponseSubscribers.value.forEach { launch { it.invoke(response) } }
        }

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
            afterSyncResponseSubscribers.value.forEach { launch { it.invoke(response) } }
        }
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
        syncJob?.cancel()
        if (wait) syncJob?.join()
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
