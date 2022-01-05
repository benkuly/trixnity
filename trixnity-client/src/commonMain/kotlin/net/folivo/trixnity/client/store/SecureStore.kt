package net.folivo.trixnity.client.store

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// It is important, that these values are stored in secure location!
data class SecureStore(
    val olmPickleKey: String = "",
    val secrets: MutableStateFlow<Map<AllowedSecretType, StoredSecret>> = MutableStateFlow(mapOf())
) {
    @Serializable
    enum class AllowedSecretType(val id: String) {
        @SerialName("m.cross_signing.self_signing")
        M_CROSS_SIGNING_SELF_SIGNING(id = "m.cross_signing.self_signing"),

        @SerialName("m.cross_signing.user_signing")
        M_CROSS_SIGNING_USER_SIGNING(id = "m.cross_signing.user_signing"),

        @SerialName("m.megolm_backup.v1")
        M_MEGOLM_BACKUP_V1(id = "m.megolm_backup.v1");

        companion object {
            fun ofId(id: String): AllowedSecretType? {
                return when (id) {
                    M_CROSS_SIGNING_SELF_SIGNING.id -> M_CROSS_SIGNING_SELF_SIGNING
                    M_CROSS_SIGNING_USER_SIGNING.id -> M_CROSS_SIGNING_USER_SIGNING
                    M_MEGOLM_BACKUP_V1.id -> M_MEGOLM_BACKUP_V1
                    else -> null
                }
            }
        }
    }
}