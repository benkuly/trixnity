package net.folivo.trixnity.client.store.repository.realm

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.TypedRealm
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import kotlin.coroutines.CoroutineContext

internal class RealmReadTransaction(
    val realm: TypedRealm,
    val coroutineContext: CoroutineContext? = null,
) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> = Key

    companion object Key : CoroutineContext.Key<RealmReadTransaction>
}

internal class RealmWriteTransaction(
    val realm: MutableRealm,
    val coroutineContext: CoroutineContext,
) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> = Key

    companion object Key : CoroutineContext.Key<RealmWriteTransaction>
}

internal suspend fun <T> withRealmRead(block: TypedRealm.() -> T) = coroutineScope {
    val readTransaction = checkNotNull(coroutineContext[RealmReadTransaction]) { "read transaction is missing" }
    val coroutineContext = readTransaction.coroutineContext
    if (coroutineContext != null)
        withContext(coroutineContext) {
            block(readTransaction.realm)
        }
    else block(readTransaction.realm)
}

internal suspend fun withRealmWrite(block: MutableRealm.() -> Unit): Unit = coroutineScope {
    val readTransaction = checkNotNull(coroutineContext[RealmWriteTransaction]) { "write transaction is missing" }
    withContext(readTransaction.coroutineContext) {
        block(readTransaction.realm)
    }
}

private sealed interface TransactionResult {
    object Success : TransactionResult
    class Failure(val exception: Exception) : TransactionResult
}

class RealmRepositoryTransactionManager(private val realm: Realm) : RepositoryTransactionManager {
    override suspend fun writeTransaction(block: suspend () -> Unit): Unit = coroutineScope {
        val existingRealmWriteTransaction = coroutineContext[RealmWriteTransaction]
        val existingRealmReadTransaction = coroutineContext[RealmReadTransaction]
        if (existingRealmWriteTransaction != null && existingRealmReadTransaction != null) block()// just re-use existing transaction (nested)
        else coroutineScope {
            val writeTransaction = MutableStateFlow<RealmWriteTransaction?>(null)
            val transactionResult = MutableStateFlow<TransactionResult?>(null)
            launch {
                realm.write {
                    val mutableRealm = this
                    // TODO runBlocking is bad. See also for a future solution: https://github.com/realm/realm-kotlin/issues/705
                    //      runBlocking also means, that all coroutines within the transaction are running on one(!) thread.
                    //      This is why a channel is used, so sync processing can be done multithreaded.
                    //      Realm needs it's write modifications to be run on a single thread bound to a transaction. Therefore [withRealmRead] and
                    //      [withRealmWrite] always need to be run on the realm write dispatcher (single threaded).
                    //      It could be solved like in ExposedRepositoryTransactionManager, but this would need work in realm-kotlin directly.
                    //      Right now it is not possible to solve it like in ExposedRepositoryTransactionManager. runBlocking
                    //      blocks the realm write dispatcher and because of this there is no chance to switch back to this dispatcher once we have left it.
                    try {
                        runBlocking {
                            writeTransaction.value = RealmWriteTransaction(mutableRealm, currentCoroutineContext())
                            when (val r = transactionResult.filterNotNull().first()) {
                                is TransactionResult.Success -> {}
                                is TransactionResult.Failure -> throw r.exception
                            }
                        }
                    } catch (exception: Exception) {
                        cancelWrite()
                        throw exception
                    }
                }
            }
            try {
                // we can do both within write transactions: read and write
                val currentWriteTransaction = writeTransaction.filterNotNull().first()
                withContext(
                    currentWriteTransaction +
                            RealmReadTransaction(
                                currentWriteTransaction.realm,
                                currentWriteTransaction.coroutineContext
                            )
                ) {
                    block()
                }
                transactionResult.value = TransactionResult.Success
            } catch (exception: Exception) {
                transactionResult.value = TransactionResult.Failure(exception)
                throw exception
            }
        }
    }

    override suspend fun <T> readTransaction(block: suspend () -> T): T = coroutineScope {
        val existingRealmReadTransaction = coroutineContext[RealmReadTransaction]
        if (existingRealmReadTransaction != null) block()// just re-use existing transaction (nested)
        else withContext(RealmReadTransaction(realm)) { block() }
    }
}