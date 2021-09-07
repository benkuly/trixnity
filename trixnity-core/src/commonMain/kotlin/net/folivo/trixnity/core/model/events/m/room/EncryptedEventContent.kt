package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import net.folivo.trixnity.core.model.crypto.EncryptionAlgorithm
import net.folivo.trixnity.core.model.crypto.EncryptionAlgorithm.*
import net.folivo.trixnity.core.model.crypto.Key.Curve25519Key
import net.folivo.trixnity.core.model.events.RoomEventContent
import net.folivo.trixnity.core.model.events.ToDeviceEventContent
import net.folivo.trixnity.core.serialization.m.room.encrypted.EncryptedEventContentSerializer
import net.folivo.trixnity.core.serialization.m.room.encrypted.OlmMessageTypeSerializer

/**
 * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#m-room-encrypted">matrix spec</a>
 */
@Serializable(with = EncryptedEventContentSerializer::class)
sealed interface EncryptedEventContent : RoomEventContent, ToDeviceEventContent {
    val senderKey: Curve25519Key
    val deviceId: String?
    val sessionId: String?
    val algorithm: EncryptionAlgorithm

    @Serializable
    data class MegolmEncryptedEventContent(
        @SerialName("ciphertext")
        val ciphertext: String,
        @SerialName("sender_key")
        override val senderKey: Curve25519Key,
        @SerialName("device_id")
        override val deviceId: String,
        @SerialName("session_id")
        override val sessionId: String,
        @SerialName("algorithm")
        override val algorithm: Megolm = Megolm
    ) : EncryptedEventContent

    @Serializable
    data class OlmEncryptedEventContent(
        @SerialName("ciphertext")
        val ciphertext: Map<String, CiphertextInfo>,
        @SerialName("sender_key")
        override val senderKey: Curve25519Key,
        @SerialName("device_id")
        override val deviceId: String? = null,
        @SerialName("session_id")
        override val sessionId: String? = null,
        @SerialName("algorithm")
        override val algorithm: Olm = Olm
    ) : EncryptedEventContent {
        @Serializable
        data class CiphertextInfo(
            @SerialName("body")
            val body: String,
            @SerialName("type")
            val type: OlmMessageType
        ) {
            @Serializable(with = OlmMessageTypeSerializer::class)
            enum class OlmMessageType(val value: Int) {
                INITIAL_PRE_KEY(0),
                ORDINARY(1);

                companion object {
                    fun of(value: Int): OlmMessageType {
                        return when (value) {
                            0 -> INITIAL_PRE_KEY
                            else -> ORDINARY
                        }
                    }
                }
            }
        }
    }

    @Serializable
    data class UnknownEncryptedEventContent(
        @SerialName("ciphertext")
        val ciphertext: JsonElement,
        @SerialName("sender_key")
        override val senderKey: Curve25519Key,
        @SerialName("device_id")
        override val deviceId: String? = null,
        @SerialName("session_id")
        override val sessionId: String? = null,
        @SerialName("algorithm")
        override val algorithm: Unknown
    ) : EncryptedEventContent
}