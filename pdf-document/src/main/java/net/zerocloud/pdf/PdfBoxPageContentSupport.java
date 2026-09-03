package net.zerocloud.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
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
            FailureFactory preservationFailure) throws DocumentFailure {
        COSBase rawContents = page.getItem(COSName.CONTENTS);
        if (rawContents == null || dereference(rawContents) instanceof COSNull) {
            return new ExistingContents(Collections.<COSBase>emptyList());
        }

        COSBase value = dereference(rawContents);
        List<COSBase> values = new ArrayList<COSBase>();
        if (value instanceof COSStream) {
            if (!(rawContents instanceof COSObject)) {
                throw preservationFailure.create();
            }
            values.add(rawContents);
        } else if (value instanceof COSArray) {
            COSArray array = (COSArray) value;
            for (int index = 0; index < array.size(); index++) {
                COSBase rawStream = array.get(index);
                if (!(rawStream instanceof COSObject)
                        || !(dereference(rawStream) instanceof COSStream)) {
                    throw preservationFailure.create();
                }
                values.add(rawStream);
            }
        } else {
            throw preservationFailure.create();
        }

        ByteArrayOutputStream combined = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int remaining = MAXIMUM_EXISTING_CONTENT_BYTES;
        for (COSBase raw : values) {
            COSStream stream = (COSStream) dereference(raw);
            if (stream.containsKey(COSName.F)) {
                throw preservationFailure.create();
            }
            try (InputStream input = stream.createInputStream()) {
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (count > remaining) {
                        throw preservationFailure.create();
                    }
                    combined.write(buffer, 0, count);
                    remaining -= count;
                }
            } catch (IOException decodingFailure) {
                throw preservationFailure.create();
            }
            combined.write('\n');
        }
        byte[] decoded = combined.toByteArray();
        try {
            PdfBoxContentStreamPreflight.validate(decoded);
            requireBalancedExistingContent(decoded);
        } catch (IOException malformed) {
            throw preservationFailure.create();
        }
        return new ExistingContents(values);
    }

    static FontResources prepareFontResources(
            COSDictionary page,
            FailureFactory preservationFailure) throws DocumentFailure {
        COSDictionary effective = effectiveResources(page, preservationFailure);
        COSDictionary existingFonts = null;
        COSBase rawFonts = effective == null
                ? null : effective.getItem(COSName.FONT);
        if (rawFonts != null) {
            COSBase fontValue = dereference(rawFonts);
            if (!(fontValue instanceof COSDictionary)
                    || fontValue instanceof COSStream) {
                throw preservationFailure.create();
            }
            existingFonts = (COSDictionary) fontValue;
        }

        COSDictionary resources = new COSDictionary();
        resources.setDirect(true);
        if (effective != null) {
            resources.addAll(effective);
        }
        COSDictionary fonts = new COSDictionary();
        fonts.setDirect(true);
        if (existingFonts != null) {
            fonts.addAll(existingFonts);
        }
        return new FontResources(resources, fonts);
    }

    static COSDictionary effectiveResources(
            COSDictionary page,
            FailureFactory preservationFailure) throws DocumentFailure {
        IdentityHashMap<COSDictionary, Boolean> visited =
                new IdentityHashMap<COSDictionary, Boolean>();
        COSDictionary current = page;
        int depth = 0;
        while (current != null && visited.put(current, Boolean.TRUE) == null) {
            if (++depth > MAXIMUM_PARENT_DEPTH) {
                throw preservationFailure.create();
            }
            COSBase rawResources = current.getItem(COSName.RESOURCES);
            if (rawResources != null) {
                COSBase resources = dereference(rawResources);
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
                COSBase parent = dereference(rawParent);
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

    static void apply(
            PDDocument document,
            PDPage page,
            ExistingContents existing,
            byte[] operators,
            COSDictionary resources,
            boolean resourcesChanged,
            FailureFactory writeFailure) throws DocumentFailure {
        COSArray contents = new COSArray();
        contents.setDirect(true);
        if (!existing.isEmpty()) {
            contents.add(contentStream(
                    document,
                    "q\n".getBytes(StandardCharsets.US_ASCII),
                    writeFailure));
            for (COSBase value : existing.values()) {
                contents.add(value);
            }
            contents.add(contentStream(
                    document,
                    "Q\nn\n".getBytes(StandardCharsets.US_ASCII),
                    writeFailure));
        }
        contents.add(contentStream(document, operators, writeFailure));

        try {
            if (resourcesChanged) {
                page.getCOSObject().setItem(COSName.RESOURCES, resources);
            }
            page.getCOSObject().setItem(COSName.CONTENTS, contents);
        } catch (RuntimeException backendFailure) {
            throw writeFailure.create();
        }
    }

    static String pdfName(COSName name, FailureFactory failure)
            throws DocumentFailure {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            name.writePDF(output);
            return new String(output.toByteArray(), StandardCharsets.US_ASCII);
        } catch (IOException serializationFailure) {
            throw failure.create();
        }
    }

    static void appendMatrix(
            StringBuilder output,
            CanvasMatrix matrix,
            FailureFactory failure) throws DocumentFailure {
        appendNumbers(output, new double[] {
            matrix.getA(), matrix.getB(), matrix.getC(),
            matrix.getD(), matrix.getE(), matrix.getF()
        }, failure);
    }

    static void appendNumbers(
            StringBuilder output,
            double[] values,
            FailureFactory failure) throws DocumentFailure {
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                output.append(' ');
            }
            output.append(number(values[index], failure));
        }
    }

    static String number(double value, FailureFactory failure)
            throws DocumentFailure {
        requireNumber(value, failure);
        return value == 0d
                ? "0"
                : BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    static void requireNumber(double value, FailureFactory failure)
            throws DocumentFailure {
        if (Double.isNaN(value)
                || Double.isInfinite(value)
                || Math.abs(value) > MAXIMUM_ABSOLUTE_NUMBER) {
            throw failure.create();
        }
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
        if (matrix == null) {
            throw failure.create();
        }
        requireNumber(matrix.getA(), failure);
        requireNumber(matrix.getB(), failure);
        requireNumber(matrix.getC(), failure);
        requireNumber(matrix.getD(), failure);
        requireNumber(matrix.getE(), failure);
        requireNumber(matrix.getF(), failure);
    }

    static COSBase dereference(COSBase value) {
        COSBase current = value;
        IdentityHashMap<COSBase, Boolean> visited =
                new IdentityHashMap<COSBase, Boolean>();
        while (current instanceof COSObject
                && visited.put(current, Boolean.TRUE) == null) {
            current = ((COSObject) current).getObject();
        }
        return current;
    }

    private static COSObject contentStream(
            PDDocument document,
            byte[] operators,
            FailureFactory writeFailure) throws DocumentFailure {
        try {
            COSStream stream = document.getDocument().createCOSStream();
            try (OutputStream output = stream.createOutputStream()) {
                output.write(operators);
            }
            return new COSObject(stream);
        } catch (IOException | RuntimeException backendFailure) {
            throw writeFailure.create();
        }
    }

    private static void requireBalancedExistingContent(byte[] content)
            throws IOException {
        PDFStreamParser parser = new PDFStreamParser(content);
        ExistingOperatorBalance balance = new ExistingOperatorBalance();
        try {
            Object token;
            while ((token = parser.parseNextToken()) != null) {
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

    private static final class ExistingOperatorBalance {
        private int graphicsDepth;
        private int markedContentDepth;
        private int compatibilityDepth;
        private boolean text;

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
