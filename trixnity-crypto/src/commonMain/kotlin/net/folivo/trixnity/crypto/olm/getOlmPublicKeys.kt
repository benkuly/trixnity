package net.folivo.trixnity.crypto.olm

import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.olm.OlmAccount
import net.folivo.trixnity.olm.freeAfter

data class OlmPublicKeys(
    val signingKey: Key.Ed25519Key,
    val identityKey: Key.Curve25519Key,
)

suspend fun getOlmPublicKeys(olmPickleKey: String?, pickledOlmAccount: String, deviceId: String) =
    freeAfter(OlmAccount.unpickle(olmPickleKey, pickledOlmAccount)) {
        OlmPublicKeys(
            signingKey = Key.Ed25519Key(deviceId, it.identityKeys.ed25519),
            identityKey = Key.Curve25519Key(deviceId, it.identityKeys.curve25519)
        )
    }