package net.folivo.trixnity.client.rest

data class MatrixRestClientProperties(
    val homeServer: MatrixHomeServerProperties,
    val token: String?
) {
    data class MatrixHomeServerProperties(
        val hostname: String,
        val port: Int = 443,
        val secure: Boolean = true
    )
}