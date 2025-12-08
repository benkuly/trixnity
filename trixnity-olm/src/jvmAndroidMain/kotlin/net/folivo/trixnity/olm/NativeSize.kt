/*
 * This file was copied from https://github.com/Dominaezzz/matrix-kt
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

package net.folivo.trixnity.olm

import com.sun.jna.IntegerType
import com.sun.jna.Native

class NativeSize(value: ULong = 0u) : IntegerType(Native.SIZE_T_SIZE, value.toLong(), true) {
    constructor(value: Int) : this(value.toULong())
    constructor() : this(0)

    override fun toByte(): Byte = toLong().toByte()
    @Deprecated(
        "Direct conversion to Char is deprecated. Use toInt().toChar() or Char constructor instead.\nIf you override toChar() function in your Number inheritor, it's recommended to gradually deprecate the overriding function and then remove it.\nSee https://youtrack.jetbrains.com/issue/KT-46465 for details about the migration",
        replaceWith = ReplaceWith("this.toInt().toChar()")
    )
    override fun toChar(): Char = toInt().toChar()
    override fun toShort(): Short = toLong().toShort()
    fun toULong(): ULong = toLong().toULong()

    companion object {
        val ZERO: NativeSize = NativeSize(0)
    }
}