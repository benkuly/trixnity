package net.folivo.trixnity.olm

expect class OlmOutboundGroupSession : WantsToBeFree {
    internal val ptr: OlmOutboundGroupSessionPointer

    companion object {
        suspend fun create(): OlmOutboundGroupSession
        suspend fun unpickle(key: String, pickle: String): OlmOutboundGroupSession
    }

    val sessionId: String
    val sessionKey: String
    val messageIndex: Long

    override fun free()
    fun pickle(key: String): String

    fun encrypt(plainText: String): String
}