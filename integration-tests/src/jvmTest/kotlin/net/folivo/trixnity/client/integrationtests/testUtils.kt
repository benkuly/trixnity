package net.folivo.trixnity.client.integrationtests

import io.kotest.matchers.types.shouldBeInstanceOf
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.authentication.AccountType
import net.folivo.trixnity.client.api.authentication.RegisterResponse
import net.folivo.trixnity.client.api.uia.AuthenticationRequest
import net.folivo.trixnity.client.api.uia.UIA

const val synapseVersion = "v1.49.0" // TODO you should update this from time to time.

suspend fun MatrixApiClient.register(
    username: String? = null,
    password: String
): Result<MatrixClient.Companion.LoginInfo> {
    val registerStep = authentication.register(
        password = password,
        username = username,
        accountType = AccountType.USER,
    ).getOrThrow()
    registerStep.shouldBeInstanceOf<UIA.UIAStep<RegisterResponse>>()
    val registerResult = registerStep.authenticate(AuthenticationRequest.Dummy).getOrThrow()
    registerResult.shouldBeInstanceOf<UIA.UIASuccess<RegisterResponse>>()
    val (userId, deviceId, accessToken) = registerResult.value
    requireNotNull(deviceId)
    requireNotNull(accessToken)
    return Result.success(MatrixClient.Companion.LoginInfo(userId, deviceId, accessToken))
}
