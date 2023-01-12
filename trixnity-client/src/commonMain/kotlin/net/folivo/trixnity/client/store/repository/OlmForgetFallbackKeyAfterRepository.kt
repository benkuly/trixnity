package net.folivo.trixnity.client.store.repository

import kotlinx.datetime.Instant

interface OlmForgetFallbackKeyAfterRepository : MinimalStoreRepository<Long, Instant>