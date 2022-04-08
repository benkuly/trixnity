package net.folivo.trixnity.client.store.sqldelight.testutils

import com.squareup.sqldelight.db.SqlDriver

expect fun createDriver(): SqlDriver

expect fun createDriverWithSchema(): SqlDriver