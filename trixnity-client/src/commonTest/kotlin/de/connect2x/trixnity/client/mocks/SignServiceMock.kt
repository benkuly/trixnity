package de.connect2x.trixnity.client.mocks

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.keys.DeviceKeys
import de.connect2x.trixnity.core.model.keys.Key
import de.connect2x.trixnity.core.model.keys.Signatures
import de.connect2x.trixnity.core.model.keys.Signed
import de.connect2x.trixnity.crypto.sign.SignService
import de.connect2x.trixnity.crypto.sign.SignWith
import de.connect2x.trixnity.crypto.sign.VerifyResult

class SignServiceMock : SignService {
    override suspend fun getSelfSignedDeviceKeys(): Signed<DeviceKeys, UserId> {
        throw NotImplementedError()
    }

    override suspend fun signatures(
        jsonObject: JsonObject,
        signWith: SignWith
    ): Signatures<UserId> {
        throw NotImplementedError()
    }

    lateinit var returnSignatures: List<Signatures<UserId>>
    override suspend fun <T> signatures(
        unsignedObject: T,
        serializer: KSerializer<T>,
        signWith: SignWith
    ): Signatures<UserId> {
        return returnSignatures[0].also { if (returnSignatures.size > 1) returnSignatures = returnSignatures - it }
    }

    val sign = mutableListOf<Signatures<UserId>>()
    override suspend fun <T> sign(
        unsignedObject: T,
        serializer: KSerializer<T>,
        signWith: SignWith
    ): Signed<T, UserId> {
        return Signed(unsignedObject, sign.removeFirstOrNull())
    }

    override suspend fun signCurve25519Key(
        keyId: String,
        keyValue: String,
        fallback: Boolean?
    ): Key.SignedCurve25519Key {
        throw NotImplementedError()
    }

    var returnVerify: VerifyResult? = null
    override suspend fun <T> verify(
        signedObject: Signed<T, UserId>,
        serializer: KSerializer<T>,
        checkSignaturesOf: Map<UserId, Set<Key.Ed25519Key>>
    ): VerifyResult {
        return returnVerify ?: VerifyResult.Invalid("")
    }
}