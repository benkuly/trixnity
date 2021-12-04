package net.folivo.trixnity.examples.multiplatform

import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlin.coroutines.CoroutineContext

actual fun createDriver(): SqlDriver {
    return JdbcSqliteDriver("jdbc:sqlite:test.db")
}

actual fun databaseCoroutineContext(): CoroutineContext {
    return Dispatchers.IO
}

@OptIn(ExperimentalCoroutinesApi::class)
actual fun blockingTransactionCoroutineContext(): CoroutineContext {
    return newSingleThreadContext("transaction")
}