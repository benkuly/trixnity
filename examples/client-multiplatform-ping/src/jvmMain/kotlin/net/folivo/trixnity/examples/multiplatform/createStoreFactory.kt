package net.folivo.trixnity.examples.multiplatform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.folivo.trixnity.client.store.StoreFactory
import net.folivo.trixnity.client.store.exposed.ExposedStoreFactory
import org.jetbrains.exposed.sql.Database

actual fun createStoreFactory(): StoreFactory {
    return ExposedStoreFactory(
        database = Database.connect("jdbc:h2:./test;DB_CLOSE_DELAY=-1;", driver = "org.h2.Driver"),
        transactionDispatcher = Dispatchers.IO,
        scope = CoroutineScope(Dispatchers.Default)
    )
}