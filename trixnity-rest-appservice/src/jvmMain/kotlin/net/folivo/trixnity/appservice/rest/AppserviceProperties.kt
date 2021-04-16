package net.folivo.trixnity.appservice.rest

data class AppserviceProperties(
    val hsToken: String,
    val namespaces: Namespaces = Namespaces()
) {
    data class Namespaces(
        val users: List<Namespace> = emptyList(),
        val aliases: List<Namespace> = emptyList(),
        val rooms: List<Namespace> = emptyList()
    )

    data class Namespace(
        val localpartRegex: String
    )
}