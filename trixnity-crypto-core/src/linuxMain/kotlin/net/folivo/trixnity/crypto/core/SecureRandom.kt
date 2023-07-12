package net.folivo.trixnity.crypto.core

import kotlinx.cinterop.*
import org.linux.random.GRND_NONBLOCK
import platform.linux.SYS_getrandom
import platform.posix.*

actual fun fillRandomBytes(array: ByteArray) {
    if (array.isEmpty()) return

    return fillBytes(array) { pointer, size ->
        if (isGetRandomAvailable()) {
            getrandom(pointer, size.convert(), 0.convert())
        } else {
            read(urandom, pointer, size.convert()).convert()
        }
    }
}

private fun fillBytes(array: ByteArray, fillBytes: (pointer: CPointer<ByteVar>, size: Int) -> Int) {
    val size = array.size
    array.usePinned {
        var filled = 0
        while (filled < size) {
            val chunkSize = fillBytes(it.addressOf(filled), size - filled)
            if (chunkSize < 0) error(errorMessage())
            filled += chunkSize
        }
    }
}

private fun isGetRandomAvailable(): Boolean {
    val stubArray = ByteArray(1)
    val stubSize = stubArray.size
    stubArray.usePinned {
        if (getrandom(it.addressOf(0), stubSize.convert(), GRND_NONBLOCK.convert()) >= 0) return true
    }

    return when (errno) {
        ENOSYS, EPERM -> false
        else -> true
    }
}

private fun getrandom(out: CPointer<ByteVar>?, outSize: size_t, flags: UInt): Int =
    syscall(SYS_getrandom.convert(), out, outSize, flags).convert()

private val urandom by lazy {
    val randomFd = open("/dev/random")
    try {
        memScoped {
            val pollFd = alloc<pollfd> {
                fd = randomFd
                events = POLLIN.convert()
                revents = 0
            }

            while (true) {
                if (poll(pollFd.ptr, 1u, -1) >= 0) break

                when (errno) {
                    EINTR, EAGAIN -> continue
                    else -> error(errorMessage())
                }
            }
        }
    } finally {
        close(randomFd)
    }
    open("/dev/urandom")
}

private fun open(path: String): Int {
    val fd = open(path, O_RDONLY, null)
    if (fd <= 0) error(errorMessage())
    return fd
}

private fun errorMessage(): String =
    when (val value = errno) {
        EFAULT -> "The address referred to by buf is outside the accessible address space."
        EINTR -> "The call was interrupted by a signal handler; see the description of how interrupted read(2) calls on 'slow' devices are handled with and without the SA_RESTART flag in the signal(7) man page."
        EINVAL -> "An invalid flag was specified in flags."
        else -> "POSIX error: $value"
    }