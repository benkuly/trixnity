package net.folivo.trixnity.libolm

expect class OlmAccount : WantsToBeFree {
    internal val ptr: OlmAccountPointer

    companion object {
        fun create(): OlmAccount
        fun unpickle(key: String?, pickle: String): OlmAccount
    }

    val identityKeys: OlmIdentityKeys
    val unpublishedFallbackKey: OlmOneTimeKeys
    val oneTimeKeys: OlmOneTimeKeys
    val maxNumberOfOneTimeKeys: Long

    override fun free()
    fun pickle(key: String?): String

    fun sign(message: String): String
    fun markKeysAsPublished()
    fun generateOneTimeKeys(numberOfKeys: Long)
    fun removeOneTimeKeys(session: OlmSession)
    fun generateFallbackKey()
    fun forgetOldFallbackKey()

}