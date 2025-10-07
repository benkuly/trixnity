const { resolve } = require("node:path");
const { writeFileSync } = require("node:fs");

const loaderPath = resolve(config.basePath, "loader.mjs")

const imports = config.files
    .map((file) => {
        if (typeof file === "object") file = file.pattern;
        return `await import ("${file}");`
    })
    .join("\n");

writeFileSync(loaderPath, `\
const originalKarmaLoader = window.__karma__.loaded
window.__karma__.loaded = () => {}

${imports}

originalKarmaLoader.call(window.__karma__)
`)

config.files = [ loaderPath ];
config.preprocessors = { [loaderPath]: ["webpack"] };