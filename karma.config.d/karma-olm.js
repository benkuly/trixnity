config.mime = {
    "application/wasm" : ["wasm"]
}
config.proxies = {
    "/olm.wasm": require("path").resolve("../../node_modules/@matrix-org/olm/olm.wasm")
}
config.files = [
    {"pattern":"../../../node_modules/@matrix-org/olm/olm.wasm","watched": false, "served": true, "included": false,"type":"wasm"},
].concat(config.files)