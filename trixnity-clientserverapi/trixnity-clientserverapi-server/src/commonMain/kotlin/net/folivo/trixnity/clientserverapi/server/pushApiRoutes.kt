package net.folivo.trixnity.clientserverapi.server

import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.server.matrixEndpoint
import net.folivo.trixnity.clientserverapi.model.push.*
import net.folivo.trixnity.core.model.push.PushRule
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

internal fun Route.pushApiRoutes(
    handler: PushApiHandler,
    json: Json,
    contentMappings: EventContentSerializerMappings,
) {
    authenticate {
        matrixEndpoint<GetPushers, GetPushers.Response>(json, contentMappings) {
            handler.getPushers(this)
        }
        matrixEndpoint<SetPushers, SetPushers.Request>(json, contentMappings) {
            handler.setPushers(this)
        }
        matrixEndpoint<GetNotifications, GetNotifications.Response>(json, contentMappings) {
            handler.getNotifications(this)
        }
        matrixEndpoint<GetPushRules, GetPushRules.Response>(json, contentMappings) {
            handler.getPushRules(this)
        }
        matrixEndpoint<GetPushRule, PushRule>(json, contentMappings) {
            handler.getPushRule(this)
        }
        matrixEndpoint<SetPushRule, SetPushRule.Request>(json, contentMappings) {
            handler.setPushRule(this)
        }
        matrixEndpoint<DeletePushRule>(json, contentMappings) {
            handler.deletePushRule(this)
        }
        matrixEndpoint<GetPushRuleActions, Unit, GetPushRuleActions.Response>(json, contentMappings) {
            handler.getPushRuleActions(this)
        }
        matrixEndpoint<SetPushRuleActions, SetPushRuleActions.Request, Unit>(json, contentMappings) {
            handler.setPushRuleActions(this)
        }
        matrixEndpoint<GetPushRuleEnabled, Unit, GetPushRuleEnabled.Response>(json, contentMappings) {
            handler.getPushRuleEnabled(this)
        }
        matrixEndpoint<SetPushRuleEnabled, SetPushRuleEnabled.Request, Unit>(json, contentMappings) {
            handler.setPushRuleEnabled(this)
        }
    }
}