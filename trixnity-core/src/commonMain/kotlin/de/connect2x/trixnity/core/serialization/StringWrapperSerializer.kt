package de.connect2x.trixnity.core.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.reflect.KClass

inline fun <reified T : Any> stringWrapperSerializer(
    noinline construct: (String) -> T,
    noinline extract: (T) -> String
): KSerializer<T> =
    object : StringWrapperSerializer<T>(T::class, construct, extract) {}

inline fun <reified T : Any> stringWrapperSerializer(
    value: T,
    stringValue: String,
): KSerializer<T> =
    object : StringWrapperSerializer<T>(T::class, { value }, { stringValue }) {}

abstract class StringWrapperSerializer<T : Any>(
    name: KClass<T>,
    private val construct: (String) -> T,
    private val extract: (T) -> String
) : KSerializer<T> {
    override val descriptor = PrimitiveSerialDescriptor("PrimitiveStringSerializer<$name>", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: T) =
        encoder.encodeString(extract(value))

    override fun deserialize(decoder: Decoder): T =
        construct(decoder.decodeString())
}