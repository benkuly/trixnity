package net.folivo.trixnity.client.api

expect fun runBlockingTest(block: suspend () -> Unit) // TODO workaround for https://youtrack.jetbrains.com/issue/KT-22228