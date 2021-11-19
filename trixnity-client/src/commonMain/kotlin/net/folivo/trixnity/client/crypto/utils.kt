package net.folivo.trixnity.client.crypto

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import net.folivo.trixnity.client.store.DeviceKeysStore
import net.folivo.trixnity.client.store.OlmStore
import net.folivo.trixnity.client.store.StoredInboundMegolmSession
import net.folivo.trixnity.client.store.StoredOlmSession
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.crypto.DeviceKeys
import net.folivo.trixnity.core.model.crypto.Key
import net.folivo.trixnity.core.model.crypto.Keys
import net.folivo.trixnity.core.model.crypto.Signed
import net.folivo.trixnity.olm.OlmAccount
import net.folivo.trixnity.olm.OlmInboundGroupSession
import net.folivo.trixnity.olm.OlmSession
import net.folivo.trixnity.olm.freeAfter

internal suspend fun DeviceKeysStore.waitForUpdateOutdatedKey(vararg users: UserId) {
    outdatedKeys
        .first { if (users.isEmpty()) it.isEmpty() else !it.any { outdated -> users.contains(outdated) } }
}

// TODO test
internal suspend inline fun <reified T : Key> DeviceKeysStore.getKeyFromDevice(
    userId: UserId,
    deviceId: String?
): T {
    val key = get(userId)?.get(deviceId)?.get<T>()
    return if (key == null) {
        outdatedKeys.update { it + userId }
        waitForUpdateOutdatedKey(userId)
        get(userId)?.get(deviceId)?.get()
            ?: throw KeyException.KeyNotFoundException("no key ${T::class} found for device $deviceId from user $userId")
    } else key
}

// TODO test
internal suspend inline fun <reified T : Key> DeviceKeysStore.getKeysFromDevice(
    userId: UserId,
    deviceId: String?
): Set<T> {
    val userKeys = get(userId) ?: run {
        outdatedKeys.update { it + userId }
        waitForUpdateOutdatedKey(userId)
        get(userId) ?: throw KeyException.KeyNotFoundException("no keys found for user $userId")
    }
    val keys = userKeys[deviceId]?.keys?.filterIsInstance<T>()
    if (keys.isNullOrEmpty()) throw KeyException.KeyNotFoundException("no key ${T::class} found for user $userId")
    return keys.toSet()
}

// TODO test
internal suspend inline fun <reified T : Key> DeviceKeysStore.getKeysFromUser(
    userId: UserId
): Set<T> {
    val userKeys = get(userId) ?: run {
        outdatedKeys.update { it + userId }
        waitForUpdateOutdatedKey(userId)
        get(userId) ?: throw KeyException.KeyNotFoundException("no keys found for user $userId")
    }
    val keys = userKeys.values.flatMap { it.keys }.filterIsInstance<T>()
    if (keys.isEmpty()) throw KeyException.KeyNotFoundException("no key ${T::class} found for user $userId")
    return keys.toSet()
}

internal fun OlmStore.storeAccount(olmAccount: OlmAccount, pickleKey: String) {
    account.update { olmAccount.pickle(pickleKey) }
}

// TODO test
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

// TODO test
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