package net.folivo.trixnity.client.crypto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.serializer
import net.folivo.trixnity.client.crypto.VerificationState.*
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.crypto.*
import net.folivo.trixnity.core.model.crypto.Key.Curve25519Key
import net.folivo.trixnity.core.model.crypto.Key.Ed25519Key
import net.folivo.trixnity.core.model.events.Event.MessageEvent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.MegolmEncryptedEventContent
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

    @OptIn(ExperimentalStdlibApi::class)
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

    suspend inline fun <reified T> verify(signedObject: Signed<T, UserId>): KeyVerificationState {
        return verify(signedObject, serializer())
    }

    @Serializable
    private data class VerifySignedKeyWrapper(
        val key: String
    )

    suspend fun <T> verify(signedObject: Signed<T, UserId>, serializer: KSerializer<T>): KeyVerificationState {
        val signed = signedObject.signed
        return if (signedObject is Key.SignedCurve25519Key) verify(
            Signed(
                VerifySignedKeyWrapper(signedObject.signed.value),
                signedObject.signatures
            )
        ) else if (signed is DeviceKeys) verifyDeviceKeys(Signed(signed, signedObject.signatures))
        else {
            val jsonObject = json.encodeToJsonElement(serializer, signed)
            require(jsonObject is JsonObject)
            val signedJson = canonicalFilteredJson(jsonObject)
            return signedObject.signatures.flatMap { (userId, signatureKeys) ->
                signatureKeys.keys.filterIsInstance<Ed25519Key>().map { signatureKey ->
                    val key = store.deviceKeys.getKeyFromDevice<Ed25519Key>(userId, signatureKey.keyId).value
                    try {
                        utility.verifyEd25519(
                            key,
                            signedJson,
                            signatureKey.value
                        )
                        KeyVerificationState.Valid
                    } catch (exception: Exception) {
                        KeyVerificationState.Invalid(exception.message ?: "unknown: $exception")
                    }
                }
            }.firstOrNull { it is KeyVerificationState.Invalid } ?: KeyVerificationState.Valid
        }
    }

    private fun verifyDeviceKeys(deviceKeys: Signed<DeviceKeys, UserId>): KeyVerificationState {
        val jsonObject = json.encodeToJsonElement(deviceKeys.signed)
        require(jsonObject is JsonObject)
        val signedJson = canonicalFilteredJson(jsonObject)
        try {
            utility.verifyEd25519(
                deviceKeys.get<Ed25519Key>()?.value
                    ?: return KeyVerificationState.Invalid("no ed25591 key found"),
                signedJson,
                deviceKeys.signatures[deviceKeys.signed.userId]?.get<Ed25519Key>()?.value
                    ?: return KeyVerificationState.Invalid("no signature key found"),
            )
            return KeyVerificationState.Valid
        } catch (exception: Exception) {
            return KeyVerificationState.Invalid(exception.message ?: "unknown: $exception")
        }
    }

    private fun canonicalFilteredJson(input: JsonObject): String =
        canonicalJson(JsonObject(input.filterKeys { it != "unsigned" && it != "signatures" }))

    suspend fun verifyEncryptedMegolm(encryptedEvent: MessageEvent<MegolmEncryptedEventContent>): VerificationState {
        val deviceKeys = store.deviceKeys.get(encryptedEvent.sender)?.get(encryptedEvent.content.deviceId)?.keys
        val curve25519Key =
            deviceKeys?.filterIsInstance<Curve25519Key>()?.find { it.value == encryptedEvent.content.senderKey.value }
        val ed25519Key =
            deviceKeys?.filterIsInstance<Ed25519Key>()?.find { it.keyId == encryptedEvent.content.deviceId }
        return if (curve25519Key == null)
            Invalid("the sender key of the event is not known for this device")
        else if (ed25519Key != null && store.deviceKeys.isVerified(
                key = ed25519Key,
                userId = encryptedEvent.sender,
                deviceId = encryptedEvent.content.deviceId
            )
        ) Verified
        else Valid
    }
}