package net.folivo.trixnity.client.store.repository.exposed

import io.kotest.core.spec.style.ShouldSpec
import net.folivo.trixnity.client.store.repository.test.repositoryTestSuite
import net.folivo.trixnity.utils.nextString
import org.jetbrains.exposed.sql.Database
import kotlin.random.Random

class RepositoryTestSuite : ShouldSpec({
    repositoryTestSuite {
        createExposedRepositoriesModule(
            Database.connect("jdbc:h2:mem:${Random.nextString(22)};DB_CLOSE_DELAY=-1;", driver = "org.h2.Driver")
        )
    }
})