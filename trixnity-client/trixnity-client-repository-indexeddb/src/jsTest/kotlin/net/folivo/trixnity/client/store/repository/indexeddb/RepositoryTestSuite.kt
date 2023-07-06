package net.folivo.trixnity.client.store.repository.indexeddb

import com.benasher44.uuid.uuid4
import io.kotest.core.spec.style.ShouldSpec
import net.folivo.trixnity.client.store.repository.test.repositoryTestSuite

class RepositoryTestSuite : ShouldSpec({
    // remove disabledRollbackTest when fixed: https://github.com/JuulLabs/indexeddb/issues/115
    repositoryTestSuite(disabledRollbackTest = true) {
        createIndexedDBRepositoriesModule(uuid4().toString())
    }
})

