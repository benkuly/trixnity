package net.folivo.trixnity.client.verification

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.key.KeyTrustService
import net.folivo.trixnity.client.store.KeyStore
import net.folivo.trixnity.client.verification.ActiveVerificationState.*
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.events.m.key.verification.*
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent.Code
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent.Code.UnexpectedMessage
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent.Code.UnknownMethod
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationStartEventContent.SasStartEventContent
import net.folivo.trixnity.utils.withReentrantLock

private val log = KotlinLogging.logger("net.folivo.trixnity.client.verification.ActiveVerification")

interface ActiveVerification {
    val theirUserId: UserId
    val timestamp: Long
    val relatesTo: RelatesTo.Reference?
    val transactionId: String?
    val state: StateFlow<ActiveVerificationState>

    val theirDeviceId: String?

    suspend fun cancel(message: String = "user cancelled verification")
}

abstract class ActiveVerificationImpl(
    request: VerificationRequest,
    requestIsFromOurOwn: Boolean,
    protected val ownUserId: UserId,
    protected val ownDeviceId: String,
    override val theirUserId: UserId,
    theirInitialDeviceId: String?,
    override val timestamp: Long,
    protected val supportedMethods: Set<VerificationMethod>,
    override val relatesTo: RelatesTo.Reference?,
    override val transactionId: String?,
    protected val keyStore: KeyStore,
    private val keyTrustService: KeyTrustService,
    protected val json: Json,
) : ActiveVerification {
    final override var theirDeviceId: String? = theirInitialDeviceId
        private set

    protected val mutableState: MutableStateFlow<ActiveVerificationState> =
        MutableStateFlow(
            if (requestIsFromOurOwn) OwnRequest(request)
            else TheirRequest(
                request, ownDeviceId, supportedMethods, relatesTo, transactionId, ::sendVerificationStepAndHandleIt
            )
        )
    override val state = mutableState.asStateFlow()

    private val lifecycleStarted = MutableStateFlow(false)
    protected abstract suspend fun lifecycle()
    internal fun startLifecycle(scope: CoroutineScope): Boolean {
        log.debug { "start lifecycle of verification ${transactionId ?: relatesTo}" }
        return if (!lifecycleAlreadyStarted()) {
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                lifecycle()
                log.debug { "stop lifecycle of verification ${transactionId ?: relatesTo}" }
            }
            true
        } else false
    }

    private fun lifecycleAlreadyStarted() = lifecycleStarted.getAndUpdate { true }

    private val handleVerificationStepMutex = Mutex()

    protected open suspend fun handleVerificationStep(step: VerificationStep, sender: UserId, isOurOwn: Boolean) {
        handleVerificationStepMutex.withReentrantLock {
            try {
                if (sender != theirUserId && sender != ownUserId)
                    cancel(Code.UserMismatch, "the user did not match the expected user, we want to verify")
                if (!(relatesTo != null && step.relatesTo == relatesTo || transactionId != null && step.transactionId == transactionId))
                    cancel(Code.UnknownTransaction, "transaction is unknown")
                val currentState = state.value
                log.debug {
                    """
                        handle verification step:
                            step=$step
                            sender=$sender
                            isOurOwn=$isOurOwn ($ownUserId/$ownDeviceId)
                            current state: $currentState
                    """.trimIndent()
                }
                if (currentState is AcceptedByOtherDevice) {
                    if (step is VerificationDoneEventContent) {
                        mutableState.value = Done
                    }
                    if (step is VerificationCancelEventContent) {
                        mutableState.value = Cancel(step, isOurOwn)
                    }
                } else when (step) {
                    is VerificationReadyEventContent -> {
                        if (currentState is OwnRequest || currentState is TheirRequest)
                            onReady(step)
                        else cancelUnexpectedMessage(currentState)
                    }

                    is VerificationStartEventContent -> {
                        if (currentState is Ready || currentState is Start)
                            onStart(step, sender, isOurOwn)
                        else cancelUnexpectedMessage(currentState)
                    }

                    is VerificationDoneEventContent -> {
                        if (currentState is Start || currentState is WaitForDone)
                            onDone(isOurOwn)
                        else cancelUnexpectedMessage(currentState)
                    }

                    is VerificationCancelEventContent -> {
                        onCancel(step, isOurOwn)
                    }

                    else -> when (currentState) {
                        is Start -> currentState.method.handleVerificationStep(step, isOurOwn)
                        else -> cancelUnexpectedMessage(currentState)
                    }
                }
            } catch (error: Exception) {
                cancel(Code.InternalError, "something went wrong: ${error.message}")
            }
        }
    }

    private suspend fun cancelUnexpectedMessage(currentState: ActiveVerificationState) {
        cancel(UnexpectedMessage, "this verification is at step ${currentState::class.simpleName}")
    }

    private fun onReady(step: VerificationReadyEventContent) {
        if (theirDeviceId == null && step.fromDevice != ownDeviceId) theirDeviceId = step.fromDevice
        mutableState.value = Ready(
            ownDeviceId,
            step.methods.intersect(supportedMethods),
            relatesTo,
            transactionId,
            ::sendVerificationStepAndHandleIt
        )
    }

    private suspend fun onStart(step: VerificationStartEventContent, sender: UserId, isOurOwn: Boolean) {
        val senderDevice = step.fromDevice
        val currentState = state.value
        suspend fun setNewStartEvent(weStartedVerification: Boolean) {
            log.debug { "set new start event $step from $sender ($senderDevice)" }
            val method = when (step) {
                is SasStartEventContent ->
                    ActiveSasVerificationMethod.create(
                        startEventContent = step,
                        weStartedVerification = weStartedVerification,
                        ownUserId = ownUserId,
                        ownDeviceId = ownDeviceId,
                        theirUserId = theirUserId,
                        theirDeviceId = theirDeviceId
                            ?: throw IllegalArgumentException("their device id should never be null at this step"),
                        relatesTo = relatesTo,
                        transactionId = transactionId,
                        sendVerificationStep = ::sendVerificationStepAndHandleIt,
                        keyStore = keyStore,
                        keyTrustService = keyTrustService,
                        json = json,
                    )
            }
            if (method != null) // the method already called cancel
                mutableState.value = Start(method, sender, senderDevice)
        }
        if (currentState is Start) {
            val currentStartContent = currentState.method.startEventContent
            if (currentStartContent is SasStartEventContent) {
                val userIdComparison = currentState.senderUserId.full.compareTo(sender.full)
                when {
                    userIdComparison > 0 -> setNewStartEvent(ownUserId == sender)
                    userIdComparison < 0 -> {// do nothing (we keep the current Start)
                    }

                    else -> {
                        val deviceIdComparison = currentState.senderDeviceId.compareTo(senderDevice)
                        when {
                            deviceIdComparison > 0 -> setNewStartEvent(ownDeviceId == senderDevice)
                            else -> { // do nothing (we keep the current Start)
                            }
                        }
                    }
                }
            } else cancel(UnknownMethod, "the users selected two different verification methods")
        } else setNewStartEvent(isOurOwn)
    }

    private fun onDone(isOurOwn: Boolean) {
        val oldState = mutableState.value
        val newState =
            if (oldState is WaitForDone && (isOurOwn && !oldState.isOurOwn || !isOurOwn && oldState.isOurOwn)) Done
            else WaitForDone(isOurOwn)
        mutableState.value = newState
    }

    private suspend fun onCancel(step: VerificationCancelEventContent, isOurOwn: Boolean) {
        mutableState.value = Cancel(step, isOurOwn)
        when (val currentState = state.value) {
            is Start -> {
                currentState.method.handleVerificationStep(step, isOurOwn)
            }

            else -> {}
        }
    }

    protected abstract suspend fun sendVerificationStep(step: VerificationStep)

    protected suspend fun sendVerificationStepAndHandleIt(step: VerificationStep) {
        log.trace { "send verification step and handle it: $step" }
        when (step) {
            is VerificationCancelEventContent -> {
                val sendEvent = state.value !is Cancel
                handleVerificationStep(step, ownUserId, true)
                if (sendEvent) {
                    try {
                        sendVerificationStep(step)
                    } catch (error: Exception) {
                        log.warn(error) { "could not send cancel event: ${error.message}" }
                        // we just ignore when we could not send it, because it would time out on the other side anyway
                    }
                }
            }

            else -> try {
                handleVerificationStep(step, ownUserId, true)
                sendVerificationStep(step)
            } catch (error: Exception) {
                log.debug { "could not send step $step because: ${error.message}" }
                handleVerificationStep(
                    VerificationCancelEventContent(
                        Code.InternalError,
                        "problem sending step",
                        relatesTo,
                        transactionId
                    ), ownUserId,
                    true
                )
            }
        }
    }

    protected suspend fun cancel(code: Code, reason: String) {
        sendVerificationStepAndHandleIt(VerificationCancelEventContent(code, reason, relatesTo, transactionId))
    }

    override suspend fun cancel(message: String) {
        log.debug { "user cancelled verification" }
        cancel(Code.User, message)
    }
}