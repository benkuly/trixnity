/*
 * Parts of this file were copied from https://github.com/Dominaezzz/matrix-kt
 * and has been slightly modified.
 *
 * Licenced under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.folivo.trixnity.libolm

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.PointerType

internal inline fun <T : PointerType> genericInit(init: (Pointer?) -> T?, size: NativeSize): T {
    val memory = Pointer(Native.malloc(size.toLong()))
    try {
        val ptr = init(memory)
        checkNotNull(ptr)
        return ptr
    } catch (e: Exception) {
        Native.free(Pointer.nativeValue(memory))
        throw OlmLibraryException(message = "could not init object", cause = e)
    }
}


internal inline fun <T> ByteArray?.withNativeRead(block: (Pointer?, NativeSize) -> T): T {
    return if (this == null) {
        block(null, NativeSize(0))
    } else withAllocation(size.toLong()) {
        it.write(0, this, 0, size)
        block(it, NativeSize(this.size))
    }
}

internal inline fun <T> ByteArray.withNativeWrite(block: (Pointer, NativeSize) -> T): T {
    return withAllocation(size.toLong()) {
        try {
            block(it, NativeSize(this.size))
        } finally {
            it.read(0, this, 0, size)
        }
    }
}

internal inline fun <T> withAllocation(size: Long, block: (Pointer) -> T): T {
    val buffer = Native.malloc(size)
    return try {
        block(Pointer(buffer))
    } finally {
        Native.free(buffer)
    }
}