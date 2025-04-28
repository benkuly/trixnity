package net.folivo.trixnity.crypto.key

import net.folivo.trixnity.core.model.keys.*

inline fun <reified T : Key> DeviceKeys.get(): T? =
    keys.keys.filterIsInstance<T>().firstOrNull()


inline fun <reified T : Key> CrossSigningKeys.get(): T? =
    keys.keys.filterIsInstance<T>().firstOrNull()


inline fun <reified T : Key> Keys.get(): T? =
    keys.filterIsInstance<T>().firstOrNull()


inline fun <reified T : Key> SignedDeviceKeys.get(): T? =
    signed.keys.keys.filterIsInstance<T>().firstOrNull()