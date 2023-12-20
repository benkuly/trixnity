package net.folivo.trixnity.olm

interface WantsToBeFree {
    fun free()
}

// TODO freeAfter w# parameters should be factories itself. Otherwise cancellation could leak.

inline fun <T, W1 : WantsToBeFree> freeAfter(w1: W1, block: (W1) -> T): T =
    try {
        block(w1)
    } finally {
        w1.free()
    }


inline fun <T, W1 : WantsToBeFree, W2 : WantsToBeFree> freeAfter(w1: W1, w2: W2, block: (W1, W2) -> T): T =
    try {
        block(w1, w2)
    } finally {
        w1.free()
        w2.free()
    }

inline fun <T, W1 : WantsToBeFree, W2 : WantsToBeFree, W3 : WantsToBeFree> freeAfter(
    w1: W1,
    w2: W2,
    w3: W3,
    block: (W1, W2, W3) -> T
): T =
    try {
        block(w1, w2, w3)
    } finally {
        w1.free()
        w2.free()
        w3.free()
    }

inline fun <T, W1 : WantsToBeFree, W2 : WantsToBeFree, W3 : WantsToBeFree, W4 : WantsToBeFree> freeAfter(
    w1: W1,
    w2: W2,
    w3: W3,
    w4: W4,
    block: (W1, W2, W3, W4) -> T
): T =
    try {
        block(w1, w2, w3, w4)
    } finally {
        w1.free()
        w2.free()
        w3.free()
        w4.free()
    }