package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.StoredSecret
import net.folivo.trixnity.crypto.SecretType

interface SecretsRepository : MinimalStoreRepository<Long, Map<SecretType, StoredSecret>>