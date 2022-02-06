package net.folivo.trixnity.client.key

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import mu.KotlinLogging
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.crypto.*
import net.folivo.trixnity.client.crypto.KeySignatureTrustLevel.*
import net.folivo.trixnity.client.crypto.OlmSignService.SignWith
import net.folivo.trixnity.client.store.AllowedSecretType.M_CROSS_SIGNING_SELF_SIGNING
import net.folivo.trixnity.client.store.AllowedSecretType.M_CROSS_SIGNING_USER_SIGNING
import net.folivo.trixnity.client.store.KeyChainLink
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.verification.KeyVerificationState
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.CrossSigningKeysUsage.MasterKey
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key
import net.folivo.trixnity.core.model.keys.Keys
import net.folivo.trixnity.core.model.keys.Signatures
import net.folivo.trixnity.core.model.keys.SignedCrossSigningKeys
import net.folivo.trixnity.core.model.keys.SignedDeviceKeys
import kotlin.collections.component1
import kotlin.collections.component2

private val log = KotlinLogging.logger {}

class KeyTrustService(
    private val ownUserId: UserId,
    private val store: Store,
    private val olm: OlmService,
    private val api: MatrixApiClient,
) {

    internal suspend fun updateTrustLevelOfKeyChainSignedBy(signingUserId: UserId, signingKey: Ed25519Key) {
        log.trace { "update trust level of all keys signed by $signingUserId $signingKey" }
        store.keys.getKeyChainLinksBySigningKey(signingUserId, signingKey).forEach { keyChainLink ->
            updateTrustLevelOfKey(keyChainLink.signedUserId, keyChainLink.signedKey)
            updateTrustLevelOfKeyChainSignedBy(keyChainLink.signedUserId, keyChainLink.signedKey)
        }
    }

    private suspend fun updateTrustLevelOfKey(userId: UserId, key: Ed25519Key) {
        val keyId = key.keyId

        if (keyId != null) {
            val foundKey = MutableStateFlow(false)

            store.keys.updateDeviceKeys(userId) { oldDeviceKeys ->
                val foundDeviceKeys = oldDeviceKeys?.get(keyId)
                if (foundDeviceKeys != null) {
                    val newTrustLevel = calculateDeviceKeysTrustLevel(foundDeviceKeys.value)
                    foundKey.value = true
                    log.debug { "updated device keys ${foundDeviceKeys.value.signed.deviceId} of user $userId with trust level $newTrustLevel" }
                    oldDeviceKeys + (keyId to foundDeviceKeys.copy(trustLevel = newTrustLevel))
                } else oldDeviceKeys
            }
            if (foundKey.value.not()) {
                store.keys.updateCrossSigningKeys(userId) { oldKeys ->
                    val foundCrossSigningKeys = oldKeys?.firstOrNull { keys ->
                        keys.value.signed.keys.keys.filterIsInstance<Ed25519Key>().any { it.keyId == keyId }
                    }
                    if (foundCrossSigningKeys != null) {
                        val newTrustLevel = calculateCrossSigningKeysTrustLevel(foundCrossSigningKeys.value)
                        foundKey.value = true
                        log.debug { "updated cross signing key ${foundCrossSigningKeys.value.signed.usage.firstOrNull()?.name} of user $userId with trust level $newTrustLevel" }
                        (oldKeys - foundCrossSigningKeys) + foundCrossSigningKeys.copy(trustLevel = newTrustLevel)

                    } else oldKeys
                }
            }
            if (foundKey.value.not()) log.warn { "could not find device or cross signing keys of $key" }
        } else log.warn { "could not update trust level, because key id of $key was null" }
    }

    internal suspend fun calculateDeviceKeysTrustLevel(deviceKeys: SignedDeviceKeys): KeySignatureTrustLevel {
        log.trace { "calculate trust level for ${deviceKeys.signed}" }
        val userId = deviceKeys.signed.userId
        val deviceId = deviceKeys.signed.deviceId
        val signedKey = deviceKeys.signed.keys.get<Ed25519Key>()
            ?: return Invalid("missing ed25519 key")
        return calculateTrustLevel(
            userId,
            { olm.sign.verify(deviceKeys, it) },
            signedKey,
            deviceKeys.signatures,
            deviceKeys.getVerificationState(userId, deviceId),
            false
        ).also { log.trace { "calculated trust level of ${deviceKeys.signed} from $userId is $it" } }
    }

    internal suspend fun calculateCrossSigningKeysTrustLevel(crossSigningKeys: SignedCrossSigningKeys): KeySignatureTrustLevel {
        log.trace { "calculate trust level for ${crossSigningKeys.signed}" }
        val userId = crossSigningKeys.signed.userId
        val signedKey = crossSigningKeys.signed.keys.get<Ed25519Key>()
            ?: return Invalid("missing ed25519 key")
        return calculateTrustLevel(
            userId,
            { olm.sign.verify(crossSigningKeys, it) },
            signedKey,
            crossSigningKeys.signatures,
            crossSigningKeys.getVerificationState(userId),
            crossSigningKeys.signed.usage.contains(MasterKey)
        ).also { log.trace { "calculated trust level of ${crossSigningKeys.signed} from $userId is $it" } }
    }

    private suspend fun calculateTrustLevel(
        userId: UserId,
        verifySignedObject: suspend (signingKeys: Map<UserId, Set<Ed25519Key>>) -> VerifyResult,
        signedKey: Ed25519Key,
        signatures: Signatures<UserId>,
        keyVerificationState: KeyVerificationState?,
        isMasterKey: Boolean
    ): KeySignatureTrustLevel {
        val masterKey = store.keys.getCrossSigningKey(userId, MasterKey)
        return when {
            keyVerificationState is KeyVerificationState.Verified && isMasterKey -> CrossSigned(true)
            keyVerificationState is KeyVerificationState.Verified && (masterKey == null) -> Valid(true)
            keyVerificationState is KeyVerificationState.Blocked -> Blocked
            else -> searchSignaturesForTrustLevel(userId, verifySignedObject, signedKey, signatures)
                ?: when {
                    isMasterKey -> CrossSigned(false)
                    else -> if (masterKey == null) Valid(false) else NotCrossSigned
                }
        }
    }

    private suspend fun searchSignaturesForTrustLevel(
        signedUserId: UserId,
        verifySignedObject: suspend (signingKeys: Map<UserId, Set<Ed25519Key>>) -> VerifyResult,
        signedKey: Ed25519Key,
        signatures: Signatures<UserId>,
        visitedKeys: MutableSet<Pair<UserId, String?>> = mutableSetOf()
    ): KeySignatureTrustLevel? {
        log.trace { "search in signatures of $signedKey for trust level calculation: $signatures" }
        visitedKeys.add(signedUserId to signedKey.keyId)
        store.keys.deleteKeyChainLinksBySignedKey(signedUserId, signedKey)
        val states = signatures.flatMap { (signingUserId, signatureKeys) ->
            signatureKeys
                .filterIsInstance<Ed25519Key>()
                .filterNot { visitedKeys.contains(signingUserId to it.keyId) }
                .flatMap { signatureKey ->
                    visitedKeys.add(signingUserId to signatureKey.keyId)

                    val crossSigningKey =
                        signatureKey.keyId?.let { store.keys.getCrossSigningKey(signingUserId, it) }?.value
                    val signingCrossSigningKey = crossSigningKey?.signed?.get<Ed25519Key>()
                    val crossSigningKeyState = if (signingCrossSigningKey != null) {
                        val isValid = verifySignedObject(mapOf(signingUserId to setOf(signingCrossSigningKey)))
                            .also { v ->
                                if (v != VerifyResult.Valid)
                                    log.warn { "signature was $v for key chain $signingCrossSigningKey ($signingUserId) ---> $signedKey ($signedUserId)" }
                            } == VerifyResult.Valid
                        if (isValid) when (crossSigningKey.getVerificationState(signingUserId)) {
                            is KeyVerificationState.Verified -> CrossSigned(true)
                            is KeyVerificationState.Blocked -> Blocked
                            else -> {
                                searchSignaturesForTrustLevel(
                                    signingUserId,
                                    { olm.sign.verify(crossSigningKey, it) },
                                    signingCrossSigningKey,
                                    crossSigningKey.signatures,
                                    visitedKeys
                                ) ?: if (crossSigningKey.signed.usage.contains(MasterKey)
                                    && crossSigningKey.signed.userId == signedUserId
                                    && crossSigningKey.signed.userId == signingUserId
                                ) CrossSigned(false) else null
                            }
                        } else null
                    } else null

                    val deviceKey = signatureKey.keyId?.let { store.keys.getDeviceKey(signingUserId, it) }?.value
                    val signingDeviceKey = deviceKey?.get<Ed25519Key>()
                    val deviceKeyState = if (signingDeviceKey != null) {
                        val isValid = verifySignedObject(mapOf(signingUserId to setOf(signingDeviceKey)))
                            .also { v ->
                                if (v != VerifyResult.Valid)
                                    log.warn { "signature was $v for key chain $signingCrossSigningKey ($signingUserId) ---> $signedKey ($signedUserId)" }
                            } == VerifyResult.Valid
                        if (isValid) when (deviceKey.getVerificationState(signingUserId, deviceKey.signed.deviceId)) {
                            is KeyVerificationState.Verified -> CrossSigned(true)
                            is KeyVerificationState.Blocked -> Blocked
                            else -> searchSignaturesForTrustLevel(
                                signedUserId,
                                { olm.sign.verify(deviceKey, it) },
                                signingDeviceKey,
                                deviceKey.signatures,
                                visitedKeys
                            )
                        } else null
                    } else null

                    val signingKey = signingCrossSigningKey ?: signingDeviceKey
                    if (signingKey != null) {
                        store.keys.saveKeyChainLink(KeyChainLink(signingUserId, signingKey, signedUserId, signedKey))
                    }

                    listOf(crossSigningKeyState, deviceKeyState)
                }.toSet()
        }.toSet()
        return when {
            states.any { it is CrossSigned && it.verified } -> CrossSigned(true)
            states.any { it is CrossSigned && !it.verified } -> CrossSigned(false)
            states.contains(Blocked) -> Blocked
            else -> null
        }
    }

    internal suspend fun trustAndSignKeys(keys: Set<Ed25519Key>, userId: UserId) {
        log.debug { "sign keys (when possible): $keys" }
        val signedDeviceKeys = keys.mapNotNull { key ->
            val deviceKey = key.keyId?.let { store.keys.getDeviceKey(userId, it) }?.value?.signed
            if (deviceKey != null) {
                store.keys.saveKeyVerificationState(
                    key, deviceKey.userId, deviceKey.deviceId, KeyVerificationState.Verified(key.value)
                )
                updateTrustLevelOfKey(userId, key)
                try {
                    if (userId == ownUserId && deviceKey.get<Ed25519Key>() == key) {
                        log.info { "sign own accounts device with own self signing key" }
                        olm.sign.sign(deviceKey, SignWith.AllowedSecrets(M_CROSS_SIGNING_SELF_SIGNING))
                    } else null
                } catch (error: Throwable) {
                    log.warn { "could not sign key $key: ${error.message}" }
                    null
                }
            } else null
        }
        val signedCrossSigningKeys = keys.mapNotNull { key ->
            val crossSigningKey = key.keyId?.let { store.keys.getCrossSigningKey(userId, it) }?.value?.signed
            if (crossSigningKey != null) {
                store.keys.saveKeyVerificationState(
                    key, crossSigningKey.userId, null, KeyVerificationState.Verified(key.value)
                )
                updateTrustLevelOfKey(userId, key)
                if (crossSigningKey.usage.contains(MasterKey)) {
                    try {
                        if (crossSigningKey.get<Ed25519Key>() == key) {
                            if (userId == ownUserId) {
                                log.info { "sign own master key with own device key" }
                                olm.sign.sign(crossSigningKey, SignWith.DeviceKey)
                            } else {
                                log.info { "sign other users master key with own user signing key" }
                                olm.sign.sign(crossSigningKey, SignWith.AllowedSecrets(M_CROSS_SIGNING_USER_SIGNING))
                            }
                        } else null
                    } catch (error: Throwable) {
                        log.warn { "could not sign key $key: ${error.message}" }
                        null
                    }
                } else null
            } else null
        }
        if (signedDeviceKeys.isNotEmpty() || signedCrossSigningKeys.isNotEmpty()) {
            log.debug { "upload signed keys: ${signedDeviceKeys + signedCrossSigningKeys}" }
            val response = api.keys.addSignatures(signedDeviceKeys.toSet(), signedCrossSigningKeys.toSet()).getOrThrow()
            if (response.failures.isNotEmpty()) {
                log.error { "could not add signatures to server: ${response.failures}" }
                throw UploadSignaturesException(response.failures.toString())
            }
        }
    }

    private suspend fun Keys.getVerificationState(userId: UserId, deviceId: String? = null) =
        this.asFlow().mapNotNull { store.keys.getKeyVerificationState(it, userId, deviceId) }.firstOrNull()

    private suspend fun SignedCrossSigningKeys.getVerificationState(userId: UserId) =
        this.signed.keys.getVerificationState(userId)

    private suspend fun SignedDeviceKeys.getVerificationState(userId: UserId, deviceId: String) =
        this.signed.keys.getVerificationState(userId, deviceId)
}