package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.StateEventContent

/**
 * @see <a href="https://spec.matrix.org/v1.7/client-server-api/#mroomserver_acl">matrix spec</a>
 */
@Serializable
data class ServerACLEventContent(
    @SerialName("allow")
    val allow: Set<String> = setOf(),
    @SerialName("allow_ip_literals")
    val allowIpLiterals: Boolean = true,
    @SerialName("deny")
    val deny: Set<String> = setOf(),
    @SerialName("external_url")
    override val externalUrl: String? = null,
) : StateEventContent