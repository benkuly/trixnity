const firefoxTmpDir = require("path").resolve("../../../firefox-tmp")
if (!require('fs').existsSync(firefoxTmpDir)){
    require('fs').mkdirSync(firefoxTmpDir);
}
config.set({
  customLaunchers: {
      'FirefoxHeadless': {
          base: 'Firefox',
          flags: [ '-headless' ],
          profile: firefoxTmpDir,
      }
  }
});