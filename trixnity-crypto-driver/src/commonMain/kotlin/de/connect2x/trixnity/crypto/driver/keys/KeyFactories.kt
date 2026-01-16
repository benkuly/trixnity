package de.connect2x.trixnity.crypto.driver.keys

interface KeyFactories {
    val ed25519PublicKey: Ed25519PublicKeyFactory
    val ed25519SecretKey: Ed25519SecretKeyFactory

    val curve25519PublicKey: Curve25519PublicKeyFactory
    val curve25519SecretKey: Curve25519SecretKeyFactory

    val ed25519Signature: Ed25519SignatureFactory

    val pickleKey: PickleKeyFactory
}