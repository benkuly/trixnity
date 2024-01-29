package net.folivo.trixnity.utils

fun <E> concurrentMutableList() = concurrentOf<List<E>, MutableList<E>> { mutableListOf() }

fun <E> concurrentMutableSet() = concurrentOf<Set<E>, MutableSet<E>> { mutableSetOf() }

fun <K, V> concurrentMutableMap() = concurrentOf<Map<K, V>, MutableMap<K, V>> { mutableMapOf() }