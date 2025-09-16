package net.folivo.trixnity.crypto.core

import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.cValuesOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.refTo
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFErrorCopyDescription
import platform.CoreFoundation.CFErrorRef
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFNumberIntType
import platform.Security.SecKeyCreateSignature
import platform.Security.SecKeyCreateWithData
import platform.Security.SecKeyVerifySignature
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPrivate
import platform.Security.kSecAttrKeyClassPublic
import platform.Security.kSecAttrKeySizeInBits
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeRSA
import platform.Security.kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA256
import platform.posix.memcpy

actual suspend fun signSha256WithRSA(key: String, data: ByteArray): ByteArray = memScoped {
    val rawKey = key.encodeToByteArray()
    val key = rawKey.usePinned { pinned ->
        val cfData = CFDataCreate(kCFAllocatorDefault, pinned.addressOf(0).reinterpret(), key.size.convert())
            ?: error("Unable to create data object for key")
        val dictionary = CFDictionaryCreateMutable(kCFAllocatorDefault, 0, null, null)
            ?: error("Unable to create dictionary")

        val keySize = CFNumberCreate(kCFAllocatorDefault, kCFNumberIntType, cValuesOf(key.size * 8))
            ?: error("Unable to create int for key length")
        CFDictionaryAddValue(dictionary, kSecAttrKeySizeInBits, keySize)
        CFDictionaryAddValue(dictionary, kSecAttrKeyType, kSecAttrKeyTypeRSA)
        CFDictionaryAddValue(dictionary, kSecAttrKeyClass, kSecAttrKeyClassPrivate)
        SecKeyCreateWithData(cfData, dictionary, null) ?: error("Unable to create private key")
    }

    val signature = data.usePinned { pinned ->
        val cfData = CFDataCreate(kCFAllocatorDefault, pinned.addressOf(0).reinterpret(), data.size.convert())
            ?: error("Unable to create data object for payload")
        val errorPtr = alloc<CPointerVarOf<CFErrorRef>>()
        SecKeyCreateSignature(key, kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA256, cfData, errorPtr.ptr) ?: run {
            val error = errorPtr.value
            val errorMessage = if (error != null) CFErrorCopyDescription(error)?.toString() else "unknown error"
            throw Error("Signing failed: $errorMessage")
        }
    }

    val length = CFDataGetLength(signature).toInt()
    val buffer = ByteArray(length.convert())
    buffer.usePinned { pinned ->
        memcpy(pinned.addressOf(0), CFDataGetBytePtr(signature), length.convert())
    }
    return buffer
}

actual suspend fun verifySha256WithRSA(key: String, payload: ByteArray, signature: ByteArray): Boolean {
    val rawKey = key.encodeToByteArray()
    val key = rawKey.usePinned { pinned ->
        val cfData = CFDataCreate(kCFAllocatorDefault, pinned.addressOf(0).reinterpret(), key.size.convert())
            ?: error("Unable to create data object for key")
        val dictionary = CFDictionaryCreateMutable(kCFAllocatorDefault, 0, null, null)
            ?: error("Unable to create dictionary")

        val keySize = CFNumberCreate(kCFAllocatorDefault, kCFNumberIntType, cValuesOf(key.size * 8))
            ?: error("Unable to create int for key length")
        CFDictionaryAddValue(dictionary, kSecAttrKeySizeInBits, keySize)
        CFDictionaryAddValue(dictionary, kSecAttrKeyType, kSecAttrKeyTypeRSA)
        CFDictionaryAddValue(dictionary, kSecAttrKeyClass, kSecAttrKeyClassPublic)
        SecKeyCreateWithData(cfData, dictionary, null) ?: error("Unable to create public key")
    }

    val payloadData = payload.usePinned { pinned ->
        CFDataCreate(kCFAllocatorDefault, pinned.addressOf(0).reinterpret(), payload.size.convert())
            ?: error("Unable to create data object for payload")
    }
    val signatureData = signature.usePinned { pinned ->
        CFDataCreate(kCFAllocatorDefault, pinned.addressOf(0).reinterpret(), signature.size.convert())
            ?: error("Unable to create data object for signature")
    }

    val isValid = memScoped {
        val errorPtr = alloc<CPointerVarOf<CFErrorRef>>()
        val result = SecKeyVerifySignature(
            key,
            kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA256,
            payloadData,
            signatureData,
            errorPtr.ptr
        )
        if (errorPtr.value != null) throw Error("Verification failed")
        result
    }

    return isValid
}
