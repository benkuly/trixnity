package de.connect2x.trixnity.client.store.repository

// Mapping of the Migration name to arbitrary encoded Metadata to be used by RepositoryMigrations

interface MigrationRepository : MinimalRepository<String, String> {
    override fun serializeKey(key: String): String = key
}