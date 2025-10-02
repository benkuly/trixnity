package net.folivo.trixnity.vodozemac.utils

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

expect class NativePointer

expect class InteropPointer

expect class NativePointerArray(size: Int) {
    operator fun get(index: Int): NativePointer

    operator fun set(index: Int, value: NativePointer)

    val size: Int
}

operator fun NativePointerArray.component1(): NativePointer = get(0)

operator fun NativePointerArray.component2(): NativePointer = get(1)

operator fun NativePointerArray.component3(): NativePointer = get(2)

operator fun NativePointerArray.component4(): NativePointer = get(3)

operator fun NativePointerArray.component5(): NativePointer = get(4)

operator fun NativePointerArray.component6(): NativePointer = get(5)

inline fun <R> NativePointerArray.map(transform: (NativePointer) -> R): List<R> =
    List(size) { transform(this[it]) }

expect fun NativePointerArray.asPtrSequence(): Sequence<NativePointer>

expect val nullPtr: NativePointer
expect val ptrSize: Int

expect val NativePointer.longValue: Long
expect val NativePointer.intValue: Int

expect fun NativePointer.format(): String

internal interface InteropScope : AutoCloseable {
    fun ByteArray.toInterop(): InteropPointer

    fun ByteArray.toInteropForResult(): InteropPointer

    fun ByteArray.fromInterop(ptr: InteropPointer)

    fun ShortArray.toInterop(): InteropPointer

    fun ShortArray.toInteropForResult(): InteropPointer

    fun ShortArray.fromInterop(ptr: InteropPointer)

    fun IntArray.toInterop(): InteropPointer

    fun IntArray.toInteropForResult(): InteropPointer

    fun IntArray.fromInterop(ptr: InteropPointer)

    fun LongArray.toInterop(): InteropPointer

    fun LongArray.toInteropForResult(): InteropPointer

    fun LongArray.fromInterop(ptr: InteropPointer)

    fun NativePointerArray.toPtrInterop(): InteropPointer

    fun NativePointerArray.toPtrInteropForResult(): InteropPointer

    fun NativePointerArray.fromPtrInterop(ptr: InteropPointer)
}

@OptIn(ExperimentalContracts::class)
internal inline fun withResult(
    result: ByteArray,
    crossinline block: InteropScope.(InteropPointer) -> Unit
): ByteArray {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }

    return interopScope {
        val handle = result.toInteropForResult()
        block(handle)
        result.fromInterop(handle)
        result
    }
}

@OptIn(ExperimentalContracts::class)
internal inline fun withResult(
    result: ShortArray,
    crossinline block: InteropScope.(InteropPointer) -> Unit
): ShortArray {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }

    return interopScope {
        val handle = result.toInteropForResult()
        block(handle)
        result.fromInterop(handle)
        result
    }
}

@OptIn(ExperimentalContracts::class)
internal inline fun withResult(
    result: NativePointerArray,
    crossinline block: InteropScope.(InteropPointer) -> Unit
): NativePointerArray {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }

    return interopScope {
        val handle = result.toPtrInteropForResult()
        block(handle)
        result.fromPtrInterop(handle)
        result
    }
}

internal expect fun reachabilityBarrier(obj: Any)

internal expect class InteropScopeImpl() : InteropScope {
    override fun ByteArray.fromInterop(ptr: InteropPointer)

    override fun ByteArray.toInterop(): InteropPointer

    override fun ByteArray.toInteropForResult(): InteropPointer

    override fun ShortArray.fromInterop(ptr: InteropPointer)

    override fun ShortArray.toInterop(): InteropPointer

    override fun ShortArray.toInteropForResult(): InteropPointer

    override fun IntArray.fromInterop(ptr: InteropPointer)

    override fun IntArray.toInterop(): InteropPointer

    override fun IntArray.toInteropForResult(): InteropPointer

    override fun LongArray.fromInterop(ptr: InteropPointer)

    override fun LongArray.toInterop(): InteropPointer

    override fun LongArray.toInteropForResult(): InteropPointer

    override fun NativePointerArray.fromPtrInterop(ptr: InteropPointer)

    override fun NativePointerArray.toPtrInterop(): InteropPointer

    override fun NativePointerArray.toPtrInteropForResult(): InteropPointer

    override fun close()
}

@OptIn(ExperimentalContracts::class)
internal inline fun <T> interopScope(crossinline block: InteropScope.() -> T): T {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }

    return InteropScopeImpl().use(block)
}

@OptIn(ExperimentalContracts::class)
internal inline fun <T> reachableScope(obj1: Any, crossinline block: InteropScope.() -> T): T {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }

    return try {
        interopScope { block() }
    } finally {
        reachabilityBarrier(obj1)
    }
}

@OptIn(ExperimentalContracts::class)
internal inline fun <T> reachableScope(
    obj1: Any,
    obj2: Any,
    crossinline block: InteropScope.() -> T
): T {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }

    return try {
        interopScope { block() }
    } finally {
        reachabilityBarrier(obj1)
        reachabilityBarrier(obj2)
    }
}

@OptIn(ExperimentalContracts::class)
internal inline fun <T> reachableScope(
    obj1: Any,
    obj2: Any,
    obj3: Any,
    crossinline block: InteropScope.() -> T
): T {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }

    return try {
        interopScope { block() }
    } finally {
        reachabilityBarrier(obj1)
        reachabilityBarrier(obj2)
        reachabilityBarrier(obj3)
    }
}

@OptIn(ExperimentalContracts::class)
internal inline fun <T> reachableScope(
    obj1: Any,
    obj2: Any,
    obj3: Any,
    obj4: Any,
    crossinline block: InteropScope.() -> T
): T {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }

    return try {
        interopScope { block() }
    } finally {
        reachabilityBarrier(obj1)
        reachabilityBarrier(obj2)
        reachabilityBarrier(obj3)
        reachabilityBarrier(obj4)
    }
}

@OptIn(ExperimentalContracts::class)
internal inline fun <T> Managed.managedReachableScope(crossinline block: InteropScope.() -> T): T {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }

    return reachableScope(this, block)
}

@OptIn(ExperimentalContracts::class)
internal inline fun <T> Managed.managedReachableScope(
    obj1: Any,
    crossinline block: InteropScope.() -> T
): T {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }

    return reachableScope(this, obj1, block)
}

@OptIn(ExperimentalContracts::class)
internal inline fun <T> Managed.managedReachableScope(
    obj1: Any,
    obj2: Any,
    crossinline block: InteropScope.() -> T
): T {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }

    return reachableScope(this, obj1, obj2, block)
}

@OptIn(ExperimentalContracts::class)
internal inline fun <T> Managed.managedReachableScope(
    obj1: Any,
    obj2: Any,
    obj3: Any,
    crossinline block: InteropScope.() -> T
): T {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }

    return reachableScope(this, obj1, obj2, obj3, block)
}

abstract class Native(ptr: NativePointer) {
    internal var ptrOrNull: NativePointer? = ptr

    internal val ptr: NativePointer = requireNotNull(ptrOrNull) { "Pointer already cleaned" }

    override fun toString(): String = "${this::class.simpleName}(ptr=${ptr.format()})"
}

expect abstract class Managed(ptr: NativePointer, finalizer: (NativePointer) -> Unit) :
    Native, AutoCloseable {
    override fun close()
}

internal class Cleanup(ptr: NativePointer, finalizer: (NativePointer) -> Unit) :
    Managed(ptr, finalizer)
