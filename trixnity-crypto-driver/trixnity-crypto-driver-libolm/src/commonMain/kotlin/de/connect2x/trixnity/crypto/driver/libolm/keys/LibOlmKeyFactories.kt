package de.connect2x.trixnity.crypto.driver.libolm.keys

import de.connect2x.trixnity.crypto.driver.keys.KeyFactories

object LibOlmKeyFactories : KeyFactories {

    override val ed25519PublicKey: LibOlmEd25519PublicKeyFactory = LibOlmEd25519PublicKeyFactory
    override val ed25519SecretKey: LibOlmEd25519SecretKeyFactory = LibOlmEd25519SecretKeyFactory


    override val curve25519PublicKey: LibOlmCurve25519PublicKeyFactory = LibOlmCurve25519PublicKeyFactory
    override val curve25519SecretKey: LibOlmCurve25519SecretKeyFactory = LibOlmCurve25519SecretKeyFactory

    override val ed25519Signature: LibOlmEd25519SignatureFactory = LibOlmEd25519SignatureFactory

    override val pickleKey: LibOlmPickleKeyFactory = LibOlmPickleKeyFactory
}