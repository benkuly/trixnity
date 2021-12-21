package net.folivo.trixnity.examples.multiplatform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.folivo.trixnity.client.store.StoreFactory
import net.folivo.trixnity.client.store.exposed.ExposedStoreFactory
import org.h2.jdbcx.JdbcDataSource
import org.kodein.log.LoggerFactory

actual fun createStoreFactory(): StoreFactory {
    return ExposedStoreFactory(
        dataSource = JdbcDataSource().apply {
            setURL("jdbc:h2:./test;DB_CLOSE_DELAY=-1;")
        },
        transactionDispatcher = Dispatchers.IO,
        scope = CoroutineScope(Dispatchers.Default),
        loggerFactory = LoggerFactory.default
    )
}