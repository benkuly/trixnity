package net.folivo.trixnity.examples.multiplatform

import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.api.authentication.IdentifierType
import net.folivo.trixnity.client.store.SecureStore
import net.folivo.trixnity.client.verification.ActiveSasVerificationMethod
import net.folivo.trixnity.client.verification.ActiveSasVerificationState.*
import net.folivo.trixnity.client.verification.ActiveVerificationState.*
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod
import org.kodein.log.Logger
import org.kodein.log.LoggerFactory
import org.kodein.log.filter.entry.minimumLevel
import org.kodein.log.frontend.defaultLogFrontend

suspend fun verificationExample() = coroutineScope {
    val scope = CoroutineScope(Dispatchers.Default)

    val username = "username"
    val password = "password"
    val baseUrl = Url("https://example.org")
    val secureStore = object : SecureStore {
        override val olmPickleKey = ""
    }
    val loggerFactory = LoggerFactory(
        listOf(defaultLogFrontend),
        listOf(minimumLevel(Logger.Level.INFO)),
    )
    val matrixClient = MatrixClient.fromStore(
        storeFactory = createStoreFactory(),
        secureStore = secureStore,
        scope = scope,
        loggerFactory = loggerFactory
    ) ?: MatrixClient.login(
        baseUrl = baseUrl,
        IdentifierType.User(username),
        password,
        initialDeviceDisplayName = "trixnity-client-${kotlin.random.Random.Default.nextInt()}",
        storeFactory = createStoreFactory(),
        secureStore = secureStore,
        scope = scope,
        loggerFactory = loggerFactory
    )

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
                    println("cancelled because ${state.content} from ${state.sender}")
                }
            }
        }
    }

    matrixClient.startSync()

    delay(300000)
    scope.cancel()

    job1.cancelAndJoin()
}