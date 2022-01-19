package net.folivo.trixnity.client.crypto

import io.ktor.util.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializer
import net.folivo.trixnity.client.crypto.OlmSignService.SignWith.DeviceKey
import net.folivo.trixnity.client.store.AllowedSecretType
import net.folivo.trixnity.client.store.AllowedSecretType.M_CROSS_SIGNING_SELF_SIGNING
import net.folivo.trixnity.client.store.AllowedSecretType.M_CROSS_SIGNING_USER_SIGNING
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.CrossSigningKeysUsage.SelfSigningKey
import net.folivo.trixnity.core.model.keys.CrossSigningKeysUsage.UserSigningKey
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.model.keys.Key.Curve25519Key
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key
import net.folivo.trixnity.core.model.keys.Signatures
import net.folivo.trixnity.core.model.keys.Signed
import net.folivo.trixnity.core.model.keys.keysOf
import net.folivo.trixnity.core.serialization.canonicalJson
import net.folivo.trixnity.olm.OlmAccount
import net.folivo.trixnity.olm.OlmPkSigning
import net.folivo.trixnity.olm.OlmUtility
import net.folivo.trixnity.olm.freeAfter

class OlmSignService internal constructor(
    private val ownUserId: UserId,
    private val ownDeviceId: String,
    private val json: Json,
    private val store: Store,
    private val account: OlmAccount,
    private val utility: OlmUtility,
) {
    sealed interface SignWith {
        object DeviceKey : SignWith
        data class AllowedSecrets(val allowedSecretType: AllowedSecretType) : SignWith
        data class Custom(val privateKey: String, val publicKey: String) : SignWith
    }

    @OptIn(InternalAPI::class)
    suspend fun signatures(jsonObject: JsonObject, signWith: SignWith = DeviceKey): Signatures<UserId> {
        val stringToSign = canonicalFilteredJson(jsonObject)
        return when (signWith) {
            DeviceKey -> {
                mapOf(
                    ownUserId to keysOf(
                        Ed25519Key(
                            keyId = ownDeviceId,
                            value = account.sign(stringToSign)
                        )
                    )
                )
            }
            is SignWith.AllowedSecrets -> {
                val privateKey = store.keys.secrets.value[signWith.allowedSecretType]?.decryptedPrivateKey
                requireNotNull(privateKey) { "could not find self signing private key" }
                val publicKey =
                    store.keys.getCrossSigningKey(
                        ownUserId,
                        when (signWith.allowedSecretType) {
                            M_CROSS_SIGNING_SELF_SIGNING -> SelfSigningKey
                            M_CROSS_SIGNING_USER_SIGNING -> UserSigningKey
                            AllowedSecretType.M_MEGOLM_BACKUP_V1 ->
                                throw IllegalArgumentException("cannot sign with ${signWith.allowedSecretType}")
                        }
                    )?.value?.signed?.get<Ed25519Key>()?.keyId
                requireNotNull(publicKey) { "could not find self signing public key" }
                mapOf(
                    ownUserId to keysOf(
                        Ed25519Key(
                            keyId = publicKey,
                            value = freeAfter(OlmPkSigning.create(privateKey)) {
                                it.sign(stringToSign)
                            }
                        )
                    )
                )
            }
            is SignWith.Custom -> {
                mapOf(
                    ownUserId to keysOf(
                        Ed25519Key(
                            keyId = signWith.publicKey,
                            value = freeAfter(OlmPkSigning.create(signWith.privateKey)) {
                                it.sign(stringToSign)
                            }
                        )
                    )
                )
            }
        }
    }

    suspend inline fun <reified T> sign(unsignedObject: T, signWith: SignWith = DeviceKey): Signed<T, UserId> {
        return sign(unsignedObject, serializer(), signWith)
    }

    suspend fun <T> sign(
        unsignedObject: T,
        serializer: KSerializer<T>,
        signWith: SignWith = DeviceKey
    ): Signed<T, UserId> {
        val jsonObject = json.encodeToJsonElement(serializer, unsignedObject)
        require(jsonObject is JsonObject)
        return Signed(unsignedObject, signatures(jsonObject, signWith))
    }

    suspend fun signCurve25519Key(key: Curve25519Key): Key.SignedCurve25519Key {
        return Key.SignedCurve25519Key(
            keyId = key.keyId,
            value = key.value,
            signatures = signatures(JsonObject(mapOf("key" to JsonPrimitive(key.value))))
        )
    }

    suspend inline fun <reified T> verify(
        signedObject: Signed<T, UserId>,
        checkSignaturesOf: Map<UserId, Set<Ed25519Key>>
    ): VerifyResult {
        return verify(signedObject, serializer(), checkSignaturesOf)
    }

    @Serializable
    private data class VerifySignedKeyWrapper(
        val key: String
    )

    suspend fun <T> verify(
        signedObject: Signed<T, UserId>,
        serializer: KSerializer<T>,
        checkSignaturesOf: Map<UserId, Set<Ed25519Key>>
    ): VerifyResult {
        checkSignaturesOf.flatMap { it.value }.ifEmpty { return VerifyResult.MissingSignature("no signing keys given") }
        return when (signedObject) {
            is Key.SignedCurve25519Key -> verify(
                Signed(
                    VerifySignedKeyWrapper(signedObject.signed.value),
                    signedObject.signatures,
                ),
                checkSignaturesOf
            )
            else -> {
                val signed = signedObject.signed
                val jsonObject = json.encodeToJsonElement(serializer, signed)
                require(jsonObject is JsonObject)
                val signedJson = canonicalFilteredJson(jsonObject)
                val verifyResults = checkSignaturesOf.flatMap { (userId, signingKeys) ->
                    signingKeys.map { signingKey ->
                        val signatureKey = signedObject.signatures[userId]?.find { it.keyId == signingKey.keyId }
                            ?: return VerifyResult.MissingSignature("no signature found for signing key $signingKey")
                        try {
                            utility.verifyEd25519(
                                signingKey.value,
                                signedJson,
                                signatureKey.value
                            )
                            VerifyResult.Valid
                        } catch (exception: Exception) {
                            VerifyResult.Invalid("error ${exception.message} of public key $signingKey with signature $signatureKey")
                        }
                    }
                }
                return when {
                    verifyResults.any { it is VerifyResult.Invalid } -> verifyResults.first { it is VerifyResult.Invalid }
                    verifyResults.any { it is VerifyResult.MissingSignature } -> verifyResults.first { it is VerifyResult.MissingSignature }
                    else -> VerifyResult.Valid
                }
            }
        }
    }

    private fun canonicalFilteredJson(input: JsonObject): String =
        canonicalJson(JsonObject(input.filterKeys { it != "unsigned" && it != "signatures" }))
}