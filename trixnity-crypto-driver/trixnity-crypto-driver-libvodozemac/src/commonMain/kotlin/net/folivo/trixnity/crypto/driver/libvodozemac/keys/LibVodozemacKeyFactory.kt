package net.folivo.trixnity.crypto.driver.libvodozemac.keys

import net.folivo.trixnity.crypto.driver.keys.KeyFactories

object LibVodozemacKeyFactory : KeyFactories {

    override val ed25519PublicKey: LibVodozemacEd25519PublicKeyFactory
        = LibVodozemacEd25519PublicKeyFactory

    override val ed25519SecretKey: LibVodozemacEd25519SecretKeyFactory
        = LibVodozemacEd25519SecretKeyFactory


    override val curve25519PublicKey: LibVodozemacCurve25519PublicKeyFactory
        = LibVodozemacCurve25519PublicKeyFactory

    override val curve25519SecretKey: LibVodozemacCurve25519SecretKeyFactory
        = LibVodozemacCurve25519SecretKeyFactory


    override val ed25519Signature: LibVodozemacEd25519SignatureFactory
        = LibVodozemacEd25519SignatureFactory


    override val pickleKey: LibVodozemacPickleKeyFactory
        get() = LibVodozemacPickleKeyFactory
}