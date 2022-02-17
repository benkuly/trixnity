package net.folivo.trixnity.clientserverapi.model

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("errcode")
@Serializable
sealed class ErrorResponse {
    abstract val error: String?

    /**
     * Forbidden access, e.g. joining a room without permission, failed login.
     */
    @Serializable
    @SerialName("M_FORBIDDEN")
    data class Forbidden(override val error: String? = null) : net.folivo.trixnity.clientserverapi.model.ErrorResponse()

    /**
     * The access token specified was not recognised.
     *
     * An additional response parameter, soft_logout, might be present on the response for 401 HTTP status codes.
     * See the [soft logout](https://matrix.org/docs/spec/client_server/latest#soft-logout) section for more information.
     */
    @Serializable
    @SerialName("M_UNKNOWN_TOKEN")
    data class UnknownToken(
        override val error: String? = null,
        @SerialName("soft_logout") val softLogout: Boolean = false
    ) : net.folivo.trixnity.clientserverapi.model.ErrorResponse()

    /**
     * No access token was specified for the request.
     */
    @Serializable
    @SerialName("M_MISSING_TOKEN")
    data class MissingToken(override val error: String? = null) :
        net.folivo.trixnity.clientserverapi.model.ErrorResponse()

    /**
     * Request contained valid JSON, but it was malformed in some way, e.g. missing required keys, invalid values for keys.
     */
    @Serializable
    @SerialName("M_BAD_JSON")
    data class BadJson(override val error: String? = null) : net.folivo.trixnity.clientserverapi.model.ErrorResponse()

    /**
     * Request did not contain valid JSON.
     */
    @Serializable
    @SerialName("M_NOT_JSON")
    data class NotJson(override val error: String? = null) : net.folivo.trixnity.clientserverapi.model.ErrorResponse()

    /**
     * No resource was found for this request.
     */
    @Serializable
    @SerialName("M_NOT_FOUND")
    data class NotFound(override val error: String? = null) : net.folivo.trixnity.clientserverapi.model.ErrorResponse()

    /**
     * Too many requests have been sent in a short period of time. Wait a while then try again.
     */
    @Serializable
    @SerialName("M_LIMIT_EXCEEDED")
    data class LimitExceeded(
        override val error: String? = null,
        @SerialName("retry_after_ms") val retryAfterMillis: Long
    ) : net.folivo.trixnity.clientserverapi.model.ErrorResponse()

    /**
     * An unknown error has occurred.
     */
    @Serializable
    @SerialName("M_UNKNOWN")
    data class Unknown(override val error: String? = null) : net.folivo.trixnity.clientserverapi.model.ErrorResponse()

    /**
     * The server did not understand the request.
     */
    @Serializable
    @SerialName("M_UNRECOGNIZED")
    data class Unrecognized(override val error: String? = null) :
        net.folivo.trixnity.clientserverapi.model.ErrorResponse()

    /**
     * The request was not correctly authorized. Usually due to login failures.
     */
    @Serializable
    @SerialName("M_UNAUTHORIZED")
    data class Unauthorized(override val error: String? = null) :
        net.folivo.trixnity.clientserverapi.model.ErrorResponse()

    /**
     * The user ID associated with the request has been deactivated. Typically for endpoints that prove authentication,
     * such as `/login`.
     */
    @Serializable
    @SerialName("M_USER_DEACTIVATED")
    data class UserDeactivated(override val error: String? = null) :
        net.folivo.trixnity.clientserverapi.model.ErrorResponse()

    /**
     * Encountered when trying to register a user ID which has been taken.
     */
    @Serializable
    @SerialName("M_USER_IN_USE")
    data class UserInUse(override val error: String? = null) : net.folivo.trixnity.clientserverapi.model.ErrorResponse()

    /**
     * Encountered when trying to register a user ID which is not valid.
     */
    @Serializable
    @SerialName("M_INVALID_USERNAME")
    data class InvalidUsername(override val error: String? = null) :
        net.folivo.trixnity.clientserverapi.model.ErrorResponse()

    /**
     * Sent when the room alias given to the createRoom API is already in use.
     */
    @Serializable
    @SerialName("M_ROOM_IN_USE")
    data class RoomInUse(override val error: String? = null) : net.folivo.trixnity.clientserverapi.model.ErrorResponse()

    /**
     * Sent when the initial state given to the createRoom API is invalid.
     */
    @Serializable
    @SerialName("M_INVALID_ROOM_STATE")
    data class InvalidRoomState(override val error: String? = null) :
        net.folivo.trixnity.clientserverapi.model.ErrorResponse()

    /**
     * Sent when a threepid given to an API cannot be used because the same threepid is already in use.
     */
    @Serializable
    @SerialName("M_THREEPID_IN_USE")
    data class ThreePIdInUse(override val error: String? = null) :
        net.folivo.trixnity.clientserverapi.model.ErrorResponse()

    /**
     * Sent when a threepid given to an API cannot be used because no record matching the threepid was found.
     */
    @Serializable
    @SerialName("M_THREEPID_NOT_FOUND")
    data class ThreePIdNotFound(override val error: String? = null) :
        net.folivo.trixnity.clientserverapi.model.ErrorResponse()

    /**
     * Authentication could not be performed on the third party identifier.
     */
    @Serializable
    @SerialName("M_THREEPID_AUTH_FAILED")
    data class ThreePIdAuthFailed(override val error: String? = null) :
        net.folivo.trixnity.clientserverapi.model.ErrorResponse()

    /**
     * The server does not permit this third party identifier.
     * This may happen if the server only permits, for example, email addresses from a particular domain.
     */
    @Serializable
    @SerialName("M_THREEPID_DENIED")
    data class ThreePIdDenied(override val error: String? = null) :
        net.folivo.trixnity.clientserverapi.model.ErrorResponse()

    /**
     * The client's request used a third party server, eg. identity server, that this server does not trust.
     */
    @Serializable
    @SerialName("M_SERVER_NOT_TRUSTED")
    data class ServerNotTrusted(override val error: String? = null) :
        net.folivo.trixnity.clientserverapi.model.ErrorResponse()

    /**
     * The client's request to create a room used a room version that the server does not support.
     */
    @Serializable
    @SerialName("M_UNSUPPORTED_ROOM_VERSION")
    data class UnsupportedRoomVersion(override val error: String? = null) :
        net.folivo.trixnity.clientserverapi.model.ErrorResponse()

    /**
     * The client attempted to join a room that has a version the server does not support. Inspect the `room_version`
     * property of the error response for the room's version.
     */
    @Serializable
    @SerialName("M_INCOMPATIBLE_ROOM_VERSION")
    data class IncompatibleRoomVersion(
        override val error: String? = null,
        @SerialName("room_version") val roomVersion: String? = null
    ) : net.folivo.trixnity.clientserverapi.model.ErrorResponse()

    /**
     * The state change requested cannot be performed, such as attempting to unban a user who is not banned.
     */
    @Serializable
    @SerialName("M_BAD_STATE")
    data class BadState(override val error: String? = null) : net.folivo.trixnity.clientserverapi.model.ErrorResponse()

    /**
     * The room or resource does not permit guests to access it.
     */
    @Serializable
    @SerialName("M_GUEST_ACCESS_FORBIDDEN")
    data class GuestAccessForbidden(override val error: String? = null) :
        net.folivo.trixnity.clientserverapi.model.ErrorResponse()

    /**
     * A Captcha is required to complete the request.
     */
    @Serializable
    @SerialName("M_CAPTCHA_NEEDED")
    data class CaptchaNeeded(override val error: String? = null) :
        net.folivo.trixnity.clientserverapi.model.ErrorResponse()

    /**
     * The Captcha provided did not match what was expected.
     */
    @Serializable
    @SerialName("M_CAPTCHA_INVALID")
    data class CaptchaInvalid(override val error: String? = null) :
        net.folivo.trixnity.clientserverapi.model.ErrorResponse()

    /**
     * A required parameter was missing from the request.
     */
    @Serializable
    @SerialName("M_MISSING_PARAM")
    data class MissingParam(override val error: String? = null) :
        net.folivo.trixnity.clientserverapi.model.ErrorResponse()

    /**
     * A parameter that was specified has the wrong value. For example, the server expected an integer and instead
     * received a string.
     */
    @Serializable
    @SerialName("M_INVALID_PARAM")
    data class InvalidParam(override val error: String? = null) :
        net.folivo.trixnity.clientserverapi.model.ErrorResponse()

    /**
     * The request or entity was too large.
     */
    @Serializable
    @SerialName("M_TOO_LARGE")
    data class TooLarge(override val error: String? = null) : net.folivo.trixnity.clientserverapi.model.ErrorResponse()

    /**
     * The resource being requested is reserved by an application service, or the application service making the request
     * has not created the resource.
     */
    @Serializable
    @SerialName("M_EXCLUSIVE")
    data class Exclusive(override val error: String? = null) : net.folivo.trixnity.clientserverapi.model.ErrorResponse()

    /**
     * The request cannot be completed because the homeserver has reached a resource limit imposed on it. For example,
     * a homeserver held in a shared hosting environment may reach a resource limit if it starts using too much memory
     * or disk space.
     *
     * The error MUST have an `admin_contact` field to provide the user receiving the error a place to reach out to.
     * Typically, this error will appear on routes which attempt to modify state (eg: sending messages, account data, etc)
     * and not routes which only read state (eg: `/sync`, get account data, etc).
     */
    @Serializable
    @SerialName("M_RESOURCE_LIMIT_EXCEEDED")
    data class ResourceLimitExceeded(
        override val error: String? = null,
        @SerialName("admin_contact") val adminContact: String? = null
    ) : net.folivo.trixnity.clientserverapi.model.ErrorResponse()

    /**
     * The user is unable to reject an invite to join the server notices room.
     * See the [Server Notices](https://matrix.org/docs/spec/client_server/latest#server-notices) module for more information.
     */
    @Serializable
    @SerialName("M_CANNOT_LEAVE_SERVER_NOTICE_ROOM")
    data class CannotLeaveServerNoticeRoom(override val error: String? = null) :
        net.folivo.trixnity.clientserverapi.model.ErrorResponse()

    @Serializable
    @SerialName("M_WRONG_ROOM_KEYS_VERSION")
    data class WrongRoomKeysVersion(
        override val error: String? = null,
        @SerialName("current_version") val currentVersion: String? = null
    ) : net.folivo.trixnity.clientserverapi.model.ErrorResponse()

    /**
     * All ErrorResponses, that we cannot map to a subtype of ErrorResponse.
     */
    @Serializable
    data class CustomErrorResponse(
        @SerialName("errcode") val errorCode: String,
        override val error: String? = null
    ) : net.folivo.trixnity.clientserverapi.model.ErrorResponse()
}

object ErrorResponseSerializer : KSerializer<net.folivo.trixnity.clientserverapi.model.ErrorResponse> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ErrorResponseSerializer")

    override fun deserialize(decoder: Decoder): net.folivo.trixnity.clientserverapi.model.ErrorResponse {
        require(decoder is JsonDecoder)
        val jsonElement = decoder.decodeJsonElement()
        return try {
            decoder.json.decodeFromJsonElement(jsonElement)
        } catch (error: SerializationException) {
            decoder.json.decodeFromJsonElement<ErrorResponse.CustomErrorResponse>(
                jsonElement
            )
        }
    }

    override fun serialize(
        encoder: Encoder,
        value: ErrorResponse
    ) {
        require(encoder is JsonEncoder)
        val jsonElement = when (value) {
            is ErrorResponse.CustomErrorResponse -> encoder.json.encodeToJsonElement(
                value
            )
            else -> encoder.json.encodeToJsonElement(value)
        }
        return encoder.encodeJsonElement(jsonElement)
    }
}