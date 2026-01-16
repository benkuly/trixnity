@file:Import("vodozemac")

package de.connect2x.trixnity.vodozemac

import de.connect2x.trixnity.vodozemac.utils.NativePointer
import web.assembly.Memory

@ExternalSymbolName("alloc")
@ModuleImport("vodozemac", "alloc")
internal external fun alloc(size: Int, align: Int): NativePointer

@ExternalSymbolName("dealloc")
@ModuleImport("vodozemac", "dealloc")
internal external fun dealloc(ptr: NativePointer, size: Int, align: Int)

@ExternalSymbolName("memory") internal external val memory: Memory
