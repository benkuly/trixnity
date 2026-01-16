package de.connect2x.trixnity.vodozemac.megolm

import de.connect2x.trixnity.vodozemac.bindings.megolm.SessionConfigBindings
import de.connect2x.trixnity.vodozemac.utils.Managed
import de.connect2x.trixnity.vodozemac.utils.NativePointer
import de.connect2x.trixnity.vodozemac.utils.managedReachableScope

class MegolmSessionConfig internal constructor(ptr: NativePointer) :
    Managed(ptr, SessionConfigBindings::free) {

    val version: Version
        get() = managedReachableScope {
            when (val version = SessionConfigBindings.version(ptr)) {
                1 -> Version.VERSION_1
                2 -> Version.VERSION_2
                else -> error("Unknown version $version")
            }
        }

    companion object {
        operator fun invoke(version: Version = Version.VERSION_2): MegolmSessionConfig =
            when (version) {
                Version.VERSION_1 -> v1()
                Version.VERSION_2 -> v2()
            }

        fun v1(): MegolmSessionConfig = MegolmSessionConfig(SessionConfigBindings.version1())

        fun v2(): MegolmSessionConfig = MegolmSessionConfig(SessionConfigBindings.version2())
    }

    enum class Version {
        VERSION_1,
        VERSION_2
    }
}
