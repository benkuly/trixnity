package net.folivo.trixnity.clientserverapi.model.users

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlin.jvm.JvmInline

@Serializable(with = Profile.Serializer::class)
@JvmInline
value class Profile private constructor(
    private val profileFields: Map<ProfileField.Key<*>, ProfileField> = emptyMap(),
) {
    constructor(profileFields: Set<ProfileField>) : this(profileFields.associateBy { it.key })
    constructor(vararg profileFields: ProfileField) : this(profileFields.toSet())

    operator fun <T : ProfileField> get(type: ProfileField.Key<T>): T? {
        val block = profileFields[type] ?: return null
        @Suppress("UNCHECKED_CAST")
        return block as? T
    }

    fun getUnknown(type: String): ProfileField.Unknown? {
        return profileFields[ProfileField.Unknown.Key(type)] as? ProfileField.Unknown
    }

    operator fun plus(other: ProfileField): Profile = Profile(profileFields + (other.key to other))
    operator fun minus(other: ProfileField): Profile = Profile(profileFields - other.key)

    val size: Int get() = profileFields.size
    fun isEmpty(): Boolean = profileFields.isEmpty()
    fun containsType(key: ProfileField.Key<*>): Boolean = profileFields.containsKey(key)
    fun contains(block: ProfileField): Boolean = profileFields.containsValue(block)
    val types: Set<ProfileField.Key<*>> get() = profileFields.keys
    val values: Collection<ProfileField> get() = profileFields.values

    object Serializer : KSerializer<Profile> {
        override val descriptor = buildClassSerialDescriptor("Profile")

        override fun deserialize(decoder: Decoder): Profile {
            require(decoder is JsonDecoder)
            val jsonObject = decoder.decodeJsonElement().jsonObject
            return Profile(
                jsonObject.map {
                    when (it.key) {
                        ProfileField.DisplayName.name ->
                            decoder.json.decodeFromJsonElement<ProfileField.DisplayName>(jsonObject)

                        ProfileField.AvatarUrl.name ->
                            decoder.json.decodeFromJsonElement<ProfileField.AvatarUrl>(jsonObject)

                        ProfileField.TimeZone.name ->
                            decoder.json.decodeFromJsonElement<ProfileField.TimeZone>(jsonObject)

                        else -> ProfileField.Unknown(ProfileField.Unknown.Key(it.key), it.value)
                    }
                }.toSet()
            )
        }

        override fun serialize(encoder: Encoder, value: Profile) {
            require(encoder is JsonEncoder)
            encoder.encodeJsonElement(
                JsonObject(
                    buildMap {
                        value.values.forEach {
                            putAll(encoder.json.encodeToJsonElement(it).jsonObject)
                        }
                    }
                )
            )
        }
    }
}

val Profile.displayName: String? get() = get(ProfileField.DisplayName)?.value
val Profile.avatarUrl: String? get() = get(ProfileField.AvatarUrl)?.value
val Profile.timeZone: String? get() = get(ProfileField.TimeZone)?.value