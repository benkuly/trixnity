package net.folivo.trixnity.examples.client.ping

import kotlinx.coroutines.Dispatchers
import net.folivo.trixnity.client.store.exposed.createExposedRepositoriesModule
import org.jetbrains.exposed.sql.Database
import org.koin.core.module.Module

actual suspend fun createRepositoriesModule(): Module = createExposedRepositoriesModule(
    database = Database.connect("jdbc:h2:./test;DB_CLOSE_DELAY=-1;"),
    transactionContext = Dispatchers.IO,
)