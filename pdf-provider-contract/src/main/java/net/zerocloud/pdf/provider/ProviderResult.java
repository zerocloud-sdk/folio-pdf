package net.zerocloud.pdf.provider;

import java.util.Objects;

/** Immutable detached bytes returned by a Capability Provider. */
public final class ProviderResult {

    private final byte[] output;

    private ProviderResult(byte[] output) {
        this.output = output.clone();
    }

    public static ProviderResult of(byte[] output) {
        return new ProviderResult(Objects.requireNonNull(output, "output"));
    }

    public byte[] getOutput() {
        return output.clone();
    }

    public long getOutputLength() {
        return output.length;
    }
}
