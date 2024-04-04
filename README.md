![Version](https://gitlab.com/trixnity/trixnity/-/badges/release.svg?value_width=90)

# Trixnity - Multiplatform Matrix SDK

Trixnity is a multiplatform [Matrix](matrix.org) SDK written in Kotlin.
You can write clients, bots, appservices and servers with it.
This SDK supports JVM (also Android), JS and Native as targets for most modules.
[Ktor](https://github.com/ktorio/ktor) is used for the HTTP client/server and
[kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) for the serialization/deserialization.

Trixnity aims to be strongly typed, customizable and easy to use.
You can register custom events and Trixnity will take care, that you can send and receive that type.

If you just want to implement a messenger without having to dive to deep into trixnity-client and Matrix, consider
using [trixnity-messenger](https://gitlab.com/connect2x/trixnity-messenger).

[Website with documentation](https://trixnity.gitlab.io/trixnity)

**You need help? Ask your questions in [#trixnity:imbitbu.de](https://matrix.to/#/#trixnity:imbitbu.de).**

## Development

### Upgrade lock

If any dependency is upgraded, the locks also have to be upgraded. This is done with the following command:

Run `./gradlew dependenciesForAll --write-locks --no-parallel`.
