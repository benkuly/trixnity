package net.folivo.trixnity.crypto.driver.sas

import net.folivo.trixnity.crypto.driver.keys.Curve25519PublicKey

interface EstablishedSas : AutoCloseable {
    val ourPublicKey: Curve25519PublicKey
    val theirPublicKey: Curve25519PublicKey

    fun generateBytes(info: String): SasBytes
    fun calculateMac(input: String, info: String): Mac
    fun calculateMacInvalidBase64(input: String, info: String): String
    fun verifyMac(input: String, info: String, tag: Mac)
}