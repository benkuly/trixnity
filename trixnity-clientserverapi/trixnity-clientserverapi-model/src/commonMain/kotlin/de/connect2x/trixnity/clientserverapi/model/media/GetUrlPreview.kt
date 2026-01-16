package de.connect2x.trixnity.clientserverapi.model.media

import io.ktor.resources.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.jsonObject
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixmediav3preview_url">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v1/media/preview_url")
@HttpMethod(GET)
data class GetUrlPreview(
    @SerialName("url") val url: String,
    @SerialName("ts") val timestamp: Long? = null,
) : MatrixEndpoint<Unit, GetUrlPreview.Response> {
    @OptIn(ExperimentalSerializationApi::class)
    @Serializable(with = Response.Serializer::class)
    @KeepGeneratedSerializer
    data class Response(
        @SerialName("matrix:image:size") val size: Long? = null,
        @SerialName("og:image") val imageUrl: String? = null,
        @SerialName("others") val others: Map<String, JsonElement>? = null,
    ) {
        object Serializer : JsonTransformingSerializer<Response>(Response.generatedSerializer()) {
            override fun transformDeserialize(element: JsonElement): JsonElement {
                return JsonObject(buildMap {
                    element.jsonObject["matrix:image:size"]?.let { put("matrix:image:size", it) }
                    element.jsonObject["og:image"]?.let { put("og:image", it) }
                    JsonObject(element.jsonObject - "matrix:image:size" - "og:image")
                        .takeIf { it.isNotEmpty() }
                        ?.let { put("others", it) }
                })
            }

            override fun transformSerialize(element: JsonElement): JsonElement {
                return JsonObject(buildMap {
                    putAll(element.jsonObject - "others")
                    putAll(element.jsonObject["others"]?.jsonObject ?: emptyMap())
                })
            }
        }
    }
}