package net.folivo.trixnity.core.model

import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.util.MatrixIdRegex
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class EventId(val full: String) {
    companion object {
        const val sigilCharacter = '$'

        fun isValid(id: String): Boolean = id.length <= 255 && id.matches(MatrixIdRegex.eventId)
        fun isReasonable(id: String): Boolean = id.length <= 255 && id.matches(MatrixIdRegex.reasonableEventId)
    }

    val isValid: Boolean get() = isValid(full)
    val isReasonable: Boolean get() = isReasonable(full)

    override fun toString() = full
}