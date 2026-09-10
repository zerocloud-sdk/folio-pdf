package net.zerocloud.pdf.acceptance;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentSession;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.ObjectReference;
import net.zerocloud.pdf.PdfArray;
import net.zerocloud.pdf.PdfDictionary;
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
import net.zerocloud.pdf.query.DocumentRootReference;
import net.zerocloud.pdf.query.InspectObject;
import net.zerocloud.pdf.query.PageCount;
import net.zerocloud.pdf.query.PageObjectReference;

/** Frozen T10 expectations, checked by reopening only through the public Native Interface. */
final class T10PageSemantics {
    private final DocumentSession session;
    private final List<ExpectedPage> pages;
    private final Map<String, Integer> destinations;
    private final List<ObjectReference> pageReferences = new ArrayList<ObjectReference>();
    private String mismatch = "";

    private T10PageSemantics(DocumentSession session, List<ExpectedPage> pages, Map<String, Integer> destinations) {
        this.session = session;
        this.pages = pages;
        this.destinations = destinations;
    }

    static Properties inspect(T10Corpus corpus, Path input, String product, WorkflowExecutionProfile execution) throws IOException {
        if (!Arrays.asList("edited", "merged", "left", "right").contains(product)) {
            throw new IllegalArgumentException("Unknown frozen T10 product: " + product);
        }
        List<ExpectedPage> pages = new ArrayList<ExpectedPage>();
        String prefix = "products." + product + ".pages.";
        for (int index = 0; index < Integer.parseInt(corpus.expected(prefix + "count")); index++) {
            pages.add(new ExpectedPage(corpus, corpus.expected(prefix + index)));
        }
        Map<String, Integer> destinations = new LinkedHashMap<String, Integer>();
        String[] names = "merged".equals(product) ? new String[] {"shared", "shared-1", "shared-2"}
                : "right".equals(product) ? new String[] {"shared-1", "shared-2"} : new String[] {"shared"};
        for (String name : names) {
            destinations.put(name, Integer.valueOf(corpus.expected("products." + product + ".destinations." + name)));
        }
        Properties result = new Properties();
        result.setProperty("semantic-scope", "frozen-corpus");
        try {
            WorkflowOutcome<String> outcome = new DocumentWorkflow().execute(WorkflowRequest.builder()
                    .source("input", DocumentSource.path(input)).primarySource("input")
                    .executionProfile(execution).saveMode(SaveMode.REWRITE).build(),
                    session -> new T10PageSemantics(session, pages, destinations).verify());
            result.setProperty("execution-profile", outcome.getExecutionProfile().name());
            result.setProperty("semantic", outcome.getExecutionProfile() != execution ? "indeterminate"
                    : outcome.getResult().isEmpty() ? "pass" : "fail");
            result.setProperty("finding", outcome.getResult().isEmpty() ? "All frozen page, resource, annotation and destination expectations matched."
                    : "Frozen expectation mismatch: " + outcome.getResult());
        } catch (DocumentFailure failure) {
            result.setProperty("execution-profile", "unavailable");
            result.setProperty("semantic", "indeterminate");
            result.setProperty("finding", "Public Native observation failed: " + failure.getCode());
        }
        return result;
    }

    private String verify() throws DocumentFailure {
        if (!check(session.query(PageCount.INSTANCE) == pages.size(), "page count")) {
            return mismatch;
        }
        for (int index = 0; index < pages.size(); index++) {
            pageReferences.add(session.query(PageObjectReference.version1(index + 1)));
        }
        for (int index = 0; index < pages.size(); index++) {
            PdfValue value = inspect(pageReferences.get(index));
            if (!check(value instanceof PdfDictionary, "page dictionary " + (index + 1))
                    || !page((PdfDictionary) value, pages.get(index), index)) {
                return mismatch;
            }
        }
        navigation();
        return mismatch;
    }

    private boolean page(PdfDictionary page, ExpectedPage expected, int index) throws DocumentFailure {
        String label = "page " + (index + 1) + " " + expected.id;
        if (!check(onlyKeys(page, "Type", "Parent", "MediaBox", "CropBox", "BleedBox", "TrimBox", "ArtBox", "Rotate",
                "Resources", "Contents", "Annots", "T10Marker"), label + " scope")
                || !check(PdfName.of("Page").equals(page.get(PdfName.of("Type"))), label + " Type")
                || !check(expected.marker.isEmpty() ? page.get(PdfName.of("T10Marker")) == null
                    : PdfName.of(expected.marker).equals(page.get(PdfName.of("T10Marker"))), label + " order/marker")) {
            return false;
        }
        PdfValue media = inherited(page, "MediaBox");
        PdfValue crop = inherited(page, "CropBox");
        if (!check(numbers(media, expected.media), label + " MediaBox")
                || !check(numbers(crop == null ? media : crop, expected.crop), label + " CropBox")) {
            return false;
        }
        PdfValue rotation = inherited(page, "Rotate");
        if (!check(rotation == null ? expected.rotation == 0 : number(rotation, expected.rotation), label + " Rotate")) {
            return false;
        }
        if (!expected.marker.isEmpty()) {
            for (Map.Entry<String, int[]> box : expected.optionalBoxes.entrySet()) {
                if (!check(numbers(resolve(page.get(PdfName.of(box.getKey()))), box.getValue()), label + " " + box.getKey())) {
                    return false;
                }
            }
        }
        if (!check(program(page.get(PdfName.of("Contents")), expected.content), label + " ordered content")) {
            return false;
        }
        PdfValue resources = inherited(page, "Resources");
        if (!check(resources instanceof PdfDictionary, label + " Resources")) {
            return false;
        }
        if (expected.marker.isEmpty()) {
            return check(((PdfDictionary) resources).size() == 0 && page.get(PdfName.of("Annots")) == null,
                    label + " blank resources/annotations");
        }
        PdfDictionary resourceDictionary = (PdfDictionary) resources;
        PdfValue xobjects = resolve(resourceDictionary.get(PdfName.of("XObject")));
        if (!check(onlyKeys(resourceDictionary, "XObject") && xobjects instanceof PdfDictionary
                && ((PdfDictionary) xobjects).size() == 1, label + " resource scope")) {
            return false;
        }
        PdfValue tile = resolve(((PdfDictionary) xobjects).get(PdfName.of("Tile")));
        if (!check(tile instanceof PdfStream && stream((PdfStream) tile, expected.tileProgram), label + " Tile program")) {
            return false;
        }
        PdfDictionary form = ((PdfStream) tile).getDictionary();
        if (!check(onlyKeys(form, "Type", "Subtype", "FormType", "BBox", "Matrix", "Resources", "Length", "Filter")
                && PdfName.of("XObject").equals(form.get(PdfName.of("Type")))
                && PdfName.of("Form").equals(form.get(PdfName.of("Subtype")))
                && number(form.get(PdfName.of("FormType")), 1)
                && numbers(resolve(form.get(PdfName.of("BBox"))), new int[] {0, 0, 40, 40})
                && numbers(resolve(form.get(PdfName.of("Matrix"))), new int[] {1, 0, 0, 1, 0, 0}), label + " Form geometry/type")) {
            return false;
        }
        PdfValue formResources = resolve(form.get(PdfName.of("Resources")));
        if (!check(formResources instanceof PdfDictionary && ((PdfDictionary) formResources).size() == 0, label + " Form Resources")) {
            return false;
        }
        PdfValue annots = resolve(page.get(PdfName.of("Annots")));
        if (!check(annots instanceof PdfArray && ((PdfArray) annots).size() == 1, label + " annotation count")) {
            return false;
        }
        PdfValue annotation = resolve(((PdfArray) annots).get(0));
        if (!check(annotation instanceof PdfDictionary, label + " annotation dictionary")) {
            return false;
        }
        PdfDictionary annot = (PdfDictionary) annotation;
        PdfValue owner = annot.get(PdfName.of("P"));
        return check(onlyKeys(annot, "Type", "Subtype", "Rect", "Contents", "NM", "F", "P"),
                    label + " annotation scope " + keyNames(annot))
                && check(PdfName.of("Annot").equals(annot.get(PdfName.of("Type"))),
                    label + " annotation Type")
                && check(PdfName.of("Text").equals(annot.get(PdfName.of("Subtype"))),
                    label + " annotation Subtype")
                && check(numbers(resolve(annot.get(PdfName.of("Rect"))), new int[] {30, 50, 45, 65}),
                    label + " annotation Rect")
                && check(text(annot.get(PdfName.of("Contents")), expected.annotationContents),
                    label + " annotation Contents")
                && check(text(annot.get(PdfName.of("NM")), expected.annotationName),
                    label + " annotation NM")
                && check(number(annot.get(PdfName.of("F")), 2), label + " annotation flags")
                && check(owner instanceof PdfIndirectReference, label + " annotation page-reference shape")
                && check(pageReferences.get(index).equals(((PdfIndirectReference) owner).getReference()),
                    label + " annotation page retargeting");
    }

    private boolean navigation() throws DocumentFailure {
        PdfValue root = inspect(session.query(DocumentRootReference.INSTANCE));
        if (!check(root instanceof PdfDictionary && onlyKeys((PdfDictionary) root, "Type", "Pages", "Names", "Version"), "Catalog scope")) {
            return false;
        }
        PdfValue names = resolve(((PdfDictionary) root).get(PdfName.of("Names")));
        if (!check(names instanceof PdfDictionary && onlyKeys((PdfDictionary) names, "Dests"), "Names scope")) {
            return false;
        }
        PdfValue tree = resolve(((PdfDictionary) names).get(PdfName.of("Dests")));
        if (!check(tree instanceof PdfDictionary && onlyKeys((PdfDictionary) tree, "Names"), "flat destination name tree")) {
            return false;
        }
        PdfValue values = resolve(((PdfDictionary) tree).get(PdfName.of("Names")));
        if (!check(values instanceof PdfArray && ((PdfArray) values).size() == destinations.size() * 2, "destination count")) {
            return false;
        }
        PdfArray pairs = (PdfArray) values;
        int index = 0;
        for (Map.Entry<String, Integer> destination : destinations.entrySet()) {
            PdfValue target = resolve(pairs.get(index + 1));
            if (!check(text(pairs.get(index), destination.getKey()) && target instanceof PdfArray
                    && ((PdfArray) target).size() == 2, "destination name/order/shape")) {
                return false;
            }
            PdfArray array = (PdfArray) target;
            PdfValue page = array.get(0);
            if (!check(page instanceof PdfIndirectReference && PdfName.of("Fit").equals(array.get(1))
                    && pageReferences.get(destination.getValue().intValue() - 1).equals(((PdfIndirectReference) page).getReference()),
                    "destination page/view: " + destination.getKey())) {
                return false;
            }
            index += 2;
        }
        return true;
    }

    private boolean program(PdfValue value, String expected) throws DocumentFailure {
        value = resolve(value);
        if (value == null) {
            return expected.isEmpty();
        }
        if (value instanceof PdfStream) {
            return stream((PdfStream) value, expected);
        }
        if (!(value instanceof PdfArray)) {
            return false;
        }
        StringBuilder program = new StringBuilder();
        PdfArray streams = (PdfArray) value;
        for (int index = 0; index < streams.size(); index++) {
            PdfValue element = resolve(streams.get(index));
            if (!(element instanceof PdfStream) || !filter((PdfStream) element)) {
                return false;
            }
            program.append(new String(((PdfStream) element).readBytes(), StandardCharsets.US_ASCII)).append('\n');
        }
        return canonical(expected).equals(canonical(program.toString()));
    }

    private boolean stream(PdfStream stream, String expected) throws DocumentFailure {
        return filter(stream) && canonical(expected).equals(canonical(new String(stream.readBytes(), StandardCharsets.US_ASCII)));
    }

    private boolean filter(PdfStream stream) throws DocumentFailure {
        PdfDictionary dictionary = stream.getDictionary();
        PdfValue filter = resolve(dictionary.get(PdfName.of("Filter")));
        if (dictionary.get(PdfName.of("DecodeParms")) != null) {
            return false;
        }
        if (filter instanceof PdfArray) {
            return ((PdfArray) filter).size() == 1 && PdfName.of("FlateDecode").equals(((PdfArray) filter).get(0));
        }
        return filter == null || PdfName.of("FlateDecode").equals(filter);
    }

    private PdfValue inherited(PdfDictionary page, String key) throws DocumentFailure {
        for (int depth = 0; depth < 32; depth++) {
            PdfValue value = resolve(page.get(PdfName.of(key)));
            if (value != null) {
                return value;
            }
            PdfValue parent = resolve(page.get(PdfName.of("Parent")));
            if (!(parent instanceof PdfDictionary)) {
                return null;
            }
            page = (PdfDictionary) parent;
        }
        return null;
    }

    private PdfValue resolve(PdfValue value) throws DocumentFailure {
        for (int depth = 0; depth < 8 && value instanceof PdfIndirectReference; depth++) {
            value = inspect(((PdfIndirectReference) value).getReference());
        }
        return value;
    }

    private PdfValue inspect(ObjectReference reference) throws DocumentFailure {
        return session.query(InspectObject.version1(reference, PdfInspectionLimits.of(10000, 1 << 20)));
    }

    private boolean check(boolean matches, String description) {
        if (!matches && mismatch.isEmpty()) {
            mismatch = description;
        }
        return matches;
    }

    private static boolean onlyKeys(PdfDictionary dictionary, String... keys) throws DocumentFailure {
        List<String> allowed = Arrays.asList(keys);
        for (int index = 0; index < dictionary.size(); index++) {
            if (!allowed.contains(dictionary.getEntry(index).getName().getValue())) {
                return false;
            }
        }
        return true;
    }

    private static String keyNames(PdfDictionary dictionary) throws DocumentFailure {
        List<String> names = new ArrayList<String>();
        for (int index = 0; index < dictionary.size(); index++) {
            names.add(dictionary.getEntry(index).getName().getValue());
        }
        return names.toString();
    }

    private static boolean numbers(PdfValue value, int[] expected) throws DocumentFailure {
        if (!(value instanceof PdfArray) || ((PdfArray) value).size() != expected.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if (!number(((PdfArray) value).get(index), expected[index])) {
                return false;
            }
        }
        return true;
    }

    private static boolean number(PdfValue value, int expected) {
        return value instanceof PdfNumber && ((PdfNumber) value).decimalValue().compareTo(BigDecimal.valueOf(expected)) == 0;
    }

    private static boolean text(PdfValue value, String expected) {
        return value instanceof PdfString && Arrays.equals(expected.getBytes(StandardCharsets.US_ASCII), ((PdfString) value).getBytes());
    }

    private static String canonical(String program) {
        return program.replaceAll("[\\x00\\x09\\x0a\\x0c\\x0d\\x20]+", " ").replaceAll("^ | $", "");
    }

    private static final class ExpectedPage {
        final String id;
        final String marker;
        final int[] media;
        final int[] crop;
        final int rotation;
        final Map<String, int[]> optionalBoxes = new LinkedHashMap<String, int[]>();
        final String content;
        final String tileProgram;
        final String annotationName;
        final String annotationContents;

        ExpectedPage(T10Corpus corpus, String id) throws IOException {
            this.id = id;
            String prefix = "pages." + id + ".";
            marker = corpus.expected(prefix + "marker");
            media = numbers(corpus, prefix + "media-box");
            crop = numbers(corpus, prefix + "crop-box");
            rotation = Integer.parseInt(corpus.expected(prefix + "rotation"));
            StringBuilder content = new StringBuilder();
            for (int index = 0; index < Integer.parseInt(corpus.expected(prefix + "contents.count")); index++) {
                content.append(corpus.expected(prefix + "contents." + index)).append('\n');
            }
            this.content = content.toString();
            if (marker.isEmpty()) {
                tileProgram = annotationName = annotationContents = "";
            } else {
                optionalBoxes.put("BleedBox", numbers(corpus, prefix + "bleed-box"));
                optionalBoxes.put("TrimBox", numbers(corpus, prefix + "trim-box"));
                optionalBoxes.put("ArtBox", numbers(corpus, prefix + "art-box"));
                tileProgram = corpus.expected(prefix + "tile-program");
                annotationName = corpus.expected(prefix + "annotation-name");
                annotationContents = corpus.expected(prefix + "annotation-contents");
            }
        }

        private static int[] numbers(T10Corpus corpus, String prefix) throws IOException {
            int[] result = new int[Integer.parseInt(corpus.expected(prefix + ".count"))];
            for (int index = 0; index < result.length; index++) {
                result[index] = Integer.parseInt(corpus.expected(prefix + "." + index));
            }
            return result;
        }
    }
}
