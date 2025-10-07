package net.folivo.trixnity.client.store.repository.exposed

import net.folivo.trixnity.client.store.repository.test.testVodozemacMigration
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.utils.nextString
import org.jetbrains.exposed.sql.Database
import kotlin.random.Random
import kotlin.test.Test

class MigrationTest : TrixnityBaseTest() {

    @Test
    fun vodozemac() = runTest {
        testVodozemacMigration {
            createExposedRepositoriesModule(
                Database.connect("jdbc:h2:mem:${Random.nextString(22)};DB_CLOSE_DELAY=-1;")
            )
        }
    }
}