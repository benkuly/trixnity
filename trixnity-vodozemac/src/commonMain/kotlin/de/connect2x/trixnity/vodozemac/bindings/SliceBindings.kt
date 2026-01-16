@file:Import("vodozemac")

package de.connect2x.trixnity.vodozemac.bindings

import de.connect2x.trixnity.vodozemac.ExternalSymbolName
import de.connect2x.trixnity.vodozemac.Import
import de.connect2x.trixnity.vodozemac.InitHook
import de.connect2x.trixnity.vodozemac.ModuleImport
import de.connect2x.trixnity.vodozemac.utils.InteropPointer
import de.connect2x.trixnity.vodozemac.utils.NativePointer

internal object SliceBindings {

    init {
        InitHook()
    }

    fun copyNonoverlapping(src: NativePointer, dest: InteropPointer, size: Int) =
        copy_nonoverlapping(src, dest, size)

    fun alloc(size: Int, align: Int) = allocOuter(size, align)

    fun dealloc(ptr: NativePointer, size: Int, align: Int) = deallocOuter(ptr, size, align)
}

@ModuleImport("vodozemac", "copy_nonoverlapping")
@ExternalSymbolName("copy_nonoverlapping")
private external fun copy_nonoverlapping(src: NativePointer, dest: InteropPointer, size: Int)

@ModuleImport("vodozemac", "dealloc")
@ExternalSymbolName("dealloc")
private external fun dealloc(ptr: NativePointer, size: Int, align: Int)

@ModuleImport("vodozemac", "alloc")
@ExternalSymbolName("alloc")
private external fun alloc(size: Int, align: Int): NativePointer

private val deallocOuter = ::dealloc
private val allocOuter = ::alloc
