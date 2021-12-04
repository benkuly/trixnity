package net.folivo.trixnity.examples.multiplatform

import com.squareup.sqldelight.db.SqlDriver
import kotlin.coroutines.CoroutineContext

expect fun createDriver(): SqlDriver

expect fun databaseCoroutineContext(): CoroutineContext

expect fun blockingTransactionCoroutineContext(): CoroutineContext