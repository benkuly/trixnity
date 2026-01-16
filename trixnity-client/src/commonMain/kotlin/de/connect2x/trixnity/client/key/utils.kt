package de.connect2x.trixnity.client.key

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import de.connect2x.trixnity.client.store.GlobalAccountDataStore
import de.connect2x.trixnity.client.store.KeyStore
import de.connect2x.trixnity.client.store.StoredCrossSigningKeys
import de.connect2x.trixnity.client.store.get
import de.connect2x.trixnity.core.MSC3814
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.DehydratedDeviceEventContent
import de.connect2x.trixnity.core.model.events.m.MegolmBackupV1EventContent
import de.connect2x.trixnity.core.model.events.m.crosssigning.MasterKeyEventContent
import de.connect2x.trixnity.core.model.events.m.crosssigning.SelfSigningKeyEventContent
import de.connect2x.trixnity.core.model.events.m.crosssigning.UserSigningKeyEventContent
import de.connect2x.trixnity.core.model.keys.CrossSigningKeysUsage
import de.connect2x.trixnity.core.model.keys.Key
import de.connect2x.trixnity.crypto.SecretType

internal suspend inline fun <reified T : Key> KeyStore.getAllKeysFromUser(
    userId: UserId,
    filterDeviceId: String? = null,
    filterUsage: CrossSigningKeysUsage? = null
): Set<T> {
    val deviceKeys = getDeviceKeys(userId).first()
        ?.entries?.filter { if (filterDeviceId != null) it.key == filterDeviceId else true }
        ?.flatMap { it.value.value.signed.keys.filterIsInstance<T>() } ?: listOf()
    val crossSigningKeys =
        getCrossSigningKeys(userId).first()
            ?.filter { if (filterUsage != null) it.value.signed.usage.contains(filterUsage) else true }
            ?.flatMap { it.value.signed.keys.filterIsInstance<T>() } ?: listOf()
    return (deviceKeys + crossSigningKeys).toSet()
}


internal fun KeyStore.getDeviceKey(userId: UserId, deviceId: String) =
    this.getDeviceKeys(userId).map { it?.get(deviceId) }

internal suspend inline fun KeyStore.getCrossSigningKey(
    userId: UserId,
    usage: CrossSigningKeysUsage,
): StoredCrossSigningKeys? {
    return this.getCrossSigningKeys(userId).first()
        ?.firstOrNull { it.value.signed.usage.contains(usage) }
}

internal suspend inline fun KeyStore.getCrossSigningKey(
    userId: UserId,
    keyId: String,
): StoredCrossSigningKeys? {
    return this.getCrossSigningKeys(userId).first()?.find { keys ->
        keys.value.signed.keys.keys.filterIsInstance<Key.Ed25519Key>().any { it.id == keyId }
    }
}

internal fun SecretType.getEncryptedSecret(globalAccountDataStore: GlobalAccountDataStore) = when (this) {
    SecretType.M_CROSS_SIGNING_MASTER -> globalAccountDataStore.get<MasterKeyEventContent>()
    SecretType.M_CROSS_SIGNING_USER_SIGNING -> globalAccountDataStore.get<UserSigningKeyEventContent>()
    SecretType.M_CROSS_SIGNING_SELF_SIGNING -> globalAccountDataStore.get<SelfSigningKeyEventContent>()
    SecretType.M_MEGOLM_BACKUP_V1 -> globalAccountDataStore.get<MegolmBackupV1EventContent>()
    @OptIn(MSC3814::class)
    SecretType.M_DEHYDRATED_DEVICE -> @OptIn(MSC3814::class) globalAccountDataStore.get<DehydratedDeviceEventContent>()
}