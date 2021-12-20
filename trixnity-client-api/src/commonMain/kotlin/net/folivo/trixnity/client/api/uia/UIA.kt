package net.folivo.trixnity.client.api.uia

import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import net.folivo.trixnity.client.api.ErrorResponse

sealed interface UIA<T> {
    data class UIASuccess<T>(
        val value: T
    ) : UIA<T>

    data class UIAStep<T>(
        val state: UIAState,
        val authenticate: suspend (AuthenticationRequest) -> Result<UIA<T>>,
        val getFallbackUrl: (AuthenticationType) -> Url
    ) : UIA<T>

    data class UIAError<T>(
        val state: UIAState,
        val errorResponse: ErrorResponse,
        val authenticate: suspend (AuthenticationRequest) -> Result<UIA<T>>,
        val getFallbackUrl: (AuthenticationType) -> Url
    ) : UIA<T>

    @Serializable
    data class FlowInformation(
        @SerialName("stages") val stages: List<AuthenticationType>
    )

    @Serializable
    data class UIAState(
        @SerialName("completed") val completed: List<AuthenticationType> = listOf(),
        @SerialName("flows") val flows: Set<FlowInformation> = setOf(),
        @SerialName("params") val parameter: JsonObject? = null,
        @SerialName("session") val session: String? = null
    )
}