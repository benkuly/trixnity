package de.connect2x.trixnity.core.model.keys

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import de.connect2x.trixnity.core.model.keys.KeyValue.Curve25519KeyValue

@Serializable(with = RoomKeyBackupSessionData.Serializer::class)
sealed interface RoomKeyBackupSessionData {
    @Serializable
    data class EncryptedRoomKeyBackupV1SessionData(
        @SerialName("ciphertext")
        val ciphertext: String,
        @SerialName("ephemeral")
        val ephemeral: Curve25519KeyValue,
        @SerialName("mac")
        val mac: String,
    ) : RoomKeyBackupSessionData {
        @Serializable
        data class RoomKeyBackupV1SessionData(
            @SerialName("sender_key")
            val senderKey: Curve25519KeyValue,
            @SerialName("forwarding_curve25519_key_chain")
            val forwardingKeyChain: List<Curve25519KeyValue> = listOf(),
            @SerialName("sender_claimed_keys")
            val senderClaimedKeys: Keys,
            @SerialName("session_key")
            val sessionKey: ExportedSessionKeyValue,
            @SerialName("algorithm")
            val algorithm: EncryptionAlgorithm = EncryptionAlgorithm.Megolm
        )
    }

    object Serializer : KSerializer<RoomKeyBackupSessionData> {
        override val descriptor = buildClassSerialDescriptor("RoomKeyBackupSessionData")

        override fun deserialize(decoder: Decoder): RoomKeyBackupSessionData {
            return decoder.decodeSerializableValue(EncryptedRoomKeyBackupV1SessionData.serializer())
        }

        override fun serialize(encoder: Encoder, value: RoomKeyBackupSessionData) {
            when (value) {
                is EncryptedRoomKeyBackupV1SessionData ->
                    encoder.encodeSerializableValue(
                        EncryptedRoomKeyBackupV1SessionData.serializer(),
                        value
                    )
            }
        }

    }
}