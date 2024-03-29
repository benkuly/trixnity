package net.folivo.trixnity.crypto.core

import kotlinx.coroutines.flow.flow
import net.folivo.trixnity.utils.ByteArrayFlow
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

    filterNotEmpty().collect { input ->
        cipher.update(input)?.also { emit(it) }
    }
    cipher.doFinal()?.also { emit(it) }
}.filterNotEmpty()

actual fun ByteArrayFlow.decryptAes256Ctr(
    key: ByteArray,
    initialisationVector: ByteArray
): ByteArrayFlow = flow {
    val cipher: Cipher = Cipher.getInstance("AES/CTR/NoPadding")
    try {
        val keySpec: Key = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(initialisationVector)

        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        filterNotEmpty().collect { input ->
            cipher.update(input)?.also { emit(it) }
        }
        cipher.doFinal()?.also { emit(it) }
    } catch (exception: Exception) {
        throw AesDecryptionException(exception)
    }
}.filterNotEmpty()