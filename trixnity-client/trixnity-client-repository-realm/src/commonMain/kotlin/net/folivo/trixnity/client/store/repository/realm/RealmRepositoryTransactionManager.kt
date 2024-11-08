package net.folivo.trixnity.client.store.repository.realm

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.TypedRealm
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import kotlin.coroutines.CoroutineContext

class RealmReadTransaction(
    val realm: TypedRealm,
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

suspend fun <T> withRealmRead(block: TypedRealm.() -> T) = coroutineScope {
    block(checkNotNull(coroutineContext[RealmReadTransaction]?.realm) { "read transaction is missing" })
}

suspend fun <T> withRealmWrite(block: MutableRealm.() -> T) = coroutineScope {
    block(checkNotNull(coroutineContext[RealmWriteTransaction]?.realm) { "write transaction is missing" })
}

class RealmRepositoryTransactionManager(private val realm: Realm) : RepositoryTransactionManager {
    override suspend fun writeTransaction(block: suspend () -> Unit) = coroutineScope {
        val existingRealmWriteTransaction = coroutineContext[RealmWriteTransaction]
        val existingRealmReadTransaction = coroutineContext[RealmReadTransaction]
        if (existingRealmWriteTransaction != null && existingRealmReadTransaction != null) block()// just reuse existing transaction (nested)
        else realm.write {
            // TODO runBlocking is bad. See also for a future solution: https://github.com/realm/realm-kotlin/issues/705
            //      runBlocking also means, that all coroutines within the transaction are running on one(!) thread.
            //      Realm needs it's write modifications to be run on a single thread bound to a transaction. Therefore [withRealmRead] and
            //      [withRealmWrite] always need to be run on the realm write dispatcher (single threaded).
            //      It could be solved like in ExposedRepositoryTransactionManager, but this would need work in realm-kotlin directly.
            //      Right now it is not possible to solve it like in ExposedRepositoryTransactionManager. runBlocking
            //      blocks the realm write dispatcher and because of this there is no chance to switch back to this dispatcher once we have left it.
            // we can do both within write transactions: read and write
            try {
                runBlocking(RealmWriteTransaction(this) + RealmReadTransaction(this)) {
                    block()
                }
            } catch (exception: Exception) {
                cancelWrite()
                throw exception
            }
        }
    }

    override suspend fun <T> readTransaction(block: suspend () -> T): T = coroutineScope {
        val existingRealmReadTransaction = coroutineContext[RealmReadTransaction]
        if (existingRealmReadTransaction != null) block()// just reuse existing transaction (nested)
        else withContext(RealmReadTransaction(realm)) { block() }
    }
}