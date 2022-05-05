@file:JsModule("@matrix-org/olm")
@file:JsNonModule

package net.folivo.trixnity.olm

import kotlin.js.Promise


external fun init(opts: Any? = definedExternally): Promise<Unit>
