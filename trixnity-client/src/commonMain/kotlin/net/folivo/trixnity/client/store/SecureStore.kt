package net.folivo.trixnity.client.store

interface SecureStore {
    // it is important, that this key is stored in secure location! Changing this value is not that easy, because
    // we need to encrypt every pickled object with the new key.
    val olmPickleKey: String
}