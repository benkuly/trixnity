package de.connect2x.trixnity.crypto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.MSC3814

@Serializable
enum class SecretType(val id: String, val cacheable: Boolean) {
    @SerialName("m.cross_signing.master")
    M_CROSS_SIGNING_MASTER(id = "m.cross_signing.master", cacheable = false),

    @SerialName("m.cross_signing.self_signing")
    M_CROSS_SIGNING_SELF_SIGNING(id = "m.cross_signing.self_signing", cacheable = true),

    @SerialName("m.cross_signing.user_signing")
    M_CROSS_SIGNING_USER_SIGNING(id = "m.cross_signing.user_signing", cacheable = true),

    @SerialName("m.megolm_backup.v1")
    M_MEGOLM_BACKUP_V1(id = "m.megolm_backup.v1", cacheable = true),

    @MSC3814
    @SerialName("org.matrix.msc3814")
    M_DEHYDRATED_DEVICE(id = "org.matrix.msc3814", cacheable = true);

    companion object {
        fun ofId(id: String): SecretType? = entries.find { it.id == id }
    }
}
