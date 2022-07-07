package net.folivo.trixnity.client.mocks

import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.DeviceKeys
import net.folivo.trixnity.core.model.keys.Signed
import net.folivo.trixnity.crypto.olm.IOlmService

class OlmServiceMock : IOlmService {
    override val event: OlmEventServiceMock = OlmEventServiceMock()
    override val decrypter: OlmDecrypterMock = OlmDecrypterMock()

    override fun start() {
        TODO("Not yet implemented")
    }

    override fun stop() {
        TODO("Not yet implemented")
    }

    override suspend fun getSelfSignedDeviceKeys(): Signed<DeviceKeys, UserId> {
        TODO("Not yet implemented")
    }
}