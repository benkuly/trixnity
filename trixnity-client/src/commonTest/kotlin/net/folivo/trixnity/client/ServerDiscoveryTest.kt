package net.folivo.trixnity.client

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.Test
import kotlin.test.fail

class ServerDiscoveryTest : TrixnityBaseTest() {

    @Test
    fun `find server from UserId`() = runTest {
        val httpClientEngine = backgroundScope.scopedMockEngine {
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

    @Test
    fun `find server`() = runTest {
        val httpClientEngine = backgroundScope.scopedMockEngine {
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

    @Test
    fun `find server with port`() = runTest {
        val httpClientEngine = backgroundScope.scopedMockEngine {
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

    @Test
    fun `allow http`() = runTest {
        val httpClientEngine = backgroundScope.scopedMockEngine {
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

    @Test
    fun `not fail when cannot get discovery information use url as is`() = runTest {
        val httpClientEngine = backgroundScope.scopedMockEngine {
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

    @Test
    fun `fail when cannot get version`() = runTest {
        val httpClientEngine = backgroundScope.scopedMockEngine {
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
            .exceptionOrNull()
            .shouldBeInstanceOf<MatrixServerException>().statusCode shouldBe HttpStatusCode.NotFound
    }


    private fun CoroutineScope.scopedMockEngine(config: MockEngineConfig.() -> Unit): HttpClientEngine =
        MockEngine.create {
            dispatcher = coroutineContext[ContinuationInterceptor] as? CoroutineDispatcher
            config()
        }.also { engine -> coroutineContext.job.invokeOnCompletion { engine.close() } }
}