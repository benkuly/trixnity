package net.folivo.trixnity.client

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.model.UserId
import kotlin.test.fail

class ServerDiscoveryTest : ShouldSpec({
    timeout = 10_000

    fun createMockEngineFactory(config: MockEngineConfig.() -> Unit): (HttpClientConfig<*>.() -> Unit) -> HttpClient = {
        HttpClient(MockEngine) {
            it()
            engine {
                config()
            }
        }
    }

    context(String::serverDiscovery.name) {
        should("find server from UserId") {
            val httpClientFactory = createMockEngineFactory {
                addHandler {
                    when (it.url) {
                        Url("https://someHost.org/.well-known/matrix/client") -> respond(
                            """
                            {
                              "m.homeserver": {
                                "base_url": "https://matrix.someHost.org"
                              }
                            }
                        """.trimIndent(),
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        )

                        Url("https://matrix.someHost.org/_matrix/client/versions") -> respond(
                            "{}",
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        )

                        else -> fail("unchecked request ${it.url}")
                    }
                }
            }
            UserId("@someUser:someHost.org").serverDiscovery(httpClientFactory)
                .getOrThrow() shouldBe Url("https://matrix.someHost.org")
        }
        should("find server") {
            val httpClientFactory = createMockEngineFactory {
                addHandler {
                    when (it.url) {
                        Url("https://someHost.org/.well-known/matrix/client") -> respond(
                            """
                            {
                              "m.homeserver": {
                                "base_url": "https://matrix.someHost.org"
                              }
                            }
                        """.trimIndent(),
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        )

                        Url("https://matrix.someHost.org/_matrix/client/versions") -> respond(
                            "{}",
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        )

                        else -> fail("unchecked request")
                    }
                }
            }
            "https://someHost.org".serverDiscovery(httpClientFactory)
                .getOrThrow() shouldBe Url("https://matrix.someHost.org")
        }
        should("find server with port") {
            val httpClientFactory = createMockEngineFactory {
                addHandler {
                    when (it.url) {
                        Url("https://someHost:8008/.well-known/matrix/client") -> respond(
                            """
                            {
                              "m.homeserver": {
                                "base_url": "https://otherHost:8008"
                              }
                            }
                        """.trimIndent(),
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        )

                        Url("https://otherHost:8008/_matrix/client/versions") -> respond(
                            "{}",
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        )

                        else -> fail("unchecked request")
                    }
                }
            }
            "https://someHost:8008".serverDiscovery(httpClientFactory)
                .getOrThrow() shouldBe Url("https://otherHost:8008")
        }
        should("allow http") {
            val httpClientFactory = createMockEngineFactory {
                addHandler {
                    when (it.url) {
                        Url("http://someHost:8008/.well-known/matrix/client") -> respond(
                            """
                            {
                              "m.homeserver": {
                                "base_url": "http://otherHost:8008"
                              }
                            }
                        """.trimIndent(),
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        )

                        Url("http://otherHost:8008/_matrix/client/versions") -> respond(
                            "{}",
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        )

                        else -> fail("unchecked request")
                    }
                }
            }
            "http://someHost:8008".serverDiscovery(httpClientFactory)
                .getOrThrow() shouldBe Url("http://otherHost:8008")
        }
        should("not fail when cannot get discovery information (use url as is)") {
            val httpClientFactory = createMockEngineFactory {
                addHandler {
                    when (it.url) {
                        Url("http://someHost:8008/.well-known/matrix/client") -> respondError(HttpStatusCode.NotFound)
                        Url("http://someHost:8008/_matrix/client/versions") -> respond(
                            "{}",
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        )

                        else -> fail("unchecked request")
                    }
                }
            }
            "http://someHost:8008".serverDiscovery(httpClientFactory)
                .getOrThrow() shouldBe Url("http://someHost:8008")
        }
        should("fail when cannot get version") {
            val httpClientFactory = createMockEngineFactory {
                addHandler {
                    when (it.url) {
                        Url("https://someHost:8008/.well-known/matrix/client") -> respond(
                            """
                            {
                              "m.homeserver": {
                                "base_url": "https://otherHost:8008"
                              }
                            }
                        """.trimIndent(),
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        )

                        Url("https://otherHost:8008/_matrix/client/versions") -> respondError(HttpStatusCode.NotFound)
                        else -> fail("unchecked request")
                    }
                }
            }
            "https://someHost:8008".serverDiscovery(httpClientFactory)
                .exceptionOrNull() shouldBe MatrixServerException(
                HttpStatusCode.NotFound,
                ErrorResponse.CustomErrorResponse("UNKNOWN", "Not Found")
            )
        }
    }
})