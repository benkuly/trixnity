package net.folivo.trixnity.client.crypto

import net.folivo.trixnity.clientserverapi.client.IMatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.crypto.olm.DeviceOneTimeKeysCountSubscriber
import net.folivo.trixnity.crypto.olm.OneTimeKeysCountEmitter

class ClientOneTimeKeysCountEmitter(private val api: IMatrixClientServerApiClient) : OneTimeKeysCountEmitter {
    override fun subscribeDeviceOneTimeKeysCount(subscriber: DeviceOneTimeKeysCountSubscriber) {
        api.sync.subscribeDeviceOneTimeKeysCount(subscriber)
    }

    override fun unsubscribeDeviceOneTimeKeysCount(subscriber: DeviceOneTimeKeysCountSubscriber) {
        api.sync.unsubscribeDeviceOneTimeKeysCount(subscriber)
    }
}