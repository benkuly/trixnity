package net.folivo.trixnity.crypto.core

import kotlinx.cinterop.*
import org.openssl.OSSL_PARAM
import org.openssl.OSSL_PARAM_construct_end

internal fun NativePlacement.OSSL_PARAM_array(vararg values: CValue<OSSL_PARAM>): CArrayPointer<OSSL_PARAM> =
    allocArrayOf(*values, OSSL_PARAM_construct_end())

internal inline fun <reified T : CVariable> NativePlacement.allocArrayOf(vararg elements: CValue<T>): CArrayPointer<T> {
    val array = allocArray<T>(elements.size)
    elements.forEachIndexed { index, element -> array[index] = element }
    return array
}

internal inline operator fun <reified T : CVariable> CArrayPointer<T>.set(index: Int, value: CValue<T>) {
    value.place(get(index).ptr)
}