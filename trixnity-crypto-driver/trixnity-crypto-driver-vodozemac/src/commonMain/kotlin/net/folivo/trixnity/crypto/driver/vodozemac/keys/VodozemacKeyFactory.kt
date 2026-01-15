package net.folivo.trixnity.crypto.driver.vodozemac.keys

import net.folivo.trixnity.crypto.driver.keys.KeyFactories

object VodozemacKeyFactory : KeyFactories {

    override val ed25519PublicKey: VodozemacEd25519PublicKeyFactory = VodozemacEd25519PublicKeyFactory

    override val ed25519SecretKey: VodozemacEd25519SecretKeyFactory = VodozemacEd25519SecretKeyFactory


    override val curve25519PublicKey: VodozemacCurve25519PublicKeyFactory = VodozemacCurve25519PublicKeyFactory

    override val curve25519SecretKey: VodozemacCurve25519SecretKeyFactory = VodozemacCurve25519SecretKeyFactory


    override val ed25519Signature: VodozemacEd25519SignatureFactory = VodozemacEd25519SignatureFactory

    override val pickleKey: VodozemacPickleKeyFactory = VodozemacPickleKeyFactory
}