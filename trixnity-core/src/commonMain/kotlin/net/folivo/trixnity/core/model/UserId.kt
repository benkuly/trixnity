package net.folivo.trixnity.core.model

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class UserId(val full: String) {

    constructor(localpart: String, domain: String) : this("$sigilCharacter$localpart:$domain")

    companion object {
        const val sigilCharacter = '@'
    }

    val localpart: String
        get() = full.trimStart(sigilCharacter).substringBefore(':')
    val domain: String
        get() = full.trimStart(sigilCharacter).substringBefore(':')
}
