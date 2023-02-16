package net.folivo.trixnity.crypto

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.core.ByteArrayFlow
import net.folivo.trixnity.core.toByteArrayFlow
import net.folivo.trixnity.crypto.olm.DecryptionException
import java.security.Key
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@OptIn(FlowPreview::class)
actual fun ByteArrayFlow.encryptAes256Ctr(
    key: ByteArray,
    initialisationVector: ByteArray
): ByteArrayFlow = flow {
    val cipher = Cipher.getInstance("AES/CTR/NoPadding")
    val keySpec: Key = SecretKeySpec(key, "AES")
    val ivSpec = IvParameterSpec(initialisationVector)

    cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)

    emitAll(
        mapNotNull { input ->
            cipher.update(input)?.toByteArrayFlow()
        }.flattenConcat().onCompletion {
            cipher.doFinal()?.also { emitAll(it.toByteArrayFlow()) }
        }
    )
}

@OptIn(FlowPreview::class)
actual fun ByteArrayFlow.decryptAes256Ctr(
    key: ByteArray,
    initialisationVector: ByteArray
): ByteArrayFlow = flow {
    val cipher: Cipher = Cipher.getInstance("AES/CTR/NoPadding")
    try {
        val keySpec: Key = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(initialisationVector)

        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        emitAll(
            mapNotNull { input ->
                cipher.update(input)?.toByteArrayFlow()
            }.flattenConcat().onCompletion {
                cipher.doFinal()?.also { emitAll(it.toByteArrayFlow()) }
            }
        )
    } catch (exception: Exception) {
        throw DecryptionException.OtherException(exception)
    }
}