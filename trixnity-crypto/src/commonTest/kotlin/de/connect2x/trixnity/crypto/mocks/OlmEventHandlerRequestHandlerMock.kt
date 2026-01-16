package de.connect2x.trixnity.crypto.mocks

import de.connect2x.trixnity.core.model.keys.Keys
import de.connect2x.trixnity.crypto.olm.OlmEventHandlerRequestHandler

class OlmEventHandlerRequestHandlerMock: OlmEventHandlerRequestHandler {
    val setOneTimeKeysParam= mutableListOf<Pair<Keys?,Keys?>>()
    var setOneTimeKeys:Result<Unit>?=null
    override suspend fun setOneTimeKeys(oneTimeKeys: Keys?, fallbackKeys: Keys?): Result<Unit> {
        setOneTimeKeysParam.add(oneTimeKeys to fallbackKeys)
        return setOneTimeKeys?: Result.success(Unit)
    }
}