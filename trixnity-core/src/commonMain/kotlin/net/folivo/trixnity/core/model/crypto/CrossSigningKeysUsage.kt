package net.folivo.trixnity.core.model.crypto

import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.serialization.crypto.CrossSigningKeyUsageSerializer

@Serializable(with = CrossSigningKeyUsageSerializer::class)
sealed class CrossSigningKeysUsage {
    abstract val name: String

    object MasterKey : CrossSigningKeysUsage() {
        override val name = "master"
    }

    object SelfSigningKey : CrossSigningKeysUsage() {
        override val name = "self_signing"
    }

    object UserSigningKey : CrossSigningKeysUsage() {
        override val name = "user_signing"
    }

    data class UnknownCrossSigningKeyUsage(
        override val name: String
    ) : CrossSigningKeysUsage()
}