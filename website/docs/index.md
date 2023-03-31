---
sidebar_position: 0
---

# Introduction

Trixnity is a multiplatform [Matrix](https://matrix.org) SDK written in Kotlin.
You can write clients, bots, appservices and servers with
it. This SDK supports JVM (also Android), JS and Native as targets for most
modules.
[Ktor](https://github.com/ktorio/ktor) is used for the HTTP client/server and
[kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) for the
serialization/deserialization.

Trixnity aims to be strongly typed, customizable and easy to use. You can
register custom events and Trixnity will take
care, that you can send and receive that type.