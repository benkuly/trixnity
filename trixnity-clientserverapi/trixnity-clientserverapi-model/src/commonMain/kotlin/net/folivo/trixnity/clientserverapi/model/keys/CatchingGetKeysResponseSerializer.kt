package net.folivo.trixnity.clientserverapi.model.keys

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.SignedCrossSigningKeys
import net.folivo.trixnity.core.model.keys.SignedDeviceKeys

private val log = KotlinLogging.logger {}

object CatchingGetKeysResponseSerializer : KSerializer<GetKeys.Response> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("QueryKeysResponseSerializer")

    override fun deserialize(decoder: Decoder): GetKeys.Response {
        require(decoder is JsonDecoder)
        val json = decoder.decodeJsonElement()
        if (json !is JsonObject) throw SerializationException("QueryKeysResponse should be of type JsonObject")

        return GetKeys.Response(
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

    override fun serialize(encoder: Encoder, value: GetKeys.Response) {
        encoder.encodeSerializableValue(GetKeys.Response.serializer(), value)
    }
}