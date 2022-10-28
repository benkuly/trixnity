package net.folivo.trixnity.client.store.repository.realm

import io.realm.kotlin.Realm
import kotlinx.coroutines.runBlocking

suspend fun <T> writeTransaction(realm: Realm, block: suspend () -> T): T =
    realm.write {
        // TODO runBlocking is bad. See also for a future solution: https://github.com/realm/realm-kotlin/issues/705
        // we can do both within write transactions: read and write
        runBlocking(RealmWriteTransaction(this) + RealmReadTransaction(this)) {
            block()
        }
    }