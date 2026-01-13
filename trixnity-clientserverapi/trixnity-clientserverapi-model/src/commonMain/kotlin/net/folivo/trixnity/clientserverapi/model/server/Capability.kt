package net.folivo.trixnity.clientserverapi.model.server

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlin.jvm.JvmInline

sealed interface Capability {
    @Serializable
    data class ChangePassword(
        @SerialName("enabled") val enabled: Boolean
    ) : Capability {
        companion object {
            const val name = "m.change_password"
        }
    }

    @Serializable
    data class RoomVersions(
        @SerialName("default") val default: String,
        @SerialName("available") val available: Map<String, RoomVersionStability>
    ) : Capability {
        companion object {
            const val name = "m.room_versions"
        }

        @Serializable
        enum class RoomVersionStability {
            @SerialName("stable")
            STABLE,

            @SerialName("unstable")
            UNSTABLE
        }
    }

    @Deprecated("use ProfileFields instead")
    @Serializable
    data class SetDisplayName(
        @SerialName("enabled") val enabled: Boolean
    ) : Capability {
        companion object {
            const val name = "m.set_displayname"
        }
    }

    @Deprecated("use ProfileFields instead")
    @Serializable
    data class SetAvatarUrl(
        @SerialName("enabled") val enabled: Boolean
    ) : Capability {
        companion object {
            const val name = "m.set_avatar_url"
        }
    }

    @Serializable
    data class ProfileFields(
        @SerialName("enabled") val enabled: Boolean,
        @SerialName("allowed") val allowed: Set<String>? = null,
        @SerialName("disallowed") val disallowed: Set<String>? = null
    ) : Capability {
        companion object {
            const val name = "m.profile_fields"
        }
    }

    @Serializable
    data class ThirdPartyChanges(
        @SerialName("enabled") val enabled: Boolean
    ) : Capability {
        companion object {
            const val name = "m.3pid_changes"
        }
    }

    @Serializable
    data class GetLoginToken(
        @SerialName("enabled") val enabled: Boolean
    ) : Capability {
        companion object {
            const val name = "m.get_login_token"
        }
    }

    data class Unknown(val name: String, val raw: JsonElement) : Capability
}

@JvmInline
@Serializable(with = CapabilitiesSerializer::class)
value class Capabilities(private val delegate: Set<Capability>) : Set<Capability> by delegate

class CapabilitiesSerializer : KSerializer<Capabilities> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("CapabilitiesSerializer")

    override fun deserialize(decoder: Decoder): Capabilities {
        require(decoder is JsonDecoder)
        val jsonObject = decoder.decodeJsonElement() as? JsonObject ?: throw SerializationException("expected object")
        return Capabilities(
            jsonObject.map { (key, value) ->
                @Suppress("DEPRECATION")
                when (key) {
                    Capability.ChangePassword.name ->
                        decoder.json.decodeFromJsonElement<Capability.ChangePassword>(value)

                    Capability.RoomVersions.name ->
                        decoder.json.decodeFromJsonElement<Capability.RoomVersions>(value)

                    Capability.SetDisplayName.name ->
                        decoder.json.decodeFromJsonElement<Capability.SetDisplayName>(value)

                    Capability.SetAvatarUrl.name ->
                        decoder.json.decodeFromJsonElement<Capability.SetAvatarUrl>(value)

                    Capability.ProfileFields.name ->
                        decoder.json.decodeFromJsonElement<Capability.ProfileFields>(value)

                    Capability.ThirdPartyChanges.name ->
                        decoder.json.decodeFromJsonElement<Capability.ThirdPartyChanges>(value)

                    Capability.GetLoginToken.name ->
                        decoder.json.decodeFromJsonElement<Capability.GetLoginToken>(value)

                    else -> Capability.Unknown(key, value)
                }
            }.toSet()
        )
    }

    override fun serialize(encoder: Encoder, value: Capabilities) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(
            encoder.json.encodeToJsonElement(
                value.associate { element ->
                    @Suppress("DEPRECATION")
                    when (element) {
                        is Capability.ChangePassword ->
                            Capability.ChangePassword.name to encoder.json.encodeToJsonElement(element)

                        is Capability.GetLoginToken ->
                            Capability.GetLoginToken.name to encoder.json.encodeToJsonElement(element)

                        is Capability.RoomVersions ->
                            Capability.RoomVersions.name to encoder.json.encodeToJsonElement(element)

                        is Capability.SetAvatarUrl ->
                            Capability.SetAvatarUrl.name to encoder.json.encodeToJsonElement(element)

                        is Capability.SetDisplayName ->
                            Capability.SetDisplayName.name to encoder.json.encodeToJsonElement(element)

                        is Capability.ProfileFields ->
                            Capability.ProfileFields.name to encoder.json.encodeToJsonElement(element)

                        is Capability.ThirdPartyChanges ->
                            Capability.ThirdPartyChanges.name to encoder.json.encodeToJsonElement(element)

                        is Capability.Unknown -> element.name to element.raw
                    }
                }
            ))
    }
}

val Capabilities.changePassword: Capability.ChangePassword
    get() = filterIsInstance<Capability.ChangePassword>().firstOrNull()
        ?: Capability.ChangePassword(true)

val Capabilities.roomVersion: Capability.RoomVersions?
    get() = filterIsInstance<Capability.RoomVersions>().firstOrNull()

@Deprecated("use ProfileFields instead")
@Suppress("DEPRECATION")
val Capabilities.setDisplayName: Capability.SetDisplayName
    get() = filterIsInstance<Capability.SetDisplayName>().firstOrNull()
        ?: Capability.SetDisplayName(true)

@Deprecated("use ProfileFields instead")
@Suppress("DEPRECATION")
val Capabilities.setAvatarUrl: Capability.SetAvatarUrl
    get() = filterIsInstance<Capability.SetAvatarUrl>().firstOrNull()
        ?: Capability.SetAvatarUrl(true)

val Capabilities.profileFields: Capability.ProfileFields
    get() = filterIsInstance<Capability.ProfileFields>().firstOrNull()
        ?: Capability.ProfileFields(true)

val Capabilities.thirdPartyChanges: Capability.ThirdPartyChanges
    get() = filterIsInstance<Capability.ThirdPartyChanges>().firstOrNull()
        ?: Capability.ThirdPartyChanges(true)

val Capabilities.getLoginToken: Capability.GetLoginToken
    get() = filterIsInstance<Capability.GetLoginToken>().firstOrNull()
        ?: Capability.GetLoginToken(false)