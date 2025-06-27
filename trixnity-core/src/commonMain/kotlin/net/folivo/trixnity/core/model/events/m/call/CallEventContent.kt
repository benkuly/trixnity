package net.folivo.trixnity.core.model.events.m.call

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonPrimitive
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
    val version: String
    val partyId: String?

    /**
     * Matrix call invite content
     *
     * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#mcallinvite">matrix spec</a>
     */
    @Serializable
    data class Invite(
        @Serializable(with = VersionSerializer::class)
        @SerialName("version") override val version: String,
        @SerialName("call_id") override val callId: String,
        @SerialName("party_id") override val partyId: String? = null,
        @SerialName("invitee") val invitee: String? = null,
        @SerialName("lifetime") val lifetime: Long,
        @SerialName("offer") val offer: Offer,

        // Added in v1.10:
        @SerialName("sdp_stream_metadata") val sdpStreamMetadata: Map<String, StreamMetadata>?,
    ) : CallEventContent {
        override val externalUrl: String? = null
        override val relatesTo: RelatesTo? = null
        override val mentions: Mentions? = null

        @Serializable
        enum class OfferType {
            @SerialName("offer")
            OFFER,
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
     * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#mcallcandidates">matrix spec</a>
     */
    @Serializable
    data class Candidates(
        @Serializable(with = VersionSerializer::class)
        @SerialName("version") override val version: String,
        @SerialName("call_id") override val callId: String,
        @SerialName("party_id") override val partyId: String? = null,
        @SerialName("candidates") val candidates: List<Candidate>,
    ) : CallEventContent {
        override val relatesTo: RelatesTo? = null
        override val mentions: Mentions? = null
        override val externalUrl: String? = null

        @Serializable
        data class Candidate(
            @SerialName("candidate") val candidate: String,
            @SerialName("sdpMLineIndex") val sdpMLineIndex: Long? = null,
            @SerialName("sdpMid") val sdpMid: String? = null,
        )
    }

    /**
     * Matrix call answer content
     *
     * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#mcallanswer">matrix spec</a>
     */
    @Serializable
    data class Answer(
        @Serializable(with = VersionSerializer::class)
        @SerialName("version") override val version: String,
        @SerialName("call_id") override val callId: String,
        @SerialName("party_id") override val partyId: String? = null,
        @SerialName("answer") val answer: Answer,

        // Added in v1.10:

        @SerialName("sdp_stream_metadata") val sdpStreamMetadata: Map<String, StreamMetadata>? = null,
    ) : CallEventContent {
        override val relatesTo: RelatesTo? = null
        override val mentions: Mentions? = null
        override val externalUrl: String? = null

        @Serializable
        enum class AnswerType {
            @SerialName("answer")
            ANSWER,
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
     * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#mcallhangup">matrix spec</a>
     */
    @Serializable
    data class Hangup(
        @Serializable(with = VersionSerializer::class)
        @SerialName("version") override val version: String,
        @SerialName("call_id") override val callId: String,
        @SerialName("party_id") override val partyId: String? = null,
        @SerialName("reason") val reason: Reason? = null,
    ) : CallEventContent {
        override val relatesTo: RelatesTo? = null
        override val mentions: Mentions? = null
        override val externalUrl: String? = null

        @Serializable
        enum class Reason {
            @SerialName("ice_failed")
            ICE_FAILED,
            @SerialName("invite_timeout")
            INVITE_TIMEOUT,

            // Added in v1.7:
            @SerialName("ice_timeout")
            ICE_TIMEOUT,
            @SerialName("user_hangup")
            USER_HANGUP,
            @SerialName("user_media_failed")
            USER_MEDIA_FAILED,
            @SerialName("user_busy")
            USER_BUSY,
            @SerialName("unknown_error")
            UNKNOWN_ERROR,
        }
    }

    // Added in v1.7:

    /**
     * Matrix call negotiate content
     *
     * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#mcallnegotiate">matrix spec</a>
     */
    @Serializable
    data class Negotiate constructor(
        @OptIn(ExperimentalSerializationApi::class)
        @EncodeDefault
        @Serializable(with = VersionSerializer::class)
        @SerialName("version") override val version: String = "1",
        @SerialName("call_id") override val callId: String,
        @SerialName("party_id") override val partyId: String,
        @SerialName("description") val description: Description,
        @SerialName("lifetime") val lifetime: Long,

        // Added in v1.10:

        @SerialName("sdp_stream_metadata") val sdpStreamMetadata: Map<String, StreamMetadata>? = null
    ) : CallEventContent {
        override val relatesTo: RelatesTo? = null
        override val mentions: Mentions? = null
        override val externalUrl: String? = null

        @Serializable
        enum class DescriptionType {
            @SerialName("offer")
            OFFER,
            @SerialName("answer")
            ANSWER
        }

        @Serializable
        data class Description(
            @SerialName("sdp") val sdp: String,
            @SerialName("type") val type: DescriptionType,
        )
    }

    /**
     * Matrix call reject content
     *
     * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#mcallreject">matrix spec</a>
     */
    @Serializable
    data class Reject(
        @OptIn(ExperimentalSerializationApi::class)
        @EncodeDefault
        @Serializable(with = VersionSerializer::class)
        @SerialName("version") override val version: String = "1",
        @SerialName("call_id") override val callId: String,
        @SerialName("party_id") override val partyId: String,
    ) : CallEventContent {
        override val relatesTo: RelatesTo? = null
        override val mentions: Mentions? = null
        override val externalUrl: String? = null
    }

    /**
     * Matrix call select answer content
     *
     * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#mcallselect_answer">matrix spec</a>
     */
    @Serializable
    data class SelectAnswer(
        @OptIn(ExperimentalSerializationApi::class)
        @EncodeDefault
        @Serializable(with = VersionSerializer::class)
        @SerialName("version") override val version: String = "1",
        @SerialName("call_id") override val callId: String,
        @SerialName("party_id") override val partyId: String,
        @SerialName("selected_party_id") val selectedPartyId: String,
    ) : CallEventContent {
        override val relatesTo: RelatesTo? = null
        override val mentions: Mentions? = null
        override val externalUrl: String? = null
    }

    // Added in V1.11:

    /**
     * Matrix call SDP stream metadata changed event content
     *
     * @see <a href="https://spec.matrix.org/v1.11/client-server-api/#mcallsdp_stream_metadata_changed">matrix spec</a>
     */
    @Serializable
    data class SdpStreamMetadataChanged(
        @OptIn(ExperimentalSerializationApi::class)
        @EncodeDefault
        @Serializable(with = VersionSerializer::class)
        @SerialName("version") override val version: String = "1",
        @SerialName("call_id") override val callId: String,
        @SerialName("party_id") override val partyId: String,
        @SerialName("sdp_stream_metadata") val sdpStreamMetadata: Map<String, StreamMetadata>,
    ) : CallEventContent {
        override val relatesTo: RelatesTo? = null
        override val mentions: Mentions? = null
        override val externalUrl: String? = null
    }
}

internal object VersionSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("net.folivo.trixnity.core.model.call.Version")

    override fun deserialize(decoder: Decoder): String {
        val jsonDecoder = decoder as? JsonDecoder ?: throw IllegalStateException("Expected JsonDecoder")
        val element = jsonDecoder.decodeJsonElement().jsonPrimitive

        // All VoIP events have a version field. This is used to determine whether devices support this new version of
        // the protocol. For example, clients can use this field to know whether to expect an m.call.select_answer event
        // from their opponent. If clients see events with version other than 0 or "1" (including, for example, the
        // numeric value 1), they should treat these the same as if they had version == "1".
        // @see <a href="https://spec.matrix.org/v1.10/client-server-api/#voice-over-ip">matrix spec</a>
        return element.content
    }

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: String) {
        when (value) {
            "0" -> encoder.encodeLong(0)
            else -> encoder.encodeString(value)
        }
    }
}
