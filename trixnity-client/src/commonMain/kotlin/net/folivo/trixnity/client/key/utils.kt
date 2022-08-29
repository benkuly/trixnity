package net.folivo.trixnity.client.key

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.MegolmBackupV1EventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.SelfSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.UserSigningKeyEventContent
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.crypto.SecretType
import net.folivo.trixnity.olm.OlmAccount

internal suspend fun KeyStore.waitForUpdateOutdatedKey(user: UserId) = waitForUpdateOutdatedKey(setOf(user))
internal suspend fun KeyStore.waitForUpdateOutdatedKey(users: Set<UserId> = setOf()) {
    outdatedKeys.first { if (users.isEmpty()) it.isEmpty() else it.none { outdated -> users.contains(outdated) } }
}

internal suspend inline fun <reified T : Key> KeyStore.getFromDevice(
    userId: UserId,
    deviceId: String?
): T? {
    return getDeviceKeys(userId)?.get(deviceId)?.value?.get()
}

internal suspend inline fun <reified T : Key> KeyStore.getOrFetchKeyFromDevice(
    userId: UserId,
    deviceId: String?
): T? {
    val key = this.getFromDevice<T>(userId, deviceId)
    return if (key == null) {
        outdatedKeys.update { it + userId }
        waitForUpdateOutdatedKey(userId)
        this.getFromDevice(userId, deviceId)
    } else key
}

internal suspend inline fun <reified T : Key> KeyStore.getOrFetchKeysFromDevice(
    userId: UserId,
    deviceId: String?
): Set<T>? {
    val userKeys = getDeviceKeys(userId) ?: run {
        outdatedKeys.update { it + userId }
        waitForUpdateOutdatedKey(userId)
        getDeviceKeys(userId)
    }
    val keys = userKeys?.get(deviceId)?.value?.signed?.keys?.filterIsInstance<T>()
    return keys?.toSet()
}

internal suspend inline fun <reified T : Key> KeyStore.getDeviceKeyByValue(
    userId: UserId,
    keyValue: String
): T? {
    return getDeviceKeys(userId)?.map { deviceKeys ->
        deviceKeys.value.value.signed.keys.keys.filterIsInstance<T>().find { it.value == keyValue }
    }?.filterNotNull()?.firstOrNull() ?: run {
        outdatedKeys.update { it + userId }
        waitForUpdateOutdatedKey(userId)
        getDeviceKeys(userId)?.map { deviceKeys ->
            deviceKeys.value.value.signed.keys.keys.filterIsInstance<T>().find { it.value == keyValue }
        }?.filterNotNull()?.firstOrNull()
    }
}

// TODO test
internal suspend inline fun <reified T : Key> KeyStore.getAllKeysFromUser(
    userId: UserId,
    filterDeviceId: String? = null,
    filterUsage: CrossSigningKeysUsage? = null
): Set<T> {
    val deviceKeys = (getDeviceKeys(userId) ?: run {
        outdatedKeys.update { it + userId }
        waitForUpdateOutdatedKey(userId)
        getDeviceKeys(userId)
    })?.entries?.filter { if (filterDeviceId != null) it.key == filterDeviceId else true }
        ?.flatMap { it.value.value.signed.keys.filterIsInstance<T>() } ?: listOf()
    val crossSigningKeys =
        getCrossSigningKeys(userId)?.filter { if (filterUsage != null) it.value.signed.usage.contains(filterUsage) else true }
            ?.flatMap { it.value.signed.keys.filterIsInstance<T>() } ?: listOf()
    return (deviceKeys + crossSigningKeys).toSet()
}

internal suspend inline fun KeyStore.getDeviceKey(userId: UserId, deviceId: String): StoredDeviceKeys? {
    return this.getDeviceKeys(userId)?.get(deviceId)
}

internal suspend inline fun KeyStore.getDeviceKey(userId: UserId, deviceId: String, scope: CoroutineScope) =
    this.getDeviceKeys(userId, scope).map { it?.get(deviceId) }

internal suspend inline fun KeyStore.getCrossSigningKey(
    userId: UserId,
    usage: CrossSigningKeysUsage
): StoredCrossSigningKeys? {
    return this.getCrossSigningKeys(userId)?.firstOrNull { it.value.signed.usage.contains(usage) }
}

internal suspend inline fun KeyStore.getCrossSigningKey(
    userId: UserId,
    keyId: String
): StoredCrossSigningKeys? {
    return this.getCrossSigningKeys(userId)?.find { keys ->
        keys.value.signed.keys.keys.filterIsInstance<Key.Ed25519Key>().any { it.keyId == keyId }
    }
}

internal fun OlmStore.storeAccount(olmAccount: OlmAccount, pickleKey: String) {
    account.update { olmAccount.pickle(pickleKey) }
}

internal inline fun <reified T : Key> DeviceKeys.get(): T? {
    return keys.keys.filterIsInstance<T>().firstOrNull()
}

internal inline fun <reified T : Key> CrossSigningKeys.get(): T? {
    return keys.keys.filterIsInstance<T>().firstOrNull()
}

internal inline fun <reified T : Key> Keys.get(): T? {
    return keys.filterIsInstance<T>().firstOrNull()
}

internal inline fun <reified T : Key> SignedDeviceKeys.get(): T? {
    return signed.keys.keys.filterIsInstance<T>().firstOrNull()
}

internal suspend fun SecretType.getEncryptedSecret(globalAccountDataStore: GlobalAccountDataStore) = when (this) {
    SecretType.M_CROSS_SIGNING_USER_SIGNING -> globalAccountDataStore.get<UserSigningKeyEventContent>()
    SecretType.M_CROSS_SIGNING_SELF_SIGNING -> globalAccountDataStore.get<SelfSigningKeyEventContent>()
    SecretType.M_MEGOLM_BACKUP_V1 -> globalAccountDataStore.get<MegolmBackupV1EventContent>()
}