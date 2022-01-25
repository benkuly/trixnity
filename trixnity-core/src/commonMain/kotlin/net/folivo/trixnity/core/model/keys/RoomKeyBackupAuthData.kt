package net.folivo.trixnity.core.model.keys

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.UserId

@Serializable
sealed class RoomKeyBackupAuthData {

    @Serializable
    data class RoomKeyBackupV1AuthData(
        @SerialName("public_key")
        val publicKey: Key.Curve25519Key,
        @SerialName("signatures")
        val signatures: Signatures<UserId> = mapOf()
    ) : RoomKeyBackupAuthData() {
        companion object {
            fun fromSigned(signed: Key.SignedCurve25519Key) = RoomKeyBackupV1AuthData(signed.signed, signed.signatures)
        }

        fun toSigned() = Key.SignedCurve25519Key(publicKey.keyId, publicKey.value, signatures)
    }
}
