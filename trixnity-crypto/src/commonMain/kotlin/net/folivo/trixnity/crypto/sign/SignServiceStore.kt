package net.folivo.trixnity.crypto.sign

interface SignServiceStore {
    suspend fun getOlmAccount(): String
    suspend fun getOlmPickleKey(): String
}