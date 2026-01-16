package de.connect2x.trixnity.crypto.driver

inline fun <A : AutoCloseable, B : AutoCloseable, R> useAll(a: () -> A, b: (A) -> B, block: (A, B) -> R): R =
    a().use { a -> b(a).use { b -> block(a, b) } }