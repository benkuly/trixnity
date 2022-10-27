package net.folivo.trixnity.crypto.sign

interface SignServiceStore {
    val olmAccount: String
    val olmPickleKey: String
}