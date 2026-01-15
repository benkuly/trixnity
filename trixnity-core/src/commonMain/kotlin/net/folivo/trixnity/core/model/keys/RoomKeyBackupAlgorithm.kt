package net.folivo.trixnity.core.model.keys

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.folivo.trixnity.core.serialization.stringWrapperSerializer

@Serializable(with = RoomKeyBackupAlgorithm.Serializer::class)
sealed class RoomKeyBackupAlgorithm {
    abstract val name: String

    override fun toString(): String {
        return name
    }

    @Serializable(with = RoomKeyBackupV1.Serializer::class)
    data object RoomKeyBackupV1 : RoomKeyBackupAlgorithm() {
        override val name: String = "m.megolm_backup.v1.curve25519-aes-sha2"

        object Serializer : KSerializer<RoomKeyBackupV1> by stringWrapperSerializer(RoomKeyBackupV1, name)
    }

    data class Unknown(override val name: String) : RoomKeyBackupAlgorithm()

    object Serializer : KSerializer<RoomKeyBackupAlgorithm> {
        override fun deserialize(decoder: Decoder): RoomKeyBackupAlgorithm {
            return when (val name = decoder.decodeString()) {
                RoomKeyBackupV1.name -> RoomKeyBackupV1
                else -> Unknown(name)
            }
        }

        override fun serialize(encoder: Encoder, value: RoomKeyBackupAlgorithm) {
            encoder.encodeString(value.name)
        }

        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("RoomKeyBackupAlgorithm", PrimitiveKind.STRING)
    }
}