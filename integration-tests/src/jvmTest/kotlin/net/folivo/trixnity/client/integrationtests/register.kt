package net.folivo.trixnity.client.integrationtests

import io.kotest.matchers.types.shouldBeInstanceOf
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.authentication.AccountType
import net.folivo.trixnity.client.api.authentication.RegisterResponse
import net.folivo.trixnity.client.api.uia.AuthenticationRequest
import net.folivo.trixnity.client.api.uia.UIA

suspend fun MatrixApiClient.register(username: String? = null, password: String): MatrixClient.Companion.LoginInfo {
    val registerStep = authentication.register(
        password = password,
        username = username,
        accountType = AccountType.USER,
    )
    registerStep.shouldBeInstanceOf<UIA.UIAStep<RegisterResponse>>()
    val registerResult = registerStep.authenticate(AuthenticationRequest.Dummy)
    registerResult.shouldBeInstanceOf<UIA.UIASuccess<RegisterResponse>>()
    val (userId, deviceId, accessToken) = registerResult.value
    requireNotNull(deviceId)
    requireNotNull(accessToken)
    return MatrixClient.Companion.LoginInfo(userId, deviceId, accessToken)
}