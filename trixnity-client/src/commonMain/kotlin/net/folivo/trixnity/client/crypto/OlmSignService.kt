package net.folivo.trixnity.client.crypto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.serializer
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.crypto.*
import net.folivo.trixnity.core.model.crypto.Key.Curve25519Key
import net.folivo.trixnity.core.model.crypto.Key.Ed25519Key
import net.folivo.trixnity.core.serialization.canonicalJson
import net.folivo.trixnity.olm.OlmAccount
import net.folivo.trixnity.olm.OlmUtility

class OlmSignService internal constructor(
    private val json: Json,
    private val store: Store,
    private val account: OlmAccount,
    private val utility: OlmUtility,
) {
    private val myUserId = store.account.userId.value ?: throw IllegalArgumentException("userId must not be null")
    private val myDeviceId = store.account.deviceId.value ?: throw IllegalArgumentException("deviceId must not be null")

    fun signatures(jsonObject: JsonObject): Signatures<UserId> {
        val signature = account.sign(canonicalFilteredJson(jsonObject))
        return mapOf(
            myUserId to keysOf(
                Ed25519Key(
                    keyId = myDeviceId,
                    value = signature
                )
            )
        )
    }

    inline fun <reified T> sign(unsignedObject: T): Signed<T, UserId> {
        return sign(unsignedObject, serializer())
    }

    fun <T> sign(unsignedObject: T, serializer: KSerializer<T>): Signed<T, UserId> {
        val jsonObject = json.encodeToJsonElement(serializer, unsignedObject)
        require(jsonObject is JsonObject)
        return Signed(unsignedObject, signatures(jsonObject))
    }

    fun signCurve25519Key(key: Curve25519Key): Key.SignedCurve25519Key {
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
        return when {
            signedObject is Key.SignedCurve25519Key -> verify(
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
                                ?: store.keys.getCrossSigningKey(userId, it)?.value?.signed?.keys
                                    ?.get<Ed25519Key>()?.value
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