package net.folivo.trixnity.client.notification

import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

fun createNotificationModule() = module {
    singleOf(::PushRuleConditionMatcherImpl) { bind<PushRuleConditionMatcher>() }
    singleOf(::PushRuleMatcherImpl) { bind<PushRuleMatcher>() }
    singleOf(::EvaluatePushRulesImpl) { bind<EvaluatePushRules>() }
    singleOf(::NotificationServiceImpl) { bind<NotificationService>() }
}