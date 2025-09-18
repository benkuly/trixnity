package net.folivo.trixnity.crypto.core

import kotlinx.serialization.Serializable
import net.folivo.trixnity.utils.nextString
import kotlin.io.encoding.Base64
import kotlin.jvm.JvmInline

@JvmInline
@Serializable
value class CodeChallenge(private val value: String) {
    override fun toString(): String = value
}

@JvmInline
@Serializable
value class CodeVerifier(private val value: String) {
    fun createChallenge(): CodeChallenge = Sha256().use {
        it.update(value.encodeToByteArray())
        CodeChallenge(base64.encode(it.digest()))
    }

    override fun toString(): String = value

    companion object {
        private val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

        fun random(length: Int = 64): CodeVerifier = CodeVerifier(SecureRandom.nextString(length))
    }
}
