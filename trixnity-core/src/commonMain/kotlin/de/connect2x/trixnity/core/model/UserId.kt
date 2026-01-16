package de.connect2x.trixnity.core.model

import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.util.MatrixIdRegex
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class UserId(val full: String) {

    constructor(localpart: String, domain: String) : this("$sigilCharacter$localpart:$domain")

    companion object {
        const val sigilCharacter = '@'

        fun isValid(id: String): Boolean = id.length <= 255 && id.matches(MatrixIdRegex.userId)
        fun isReasonable(id: String): Boolean = id.length <= 255 && id.matches(MatrixIdRegex.reasonableUserId)
    }

    val localpart: String
        get() = full.trimStart(sigilCharacter).substringBefore(':')
    val domain: String
        get() = full.trimStart(sigilCharacter).substringAfter(':')

    val isValid: Boolean get() = isValid(full)
    val isReasonable: Boolean get() = isReasonable(full)

    override fun toString() = full
}
