package net.folivo.trixnity.client.crypto

import io.ktor.util.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.serializer
import net.folivo.trixnity.client.crypto.OlmSignService.SignWith.*
import net.folivo.trixnity.client.store.AllowedSecretType.M_CROSS_SIGNING_SELF_SIGNING
import net.folivo.trixnity.client.store.AllowedSecretType.M_CROSS_SIGNING_USER_SIGNING
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.crypto.*
import net.folivo.trixnity.core.model.crypto.CrossSigningKeysUsage.SelfSigningKey
import net.folivo.trixnity.core.model.crypto.CrossSigningKeysUsage.UserSigningKey
import net.folivo.trixnity.core.model.crypto.Key.Curve25519Key
import net.folivo.trixnity.core.model.crypto.Key.Ed25519Key
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
    enum class SignWith {
        DEVICE_KEY,
        SELF_SIGNING_KEY,
        USER_SIGNING_KEY;
    }

    @OptIn(InternalAPI::class)
    suspend fun signatures(jsonObject: JsonObject, signWith: SignWith = DEVICE_KEY): Signatures<UserId> {
        val stringToSign = canonicalFilteredJson(jsonObject)
        return when (signWith) {
            DEVICE_KEY -> {
                mapOf(
                    ownUserId to keysOf(
                        Ed25519Key(
                            keyId = ownDeviceId,
                            value = account.sign(stringToSign)
                        )
                    )
                )
            }
            SELF_SIGNING_KEY -> {
                val privateKey = store.keys.secrets.value[M_CROSS_SIGNING_SELF_SIGNING]?.decryptedPrivateKey
                requireNotNull(privateKey) { "could not find self signing private key" }
                val publicKey =
                    store.keys.getCrossSigningKey(ownUserId, SelfSigningKey)?.value?.signed?.get<Ed25519Key>()?.keyId
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
            USER_SIGNING_KEY -> {
                val privateKey = store.keys.secrets.value[M_CROSS_SIGNING_USER_SIGNING]?.decryptedPrivateKey
                requireNotNull(privateKey) { "could not find user signing private key" }
                val publicKey =
                    store.keys.getCrossSigningKey(ownUserId, UserSigningKey)?.value?.signed?.get<Ed25519Key>()?.keyId
                requireNotNull(publicKey) { "could not find user signing public key" }
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
        }
    }

    suspend inline fun <reified T> sign(unsignedObject: T, signWith: SignWith = DEVICE_KEY): Signed<T, UserId> {
        return sign(unsignedObject, serializer(), signWith)
    }

    suspend fun <T> sign(
        unsignedObject: T,
        serializer: KSerializer<T>,
        signWith: SignWith = DEVICE_KEY
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

    suspend inline fun <reified T> verify(signedObject: Signed<T, UserId>): VerifyResult {
        return verify(signedObject, serializer())
    }

    @Serializable
    private data class VerifySignedKeyWrapper(
        val key: String
    )

    suspend fun <T> verify(signedObject: Signed<T, UserId>, serializer: KSerializer<T>): VerifyResult {
        val signed = signedObject.signed
        return when (signedObject) {
            is Key.SignedCurve25519Key -> verify(
                Signed(VerifySignedKeyWrapper(signedObject.signed.value), signedObject.signatures)
            )
            else -> {
                val jsonObject = json.encodeToJsonElement(serializer, signed)
                require(jsonObject is JsonObject)
                val signedJson = canonicalFilteredJson(jsonObject)
                val verifyResults = signedObject.signatures.flatMap { (userId, signatureKeys) ->
                    signatureKeys.keys.filterIsInstance<Ed25519Key>().map { signatureKey ->
                        val key = signatureKey.keyId?.let {
                            store.keys.getFromDevice<Ed25519Key>(userId, it)?.value
                                ?: store.keys.getCrossSigningKey(userId, it)?.value?.signed?.get<Ed25519Key>()?.value
                        } ?: return VerifyResult.MissingSignature
                        try {
                            utility.verifyEd25519(
                                key,
                                signedJson,
                                signatureKey.value
                            )
                            VerifyResult.Valid
                        } catch (exception: Exception) {
                            VerifyResult.Invalid(exception.message ?: "unknown: $exception")
                        }
                    }
                }
                return when {
                    verifyResults.any { it is VerifyResult.Invalid } -> verifyResults.first { it is VerifyResult.Invalid }
                    verifyResults.any { it is VerifyResult.MissingSignature } -> VerifyResult.MissingSignature
                    else -> VerifyResult.Valid
                }
            }
        }
    }

    suspend fun verifySelfSignedDeviceKeys(deviceKeys: Signed<DeviceKeys, UserId>): VerifyResult {
        val jsonObject = json.encodeToJsonElement(deviceKeys.signed)
        require(jsonObject is JsonObject)
        val signedJson = canonicalFilteredJson(jsonObject)
        val userId = deviceKeys.signed.userId
        val userSignatures = deviceKeys.signatures[userId]
        val selfSigningSignature = userSignatures?.get<Ed25519Key>()
            ?: return VerifyResult.MissingSignature
        try {
            utility.verifyEd25519(
                deviceKeys.get<Ed25519Key>()?.value
                    ?: return VerifyResult.Invalid("no ed25591 key found"),
                signedJson,
                selfSigningSignature.value,
            )
        } catch (exception: Exception) {
            return VerifyResult.Invalid(exception.message ?: "unknown: $exception")
        }
        return verify(
            Signed(
                deviceKeys.signed,
                deviceKeys.signatures + (userId to Keys((userSignatures.keys - selfSigningSignature)))
            )
        )
    }

    private fun canonicalFilteredJson(input: JsonObject): String =
        canonicalJson(JsonObject(input.filterKeys { it != "unsigned" && it != "signatures" }))
}