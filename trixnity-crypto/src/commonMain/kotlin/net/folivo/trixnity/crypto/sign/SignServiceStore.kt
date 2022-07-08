package net.folivo.trixnity.crypto.sign

interface SignServiceStore {
    suspend fun getOlmAccount(): String?
}