package net.folivo.trixnity.client.store.repository.room

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import net.folivo.trixnity.client.store.repository.test.testVodozemacMigration
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import kotlin.test.Test

class MigrationTest : TrixnityBaseTest() {

    @Test
    fun vodozemac() = runTest {
        testVodozemacMigration {
            val builder = inMemoryDatabaseBuilder()
            builder.setDriver(BundledSQLiteDriver())
            createRoomRepositoriesModule(builder)
        }
    }
}