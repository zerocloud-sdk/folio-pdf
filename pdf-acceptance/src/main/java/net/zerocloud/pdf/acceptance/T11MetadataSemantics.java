package net.zerocloud.pdf.acceptance;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentSession;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.EmbeddedFileData;
import net.zerocloud.pdf.EmbeddedFileSummary;
import net.zerocloud.pdf.OutlineItem;
import net.zerocloud.pdf.PageDestination;
import net.zerocloud.pdf.PdfArray;
import net.zerocloud.pdf.PdfBoolean;
import net.zerocloud.pdf.PdfDictionary;
import net.zerocloud.pdf.PdfDictionaryEntry;
import net.zerocloud.pdf.PdfIndirectReference;
import net.zerocloud.pdf.PdfInspectionLimits;
import net.zerocloud.pdf.PdfName;
import net.zerocloud.pdf.PdfNumber;
import net.zerocloud.pdf.PdfStream;
import net.zerocloud.pdf.PdfString;
import net.zerocloud.pdf.PdfValue;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.query.DocumentInfo;
import net.zerocloud.pdf.query.DocumentRootReference;
import net.zerocloud.pdf.query.EmbeddedFiles;
import net.zerocloud.pdf.query.InspectObject;
import net.zerocloud.pdf.query.NamedDestinations;
import net.zerocloud.pdf.query.OutlineTree;
import net.zerocloud.pdf.query.PageCount;
import net.zerocloud.pdf.query.PageObjectReference;
import net.zerocloud.pdf.query.ReadEmbeddedFile;
import net.zerocloud.pdf.query.XmpMetadata;

/** Separate observation of the frozen corpus through bounded public Native reads. */
final class T11MetadataSemantics {
    private final DocumentSession session;
    private final Map<String, String> expected;
    private final Map<String, String> observed = new TreeMap<String, String>();

    private T11MetadataSemantics(DocumentSession session, Map<String, String> expected) {
        this.session = session;
        this.expected = expected;
    }

    static Properties inspect(T11Corpus corpus, Path input, String product, WorkflowExecutionProfile execution)
            throws IOException {
        Map<String, String> expected = corpus.product(product);
        String before = EvidenceFiles.sha256(input);
        Properties result = new Properties();
        result.setProperty("profile", T11Corpus.PROFILE);
        result.setProperty("semantic-scope", "frozen-corpus");
        result.setProperty("input-sha256", before);
        try {
            WorkflowOutcome<String> outcome = new DocumentWorkflow().execute(WorkflowRequest.builder()
                    .source("input", DocumentSource.path(input)).primarySource("input")
                    .executionProfile(execution).saveMode(SaveMode.REWRITE).build(),
                    session -> new T11MetadataSemantics(session, expected).verify());
            result.setProperty("execution-profile", outcome.getExecutionProfile().name());
            result.setProperty("semantic", outcome.getExecutionProfile() != execution ? "indeterminate"
                    : outcome.getResult().isEmpty() ? "pass" : "fail");
            result.setProperty("finding", outcome.getResult().isEmpty()
                    ? "All frozen metadata, navigation, payload, page and retained-content expectations matched."
                    : "Frozen expectation mismatch: " + outcome.getResult());
        } catch (DocumentFailure failure) {
            result.setProperty("execution-profile", "unavailable");
            result.setProperty("semantic", "indeterminate");
            result.setProperty("finding", "Public Native observation unavailable: " + failure.getCode());
        }
        if (!before.equals(EvidenceFiles.sha256(input))) {
            result.setProperty("semantic", "indeterminate");
            result.setProperty("finding", "Input changed during public Native observation.");
        }
        return result;
    }

    private String verify() throws DocumentFailure {
        String field = "pages";
        try {
            pages();
            field = "Info";
            info();
            field = "Catalog private metadata";
            PdfDictionary catalog = (PdfDictionary) session.query(InspectObject.version1(
                    session.query(DocumentRootReference.INSTANCE), PdfInspectionLimits.of(10000, 1 << 20)));
            PdfDictionary privateData = dictionary(catalog, "T73Keep");
            put("catalog-flag", ((PdfBoolean) privateData.get(PdfName.of("Flag"))).booleanValue());
            numbers("catalog-numbers", resolve(privateData.get(PdfName.of("Numbers"))));
            field = "XMP";
            byte[] packet = session.query(XmpMetadata.version1(1 << 20));
            put("xmp", packet == null ? "" : new String(packet, StandardCharsets.UTF_8));
            PdfStream metadata = (PdfStream) resolve(catalog.get(PdfName.of("Metadata")));
            put("xmp-marker", text(dictionary(metadata.getDictionary(), "T73Keep").get(PdfName.of("Opaque"))));
            field = "navigation";
            navigation(catalog);
            field = "attachments";
            attachments();
            for (Map.Entry<String, String> entry : expected.entrySet()) {
                if (!entry.getValue().equals(observed.get(entry.getKey()))) {
                    return entry.getKey();
                }
            }
            for (String key : observed.keySet()) {
                if (!expected.containsKey(key)) {
                    return "unexpected " + key;
                }
            }
            return "";
        } catch (Mismatch mismatch) {
            return mismatch.getMessage();
        } catch (ClassCastException | NullPointerException | IndexOutOfBoundsException malformed) {
            return "required " + field + " value shape";
        }
    }

    private void pages() throws DocumentFailure {
        int count = session.query(PageCount.INSTANCE);
        put("pages.count", count);
        require(count == Integer.parseInt(expected.get("pages.count")), "page count");
        for (int index = 0; index < count; index++) {
            PdfDictionary page = (PdfDictionary) session.query(InspectObject.version1(
                    session.query(PageObjectReference.version1(index + 1)), PdfInspectionLimits.of(10000, 1 << 20)));
            put("pages." + index, ((PdfName) page.get(PdfName.of("T73Marker"))).getValue());
            String prefix = "page-observations." + index + ".";
            PdfValue media = inherited(page, "MediaBox");
            PdfValue crop = inherited(page, "CropBox");
            numbers(prefix + "media-box", media);
            numbers(prefix + "crop-box", crop == null ? media : crop);
            PdfValue rotation = inherited(page, "Rotate");
            put(prefix + "rotation", rotation == null ? "0" : number(rotation));
            List<String> programs = new ArrayList<String>();
            PdfValue contents = resolve(page.get(PdfName.of("Contents")));
            if (contents instanceof PdfArray) {
                PdfArray streams = (PdfArray) contents;
                require(streams.size() < 16, "page content bound");
                for (int stream = 0; stream < streams.size(); stream++) {
                    programs.add(new String(((PdfStream) resolve(streams.get(stream))).readBytes(), StandardCharsets.US_ASCII));
                }
            } else {
                programs.add(new String(((PdfStream) contents).readBytes(), StandardCharsets.US_ASCII));
            }
            strings(prefix + "contents", programs);
            PdfValue resources = inherited(page, "Resources");
            require(resources instanceof PdfDictionary && ((PdfDictionary) resources).size() == 0,
                    "unexpected page resources");
            require(page.get(PdfName.of("Annots")) == null, "unexpected page annotations");
        }
    }

    private void info() throws DocumentFailure {
        PdfDictionary info = session.query(DocumentInfo.INSTANCE);
        for (int index = 0; index < info.size(); index++) {
            PdfDictionaryEntry entry = info.getEntry(index);
            put("info." + entry.getName().getValue(), text(entry.getValue()));
        }
    }

    private void navigation(PdfDictionary catalog) throws DocumentFailure {
        Map<String, PageDestination> destinations = session.query(NamedDestinations.version1(100));
        strings("destination-order", new ArrayList<String>(destinations.keySet()));
        for (Map.Entry<String, PageDestination> entry : destinations.entrySet()) {
            destination("destinations." + entry.getKey(), entry.getValue());
        }
        // Check the serialized byte ordering as well as the decoded public map.
        PdfDictionary tree = dictionary(dictionary(catalog, "Names"), "Dests");
        PdfArray pairs = (PdfArray) resolve(tree.get(PdfName.of("Names")));
        int count = Integer.parseInt(expected.get("destination-order.count"));
        require(pairs.size() == 2 * count && tree.get(PdfName.of("Kids")) == null, "flat destination name tree");
        for (int index = 0; index < count; index++) {
            byte[] actual = ((PdfString) pairs.get(2 * index)).getBytes();
            require(Arrays.equals(encodedName(expected.get("destination-order." + index)), actual),
                    "encoded destination key order at " + index);
        }
        outlines("outlines", session.query(OutlineTree.version1(100)));
    }

    private void destination(String prefix, PageDestination destination) {
        put(prefix + ".page", destination.getPageNumber());
        put(prefix + ".style", destination.getStyle().name());
        put(prefix + ".parameters.count", destination.getOperands().size());
        for (int index = 0; index < destination.getOperands().size(); index++) {
            BigDecimal value = destination.getOperands().get(index);
            put(prefix + ".parameters." + index, value == null ? "" : decimal(value));
        }
    }

    private void outlines(String prefix, List<OutlineItem> items) {
        put(prefix + ".count", items.size());
        for (int index = 0; index < items.size(); index++) {
            OutlineItem item = items.get(index);
            String key = prefix + "." + index;
            put(key + ".title", item.getTitle());
            put(key + ".named", item.getNamedDestination().orElse(""));
            if (item.getDestination().isPresent()) {
                destination(key + ".destination", item.getDestination().get());
            } else {
                put(key + ".destination", "");
            }
            outlines(key + ".children", item.getChildren());
        }
    }

    private void attachments() throws DocumentFailure {
        List<EmbeddedFileSummary> files = session.query(EmbeddedFiles.version1(100));
        put("attachments.count", files.size());
        for (int index = 0; index < files.size(); index++) {
            EmbeddedFileSummary summary = files.get(index);
            EmbeddedFileData file = session.query(ReadEmbeddedFile.version1(summary.getName(), 1 << 20)).get();
            byte[] bytes = file.getContent();
            String prefix = "attachments." + index + ".";
            put(prefix + "name", file.getName());
            put(prefix + "mime", file.getMimeSubtype().orElse(""));
            put(prefix + "description", file.getDescription().orElse(""));
            String relationship = file.getRelationship().name().toLowerCase(Locale.ROOT);
            put(prefix + "relationship", Character.toUpperCase(relationship.charAt(0)) + relationship.substring(1));
            put(prefix + "size", bytes.length);
            put(prefix + "hex", hex(bytes));
            String md5 = md5(bytes);
            String sha = EvidenceFiles.sha256(bytes);
            put(prefix + "md5", md5);
            put(prefix + "sha256", sha);
            require(file.getSize() == bytes.length && summary.getSize() == bytes.length
                    && sha.equals(file.getSha256Hex()) && md5.equals(file.getMd5Hex().orElse(""))
                    && file.getMd5Hex().equals(summary.getMd5Hex())
                    && file.getMimeSubtype().equals(summary.getMimeSubtype())
                    && file.getDescription().equals(summary.getDescription())
                    && file.getRelationship() == summary.getRelationship(), "attachment summary/decoded payload consistency");
        }
    }

    private PdfValue resolve(PdfValue value) throws DocumentFailure {
        int references = 0;
        while (value instanceof PdfIndirectReference) {
            require(++references <= 16, "reference chain bound");
            value = session.query(InspectObject.version1(((PdfIndirectReference) value).getReference(),
                    PdfInspectionLimits.of(10000, 1 << 20)));
        }
        return value;
    }

    private PdfDictionary dictionary(PdfDictionary dictionary, String key) throws DocumentFailure {
        return (PdfDictionary) resolve(dictionary.get(PdfName.of(key)));
    }

    private PdfValue inherited(PdfDictionary page, String name) throws DocumentFailure {
        for (int depth = 0; depth < 16; depth++) {
            PdfValue value = resolve(page.get(PdfName.of(name)));
            if (value != null) {
                return value;
            }
            PdfValue parent = resolve(page.get(PdfName.of("Parent")));
            if (parent == null) {
                return null;
            }
            page = (PdfDictionary) parent;
        }
        throw new Mismatch("page inheritance bound");
    }

    private void numbers(String prefix, PdfValue value) throws DocumentFailure {
        PdfArray array = (PdfArray) value;
        put(prefix + ".count", array.size());
        for (int index = 0; index < array.size(); index++) {
            put(prefix + "." + index, number(array.get(index)));
        }
    }

    private static String number(PdfValue value) {
        return decimal(((PdfNumber) value).decimalValue());
    }

    private static String decimal(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String text(PdfValue value) {
        byte[] bytes = ((PdfString) value).getBytes();
        return bytes.length >= 2 && bytes[0] == (byte) 254 && bytes[1] == (byte) 255
                ? new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE)
                : new String(bytes, StandardCharsets.ISO_8859_1);
    }

    private static byte[] encodedName(String name) {
        if ("\u2022".equals(name)) {
            return new byte[] {(byte) 128};
        }
        if ("\u0100".equals(name)) {
            return new byte[] {(byte) 254, (byte) 255, 1, 0};
        }
        return name.getBytes(StandardCharsets.US_ASCII);
    }

    private static String md5(byte[] bytes) {
        try {
            return hex(MessageDigest.getInstance("MD5").digest(bytes));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("JDK MD5 unavailable", unavailable);
        }
    }

    static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte octet : bytes) {
            value.append(Character.forDigit((octet & 255) >>> 4, 16));
            value.append(Character.forDigit(octet & 15, 16));
        }
        return value.toString();
    }

    private void strings(String prefix, List<String> values) {
        put(prefix + ".count", values.size());
        for (int index = 0; index < values.size(); index++) {
            put(prefix + "." + index, values.get(index));
        }
    }

    private void put(String key, Object value) {
        observed.put(key, String.valueOf(value));
    }

    private static void require(boolean condition, String finding) {
        if (!condition) {
            throw new Mismatch(finding);
        }
    }

    private static final class Mismatch extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Mismatch(String finding) {
            super(finding);
        }
    }
}
