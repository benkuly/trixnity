package de.connect2x.trixnity.crypto.sign

interface SignServiceStore {
    suspend fun getOlmAccount(): String
    suspend fun getOlmPickleKey(): String?
}