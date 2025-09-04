package net.folivo.trixnity.olm

expect class OlmSession : WantsToBeFree {
    internal val ptr: OlmSessionPointer

    companion object {
        fun createOutbound(account: OlmAccount, theirIdentityKey: String, theirOneTimeKey: String): OlmSession
        fun createInbound(account: OlmAccount, oneTimeKeyMessage: String): OlmSession
        fun createInboundFrom(account: OlmAccount, identityKey: String, oneTimeKeyMessage: String): OlmSession
        fun unpickle(key: String, pickle: String): OlmSession
    }

    val sessionId: String
    val hasReceivedMessage: Boolean
    val description: String

    override fun free()
    fun pickle(key: String): String

    fun matchesInboundSession(oneTimeKeyMessage: String): Boolean
    fun matchesInboundSessionFrom(identityKey: String, oneTimeKeyMessage: String): Boolean
    fun encrypt(plainText: String): OlmMessage
    fun decrypt(message: OlmMessage): String
}