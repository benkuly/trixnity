package net.folivo.trixnity.test.utils

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration

interface CoroutineTest {
    val defaultTimeout: Duration?
        get() = null

    val testScope: TestScope
}

fun CoroutineTest.scheduleSetup(block: ScheduledTaskLambda) = testScope.coroutineContext.scheduleSetup(block)

fun CoroutineTest.scheduleTeardown(block: ScheduledTaskLambda) = testScope.coroutineContext.scheduleTeardown(block)

fun <T> CoroutineTest.runTest(
    timeout: Duration? = defaultTimeout,
    setup: suspend TestScope.() -> T,
    testBody: suspend TestScope.(T) -> Unit
): TestResult {
    suspend fun TestScope.wrappedTest() = withScheduledTasks(setup, testBody)

    return if (timeout == null) testScope.runTest { wrappedTest() }
    else testScope.runTest(timeout) { wrappedTest() }
}

fun CoroutineTest.runTest(
    timeout: Duration? = defaultTimeout,
    testBody: suspend TestScope.() -> Unit
): TestResult = runTest(timeout, {}) { testBody() }

fun <T> CoroutineTest.suspendLazy(block: suspend TestScope.() -> T): LazySuspend<T> =
    LazySuspendImpl(testScope.coroutineContext, block)

@Suppress("FunctionName")
fun CoroutineTestScope(coroutineContext: CoroutineContext = EmptyCoroutineContext): TestScope =
    TestScope(coroutineContext + ScheduledTasksContext())

internal typealias ScheduledTaskLambda = suspend TestScope.() -> Unit
internal typealias SchuduledTaskLambdas = List<ScheduledTask>

internal sealed interface ScheduledTask {
    val task: ScheduledTaskLambda

    data class Setup(override val task: ScheduledTaskLambda) : ScheduledTask
    data class Teardown(override val task: ScheduledTaskLambda) : ScheduledTask
}

internal data class ScheduledTasksContext(
    val tasks: MutableStateFlow<SchuduledTaskLambdas?> = MutableStateFlow(emptyList())
) : AbstractCoroutineContextElement(ScheduledTasksContext) {
    val isScheduled: Boolean
        get() = tasks.value == null

    companion object Key : CoroutineContext.Key<ScheduledTasksContext>

    override fun toString(): String = "ScheduledTasksContext(tasks = $tasks)"
}

internal fun CoroutineContext.scheduleSetup(block: ScheduledTaskLambda) {
    val tasks = this[ScheduledTasksContext]?.tasks
    requireNotNull(tasks) { "cannot schedule task without ScheduledTasksContext present" }

    tasks.update {
        checkNotNull(it) { "cannot schedule setup tasks after runTest" }
        it + ScheduledTask.Setup(block)
    }
}

internal fun CoroutineContext.scheduleTeardown(block: ScheduledTaskLambda) {
    val tasks = this[ScheduledTasksContext]?.tasks
    requireNotNull(tasks) { "cannot schedule task without ScheduledTasksContext present" }

    tasks.update {
        checkNotNull(it) { "cannot schedule setup tasks after runTest" }
        it + ScheduledTask.Teardown(block)
    }
}

private suspend fun <T> TestScope.withScheduledTasks(
    setup: suspend TestScope.() -> T,
    testBody: suspend TestScope.(T) -> Unit
) {
    val scheduledTasks = currentCoroutineContext()[ScheduledTasksContext]
    requireNotNull(scheduledTasks) { "cannot schedule tasks without ScheduledTasks present" }

    val tasks = scheduledTasks.tasks.getAndUpdate { null }
    checkNotNull(tasks) { "cannot runTest after already running once" }

    val setupTasks = tasks.mapNotNull { (it as? ScheduledTask.Setup)?.task }
    val teardownTasks = tasks.mapNotNull { (it as? ScheduledTask.Teardown)?.task }.reversed()

    coroutineScope {
        setupTasks.map { launch { it() } }.joinAll()
    }
    testBody(setup())

    coroutineScope {
        teardownTasks.map { launch { it() } }.joinAll()
    }
}