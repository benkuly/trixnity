---
sidebar_position: 300
---

# Contributions

## Build this project

### Android SDK

Install the Android SDK and add a file named `local.properties` with the
following content in the project root:

```properties
sdk.dir=/path/to/android/sdk
```

## Upgrade lock

If any dependency is upgraded, the locks also have to be upgraded. This is done with the following command:

Run `./gradlew dependenciesForAll --write-locks --no-parallel`.