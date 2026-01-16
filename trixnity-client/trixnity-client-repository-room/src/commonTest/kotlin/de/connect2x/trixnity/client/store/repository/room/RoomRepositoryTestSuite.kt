package de.connect2x.trixnity.client.store.repository.room

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.connect2x.trixnity.client.RepositoriesModule
import de.connect2x.trixnity.client.store.repository.test.RepositoryTestSuite

class RoomRepositoryTestSuite : RepositoryTestSuite(
    repositoriesModule =
        RepositoriesModule.room(
            randomDatabaseBuilder().apply { setDriver(BundledSQLiteDriver()) }
        )
)

expect fun randomDatabaseBuilder(): RoomDatabase.Builder<TrixnityRoomDatabase>