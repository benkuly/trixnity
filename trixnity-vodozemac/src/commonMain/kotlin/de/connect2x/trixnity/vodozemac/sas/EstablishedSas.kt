package de.connect2x.trixnity.vodozemac.sas

import de.connect2x.trixnity.vodozemac.Curve25519PublicKey
import de.connect2x.trixnity.vodozemac.VodozemacException
import de.connect2x.trixnity.vodozemac.bindings.sas.EstablishedSasBindings
import de.connect2x.trixnity.vodozemac.toByteArray
import de.connect2x.trixnity.vodozemac.utils.*
import de.connect2x.trixnity.vodozemac.utils.managedReachableScope
import de.connect2x.trixnity.vodozemac.utils.withResult

class EstablishedSas internal constructor(ptr: NativePointer) :
    Managed(ptr, EstablishedSasBindings::free) {

    val ourPublicKey: Curve25519PublicKey
        get() = managedReachableScope {
            Curve25519PublicKey(EstablishedSasBindings.ourPublicKey(ptr))
        }

    val theirPublicKey: Curve25519PublicKey
        get() = managedReachableScope {
            Curve25519PublicKey(EstablishedSasBindings.theirPublicKey(ptr))
        }

    fun generateBytes(info: ByteArray): SasBytes = managedReachableScope {
        SasBytes(EstablishedSasBindings.bytes(ptr, info.toInterop(), info.size))
    }

    fun generateBytes(info: String): SasBytes = generateBytes(info.encodeToByteArray())

    fun calculateMac(input: ByteArray, info: ByteArray): Mac = managedReachableScope {
        Mac(
            EstablishedSasBindings.calculateMac(
                ptr,
                input.toInterop(),
                input.size,
                info.toInterop(),
                info.size,
            ))
    }

    fun calculateMac(input: String, info: String): Mac =
        calculateMac(input.encodeToByteArray(), info.encodeToByteArray())

    fun calculateMacInvalidBase64(input: ByteArray, info: ByteArray): String =
        managedReachableScope {
            val (ptr, size) =
                withResult(NativePointerArray(2)) {
                    EstablishedSasBindings.calculateMacInvalidBase64(
                        it, ptr, input.toInterop(), input.size, info.toInterop(), info.size)
                }

            ptr.toByteArray(size.intValue).decodeToString()
        }

    fun calculateMacInvalidBase64(input: String, info: String): String =
        calculateMacInvalidBase64(input.encodeToByteArray(), info.encodeToByteArray())

    fun verifyMac(input: ByteArray, info: ByteArray, tag: Mac) = managedReachableScope {
        val isCorrect =
            EstablishedSasBindings.verifyMac(
                ptr, input.toInterop(), input.size, info.toInterop(), info.size, tag.ptr)

        if (!isCorrect) throw VodozemacException("Invalid Mac")
    }

    fun verifyMac(input: String, info: String, tag: Mac) =
        verifyMac(input.encodeToByteArray(), info.encodeToByteArray(), tag)
}
