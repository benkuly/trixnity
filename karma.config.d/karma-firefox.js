const randomNumber = Math.floor(Math.random() * 1000000000 );
const firefoxTmpDir = require("path").resolve("../../../firefox-tmp-" + randomNumber)
require('fs').mkdirSync(firefoxTmpDir);
config.set({
  customLaunchers: {
      'FirefoxHeadless': {
          base: 'Firefox',
          flags: [ '-headless' ],
          profile: firefoxTmpDir,
      }
  }
});