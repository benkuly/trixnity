package net.folivo.trixnity.vodozemac.utils

import net.folivo.trixnity.vodozemac.alloc
import net.folivo.trixnity.vodozemac.dealloc

internal expect fun toWasm(dest: NativePointer, src: ByteArray)

internal expect fun toWasm(dest: NativePointer, src: ShortArray)

internal expect fun toWasm(dest: NativePointer, src: IntArray)

internal expect fun toWasm(dest: NativePointer, src: LongArray)

internal expect fun fromWasm(src: NativePointer, result: ByteArray)

internal expect fun fromWasm(src: NativePointer, result: ShortArray)

internal expect fun fromWasm(src: NativePointer, result: IntArray)

internal expect fun fromWasm(src: NativePointer, result: LongArray)

internal actual class InteropScopeImpl : InteropScope {
    data class Element(var ptr: NativePointer?, val size: Int, val align: Int) {
        fun drop() {
            ptr?.run {
                ptr = null
                dealloc(this, size, align)
            }
        }
    }

    private val elements = mutableListOf<Element>()

    actual override fun ByteArray.toInterop(): InteropPointer = toInterop(copyArrayToWasm = true)

    actual override fun ByteArray.toInteropForResult(): InteropPointer =
        toInterop(copyArrayToWasm = false)

    actual override fun ByteArray.fromInterop(ptr: InteropPointer) {
        if (isEmpty()) return

        fromWasm(ptr, this)
    }

    actual override fun ShortArray.toInterop(): InteropPointer = toInterop(copyArrayToWasm = true)

    actual override fun ShortArray.toInteropForResult(): InteropPointer =
        toInterop(copyArrayToWasm = false)

    actual override fun ShortArray.fromInterop(ptr: InteropPointer) {
        if (isEmpty()) return

        fromWasm(ptr, this)
    }

    actual override fun IntArray.toInterop(): InteropPointer = toInterop(copyArrayToWasm = true)

    actual override fun IntArray.toInteropForResult(): InteropPointer =
        toInterop(copyArrayToWasm = false)

    actual override fun IntArray.fromInterop(ptr: InteropPointer) {
        if (isEmpty()) return

        fromWasm(ptr, this)
    }

    actual override fun LongArray.toInterop(): InteropPointer = toInterop(copyArrayToWasm = true)

    actual override fun LongArray.toInteropForResult(): InteropPointer =
        toInterop(copyArrayToWasm = false)

    actual override fun LongArray.fromInterop(ptr: InteropPointer) {
        if (isEmpty()) return

        fromWasm(ptr, this)
    }

    actual override fun NativePointerArray.toPtrInterop(): InteropPointer =
        toInterop(copyArrayToWasm = true)

    actual override fun NativePointerArray.toPtrInteropForResult(): InteropPointer =
        toInterop(copyArrayToWasm = false)

    actual override fun NativePointerArray.fromPtrInterop(ptr: InteropPointer) {
        if (isEmpty()) return

        fromWasm(ptr, this)
    }

    actual override fun close() {
        elements.forEach { it.drop() }
    }

    private fun ByteArray.toInterop(copyArrayToWasm: Boolean): InteropPointer {
        if (isEmpty()) return nullPtr

        val data = alloc(size, 1)
        elements.add(Element(data, size, 1))
        if (copyArrayToWasm) toWasm(data, this)
        return data
    }

    private fun ShortArray.toInterop(copyArrayToWasm: Boolean): InteropPointer {
        if (isEmpty()) return nullPtr

        val data = alloc(size * 2, 2)
        elements.add(Element(data, size * 2, 2))
        if (copyArrayToWasm) toWasm(data, this)
        return data
    }

    private fun IntArray.toInterop(copyArrayToWasm: Boolean): InteropPointer {
        if (isEmpty()) return nullPtr

        val data = alloc(size * 4, 4)
        elements.add(Element(data, size * 4, 4))
        if (copyArrayToWasm) toWasm(data, this)
        return data
    }

    private fun LongArray.toInterop(copyArrayToWasm: Boolean): InteropPointer {
        if (isEmpty()) return nullPtr

        val data = alloc(size * 8, 8)
        elements.add(Element(data, size * 8, 8))
        if (copyArrayToWasm) toWasm(data, this)
        return data
    }
}

actual typealias NativePointer = Int

actual typealias InteropPointer = Int

actual typealias NativePointerArray = IntArray

actual fun NativePointerArray.asPtrSequence(): Sequence<NativePointer> = asSequence()

actual val ptrSize: Int
    get() = 4

actual val NativePointer.longValue: Long
    get() = toLong()

actual val NativePointer.intValue: Int
    get() = this

actual val nullPtr: NativePointer
    get() = 0

internal actual fun reachabilityBarrier(obj: Any) {}

internal class FinalizationThunk(
    private val finalizer: (NativePointer) -> Unit,
    private var obj: NativePointer
) {
    fun clean() {
        if (obj != 0) finalizer(obj)
        obj = 0
    }
}

internal expect fun register(managed: Managed, thunk: FinalizationThunk)

internal expect fun unregister(managed: Managed)

actual abstract class Managed
actual constructor(ptr: NativePointer, finalizer: (NativePointer) -> Unit) :
    Native(ptr), AutoCloseable {

    init {
        require(ptr != 0) { "Managed ptr is 0" }
    }

    private var cleaner: FinalizationThunk? =
        FinalizationThunk(finalizer, ptr).also { register(this, it) }

    actual override fun close() {
        if (ptrOrNull == null)
            throw RuntimeException("Object already closed: ${this::class.simpleName}, _ptr=0")
        else if (null == cleaner)
            throw RuntimeException(
                "Object is not managed, can't close(): ${this::class.simpleName}, _ptr=$ptrOrNull")
        else {
            unregister(this)
            cleaner!!.clean()
            cleaner = null
            ptrOrNull = null
        }
    }
}

@OptIn(ExperimentalStdlibApi::class)
actual fun NativePointer.format(): String = "0x" + toUInt().toHexString()
