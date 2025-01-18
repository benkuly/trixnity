@file:Suppress(
    "INTERFACE_WITH_SUPERCLASS",
    "OVERRIDING_FINAL_MEMBER",
    "RETURN_TYPE_MISMATCH_ON_OVERRIDE",
    "CONFLICTING_OVERLOADS"
)
@file:JsQualifier("Olm")
@file:JsModule("@matrix-org/olm")
@file:JsNonModule

package net.folivo.trixnity.olm

import js.typedarrays.Uint8Array

external class Account {
    fun free()
    fun create()
    fun identity_keys(): String
    fun sign(message: String): String
    fun one_time_keys(): String
    fun mark_keys_as_published()
    fun max_number_of_one_time_keys(): Number
    fun generate_one_time_keys(number_of_keys: Number)
    fun remove_one_time_keys(session: Session)
    fun generate_fallback_key()
    fun forget_old_fallback_key()
    fun unpublished_fallback_key(): String
    fun pickle(key: String): String
    fun unpickle(key: String, pickle: String)
}

external class Session {
    fun free()
    fun pickle(key: String): String
    fun unpickle(key: String, pickle: String)
    fun create_outbound(account: Account, their_identity_key: String, their_one_time_key: String)
    fun create_inbound(account: Account, one_time_key_message: String)
    fun create_inbound_from(account: Account, identity_key: String, one_time_key_message: String)
    fun session_id(): String
    fun has_received_message(): Boolean
    fun matches_inbound(one_time_key_message: String): Boolean
    fun matches_inbound_from(identity_key: String, one_time_key_message: String): Boolean
    fun encrypt(plainText: String): Message
    fun decrypt(message_type: Number, message: String): String
    fun describe(): String
}

external class Utility {
    fun free()
    fun sha256(input: Uint8Array<*>): String
    fun sha256(input: String): String
    fun ed25519_verify(key: String, message: String, signature: String)
}

external class InboundGroupSession {
    fun free()
    fun pickle(key: String): String
    fun unpickle(key: String, pickle: String)
    fun create(session_key: String): String
    fun import_session(session_key: String): String
    fun decrypt(message: String): InboundGroupMessage
    fun session_id(): String
    fun first_known_index(): Number
    fun export_session(message_index: Number): String
}

external class OutboundGroupSession {
    fun free()
    fun pickle(key: String): String
    fun unpickle(key: String, pickle: String)
    fun create()
    fun encrypt(plainText: String): String
    fun session_id(): String
    fun session_key(): String
    fun message_index(): Number
}

external class PkEncryption {
    fun free()
    fun set_recipient_key(key: String)
    fun encrypt(plainText: String): PkMessage
}

external class PkDecryption {
    fun free()
    fun init_with_private_key(key: Uint8Array<*>): String
    fun generate_key(): String
    fun get_private_key(): Uint8Array<*>
    fun pickle(key: String): String
    fun unpickle(key: String, pickle: String): String
    fun decrypt(ephemeral_key: String, mac: String, cipherText: String): String
}

external class PkSigning {
    fun free()
    fun init_with_seed(seed: Uint8Array<*>): String
    fun generate_seed(): Uint8Array<*>
    fun sign(message: String): String
}

external class SAS {
    fun free()
    fun get_pubkey(): String
    fun set_their_key(their_key: String)
    fun generate_bytes(info: String, length: Number): Uint8Array<*>
    fun calculate_mac(input: String, info: String): String
    fun calculate_mac_fixed_base64(input: String, info: String): String
}

external class Message {
    val type: Number
    val body: String
}

external class InboundGroupMessage {
    val plaintext: String
    val message_index: Number
}

external class PkMessage {
    val ciphertext: String
    val mac: String
    val ephemeral: String
}

external fun get_library_version(): List<Number> /* JsTuple<Number, Number, Number> */

external var PRIVATE_KEY_LENGTH: Number