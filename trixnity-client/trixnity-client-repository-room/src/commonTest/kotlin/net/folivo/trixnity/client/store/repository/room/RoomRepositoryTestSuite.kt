package net.folivo.trixnity.client.store.repository.room

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import net.folivo.trixnity.client.store.repository.test.RepositoryTestSuite

class RoomRepositoryTestSuite : RepositoryTestSuite(
    repositoriesModuleBuilder = {
        val builder = inMemoryDatabaseBuilder()
        builder.setDriver(BundledSQLiteDriver())
        createRoomRepositoriesModule(builder)
    }
)

expect fun inMemoryDatabaseBuilder(): RoomDatabase.Builder<TrixnityRoomDatabase>