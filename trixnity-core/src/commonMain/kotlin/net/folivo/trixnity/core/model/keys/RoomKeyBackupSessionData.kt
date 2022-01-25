package net.folivo.trixnity.core.model.keys

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class RoomKeyBackupSessionData {

    @Serializable
    data class EncryptedRoomKeyBackupV1SessionData(
        @SerialName("ciphertext")
        val ciphertext: String,
        @SerialName("ephemeral")
        val ephemeral: String,
        @SerialName("mac")
        val mac: String,
    ) : RoomKeyBackupSessionData() {
        @Serializable
        data class RoomKeyBackupV1SessionData(
            @SerialName("sender_key")
            val senderKey: Key.Curve25519Key,
            @SerialName("forwarding_curve25519_key_chain")
            val forwardingKeyChain: List<Key.Curve25519Key> = listOf(),
            @SerialName("sender_claimed_keys")
            val senderClaimedKeys: Map<String, String>,
            @SerialName("session_key")
            val sessionKey: String,
            @SerialName("algorithm")
            val algorithm: EncryptionAlgorithm = EncryptionAlgorithm.Megolm
        )
    }

}