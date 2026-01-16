package de.connect2x.trixnity.libolm

expect class OlmOutboundGroupSession : WantsToBeFree {
    internal val ptr: OlmOutboundGroupSessionPointer

    companion object {
        fun create(): OlmOutboundGroupSession
        fun unpickle(key: String?, pickle: String): OlmOutboundGroupSession
    }

    val sessionId: String
    val sessionKey: String
    val messageIndex: Long

    override fun free()
    fun pickle(key: String?): String

    fun encrypt(plainText: String): String
}