package de.connect2x.trixnity.clientserverapi.model.server

import de.connect2x.trixnity.clientserverapi.model.user.ProfileField
import de.connect2x.trixnity.core.MSC4140
import kotlin.jvm.JvmInline
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

sealed interface Capability {
    @Serializable
    data class ChangePassword(@SerialName("enabled") val enabled: Boolean) : Capability {
        companion object {
            const val name = "m.change_password"
        }
    }

    @Serializable
    data class ForgetForcedUponLeave(@SerialName("enabled") val enabled: Boolean) : Capability {
        companion object {
            const val name = "m.forget_forced_upon_leave"
        }
    }

    @Serializable
    data class RoomVersions(
        @SerialName("default") val default: String,
        @SerialName("available") val available: Map<String, RoomVersionStability>,
    ) : Capability {
        companion object {
            const val name = "m.room_versions"
        }

        @Serializable
        enum class RoomVersionStability {
            @SerialName("stable") STABLE,
            @SerialName("unstable") UNSTABLE,
        }
    }

    @Deprecated("use ProfileFields instead")
    @Serializable
    data class SetDisplayName(@SerialName("enabled") val enabled: Boolean) : Capability {
        companion object {
            const val name = "m.set_displayname"
        }
    }

    @Deprecated("use ProfileFields instead")
    @Serializable
    data class SetAvatarUrl(@SerialName("enabled") val enabled: Boolean) : Capability {
        companion object {
            const val name = "m.set_avatar_url"
        }
    }

    @Serializable
    data class ProfileFields(
        @SerialName("enabled") val enabled: Boolean,
        @SerialName("allowed") val allowed: Set<ProfileField.Key<*>>? = null,
        @SerialName("disallowed") val disallowed: Set<ProfileField.Key<*>>? = null,
    ) : Capability {
        companion object {
            const val name = "m.profile_fields"
        }

        fun isChangeAllowed(key: ProfileField.Key<*>): Boolean =
            enabled &&
                (allowed != null && allowed.contains(key) ||
                    allowed == null && (disallowed == null || !disallowed.contains(key)))
    }

    @Serializable
    data class ThirdPartyChanges(@SerialName("enabled") val enabled: Boolean) : Capability {
        companion object {
            const val name = "m.3pid_changes"
        }
    }

    @Serializable
    data class GetLoginToken(@SerialName("enabled") val enabled: Boolean) : Capability {
        companion object {
            const val name = "m.get_login_token"
        }
    }

    @Serializable
    data class AccountModeration(
        @SerialName("lock") val lock: Boolean? = null,
        @SerialName("suspend") val suspend: Boolean? = null,
    ) : Capability {
        companion object {
            const val name = "m.account_moderation"
        }
    }

    @Serializable
    @MSC4140
    data class DelayedEvents(
        @SerialName("max_delay_ms") val maxDelayMs: Long? = null,
        @SerialName("max_scheduled") val maxScheduled: Long? = null,
    ) : Capability {
        companion object {
            const val name = "org.matrix.msc4140.delayed_events"
        }
    }

    data class Unknown(val name: String, val raw: JsonElement) : Capability
}

@JvmInline
@Serializable(with = Capabilities.Serializer::class)
value class Capabilities(private val delegate: Set<Capability>) : Set<Capability> by delegate {
    class Serializer : KSerializer<Capabilities> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Capabilities")

        override fun deserialize(decoder: Decoder): Capabilities {
            require(decoder is JsonDecoder)
            val jsonObject =
                decoder.decodeJsonElement() as? JsonObject ?: throw SerializationException("expected object")
            return Capabilities(
                jsonObject
                    .map { (key, value) ->
                        @Suppress("DEPRECATION") @OptIn(MSC4140::class)
                        when (key) {
                            Capability.ChangePassword.name ->
                                decoder.json.decodeFromJsonElement<Capability.ChangePassword>(value)

                            Capability.ForgetForcedUponLeave.name ->
                                decoder.json.decodeFromJsonElement<Capability.ForgetForcedUponLeave>(value)

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

                            Capability.AccountModeration.name ->
                                decoder.json.decodeFromJsonElement<Capability.AccountModeration>(value)

                            Capability.DelayedEvents.name ->
                                decoder.json.decodeFromJsonElement<Capability.DelayedEvents>(value)

                            else -> Capability.Unknown(key, value)
                        }
                    }
                    .toSet()
            )
        }

        override fun serialize(encoder: Encoder, value: Capabilities) {
            require(encoder is JsonEncoder)
            encoder.encodeJsonElement(
                encoder.json.encodeToJsonElement(
                    value.associate { element ->
                        @Suppress("DEPRECATION") @OptIn(MSC4140::class)
                        when (element) {
                            is Capability.ChangePassword ->
                                Capability.ChangePassword.name to encoder.json.encodeToJsonElement(element)

                            is Capability.ForgetForcedUponLeave ->
                                Capability.ForgetForcedUponLeave.name to encoder.json.encodeToJsonElement(element)

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

                            is Capability.AccountModeration ->
                                Capability.AccountModeration.name to encoder.json.encodeToJsonElement(element)

                            is Capability.DelayedEvents ->
                                Capability.DelayedEvents.name to encoder.json.encodeToJsonElement(element)

                            is Capability.Unknown -> element.name to element.raw
                        }
                    }
                )
            )
        }
    }
}

val Capabilities.changePassword: Capability.ChangePassword
    get() = filterIsInstance<Capability.ChangePassword>().firstOrNull() ?: Capability.ChangePassword(true)

val Capabilities.forgetForcedUponLeave: Capability.ForgetForcedUponLeave
    get() =
        filterIsInstance<Capability.ForgetForcedUponLeave>().firstOrNull() ?: Capability.ForgetForcedUponLeave(false)

val Capabilities.roomVersion: Capability.RoomVersions?
    get() = filterIsInstance<Capability.RoomVersions>().firstOrNull()

@Deprecated("use profileFields instead")
@Suppress("DEPRECATION")
val Capabilities.setDisplayName: Capability.SetDisplayName
    get() = filterIsInstance<Capability.SetDisplayName>().firstOrNull() ?: Capability.SetDisplayName(true)

@Deprecated("use profileFields instead")
@Suppress("DEPRECATION")
val Capabilities.setAvatarUrl: Capability.SetAvatarUrl
    get() = filterIsInstance<Capability.SetAvatarUrl>().firstOrNull() ?: Capability.SetAvatarUrl(true)

@Deprecated("use profileFields function instead")
val Capabilities.profileFields: Capability.ProfileFields
    get() = filterIsInstance<Capability.ProfileFields>().firstOrNull() ?: Capability.ProfileFields(true)

fun Capabilities.profileFields(versions: GetVersions.Response): Capability.ProfileFields =
    filterIsInstance<Capability.ProfileFields>().firstOrNull()
        ?: Capability.ProfileFields(versions.versions.contains("v1.16"))

val Capabilities.thirdPartyChanges: Capability.ThirdPartyChanges
    get() = filterIsInstance<Capability.ThirdPartyChanges>().firstOrNull() ?: Capability.ThirdPartyChanges(true)

val Capabilities.getLoginToken: Capability.GetLoginToken
    get() = filterIsInstance<Capability.GetLoginToken>().firstOrNull() ?: Capability.GetLoginToken(false)

val Capabilities.accountModeration: Capability.AccountModeration
    get() = filterIsInstance<Capability.AccountModeration>().firstOrNull() ?: Capability.AccountModeration()
