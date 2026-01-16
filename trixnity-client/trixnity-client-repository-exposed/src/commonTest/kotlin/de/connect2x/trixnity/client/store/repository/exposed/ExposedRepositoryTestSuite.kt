package de.connect2x.trixnity.client.store.repository.exposed

import de.connect2x.trixnity.client.RepositoriesModule
import de.connect2x.trixnity.client.store.repository.test.RepositoryTestSuite
import de.connect2x.trixnity.utils.nextString
import org.jetbrains.exposed.sql.Database
import kotlin.random.Random

class ExposedRepositoryTestSuite : RepositoryTestSuite(
    repositoriesModule =
        RepositoriesModule.exposed(
            Database.connect("jdbc:h2:mem:${Random.nextString(22)};DB_CLOSE_DELAY=-1;", driver = "org.h2.Driver")
        )
)