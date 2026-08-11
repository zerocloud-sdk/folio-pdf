package net.zerocloud.pdf.provider;

/** How the engine used by a Capability Provider is distributed. */
public enum ProviderDistribution {
    /** The engine is included in the Provider artifact. */
    BUNDLED,

    /** The operator installs the engine separately from the Provider artifact. */
    SEPARATELY_INSTALLED,

    /** The engine is supplied by the embedding application. */
    CALLER_SUPPLIED,

    /** The engine is operated as a remote service. */
    REMOTE_SERVICE
}
