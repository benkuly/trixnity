package net.folivo.trixnity.client.rest.api.sync

import com.soywiz.klogger.Logger
import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.core.model.MatrixId.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.EventContent
import kotlin.reflect.KClass

class SyncApiClient(
    private val httpClient: HttpClient,
    private val syncBatchTokenService: SyncBatchTokenService
) {
    companion object {
        private val LOG = Logger("SyncApiClient")
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#get-matrix-client-r0-sync">matrix spec</a>
     */
    suspend fun syncOnce(
        filter: String? = null,
        since: String? = null,
        fullState: Boolean = false,
        setPresence: Presence? = null,
        timeout: Long = 0,
        asUserId: UserId? = null
    ): SyncResponse {
        return httpClient.get {
            url("/r0/sync")
            parameter("filter", filter)
            parameter("full_state", fullState)
            parameter("set_presence", setPresence?.value)
            parameter("since", since)
            parameter("timeout", timeout)
            parameter("user_id", asUserId)
            timeout { requestTimeoutMillis = timeout }
        }
    }

    fun syncLoop(
        filter: String? = null,
        setPresence: Presence? = null,
        asUserId: UserId? = null
    ): Flow<SyncResponse> {
        return flow {
            while (true) {
                try {
                    val batchToken = syncBatchTokenService.getBatchToken(asUserId)
                    val response = if (batchToken != null) {
                        syncOnce(
                            filter = filter,
                            setPresence = setPresence,
                            fullState = false,
                            since = batchToken,
                            timeout = 30000,
                            asUserId = asUserId
                        )
                    } else {
                        syncOnce(
                            filter = filter,
                            setPresence = setPresence,
                            fullState = false,
                            timeout = 30000,
                            asUserId = asUserId
                        )
                    }
                    syncBatchTokenService.setBatchToken(response.nextBatch, asUserId)
                    emit(response)
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    LOG.error { "error while sync to server: ${error.message}" }
                    LOG.debug { error.stackTraceToString() }
                    delay(5000)// FIXME better retry policy!
                    continue
                }
            }
        }
    }

    private var syncJob: Job? = null

    suspend fun start( // FIXME test
        filter: String? = null,
        setPresence: Presence? = null,
        asUserId: UserId? = null
    ) {
        stop()
        syncJob = GlobalScope.launch {
            LOG.info { "started syncLoop" }
            try {
                syncLoop(filter, setPresence, asUserId)
                    .collect { syncResponse ->
                        try {
                            // TODO account_data
                            syncResponse.room?.join?.forEach { (_, joinedRoom) ->
                                joinedRoom.timeline?.events?.forEach { emitEvent(it) }
                                joinedRoom.state?.events?.forEach { emitEvent(it) }
                                joinedRoom.ephemeral?.events?.forEach { emitEvent(it) }
                            }
                            syncResponse.room?.invite?.forEach { (_, invitedRoom) ->
                                invitedRoom.inviteState?.events?.forEach { emitEvent(it) }
                            }
                            syncResponse.room?.leave?.forEach { (_, leftRoom) ->
                                leftRoom.state?.events?.forEach { emitEvent(it) }
                                leftRoom.timeline?.events?.forEach { emitEvent(it) }
                            }
                            syncResponse.presence?.events?.forEach { emitEvent(it) }
                            syncResponse.toDevice?.events?.forEach { emitEvent(it) }
                            LOG.debug { "processed sync response" }
                        } catch (error: Throwable) {
                            LOG.error { "some error while processing response: ${error.message}" }
                            LOG.debug { error.stackTraceToString() }
                            throw error
                        }
                    }
            } catch (error: CancellationException) {
                LOG.info { "stopped syncLoop" }
            }
        }
    }

    suspend fun stop() {
        syncJob?.cancelAndJoin()
    }

    private val eventHandler: MutableMap<KClass<out EventContent>, MutableSharedFlow<Event<*>>> =
        mutableMapOf()

    private suspend fun emitEvent(event: Event<*>) {
        eventHandler
            .filterKeys { it.isInstance(event.content) }
            .forEach { it.value.emit(event) }
    }

    fun <T : EventContent> events(clazz: KClass<T>): SharedFlow<Event<T>> {
        return (eventHandler.getOrPut(clazz) { MutableSharedFlow() } as MutableSharedFlow<Event<T>>)
            .asSharedFlow()
    }

    inline fun <reified T : EventContent> events(): SharedFlow<Event<T>> {
        return events(T::class)
    }

}