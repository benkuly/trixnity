package net.folivo.trixnity.utils

typealias ConcurrentList<E> = Concurrent<List<E>, MutableList<E>>
typealias ConcurrentSet<E> = Concurrent<Set<E>, MutableSet<E>>
typealias ConcurrentMap<K, V> = Concurrent<Map<K, V>, MutableMap<K, V>>

fun <E> concurrentMutableList(): ConcurrentList<E> = concurrentOf<List<E>, MutableList<E>> { mutableListOf() }

fun <E> concurrentMutableSet(): ConcurrentSet<E> = concurrentOf<Set<E>, MutableSet<E>> { mutableSetOf() }

fun <K, V> concurrentMutableMap(): ConcurrentMap<K, V> = concurrentOf<Map<K, V>, MutableMap<K, V>> { mutableMapOf() }