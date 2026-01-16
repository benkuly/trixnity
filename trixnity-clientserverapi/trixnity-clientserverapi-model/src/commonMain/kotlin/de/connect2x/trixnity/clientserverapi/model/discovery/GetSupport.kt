package de.connect2x.trixnity.clientserverapi.model.discovery

import io.ktor.resources.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import de.connect2x.trixnity.core.Auth
import de.connect2x.trixnity.core.AuthRequired
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#getwell-knownmatrixsupport">matrix spec</a>
 */
@Serializable
@Resource("/.well-known/matrix/support")
@HttpMethod(GET)
@Auth(AuthRequired.NO)
object GetSupport : MatrixEndpoint<Unit, GetSupport.Response> {
    @Serializable
    data class Response(
        @SerialName("contacts") val contacts: List<Contact> = listOf(),
        @SerialName("support_page") val supportPage: String? = null,
    ) {
        @Serializable
        data class Contact(
            @SerialName("email_address") val emailAddress: String? = null,
            @SerialName("matrix_id") val userId: UserId? = null,
            @SerialName("role") val role: Role,
        ) {
            @Serializable(with = Role.Serializer::class)
            sealed interface Role {
                val name: String

                data object Admin : Role {
                    override val name: String = "m.role.admin"
                }

                data object Security : Role {
                    override val name: String = "m.role.security"
                }

                data class Unknown(override val name: String) : Role

                object Serializer : KSerializer<Role> {
                    override val descriptor: SerialDescriptor =
                        PrimitiveSerialDescriptor("Role", PrimitiveKind.STRING)

                    override fun deserialize(decoder: Decoder): Role {
                        return when (val name = decoder.decodeString()) {
                            Admin.name -> Admin
                            Security.name -> Security
                            else -> Unknown(name)
                        }
                    }

                    override fun serialize(encoder: Encoder, value: Role) {
                        encoder.encodeString(value.name)
                    }
                }
            }
        }
    }
}