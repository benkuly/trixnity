package net.folivo.trixnity.client.store.repository.sqldelight.testutils

import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.logs.LogSqliteDriver
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import net.folivo.trixnity.client.store.sqldelight.db.Database

actual fun createDriver(): SqlDriver {
    return LogSqliteDriver(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)) { /*println(it)*/ }
}

actual fun createDriverWithSchema(): SqlDriver {
    return createDriver().also { Database.Schema.create(it) }
}