package net.folivo.trixnity.client.store.repository.realm

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import kotlin.coroutines.CoroutineContext

class RealmReadTransaction(
    val realm: Realm,
) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> = Key

    companion object Key : CoroutineContext.Key<RealmReadTransaction>
}

class RealmWriteTransaction(
    val realm: MutableRealm,
) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> = Key

    companion object Key : CoroutineContext.Key<RealmWriteTransaction>
}

suspend fun <T> withRealmRead(block: Realm.() -> T) = coroutineScope {
    block(requireNotNull(coroutineContext[RealmReadTransaction]?.realm))
}

suspend fun <T> withRealmWrite(block: MutableRealm.() -> T) = coroutineScope {
    block(requireNotNull(coroutineContext[RealmWriteTransaction]?.realm))
}

class RealmRepositoryTransactionManager(
    private val realm: Realm,
) : RepositoryTransactionManager {
    override suspend fun <T> writeTransaction(block: suspend () -> T): T =
        realm.write {
            // TODO runBlocking is bad. See also for a future solution: https://github.com/realm/realm-kotlin/issues/705
            runBlocking(RealmWriteTransaction(this)) {
                block()
            }
        }

    override suspend fun <T> readTransaction(block: suspend () -> T): T =
        withContext(RealmReadTransaction(realm)) {
            block()
        }
}