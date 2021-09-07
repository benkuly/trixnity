const path = require("path");
config.set({
  "mime": {
     "application/wasm" : ["wasm"]
  },
  "files": [
    "../adapter-browser.js",
     {"pattern":"../../../node_modules/@matrix-org/olm/olm.wasm","watched": false, "served": true, "included": false,"type":"wasm"}
  ],
  proxies: {
        "/olm.wasm": path.resolve("../../node_modules/@matrix-org/olm/olm.wasm"),
      },
});