package net.folivo.trixnity.crypto.mocks

import net.folivo.trixnity.core.model.keys.Keys
import net.folivo.trixnity.crypto.olm.OlmEventHandlerRequestHandler

class OlmEventHandlerRequestHandlerMock: OlmEventHandlerRequestHandler {
    val setOneTimeKeysParam= mutableListOf<Pair<Keys?,Keys?>>()
    var setOneTimeKeys:Result<Unit>?=null
    override suspend fun setOneTimeKeys(oneTimeKeys: Keys?, fallbackKeys: Keys?): Result<Unit> {
        setOneTimeKeysParam.add(oneTimeKeys to fallbackKeys)
        return setOneTimeKeys?: Result.success(Unit)
    }
}