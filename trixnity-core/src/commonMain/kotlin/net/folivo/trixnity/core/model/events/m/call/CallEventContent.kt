package net.folivo.trixnity.core.model.events.m.call

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.Mentions
import net.folivo.trixnity.core.model.events.m.RelatesTo

/**
 * Matrix call event content
 *
 * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#voice-over-ip">matrix spec</a>
 */
sealed interface CallEventContent : MessageEventContent {
    val callId: String
    val version: Long

    /**
     * Matrix call invite content
     *
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#mcallinvite">matrix spec</a>
     */
    @Serializable
    data class Invite(
        @SerialName("version") override val version: Long,
        @SerialName("call_id") override val callId: String,
        @SerialName("lifetime") val lifetime: Long,
        @SerialName("offer") val offer: Offer,
    ) : CallEventContent {
        override val externalUrl: String? = null
        override val relatesTo: RelatesTo? = null
        override val mentions: Mentions? = null

        @Serializable
        enum class OfferType {
            @SerialName("offer") OFFER,
        }

        @Serializable
        data class Offer(
            @SerialName("sdp") val sdp: String,
            @SerialName("type") val type: OfferType,
        )
    }

    /**
     * Matrix call candidates content
     *
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#mcallcandidates">matrix spec</a>
     */
    @Serializable
    data class Candidates(
        @SerialName("version") override val version: Long,
        @SerialName("call_id") override val callId: String,
        @SerialName("candidates") val candidates: List<Candidate>,
    ) : CallEventContent {
        override val relatesTo: RelatesTo? = null
        override val mentions: Mentions? = null
        override val externalUrl: String? = null

        @Serializable
        data class Candidate(
            @SerialName("candidate") val candidate: String,
            @SerialName("sdpMLineIndex") val sdpMLineIndex: Long,
            @SerialName("sdpMid") val sdpMid: String,
        )
    }

    /**
     * Matrix call answer content
     *
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#mcallanswer">matrix spec</a>
     */
    @Serializable
    data class Answer(
        @SerialName("version") override val version: Long,
        @SerialName("call_id") override val callId: String,
        @SerialName("answer") val answer: Answer,
    ) : CallEventContent {
        override val relatesTo: RelatesTo? = null
        override val mentions: Mentions? = null
        override val externalUrl: String? = null

        @Serializable
        enum class AnswerType {
            @SerialName("answer") ANSWER,
        }

        @Serializable
        data class Answer(
            @SerialName("sdp") val sdp: String,
            @SerialName("type") val type: AnswerType,
        )
    }

    /**
     * Matrix call hangup content
     *
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#mcallhangup">matrix spec</a>
     */
    @Serializable
    data class Hangup(
        @SerialName("version") override val version: Long,
        @SerialName("call_id") override val callId: String,
        @SerialName("reason") val reason: Reason? = null,
    ) : CallEventContent {
        override val relatesTo: RelatesTo? = null
        override val mentions: Mentions? = null
        override val externalUrl: String? = null

        @Serializable
        enum class Reason {
            @SerialName("ice_failed") ICE_FAILED,
            @SerialName("invite_timeout") INVITE_TIMEOUT,
        }
    }
}
