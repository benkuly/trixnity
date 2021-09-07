package net.folivo.trixnity.client.store

import net.folivo.trixnity.core.model.MatrixId

operator fun MatrixId.plus(other: MatrixId): String {
    return "$this|$other"
}