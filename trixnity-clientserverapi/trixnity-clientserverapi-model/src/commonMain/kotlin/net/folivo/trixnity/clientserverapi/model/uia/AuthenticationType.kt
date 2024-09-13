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

    @Serializable(with = PasswordAuthenticationSerializer::class)
    data object Password : AuthenticationType {
        override val name = "m.login.password"
    }

    @Serializable(with = RecaptchaAuthenticationSerializer::class)
    data object Recaptcha : AuthenticationType {
        override val name = "m.login.recaptcha"
    }

    @Serializable(with = SSOAuthenticationSerializer::class)
    data object SSO : AuthenticationType {
        override val name = "m.login.sso"
    }

    @Serializable(with = TermsOfServiceAuthenticationSerializer::class)
    data object TermsOfService : AuthenticationType {
        override val name = "m.login.terms"
    }

    @Serializable(with = EmailIdentityAuthenticationSerializer::class)
    data object EmailIdentity : AuthenticationType {
        override val name = "m.login.email.identity"
    }

    @Serializable(with = MsisdnAuthenticationSerializer::class)
    data object Msisdn : AuthenticationType {
        override val name = "m.login.msisdn"
    }

    @Serializable(with = DummyAuthenticationSerializer::class)
    data object Dummy : AuthenticationType {
        override val name = "m.login.dummy"
    }

    @Serializable(with = RegistrationTokenAuthenticationSerializer::class)
    data object RegistrationToken : AuthenticationType {
        override val name = "m.login.registration_token"
    }

    @Serializable(with = UnknownAuthenticationSerializer::class)
    data class Unknown(
        override val name: String
    ) : AuthenticationType

    object PasswordAuthenticationSerializer : KSerializer<Password> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("PasswordAuthenticationSerializer", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): Password {
            return Password
        }

        override fun serialize(encoder: Encoder, value: Password) {
            encoder.encodeString(value.name)
        }
    }

    object RecaptchaAuthenticationSerializer : KSerializer<Recaptcha> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("RecaptchaAuthenticationSerializer", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): Recaptcha {
            return Recaptcha
        }

        override fun serialize(encoder: Encoder, value: Recaptcha) {
            encoder.encodeString(value.name)
        }
    }

    object SSOAuthenticationSerializer : KSerializer<SSO> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("SSOAuthenticationSerializer", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): SSO {
            return SSO
        }

        override fun serialize(encoder: Encoder, value: SSO) {
            encoder.encodeString(value.name)
        }
    }

    object TermsOfServiceAuthenticationSerializer : KSerializer<TermsOfService> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("TermsOfServiceAuthenticationSerializer", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): TermsOfService {
            return TermsOfService
        }

        override fun serialize(encoder: Encoder, value: TermsOfService) {
            encoder.encodeString(value.name)
        }
    }

    object EmailIdentityAuthenticationSerializer : KSerializer<EmailIdentity> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("EmailIdentityAuthenticationSerializer", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): EmailIdentity {
            return EmailIdentity
        }

        override fun serialize(encoder: Encoder, value: EmailIdentity) {
            encoder.encodeString(value.name)
        }
    }

    object MsisdnAuthenticationSerializer : KSerializer<Msisdn> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("MsisdnAuthenticationSerializer", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): Msisdn {
            return Msisdn
        }

        override fun serialize(encoder: Encoder, value: Msisdn) {
            encoder.encodeString(value.name)
        }
    }

    object DummyAuthenticationSerializer : KSerializer<Dummy> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("DummyAuthenticationSerializer", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): Dummy {
            return Dummy
        }

        override fun serialize(encoder: Encoder, value: Dummy) {
            encoder.encodeString(value.name)
        }
    }

    object RegistrationTokenAuthenticationSerializer : KSerializer<RegistrationToken> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("RegistrationTokenAuthenticationSerializer", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): RegistrationToken {
            return RegistrationToken
        }

        override fun serialize(encoder: Encoder, value: RegistrationToken) {
            encoder.encodeString(value.name)
        }
    }

    object UnknownAuthenticationSerializer : KSerializer<Unknown> {
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

