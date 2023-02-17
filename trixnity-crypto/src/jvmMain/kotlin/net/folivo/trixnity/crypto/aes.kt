package net.folivo.trixnity.crypto

import kotlinx.coroutines.flow.flow
import net.folivo.trixnity.core.ByteArrayFlow
import net.folivo.trixnity.crypto.olm.DecryptionException
import java.security.Key
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

actual fun ByteArrayFlow.encryptAes256Ctr(
    key: ByteArray,
    initialisationVector: ByteArray
): ByteArrayFlow = flow {
    val cipher = Cipher.getInstance("AES/CTR/NoPadding")
    val keySpec: Key = SecretKeySpec(key, "AES")
    val ivSpec = IvParameterSpec(initialisationVector)

    cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)

    collect { input ->
        cipher.update(input)?.also { emit(it) }
    }
    cipher.doFinal()?.also { emit(it) }
}

actual fun ByteArrayFlow.decryptAes256Ctr(
    key: ByteArray,
    initialisationVector: ByteArray
): ByteArrayFlow = flow {
    val cipher: Cipher = Cipher.getInstance("AES/CTR/NoPadding")
    try {
        val keySpec: Key = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(initialisationVector)

        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        collect { input ->
            cipher.update(input)?.also { emit(it) }
        }
        cipher.doFinal()?.also { emit(it) }
    } catch (exception: Exception) {
        throw DecryptionException.OtherException(exception)
    }
}