package net.folivo.trixnity.olm

expect class OlmInboundGroupSession : WantsToBeFree {
    internal val ptr: OlmInboundGroupSessionPointer

    companion object {
        suspend fun create(sessionKey: String): OlmInboundGroupSession
        suspend fun import(sessionKey: String): OlmInboundGroupSession
        suspend fun unpickle(key: String, pickle: String): OlmInboundGroupSession
    }

    val sessionId: String
    val firstKnownIndex: Long

    override fun free()
    fun export(messageIndex: Long): String
    fun pickle(key: String): String

    fun decrypt(encryptedText: String): OlmInboundGroupMessage
}