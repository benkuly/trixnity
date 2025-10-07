package net.folivo.trixnity.crypto.driver.libvodozemac.pkencryption

import net.folivo.trixnity.crypto.driver.libvodozemac.UnpaddedBase64
import net.folivo.trixnity.crypto.driver.pkencryption.PkMessageFactory
import net.folivo.trixnity.vodozemac.Curve25519PublicKey
import net.folivo.trixnity.vodozemac.pkencryption.PkEncryptionMessage

object LibVodozemacPkMessageFactory : PkMessageFactory {
    override fun invoke(cipherText: String, mac: String, ephemeralKey: String): LibVodozemacPkMessage =
        LibVodozemacPkMessage(
            PkEncryptionMessage.Text(
                UnpaddedBase64.decode(cipherText),
                UnpaddedBase64.decode(mac),
                Curve25519PublicKey(ephemeralKey),
            )
        )
}