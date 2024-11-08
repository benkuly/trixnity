package net.folivo.trixnity.client

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.model.UserId
import kotlin.test.fail

class ServerDiscoveryTest : ShouldSpec({
    timeout = 10_000

    fun CoroutineScope.scopedMockEngine(config: MockEngineConfig.() -> Unit): HttpClientEngine =
        MockEngine.create {
            config()
        }.also { engine -> coroutineContext.job.invokeOnCompletion { engine.close() } }

    context(String::serverDiscovery.name) {
        should("find server from UserId") {
            val httpClientEngine = scopedMockEngine {
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
            UserId("@someUser:someHost.org").serverDiscovery(httpClientEngine)
                .getOrThrow() shouldBe Url("https://matrix.someHost.org")
        }
        should("find server") {
            val httpClientEngine = scopedMockEngine {
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
            "https://someHost.org".serverDiscovery(httpClientEngine)
                .getOrThrow() shouldBe Url("https://matrix.someHost.org")
        }
        should("find server with port") {
            val httpClientEngine = scopedMockEngine {
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
            "https://someHost:8008".serverDiscovery(httpClientEngine)
                .getOrThrow() shouldBe Url("https://otherHost:8008")
        }
        should("allow http") {
            val httpClientEngine = scopedMockEngine {
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
            "http://someHost:8008".serverDiscovery(httpClientEngine)
                .getOrThrow() shouldBe Url("http://otherHost:8008")
        }
        should("not fail when cannot get discovery information (use url as is)") {
            val httpClientEngine = scopedMockEngine {
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
            "http://someHost:8008".serverDiscovery(httpClientEngine)
                .getOrThrow() shouldBe Url("http://someHost:8008")
        }
        should("fail when cannot get version") {
            val httpClientEngine = scopedMockEngine {
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
            "https://someHost:8008".serverDiscovery(httpClientEngine)
                .exceptionOrNull() shouldBe MatrixServerException(
                HttpStatusCode.NotFound,
                ErrorResponse.CustomErrorResponse("UNKNOWN", "Not Found")
            )
        }
    }
})