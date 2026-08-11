package net.zerocloud.pdf.provider;

/** Stable Capability Provider failure categories. */
public enum ProviderFailureCode {
    PROVIDER_NOT_FOUND("No eligible Capability Provider is registered."),
    PROVIDER_UNAVAILABLE("The selected Capability Provider is unavailable."),
    REMOTE_DISCLOSURE_NOT_AUTHORIZED(
            "Remote document disclosure was not explicitly authorized."),
    INPUT_LIMIT_EXCEEDED("The Provider input limit was exceeded."),
    OUTPUT_LIMIT_EXCEEDED("The Provider output limit was exceeded."),
    DEADLINE_EXCEEDED("The Provider execution deadline expired."),
    STARTUP_FAILED("The Provider engine could not be started."),
    EXECUTION_FAILED("The Provider engine did not complete successfully."),
    MALFORMED_OUTPUT("The Provider engine returned malformed output.");

    private final String safeDiagnostic;

    ProviderFailureCode(String safeDiagnostic) {
        this.safeDiagnostic = safeDiagnostic;
    }

    String getSafeDiagnostic() {
        return safeDiagnostic;
    }
}
