package net.zerocloud.pdf.query;

import net.zerocloud.pdf.DocumentQuery;
import net.zerocloud.pdf.PdfDictionary;

/**
 * Reports the document information dictionary as a detached immutable PDF
 * dictionary after all preceding session commands.
 *
 * <p>An absent information dictionary reports as empty. The detached result
 * remains usable after the Session ends. Information graphs that contain
 * streams or indirect references fail with
 * {@link net.zerocloud.pdf.DocumentFailureCode#QUERY_FAILED}.</p>
 *
 * @since 0.1.0
 */
public final class DocumentInfo implements DocumentQuery<PdfDictionary> {

    /** The currently supported query representation version. */
    public static final int VERSION_1 = 1;

    /** The immutable query instance. */
    public static final DocumentInfo INSTANCE = new DocumentInfo();

    private DocumentInfo() {
    }

    /**
     * Returns the query representation version.
     *
     * @return {@link #VERSION_1}
     */
    public int getVersion() {
        return VERSION_1;
    }
}
