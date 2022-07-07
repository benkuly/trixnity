package net.folivo.trixnity.client.verification

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import net.folivo.trixnity.client.key.IKeyTrustService
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.verification.ActiveVerificationState.*
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.RelatesTo
import net.folivo.trixnity.core.model.events.m.key.verification.*
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent.Code
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent.Code.UnexpectedMessage
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent.Code.UnknownMethod
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationStartEventContent.SasStartEventContent

private val log = KotlinLogging.logger {}

abstract class ActiveVerification(
    request: VerificationRequest,
    requestIsFromOurOwn: Boolean,
    protected val ownUserId: UserId,
    protected val ownDeviceId: String,
    val theirUserId: UserId,
    theirInitialDeviceId: String?,
    val timestamp: Long,
    protected val supportedMethods: Set<VerificationMethod>,
    val relatesTo: RelatesTo.Reference?,
    val transactionId: String?,
    protected val store: Store,
    private val keyTrustService: IKeyTrustService,
    protected val json: Json,
) {
    var theirDeviceId: String? = theirInitialDeviceId
        private set

    private val mutex = Mutex()

    protected val mutableState: MutableStateFlow<ActiveVerificationState> =
        MutableStateFlow(
            if (requestIsFromOurOwn) OwnRequest(request)
            else TheirRequest(
                request, ownDeviceId, supportedMethods, relatesTo, transactionId, ::sendVerificationStepAndHandleIt
            )
        )
    val state = mutableState.asStateFlow()

    private val lifecycleStarted = MutableStateFlow(false)
    protected abstract suspend fun lifecycle()
    internal suspend fun startLifecycle(scope: CoroutineScope): Boolean {
        log.debug { "start lifecycle of verification ${transactionId ?: relatesTo}" }
        return if (!lifecycleAlreadyStarted()) {
            scope.launch {
                lifecycle()
                log.debug { "stop lifecycle of verification ${transactionId ?: relatesTo}" }
            }
            true
        } else false
    }

    private fun lifecycleAlreadyStarted() = lifecycleStarted.getAndUpdate { true }

    protected suspend fun handleIncomingVerificationStep(
        step: VerificationStep,
        sender: UserId,
        isOurOwn: Boolean
    ) {
        mutex.withLock { // we just want to be sure, that only one coroutine can access this simultaneously
            handleVerificationStep(step, sender, isOurOwn)
        }
    }

    private suspend fun handleVerificationStep(step: VerificationStep, sender: UserId, isOurOwn: Boolean) {
        try {
            log.debug { "handle verification step: $step from $sender" }
            if (sender != theirUserId && sender != ownUserId)
                cancel(Code.UserMismatch, "the user did not match the expected user, we want to verify")
            if (!(relatesTo != null && step.relatesTo == relatesTo || transactionId != null && step.transactionId == transactionId))
                cancel(Code.UnknownTransaction, "transaction is unknown")
            val currentState = state.value
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
                    if (currentState is Start || currentState is PartlyDone)
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
        } catch (error: Throwable) {
            cancel(Code.InternalError, "something went wrong: ${error.message}")
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
        suspend fun setNewStartEvent() {
            log.debug { "set new start event $step from $sender ($senderDevice)" }
            val method = when (step) {
                is SasStartEventContent ->
                    ActiveSasVerificationMethod.create(
                        startEventContent = step,
                        weStartedVerification = isOurOwn,
                        ownUserId = ownUserId,
                        ownDeviceId = ownDeviceId,
                        theirUserId = theirUserId,
                        theirDeviceId = theirDeviceId
                            ?: throw IllegalArgumentException("their device id should never be null at this step"),
                        relatesTo = relatesTo,
                        transactionId = transactionId,
                        sendVerificationStep = ::sendVerificationStepAndHandleIt,
                        store = store,
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
                    userIdComparison > 0 -> setNewStartEvent()
                    userIdComparison < 0 -> {// do nothing (we keep the current Start)
                    }
                    else -> {
                        val deviceIdComparison = currentState.senderDeviceId.compareTo(step.fromDevice)
                        when {
                            deviceIdComparison > 0 -> setNewStartEvent()
                            else -> {// do nothing (we keep the current Start)
                            }
                        }
                    }
                }
            } else cancel(UnknownMethod, "the users selected two different verification methods")
        } else setNewStartEvent()
    }

    private fun onDone(isOurOwn: Boolean) {
        val oldState = mutableState.value
        val newState =
            if (oldState is PartlyDone && (isOurOwn && !oldState.isOurOwn || !isOurOwn && oldState.isOurOwn)) Done
            else PartlyDone(isOurOwn)
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

    private suspend fun sendVerificationStepAndHandleIt(step: VerificationStep) {
        log.trace { "send verification step and handle it: $step" }
        when (step) {
            is VerificationCancelEventContent -> {
                if (state.value !is Cancel)
                    try {
                        sendVerificationStep(step)
                    } catch (error: Throwable) {
                        log.warn(error) { "could not send cancel event: ${error.message}" }
                        // we just ignore when we could not send it, because it would time out on the other side anyway
                    }
                handleVerificationStep(step, ownUserId, true)
            }
            else -> try {
                sendVerificationStep(step)
                handleVerificationStep(step, ownUserId, true)
            } catch (error: Throwable) {
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

    suspend fun cancel(message: String = "user cancelled verification") {
        log.debug { "user cancelled verification" }
        cancel(Code.User, message)
    }
}