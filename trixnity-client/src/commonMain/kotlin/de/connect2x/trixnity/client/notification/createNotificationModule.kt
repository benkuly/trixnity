package de.connect2x.trixnity.client.notification

import de.connect2x.trixnity.core.EventHandler
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun createNotificationModule() = module {
    singleOf(::PushRuleConditionMatcherImpl) { bind<PushRuleConditionMatcher>() }
    singleOf(::PushRuleMatcherImpl) { bind<PushRuleMatcher>() }
    singleOf(::EvaluatePushRulesImpl) { bind<EvaluatePushRules>() }
    singleOf(::EventsToNotificationUpdatesImpl) { bind<EventsToNotificationUpdates>() }
    singleOf(::NotificationEventHandler) {
        bind<EventHandler>()
        named<NotificationEventHandler>()
    }
    singleOf(::NotificationServiceImpl) { bind<NotificationService>() }
}