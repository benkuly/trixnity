package net.folivo.trixnity.crypto.sign

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializer
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.model.keys.Key.Curve25519Key
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key
import net.folivo.trixnity.core.serialization.canonicalJsonString
import net.folivo.trixnity.olm.OlmAccount
import net.folivo.trixnity.olm.OlmPkSigning
import net.folivo.trixnity.olm.OlmUtility
import net.folivo.trixnity.olm.freeAfter

interface SignService {
    suspend fun getSelfSignedDeviceKeys(): Signed<DeviceKeys, UserId>
    suspend fun signatures(jsonObject: JsonObject, signWith: SignWith = SignWith.DeviceKey): Signatures<UserId>

    suspend fun <T> signatures(
        unsignedObject: T,
        serializer: KSerializer<T>,
        signWith: SignWith = SignWith.DeviceKey
    ): Signatures<UserId>

    suspend fun <T> sign(
        unsignedObject: T,
        serializer: KSerializer<T>,
        signWith: SignWith = SignWith.DeviceKey
    ): Signed<T, UserId>

    suspend fun signCurve25519Key(key: Curve25519Key, signatureJsonKey: String = "key"): Key.SignedCurve25519Key

    suspend fun <T> verify(
        signedObject: Signed<T, UserId>,
        serializer: KSerializer<T>,
        checkSignaturesOf: Map<UserId, Set<Ed25519Key>>
    ): VerifyResult
}

class SignServiceImpl(
    private val userInfo: UserInfo,
    private val json: Json,
    private val store: SignServiceStore,
) : SignService {

    override suspend fun getSelfSignedDeviceKeys(): Signed<DeviceKeys, UserId> = sign(
        DeviceKeys(
            userId = userInfo.userId,
            deviceId = userInfo.deviceId,
            algorithms = setOf(EncryptionAlgorithm.Olm, EncryptionAlgorithm.Megolm),
            keys = keysOf(userInfo.signingPublicKey, userInfo.identityPublicKey)
        )
    )

    override suspend fun signatures(jsonObject: JsonObject, signWith: SignWith): Signatures<UserId> {
        val stringToSign = canonicalFilteredJson(jsonObject)
        return when (signWith) {
            SignWith.DeviceKey -> {
                freeAfter(
                    OlmAccount.unpickle(
                        store.getOlmPickleKey(),
                        store.getOlmAccount()
                    )
                ) { olmAccount ->
                    mapOf(
                        userInfo.userId to keysOf(
                            Ed25519Key(
                                keyId = userInfo.deviceId,
                                value = olmAccount.sign(stringToSign)
                            )
                        )
                    )
                }
            }

            is SignWith.PrivateKey -> {
                mapOf(
                    userInfo.userId to keysOf(
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

    override suspend fun <T> signatures(
        unsignedObject: T,
        serializer: KSerializer<T>,
        signWith: SignWith
    ): Signatures<UserId> {
        val jsonObject = json.encodeToJsonElement(serializer, unsignedObject)
        require(jsonObject is JsonObject)
        return signatures(jsonObject, signWith)
    }

    override suspend fun <T> sign(
        unsignedObject: T,
        serializer: KSerializer<T>,
        signWith: SignWith
    ): Signed<T, UserId> {
        return Signed(unsignedObject, signatures(unsignedObject, serializer, signWith))
    }


    override suspend fun signCurve25519Key(key: Curve25519Key, signatureJsonKey: String): Key.SignedCurve25519Key {
        return Key.SignedCurve25519Key(
            keyId = key.keyId,
            value = key.value,
            signatures = signatures(JsonObject(mapOf(signatureJsonKey to JsonPrimitive(key.value)))),
            fallback = key.fallback,
        )
    }

    @Serializable
    private data class VerifySignedKeyWrapper(
        val key: String
    )

    override suspend fun <T> verify(
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
                        val signatureKey = signedObject.signatures?.get(userId)?.find { it.keyId == signingKey.keyId }
                            ?: return VerifyResult.MissingSignature("no signature found for signing key $signingKey")
                        try {
                            freeAfter(OlmUtility.create()) { olmUtility ->
                                olmUtility.verifyEd25519(
                                    signingKey.value,
                                    signedJson,
                                    signatureKey.value
                                )
                            }
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
        canonicalJsonString(JsonObject(input.filterKeys { it != "unsigned" && it != "signatures" }))
}

suspend inline fun <reified T> SignService.sign(
    unsignedObject: T,
    signWith: SignWith = SignWith.DeviceKey
): Signed<T, UserId> =
    sign(unsignedObject, serializer(), signWith)

suspend inline fun <reified T> SignService.signatures(
    unsignedObject: T,
    signWith: SignWith = SignWith.DeviceKey
): Signatures<UserId> =
    signatures(unsignedObject, serializer(), signWith)

suspend inline fun <reified T> SignService.verify(
    signedObject: Signed<T, UserId>,
    checkSignaturesOf: Map<UserId, Set<Ed25519Key>>
): VerifyResult =
    verify(signedObject, serializer(), checkSignaturesOf)