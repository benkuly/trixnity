package net.folivo.trixnity.core.model.events.m.key.verification

import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.serialization.m.key.verification.VerificationMethodSerializer

@Serializable(with = VerificationMethodSerializer::class)
sealed class VerificationMethod {
    abstract val value: String

    object Sas : VerificationMethod() {
        override val value = "m.sas.v1"
    }

    data class Unknown(override val value: String) : VerificationMethod()
}