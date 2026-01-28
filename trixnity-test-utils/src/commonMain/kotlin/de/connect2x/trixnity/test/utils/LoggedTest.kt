package de.connect2x.trixnity.test.utils

import de.connect2x.lognity.api.backend.Backend
import de.connect2x.lognity.test.TestBackend
import kotlin.test.BeforeTest

interface LoggedTest {
    @BeforeTest
    fun setupLogged() {
        Backend.setOnce(TestBackend)
    }
}