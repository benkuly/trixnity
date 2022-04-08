package net.folivo.trixnity.client.mocks

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import net.folivo.trixnity.client.crypto.IOlmSignService
import net.folivo.trixnity.client.crypto.VerifyResult
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.model.keys.Signatures
import net.folivo.trixnity.core.model.keys.Signed

class OlmSignServiceMock : IOlmSignService {
    override suspend fun signatures(
        jsonObject: JsonObject,
        signWith: IOlmSignService.SignWith
    ): Signatures<UserId> {
        throw NotImplementedError()
    }

    lateinit var returnSignatures: List<Signatures<UserId>>
    override suspend fun <T> signatures(
        unsignedObject: T,
        serializer: KSerializer<T>,
        signWith: IOlmSignService.SignWith
    ): Signatures<UserId> {
        return returnSignatures[0].also { if (returnSignatures.size > 1) returnSignatures = returnSignatures - it }
    }

    override suspend fun <T> sign(
        unsignedObject: T,
        serializer: KSerializer<T>,
        signWith: IOlmSignService.SignWith
    ): Signed<T, UserId> {
        return Signed(unsignedObject, null)
    }

    override suspend fun signCurve25519Key(key: Key.Curve25519Key, jsonKey: String): Key.SignedCurve25519Key {
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