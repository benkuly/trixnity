package net.folivo.trixnity.client.notification

import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

fun createNotificationModule() = module {
    singleOf(::NotificationServiceImpl) { bind<NotificationService>() }
}