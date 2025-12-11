package net.folivo.trixnity.clientserverapi.model.uia

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = AuthenticationTypeSerializer::class)
sealed interface AuthenticationType {
    val name: String

    @Serializable(with = PasswordAuthenticationTypeSerializer::class)
    data object Password : AuthenticationType {
        override val name = "m.login.password"
    }

    @Serializable(with = RecaptchaAuthenticationTypeSerializer::class)
    data object Recaptcha : AuthenticationType {
        override val name = "m.login.recaptcha"
    }

    @Serializable(with = SSOAuthenticationTypeSerializer::class)
    data object SSO : AuthenticationType {
        override val name = "m.login.sso"
    }

    @Serializable(with = TermsOfServiceAuthenticationTypeSerializer::class)
    data object TermsOfService : AuthenticationType {
        override val name = "m.login.terms"
    }

    @Serializable(with = EmailIdentityAuthenticationTypeSerializer::class)
    data object EmailIdentity : AuthenticationType {
        override val name = "m.login.email.identity"
    }

    @Serializable(with = MsisdnAuthenticationTypeSerializer::class)
    data object Msisdn : AuthenticationType {
        override val name = "m.login.msisdn"
    }

    @Serializable(with = DummyAuthenticationTypeSerializer::class)
    data object Dummy : AuthenticationType {
        override val name = "m.login.dummy"
    }

    @Serializable(with = RegistrationTokenAuthenticationTypeSerializer::class)
    data object RegistrationToken : AuthenticationType {
        override val name = "m.login.registration_token"
    }

    @Serializable(with = OAuth2AuthenticationTypeSerializer::class)
    data object OAuth2 : AuthenticationType {
        override val name = "m.oauth"
    }

    @Serializable(with = UnknownAuthenticationTypeSerializer::class)
    data class Unknown(
        override val name: String
    ) : AuthenticationType

    object PasswordAuthenticationTypeSerializer : KSerializer<Password> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("PasswordAuthenticationSerializer", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): Password {
            return Password
        }

        override fun serialize(encoder: Encoder, value: Password) {
            encoder.encodeString(value.name)
        }
    }

    object RecaptchaAuthenticationTypeSerializer : KSerializer<Recaptcha> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("RecaptchaAuthenticationSerializer", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): Recaptcha {
            return Recaptcha
        }

        override fun serialize(encoder: Encoder, value: Recaptcha) {
            encoder.encodeString(value.name)
        }
    }

    object SSOAuthenticationTypeSerializer : KSerializer<SSO> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("SSOAuthenticationSerializer", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): SSO {
            return SSO
        }

        override fun serialize(encoder: Encoder, value: SSO) {
            encoder.encodeString(value.name)
        }
    }

    object TermsOfServiceAuthenticationTypeSerializer : KSerializer<TermsOfService> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("TermsOfServiceAuthenticationSerializer", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): TermsOfService {
            return TermsOfService
        }

        override fun serialize(encoder: Encoder, value: TermsOfService) {
            encoder.encodeString(value.name)
        }
    }

    object EmailIdentityAuthenticationTypeSerializer : KSerializer<EmailIdentity> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("EmailIdentityAuthenticationSerializer", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): EmailIdentity {
            return EmailIdentity
        }

        override fun serialize(encoder: Encoder, value: EmailIdentity) {
            encoder.encodeString(value.name)
        }
    }

    object MsisdnAuthenticationTypeSerializer : KSerializer<Msisdn> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("MsisdnAuthenticationSerializer", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): Msisdn {
            return Msisdn
        }

        override fun serialize(encoder: Encoder, value: Msisdn) {
            encoder.encodeString(value.name)
        }
    }

    object DummyAuthenticationTypeSerializer : KSerializer<Dummy> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("DummyAuthenticationSerializer", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): Dummy {
            return Dummy
        }

        override fun serialize(encoder: Encoder, value: Dummy) {
            encoder.encodeString(value.name)
        }
    }

    object RegistrationTokenAuthenticationTypeSerializer : KSerializer<RegistrationToken> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("RegistrationTokenAuthenticationSerializer", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): RegistrationToken {
            return RegistrationToken
        }

        override fun serialize(encoder: Encoder, value: RegistrationToken) {
            encoder.encodeString(value.name)
        }
    }

    object OAuth2AuthenticationTypeSerializer : KSerializer<OAuth2> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("OAuth2AuthenticationSerializer", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): OAuth2 {
            return OAuth2
        }

        override fun serialize(encoder: Encoder, value: OAuth2) {
            encoder.encodeString(value.name)
        }
    }

    object UnknownAuthenticationTypeSerializer : KSerializer<Unknown> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("UnknownAuthenticationSerializer", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): Unknown {
            return Unknown(decoder.decodeString())
        }

        override fun serialize(encoder: Encoder, value: Unknown) {
            encoder.encodeString(value.name)
        }
    }
}

object AuthenticationTypeSerializer : KSerializer<AuthenticationType> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("AuthenticationTypeSerializer", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): AuthenticationType {
        return when (val name = decoder.decodeString()) {
            AuthenticationType.Password.name -> AuthenticationType.Password
            AuthenticationType.Recaptcha.name -> AuthenticationType.Recaptcha
            AuthenticationType.SSO.name -> AuthenticationType.SSO
            AuthenticationType.TermsOfService.name -> AuthenticationType.TermsOfService
            AuthenticationType.EmailIdentity.name -> AuthenticationType.EmailIdentity
            AuthenticationType.Msisdn.name -> AuthenticationType.Msisdn
            AuthenticationType.Dummy.name -> AuthenticationType.Dummy
            AuthenticationType.RegistrationToken.name -> AuthenticationType.RegistrationToken
            else -> AuthenticationType.Unknown(name)
        }
    }

    override fun serialize(encoder: Encoder, value: AuthenticationType) {
        encoder.encodeString(value.name)
    }
}

