package net.folivo.trixnity.client.store.repository.room

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.kotest.core.spec.style.ShouldSpec
import net.folivo.trixnity.client.store.repository.test.repositoryTestSuite

class RepositoryTestSuite : ShouldSpec({
    repositoryTestSuite(disabledSimultaneousReadWriteTests = true) {
        val builder = Room.inMemoryDatabaseBuilder<TrixnityRoomDatabase>()
        builder.setDriver(BundledSQLiteDriver())
        createRoomRepositoriesModule(builder)
    }
})