package net.zerocloud.pdf.provider;

import java.util.Objects;

/** Detached Provider selection and result from one completed invocation. */
public final class ProviderExecution {

    private final ProviderSelection selection;
    private final ProviderResult result;

    ProviderExecution(ProviderSelection selection, ProviderResult result) {
        this.selection = Objects.requireNonNull(selection, "selection");
        this.result = Objects.requireNonNull(result, "result");
    }

    public ProviderSelection getSelection() {
        return selection;
    }

    public ProviderResult getResult() {
        return result;
    }
}
