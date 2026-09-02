package net.zerocloud.pdf.acceptance;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentResourceInventory;
import net.zerocloud.pdf.DocumentSession;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.FontResource;
import net.zerocloud.pdf.ImageByteAccess;
import net.zerocloud.pdf.ObjectReference;
import net.zerocloud.pdf.PdfArray;
import net.zerocloud.pdf.PdfDictionary;
import net.zerocloud.pdf.PdfDictionaryEntry;
import net.zerocloud.pdf.PdfIndirectReference;
import net.zerocloud.pdf.PdfInspectionLimits;
import net.zerocloud.pdf.PdfName;
import net.zerocloud.pdf.PdfStream;
import net.zerocloud.pdf.PdfValue;
import net.zerocloud.pdf.PublicationReceipt;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.ResourceExtractionLimits;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.query.ExtractImagesAndResources;
import net.zerocloud.pdf.query.InspectObject;
import net.zerocloud.pdf.query.PageObjectReference;

/** Project-owned semantic assertions for the T17 Canvas artifact. */
final class CanvasSemanticAssertions {

    private static final String CAPABILITY =
            "composition.canvas.draw-positioned-text";

    private CanvasSemanticAssertions() {
    }

    static CanvasSemanticObservation inspect(
            WorkflowOutcome<Void> creation,
            Path artifact) {
        PublicationStatus publicationStatus = publicationStatus(creation);
        boolean capabilityReported = CAPABILITY.equals(
                creation.getCapabilityId());
        try {
            Observations observations = new DocumentWorkflow().execute(
                    WorkflowRequest.open(artifact, SaveMode.REWRITE),
                    CanvasSemanticAssertions::observe).getResult();
            return CanvasSemanticObservation.observed(
                    publicationStatus,
                    capabilityReported,
                    observations.pathSemantics,
                    observations.stateSemantics,
                    observations.textSemantics,
                    observations.resourceReuse,
                    observations.preservation);
        } catch (DocumentFailure failure) {
            return CanvasSemanticObservation.reopenFailed(
                    publicationStatus,
                    capabilityReported,
                    failure.getCode());
        } catch (RuntimeException malformedObservation) {
            return CanvasSemanticObservation.reopenFailed(
                    publicationStatus,
                    capabilityReported,
                    null);
        }
    }

    private static Observations observe(DocumentSession session)
            throws DocumentFailure {
        String content = pageContent(session);
        List<ContentOperation> operations = contentOperations(content);
        boolean pathSemantics = containsSequence(
                    operations,
                    expected("cm", 1d, 0d, 0d, 1d, 6d, 8d),
                    expected("m", 10d, 10d),
                    expected("l", 80d, 10d),
                    expected("S"))
                && containsSequence(
                    operations,
                    expected("m", 10d, 20d),
                    expected("c", 20d, 30d, 40d, 70d, 80d, 90d),
                    expected("f"))
                && containsOperation(operations, expected("f*"));
        boolean stateSemantics = containsSequence(
                    operations,
                    expected("W"),
                    expected("n"),
                    expected("m", 0d, 100d),
                    expected("l", 200d, 100d),
                    expected("S"))
                && containsSequence(
                    operations,
                    expected("W*"),
                    expected("n"),
                    expected("m", 100d, 0d),
                    expected("l", 100d, 200d),
                    expected("S"))
                && balancedWithMaximumDepth(operations, 3);

        DocumentResourceInventory inventory = session.query(
                ExtractImagesAndResources.version1(
                        resourceLimits(),
                        ImageByteAccess.NONE));
        PdfDictionary page = inspectDictionary(
                session,
                session.query(PageObjectReference.version1(1)));
        PdfDictionary resources = (PdfDictionary) resolve(
                session,
                page.get(PdfName.of("Resources")));
        PdfName fontName = soleIndirectFontName(
                session,
                resources,
                inventory);
        boolean resourceReuse = fontName != null
                && inventory.getFonts().size() == 1
                && inventory.getFonts().get(0).getDeclarations().size() == 1;

        boolean textSemantics = resourceReuse
                && countOperations(operations, "BT") == 9
                && countOperations(operations, "ET") == 9
                && countOperations(operations, "Tf") == 9
                && countOperations(operations, "Tr") == 9
                && countOperations(operations, "Tm") == 9
                && countOperations(operations, "Tj") == 9;
        for (int index = 0; textSemantics && index < 8; index++) {
            textSemantics = containsPositionedGlyph(
                    operations,
                    14d,
                    index,
                    30d + index * 25d,
                    120d + index * 8d,
                    fontName,
                    new byte[] {65});
        }
        textSemantics = textSemantics && containsPositionedGlyph(
                operations,
                14d,
                0,
                240d,
                40d,
                fontName,
                new byte[] {65});
        boolean preservation = containsSequence(
                    operations,
                    expected("m", 2d, 2d),
                    expected("l", 3d, 3d),
                    expected("S"))
                && PdfName.of("Kept").equals(
                        resources.get(PdfName.of("FolioKeep")));
        return new Observations(
                pathSemantics,
                stateSemantics,
                textSemantics,
                resourceReuse,
                preservation);
    }

    private static String pageContent(DocumentSession session)
            throws DocumentFailure {
        PdfDictionary page = inspectDictionary(
                session,
                session.query(PageObjectReference.version1(1)));
        PdfValue contents = resolve(
                session,
                page.get(PdfName.of("Contents")));
        StringBuilder combined = new StringBuilder();
        if (contents instanceof PdfStream) {
            combined.append(ascii(((PdfStream) contents).readBytes()));
        } else {
            PdfArray streams = (PdfArray) contents;
            for (int index = 0; index < streams.size(); index++) {
                PdfStream stream = (PdfStream) resolve(
                        session,
                        streams.get(index));
                combined.append(ascii(stream.readBytes())).append('\n');
            }
        }
        return combined.toString();
    }

    private static PdfDictionary inspectDictionary(
            DocumentSession session,
            ObjectReference reference) throws DocumentFailure {
        return (PdfDictionary) session.query(InspectObject.version1(
                reference,
                PdfInspectionLimits.of(128, 4L * 1024L * 1024L)));
    }

    private static PdfValue resolve(
            DocumentSession session,
            PdfValue value) throws DocumentFailure {
        if (value instanceof PdfIndirectReference) {
            return session.query(InspectObject.version1(
                    ((PdfIndirectReference) value).getReference(),
                    PdfInspectionLimits.of(128, 4L * 1024L * 1024L)));
        }
        return value;
    }

    private static String ascii(byte[] value) {
        return new String(value, StandardCharsets.US_ASCII);
    }

    private static List<ContentOperation> contentOperations(String content) {
        List<ContentOperation> operations =
                new ArrayList<ContentOperation>();
        List<String> operands = new ArrayList<String>();
        String trimmed = content.trim();
        if (trimmed.isEmpty()) {
            return operations;
        }
        for (String token : trimmed.split("\\s+")) {
            if (isObservedOperator(token)) {
                operations.add(new ContentOperation(token, operands));
                operands = new ArrayList<String>();
            } else {
                operands.add(token);
            }
        }
        if (!operands.isEmpty()) {
            throw new IllegalArgumentException(
                    "Unexpected trailing content operands");
        }
        return operations;
    }

    private static boolean isObservedOperator(String token) {
        return "q".equals(token) || "Q".equals(token)
                || "cm".equals(token) || "m".equals(token)
                || "l".equals(token) || "c".equals(token)
                || "h".equals(token) || "S".equals(token)
                || "f".equals(token) || "f*".equals(token)
                || "W".equals(token) || "W*".equals(token)
                || "n".equals(token) || "BT".equals(token)
                || "Tf".equals(token) || "Tr".equals(token)
                || "Tm".equals(token) || "Tj".equals(token)
                || "ET".equals(token);
    }

    private static ExpectedOperation expected(
            String operator,
            double... operands) {
        return new ExpectedOperation(operator, operands);
    }

    private static boolean containsOperation(
            List<ContentOperation> operations,
            ExpectedOperation expected) {
        for (ContentOperation operation : operations) {
            if (expected.matches(operation)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsSequence(
            List<ContentOperation> operations,
            ExpectedOperation... expected) {
        for (int start = 0;
                start + expected.length <= operations.size();
                start++) {
            boolean match = true;
            for (int offset = 0; offset < expected.length; offset++) {
                if (!expected[offset].matches(operations.get(start + offset))) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return true;
            }
        }
        return false;
    }

    private static int countOperations(
            List<ContentOperation> operations,
            String operator) {
        int count = 0;
        for (ContentOperation operation : operations) {
            if (operator.equals(operation.operator)) {
                count++;
            }
        }
        return count;
    }

    private static boolean containsPositionedGlyph(
            List<ContentOperation> operations,
            double fontSize,
            int renderingMode,
            double x,
            double y,
            PdfName fontName,
            byte[] glyph) {
        for (int start = 0; start + 5 < operations.size(); start++) {
            if (expected("BT").matches(operations.get(start))
                    && matchesFontSelection(
                            operations.get(start + 1), fontName, fontSize)
                    && expected("Tr", renderingMode).matches(
                            operations.get(start + 2))
                    && expected("Tm", 1d, 0d, 0d, 1d, x, y).matches(
                            operations.get(start + 3))
                    && matchesGlyph(operations.get(start + 4), glyph)
                    && expected("ET").matches(operations.get(start + 5))) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesFontSelection(
            ContentOperation operation,
            PdfName expectedName,
            double expectedSize) {
        if (!"Tf".equals(operation.operator)
                || operation.operands.size() != 2
                || expectedName == null
                || !("/" + expectedName.getValue()).equals(
                        operation.operands.get(0))) {
            return false;
        }
        return numericEquals(expectedSize, operation.operands.get(1));
    }

    private static PdfName soleIndirectFontName(
            DocumentSession session,
            PdfDictionary resources,
            DocumentResourceInventory inventory) throws DocumentFailure {
        if (inventory.getFonts().size() != 1) {
            return null;
        }
        FontResource font = inventory.getFonts().get(0);
        if (!font.getObjectReference().isPresent()) {
            return null;
        }
        PdfValue fontResources = resolve(
                session,
                resources.get(PdfName.of("Font")));
        if (!(fontResources instanceof PdfDictionary)) {
            return null;
        }
        PdfDictionary fonts = (PdfDictionary) fontResources;
        PdfName found = null;
        for (int index = 0; index < fonts.size(); index++) {
            PdfDictionaryEntry entry = fonts.getEntry(index);
            if (entry.getValue() instanceof PdfIndirectReference
                    && font.getObjectReference().get().equals(
                            ((PdfIndirectReference) entry.getValue())
                                    .getReference())) {
                if (found != null) {
                    return null;
                }
                found = entry.getName();
            }
        }
        return found;
    }

    private static boolean matchesGlyph(
            ContentOperation operation,
            byte[] expectedGlyph) {
        if (!"Tj".equals(operation.operator)
                || operation.operands.size() != 1) {
            return false;
        }
        String operand = operation.operands.get(0);
        if (operand.length() != expectedGlyph.length * 2 + 2
                || operand.charAt(0) != '<'
                || operand.charAt(operand.length() - 1) != '>') {
            return false;
        }
        for (int index = 0; index < expectedGlyph.length; index++) {
            int high = Character.digit(operand.charAt(index * 2 + 1), 16);
            int low = Character.digit(operand.charAt(index * 2 + 2), 16);
            if (high < 0 || low < 0
                    || (byte) ((high << 4) | low) != expectedGlyph[index]) {
                return false;
            }
        }
        return true;
    }

    private static boolean numericEquals(
            double expected,
            String actual) {
        try {
            return BigDecimal.valueOf(expected).compareTo(
                    new BigDecimal(actual)) == 0;
        } catch (NumberFormatException notNumeric) {
            return false;
        }
    }

    private static boolean balancedWithMaximumDepth(
            List<ContentOperation> operations,
            int requiredMaximum) {
        int depth = 0;
        int maximum = 0;
        for (ContentOperation operation : operations) {
            if ("q".equals(operation.operator)) {
                depth++;
                maximum = Math.max(maximum, depth);
            } else if ("Q".equals(operation.operator)) {
                depth--;
                if (depth < 0) {
                    return false;
                }
            }
        }
        return depth == 0 && maximum >= requiredMaximum;
    }

    private static PublicationStatus publicationStatus(
            WorkflowOutcome<Void> outcome) {
        List<PublicationReceipt> receipts = outcome.getPublicationReceipts();
        return receipts.size() == 1
                ? receipts.get(0).getStatus()
                : PublicationStatus.FAILED;
    }

    private static ResourceExtractionLimits resourceLimits() {
        return ResourceExtractionLimits.builder()
                .maximumPages(2)
                .maximumPageTreeNodes(16)
                .maximumTraversedResourceValues(256L)
                .maximumResourceTraversalDepth(16)
                .maximumDecodedPixels(0L)
                .maximumDecompressedBytes(1024L * 1024L)
                .maximumReturnedBytes(0L)
                .build();
    }

    private static final class Observations {

        private final boolean pathSemantics;
        private final boolean stateSemantics;
        private final boolean textSemantics;
        private final boolean resourceReuse;
        private final boolean preservation;

        Observations(
                boolean pathSemantics,
                boolean stateSemantics,
                boolean textSemantics,
                boolean resourceReuse,
                boolean preservation) {
            this.pathSemantics = pathSemantics;
            this.stateSemantics = stateSemantics;
            this.textSemantics = textSemantics;
            this.resourceReuse = resourceReuse;
            this.preservation = preservation;
        }
    }

    private static final class ContentOperation {

        private final String operator;
        private final List<String> operands;

        ContentOperation(String operator, List<String> operands) {
            this.operator = operator;
            this.operands = new ArrayList<String>(operands);
        }
    }

    private static final class ExpectedOperation {

        private final String operator;
        private final double[] operands;

        ExpectedOperation(String operator, double[] operands) {
            this.operator = operator;
            this.operands = operands.clone();
        }

        boolean matches(ContentOperation operation) {
            if (!operator.equals(operation.operator)
                    || operands.length != operation.operands.size()) {
                return false;
            }
            for (int index = 0; index < operands.length; index++) {
                if (!numericEquals(
                        operands[index], operation.operands.get(index))) {
                    return false;
                }
            }
            return true;
        }
    }
}
