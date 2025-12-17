package net.folivo.trixnity.client.store.repository.room

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import net.folivo.trixnity.client.RepositoriesModule
import net.folivo.trixnity.client.store.repository.test.RepositoryTestSuite

class RoomRepositoryTestSuite : RepositoryTestSuite(
    repositoriesModule =
        RepositoriesModule.room(
            randomDatabaseBuilder().apply { setDriver(BundledSQLiteDriver()) }
        )
)

expect fun randomDatabaseBuilder(): RoomDatabase.Builder<TrixnityRoomDatabase>