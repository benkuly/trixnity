config = {
    ...config,
    resolve: {
        ...config.resolve,
        fallback: {
            ...config.resolve?.fallback,
            crypto: false,
            fs: false,
            path: false,
        },
    },
    module: {
        ...config.module,
        rules: [
            ...(config.module?.rules || []),
            { test: /\.wasm$/, type: "asset/resource" },
        ],
    },
};