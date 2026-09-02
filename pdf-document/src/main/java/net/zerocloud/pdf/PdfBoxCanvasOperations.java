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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.zerocloud.pdf.composition.CanvasFont;
import net.zerocloud.pdf.composition.CanvasMatrix;
import net.zerocloud.pdf.composition.CanvasProgram;
import net.zerocloud.pdf.composition.CanvasWindingRule;
import net.zerocloud.pdf.composition.command.DrawCanvas;
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

/** Validates and appends backend-neutral Canvas Programs atomically. */
final class PdfBoxCanvasOperations {

    static final String CAPABILITY_ID =
            "composition.canvas.draw-positioned-text";

    private static final int MAXIMUM_INSTRUCTIONS = 10000;
    private static final int MAXIMUM_GRAPHICS_STATE_DEPTH = 64;
    private static final int MAXIMUM_PARENT_DEPTH = 64;
    private static final int MAXIMUM_FONT_RESOURCES = 256;
    private static final int MAXIMUM_GLYPH_BYTES = 4;
    private static final int MAXIMUM_PROGRAM_BYTES = 1024 * 1024;
    private static final int MAXIMUM_EXISTING_CONTENT_BYTES = 8 * 1024 * 1024;
    private static final double MAXIMUM_ABSOLUTE_NUMBER = 1000000000d;
    private static final double MAXIMUM_FONT_SIZE = 1000000d;

    private static final COSName TYPE_0 = COSName.getPDFName("Type0");
    private static final COSName TYPE_1 = COSName.getPDFName("Type1");
    private static final COSName MM_TYPE_1 = COSName.getPDFName("MMType1");
    private static final COSName TRUE_TYPE = COSName.getPDFName("TrueType");
    private static final COSName CID_FONT_TYPE_0 =
            COSName.getPDFName("CIDFontType0");
    private static final COSName CID_FONT_TYPE_2 =
            COSName.getPDFName("CIDFontType2");
    private static final COSName IDENTITY_H = COSName.getPDFName("Identity-H");
    private static final COSName IDENTITY_V = COSName.getPDFName("Identity-V");
    private static final COSName DESCENDANT_FONTS =
            COSName.getPDFName("DescendantFonts");

    private final PDDocument document;
    private final PdfBoxValueAdapter valueAdapter;

    PdfBoxCanvasOperations(
            PDDocument document,
            PdfBoxValueAdapter valueAdapter) {
        this.document = document;
        this.valueAdapter = valueAdapter;
    }

    boolean supports(DocumentCommand command) {
        return command instanceof DrawCanvas;
    }

    void execute(DrawCanvas command) throws DocumentFailure {
        PDPage page = selectedPage(command.getPageNumber());
        ValidatedProgram program = validate(command.getProgram());
        ExistingContents existing = prepareExistingContents(
                page.getCOSObject());
        ResourcesPlan resources = prepareResources(
                page.getCOSObject(),
                program.glyphsByFont);
        byte[] operators = serialize(
                command.getProgram(),
                resources.namesByFont);
        if (operators.length > MAXIMUM_PROGRAM_BYTES) {
            throw invalidProgram();
        }
        try {
            PdfBoxContentStreamPreflight.validate(operators);
        } catch (IOException invalidGeneratedContent) {
            throw invalidProgram();
        }

        COSArray contents = new COSArray();
        contents.setDirect(true);
        if (!existing.values.isEmpty()) {
            contents.add(contentStream("q\n"));
            for (COSBase value : existing.values) {
                contents.add(value);
            }
            contents.add(contentStream("Q\nn\n"));
        }
        contents.add(contentStream(operators));

        COSDictionary pageDictionary = page.getCOSObject();
        try {
            if (resources.changed) {
                pageDictionary.setItem(COSName.RESOURCES, resources.resources);
            }
            pageDictionary.setItem(COSName.CONTENTS, contents);
        } catch (RuntimeException backendFailure) {
            throw failure(
                    DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                    "The Canvas Program could not be applied.");
        }
    }

    static DocumentFailure signatureFailure() {
        return failure(
                DocumentFailureCode.SIGNATURE_POLICY_REJECTED,
                "The Existing Signature policy does not permit Canvas drawing.");
    }

    static void requireModificationPermission(
            PasswordSecurityInfo securityInfo) throws DocumentFailure {
        if (securityInfo.isPasswordProtected()
                && !securityInfo.getEffectivePermissions().canModify()) {
            throw failure(
                    DocumentFailureCode.DOCUMENT_PERMISSION_DENIED,
                    "The Source credential does not authorize Canvas drawing.");
        }
    }

    private PDPage selectedPage(int pageNumber) throws DocumentFailure {
        if (pageNumber < 1 || pageNumber > document.getNumberOfPages()) {
            throw failure(
                    DocumentFailureCode.PAGE_RANGE_INVALID,
                    "The Canvas page selection is invalid.");
        }
        return document.getPage(pageNumber - 1);
    }

    private static ValidatedProgram validate(CanvasProgram program)
            throws DocumentFailure {
        List<CanvasProgram.Instruction> instructions =
                program.getInstructions();
        if (instructions.isEmpty()
                || instructions.size() > MAXIMUM_INSTRUCTIONS) {
            throw invalidProgram();
        }

        int graphicsDepth = 0;
        CanvasState state = CanvasState.IDLE;
        CanvasFont activeFont = null;
        Map<CanvasFont, List<byte[]>> glyphs =
                new LinkedHashMap<CanvasFont, List<byte[]>>();

        for (CanvasProgram.Instruction instruction : instructions) {
            switch (instruction.getKind()) {
                case SAVE_STATE:
                    requireState(state, CanvasState.IDLE);
                    graphicsDepth++;
                    if (graphicsDepth > MAXIMUM_GRAPHICS_STATE_DEPTH) {
                        throw invalidProgram();
                    }
                    break;
                case RESTORE_STATE:
                    requireState(state, CanvasState.IDLE);
                    if (graphicsDepth == 0) {
                        throw invalidProgram();
                    }
                    graphicsDepth--;
                    break;
                case TRANSFORM:
                    requireState(state, CanvasState.IDLE);
                    requireMatrix(instruction.getMatrix());
                    break;
                case MOVE_TO:
                    if (state == CanvasState.TEXT_READY
                            || state == CanvasState.TEXT_NEEDS_MATRIX) {
                        throw invalidProgram();
                    }
                    requireNumbers(instruction.getNumbers(), 2);
                    state = CanvasState.PATH;
                    break;
                case LINE_TO:
                    requireState(state, CanvasState.PATH);
                    requireNumbers(instruction.getNumbers(), 2);
                    break;
                case CURVE_TO:
                    requireState(state, CanvasState.PATH);
                    requireNumbers(instruction.getNumbers(), 6);
                    break;
                case CLOSE_PATH:
                    requireState(state, CanvasState.PATH);
                    break;
                case STROKE:
                case FILL:
                case CLIP:
                    requireState(state, CanvasState.PATH);
                    state = CanvasState.IDLE;
                    break;
                case BEGIN_TEXT:
                    requireState(state, CanvasState.IDLE);
                    double[] fontSize = instruction.getNumbers();
                    requireNumbers(fontSize, 1);
                    if (fontSize[0] <= 0d
                            || fontSize[0] > MAXIMUM_FONT_SIZE) {
                        throw invalidProgram();
                    }
                    requireMatrix(instruction.getMatrix());
                    if (instruction.getFont() == null
                            || instruction.getRenderingMode() == null) {
                        throw invalidProgram();
                    }
                    state = CanvasState.TEXT_READY;
                    activeFont = instruction.getFont();
                    if (!glyphs.containsKey(activeFont)) {
                        if (glyphs.size() >= MAXIMUM_FONT_RESOURCES) {
                            throw invalidProgram();
                        }
                        glyphs.put(activeFont, new ArrayList<byte[]>());
                    }
                    break;
                case SET_TEXT_MATRIX:
                    requireState(state, CanvasState.TEXT_NEEDS_MATRIX);
                    requireMatrix(instruction.getMatrix());
                    state = CanvasState.TEXT_READY;
                    break;
                case SHOW_GLYPH:
                    requireState(state, CanvasState.TEXT_READY);
                    if (activeFont == null) {
                        throw invalidProgram();
                    }
                    byte[] glyph = instruction.getGlyphCode();
                    if (glyph == null || glyph.length < 1
                            || glyph.length > MAXIMUM_GLYPH_BYTES) {
                        throw invalidProgram();
                    }
                    glyphs.get(activeFont).add(glyph);
                    state = CanvasState.TEXT_NEEDS_MATRIX;
                    break;
                case END_TEXT:
                    requireState(state, CanvasState.TEXT_NEEDS_MATRIX);
                    state = CanvasState.IDLE;
                    activeFont = null;
                    break;
                default:
                    throw invalidProgram();
            }
        }
        if (graphicsDepth != 0 || state != CanvasState.IDLE) {
            throw invalidProgram();
        }
        return new ValidatedProgram(glyphs);
    }

    private ExistingContents prepareExistingContents(COSDictionary page)
            throws DocumentFailure {
        COSBase rawContents = page.getItem(COSName.CONTENTS);
        if (rawContents == null || dereference(rawContents) instanceof COSNull) {
            return new ExistingContents(Collections.<COSBase>emptyList());
        }

        COSBase value = dereference(rawContents);
        List<COSBase> values = new ArrayList<COSBase>();
        if (value instanceof COSStream) {
            if (!(rawContents instanceof COSObject)) {
                throw preservationUnsupported();
            }
            values.add(rawContents);
        } else if (value instanceof COSArray) {
            COSArray array = (COSArray) value;
            for (int index = 0; index < array.size(); index++) {
                COSBase rawStream = array.get(index);
                if (!(rawStream instanceof COSObject)
                        || !(dereference(rawStream) instanceof COSStream)) {
                    throw preservationUnsupported();
                }
                values.add(rawStream);
            }
        } else {
            throw preservationUnsupported();
        }

        ByteArrayOutputStream combined = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int remaining = MAXIMUM_EXISTING_CONTENT_BYTES;
        for (COSBase raw : values) {
            COSStream stream = (COSStream) dereference(raw);
            if (stream.containsKey(COSName.F)) {
                throw preservationUnsupported();
            }
            try (InputStream input = stream.createInputStream()) {
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (count > remaining) {
                        throw preservationUnsupported();
                    }
                    combined.write(buffer, 0, count);
                    remaining -= count;
                }
            } catch (IOException decodingFailure) {
                throw preservationUnsupported();
            }
            combined.write('\n');
        }
        byte[] decoded = combined.toByteArray();
        try {
            PdfBoxContentStreamPreflight.validate(decoded);
            requireBalancedExistingContent(decoded);
        } catch (IOException malformed) {
            throw preservationUnsupported();
        }
        return new ExistingContents(values);
    }

    private ResourcesPlan prepareResources(
            COSDictionary page,
            Map<CanvasFont, List<byte[]>> glyphsByFont)
            throws DocumentFailure {
        COSDictionary effective = effectiveResources(page);
        COSDictionary existingFonts = null;
        COSBase rawFonts = effective == null
                ? null : effective.getItem(COSName.FONT);
        if (rawFonts != null) {
            COSBase fontValue = dereference(rawFonts);
            if (!(fontValue instanceof COSDictionary)
                    || fontValue instanceof COSStream) {
                throw preservationUnsupported();
            }
            existingFonts = (COSDictionary) fontValue;
        }
        if (glyphsByFont.isEmpty()) {
            return ResourcesPlan.unchanged();
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

        boolean changed = false;
        Map<CanvasFont, COSName> names =
                new LinkedHashMap<CanvasFont, COSName>();
        for (Map.Entry<CanvasFont, List<byte[]>> entry
                : glyphsByFont.entrySet()) {
            COSBase rawFont;
            try {
                rawFont = valueAdapter.referencedObject(
                        entry.getKey().getObjectReference());
            } catch (DocumentFailure invalidReference) {
                throw invalidResource();
            }
            if (!(rawFont instanceof COSObject)) {
                throw invalidResource();
            }
            COSBase fontValue = dereference(rawFont);
            if (!(fontValue instanceof COSDictionary)
                    || fontValue instanceof COSStream) {
                throw invalidResource();
            }
            COSDictionary font = (COSDictionary) fontValue;
            int codeLength = validateFont(font);
            for (byte[] glyph : entry.getValue()) {
                if (glyph.length != codeLength) {
                    throw invalidResource();
                }
            }

            COSName name = nameFor(fonts, font);
            if (name == null) {
                name = availableFontName(fonts);
                fonts.setItem(name, rawFont);
                changed = true;
            }
            names.put(entry.getKey(), name);
        }
        if (changed) {
            resources.setItem(COSName.FONT, fonts);
        }
        return new ResourcesPlan(resources, names, changed);
    }

    private static COSDictionary effectiveResources(COSDictionary page)
            throws DocumentFailure {
        IdentityHashMap<COSDictionary, Boolean> visited =
                new IdentityHashMap<COSDictionary, Boolean>();
        COSDictionary current = page;
        int depth = 0;
        while (current != null && visited.put(current, Boolean.TRUE) == null) {
            depth++;
            if (depth > MAXIMUM_PARENT_DEPTH) {
                throw preservationUnsupported();
            }
            COSBase rawResources = current.getItem(COSName.RESOURCES);
            if (rawResources != null) {
                COSBase resources = dereference(rawResources);
                if (!(resources instanceof COSDictionary)
                        || resources instanceof COSStream) {
                    throw preservationUnsupported();
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
                    throw preservationUnsupported();
                }
                current = (COSDictionary) parent;
            }
        }
        if (current != null) {
            throw preservationUnsupported();
        }
        return null;
    }

    private static int validateFont(COSDictionary font)
            throws DocumentFailure {
        if (!COSName.FONT.equals(dereference(font.getItem(COSName.TYPE)))) {
            throw invalidResource();
        }
        COSBase subtype = dereference(font.getItem(COSName.SUBTYPE));
        if (!(subtype instanceof COSName)
                || !(dereference(font.getItem(COSName.BASE_FONT))
                        instanceof COSName)) {
            throw invalidResource();
        }
        if (TYPE_1.equals(subtype)
                || MM_TYPE_1.equals(subtype)
                || TRUE_TYPE.equals(subtype)) {
            return 1;
        }
        if (!TYPE_0.equals(subtype)) {
            throw invalidResource();
        }
        COSBase encoding = dereference(font.getItem(COSName.ENCODING));
        if (!IDENTITY_H.equals(encoding) && !IDENTITY_V.equals(encoding)) {
            throw invalidResource();
        }
        COSBase descendants = dereference(font.getItem(DESCENDANT_FONTS));
        if (!(descendants instanceof COSArray)
                || ((COSArray) descendants).size() != 1) {
            throw invalidResource();
        }
        COSBase descendant = dereference(((COSArray) descendants).get(0));
        if (!(descendant instanceof COSDictionary)
                || descendant instanceof COSStream) {
            throw invalidResource();
        }
        COSBase descendantSubtype = dereference(
                ((COSDictionary) descendant).getItem(COSName.SUBTYPE));
        if (!CID_FONT_TYPE_0.equals(descendantSubtype)
                && !CID_FONT_TYPE_2.equals(descendantSubtype)) {
            throw invalidResource();
        }
        return 2;
    }

    private static COSName nameFor(
            COSDictionary fonts,
            COSDictionary target) {
        for (Map.Entry<COSName, COSBase> entry : fonts.entrySet()) {
            if (dereference(entry.getValue()) == target) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static COSName availableFontName(COSDictionary fonts)
            throws DocumentFailure {
        for (int suffix = 1; suffix <= MAXIMUM_FONT_RESOURCES; suffix++) {
            COSName name = COSName.getPDFName("FolioT17F" + suffix);
            if (!fonts.containsKey(name)) {
                return name;
            }
        }
        throw invalidResource();
    }

    private COSObject contentStream(String operators)
            throws DocumentFailure {
        return contentStream(operators.getBytes(StandardCharsets.US_ASCII));
    }

    private COSObject contentStream(byte[] operators)
            throws DocumentFailure {
        try {
            COSStream stream = document.getDocument().createCOSStream();
            try (OutputStream output = stream.createOutputStream()) {
                output.write(operators);
            }
            return new COSObject(stream);
        } catch (IOException | RuntimeException failure) {
            throw failure(
                    DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                    "The Canvas Program could not be applied.");
        }
    }

    private static byte[] serialize(
            CanvasProgram program,
            Map<CanvasFont, COSName> namesByFont)
            throws DocumentFailure {
        StringBuilder output = new StringBuilder();
        output.append("q\n");
        for (CanvasProgram.Instruction instruction
                : program.getInstructions()) {
            switch (instruction.getKind()) {
                case SAVE_STATE:
                    output.append("q\n");
                    break;
                case RESTORE_STATE:
                    output.append("Q\n");
                    break;
                case TRANSFORM:
                    appendMatrix(output, instruction.getMatrix());
                    output.append(" cm\n");
                    break;
                case MOVE_TO:
                    appendNumbers(output, instruction.getNumbers());
                    output.append(" m\n");
                    break;
                case LINE_TO:
                    appendNumbers(output, instruction.getNumbers());
                    output.append(" l\n");
                    break;
                case CURVE_TO:
                    appendNumbers(output, instruction.getNumbers());
                    output.append(" c\n");
                    break;
                case CLOSE_PATH:
                    output.append("h\n");
                    break;
                case STROKE:
                    output.append("S\n");
                    break;
                case FILL:
                    output.append(instruction.getWindingRule()
                            == CanvasWindingRule.EVEN_ODD ? "f*\n" : "f\n");
                    break;
                case CLIP:
                    output.append(instruction.getWindingRule()
                            == CanvasWindingRule.EVEN_ODD
                            ? "W* n\n" : "W n\n");
                    break;
                case BEGIN_TEXT:
                    COSName fontName = namesByFont.get(instruction.getFont());
                    if (fontName == null) {
                        throw invalidResource();
                    }
                    output.append("BT\n");
                    output.append(pdfName(fontName)).append(' ')
                            .append(number(instruction.getNumbers()[0]))
                            .append(" Tf\n");
                    output.append(instruction.getRenderingMode()
                            .getOperatorValue()).append(" Tr\n");
                    appendMatrix(output, instruction.getMatrix());
                    output.append(" Tm\n");
                    break;
                case SET_TEXT_MATRIX:
                    appendMatrix(output, instruction.getMatrix());
                    output.append(" Tm\n");
                    break;
                case SHOW_GLYPH:
                    output.append('<');
                    for (byte value : instruction.getGlyphCode()) {
                        int unsigned = value & 0xff;
                        output.append(Character.forDigit(
                                (unsigned >>> 4) & 0xf, 16));
                        output.append(Character.forDigit(unsigned & 0xf, 16));
                    }
                    output.append("> Tj\n");
                    break;
                case END_TEXT:
                    output.append("ET\n");
                    break;
                default:
                    throw invalidProgram();
            }
            if (output.length() > MAXIMUM_PROGRAM_BYTES) {
                throw invalidProgram();
            }
        }
        output.append("Q\n");
        return output.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private static String pdfName(COSName name) throws DocumentFailure {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            name.writePDF(output);
            return new String(output.toByteArray(), StandardCharsets.US_ASCII);
        } catch (IOException failure) {
            throw invalidResource();
        }
    }

    private static void appendMatrix(
            StringBuilder output,
            CanvasMatrix matrix) throws DocumentFailure {
        appendNumbers(output, new double[] {
            matrix.getA(), matrix.getB(), matrix.getC(),
            matrix.getD(), matrix.getE(), matrix.getF()
        });
    }

    private static void appendNumbers(
            StringBuilder output,
            double[] values) throws DocumentFailure {
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                output.append(' ');
            }
            output.append(number(values[index]));
        }
    }

    private static String number(double value) throws DocumentFailure {
        requireNumber(value);
        if (value == 0d) {
            return "0";
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static void requireState(
            CanvasState actual,
            CanvasState expected) throws DocumentFailure {
        if (actual != expected) {
            throw invalidProgram();
        }
    }

    private static void requireNumbers(double[] values, int expected)
            throws DocumentFailure {
        if (values == null || values.length != expected) {
            throw invalidProgram();
        }
        for (double value : values) {
            requireNumber(value);
        }
    }

    private static void requireMatrix(CanvasMatrix matrix)
            throws DocumentFailure {
        if (matrix == null) {
            throw invalidProgram();
        }
        requireNumber(matrix.getA());
        requireNumber(matrix.getB());
        requireNumber(matrix.getC());
        requireNumber(matrix.getD());
        requireNumber(matrix.getE());
        requireNumber(matrix.getF());
    }

    private static void requireNumber(double value) throws DocumentFailure {
        if (Double.isNaN(value)
                || Double.isInfinite(value)
                || Math.abs(value) > MAXIMUM_ABSOLUTE_NUMBER) {
            throw invalidProgram();
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

    private static COSBase dereference(COSBase value) {
        COSBase current = value;
        IdentityHashMap<COSBase, Boolean> visited =
                new IdentityHashMap<COSBase, Boolean>();
        while (current instanceof COSObject
                && visited.put(current, Boolean.TRUE) == null) {
            current = ((COSObject) current).getObject();
        }
        return current;
    }

    private static DocumentFailure invalidProgram() {
        return failure(
                DocumentFailureCode.CANVAS_PROGRAM_INVALID,
                "The Canvas Program is invalid.");
    }

    private static DocumentFailure invalidResource() {
        return failure(
                DocumentFailureCode.CANVAS_RESOURCE_INVALID,
                "The Canvas Font resource is invalid.");
    }

    private static DocumentFailure preservationUnsupported() {
        return failure(
                DocumentFailureCode.CANVAS_PRESERVATION_UNSUPPORTED,
                "The page content or resources cannot be preserved safely for Canvas drawing.");
    }

    private static DocumentFailure failure(
            DocumentFailureCode code,
            String diagnostic) {
        return new DocumentFailure(code, CAPABILITY_ID, diagnostic);
    }

    private static final class ValidatedProgram {

        private final Map<CanvasFont, List<byte[]>> glyphsByFont;

        ValidatedProgram(Map<CanvasFont, List<byte[]>> glyphsByFont) {
            this.glyphsByFont = glyphsByFont;
        }
    }

    private enum CanvasState {
        IDLE,
        PATH,
        TEXT_READY,
        TEXT_NEEDS_MATRIX
    }

    private static final class ExistingContents {

        private final List<COSBase> values;

        ExistingContents(List<COSBase> values) {
            this.values = values;
        }
    }

    private static final class ResourcesPlan {

        private final COSDictionary resources;
        private final Map<CanvasFont, COSName> namesByFont;
        private final boolean changed;

        ResourcesPlan(
                COSDictionary resources,
                Map<CanvasFont, COSName> namesByFont,
                boolean changed) {
            this.resources = resources;
            this.namesByFont = namesByFont;
            this.changed = changed;
        }

        static ResourcesPlan unchanged() {
            return new ResourcesPlan(
                    null,
                    Collections.<CanvasFont, COSName>emptyMap(),
                    false);
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
