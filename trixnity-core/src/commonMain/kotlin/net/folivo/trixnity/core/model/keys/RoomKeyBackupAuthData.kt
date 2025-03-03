package net.folivo.trixnity.core.model.keys

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.KeyValue.Curve25519KeyValue

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
