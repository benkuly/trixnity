package net.folivo.trixnity.olm

actual class OlmSAS private constructor() : WantsToBeFree {
    internal actual val ptr: OlmSASPointer = rethrow { js("new Olm.SAS()") }.unsafeCast<OlmSASPointer>()

    actual companion object {
        actual suspend fun create(): OlmSAS {
            initOlm()
            return OlmSAS()
        }
    }

    actual val publicKey: String get() = rethrow { ptr.get_pubkey() }

    actual override fun free() = ptr.free()

    actual fun setTheirPublicKey(theirPublicKey: String) = rethrow { ptr.set_their_key(theirPublicKey) }

    actual fun generateShortCode(info: String, numberOfBytes: Int): ByteArray =
        rethrow { ptr.generate_bytes(info, numberOfBytes) }.unsafeCast<ByteArray>()

    actual fun calculateMac(input: String, info: String): String = rethrow { ptr.calculate_mac(input, info) }
    actual fun calculateMacFixedBase64(input: String, info: String) =
        rethrow { ptr.calculate_mac_fixed_base64(input, info) }
}