package net.folivo.trixnity.client.crypto

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.StoredOlmInboundMegolmSession
import net.folivo.trixnity.client.store.StoredOlmSession
import net.folivo.trixnity.core.model.MatrixId
import net.folivo.trixnity.core.model.crypto.DeviceKeys
import net.folivo.trixnity.core.model.crypto.Key
import net.folivo.trixnity.core.model.crypto.Keys
import net.folivo.trixnity.core.model.crypto.Signed
import net.folivo.trixnity.olm.OlmAccount
import net.folivo.trixnity.olm.OlmInboundGroupSession
import net.folivo.trixnity.olm.OlmSession
import net.folivo.trixnity.olm.freeAfter

// FIXME test
internal suspend inline fun <reified T : Key> Store.DeviceKeysStores.getKeyFromDevice(
    userId: MatrixId.UserId,
    deviceId: String?
): T {
    val key = byUserId(userId).value?.get(deviceId)?.get<T>()
    return if (key == null) {
        outdatedKeys.update { it + userId }
        waitForUpdateOutdatedKey(userId)
        byUserId(userId).value?.get(deviceId)?.get()
            ?: throw KeyException.KeyNotFoundException("no key ${T::class} found for device $deviceId from user $userId")
    } else key
}

// FIXME test
internal suspend inline fun <reified T : Key> Store.DeviceKeysStores.getKeysFromUser(
    userId: MatrixId.UserId
): Set<T> {
    val userKeys = byUserId(userId).value ?: run {
        outdatedKeys.update { it + userId }
        waitForUpdateOutdatedKey(userId)
        byUserId(userId).value
            ?: throw KeyException.KeyNotFoundException("no keys found for user $userId")
    }
    val keys = userKeys.values.flatMap { it.keys }.filterIsInstance<T>()
    if (keys.isEmpty()) throw KeyException.KeyNotFoundException("no key ${T::class} found for user $userId")
    return keys.toSet()
}

internal fun Store.OlmStore.storeAccount(olmAccount: OlmAccount) {
    account.update { olmAccount.pickle(pickleKey) }
}

// FIXME test
internal suspend fun Store.OlmStore.storeInboundMegolmSession(
    roomId: MatrixId.RoomId,
    senderKey: Key.Curve25519Key,
    sessionId: String,
    sessionKey: String
) {
    inboundMegolmSession(roomId, sessionId, senderKey).update { oldStoredSession ->
        oldStoredSession
            ?: freeAfter(OlmInboundGroupSession.create(sessionKey)) { session ->
                StoredOlmInboundMegolmSession(sessionId, senderKey, roomId, session.pickle(pickleKey))
            }
    }
}

// FIXME test
internal suspend fun Store.OlmStore.storeOlmSession(session: OlmSession, identityKey: Key.Curve25519Key) {
    olmSessions(identityKey).update { oldStoredSessions ->
        val newSessions = oldStoredSessions.filterNot { it.sessionId == session.sessionId }.toSet() +
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

internal suspend fun Store.DeviceKeysStores.waitForUpdateOutdatedKey(vararg users: MatrixId.UserId) {
    outdatedKeys
        .first { if (users.isEmpty()) it.isEmpty() else !it.any { outdated -> users.contains(outdated) } }
}

internal inline fun <reified T : Key> DeviceKeys.get(): T? {
    return keys.keys.filterIsInstance<T>().firstOrNull()
}

internal inline fun <reified T : Key> Keys.get(): T? {
    return keys.filterIsInstance<T>().firstOrNull()
}

internal inline fun <reified T : Key> Signed<DeviceKeys, MatrixId.UserId>.get(): T? {
    return signed.keys.keys.filterIsInstance<T>().firstOrNull()
}