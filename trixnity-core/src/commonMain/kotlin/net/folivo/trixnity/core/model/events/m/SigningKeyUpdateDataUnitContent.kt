package net.folivo.trixnity.core.model.events.m

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.EphemeralDataUnitContent
import net.folivo.trixnity.core.model.keys.SignedCrossSigningKeys

/**
 * @see <a href="https://spec.matrix.org/v1.10/server-server-api/#end-to-end-encryption">matrix spec</a>
 */
@Serializable
data class SigningKeyUpdateDataUnitContent(
    @SerialName("master_key")
    val masterKey: SignedCrossSigningKeys? = null,
    @SerialName("self_signing_key")
    val selfSigningKey: SignedCrossSigningKeys? = null,
    @SerialName("user_id")
    val userId: UserId,
) : EphemeralDataUnitContent