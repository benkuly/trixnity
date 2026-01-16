package de.connect2x.trixnity.clientserverapi.model.uia

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import de.connect2x.trixnity.core.serialization.stringWrapperSerializer


@Serializable(with = AuthenticationType.Serializer::class)
sealed interface AuthenticationType {
    val name: String

    @Serializable(with = Password.Serializer::class)
    data object Password : AuthenticationType {
        override val name = "m.login.password"

        object Serializer : KSerializer<Password> by stringWrapperSerializer(Password, name)
    }

    @Serializable(with = Recaptcha.Serializer::class)
    data object Recaptcha : AuthenticationType {
        override val name = "m.login.recaptcha"

        object Serializer : KSerializer<Recaptcha> by stringWrapperSerializer(Recaptcha, name)
    }

    @Serializable(with = SSO.Serializer::class)
    data object SSO : AuthenticationType {
        override val name = "m.login.sso"

        object Serializer : KSerializer<SSO> by stringWrapperSerializer(SSO, name)
    }

    @Serializable(with = TermsOfService.Serializer::class)
    data object TermsOfService : AuthenticationType {
        override val name = "m.login.terms"

        object Serializer : KSerializer<TermsOfService> by stringWrapperSerializer(TermsOfService, name)
    }

    @Serializable(with = EmailIdentity.Serializer::class)
    data object EmailIdentity : AuthenticationType {
        override val name = "m.login.email.identity"

        object Serializer : KSerializer<EmailIdentity> by stringWrapperSerializer(EmailIdentity, name)
    }

    @Serializable(with = Msisdn.Serializer::class)
    data object Msisdn : AuthenticationType {
        override val name = "m.login.msisdn"

        object Serializer : KSerializer<Msisdn> by stringWrapperSerializer(Msisdn, name)
    }

    @Serializable(with = Dummy.Serializer::class)
    data object Dummy : AuthenticationType {
        override val name = "m.login.dummy"

        object Serializer : KSerializer<Dummy> by stringWrapperSerializer(Dummy, name)
    }

    @Serializable(with = RegistrationToken.Serializer::class)
    data object RegistrationToken : AuthenticationType {
        override val name = "m.login.registration_token"

        object Serializer : KSerializer<RegistrationToken> by stringWrapperSerializer(RegistrationToken, name)
    }

    @Serializable(with = OAuth2.Serializer::class)
    data object OAuth2 : AuthenticationType {
        override val name = "m.oauth"

        object Serializer : KSerializer<OAuth2> by stringWrapperSerializer(OAuth2, name)
    }

    @Serializable(with = Unknown.Serializer::class)
    data class Unknown(
        override val name: String
    ) : AuthenticationType {
        object Serializer : KSerializer<Unknown> by stringWrapperSerializer(::Unknown, Unknown::name)
    }

    object Serializer : KSerializer<AuthenticationType> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("AuthenticationType", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): AuthenticationType {
            return when (val name = decoder.decodeString()) {
                Password.name -> Password
                Recaptcha.name -> Recaptcha
                SSO.name -> SSO
                TermsOfService.name -> TermsOfService
                EmailIdentity.name -> EmailIdentity
                Msisdn.name -> Msisdn
                Dummy.name -> Dummy
                RegistrationToken.name -> RegistrationToken
                else -> Unknown(name)
            }
        }

        override fun serialize(encoder: Encoder, value: AuthenticationType) {
            encoder.encodeString(value.name)
        }
    }

}
