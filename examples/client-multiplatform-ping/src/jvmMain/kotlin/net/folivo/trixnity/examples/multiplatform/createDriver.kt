package net.folivo.trixnity.examples.multiplatform

import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

actual fun createDriver(): SqlDriver {
    return JdbcSqliteDriver("jdbc:sqlite:test.db")
}

actual fun databaseCoroutineContext(): CoroutineContext {
    return Dispatchers.IO
}