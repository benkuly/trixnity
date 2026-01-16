package de.connect2x.trixnity.vodozemac.utils

import js.core.JsPrimitives.toByte
import js.core.JsPrimitives.toJsByte
import js.core.JsPrimitives.toJsInt
import js.core.JsPrimitives.toJsShort
import js.core.JsPrimitives.toShort
import js.reflect.unsafeCast
import js.typedarrays.BigInt64Array
import js.typedarrays.Int16Array
import js.typedarrays.Int32Array
import js.typedarrays.Int8Array
import de.connect2x.trixnity.vodozemac.memory

internal actual fun toWasm(dest: NativePointer, src: ByteArray) {
    val view = Int8Array(memory.buffer, dest, src.size)
    src.forEachIndexed { index, value -> view[index] = value.toJsByte() }
}

internal actual fun toWasm(dest: NativePointer, src: ShortArray) {
    val view = Int16Array(memory.buffer, dest, src.size)
    src.forEachIndexed { index, value -> view[index] = value.toJsShort() }
}

internal actual fun toWasm(dest: NativePointer, src: IntArray) {
    val view = Int32Array(memory.buffer, dest, src.size)
    src.forEachIndexed { index, value -> view[index] = value.toJsInt() }
}

internal actual fun toWasm(dest: NativePointer, src: LongArray) {
    val view = BigInt64Array(memory.buffer, dest, src.size)
    src.forEachIndexed { index, value -> view[index] = value.toJsBigInt().unsafeCast() }
}

internal actual fun fromWasm(src: NativePointer, result: ByteArray) {
    val view = Int8Array(memory.buffer, src, result.size)
    for (index in result.indices) {
        result[index] = view[index].toByte()
    }
}

internal actual fun fromWasm(src: NativePointer, result: ShortArray) {
    val view = Int16Array(memory.buffer, src, result.size)
    for (index in result.indices) {
        result[index] = view[index].toShort()
    }
}

internal actual fun fromWasm(src: NativePointer, result: IntArray) {
    val view = Int32Array(memory.buffer, src, result.size)
    for (index in result.indices) {
        result[index] = view[index].toInt()
    }
}

internal actual fun fromWasm(src: NativePointer, result: LongArray) {
    val view = BigInt64Array(memory.buffer, src, result.size)
    for (index in result.indices) {
        result[index] = view[index].unsafeCast<JsBigInt>().toLong()
    }
}

internal external class FinalizationRegistry(cleanup: (JsReference<FinalizationThunk>) -> Unit) {
    fun register(obj: JsReference<Managed>, handle: JsReference<FinalizationThunk>)

    fun unregister(obj: JsReference<Managed>)
}

private val registry = FinalizationRegistry { thunk: JsReference<FinalizationThunk> ->
    thunk.get().clean()
}

internal actual fun register(managed: Managed, thunk: FinalizationThunk) {
    registry.register(managed.toJsReference(), thunk.toJsReference())
}

internal actual fun unregister(managed: Managed) {
    registry.unregister(managed.toJsReference())
}
