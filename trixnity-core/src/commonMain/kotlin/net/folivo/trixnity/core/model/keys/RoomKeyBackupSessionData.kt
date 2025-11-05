package net.folivo.trixnity.core.model.keys

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.folivo.trixnity.core.ExportedSessionKeyValue
import net.folivo.trixnity.core.model.keys.KeyValue.Curve25519KeyValue

@Serializable(with = RoomKeyBackupSessionDataSerializer::class)
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
}

object RoomKeyBackupSessionDataSerializer : KSerializer<RoomKeyBackupSessionData> {
    override val descriptor = buildClassSerialDescriptor("RoomKeyBackupSessionDataSerializer")

    override fun deserialize(decoder: Decoder): RoomKeyBackupSessionData {
        return decoder.decodeSerializableValue(RoomKeyBackupSessionData.EncryptedRoomKeyBackupV1SessionData.serializer())
    }

    override fun serialize(encoder: Encoder, value: RoomKeyBackupSessionData) {
        when (value) {
            is RoomKeyBackupSessionData.EncryptedRoomKeyBackupV1SessionData ->
                encoder.encodeSerializableValue(
                    RoomKeyBackupSessionData.EncryptedRoomKeyBackupV1SessionData.serializer(),
                    value
                )
        }
    }

}