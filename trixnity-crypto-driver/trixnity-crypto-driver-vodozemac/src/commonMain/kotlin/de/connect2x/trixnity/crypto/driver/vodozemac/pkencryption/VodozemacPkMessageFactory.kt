package de.connect2x.trixnity.crypto.driver.vodozemac.pkencryption

import de.connect2x.trixnity.crypto.driver.vodozemac.UnpaddedBase64
import de.connect2x.trixnity.crypto.driver.pkencryption.PkMessageFactory
import de.connect2x.trixnity.vodozemac.Curve25519PublicKey
import de.connect2x.trixnity.vodozemac.pkencryption.PkEncryptionMessage

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