package net.zerocloud.pdf;

import java.util.Objects;
import java.util.Optional;

/** Detached PDF version declarations and their effective interpretation. */
public final class PdfVersionInfo {

    private final PdfVersion headerVersion;
    private final PdfVersion catalogVersion;
    private final PdfVersion effectiveVersion;

    PdfVersionInfo(
            PdfVersion headerVersion,
            PdfVersion catalogVersion,
            PdfVersion effectiveVersion) {
        this.headerVersion = Objects.requireNonNull(
                headerVersion,
                "headerVersion");
        this.catalogVersion = catalogVersion;
        this.effectiveVersion = Objects.requireNonNull(
                effectiveVersion,
                "effectiveVersion");
    }

    /** @return the version declared by the PDF header */
    public PdfVersion getHeaderVersion() {
        return headerVersion;
    }

    /** @return the optional catalog {@code /Version} declaration */
    public Optional<PdfVersion> getCatalogVersion() {
        return Optional.ofNullable(catalogVersion);
    }

    /** @return the effective document version */
    public PdfVersion getEffectiveVersion() {
        return effectiveVersion;
    }
}
