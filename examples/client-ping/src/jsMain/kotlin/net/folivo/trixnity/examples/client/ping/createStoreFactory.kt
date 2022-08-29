package net.folivo.trixnity.examples.client.ping

import net.folivo.trixnity.client.store.repository.createInMemoryRepositoriesModule

actual suspend fun createRepositoriesModule() = createInMemoryRepositoriesModule()