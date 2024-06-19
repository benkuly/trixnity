package net.folivo.trixnity.client.store.repository.room

import androidx.room.deferredTransaction
import androidx.room.useReaderConnection
import androidx.room.useWriterConnection
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager

internal class RoomRepositoryTransactionManager(
    private val db: TrixnityRoomDatabase,
) : RepositoryTransactionManager {
    override suspend fun <T> readTransaction(block: suspend () -> T): T =
        db.useReaderConnection { transactor ->
            transactor.deferredTransaction {
                block()
            }
        }

    override suspend fun writeTransaction(block: suspend () -> Unit) {
        db.useWriterConnection { transactor ->
            transactor.deferredTransaction {
                block()
            }
        }
    }
}
