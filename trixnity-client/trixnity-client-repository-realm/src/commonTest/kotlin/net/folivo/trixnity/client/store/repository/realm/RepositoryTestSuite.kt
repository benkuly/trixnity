package net.folivo.trixnity.client.store.repository.realm

import io.kotest.core.spec.style.ShouldSpec
import net.folivo.trixnity.client.store.repository.test.repositoryTestSuite
import net.folivo.trixnity.utils.nextString
import kotlin.random.Random

class RepositoryTestSuite : ShouldSpec({
    repositoryTestSuite {
        createRealmRepositoriesModule {
            val realmDbPath = "build/test-db/${Random.nextString(22)}"
            directory(realmDbPath)
        }
    }
})