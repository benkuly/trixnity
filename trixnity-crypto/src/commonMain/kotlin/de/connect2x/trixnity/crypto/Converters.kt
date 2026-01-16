package de.connect2x.trixnity.crypto

import de.connect2x.trixnity.core.model.keys.ExportedSessionKeyValue
import de.connect2x.trixnity.core.model.keys.MacValue
import de.connect2x.trixnity.core.model.keys.MegolmMessageValue
import de.connect2x.trixnity.core.model.keys.OlmMessageValue
import de.connect2x.trixnity.core.model.keys.SessionKeyValue
import de.connect2x.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent.CiphertextInfo
import de.connect2x.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent.CiphertextInfo.OlmMessageType
import de.connect2x.trixnity.core.model.keys.Key
import de.connect2x.trixnity.core.model.keys.KeyValue
import de.connect2x.trixnity.core.model.keys.RoomKeyBackupSessionData.EncryptedRoomKeyBackupV1SessionData
import de.connect2x.trixnity.crypto.driver.keys.*
import de.connect2x.trixnity.crypto.driver.megolm.*
import de.connect2x.trixnity.crypto.driver.olm.Message
import de.connect2x.trixnity.crypto.driver.olm.MessageFactory
import de.connect2x.trixnity.crypto.driver.olm.NormalMessageFactory
import de.connect2x.trixnity.crypto.driver.olm.PreKeyMessageFactory
import de.connect2x.trixnity.crypto.driver.pkencryption.PkMessage
import de.connect2x.trixnity.crypto.driver.pkencryption.PkMessageFactory
import de.connect2x.trixnity.crypto.driver.sas.Mac
import de.connect2x.trixnity.utils.encodeUnpaddedBase64

fun ExportedSessionKeyValue.Companion.of(sessionKey: ExportedSessionKey): ExportedSessionKeyValue =
    ExportedSessionKeyValue(sessionKey.base64)

fun Key.Companion.of(keyId: String?, ed25519Key: Ed25519PublicKey): Key.Ed25519Key =
    Key.Ed25519Key(id = keyId, value = KeyValue.of(ed25519Key))

fun Key.Companion.of(keyId: String?, curve25519Key: Curve25519PublicKey): Key.Curve25519Key =
    Key.Curve25519Key(id = keyId, value = KeyValue.of(curve25519Key))

fun Key.Companion.of(ed25519Key: Ed25519PublicKey): Key.Ed25519Key =
    KeyValue.of(ed25519Key).let { Key.Ed25519Key(id = it.value, value = it) }

fun Key.Companion.of(curve25519Key: Curve25519PublicKey): Key.Curve25519Key =
    KeyValue.of(curve25519Key).let { Key.Curve25519Key(id = it.value, value = it) }

fun KeyValue.Companion.of(ed25519Key: Ed25519PublicKey): KeyValue.Ed25519KeyValue =
    KeyValue.Ed25519KeyValue(value = ed25519Key.base64)

fun KeyValue.Companion.of(curve25519Key: Curve25519PublicKey): KeyValue.Curve25519KeyValue =
    KeyValue.Curve25519KeyValue(value = curve25519Key.base64)

fun EncryptedRoomKeyBackupV1SessionData.Companion.of(message: PkMessage) = EncryptedRoomKeyBackupV1SessionData(
    ciphertext = message.ciphertext.encodeUnpaddedBase64(),
    mac = message.mac.encodeUnpaddedBase64(),
    ephemeral = KeyValue.of(message.ephemeralKey)
)

fun MacValue.Companion.of(mac: Mac): MacValue = MacValue(mac.base64)

fun SessionKeyValue.Companion.of(sessionKey: SessionKey): SessionKeyValue = SessionKeyValue(sessionKey.base64)

fun MegolmMessageValue.Companion.of(megolmMessage: MegolmMessage): MegolmMessageValue =
    MegolmMessageValue(megolmMessage.base64)

fun OlmMessageValue.Companion.of(olmMessage: Message): OlmMessageValue = OlmMessageValue(olmMessage.base64)

fun CiphertextInfo.Companion.of(olmMessage: Message): CiphertextInfo = CiphertextInfo(
    body = OlmMessageValue.of(olmMessage), type = when (olmMessage) {
        is Message.PreKey -> OlmMessageType.INITIAL_PRE_KEY
        is Message.Normal -> OlmMessageType.ORDINARY
    }
)

operator fun PkMessageFactory.invoke(data: EncryptedRoomKeyBackupV1SessionData): PkMessage =
    this(data.ciphertext, data.mac, data.ephemeral.value)

operator fun SessionKeyFactory.invoke(sessionKeyValue: SessionKeyValue): SessionKey = this(sessionKeyValue.value)

operator fun MegolmMessageFactory.invoke(megolmMessage: MegolmMessageValue): MegolmMessage = this(megolmMessage.value)

operator fun NormalMessageFactory.invoke(olmMessageValue: OlmMessageValue): Message.Normal = this(olmMessageValue.value)

operator fun PreKeyMessageFactory.invoke(olmMessageValue: OlmMessageValue): Message.PreKey = this(olmMessageValue.value)

operator fun MessageFactory.invoke(ciphertextInfo: CiphertextInfo): Message = when (ciphertextInfo.type) {
    OlmMessageType.INITIAL_PRE_KEY -> preKey(ciphertextInfo.body)
    OlmMessageType.ORDINARY -> normal(ciphertextInfo.body)
}

operator fun Ed25519PublicKeyFactory.invoke(key: Key): Ed25519PublicKey = this(key.value)

operator fun Ed25519PublicKeyFactory.invoke(key: KeyValue): Ed25519PublicKey = this(key.value)

operator fun Ed25519SignatureFactory.invoke(key: Key): Ed25519Signature = this(key.value)

operator fun Ed25519SignatureFactory.invoke(key: KeyValue): Ed25519Signature = this(key.value)

operator fun Ed25519SignatureFactory.invoke(key: KeyValue.Ed25519KeyValue): Ed25519Signature = this(key.value)

operator fun ExportedSessionKeyFactory.invoke(exportedSessionKeyValue: ExportedSessionKeyValue): ExportedSessionKey =
    this(exportedSessionKeyValue.value)

operator fun Ed25519PublicKeyFactory.invoke(ed25519Key: KeyValue.Ed25519KeyValue): Ed25519PublicKey =
    this(ed25519Key.value)

operator fun Curve25519PublicKeyFactory.invoke(curve25519Key: KeyValue.Curve25519KeyValue): Curve25519PublicKey =
    this(curve25519Key.value)

operator fun Curve25519PublicKeyFactory.invoke(curve25519Key: Key.Curve25519Key): Curve25519PublicKey =
    this(curve25519Key.value)

operator fun Curve25519PublicKeyFactory.invoke(curve25519Key: Key.SignedCurve25519Key): Curve25519PublicKey =
    this(curve25519Key.value.value)