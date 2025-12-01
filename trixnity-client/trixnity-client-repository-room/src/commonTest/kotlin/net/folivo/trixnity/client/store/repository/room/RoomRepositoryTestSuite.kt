package net.folivo.trixnity.client.store.repository.room

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import net.folivo.trixnity.client.RepositoriesModule
import net.folivo.trixnity.client.store.repository.test.RepositoryTestSuite

class RoomRepositoryTestSuite : RepositoryTestSuite(
    repositoriesModule =
        RepositoriesModule.room(
            inMemoryDatabaseBuilder().apply { setDriver(BundledSQLiteDriver()) }
        )
)

expect fun inMemoryDatabaseBuilder(): RoomDatabase.Builder<TrixnityRoomDatabase>