package net.folivo.trixnity.client.store.repository.room

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.kotest.core.spec.style.ShouldSpec
import net.folivo.trixnity.client.store.repository.test.repositoryTestSuite

class RepositoryTestSuite : ShouldSpec({
    repositoryTestSuite {
        val builder = inMemoryDatabaseBuilder()
        builder.setDriver(BundledSQLiteDriver())
        createRoomRepositoriesModule(builder)
    }
})

expect fun inMemoryDatabaseBuilder(): RoomDatabase.Builder<TrixnityRoomDatabase>