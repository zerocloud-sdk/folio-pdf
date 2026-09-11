package net.zerocloud.pdf.itext7.kernel.pdf;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.PdfValue;
import net.zerocloud.pdf.command.UpdateDocumentInfo;
import net.zerocloud.pdf.itext7.kernel.exceptions.PdfException;
import net.zerocloud.pdf.query.DocumentInfo;

/**
 * Document-owned information view with detached, project-owned PDF Values.
 * Reads observe earlier changes; this view is confined to the document's
 * creating thread and expires at close. Native operational failures retain
 * their safe Document Failure cause in the mapped PdfException.
 * @since 0.1.0
 */
public final class PdfDocumentInfo {
    private static final DateTimeFormatter PDF_DATE_FIELDS =
            DateTimeFormatter.ofPattern("'D:'yyyyMMddHHmmss", Locale.ROOT);

    static {
        FacadeClasspathGuard.requireSingleEdition();
    }

    private final PdfDocument document;

    PdfDocumentInfo(PdfDocument document) {
        this.document = document;
    }

    /** @return the Title text, or null when absent or not a text string */
    public String getTitle() {
        return getMoreInfo("Title");
    }

    /**
     * @param value the Title text, or null to remove the entry
     * @return this information view
     */
    public PdfDocumentInfo setTitle(String value) {
        setMoreInfo("Title", value);
        return this;
    }

    /** @return the Author text, or null when absent or not a text string */
    public String getAuthor() {
        return getMoreInfo("Author");
    }

    /**
     * @param value the Author text, or null to remove the entry
     * @return this information view
     */
    public PdfDocumentInfo setAuthor(String value) {
        setMoreInfo("Author", value);
        return this;
    }

    /** @return the Subject text, or null when absent or not a text string */
    public String getSubject() {
        return getMoreInfo("Subject");
    }

    /**
     * @param value the Subject text, or null to remove the entry
     * @return this information view
     */
    public PdfDocumentInfo setSubject(String value) {
        setMoreInfo("Subject", value);
        return this;
    }

    /** @return the Keywords text, or null when absent or not a text string */
    public String getKeywords() {
        return getMoreInfo("Keywords");
    }

    /**
     * @param value the Keywords text, or null to remove the entry
     * @return this information view
     */
    public PdfDocumentInfo setKeywords(String value) {
        setMoreInfo("Keywords", value);
        return this;
    }

    /** @return the Creator text, or null when absent or not a text string */
    public String getCreator() {
        return getMoreInfo("Creator");
    }

    /**
     * @param value the Creator text, or null to remove the entry
     * @return this information view
     */
    public PdfDocumentInfo setCreator(String value) {
        setMoreInfo("Creator", value);
        return this;
    }

    /** @return the Producer text, or null when absent or not a text string */
    public String getProducer() {
        return getMoreInfo("Producer");
    }

    /**
     * @param value the Producer text, or null to remove the entry
     * @return this information view
     */
    public PdfDocumentInfo setProducer(String value) {
        setMoreInfo("Producer", value);
        return this;
    }

    /**
     * Writes the current creation date using PDF date syntax.
     * @return this information view
     */
    public PdfDocumentInfo addCreationDate() {
        setMoreInfo("CreationDate", currentPdfDate());
        return this;
    }

    /**
     * Writes the current modification date using PDF date syntax.
     * @return this information view
     */
    public PdfDocumentInfo addModDate() {
        setMoreInfo("ModDate", currentPdfDate());
        return this;
    }

    /** @return the Trapped name, or null when absent or not a name */
    public PdfName getTrapped() {
        PdfValue value = readEntry("Trapped");
        return value instanceof net.zerocloud.pdf.PdfName
                ? new PdfName(((net.zerocloud.pdf.PdfName) value).getValue()) : null;
    }

    /**
     * @param value the Trapped name, or null to remove the entry
     * @return this information view
     */
    public PdfDocumentInfo setTrapped(PdfName value) {
        UpdateDocumentInfo.Builder update = UpdateDocumentInfo.version1();
        if (value == null) {
            update.remove("Trapped");
        } else {
            update.set("Trapped", value.nativeValue());
        }
        document.executeDocument(update.build());
        return this;
    }

    /**
     * @param name the decoded entry name
     * @return decoded PDF text, or null when absent or not a string
     */
    public String getMoreInfo(String name) {
        PdfValue value = readEntry(name);
        return value instanceof net.zerocloud.pdf.PdfString
                ? new PdfString((net.zerocloud.pdf.PdfString) value).getValue() : null;
    }

    private PdfValue readEntry(String name) {
        Objects.requireNonNull(name, "name");
        try {
            return getEntries().get(net.zerocloud.pdf.PdfName.of(name));
        } catch (DocumentFailure failure) {
            throw new PdfException(failure.getCode().name() + ": " + failure.getDiagnostic(), failure);
        }
    }

    /**
     * Updates one text entry using the mapped PDF string encoding.
     * @param name the decoded entry name
     * @param value text, or null to remove the entry
     */
    public void setMoreInfo(String name, String value) {
        setMoreInfo(java.util.Collections.singletonMap(name, value));
    }

    /**
     * Applies all text replacements and removals in one validated Command.
     * @param entries decoded entry names and text values; null values remove entries
     */
    public void setMoreInfo(Map<String, String> entries) {
        Objects.requireNonNull(entries, "entries");
        UpdateDocumentInfo.Builder update = UpdateDocumentInfo.version1();
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            if (entry.getValue() == null) {
                update.remove(entry.getKey());
            } else {
                update.set(entry.getKey(), new PdfString(entry.getValue()).nativeValue());
            }
        }
        document.executeDocument(update.build());
    }

    /**
     * Reads all supported information entries under the Native graph bounds.
     * @return an immutable detached dictionary, usable after document close
     */
    public net.zerocloud.pdf.PdfDictionary getEntries() {
        return document.queryDocument(DocumentInfo.INSTANCE);
    }

    /**
     * Applies replacements and removals in one validated Command, preserving
     * unnamed entries. Streams and indirect references are rejected.
     * @param entries replacement values keyed by decoded PDF name
     * @param removedNames names to remove, disjoint from replacements
     */
    public void updateEntries(Map<String, ? extends PdfValue> entries, List<String> removedNames) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(removedNames, "removedNames");
        UpdateDocumentInfo.Builder update = UpdateDocumentInfo.version1();
        for (Map.Entry<String, ? extends PdfValue> entry : entries.entrySet()) {
            update.set(entry.getKey(), entry.getValue());
        }
        for (String name : removedNames) {
            update.remove(name);
        }
        document.executeDocument(update.build());
    }

    private static String currentPdfDate() {
        ZonedDateTime now = ZonedDateTime.now();
        StringBuilder value = new StringBuilder(PDF_DATE_FIELDS.format(now));
        int offsetMinutes = now.getOffset().getTotalSeconds() / 60;
        if (offsetMinutes == 0) {
            return value.append('Z').toString();
        }
        value.append(offsetMinutes < 0 ? '-' : '+');
        int absoluteMinutes = Math.abs(offsetMinutes);
        appendTwoDigits(value, absoluteMinutes / 60);
        value.append('\'');
        appendTwoDigits(value, absoluteMinutes % 60);
        return value.append('\'').toString();
    }

    private static void appendTwoDigits(StringBuilder value, int number) {
        if (number < 10) {
            value.append('0');
        }
        value.append(number);
    }
}
