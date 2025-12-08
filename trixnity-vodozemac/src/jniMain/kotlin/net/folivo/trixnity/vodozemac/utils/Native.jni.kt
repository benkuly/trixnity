package net.folivo.trixnity.vodozemac.utils

import java.lang.ref.PhantomReference
import java.lang.ref.Reference
import java.lang.ref.ReferenceQueue
import kotlin.concurrent.thread

actual typealias NativePointer = Long

actual typealias InteropPointer = Any

actual typealias NativePointerArray = LongArray

actual fun NativePointerArray.asPtrSequence(): Sequence<NativePointer> = asSequence()

actual val NativePointer.longValue: Long
    get() = this

actual val NativePointer.intValue: Int
    get() = this.toInt()

actual val nullPtr: NativePointer
    get() = 0L

actual val ptrSize: Int
    get() = 8

internal actual fun reachabilityBarrier(obj: Any) = Reference.reachabilityFence(obj)

internal actual class InteropScopeImpl : InteropScope {
    actual override fun ByteArray.toInterop(): InteropPointer = this

    actual override fun ByteArray.toInteropForResult(): InteropPointer = this

    actual override fun ByteArray.fromInterop(ptr: InteropPointer) {}

    actual override fun ShortArray.toInterop(): InteropPointer = this

    actual override fun ShortArray.toInteropForResult(): InteropPointer = this

    actual override fun ShortArray.fromInterop(ptr: InteropPointer) {}

    actual override fun IntArray.toInterop(): InteropPointer = this

    actual override fun IntArray.toInteropForResult(): InteropPointer = this

    actual override fun IntArray.fromInterop(ptr: InteropPointer) {}

    actual override fun LongArray.toInterop(): InteropPointer = this

    actual override fun LongArray.toInteropForResult(): InteropPointer = this

    actual override fun LongArray.fromInterop(ptr: InteropPointer) {}

    actual override fun NativePointerArray.toPtrInterop(): InteropPointer = this

    actual override fun NativePointerArray.toPtrInteropForResult(): InteropPointer = this

    actual override fun NativePointerArray.fromPtrInterop(ptr: InteropPointer) {}

    actual override fun close() {}
}

actual abstract class Managed
actual constructor(ptr: NativePointer, finalizer: (NativePointer) -> Unit) :
    Native(ptr), AutoCloseable {

    actual override fun close() {
        if (ptrOrNull == null)
            throw RuntimeException("Object already closed: $javaClass, _ptr=null")
        else if (null == cleanable)
            throw RuntimeException(
                "Object is not managed in JVM, can't close(): $javaClass, _ptr=$ptrOrNull")
        else {
            cleanable!!.clean()
            cleanable = null
            ptrOrNull = null
        }
    }

    class CleanerThunk(
        private val ptr: NativePointer,
        private val finalizer: (NativePointer) -> Unit
    ) : Runnable {
        override fun run() {
            finalizer(ptr)
        }
    }

    private var cleanable: Cleanable? = null

    companion object {
        private val CLEANER = Cleaner()
    }

    init {
        assert(ptr != 0L) { "Managed ptr is 0" }
        cleanable = CLEANER.register(this, CleanerThunk(ptr, finalizer))
    }
}

private interface Cleanable {
    fun clean()

    var prev: Cleanable?
    var next: Cleanable?
}

private class CleanableImpl(managed: Managed, action: Runnable, cleaner: Cleaner) :
    PhantomReference<Managed>(managed, cleaner.queue), Cleanable {

    override var prev: Cleanable? = this
    @Suppress("PROPERTY_HIDES_JAVA_FIELD")
    override var next: Cleanable? = this

    private val list: Cleanable = cleaner.list
    private var action: Runnable = action

    init {
        insert()
        reachabilityFence(managed)
        reachabilityFence(cleaner)
    }

    override fun clean() {
        if (remove()) {
            super.clear()
            action.run()
        }
    }

    override fun clear() {
        throw UnsupportedOperationException("clear() unsupported")
    }

    private fun insert() {
        synchronized(list) {
            prev = list
            next = list.next
            next?.prev = this
            list.next = this
        }
    }

    private fun remove(): Boolean {
        synchronized(list) {
            if (next !== this) {
                next?.prev = prev
                prev?.next = next
                prev = this
                next = this
                return true
            }
            return false
        }
    }
}

private class Cleaner {
    val queue = ReferenceQueue<Managed>()
    var list: Cleanable =
        object : Cleanable {
            override fun clean() {
                TODO("Must not be called")
            }

            override var prev: Cleanable? = null
            override var next: Cleanable? = null
        }

    @Volatile var stopped = false

    init {
        thread(start = true, isDaemon = true, name = "Reference Cleaner") {
            while (!stopped) {
                val ref = queue.remove(60 * 1000L) as Cleanable?
                try {
                    ref?.clean()
                } catch (t: Throwable) {}
            }
        }
    }

    fun register(managed: Managed, action: Runnable): Cleanable {
        return CleanableImpl(managed, action, this)
    }

    fun stop() {
        stopped = true
    }
}

@OptIn(ExperimentalStdlibApi::class)
actual fun NativePointer.format(): String = "0x" + toULong().toHexString().drop(4)
