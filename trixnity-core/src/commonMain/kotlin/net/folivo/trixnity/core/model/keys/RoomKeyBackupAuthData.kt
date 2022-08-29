package net.folivo.trixnity.core.model.keys

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.UserId

@Serializable
sealed interface RoomKeyBackupAuthData {

    @Serializable
    data class RoomKeyBackupV1AuthData(
        @SerialName("public_key")
        val publicKey: Key.Curve25519Key,
        @SerialName("signatures")
        val signatures: Signatures<UserId> = mapOf()
    ) : RoomKeyBackupAuthData
}
