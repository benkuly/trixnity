package de.connect2x.trixnity.clientserverapi.client

import de.connect2x.trixnity.core.serialization.createMatrixEventJson
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import de.connect2x.trixnity.core.serialization.events.default
import de.connect2x.trixnity.utils.RetryFlowDelayConfig
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.auth.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext

interface MatrixClientAuthProvider : AuthProvider {
    val baseUrl: Url

    /**
     * Invoke authentication-provider-specific behavior when logging out.
     *
     * @return The result if the logout is implemented, null if no logout was implemented by the authentication provider
     */
    suspend fun logout(): Result<Unit>? = null
}

/**
 * Provides methods to retrieve and store arbitrary authentication data.
 */
interface MatrixClientAuthProviderDataStore {
    suspend fun getAuthData(): MatrixClientAuthProviderData?
    suspend fun setAuthData(authData: MatrixClientAuthProviderData?)

    companion object {
        fun inMemory(initialValue: MatrixClientAuthProviderData? = null) = object : MatrixClientAuthProviderDataStore {
            var authData = initialValue

            override suspend fun getAuthData(): MatrixClientAuthProviderData? = authData

            override suspend fun setAuthData(authData: MatrixClientAuthProviderData?) {
                this.authData = authData
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
suspend inline fun <T : MatrixClientAuthProviderData> MatrixClientAuthProviderDataStore.getAuthData(): T? =
    getAuthData()?.let { it as T }

interface MatrixClientAuthProviderData {
    val baseUrl: Url

    fun createAuthProvider(
        store: MatrixClientAuthProviderDataStore,
        onLogout: suspend (LogoutInfo) -> Unit = {},
        httpClientEngine: HttpClientEngine? = null,
        httpClientConfig: (HttpClientConfig<*>.() -> Unit)? = null
    ): MatrixClientAuthProvider

    companion object
}

suspend fun <T> MatrixClientAuthProviderData.useApi(
    eventContentSerializerMappings: EventContentSerializerMappings = EventContentSerializerMappings.default,
    json: Json = createMatrixEventJson(eventContentSerializerMappings),
    syncBatchTokenStore: SyncBatchTokenStore = SyncBatchTokenStore.inMemory(),
    syncErrorDelayConfig: RetryFlowDelayConfig = RetryFlowDelayConfig.sync,
    coroutineContext: CoroutineContext = Dispatchers.Default,
    httpClientEngine: HttpClientEngine? = null,
    httpClientConfig: (HttpClientConfig<*>.() -> Unit)? = null,
    block: suspend (MatrixClientServerApiClient) -> T
): T = MatrixClientServerApiClientImpl(
    authProvider = createAuthProvider(
        store = MatrixClientAuthProviderDataStore.inMemory(this),
        onLogout = {},
        httpClientEngine = httpClientEngine,
        httpClientConfig = httpClientConfig,
    ),
    eventContentSerializerMappings = eventContentSerializerMappings,
    json = json,
    syncBatchTokenStore = syncBatchTokenStore,
    syncErrorDelayConfig = syncErrorDelayConfig,
    coroutineContext = coroutineContext,
    httpClientEngine = httpClientEngine,
    httpClientConfig = httpClientConfig,
).use { block(it) }