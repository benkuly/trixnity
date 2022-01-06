package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.AllowedSecretType
import net.folivo.trixnity.client.store.StoredSecret

typealias SecretsRepository = MinimalStoreRepository<Long, Map<AllowedSecretType, StoredSecret>>