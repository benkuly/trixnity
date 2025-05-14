package net.folivo.trixnity.crypto.sign

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key
import net.folivo.trixnity.core.model.keys.KeyValue.SignedCurve25519KeyValue.SignedCurve25519KeyValueSignable
import net.folivo.trixnity.core.serialization.canonicalJsonString
import net.folivo.trixnity.olm.OlmAccount
import net.folivo.trixnity.olm.OlmPkSigning
import net.folivo.trixnity.olm.OlmUtility
import net.folivo.trixnity.olm.freeAfter

interface SignService {
    suspend fun getSelfSignedDeviceKeys(): Signed<DeviceKeys, UserId>
    suspend fun signatures(
        jsonObject: JsonObject,
        signWith: SignWith = SignWith.DeviceKey,
    ): Signatures<UserId>

    suspend fun <T> signatures(
        unsignedObject: T,
        serializer: KSerializer<T>,
        signWith: SignWith = SignWith.DeviceKey,
    ): Signatures<UserId>

    suspend fun <T> sign(
        unsignedObject: T,
        serializer: KSerializer<T>,
        signWith: SignWith = SignWith.DeviceKey,
    ): Signed<T, UserId>

    suspend fun signCurve25519Key(
        keyId: String,
        keyValue: String,
        fallback: Boolean? = null,
    ): Key.SignedCurve25519Key

    suspend fun <T> verify(
        signedObject: Signed<T, UserId>,
        serializer: KSerializer<T>,
        checkSignaturesOf: Map<UserId, Set<Ed25519Key>>,
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
            keys = keysOf(userInfo.signingPublicKey, userInfo.identityPublicKey),
        ),
        SignWith.DeviceKey
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
                                id = userInfo.deviceId,
                                value = KeyValue.Ed25519KeyValue(olmAccount.sign(stringToSign))
                            )
                        )
                    )
                }
            }

            is SignWith.KeyPair -> {
                mapOf(
                    userInfo.userId to keysOf(
                        Ed25519Key(
                            id = signWith.publicKey,
                            value = KeyValue.Ed25519KeyValue(
                                freeAfter(OlmPkSigning.create(signWith.privateKey)) {
                                    it.sign(stringToSign)
                                }
                            )
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
        val jsonObject = json.encodeToJsonElement(serializer, unsignedObject).jsonObject
        return signatures(jsonObject, signWith)
    }

    override suspend fun <T> sign(
        unsignedObject: T,
        serializer: KSerializer<T>,
        signWith: SignWith
    ): Signed<T, UserId> {
        return Signed(unsignedObject, signatures(unsignedObject, serializer, signWith))
    }

    override suspend fun signCurve25519Key(
        keyId: String,
        keyValue: String,
        fallback: Boolean?
    ): Key.SignedCurve25519Key {
        return Key.SignedCurve25519Key(
            id = keyId,
            value = KeyValue.SignedCurve25519KeyValue(
                value = keyValue,
                fallback = fallback,
                signatures = signatures(
                    SignedCurve25519KeyValueSignable(keyValue, fallback)
                )
            )
        )
    }

    override suspend fun <T> verify(
        signedObject: Signed<T, UserId>,
        serializer: KSerializer<T>,
        checkSignaturesOf: Map<UserId, Set<Ed25519Key>>
    ): VerifyResult {
        checkSignaturesOf.flatMap { it.value }.ifEmpty { return VerifyResult.MissingSignature("no signing keys given") }
        val signedRaw = signedObject.raw ?: json.encodeToJsonElement(serializer, signedObject.signed).jsonObject
        val signedString = canonicalFilteredJson(signedRaw)
        val verifyResults = checkSignaturesOf.flatMap { (userId, signingKeys) ->
            signingKeys.map { signingKey ->
                val signatureKey = signedObject.signatures?.get(userId)?.find { it.id == signingKey.id }
                    ?: return VerifyResult.MissingSignature(
                        "no signature found for signing key ${signingKey.id}," +
                                "got ${signedObject.signatures?.get(userId)?.map { it.id }} instead"
                    )
                try {
                    freeAfter(OlmUtility.create()) { olmUtility ->
                        olmUtility.verifyEd25519(
                            key = signingKey.value.value,
                            message = signedString,
                            signature = signatureKey.value.value
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

    private fun canonicalFilteredJson(input: JsonObject): String =
        canonicalJsonString(JsonObject(input.filterKeys { it != "unsigned" && it != "signatures" }))
}

suspend inline fun <reified T> SignService.sign(
    unsignedObject: T,
    signWith: SignWith = SignWith.DeviceKey
): Signed<T, UserId> =
    sign(unsignedObject, serializer(), signWith)

suspend inline fun <reified T> SignService.sign(
    signedObject: Signed<T, UserId>,
    signWith: SignWith = SignWith.DeviceKey
): Signed<T, UserId> {
    val raw = signedObject.raw
    return signedObject +
            if (raw == null) signatures(signedObject.signed, serializer(), signWith)
            else signatures(raw, signWith)
}

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