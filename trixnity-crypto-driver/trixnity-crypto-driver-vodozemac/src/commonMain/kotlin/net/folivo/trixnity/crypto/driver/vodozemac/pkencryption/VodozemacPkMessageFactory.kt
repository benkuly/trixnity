package net.folivo.trixnity.crypto.driver.vodozemac.pkencryption

import net.folivo.trixnity.crypto.driver.vodozemac.UnpaddedBase64
import net.folivo.trixnity.crypto.driver.pkencryption.PkMessageFactory
import net.folivo.trixnity.vodozemac.Curve25519PublicKey
import net.folivo.trixnity.vodozemac.pkencryption.PkEncryptionMessage

object VodozemacPkMessageFactory : PkMessageFactory {
    override fun invoke(cipherText: String, mac: String, ephemeralKey: String): VodozemacPkMessage =
        VodozemacPkMessage(
            PkEncryptionMessage.Text(
                UnpaddedBase64.decode(cipherText),
                UnpaddedBase64.decode(mac),
                Curve25519PublicKey(ephemeralKey),
            )
        )
}