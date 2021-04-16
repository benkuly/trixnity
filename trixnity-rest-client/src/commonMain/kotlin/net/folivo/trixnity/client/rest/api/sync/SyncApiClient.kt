package net.folivo.trixnity.client.rest.api.sync

import com.soywiz.klogger.Logger
import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import net.folivo.trixnity.core.model.MatrixId.UserId

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
                val batchToken = syncBatchTokenService.getBatchToken(asUserId)
                val response = try {
                    if (batchToken != null) {
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
                } catch (error: Exception) {
                    LOG.error { "error while sync to server: ${error.message}" }
                    delay(5000)// FIXME better retry policy!
                    continue
                }
                syncBatchTokenService.setBatchToken(response.nextBatch, asUserId)
                emit(response)
            }
        }
    }

}