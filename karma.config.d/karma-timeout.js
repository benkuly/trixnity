if (!!config.client) {
    config.client.mocha = config.client.mocha || {}
    config.client.mocha.timeout = 30000
}
config.browserNoActivityTimeout = 30000