package net.folivo.trixnity.client.store.repository.exposed

import net.folivo.trixnity.client.store.repository.MigrationRepository
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert

internal object ExposedMigration : Table("migration") {
    val name = text("name")
    val metadata = text("metadata")

    override val primaryKey: PrimaryKey = PrimaryKey(name)
}

internal class ExposedMigrationRepository : MigrationRepository {
    override suspend fun get(key: String): String? = withExposedRead {
        ExposedMigration
            .selectAll()
            .where { ExposedMigration.name eq key }
            .firstOrNull()
            ?.get(ExposedMigration.metadata)
    }

    override suspend fun save(key: String, value: String): Unit = withExposedWrite {
        ExposedMigration.upsert {
            it[name] = key
            it[metadata] = value
        }
    }

    override suspend fun delete(key: String): Unit = withExposedWrite {
        ExposedMigration.deleteWhere { ExposedMigration.name eq key }
    }

    override suspend fun deleteAll(): Unit = withExposedWrite {
        ExposedMigration.deleteAll()
    }
}

