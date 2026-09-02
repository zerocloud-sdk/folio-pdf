package net.zerocloud.pdf.query;

import net.zerocloud.pdf.DocumentQuery;
import net.zerocloud.pdf.PdfVersionInfo;

/** Reports the current document's declared and effective PDF version. */
public final class DocumentVersion implements DocumentQuery<PdfVersionInfo> {

    /** The immutable version query. */
    public static final DocumentVersion INSTANCE = new DocumentVersion();

    private DocumentVersion() {
    }
}
