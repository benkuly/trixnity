package net.folivo.trixnity.core.model.crypto

import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.serialization.crypto.KeysSerializer

@Serializable(with = KeysSerializer::class)
data class Keys(
    val keys: Set<Key>
) : Set<Key> {
    override val size: Int = keys.size
    override fun contains(element: Key): Boolean = keys.contains(element)
    override fun containsAll(elements: Collection<Key>): Boolean = keys.containsAll(elements)
    override fun isEmpty(): Boolean = keys.isEmpty()
    override fun iterator(): Iterator<Key> = keys.iterator()
}

fun keysOf(vararg keys: Key) = Keys(keys.toSet())