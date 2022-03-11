package net.folivo.trixnity.client.store.exposed

object ExposedMigrationCheckException :
    RuntimeException("liquibase migration result did not match the schema of exposed")