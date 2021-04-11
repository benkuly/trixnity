package net.folivo.trixnity.appservice.rest

import io.ktor.application.*
import io.ktor.routing.*

class AppserviceServer {
}

fun Application.appserviceModule() {
    feature(Routing)

    routing {
        controller()
    }
}