package net.folivo.trixnity.client.store.repository.exposed

import com.benasher44.uuid.uuid4
import io.kotest.core.spec.style.ShouldSpec
import net.folivo.trixnity.client.store.repository.test.repositoryTestSuite
import org.jetbrains.exposed.sql.Database

class RepositoryTestSuite : ShouldSpec({
    repositoryTestSuite {
        createExposedRepositoriesModule(
            Database.connect("jdbc:h2:mem:${uuid4()};DB_CLOSE_DELAY=-1;", driver = "org.h2.Driver")
        )
    }
})