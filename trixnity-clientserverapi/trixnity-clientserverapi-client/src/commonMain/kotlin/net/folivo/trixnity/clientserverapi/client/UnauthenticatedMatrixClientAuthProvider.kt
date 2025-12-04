package net.folivo.trixnity.clientserverapi.client

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.auth.*
import kotlinx.serialization.Serializable

class UnauthenticatedMatrixClientAuthProvider(override val baseUrl: Url) : MatrixClientAuthProvider {
    @Deprecated("Please use sendWithoutRequest function instead", level = DeprecationLevel.ERROR)
    override val sendWithoutRequest: Boolean = true

    override fun isApplicable(auth: HttpAuthHeader): Boolean = true

    override suspend fun addRequestHeaders(
        request: HttpRequestBuilder,
        authHeader: HttpAuthHeader?
    ) {
    }
}

@Serializable
data class UnauthenticatedMatrixClientAuthProviderData(override val baseUrl: Url) : MatrixClientAuthProviderData {
    override fun createAuthProvider(
        store: MatrixClientAuthProviderDataStore,
        onLogout: suspend (LogoutInfo) -> Unit,
        httpClientEngine: HttpClientEngine?,
        httpClientConfig: (HttpClientConfig<*>.() -> Unit)?
    ): MatrixClientAuthProvider = UnauthenticatedMatrixClientAuthProvider(baseUrl)
}

fun MatrixClientAuthProviderData.Companion.unauthenticated(baseUrl: Url): UnauthenticatedMatrixClientAuthProviderData =
    UnauthenticatedMatrixClientAuthProviderData(baseUrl)