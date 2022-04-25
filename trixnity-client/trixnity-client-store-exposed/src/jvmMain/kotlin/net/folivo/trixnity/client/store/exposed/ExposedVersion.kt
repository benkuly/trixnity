package net.folivo.trixnity.client.store.exposed

import org.jetbrains.exposed.dao.id.LongIdTable

internal object ExposedVersion : LongIdTable("db_version") {
    val version = long("version")
}