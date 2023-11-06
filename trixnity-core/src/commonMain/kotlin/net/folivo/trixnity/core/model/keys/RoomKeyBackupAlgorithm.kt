package net.folivo.trixnity.core.model.keys

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = RoomKeyBackupAlgorithmSerializer::class)
sealed class RoomKeyBackupAlgorithm {
    abstract val name: String

    override fun toString(): String {
        return name
    }

    @Serializable(with = RoomKeyBackupV1Serializer::class)
    data object RoomKeyBackupV1 : RoomKeyBackupAlgorithm() {
        override val name: String
            get() = "m.megolm_backup.v1.curve25519-aes-sha2"
    }

    @Serializable(with = UnknownRoomKeyBackupAlgorithmSerializer::class)
    data class Unknown(override val name: String) : RoomKeyBackupAlgorithm()
}

object RoomKeyBackupAlgorithmSerializer : KSerializer<RoomKeyBackupAlgorithm> {
    override fun deserialize(decoder: Decoder): RoomKeyBackupAlgorithm {
        return when (val name = decoder.decodeString()) {
            RoomKeyBackupAlgorithm.RoomKeyBackupV1.name -> RoomKeyBackupAlgorithm.RoomKeyBackupV1
            else -> RoomKeyBackupAlgorithm.Unknown(name)
        }
    }

    override fun serialize(encoder: Encoder, value: RoomKeyBackupAlgorithm) {
        encoder.encodeString(value.name)
    }

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("RoomKeyBackupAlgorithmSerializer", PrimitiveKind.STRING)
}

object RoomKeyBackupV1Serializer : KSerializer<RoomKeyBackupAlgorithm.RoomKeyBackupV1> {
    override fun deserialize(decoder: Decoder): RoomKeyBackupAlgorithm.RoomKeyBackupV1 {
        return RoomKeyBackupAlgorithm.RoomKeyBackupV1
    }

    override fun serialize(encoder: Encoder, value: RoomKeyBackupAlgorithm.RoomKeyBackupV1) {
        encoder.encodeString(value.name)
    }

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("RoomKeyBackupV1AlgorithmSerializer", PrimitiveKind.STRING)
}

object UnknownRoomKeyBackupAlgorithmSerializer : KSerializer<RoomKeyBackupAlgorithm.Unknown> {
    override fun deserialize(decoder: Decoder): RoomKeyBackupAlgorithm.Unknown {
        return RoomKeyBackupAlgorithm.Unknown(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: RoomKeyBackupAlgorithm.Unknown) {
        encoder.encodeString(value.name)
    }

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("RoomKeyBackupAlgorithmSerializer", PrimitiveKind.STRING)
}