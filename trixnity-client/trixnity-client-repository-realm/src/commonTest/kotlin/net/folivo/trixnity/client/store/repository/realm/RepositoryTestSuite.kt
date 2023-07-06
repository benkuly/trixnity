package net.folivo.trixnity.client.store.repository.realm

import com.benasher44.uuid.uuid4
import io.kotest.core.spec.style.ShouldSpec
import net.folivo.trixnity.client.store.repository.test.repositoryTestSuite

class RepositoryTestSuite : ShouldSpec({
    repositoryTestSuite {
        createRealmRepositoriesModule {
            val realmDbPath = "build/test-db/${uuid4()}"
            directory(realmDbPath)
        }
    }
})