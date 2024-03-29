package net.folivo.trixnity.client.store.repository.room

import androidx.room.withTransaction
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager

internal class RoomRepositoryTransactionManager(
    private val db: TrixnityRoomDatabase,
) : RepositoryTransactionManager {
    override suspend fun <T> readTransaction(block: suspend () -> T): T =
        db.withTransaction {
            block()
        }

    override suspend fun writeTransaction(block: suspend () -> Unit) {
        db.withTransaction {
            block()
        }
    }
}
