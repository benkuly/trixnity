package net.folivo.trixnity.client.crypto

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.crypto.*
import net.folivo.trixnity.olm.OlmAccount
import net.folivo.trixnity.olm.OlmInboundGroupSession
import net.folivo.trixnity.olm.OlmSession
import net.folivo.trixnity.olm.freeAfter

internal suspend fun KeysStore.waitForUpdateOutdatedKey(vararg users: UserId) {
    withTimeoutOrNull(5_000) {
        outdatedKeys.first { if (users.isEmpty()) it.isEmpty() else it.none { outdated -> users.contains(outdated) } }
    }
}


internal suspend inline fun <reified T : Key> KeysStore.getFromDevice(
    userId: UserId,
    deviceId: String?
): T? {
    return getDeviceKeys(userId)?.get(deviceId)?.value?.get()
}

internal suspend inline fun <reified T : Key> KeysStore.getOrFetchKeyFromDevice(
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

internal suspend inline fun <reified T : Key> KeysStore.getOrFetchKeysFromDevice(
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

internal suspend inline fun <reified T : Key> KeysStore.getDeviceKeyByValue(
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
internal suspend inline fun <reified T : Key> KeysStore.getKeysFromUser(
    userId: UserId
): Set<T>? {
    val userKeys = getDeviceKeys(userId) ?: run {
        outdatedKeys.update { it + userId }
        waitForUpdateOutdatedKey(userId)
        getDeviceKeys(userId)
    }
    val keys = userKeys?.values?.flatMap { it.value.signed.keys }?.filterIsInstance<T>()
    return keys?.toSet()
}

internal suspend inline fun KeysStore.getDeviceKey(userId: UserId, deviceId: String): StoredDeviceKeys? {
    return this.getDeviceKeys(userId)?.get(deviceId)
}

internal suspend inline fun KeysStore.getDeviceKey(userId: UserId, deviceId: String, scope: CoroutineScope) =
    this.getDeviceKeys(userId, scope).map { it?.get(deviceId) }.stateIn(scope)

internal suspend inline fun KeysStore.getCrossSigningKey(
    userId: UserId,
    usage: CrossSigningKeysUsage
): StoredCrossSigningKey? {
    return this.getCrossSigningKeys(userId)?.firstOrNull { it.value.signed.usage.contains(usage) }
}

internal suspend inline fun KeysStore.getCrossSigningKey(
    userId: UserId,
    usage: CrossSigningKeysUsage,
    scope: CoroutineScope
): StateFlow<StoredCrossSigningKey?> {
    return this.getCrossSigningKeys(userId, scope)
        .map { keys -> keys?.firstOrNull { it.value.signed.usage.contains(usage) } }.stateIn(scope)
}

internal suspend inline fun KeysStore.getCrossSigningKey(userId: UserId, keyId: String): StoredCrossSigningKey? {
    return this.getCrossSigningKeys(userId)?.find { keys -> keys.value.signed.keys.keys.any { it.keyId == keyId } }
}

internal fun OlmStore.storeAccount(olmAccount: OlmAccount, pickleKey: String) {
    account.update { olmAccount.pickle(pickleKey) }
}

internal suspend fun OlmStore.storeInboundMegolmSession(
    roomId: RoomId,
    sessionId: String,
    senderKey: Key.Curve25519Key,
    sessionKey: String,
    pickleKey: String
) {
    updateInboundMegolmSession(senderKey, sessionId, roomId) { oldStoredSession ->
        oldStoredSession
            ?: freeAfter(OlmInboundGroupSession.create(sessionKey)) { session ->
                StoredInboundMegolmSession(senderKey, sessionId, roomId, session.pickle(pickleKey))
            }
    }
}

internal suspend fun OlmStore.storeOlmSession(
    session: OlmSession,
    identityKey: Key.Curve25519Key,
    pickleKey: String
) {
    updateOlmSessions(identityKey) { oldStoredSessions ->
        val newSessions =
            (oldStoredSessions?.filterNot { it.sessionId == session.sessionId }?.toSet() ?: setOf()) +
                    StoredOlmSession(
                        sessionId = session.sessionId,
                        senderKey = identityKey,
                        pickle = session.pickle(pickleKey),
                        lastUsedAt = Clock.System.now()
                    )
        if (newSessions.size > 9) {
            newSessions.sortedBy { it.lastUsedAt }.drop(1).toSet()
        } else newSessions
    }
}

internal inline fun <reified T : Key> DeviceKeys.get(): T? {
    return keys.keys.filterIsInstance<T>().firstOrNull()
}

internal inline fun <reified T : Key> Keys.get(): T? {
    return keys.filterIsInstance<T>().firstOrNull()
}

internal inline fun <reified T : Key> Signed<DeviceKeys, UserId>.get(): T? {
    return signed.keys.keys.filterIsInstance<T>().firstOrNull()
}