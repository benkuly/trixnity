package de.connect2x.trixnity.core.model.keys

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.keys.KeyValue.Curve25519KeyValue

@Serializable
sealed interface RoomKeyBackupAuthData {

    @Serializable
    data class RoomKeyBackupV1AuthData(
        @SerialName("public_key")
        val publicKey: Curve25519KeyValue,
        @SerialName("signatures")
        val signatures: Signatures<UserId> = mapOf()
    ) : RoomKeyBackupAuthData
}
