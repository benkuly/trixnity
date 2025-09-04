---
sidebar_position: 51
---

# Olm

We build [libolm](https://gitlab.matrix.org/matrix-org/olm) for various targets.
The currently supported targets can be found [here](https://gitlab.com/trixnity/olm-binaries/-/blob/main/build.sh). If
your platform is not supported, feel free to open a merge request or issue.

If you are using a module, which depends on `trixnity-olm` you may need to do some extra steps:

- JS: If you are using webpack you will likely need to add a rule for wasm files like
  [here](https://gitlab.com/trixnity/trixnity/-/blob/main/webpack.config.d/webpack-olm.js)
- JVM: If your platform is not supported yet, you can
  [download or build libolm yourself](https://gitlab.matrix.org/matrix-org/olm)
  and make it available to your JVM (e.g.
  with `-Djna.library.path="build/olm"`).