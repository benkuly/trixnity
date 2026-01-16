package de.connect2x.trixnity.vodozemac.utils

import js.buffer.ArrayBuffer
import js.typedarrays.BigInt64Array
import js.typedarrays.Int16Array
import js.typedarrays.Int32Array
import js.typedarrays.Int8Array
import de.connect2x.trixnity.vodozemac.memory

internal actual fun toWasm(dest: NativePointer, src: ByteArray) {
    val destView = Int8Array(memory.buffer, dest, src.size)
    val srcView = src.unsafeCast<Int8Array<ArrayBuffer>>()
    destView.set(srcView)
}

internal actual fun toWasm(dest: NativePointer, src: ShortArray) {
    val destView = Int16Array(memory.buffer, dest, src.size)
    val srcView = src.unsafeCast<Int16Array<ArrayBuffer>>()
    destView.set(srcView)
}

internal actual fun toWasm(dest: NativePointer, src: IntArray) {
    val destView = Int32Array(memory.buffer, dest, src.size)
    val srcView = src.unsafeCast<Int32Array<ArrayBuffer>>()
    destView.set(srcView)
}

internal actual fun toWasm(dest: NativePointer, src: LongArray) {
    val destView = BigInt64Array(memory.buffer, dest, src.size)
    val srcView = src.unsafeCast<BigInt64Array<ArrayBuffer>>()
    destView.set(srcView)
}

internal actual fun fromWasm(src: NativePointer, result: ByteArray) {
    val srcView = Int8Array(memory.buffer, src, result.size)
    val destView = result.unsafeCast<Int8Array<ArrayBuffer>>()
    destView.set(srcView)
}

internal actual fun fromWasm(src: NativePointer, result: ShortArray) {
    val srcView = Int16Array(memory.buffer, src, result.size)
    val destView = result.unsafeCast<Int16Array<ArrayBuffer>>()
    destView.set(srcView)
}

internal actual fun fromWasm(src: NativePointer, result: IntArray) {
    val srcView = Int32Array(memory.buffer, src, result.size)
    val destView = result.unsafeCast<Int32Array<ArrayBuffer>>()
    destView.set(srcView)
}

// For whatever reason kotlin represents LongArray as [Long { hi, lo }] instead of BigInt64Array
internal actual fun fromWasm(src: NativePointer, result: LongArray) {
    val srcView = BigInt64Array(memory.buffer, src, result.size)
    for (index in result.indices) {
        result[index] = srcView[index].unsafeCast<Long>()
    }
}

internal external class FinalizationRegistry(cleanup: (dynamic) -> Unit) {
    fun register(obj: dynamic, handle: dynamic)

    fun unregister(obj: dynamic)
}

private val registry = FinalizationRegistry {
    val thunk = it as FinalizationThunk
    thunk.clean()
}

internal actual fun register(managed: Managed, thunk: FinalizationThunk) {
    registry.register(managed, thunk)
}

internal actual fun unregister(managed: Managed) {
    registry.unregister(managed)
}
