package net.zerocloud.pdf;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.zerocloud.pdf.composition.CanvasMatrix;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNull;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

/** Shared guarded preservation and serialization for appended page content. */
final class PdfBoxPageContentSupport {

    private static final int MAXIMUM_PARENT_DEPTH = 64;
    private static final int MAXIMUM_EXISTING_CONTENT_BYTES = 8 * 1024 * 1024;
    private static final double MAXIMUM_ABSOLUTE_NUMBER = 1000000000d;

    private PdfBoxPageContentSupport() {
    }

    static ExistingContents prepareExistingContents(
            COSDictionary page,
            FailureFactory preservationFailure,
            WorkflowResourceContext resources) throws DocumentFailure {
        resources.checkpoint();
        COSBase rawContents = page.getItem(COSName.CONTENTS);
        if (rawContents == null
                || dereference(rawContents, resources) instanceof COSNull) {
            return new ExistingContents(Collections.<COSBase>emptyList());
        }

        COSBase value = dereference(rawContents, resources);
        List<COSBase> values = new ArrayList<COSBase>();
        if (value instanceof COSStream) {
            if (!(rawContents instanceof COSObject)) {
                throw preservationFailure.create();
            }
            values.add(rawContents);
        } else if (value instanceof COSArray) {
            COSArray array = (COSArray) value;
            for (int index = 0; index < array.size(); index++) {
                resources.checkpoint();
                COSBase rawStream = array.get(index);
                if (!(rawStream instanceof COSObject)
                        || !(dereference(rawStream, resources)
                                instanceof COSStream)) {
                    throw preservationFailure.create();
                }
                values.add(rawStream);
            }
        } else {
            throw preservationFailure.create();
        }

        try (WorkflowResourceContext.OwnedByteAccumulator combined =
                        resources.ownedByteAccumulator()) {
            ExistingContentOutput decodedOutput = new ExistingContentOutput(
                    combined,
                    MAXIMUM_EXISTING_CONTENT_BYTES);
            for (COSBase raw : values) {
                resources.checkpoint();
                COSStream stream = (COSStream) dereference(raw, resources);
                if (stream.containsKey(COSName.F)) {
                    throw preservationFailure.create();
                }
                PdfBoxHostileInputPreflight.decodeStream(
                        stream,
                        resources,
                        decodedOutput);
                combined.write('\n');
            }
            try (WorkflowResourceContext.OwnedBytes decoded =
                    combined.finishWorking()) {
                PdfBoxContentStreamPreflight.validate(
                        decoded.getBytes(), resources);
                requireBalancedExistingContent(
                        decoded.getBytes(), resources);
            }
            return new ExistingContents(values);
        } catch (ExistingContentLimitIOException exhausted) {
            throw preservationFailure.create();
        } catch (DocumentFailure failure) {
            throw failure;
        } catch (IOException | RuntimeException malformed) {
            resources.rethrowResourceOrTerminalFailure(malformed);
            throw preservationFailure.create();
        }
    }

    static FontResources prepareFontResources(
            COSDictionary page,
            FailureFactory preservationFailure,
            WorkflowResourceContext workflowResources)
            throws DocumentFailure {
        COSDictionary effective = effectiveResources(
                page, preservationFailure, workflowResources);
        COSDictionary existingFonts = null;
        COSBase rawFonts = effective == null
                ? null : effective.getItem(COSName.FONT);
        if (rawFonts != null) {
            COSBase fontValue = dereference(rawFonts, workflowResources);
            if (!(fontValue instanceof COSDictionary)
                    || fontValue instanceof COSStream) {
                throw preservationFailure.create();
            }
            existingFonts = (COSDictionary) fontValue;
        }

        COSDictionary resources = new COSDictionary();
        resources.setDirect(true);
        if (effective != null) {
            copyEntries(effective, resources, workflowResources);
        }
        COSDictionary fonts = new COSDictionary();
        fonts.setDirect(true);
        if (existingFonts != null) {
            copyEntries(existingFonts, fonts, workflowResources);
        }
        return new FontResources(resources, fonts);
    }

    static COSDictionary effectiveResources(
            COSDictionary page,
            FailureFactory preservationFailure,
            WorkflowResourceContext workflowResources)
            throws DocumentFailure {
        IdentityHashMap<COSDictionary, Boolean> visited =
                new IdentityHashMap<COSDictionary, Boolean>();
        COSDictionary current = page;
        int depth = 0;
        while (current != null && visited.put(current, Boolean.TRUE) == null) {
            workflowResources.checkpoint();
            if (++depth > MAXIMUM_PARENT_DEPTH) {
                throw preservationFailure.create();
            }
            COSBase rawResources = current.getItem(COSName.RESOURCES);
            if (rawResources != null) {
                COSBase resources = dereference(
                        rawResources, workflowResources);
                if (!(resources instanceof COSDictionary)
                        || resources instanceof COSStream) {
                    throw preservationFailure.create();
                }
                return (COSDictionary) resources;
            }
            COSBase rawParent = current.getItem(COSName.PARENT);
            if (rawParent == null) {
                current = null;
            } else {
                COSBase parent = dereference(rawParent, workflowResources);
                if (!(parent instanceof COSDictionary)
                        || parent instanceof COSStream) {
                    throw preservationFailure.create();
                }
                current = (COSDictionary) parent;
            }
        }
        if (current != null) {
            throw preservationFailure.create();
        }
        return null;
    }

    private static void copyEntries(
            COSDictionary source,
            COSDictionary target,
            WorkflowResourceContext resources) throws DocumentFailure {
        for (Map.Entry<COSName, COSBase> entry : source.entrySet()) {
            resources.checkpoint();
            target.setItem(entry.getKey(), entry.getValue());
        }
    }

    static void apply(
            PDDocument document,
            PDPage page,
            ExistingContents existing,
            byte[] operators,
            COSDictionary resources,
            boolean resourcesChanged,
            WorkflowResourceContext workflowResources,
            FailureFactory writeFailure) throws DocumentFailure {
        COSArray contents = new COSArray();
        contents.setDirect(true);
        if (!existing.isEmpty()) {
            contents.add(contentStream(
                    document,
                    "q\n".getBytes(StandardCharsets.US_ASCII),
                    workflowResources,
                    writeFailure));
            for (COSBase value : existing.values()) {
                workflowResources.checkpoint();
                contents.add(value);
            }
            contents.add(contentStream(
                    document,
                    "Q\nn\n".getBytes(StandardCharsets.US_ASCII),
                    workflowResources,
                    writeFailure));
        }
        contents.add(contentStream(
                document,
                operators,
                workflowResources,
                writeFailure));

        try {
            workflowResources.checkpoint();
            if (resourcesChanged) {
                page.getCOSObject().setItem(COSName.RESOURCES, resources);
            }
            page.getCOSObject().setItem(COSName.CONTENTS, contents);
        } catch (RuntimeException backendFailure) {
            workflowResources.rethrowResourceOrTerminalFailure(backendFailure);
            throw writeFailure.create();
        }
    }

    static void appendMatrix(
            WorkflowAsciiOutput output,
            CanvasMatrix matrix,
            FailureFactory failure) throws DocumentFailure {
        appendNumbers(output, new double[] {
            matrix.getA(), matrix.getB(), matrix.getC(),
            matrix.getD(), matrix.getE(), matrix.getF()
        }, failure);
    }

    static void appendNumbers(
            WorkflowAsciiOutput output,
            double[] values,
            FailureFactory failure) throws DocumentFailure {
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                output.append(' ');
            }
            appendNumber(output, values[index], failure);
        }
    }

    static void appendNumber(
            WorkflowAsciiOutput output,
            double value,
            FailureFactory failure) throws DocumentFailure {
        requireNumber(value, failure);
        if (value == 0d) {
            output.append('0');
            return;
        }
        output.append(BigDecimal.valueOf(value).stripTrailingZeros());
    }

    static void requireNumber(double value, FailureFactory failure)
            throws DocumentFailure {
        if (!isValidNumber(value)) {
            throw failure.create();
        }
    }

    /** Appends an already isolated stream to a newly constructed Composition page. */
    static void appendIsolated(PDDocument document, PDPage page, byte[] operators,
            COSDictionary resources, boolean resourcesChanged,
            WorkflowResourceContext workflowResources, FailureFactory writeFailure)
            throws DocumentFailure {
        COSObject stream = contentStream(document, operators, workflowResources, writeFailure);
        workflowResources.checkpoint();
        COSBase raw = page.getCOSObject().getItem(COSName.CONTENTS);
        COSArray contents;
        if (raw == null) {
            contents = new COSArray();
            contents.setDirect(true);
            page.getCOSObject().setItem(COSName.CONTENTS, contents);
        } else {
            contents = (COSArray) raw;
        }
        contents.add(stream);
        if (resourcesChanged) { page.getCOSObject().setItem(COSName.RESOURCES, resources); }
    }

    static boolean isValidNumber(double value) {
        return !Double.isNaN(value)
                && !Double.isInfinite(value)
                && Math.abs(value) <= MAXIMUM_ABSOLUTE_NUMBER;
    }

    static void requireNumbers(
            double[] values,
            int expected,
            FailureFactory failure) throws DocumentFailure {
        if (values == null || values.length != expected) {
            throw failure.create();
        }
        for (double value : values) {
            requireNumber(value, failure);
        }
    }

    static void requireMatrix(
            CanvasMatrix matrix,
            FailureFactory failure) throws DocumentFailure {
        if (!isValidMatrix(matrix)) {
            throw failure.create();
        }
    }

    static boolean isValidMatrix(CanvasMatrix matrix) {
        return matrix != null
                && isValidNumber(matrix.getA())
                && isValidNumber(matrix.getB())
                && isValidNumber(matrix.getC())
                && isValidNumber(matrix.getD())
                && isValidNumber(matrix.getE())
                && isValidNumber(matrix.getF());
    }

    static COSBase dereference(
            COSBase value,
            WorkflowResourceContext resources) throws DocumentFailure {
        COSBase current = value;
        IdentityHashMap<COSBase, Boolean> visited =
                new IdentityHashMap<COSBase, Boolean>();
        while (current instanceof COSObject
                && visited.put(current, Boolean.TRUE) == null) {
            resources.checkpoint();
            current = ((COSObject) current).getObject();
        }
        return current;
    }

    private static COSObject contentStream(
            PDDocument document,
            byte[] operators,
            WorkflowResourceContext resources,
            FailureFactory writeFailure) throws DocumentFailure {
        try {
            COSStream stream = document.getDocument().createCOSStream();
            try (OutputStream output = stream.createOutputStream()) {
                resources.writeBytesAsIOException(output, operators);
            }
            return new COSObject(stream);
        } catch (IOException | RuntimeException backendFailure) {
            resources.rethrowResourceOrTerminalFailure(backendFailure);
            throw writeFailure.create();
        }
    }

    private static void requireBalancedExistingContent(
            byte[] content,
            WorkflowResourceContext resources)
            throws IOException {
        PDFStreamParser parser = new PDFStreamParser(content);
        ExistingOperatorBalance balance = new ExistingOperatorBalance(
                resources);
        try {
            Object token;
            while ((token = parser.parseNextToken()) != null) {
                resources.checkpointAsIOException();
                if (token instanceof Operator) {
                    balance.accept(((Operator) token).getName());
                }
            }
        } finally {
            parser.close();
        }
        balance.requireBalanced();
    }

    interface FailureFactory {
        DocumentFailure create();
    }

    static final class ExistingContents {
        private final List<COSBase> values;

        ExistingContents(List<COSBase> values) {
            this.values = values;
        }

        boolean isEmpty() {
            return values.isEmpty();
        }

        List<COSBase> values() {
            return values;
        }
    }

    static final class FontResources {
        private final COSDictionary resources;
        private final COSDictionary fonts;

        FontResources(COSDictionary resources, COSDictionary fonts) {
            this.resources = resources;
            this.fonts = fonts;
        }

        COSDictionary resources() {
            return resources;
        }

        COSDictionary fonts() {
            return fonts;
        }
    }

    private static final class ExistingContentOutput extends OutputStream {

        private final WorkflowResourceContext.OwnedByteAccumulator output;
        private final long maximum;
        private long size;

        private ExistingContentOutput(
                WorkflowResourceContext.OwnedByteAccumulator output,
                long maximum) {
            this.output = output;
            this.maximum = maximum;
        }

        @Override
        public void write(int value) throws IOException {
            requireCapacity(1);
            output.write(value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length)
                throws IOException {
            if (bytes == null
                    || offset < 0
                    || length < 0
                    || offset > bytes.length - length) {
                throw new IndexOutOfBoundsException();
            }
            requireCapacity(length);
            output.write(bytes, offset, length);
        }

        private void requireCapacity(int amount) throws IOException {
            if (amount < 0 || size > maximum - amount) {
                throw new ExistingContentLimitIOException();
            }
            size += amount;
        }
    }

    private static final class ExistingContentLimitIOException
            extends IOException {

        private static final long serialVersionUID = 1L;
    }

    private static final class ExistingOperatorBalance {
        private final WorkflowResourceContext resources;
        private int graphicsDepth;
        private int markedContentDepth;
        private int compatibilityDepth;
        private boolean text;

        private ExistingOperatorBalance(
                WorkflowResourceContext resources) {
            this.resources = resources;
        }

        void accept(String operator) throws IOException {
            if ("BT".equals(operator)) {
                if (text) {
                    throw new IOException("nested text scope");
                }
                text = true;
            } else if ("ET".equals(operator)) {
                if (!text) {
                    throw new IOException("unmatched text end");
                }
                text = false;
            } else if ("q".equals(operator)) {
                if (text || graphicsDepth == Integer.MAX_VALUE) {
                    throw new IOException("invalid graphics save");
                }
                graphicsDepth++;
                resources.requireNestingDepthAsIOException(graphicsDepth);
            } else if ("Q".equals(operator)) {
                if (text || graphicsDepth == 0) {
                    throw new IOException("unmatched graphics restore");
                }
                graphicsDepth--;
            } else if ("BMC".equals(operator) || "BDC".equals(operator)) {
                if (markedContentDepth == Integer.MAX_VALUE) {
                    throw new IOException("marked-content depth overflow");
                }
                markedContentDepth++;
                resources.requireNestingDepthAsIOException(
                        markedContentDepth);
            } else if ("EMC".equals(operator)) {
                if (markedContentDepth == 0) {
                    throw new IOException("unmatched marked-content end");
                }
                markedContentDepth--;
            } else if ("BX".equals(operator)) {
                if (compatibilityDepth == Integer.MAX_VALUE) {
                    throw new IOException("compatibility depth overflow");
                }
                compatibilityDepth++;
                resources.requireNestingDepthAsIOException(
                        compatibilityDepth);
            } else if ("EX".equals(operator)) {
                if (compatibilityDepth == 0) {
                    throw new IOException("unmatched compatibility end");
                }
                compatibilityDepth--;
            } else if (isTextOperator(operator) && !text) {
                throw new IOException("text operator outside text scope");
            }
        }

        void requireBalanced() throws IOException {
            if (graphicsDepth != 0
                    || markedContentDepth != 0
                    || compatibilityDepth != 0
                    || text) {
                throw new IOException("unbalanced page content");
            }
        }

        private static boolean isTextOperator(String operator) {
            return "Tc".equals(operator)
                    || "Tw".equals(operator)
                    || "Tz".equals(operator)
                    || "TL".equals(operator)
                    || "Tf".equals(operator)
                    || "Tr".equals(operator)
                    || "Ts".equals(operator)
                    || "Td".equals(operator)
                    || "TD".equals(operator)
                    || "Tm".equals(operator)
                    || "T*".equals(operator)
                    || "Tj".equals(operator)
                    || "TJ".equals(operator)
                    || "'".equals(operator)
                    || "\"".equals(operator);
        }
    }
}
