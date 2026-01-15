package net.folivo.trixnity.clientserverapi.model.keys

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.resources.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.SignedCrossSigningKeys
import net.folivo.trixnity.core.model.keys.SignedDeviceKeys

private val log = KotlinLogging.logger("net.folivo.trixnity.clientserverapi.model.keys.GetKeys")

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#post_matrixclientv3keysquery">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/keys/query")
@HttpMethod(POST)
data object GetKeys : MatrixEndpoint<GetKeys.Request, GetKeys.Response> {
    @Serializable
    data class Request(
        @SerialName("device_keys")
        val keysFrom: Map<UserId, Set<String>>,
        @SerialName("timeout")
        val timeout: Long?,
    )

    @OptIn(ExperimentalSerializationApi::class)
    @Serializable(with = Response.Serializer::class)
    @KeepGeneratedSerializer
    data class Response(
        @SerialName("failures")
        val failures: Map<UserId, JsonElement>?,
        @SerialName("device_keys")
        val deviceKeys: Map<UserId, Map<String, SignedDeviceKeys>>?,
        @SerialName("master_keys")
        val masterKeys: Map<UserId, SignedCrossSigningKeys>?,
        @SerialName("self_signing_keys")
        val selfSigningKeys: Map<UserId, SignedCrossSigningKeys>?,
        @SerialName("user_signing_keys")
        val userSigningKeys: Map<UserId, SignedCrossSigningKeys>?,
    ) {
        object Serializer : KSerializer<Response> {
            override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Response")

            override fun deserialize(decoder: Decoder): Response {
                require(decoder is JsonDecoder)
                val json = decoder.decodeJsonElement()
                if (json !is JsonObject) throw SerializationException("QueryKeysResponse should be of type JsonObject")

                return Response(
                    failures = json["failures"]?.let { decoder.json.decodeFromJsonElement(it) },
                    deviceKeys = json["device_keys"]?.let { deviceKeysJson ->
                        decoder.json.decodeFromJsonElement<Map<UserId, Map<String, JsonObject>>>(deviceKeysJson)
                            .mapValues {
                                it.value.mapNotNull { (device, deviceKeyJson) ->
                                    try {
                                        device to decoder.json.decodeFromJsonElement<SignedDeviceKeys>(deviceKeyJson)
                                    } catch (e: SerializationException) {
                                        log.warn { "Could not deserialize device keys. It will be ignored. Reason: ${e.message}" }
                                        null
                                    }
                                }.toMap()
                            }
                    },
                    masterKeys = deserializeCrossSigningKey(json["master_keys"], decoder),
                    selfSigningKeys = deserializeCrossSigningKey(json["self_signing_keys"], decoder),
                    userSigningKeys = deserializeCrossSigningKey(json["user_signing_keys"], decoder)
                )
            }

            private fun deserializeCrossSigningKey(
                json: JsonElement?,
                decoder: JsonDecoder
            ) = json?.let { crossSigningKeysJson ->
                decoder.json.decodeFromJsonElement<Map<UserId, JsonObject>>(crossSigningKeysJson)
                    .mapNotNull { (userId, crossSigningKeyJson) ->
                        try {
                            userId to decoder.json.decodeFromJsonElement<SignedCrossSigningKeys>(crossSigningKeyJson)
                        } catch (e: SerializationException) {
                            log.warn { "Could not deserialize cross singing key. It will be ignored. Reason: ${e.message}" }
                            null
                        }
                    }.toMap()
            }

            override fun serialize(encoder: Encoder, value: Response) {
                encoder.encodeSerializableValue(generatedSerializer(), value)
            }
        }
    }
}

