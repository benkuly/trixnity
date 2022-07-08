package net.folivo.trixnity.crypto.olm

import net.folivo.trixnity.clientserverapi.model.sync.DeviceOneTimeKeysCount

typealias DeviceOneTimeKeysCountSubscriber = suspend (DeviceOneTimeKeysCount?) -> Unit

interface OneTimeKeysCountEmitter {
    fun subscribeDeviceOneTimeKeysCount(subscriber: DeviceOneTimeKeysCountSubscriber)
    fun unsubscribeDeviceOneTimeKeysCount(subscriber: DeviceOneTimeKeysCountSubscriber)
}