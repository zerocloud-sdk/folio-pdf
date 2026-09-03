package net.zerocloud.pdf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.zerocloud.pdf.composition.CanvasFont;
import net.zerocloud.pdf.composition.CanvasProgram;
import net.zerocloud.pdf.composition.CanvasTransparencyGroup;
import net.zerocloud.pdf.composition.CanvasWindingRule;
import net.zerocloud.pdf.composition.command.DrawCanvas;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

/** Validates and appends backend-neutral Canvas Programs atomically. */
final class PdfBoxCanvasOperations {

    static final String CAPABILITY_ID =
            "composition.canvas.draw-positioned-text";

    private static final int MAXIMUM_INSTRUCTIONS = 10000;
    private static final int MAXIMUM_GRAPHICS_STATE_DEPTH = 64;
    private static final int MAXIMUM_FONT_RESOURCES = 256;
    private static final int MAXIMUM_GLYPH_BYTES = 4;
    static final int MAXIMUM_PROGRAM_BYTES = 1024 * 1024;
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
    private final PdfBoxCanvasResourceOperations resourceOperations;

    PdfBoxCanvasOperations(
            PDDocument document,
            PdfBoxValueAdapter valueAdapter) {
        this.document = document;
        this.valueAdapter = valueAdapter;
        this.resourceOperations = new PdfBoxCanvasResourceOperations(
                document,
                valueAdapter);
    }

    boolean supports(DocumentCommand command) {
        return command instanceof DrawCanvas;
    }

    void execute(DrawCanvas command) throws DocumentFailure {
        if (command.getVersion() == DrawCanvas.VERSION_2) {
            try {
                executeVersion2(command);
            } catch (DocumentFailure failure) {
                if (CAPABILITY_ID.equals(failure.getCapabilityId())) {
                    throw new DocumentFailure(
                            failure.getCode(),
                            PdfBoxCanvasResourceOperations.CAPABILITY_ID,
                            failure.getDiagnostic());
                }
                throw failure;
            }
            return;
        }
        if (command.getVersion() != DrawCanvas.VERSION_1) {
            throw invalidProgram();
        }
        PDPage page = selectedPage(command.getPageNumber());
        ValidatedProgram program = validate(
                command.getProgram(),
                CanvasProgram.VERSION_1);
        PdfBoxPageContentSupport.ExistingContents existing =
                PdfBoxPageContentSupport.prepareExistingContents(
                        page.getCOSObject(),
                        PdfBoxCanvasOperations::preservationUnsupported);
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

        PdfBoxPageContentSupport.apply(
                document,
                page,
                existing,
                operators,
                resources.resources,
                resources.changed,
                PdfBoxCanvasOperations::writeFailure);
    }

    private void executeVersion2(DrawCanvas command) throws DocumentFailure {
        if (command.getProgram().getVersion() != CanvasProgram.VERSION_2
                || !command.getResourceLimits().isPresent()
                || command.getResourceLimits().get().getVersion()
                        != net.zerocloud.pdf.composition.CanvasResourceLimits.VERSION_1) {
            throw invalidProgram();
        }
        PDPage page = selectedPage(command.getPageNumber());
        validate(command.getProgram(), CanvasProgram.VERSION_2);
        PdfBoxPageContentSupport.ExistingContents existing =
                PdfBoxPageContentSupport.prepareExistingContents(
                        page.getCOSObject(),
                        PdfBoxCanvasOperations::preservationUnsupported);
        PdfBoxCanvasResourceOperations.Plan plan = resourceOperations.prepare(
                PdfBoxPageContentSupport.effectiveResources(
                        page.getCOSObject(),
                        PdfBoxCanvasOperations::preservationUnsupported),
                command.getProgram(),
                command.getResourceLimits().get(),
                !existing.isEmpty());

        PdfBoxPageContentSupport.apply(
                document,
                page,
                existing,
                plan.operators,
                plan.resources,
                plan.resourcesChanged,
                PdfBoxCanvasOperations::writeFailure);
    }

    String capabilityId(DrawCanvas command) {
        return command.getVersion() == DrawCanvas.VERSION_2
                ? PdfBoxCanvasResourceOperations.CAPABILITY_ID
                : CAPABILITY_ID;
    }

    static DocumentFailure signatureFailure() {
        return failure(
                DocumentFailureCode.SIGNATURE_POLICY_REJECTED,
                "The Existing Signature policy does not permit Canvas drawing.");
    }

    static DocumentFailure signatureFailure(DrawCanvas command) {
        if (command.getVersion() == DrawCanvas.VERSION_2) {
            return new DocumentFailure(
                    DocumentFailureCode.SIGNATURE_POLICY_REJECTED,
                    PdfBoxCanvasResourceOperations.CAPABILITY_ID,
                    "The Existing Signature policy does not permit Canvas drawing.");
        }
        return signatureFailure();
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

    static void requireModificationPermission(
            PasswordSecurityInfo securityInfo,
            DrawCanvas command) throws DocumentFailure {
        try {
            requireModificationPermission(securityInfo);
        } catch (DocumentFailure failure) {
            if (command.getVersion() != DrawCanvas.VERSION_2) {
                throw failure;
            }
            throw new DocumentFailure(
                    failure.getCode(),
                    PdfBoxCanvasResourceOperations.CAPABILITY_ID,
                    failure.getDiagnostic());
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

    private static ValidatedProgram validate(
            CanvasProgram program,
            int expectedVersion)
            throws DocumentFailure {
        ValidationAccumulator accumulator = new ValidationAccumulator();
        Map<CanvasFont, List<byte[]>> glyphs =
                new LinkedHashMap<CanvasFont, List<byte[]>>();
        validateProgram(program, expectedVersion, accumulator, glyphs, 0);
        return new ValidatedProgram(glyphs);
    }

    private static void validateProgram(
            CanvasProgram program,
            int expectedVersion,
            ValidationAccumulator accumulator,
            Map<CanvasFont, List<byte[]>> glyphs,
            int groupDepth)
            throws DocumentFailure {
        if (program == null || program.getVersion() != expectedVersion) {
            throw invalidProgram();
        }
        List<CanvasProgram.Instruction> instructions =
                program.getInstructions();
        if (instructions.isEmpty()) {
            throw invalidProgram();
        }
        accumulator.instructionCount += instructions.size();
        if (accumulator.instructionCount > MAXIMUM_INSTRUCTIONS) {
            throw invalidProgram();
        }

        int graphicsDepth = 0;
        CanvasState state = CanvasState.IDLE;
        CanvasFont activeFont = null;
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
                    PdfBoxPageContentSupport.requireMatrix(
                            instruction.getMatrix(),
                            PdfBoxCanvasOperations::invalidProgram);
                    break;
                case MOVE_TO:
                    if (state == CanvasState.TEXT_READY
                            || state == CanvasState.TEXT_NEEDS_MATRIX) {
                        throw invalidProgram();
                    }
                    PdfBoxPageContentSupport.requireNumbers(
                            instruction.getNumbers(),
                            2,
                            PdfBoxCanvasOperations::invalidProgram);
                    state = CanvasState.PATH;
                    break;
                case LINE_TO:
                    requireState(state, CanvasState.PATH);
                    PdfBoxPageContentSupport.requireNumbers(
                            instruction.getNumbers(),
                            2,
                            PdfBoxCanvasOperations::invalidProgram);
                    break;
                case CURVE_TO:
                    requireState(state, CanvasState.PATH);
                    PdfBoxPageContentSupport.requireNumbers(
                            instruction.getNumbers(),
                            6,
                            PdfBoxCanvasOperations::invalidProgram);
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
                    PdfBoxPageContentSupport.requireNumbers(
                            fontSize,
                            1,
                            PdfBoxCanvasOperations::invalidProgram);
                    if (fontSize[0] <= 0d
                            || fontSize[0] > MAXIMUM_FONT_SIZE) {
                        throw invalidProgram();
                    }
                    PdfBoxPageContentSupport.requireMatrix(
                            instruction.getMatrix(),
                            PdfBoxCanvasOperations::invalidProgram);
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
                    PdfBoxPageContentSupport.requireMatrix(
                            instruction.getMatrix(),
                            PdfBoxCanvasOperations::invalidProgram);
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
                case SET_FILL_COLOR:
                case SET_STROKE_COLOR:
                case SET_TRANSPARENCY:
                    requireVersion2(expectedVersion);
                    requireState(state, CanvasState.IDLE);
                    break;
                case DRAW_IMAGE:
                    requireVersion2(expectedVersion);
                    requireState(state, CanvasState.IDLE);
                    PdfBoxPageContentSupport.requireMatrix(
                            instruction.getMatrix(),
                            PdfBoxCanvasOperations::invalidProgram);
                    break;
                case DRAW_TRANSPARENCY_GROUP:
                    requireVersion2(expectedVersion);
                    requireState(state, CanvasState.IDLE);
                    PdfBoxPageContentSupport.requireMatrix(
                            instruction.getMatrix(),
                            PdfBoxCanvasOperations::invalidProgram);
                    CanvasTransparencyGroup group =
                            instruction.getTransparencyGroup();
                    if (group == null) {
                        throw invalidProgram();
                    }
                    int nestedDepth = groupDepth + 1;
                    if (nestedDepth
                            > net.zerocloud.pdf.composition.CanvasResourceLimits
                                    .MAXIMUM_TRANSPARENCY_GROUP_DEPTH_VERSION_1) {
                        throw PdfBoxCanvasResourceOperations.limitFailure();
                    }
                    if (accumulator.activeGroups.containsKey(group)) {
                        throw invalidProgram();
                    }
                    if (!accumulator.validatedGroups.containsKey(group)) {
                        accumulator.activeGroups.put(group, Boolean.TRUE);
                        validateProgram(
                                group.getProgram(),
                                CanvasProgram.VERSION_2,
                                accumulator,
                                glyphs,
                                nestedDepth);
                        accumulator.activeGroups.remove(group);
                        accumulator.validatedGroups.put(group, Boolean.TRUE);
                    }
                    break;
                default:
                    throw invalidProgram();
            }
        }
        if (graphicsDepth != 0 || state != CanvasState.IDLE) {
            throw invalidProgram();
        }
    }

    private static void requireVersion2(int version) throws DocumentFailure {
        if (version != CanvasProgram.VERSION_2) {
            throw invalidProgram();
        }
    }

    private ResourcesPlan prepareResources(
            COSDictionary page,
            Map<CanvasFont, List<byte[]>> glyphsByFont)
            throws DocumentFailure {
        PdfBoxPageContentSupport.FontResources prepared =
                PdfBoxPageContentSupport.prepareFontResources(
                        page,
                        PdfBoxCanvasOperations::preservationUnsupported);
        if (glyphsByFont.isEmpty()) {
            return ResourcesPlan.unchanged();
        }
        COSDictionary resources = prepared.resources();
        COSDictionary fonts = prepared.fonts();

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
            COSBase fontValue = PdfBoxPageContentSupport.dereference(rawFont);
            if (!(fontValue instanceof COSDictionary)
                    || fontValue instanceof COSStream) {
                throw invalidResource();
            }
            COSDictionary font = (COSDictionary) fontValue;
            int codeLength = validateFontDictionary(font);
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

    static int validateFontDictionary(COSDictionary font)
            throws DocumentFailure {
        if (!COSName.FONT.equals(PdfBoxPageContentSupport.dereference(
                font.getItem(COSName.TYPE)))) {
            throw invalidResource();
        }
        COSBase subtype = PdfBoxPageContentSupport.dereference(
                font.getItem(COSName.SUBTYPE));
        if (!(subtype instanceof COSName)
                || !(PdfBoxPageContentSupport.dereference(
                        font.getItem(COSName.BASE_FONT))
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
        COSBase encoding = PdfBoxPageContentSupport.dereference(
                font.getItem(COSName.ENCODING));
        if (!IDENTITY_H.equals(encoding) && !IDENTITY_V.equals(encoding)) {
            throw invalidResource();
        }
        COSBase descendants = PdfBoxPageContentSupport.dereference(
                font.getItem(DESCENDANT_FONTS));
        if (!(descendants instanceof COSArray)
                || ((COSArray) descendants).size() != 1) {
            throw invalidResource();
        }
        COSBase descendant = PdfBoxPageContentSupport.dereference(
                ((COSArray) descendants).get(0));
        if (!(descendant instanceof COSDictionary)
                || descendant instanceof COSStream) {
            throw invalidResource();
        }
        COSBase descendantSubtype = PdfBoxPageContentSupport.dereference(
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
            if (PdfBoxPageContentSupport.dereference(entry.getValue())
                    == target) {
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
                    PdfBoxPageContentSupport.appendMatrix(
                            output,
                            instruction.getMatrix(),
                            PdfBoxCanvasOperations::invalidProgram);
                    output.append(" cm\n");
                    break;
                case MOVE_TO:
                    PdfBoxPageContentSupport.appendNumbers(
                            output,
                            instruction.getNumbers(),
                            PdfBoxCanvasOperations::invalidProgram);
                    output.append(" m\n");
                    break;
                case LINE_TO:
                    PdfBoxPageContentSupport.appendNumbers(
                            output,
                            instruction.getNumbers(),
                            PdfBoxCanvasOperations::invalidProgram);
                    output.append(" l\n");
                    break;
                case CURVE_TO:
                    PdfBoxPageContentSupport.appendNumbers(
                            output,
                            instruction.getNumbers(),
                            PdfBoxCanvasOperations::invalidProgram);
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
                    output.append(PdfBoxPageContentSupport.pdfName(
                            fontName,
                            PdfBoxCanvasOperations::invalidResource))
                            .append(' ')
                            .append(PdfBoxPageContentSupport.number(
                                    instruction.getNumbers()[0],
                                    PdfBoxCanvasOperations::invalidProgram))
                            .append(" Tf\n");
                    output.append(instruction.getRenderingMode()
                            .getOperatorValue()).append(" Tr\n");
                    PdfBoxPageContentSupport.appendMatrix(
                            output,
                            instruction.getMatrix(),
                            PdfBoxCanvasOperations::invalidProgram);
                    output.append(" Tm\n");
                    break;
                case SET_TEXT_MATRIX:
                    PdfBoxPageContentSupport.appendMatrix(
                            output,
                            instruction.getMatrix(),
                            PdfBoxCanvasOperations::invalidProgram);
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

    private static void requireState(
            CanvasState actual,
            CanvasState expected) throws DocumentFailure {
        if (actual != expected) {
            throw invalidProgram();
        }
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

    private static DocumentFailure writeFailure() {
        return failure(
                DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                "The Canvas Program could not be applied.");
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

    private static final class ValidationAccumulator {

        private int instructionCount;
        private final IdentityHashMap<CanvasTransparencyGroup, Boolean>
                activeGroups =
                        new IdentityHashMap<CanvasTransparencyGroup, Boolean>();
        private final IdentityHashMap<CanvasTransparencyGroup, Boolean>
                validatedGroups =
                        new IdentityHashMap<CanvasTransparencyGroup, Boolean>();
    }

    private enum CanvasState {
        IDLE,
        PATH,
        TEXT_READY,
        TEXT_NEEDS_MATRIX
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

}
