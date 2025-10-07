package net.folivo.trixnity.client.key

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.store.KeySignatureTrustLevel.*
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.model.keys.CrossSigningKeysUsage.MasterKey
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key
import net.folivo.trixnity.crypto.SecretType
import net.folivo.trixnity.crypto.SecretType.M_CROSS_SIGNING_SELF_SIGNING
import net.folivo.trixnity.crypto.SecretType.M_CROSS_SIGNING_USER_SIGNING
import net.folivo.trixnity.crypto.driver.CryptoDriver
import net.folivo.trixnity.crypto.driver.keys.Ed25519PublicKey
import net.folivo.trixnity.crypto.driver.keys.Ed25519SecretKey
import net.folivo.trixnity.crypto.key.decryptSecret
import net.folivo.trixnity.crypto.key.get
import net.folivo.trixnity.crypto.sign.*
import net.folivo.trixnity.utils.decodeUnpaddedBase64Bytes
import kotlin.jvm.JvmName

private val log = KotlinLogging.logger("net.folivo.trixnity.client.key.KeyTrustService")

interface KeyTrustService {

    suspend fun checkOwnAdvertisedMasterKeyAndVerifySelf(
        key: ByteArray,
        keyId: String,
        keyInfo: SecretKeyEventContent
    ): Result<Unit>

    suspend fun trustAndSignKeys(keys: Set<Ed25519Key>, userId: UserId)
    suspend fun calculateDeviceKeysTrustLevel(deviceKeys: SignedDeviceKeys): KeySignatureTrustLevel
    suspend fun calculateCrossSigningKeysTrustLevel(crossSigningKeys: SignedCrossSigningKeys): KeySignatureTrustLevel

    suspend fun updateTrustLevelOfKeyChainSignedBy(
        signingUserId: UserId,
        signingKey: Ed25519Key,
    )
}

class KeyTrustServiceImpl(
    private val userInfo: UserInfo,
    private val keyStore: KeyStore,
    private val globalAccountDataStore: GlobalAccountDataStore,
    private val signService: SignService,
    private val api: MatrixClientServerApiClient,
    private val driver: CryptoDriver,
) : KeyTrustService {

    override suspend fun checkOwnAdvertisedMasterKeyAndVerifySelf(
        key: ByteArray,
        keyId: String,
        keyInfo: SecretKeyEventContent
    ): Result<Unit> {
        val encryptedMasterKey =
            SecretType.M_CROSS_SIGNING_MASTER.getEncryptedSecret(globalAccountDataStore).first()?.content
                ?: return Result.failure(MasterKeyInvalidException("could not find encrypted master key"))
        val decryptedPublicKey =
            kotlin.runCatching {
                decryptSecret(
                    key = key,
                    keyId = keyId,
                    keyInfo = keyInfo,
                    secretName = SecretType.M_CROSS_SIGNING_MASTER.id,
                    secret = encryptedMasterKey,
                    json = api.json
                )
            }.getOrNull()
                ?.let { privateKey ->
                    driver.key.ed25519SecretKey(privateKey)
                        .use(Ed25519SecretKey::publicKey)
                        .use(Ed25519PublicKey::base64)
                }
        val advertisedPublicKey =
            keyStore.getCrossSigningKey(userInfo.userId, MasterKey)?.value?.signed?.get<Ed25519Key>()

        return if (advertisedPublicKey?.value?.value?.decodeUnpaddedBase64Bytes()
                ?.contentEquals(decryptedPublicKey?.decodeUnpaddedBase64Bytes()) == true
        ) {
            val ownDeviceKeys =
                keyStore.getDeviceKey(userInfo.userId, userInfo.deviceId).first()?.value?.get<Ed25519Key>()
            kotlin.runCatching {
                trustAndSignKeys(setOfNotNull(advertisedPublicKey, ownDeviceKeys), userInfo.userId)
            }
        } else Result.failure(MasterKeyInvalidException("master public key $decryptedPublicKey did not match the advertised ${advertisedPublicKey?.value}"))
    }

    override suspend fun updateTrustLevelOfKeyChainSignedBy(
        signingUserId: UserId,
        signingKey: Ed25519Key,
    ) {
        updateTrustLevelOfKeyChainSignedBy(signingUserId, signingKey, mutableSetOf())
    }

    private suspend fun updateTrustLevelOfKeyChainSignedBy(
        signingUserId: UserId,
        signingKey: Ed25519Key,
        visitedKeys: MutableSet<Pair<UserId, String?>>
    ) {
        log.trace { "update trust level of all keys signed by $signingUserId $signingKey" }
        visitedKeys.add(signingUserId to signingKey.id)
        keyStore.getKeyChainLinksBySigningKey(signingUserId, signingKey)
            .filterNot { visitedKeys.contains(it.signedUserId to it.signedKey.id) }
            .forEach { keyChainLink ->
                updateTrustLevelOfKey(keyChainLink.signedUserId, keyChainLink.signedKey)
                updateTrustLevelOfKeyChainSignedBy(keyChainLink.signedUserId, keyChainLink.signedKey, visitedKeys)
            }
    }

    private suspend fun updateTrustLevelOfKey(userId: UserId, key: Ed25519Key) {
        val keyId = key.id

        if (keyId != null) {
            val foundKey = MutableStateFlow(false)

            keyStore.updateDeviceKeys(userId) { oldDeviceKeys ->
                val foundDeviceKeys = oldDeviceKeys?.get(keyId)
                if (foundDeviceKeys != null) {
                    val newTrustLevel = calculateDeviceKeysTrustLevel(foundDeviceKeys.value)
                    foundKey.value = true
                    log.trace { "updated device keys ${foundDeviceKeys.value.signed.deviceId} of user $userId with trust level $newTrustLevel" }
                    oldDeviceKeys + (keyId to foundDeviceKeys.copy(trustLevel = newTrustLevel))
                } else oldDeviceKeys
            }
            if (foundKey.value.not()) {
                keyStore.updateCrossSigningKeys(userId) { oldKeys ->
                    val foundCrossSigningKeys = oldKeys?.firstOrNull { keys ->
                        keys.value.signed.keys.keys.filterIsInstance<Ed25519Key>().any { it.id == keyId }
                    }
                    if (foundCrossSigningKeys != null) {
                        val newTrustLevel = calculateCrossSigningKeysTrustLevel(foundCrossSigningKeys.value)
                        foundKey.value = true
                        log.trace { "updated cross signing key ${foundCrossSigningKeys.value.signed.usage.firstOrNull()?.name} of user $userId with trust level $newTrustLevel" }
                        (oldKeys - foundCrossSigningKeys) + foundCrossSigningKeys.copy(trustLevel = newTrustLevel)

                    } else oldKeys
                }
            }
            if (foundKey.value.not()) log.warn { "could not find device or cross signing keys of $key" }
        } else log.warn { "could not update trust level, because key id of $key was null" }
    }

    override suspend fun calculateDeviceKeysTrustLevel(deviceKeys: SignedDeviceKeys): KeySignatureTrustLevel {
        log.trace { "calculate trust level for ${deviceKeys.signed}" }
        val userId = deviceKeys.signed.userId
        val signedKey = deviceKeys.signed.keys.get<Ed25519Key>()
            ?: return Invalid("missing ed25519 key")
        return calculateTrustLevel(
            userId,
            { signService.verify(deviceKeys, it) },
            signedKey,
            deviceKeys.signatures ?: mapOf(),
            deviceKeys.getVerificationState(),
            false
        ).also { log.trace { "calculated trust level of ${deviceKeys.signed} from $userId is $it" } }
    }

    override suspend fun calculateCrossSigningKeysTrustLevel(crossSigningKeys: SignedCrossSigningKeys): KeySignatureTrustLevel {
        log.trace { "calculate trust level for ${crossSigningKeys.signed}" }
        val userId = crossSigningKeys.signed.userId
        val signedKey = crossSigningKeys.signed.keys.get<Ed25519Key>()
            ?: return Invalid("missing ed25519 key")
        return calculateTrustLevel(
            userId,
            { signService.verify(crossSigningKeys, it) },
            signedKey,
            crossSigningKeys.signatures ?: mapOf(),
            crossSigningKeys.getVerificationState(),
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
        val masterKey = keyStore.getCrossSigningKey(userId, MasterKey)
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
        visitedKeys.add(signedUserId to signedKey.id)
        keyStore.deleteKeyChainLinksBySignedKey(signedUserId, signedKey)
        val states = signatures.flatMap { (signingUserId, signatureKeys) ->
            signatureKeys
                .filterIsInstance<Ed25519Key>()
                .filterNot { visitedKeys.contains(signingUserId to it.id) }
                .flatMap { signatureKey ->
                    visitedKeys.add(signingUserId to signatureKey.id)

                    val crossSigningKey =
                        signatureKey.id?.let { keyStore.getCrossSigningKey(signingUserId, it) }?.value
                    val signingCrossSigningKey = crossSigningKey?.signed?.get<Ed25519Key>()
                    val crossSigningKeyState = if (signingCrossSigningKey != null) {
                        val isValid = verifySignedObject(mapOf(signingUserId to setOf(signingCrossSigningKey)))
                            .also { v ->
                                if (v != VerifyResult.Valid)
                                    log.warn { "signature was $v for key chain $signingCrossSigningKey ($signingUserId) ---> $signedKey ($signedUserId)" }
                            } == VerifyResult.Valid
                        if (isValid) when (crossSigningKey.getVerificationState()) {
                            is KeyVerificationState.Verified -> CrossSigned(true)
                            is KeyVerificationState.Blocked -> Blocked
                            else -> {
                                searchSignaturesForTrustLevel(
                                    signingUserId,
                                    { signService.verify(crossSigningKey, it) },
                                    signingCrossSigningKey,
                                    crossSigningKey.signatures ?: mapOf(),
                                    visitedKeys
                                ) ?: if (crossSigningKey.signed.usage.contains(MasterKey)
                                    && crossSigningKey.signed.userId == signedUserId
                                    && crossSigningKey.signed.userId == signingUserId
                                ) CrossSigned(false) else null
                            }
                        } else null
                    } else null

                    val deviceKey = signatureKey.id?.let { keyStore.getDeviceKey(signingUserId, it).first() }?.value
                    val signingDeviceKey = deviceKey?.get<Ed25519Key>()
                    val deviceKeyState = if (signingDeviceKey != null) {
                        val isValid = verifySignedObject(mapOf(signingUserId to setOf(signingDeviceKey)))
                            .also { v ->
                                if (v != VerifyResult.Valid)
                                    log.warn { "signature was $v for key chain $signingCrossSigningKey ($signingUserId) ---> $signedKey ($signedUserId)" }
                            } == VerifyResult.Valid
                        if (isValid) when (deviceKey.getVerificationState()) {
                            is KeyVerificationState.Verified -> CrossSigned(true)
                            is KeyVerificationState.Blocked -> Blocked
                            else -> searchSignaturesForTrustLevel(
                                signedUserId,
                                { signService.verify(deviceKey, it) },
                                signingDeviceKey,
                                deviceKey.signatures ?: mapOf(),
                                visitedKeys
                            )
                        } else null
                    } else null

                    val signingKey = signingCrossSigningKey ?: signingDeviceKey
                    if (signingKey != null) {
                        keyStore.saveKeyChainLink(KeyChainLink(signingUserId, signingKey, signedUserId, signedKey))
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

    private suspend fun signWithSecret(type: SecretType): SignWith.KeyPair {
        val privateKey = keyStore.getSecrets()[type]?.decryptedPrivateKey
        requireNotNull(privateKey) { "could not find private key of $type" }
        val publicKey =
            keyStore.getCrossSigningKey(
                userInfo.userId,
                when (type) {
                    M_CROSS_SIGNING_SELF_SIGNING -> CrossSigningKeysUsage.SelfSigningKey
                    M_CROSS_SIGNING_USER_SIGNING -> CrossSigningKeysUsage.UserSigningKey
                    else -> throw IllegalArgumentException("cannot sign with $type")
                }
            )?.value?.signed?.get<Ed25519Key>()?.value
        requireNotNull(publicKey) { "could not find public key of $type" }
        return SignWith.KeyPair(privateKey, publicKey.value)
    }

    override suspend fun trustAndSignKeys(keys: Set<Ed25519Key>, userId: UserId) {
        log.debug { "sign keys of $userId (when possible): $keys" }
        val signedDeviceKeys = keys.mapNotNull { key ->
            val deviceKey = key.id?.let { keyStore.getDeviceKey(userId, it).first() }?.value?.signed
            if (deviceKey != null) {
                keyStore.saveKeyVerificationState(key, KeyVerificationState.Verified(key.value.value))
                updateTrustLevelOfKey(userId, key)
                try {
                    if (userId == userInfo.userId && deviceKey.get<Ed25519Key>() == key) {
                        signService.sign(deviceKey, signWithSecret(M_CROSS_SIGNING_SELF_SIGNING))
                            .also { log.info { "signed own accounts device with own self signing key" } }
                    } else null
                } catch (error: Exception) {
                    log.warn { "could not sign device key $key with self signing key: ${error.message}" }
                    null
                }
            } else null
        }
        val signedCrossSigningKeys = keys.mapNotNull { key ->
            val crossSigningKey = key.id?.let { keyStore.getCrossSigningKey(userId, it) }?.value?.signed
            if (crossSigningKey != null) {
                keyStore.saveKeyVerificationState(key, KeyVerificationState.Verified(key.value.value))
                updateTrustLevelOfKey(userId, key)
                if (crossSigningKey.usage.contains(MasterKey)) {
                    if (crossSigningKey.get<Ed25519Key>() == key) {
                        if (userId == userInfo.userId) {
                            try {
                                signService.sign(crossSigningKey, SignWith.DeviceKey)
                                    .also { log.info { "signed own master key with own device key" } }
                            } catch (error: Exception) {
                                log.warn { "could not sign own master key $key with device key: ${error.message}" }
                                null
                            }
                        } else {
                            try {
                                signService.sign(crossSigningKey, signWithSecret(M_CROSS_SIGNING_USER_SIGNING))
                                    .also { log.info { "signed other users master key with own user signing key" } }
                            } catch (error: Exception) {
                                log.warn { "could not sign other users master key $key with user signing key: ${error.message}" }
                                null
                            }
                        }
                    } else null
                } else null
            } else null
        }
        if (signedDeviceKeys.isNotEmpty() || signedCrossSigningKeys.isNotEmpty()) {
            log.debug { "upload signed keys: ${signedDeviceKeys + signedCrossSigningKeys}" }
            val response = api.key.addSignatures(signedDeviceKeys.toSet(), signedCrossSigningKeys.toSet()).getOrThrow()
            if (response.failures.isNotEmpty()) {
                log.error { "could not add signatures to server: ${response.failures}" }
                throw UploadSignaturesException(response.failures.toString())
            }
        }
    }

    private suspend fun Keys.getVerificationState() =
        this.asFlow().mapNotNull { keyStore.getKeyVerificationState(it) }.firstOrNull()

    @JvmName("getVerificationStateCsk")
    private suspend fun SignedCrossSigningKeys.getVerificationState() =
        this.signed.keys.getVerificationState()

    @JvmName("getVerificationStateDk")
    private suspend fun SignedDeviceKeys.getVerificationState() =
        this.signed.keys.getVerificationState()
}