package net.folivo.trixnity.client.verification

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.getAndUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.key.KeyService
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.verification.ActiveVerificationState.*
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.key.verification.*
import net.folivo.trixnity.core.model.events.m.key.verification.CancelEventContent.Code
import net.folivo.trixnity.core.model.events.m.key.verification.CancelEventContent.Code.UnexpectedMessage
import net.folivo.trixnity.core.model.events.m.key.verification.CancelEventContent.Code.UnknownMethod
import net.folivo.trixnity.core.model.events.m.key.verification.StartEventContent.SasStartEventContent
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

abstract class ActiveVerification(
    request: VerificationRequest,
    requestIsFromOurOwn: Boolean,
    protected val ownUserId: UserId,
    protected val ownDeviceId: String,
    val theirUserId: UserId,
    theirInitialDeviceId: String?,
    val timestamp: Long,
    protected val supportedMethods: Set<VerificationMethod>,
    val relatesTo: VerificationStepRelatesTo?,
    val transactionId: String?,
    protected val store: Store,
    private val keyService: KeyService,
    protected val json: Json,
    private val loggerFactory: LoggerFactory
) {
    private val log = newLogger(loggerFactory)

    var theirDeviceId: String? = theirInitialDeviceId
        private set

    private val mutex = Mutex()

    private val _state: MutableStateFlow<ActiveVerificationState> =
        MutableStateFlow(
            if (requestIsFromOurOwn) OwnRequest(request)
            else TheirRequest(
                request, ownDeviceId, supportedMethods, relatesTo, transactionId, ::sendVerificationStepAndHandleIt
            )
        )
    val state = _state.asStateFlow()

    private val lifecycleStarted = atomic(false)
    protected abstract suspend fun lifecycle(scope: CoroutineScope)
    internal suspend fun startLifecycle(scope: CoroutineScope): Boolean {
        log.debug { "start lifecycle of verification" }
        return if (!lifecycleAlreadyStarted()) {
            lifecycle(scope)
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
            when (step) {
                is ReadyEventContent -> {
                    if (currentState is OwnRequest || currentState is TheirRequest)
                        onReady(step)
                    else cancelUnexpectedMessage(currentState)
                }
                is StartEventContent -> {
                    if (currentState is Ready || currentState is Start)
                        onStart(step, sender, isOurOwn)
                    else cancelUnexpectedMessage(currentState)
                }
                is DoneEventContent -> {
                    if (currentState is Start || currentState is PartlyDone)
                        onDone(isOurOwn)
                    else cancelUnexpectedMessage(currentState)
                }
                is CancelEventContent -> {
                    onCancel(step, sender, isOurOwn)
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

    private fun onReady(step: ReadyEventContent) {
        if (theirDeviceId == null && step.fromDevice != ownDeviceId) theirDeviceId = step.fromDevice
        _state.value = Ready(
            ownDeviceId,
            step.methods.intersect(supportedMethods),
            relatesTo,
            transactionId,
            ::sendVerificationStepAndHandleIt
        )
    }

    private suspend fun onStart(step: StartEventContent, sender: UserId, isOurOwn: Boolean) {
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
                        keyService = keyService,
                        json = json,
                        loggerFactory = loggerFactory
                    )
            }
            if (method != null) // the method already called cancel
                _state.value = Start(method, sender, senderDevice)
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
        _state.update { oldState ->
            if (oldState is PartlyDone && (isOurOwn && !oldState.isOurOwn || !isOurOwn && oldState.isOurOwn)) Done
            else PartlyDone(isOurOwn)
        }
    }

    private suspend fun onCancel(step: CancelEventContent, sender: UserId, isOurOwn: Boolean) {
        _state.value = Cancel(step, sender)
        when (val currentState = state.value) {
            is Start -> {
                currentState.method.handleVerificationStep(step, isOurOwn)
            }
            else -> {}
        }
    }

    protected abstract suspend fun sendVerificationStep(step: VerificationStep)

    private suspend fun sendVerificationStepAndHandleIt(step: VerificationStep) {
        when (step) {
            is CancelEventContent -> {
                if (state.value !is Cancel)
                    try {
                        sendVerificationStep(step)
                    } catch (error: Throwable) {
                        log.debug { "could not send cancel event: ${error.message}" }
                        // we just ignore when we could not send it, because it would time out on the other side anyways
                    }
                handleVerificationStep(step, ownUserId, true)
            }
            else -> try {
                sendVerificationStep(step)
                handleVerificationStep(step, ownUserId, true)
            } catch (error: Throwable) {
                log.debug { "could not send step $step because: ${error.message}" }
                handleVerificationStep(
                    CancelEventContent(Code.InternalError, "problem sending step", relatesTo, transactionId), ownUserId,
                    true
                )
            }
        }
    }

    protected suspend fun cancel(code: Code, reason: String) {
        sendVerificationStepAndHandleIt(CancelEventContent(code, reason, relatesTo, transactionId))
    }

    suspend fun cancel() {
        cancel(Code.User, "user cancelled verification")
    }
}