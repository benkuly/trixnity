package de.connect2x.trixnity.core.model

import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.util.MatrixIdRegex
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class RoomId(val full: String) {

    companion object {
        const val sigilCharacter = '!'

        fun isValid(id: String): Boolean = id.length <= 255 && id.matches(MatrixIdRegex.roomId)
        fun isReasonable(id: String): Boolean = id.length <= 255 && id.matches(MatrixIdRegex.reasonableRoomId)
    }

    val isValid: Boolean get() = isValid(full)
    val isReasonable: Boolean get() = isReasonable(full)

    override fun toString() = full
}