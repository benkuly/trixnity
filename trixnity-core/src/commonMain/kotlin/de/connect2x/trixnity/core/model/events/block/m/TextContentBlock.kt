package de.connect2x.trixnity.core.model.events.block.m

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.model.events.block.EventContentBlock
import de.connect2x.trixnity.core.model.events.block.m.TextContentBlock.Representation
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class TextContentBlock(
    private val representations: List<Representation>
) : EventContentBlock.Default, List<Representation> by representations {
    constructor(body: String, mimeType: String? = null) : this(listOf(Representation(body, mimeType)))
    constructor(vararg representation: Representation) : this(representation.toList())

    override val type: EventContentBlock.Type<TextContentBlock> get() = Type

    companion object Type : EventContentBlock.Type<TextContentBlock> {
        override val value: String = "m.text"

        override fun toString(): String = value
    }

    @Serializable
    data class Representation(
        @SerialName("body")
        val body: String,

        @SerialName("mimetype")
        val mimeType: String? = null,
    )

    val plain: String? get() = find { it.mimeType == null || it.mimeType == "text/plain" }?.body
    val html: String? get() = get("text/html")

    operator fun get(mimeType: String): String? = find { it.mimeType == mimeType }?.body
}