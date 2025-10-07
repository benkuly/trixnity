package net.folivo.trixnity.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.jvm.JvmInline
import kotlin.reflect.KClass

@JvmInline
@Serializable(with = MegolmMessageValue.Serializer::class)
value class MegolmMessageValue(val value: String) {
    object Serializer : KSerializer<MegolmMessageValue> by stringWrapper(::MegolmMessageValue, MegolmMessageValue::value)
}

@JvmInline
@Serializable(with = ExportedSessionKeyValue.Serializer::class)
value class ExportedSessionKeyValue(val value: String) {
    object Serializer : KSerializer<ExportedSessionKeyValue> by stringWrapper(::ExportedSessionKeyValue, ExportedSessionKeyValue::value)
}

@JvmInline
@Serializable(with = MacValue.Serializer::class)
value class MacValue(val value: String) {
    object Serializer : KSerializer<MacValue> by stringWrapper(::MacValue, MacValue::value)
}

@JvmInline
@Serializable(with = SessionKeyValue.Serializer::class)
value class SessionKeyValue(val value: String) {
    object Serializer : KSerializer<SessionKeyValue> by stringWrapper(::SessionKeyValue, SessionKeyValue::value)
}

@JvmInline
@Serializable(with = OlmMessageValue.Serializer::class)
value class OlmMessageValue(val value: String) {
    object Serializer : KSerializer<OlmMessageValue> by stringWrapper(::OlmMessageValue, OlmMessageValue::value)
}

internal inline fun <reified T: Any> stringWrapper(
    noinline construct: (String) -> T,
    noinline extract: (T) -> String
) : KSerializer<T> =
    object : StringWrapperSerializer<T>(T::class, construct, extract) {}

internal abstract class StringWrapperSerializer<T: Any>(
    name: KClass<T>,
    private val construct: (String) -> T,
    private val extract: (T) -> String
) : KSerializer<T> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("StringWrapper<$name>", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: T) =
        encoder.encodeString(extract(value))

    override fun deserialize(decoder: Decoder): T =
        construct(decoder.decodeString())
}
