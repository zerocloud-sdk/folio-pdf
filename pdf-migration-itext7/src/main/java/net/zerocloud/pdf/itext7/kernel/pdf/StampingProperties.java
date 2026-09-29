package net.zerocloud.pdf.itext7.kernel.pdf;

import java.util.Objects;
import net.zerocloud.pdf.SaveMode;

/**
 * Explicit publication selection for an existing document. The default is
 * {@link SaveMode#REWRITE}; append mode preserves the complete Source revision
 * and applies the Native Existing Signature protection policy. This declaration
 * neither creates signatures nor verifies their cryptographic validity.
 *
 * @since 0.1.0
 */
public final class StampingProperties {

    static {
        FacadeClasspathGuard.requireSingleEdition();
    }

    private SaveMode saveMode = SaveMode.REWRITE;

    /** Creates a declaration with the default rewrite behavior. */
    public StampingProperties() {
    }

    /**
     * Copies the current publication selection.
     * @param other the declaration to copy
     */
    public StampingProperties(StampingProperties other) {
        saveMode = Objects.requireNonNull(other, "other").saveMode;
    }

    /**
     * Selects explicit incremental publication. A primary Source is required;
     * signed Sources admit only proven non-Widget annotation changes under a
     * sole coherent DocMDP P=3 restriction.
     * @return this declaration
     */
    public StampingProperties useAppendMode() {
        saveMode = SaveMode.INCREMENTAL;
        return this;
    }

    SaveMode saveMode() {
        return saveMode;
    }
}
