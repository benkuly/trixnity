@file:OptIn(ExperimentalForeignApi::class)

package de.connect2x.trixnity.vodozemac.utils

import kotlin.concurrent.atomics.AtomicNativePtr
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.internal.NativePtr
import kotlin.native.ref.createCleaner
import kotlinx.cinterop.*

actual typealias NativePointer = Long

actual typealias InteropPointer = NativePtr

actual typealias NativePointerArray = LongArray

actual fun NativePointerArray.asPtrSequence(): Sequence<NativePointer> = asSequence()

actual val NativePointer.longValue: Long
    get() = this

actual val NativePointer.intValue: Int
    get() = this.toInt()

private val Long.rawPtr
    get() = toCPointer<COpaque>().rawValue

actual val nullPtr: NativePointer
    get() = NativePtr.NULL.toLong()

actual val ptrSize: Int
    get() = 8

internal actual fun reachabilityBarrier(obj: Any) {
    // we are already pinning
}

internal actual class InteropScopeImpl : InteropScope {
    private val elements = mutableListOf<Pinned<*>>()

    actual override fun ByteArray.toInterop(): InteropPointer = toInteropImpl()

    actual override fun ByteArray.toInteropForResult(): InteropPointer = toInteropImpl()

    actual override fun ByteArray.fromInterop(ptr: InteropPointer) {}

    actual override fun ShortArray.toInterop(): InteropPointer = toInteropImpl()

    actual override fun ShortArray.toInteropForResult(): InteropPointer = toInteropImpl()

    actual override fun ShortArray.fromInterop(ptr: InteropPointer) {}

    actual override fun IntArray.toInterop(): InteropPointer = toInteropImpl()

    actual override fun IntArray.toInteropForResult(): InteropPointer = toInteropImpl()

    actual override fun IntArray.fromInterop(ptr: InteropPointer) {}

    actual override fun LongArray.toInterop(): InteropPointer = toInteropImpl()

    actual override fun LongArray.toInteropForResult(): InteropPointer = toInteropImpl()

    actual override fun LongArray.fromInterop(ptr: InteropPointer) {}

    actual override fun NativePointerArray.toPtrInterop(): InteropPointer = toInteropImpl()

    actual override fun NativePointerArray.toPtrInteropForResult(): InteropPointer = toInteropImpl()

    actual override fun NativePointerArray.fromPtrInterop(ptr: InteropPointer) {}

    actual override fun close() {
        elements.forEach { it.unpin() }
    }

    private fun ByteArray.toInteropImpl(): InteropPointer {
        if (isEmpty()) return NativePtr.NULL

        val pinned = pin()
        elements.add(pinned)
        return pinned.addressOf(0).rawValue
    }

    private fun ShortArray.toInteropImpl(): InteropPointer {
        if (isEmpty()) return NativePtr.NULL

        val pinned = pin()
        elements.add(pinned)
        return pinned.addressOf(0).rawValue
    }

    private fun IntArray.toInteropImpl(): InteropPointer {
        if (isEmpty()) return NativePtr.NULL

        val pinned = pin()
        elements.add(pinned)
        return pinned.addressOf(0).rawValue
    }

    private fun NativePointerArray.toInteropImpl(): InteropPointer {
        if (isEmpty()) return NativePtr.NULL

        val pinned = pin()
        elements.add(pinned)
        return pinned.addressOf(0).rawValue
    }
}

@OptIn(ExperimentalAtomicApi::class)
private class FinalizationThunk(
    private val finalizer: (NativePointer) -> Unit,
    obj: NativePointer
) {
    private val ptr = obj.rawPtr
    private var obj = AtomicNativePtr(ptr)

    fun clean() {
        val ptr = obj.exchange(NativePtr.NULL)
        if (ptr != NativePtr.NULL) {
            finalizer(ptr.toLong())
        }
    }

    val isActive
        get() = obj.load() != NativePtr.NULL
}

actual abstract class Managed
actual constructor(ptr: NativePointer, finalizer: (NativePointer) -> Unit) :
    Native(ptr), AutoCloseable {

    init {
        require(ptr.rawPtr != NativePtr.NULL) { "Managed ptr is nullptr" }
    }

    private val thunk = FinalizationThunk(finalizer, ptr)

    @OptIn(ExperimentalNativeApi::class) private val cleaner = createCleaner(thunk) { it.clean() }

    actual override fun close() {
        require(ptrOrNull != null) { "Object already closed: ${this::class.simpleName}, _ptr=null" }
        require(thunk.isActive) {
            "Object is closed already, can't close(): ${this::class.simpleName}, _ptr=$ptrOrNull"
        }

        thunk.clean()
        ptrOrNull = null
    }
}

@OptIn(ExperimentalStdlibApi::class)
actual fun NativePointer.format(): String = "0x" + toULong().toHexString().drop(4)
