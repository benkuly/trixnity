package de.connect2x.trixnity.client.store.repository.indexeddb

import de.connect2x.trixnity.client.RepositoriesModule
import de.connect2x.trixnity.client.store.repository.RepositoryTransactionManager
import de.connect2x.trixnity.client.store.repository.test.RepositoryTestSuite
import de.connect2x.trixnity.utils.nextString
import org.koin.dsl.module
import kotlin.random.Random

class IndexedDBRepositoryTestSuite : RepositoryTestSuite(
    // remove customRepositoryTransactionManager as soon as a solution is found for async work within a transaction
    customRepositoryTransactionManager = {
        IndexedDBRepositoryTransactionManager(createDatabase(Random.nextString(22)), allStoreNames)
    },
    repositoriesModule = RepositoriesModule {
        val delegate = RepositoriesModule.indexedDB(Random.nextString(22)).create()
        module {
            includes(module {
                single<RepositoryTransactionManager> {
                    IndexedDBRepositoryTransactionManager(get(), allStoreNames, testMode = true)
                }
            }, delegate)
        }
    }
)

