package net.folivo.trixnity.examples.client.ping

import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.verification
import net.folivo.trixnity.client.verification.ActiveSasVerificationMethod
import net.folivo.trixnity.client.verification.ActiveSasVerificationState.*
import net.folivo.trixnity.client.verification.ActiveVerificationState.*
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod

suspend fun verificationExample() = coroutineScope {
    val scope = CoroutineScope(Dispatchers.Default)

    val username = "username"
    val password = "password"
    val baseUrl = Url("https://example.org")
    val matrixClient = MatrixClient.fromStore(
        repositoriesModule = createRepositoriesModule(),
        scope = scope,
    ).getOrThrow() ?: MatrixClient.login(
        baseUrl = baseUrl,
        IdentifierType.User(username),
        password,
        initialDeviceDisplayName = "trixnity-client-${kotlin.random.Random.Default.nextInt()}",
        repositoriesModule = createRepositoriesModule(),
        scope = scope,
    ).getOrThrow()

    val job1 = launch {
        val activeDeviceVerification = matrixClient.verification.activeDeviceVerification.filterNotNull().first()
        activeDeviceVerification.state.collectLatest { state ->
            when (state) {
                is OwnRequest -> {}
                is TheirRequest -> {
                    println("new verification request from ${activeDeviceVerification.theirUserId}(${activeDeviceVerification.theirDeviceId})")
                    state.ready()
                }

                is Ready -> {
                    println("verification is ready")
                    state.start(VerificationMethod.Sas)
                }

                is Start -> {
                    println("started verification")
                    when (val method = state.method) {
                        is ActiveSasVerificationMethod -> {
                            method.state.collect { methodState ->
                                when (methodState) {
                                    is OwnSasStart -> {
                                        println("sas started")
                                    }

                                    is TheirSasStart -> {
                                        println("sas started")
                                        methodState.accept()
                                    }

                                    is Accept -> {
                                        println("sas accepted")
                                    }

                                    is WaitForKeys -> {
                                        println("waits for keys")
                                    }

                                    is ComparisonByUser -> {
                                        println(
                                            "start comparison: ${methodState.emojis.joinToString()} " +
                                                    "(${methodState.decimal.joinToString(" ")})"
                                        )
                                        delay(2000)
                                        methodState.match()
                                    }

                                    is WaitForMacs -> {
                                        println("wait for macs")
                                    }
                                }
                            }
                        }
                    }
                }

                is PartlyDone -> {
                    println("wait for done")
                }

                is Done -> {
                    println("we are done!")
                }

                is Cancel -> {
                    println("cancelled because ${state.content} (isOurOwn=${state.isOurOwn})")
                }

                AcceptedByOtherDevice -> println("another device accepted others request")
                Undefined -> println("undefined")
            }
        }
    }

    matrixClient.startSync()

    delay(300000)
    scope.cancel()

    job1.cancelAndJoin()
}