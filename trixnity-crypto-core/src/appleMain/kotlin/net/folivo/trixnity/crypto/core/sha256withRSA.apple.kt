package net.folivo.trixnity.crypto.core

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKStringFromUtf8
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFErrorCopyDescription
import platform.CoreFoundation.CFErrorRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringGetCString
import platform.CoreFoundation.CFStringGetLength
import platform.CoreFoundation.CFStringGetMaximumSizeForEncoding
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.Security.SecKeyCreateSignature
import platform.Security.SecKeyCreateWithData
import platform.Security.SecKeyRef
import platform.Security.SecKeyVerifySignature
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPrivate
import platform.Security.kSecAttrKeyClassPublic
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeRSA
import platform.Security.kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA256
import platform.posix.memcpy
import kotlin.io.encoding.Base64

private fun CFStringRef.toKString(): String? {
    val length = CFStringGetLength(this)
    val maxSize = CFStringGetMaximumSizeForEncoding(length, kCFStringEncodingUTF8) + 1
    return memScoped {
        val buffer = allocArray<ByteVar>(maxSize.toInt())
        if (CFStringGetCString(this@toKString, buffer, maxSize, kCFStringEncodingUTF8)) {
            buffer.toKStringFromUtf8()
        } else {
            null
        }
    }
}

private fun ByteArray.toCFDataRef(): CFDataRef? = this.usePinned { pinned ->
    CFDataCreate(kCFAllocatorDefault, pinned.addressOf(0).reinterpret(), this@toCFDataRef.size.convert())
}

private fun String.toSecKeyRef(keyClass: CFStringRef): SecKeyRef = memScoped {
    fun String.toX509PublicKey(): ByteArray = Base64.decode(
        replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")
    )

    val rawKey = this@toSecKeyRef.toX509PublicKey()
    val cfData = rawKey.toCFDataRef() ?: error("Unable to create data object for key")
    val dictionary = CFDictionaryCreateMutable(kCFAllocatorDefault, 0, null, null)
        ?: error("Unable to create dictionary")

    val errorPtr = alloc<CPointerVarOf<CFErrorRef>>()
    CFDictionaryAddValue(dictionary, kSecAttrKeyType, kSecAttrKeyTypeRSA)
    CFDictionaryAddValue(dictionary, kSecAttrKeyClass, keyClass)
    val keyRef = SecKeyCreateWithData(cfData, dictionary, errorPtr.ptr) ?: run {
        val error = errorPtr.value
        val errorMessage = if (error != null) CFErrorCopyDescription(error)?.toKString() else "unknown error"
        CFRelease(error)
        throw Error("Key creation failed: $errorMessage")
    }

    CFRelease(dictionary)
    CFRelease(cfData)
    keyRef
}

actual suspend fun signSha256WithRSA(key: String, data: ByteArray): ByteArray = memScoped {
    val key = key.toSecKeyRef(requireNotNull(kSecAttrKeyClassPrivate))
    val cfData = data.toCFDataRef() ?: error("Unable to create data object for payload")

    val errorPtr = alloc<CPointerVarOf<CFErrorRef>>()
    val signature =
        SecKeyCreateSignature(key, kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA256, cfData, errorPtr.ptr) ?: run {
            val error = errorPtr.value
            val errorMessage = if (error != null) CFErrorCopyDescription(error)?.toKString() else "unknown error"
            CFRelease(error)
            CFRelease(cfData)
            CFRelease(key)
            throw Error("Signing failed: $errorMessage")
        }

    val length = CFDataGetLength(signature).toInt()
    val buffer = ByteArray(length.convert())
    buffer.usePinned { pinned ->
        memcpy(pinned.addressOf(0), CFDataGetBytePtr(signature), length.convert())
    }

    CFRelease(signature)
    CFRelease(key)
    return buffer
}

actual suspend fun verifySha256WithRSA(key: String, payload: ByteArray, signature: ByteArray): Boolean {
    val key = key.toSecKeyRef(requireNotNull(kSecAttrKeyClassPublic))
    val payloadData = payload.toCFDataRef() ?: error("Unable to create data object for payload")
    val signatureData = signature.toCFDataRef() ?: error("Unable to create data object for signature")
    val isValid = memScoped {
        val errorPtr = alloc<CPointerVarOf<CFErrorRef>>()
        val result = SecKeyVerifySignature(
            key,
            kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA256,
            payloadData,
            signatureData,
            errorPtr.ptr
        )
        if (errorPtr.value != null) {
            val error = errorPtr.value
            val errorMessage = if (error != null) CFErrorCopyDescription(error)?.toKString() else "unknown error"
            CFRelease(error)
            CFRelease(payloadData)
            CFRelease(signatureData)
            CFRelease(key)
            throw Error("Verification failed: $errorMessage")
        }
        result
    }

    CFRelease(payloadData)
    CFRelease(signatureData)
    CFRelease(key)
    return isValid
}
