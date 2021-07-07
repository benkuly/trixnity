package net.folivo.trixnity.client.rest

data class MatrixRestClientProperties(
    val homeServer: HomeServerProperties,
    val token: String?
) {
    data class HomeServerProperties(
        val hostname: String,
        val port: Int = 443,
        val secure: Boolean = true
    )
}