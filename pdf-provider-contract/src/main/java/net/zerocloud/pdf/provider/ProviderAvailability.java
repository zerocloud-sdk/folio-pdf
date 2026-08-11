package net.zerocloud.pdf.provider;

/** Availability reported by one immutable Provider registration. */
public enum ProviderAvailability {
    /** The Provider's required engine is available for use. */
    AVAILABLE,

    /** The Provider's required engine is not currently available. */
    UNAVAILABLE
}
