package de.connect2x.trixnity.vodozemac

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalMultiplatform::class)
@OptionalExpectation
expect annotation class ExternalSymbolName(val name: String)

@OptIn(ExperimentalMultiplatform::class)
@OptionalExpectation
@Target(AnnotationTarget.FUNCTION)
expect annotation class ModuleImport(val module: String, val name: String)

@OptIn(ExperimentalMultiplatform::class)
@OptionalExpectation
@Target(AnnotationTarget.FILE)
expect annotation class Import(
    val import: String,
)

internal expect val InitHook: () -> Unit

class VodozemacException(message: String) : Exception(message)

@OptIn(ExperimentalEncodingApi::class)
internal object UnpaddedBase64 {
    private val impl = Base64.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

    fun encode(data: ByteArray): String = impl.encode(data)

    fun decode(data: String): ByteArray = impl.decode(data)
}

sealed interface MessageTag<T> {
    val value: T

    class String(override val value: kotlin.String) : MessageTag<kotlin.String>

    class Bytes(override val value: ByteArray) : MessageTag<ByteArray>
}

sealed interface Plaintext<out T> {
    val value: T

    data class Text(override val value: String) : Plaintext<String> {
        companion object {
            fun of(value: String): Text = Text(value)

            fun of(value: ByteArray): Text = Text(value.decodeToString())

            fun of(value: Bytes): Text = of(value.value)
        }
    }

    data class Bytes(override val value: ByteArray) : Plaintext<ByteArray> {
        companion object {
            fun of(value: String): Bytes = Bytes(value.encodeToByteArray())

            fun of(value: ByteArray): Bytes = Bytes(value)

            fun of(value: Text): Bytes = of(value.value)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Bytes

            return value.contentEquals(other.value)
        }

        override fun hashCode(): Int {
            return value.contentHashCode()
        }
    }

    companion object {
        fun of(value: ByteArray): Bytes = Bytes(value)

        fun of(value: String): Text = Text(value)
    }
}
