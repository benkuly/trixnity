package net.folivo.trixnity.clientserverapi.client

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.auth.*
import io.ktor.http.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlin.reflect.KClass

interface MatrixClientAuthProvider<T : MatrixClientAuthProviderData> : AuthProvider {
    /**
     * Invoke authentication-provider-specific behavior when logging out.
     *
     * @return The result if the logout is implemented, null if no logout was implemented by the authentication provider
     */
    suspend fun logout(): Result<Unit>? = null
}

/**
 * Provides methods to retrieve and store arbitrary (usually JSON-encoded) authentication data.
 */
interface MatrixClientAuthProviderStore {
    suspend fun getAuthData(): String?
    suspend fun setAuthData(authData: String?)

    companion object {
        fun inMemory(initialValue: String? = null) = object : MatrixClientAuthProviderStore {
            var authData = initialValue

            override suspend fun getAuthData(): String? = authData

            override suspend fun setAuthData(authData: String?) {
                this.authData = authData
            }
        }

        inline fun <reified T : MatrixClientAuthProviderData> inMemory(initialValue: T) =
            inMemory(matrixClientAuthProviderStoreJson.encodeToString(initialValue))
    }
}

@PublishedApi
internal val matrixClientAuthProviderStoreJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

suspend fun <T : MatrixClientAuthProviderData> MatrixClientAuthProviderStore.getAuthData(serializer: KSerializer<T>): T? =
    getAuthData()?.let { matrixClientAuthProviderStoreJson.decodeFromString<T>(serializer, it) }

suspend fun <T : MatrixClientAuthProviderData> MatrixClientAuthProviderStore.setAuthData(
    serializer: KSerializer<T>,
    authData: T
) = setAuthData(matrixClientAuthProviderStoreJson.encodeToString(serializer, authData))

suspend inline fun <reified T : MatrixClientAuthProviderData> MatrixClientAuthProviderStore.getAuthData(): T? =
    getAuthData()?.let { matrixClientAuthProviderStoreJson.decodeFromString<T>(it) }

suspend inline fun <reified T : MatrixClientAuthProviderData> MatrixClientAuthProviderStore.setAuthData(authData: T) =
    setAuthData(matrixClientAuthProviderStoreJson.encodeToString(authData))

interface MatrixClientAuthProviderData {
    companion object
}

interface MatrixClientAuthProviderFactory {
    /**
     * Is used to identify the authentication provider. It must never be changed without a migration.
     */
    val id: String

    /**
     * Is used to identify the authentication provider.
     */
    val supports: KClass<out MatrixClientAuthProviderData>
    suspend fun create(
        baseUrl: Url,
        store: MatrixClientAuthProviderStore,
        initialData: MatrixClientAuthProviderData?,
        onLogout: suspend (LogoutInfo) -> Unit,
        httpClientEngine: HttpClientEngine?,
        httpClientConfig: (HttpClientConfig<*>.() -> Unit)?,
    ): MatrixClientAuthProvider<*>
}