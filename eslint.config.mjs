export default [
    {
        files: ["frontend/src/**/*.js"],
        languageOptions: {
            ecmaVersion: "latest",
            sourceType: "script",
            globals: {
                document: "readonly",
                window: "readonly",
                fetch: "readonly",
                URLSearchParams: "readonly",
                console: "readonly",
                alert: "readonly",
                confirm: "readonly",
                setTimeout: "readonly",
                clearTimeout: "readonly",
                AbortController: "readonly"
            }
        },
        rules: {
            "no-undef": "error",
            "no-unreachable": "error",
            "no-dupe-keys": "error"
        }
    }
];
