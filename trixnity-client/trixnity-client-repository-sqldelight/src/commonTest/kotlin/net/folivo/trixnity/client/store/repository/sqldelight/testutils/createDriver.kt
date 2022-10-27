package net.folivo.trixnity.client.store.repository.sqldelight.testutils

import com.squareup.sqldelight.db.SqlDriver

expect fun createDriver(): SqlDriver

expect fun createDriverWithSchema(): SqlDriver