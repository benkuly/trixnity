package net.folivo.trixnity.crypto.olm

import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.crypto.driver.CryptoDriver
import net.folivo.trixnity.crypto.driver.useAll

data class OlmPublicKeys(
    val signingKey: Key.Ed25519Key,
    val identityKey: Key.Curve25519Key,
)

fun CryptoDriver.getOlmPublicKeys(
    pickledOlmAccount: String,
    deviceId: String,
    olmPickleKey: String? = null,
): OlmPublicKeys {
    return olm.account.fromPickle(
        pickledOlmAccount, key.pickleKey(olmPickleKey)
    ).use { account ->
        useAll(
            { account.ed25519Key },
            { account.curve25519Key },
        ) { signingKey, identityKey ->
            OlmPublicKeys(
                signingKey = Key.Ed25519Key(deviceId, signingKey.base64),
                identityKey = Key.Curve25519Key(deviceId, identityKey.base64)
            )
        }
    }
}