package net.folivo.trixnity.core.serialization.crypto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.folivo.trixnity.core.model.crypto.CrossSigningKeysUsage

object CrossSigningKeyUsageSerializer : KSerializer<CrossSigningKeysUsage> {
    override fun deserialize(decoder: Decoder): CrossSigningKeysUsage {
        return when (val name = decoder.decodeString()) {
            CrossSigningKeysUsage.MasterKey.name -> CrossSigningKeysUsage.MasterKey
            CrossSigningKeysUsage.SelfSigningKey.name -> CrossSigningKeysUsage.SelfSigningKey
            CrossSigningKeysUsage.UserSigningKey.name -> CrossSigningKeysUsage.UserSigningKey
            else -> CrossSigningKeysUsage.UnknownCrossSigningKeyUsage(name)
        }
    }

    override fun serialize(encoder: Encoder, value: CrossSigningKeysUsage) {
        encoder.encodeString(value.name)
    }

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("CrossSigningKeyUsageSerializer", PrimitiveKind.STRING)
}