package de.connect2x.trixnity.vodozemac.sas

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import de.connect2x.trixnity.vodozemac.*
import de.connect2x.trixnity.vodozemac.bindings.sas.SasBindings
import de.connect2x.trixnity.vodozemac.utils.Managed
import de.connect2x.trixnity.vodozemac.utils.NativePointer
import de.connect2x.trixnity.vodozemac.utils.managedReachableScope
import de.connect2x.trixnity.vodozemac.utils.nullPtr

@OptIn(ExperimentalAtomicApi::class)
class Sas internal constructor(ptr: NativePointer) : Managed(ptr, SasBindings::free) {

    private val keyUsed = AtomicBoolean(false)

    constructor() : this(SasBindings.new())

    // TODO: figure out a cleaner way (key has to be moved in rust...)
    //       We don't need to check whether the key was used because this is fetched at construction
    val publicKey: Curve25519PublicKey = managedReachableScope {
        Curve25519PublicKey(SasBindings.publicKey(ptr))
    }

    fun diffieHellman(theirPublicKey: Curve25519PublicKey): EstablishedSas = managedReachableScope {
        if (keyUsed.exchange(true)) throw VodozemacException("key already exchanged")

        val establishedSasPtr = SasBindings.diffieHellman(ptr, theirPublicKey.ptr)

        if (establishedSasPtr == nullPtr) throw VodozemacException("NonContributoryKey")

        EstablishedSas(establishedSasPtr)
    }
}
