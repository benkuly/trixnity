package net.folivo.trixnity.client.key

import mu.KotlinLogging
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.clientserverapi.model.keys.GetRoomKeysVersionResponse
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.olm.OlmPkDecryption
import net.folivo.trixnity.olm.freeAfter

private val log = KotlinLogging.logger {}

internal suspend fun keyBackupCanBeTrusted(
    keyBackupVersion: GetRoomKeysVersionResponse,
    privateKey: String,
    ownUserId: UserId,
    store: Store
): Boolean {
    val generatedPublicKey = try {
        freeAfter(OlmPkDecryption.create(privateKey)) { it.publicKey }
    } catch (error: Throwable) {
        log.warn(error) { "could not generate public key from private backup key" }
        return false
    }
    if (keyBackupVersion !is GetRoomKeysVersionResponse.V1) {
        log.warn { "current room key backup version does not match v1 or there was no backup" }
        return false
    }
    val originalPublicKey = keyBackupVersion.authData.publicKey.value
    if (originalPublicKey != generatedPublicKey) {
        log.warn { "key backup private key does not match public key" }
        return false
    }
//    if ( // TODO this is only relevant, when we want to use the key backup without private key
//        keyBackupVersion.authData.signatures[ownUserId]?.none {
//            it.keyId?.let { keyId ->
//                val keyTrustLevel = store.keys.getDeviceKey(ownUserId, keyId)?.trustLevel
//                    ?: store.keys.getCrossSigningKey(ownUserId, keyId)?.trustLevel
//                keyTrustLevel == KeySignatureTrustLevel.Valid(true)
//                        || keyTrustLevel == KeySignatureTrustLevel.CrossSigned(true)
//                        || keyTrustLevel == KeySignatureTrustLevel.NotAllDeviceKeysCrossSigned(true)
//            } == true
//        } == true
//    ) {
//        log.warn { "key backup cannot be trusted, because it is not signed by any trusted key" }
//        return false
//    }
    return true
}