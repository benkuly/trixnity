package net.folivo.trixnity.core.model

import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.util.MatrixIdRegex
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class RoomAliasId(val full: String) {

    constructor(localpart: String, domain: String) : this("${sigilCharacter}$localpart:$domain")

    companion object {
        const val sigilCharacter = '#'

        fun isValid(id: String): Boolean = id.length <= 255 && id.matches(MatrixIdRegex.roomAlias)
    }

    val localpart: String
        get() = full.trimStart(sigilCharacter).substringBefore(':')
    val domain: String
        get() = full.trimStart(sigilCharacter).substringAfter(':')

    val isValid: Boolean get() = isValid(full)

    override fun toString() = full
}