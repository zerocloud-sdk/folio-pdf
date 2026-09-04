package net.zerocloud.pdf;

import java.io.IOException;
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
    private final WorkflowResourceContext resources;
    private WorkerPreservation workerPreservation;

    PdfBoxCanvasOperations(
            PDDocument document,
            PdfBoxValueAdapter valueAdapter,
            WorkflowResourceContext resources) {
        this.document = document;
        this.valueAdapter = valueAdapter;
        this.resources = resources;
        this.resourceOperations = new PdfBoxCanvasResourceOperations(
                document,
                valueAdapter,
                resources);
    }

    boolean supports(DocumentCommand command) {
        return command instanceof DrawCanvas;
    }

    void execute(DrawCanvas command) throws DocumentFailure {
        resources.checkpoint();
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
        try (ValidatedProgram program = validate(
                command.getProgram(),
                CanvasProgram.VERSION_1,
                resources)) {
            WorkerPreservation preflight = takeWorkerPreservation(page);
            PdfBoxPageContentSupport.ExistingContents existing;
            if (preflight == null) {
                existing = PdfBoxPageContentSupport.prepareExistingContents(
                        page.getCOSObject(),
                        PdfBoxCanvasOperations::preservationUnsupported,
                        resources);
            } else {
                existing = preflight.existing;
            }
            ResourcesPlan resources = prepareResources(
                    page.getCOSObject(),
                    program.glyphsByFont);
            try (WorkflowResourceContext.OwnedBytes ownedOperators =
                    serialize(
                            command.getProgram(),
                            resources.namesByFont,
                            program.glyphCodes)) {
                byte[] operators = ownedOperators.getBytes();
                if (operators.length > MAXIMUM_PROGRAM_BYTES) {
                    throw invalidProgram();
                }
                try {
                    PdfBoxContentStreamPreflight.validate(
                            operators, this.resources);
                } catch (IOException invalidGeneratedContent) {
                    this.resources.rethrowResourceOrTerminalFailure(
                            invalidGeneratedContent);
                    throw invalidProgram();
                }

                PdfBoxPageContentSupport.apply(
                        document,
                        page,
                        existing,
                        operators,
                        resources.resources,
                        resources.changed,
                        this.resources,
                        PdfBoxCanvasOperations::writeFailure);
            }
        }
    }

    private void executeVersion2(DrawCanvas command) throws DocumentFailure {
        if (command.getProgram().getVersion() != CanvasProgram.VERSION_2
                || !command.getResourceLimits().isPresent()
                || command.getResourceLimits().get().getVersion()
                        != net.zerocloud.pdf.composition.CanvasResourceLimits.VERSION_1) {
            throw invalidProgram();
        }
        PDPage page = selectedPage(command.getPageNumber());
        try (ValidatedProgram ignored = validate(
                command.getProgram(),
                CanvasProgram.VERSION_2,
                resources)) {
            // Generic Canvas state validation precedes resource preparation.
        }
        WorkerPreservation preflight = takeWorkerPreservation(page);
        PdfBoxPageContentSupport.ExistingContents existing;
        COSDictionary effectiveResources;
        if (preflight != null) {
            existing = preflight.existing;
            effectiveResources = preflight.effectiveResources;
        } else {
            existing = PdfBoxPageContentSupport.prepareExistingContents(
                    page.getCOSObject(),
                    PdfBoxCanvasOperations::preservationUnsupported,
                    resources);
            effectiveResources = PdfBoxPageContentSupport.effectiveResources(
                    page.getCOSObject(),
                    PdfBoxCanvasOperations::preservationUnsupported,
                    resources);
        }
        try (PdfBoxCanvasResourceOperations.Plan plan =
                resourceOperations.prepare(
                        effectiveResources,
                        command.getProgram(),
                        command.getResourceLimits().get(),
                        !existing.isEmpty())) {
            PdfBoxPageContentSupport.apply(
                    document,
                    page,
                    existing,
                    plan.operators,
                    plan.resources,
                    plan.resourcesChanged,
                    resources,
                    PdfBoxCanvasOperations::writeFailure);
        }
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
        return signatureFailure(command.getVersion());
    }

    static DocumentFailure signatureFailure(int version) {
        if (version == DrawCanvas.VERSION_2) {
            return new DocumentFailure(
                    DocumentFailureCode.SIGNATURE_POLICY_REJECTED,
                    PdfBoxCanvasResourceOperations.CAPABILITY_ID,
                    "The Existing Signature policy does not permit Canvas drawing.");
        }
        return signatureFailure();
    }

    void requirePage(int pageNumber) throws DocumentFailure {
        selectedPage(pageNumber);
    }

    void preflightWorkerCommand(
            int commandVersion,
            int programVersion,
            boolean limitsPresent,
            int limitsVersion,
            int pageNumber) throws DocumentFailure {
        if (commandVersion == DrawCanvas.VERSION_2) {
            if (programVersion != CanvasProgram.VERSION_2
                    || !limitsPresent
                    || limitsVersion
                            != net.zerocloud.pdf.composition.CanvasResourceLimits.VERSION_1) {
                throw new DocumentFailure(
                        DocumentFailureCode.CANVAS_PROGRAM_INVALID,
                        PdfBoxCanvasResourceOperations.CAPABILITY_ID,
                        "The Canvas Program is invalid.");
            }
            try {
                selectedPage(pageNumber);
            } catch (DocumentFailure failure) {
                throw new DocumentFailure(
                        failure.getCode(),
                        PdfBoxCanvasResourceOperations.CAPABILITY_ID,
                        failure.getDiagnostic());
            }
            return;
        }
        if (commandVersion != DrawCanvas.VERSION_1) {
            throw invalidProgram();
        }
        selectedPage(pageNumber);
    }

    void clearWorkerPreflight() {
        workerPreservation = null;
    }

    private WorkerPreservation takeWorkerPreservation(PDPage page) {
        WorkerPreservation preflight = workerPreservation;
        workerPreservation = null;
        if (preflight != null
                && preflight.page == page.getCOSObject()) {
            return preflight;
        }
        return null;
    }

    void preflightWorkerPreservation(int pageNumber)
            throws DocumentFailure {
        PDPage page = selectedPage(pageNumber);
        PdfBoxPageContentSupport.ExistingContents existing =
                PdfBoxPageContentSupport.prepareExistingContents(
                        page.getCOSObject(),
                        PdfBoxCanvasOperations::preservationUnsupported,
                        resources);
        COSDictionary effectiveResources = PdfBoxPageContentSupport
                .effectiveResources(
                        page.getCOSObject(),
                        PdfBoxCanvasOperations::preservationUnsupported,
                        resources);
        workerPreservation = new WorkerPreservation(
                page.getCOSObject(),
                existing,
                effectiveResources);
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
        requireModificationPermission(securityInfo, command.getVersion());
    }

    static void requireModificationPermission(
            PasswordSecurityInfo securityInfo,
            int version) throws DocumentFailure {
        try {
            requireModificationPermission(securityInfo);
        } catch (DocumentFailure failure) {
            if (version != DrawCanvas.VERSION_2) {
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

    static void preflightProgramShape(
            DrawCanvas command,
            WorkflowResourceContext resources) throws DocumentFailure {
        int expectedVersion;
        if (command.getVersion() == DrawCanvas.VERSION_1) {
            expectedVersion = CanvasProgram.VERSION_1;
        } else if (command.getVersion() == DrawCanvas.VERSION_2) {
            expectedVersion = CanvasProgram.VERSION_2;
        } else {
            throw invalidProgram();
        }
        try (ValidatedProgram ignored = validate(
                command.getProgram(),
                expectedVersion,
                resources)) {
            // The validated projection intentionally does not access image data.
        } catch (DocumentFailure failure) {
            if (command.getVersion() == DrawCanvas.VERSION_2
                    && CAPABILITY_ID.equals(failure.getCapabilityId())) {
                throw new DocumentFailure(
                        failure.getCode(),
                        PdfBoxCanvasResourceOperations.CAPABILITY_ID,
                        failure.getDiagnostic());
            }
            throw failure;
        }
    }

    private static ValidatedProgram validate(
            CanvasProgram program,
            int expectedVersion,
            WorkflowResourceContext resources)
            throws DocumentFailure {
        ValidationAccumulator accumulator = new ValidationAccumulator();
        Map<CanvasFont, List<byte[]>> glyphs =
                new LinkedHashMap<CanvasFont, List<byte[]>>();
        try {
            validateProgram(
                    program,
                    expectedVersion,
                    accumulator,
                    glyphs,
                    0,
                    resources);
            return new ValidatedProgram(
                    glyphs,
                    accumulator.glyphCodes,
                    accumulator.memoryReservations);
        } catch (DocumentFailure failure) {
            accumulator.releaseMemory();
            throw failure;
        } catch (RuntimeException failure) {
            accumulator.releaseMemory();
            throw failure;
        }
    }

    private static void validateProgram(
            CanvasProgram program,
            int expectedVersion,
            ValidationAccumulator accumulator,
            Map<CanvasFont, List<byte[]>> glyphs,
            int groupDepth,
            WorkflowResourceContext resources)
            throws DocumentFailure {
        resources.checkpoint();
        resources.requireNestingDepth(groupDepth);
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
            resources.checkpoint();
            switch (instruction.getKind()) {
                case SAVE_STATE:
                    requireState(state, CanvasState.IDLE);
                    graphicsDepth++;
                    resources.requireNestingDepth(graphicsDepth);
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
                    int glyphLength = instruction.getGlyphCodeLength();
                    if (glyphLength < 1
                            || glyphLength > MAXIMUM_GLYPH_BYTES) {
                        throw invalidProgram();
                    }
                    if (expectedVersion == CanvasProgram.VERSION_1) {
                        accumulator.reserveMemory(resources, glyphLength);
                        byte[] glyph = instruction.getGlyphCode();
                        if (glyph == null || glyph.length != glyphLength) {
                            throw invalidProgram();
                        }
                        glyphs.get(activeFont).add(glyph);
                        accumulator.glyphCodes.put(instruction, glyph);
                    }
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
                                nestedDepth,
                                resources);
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
                        PdfBoxCanvasOperations::preservationUnsupported,
                        resources);
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
            this.resources.checkpoint();
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
            COSBase fontValue = PdfBoxPageContentSupport.dereference(
                    rawFont, this.resources);
            if (!(fontValue instanceof COSDictionary)
                    || fontValue instanceof COSStream) {
                throw invalidResource();
            }
            COSDictionary font = (COSDictionary) fontValue;
            int codeLength = validateFontDictionary(font, this.resources);
            for (byte[] glyph : entry.getValue()) {
                this.resources.checkpoint();
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

    static int validateFontDictionary(
            COSDictionary font,
            WorkflowResourceContext resources)
            throws DocumentFailure {
        if (!COSName.FONT.equals(PdfBoxPageContentSupport.dereference(
                font.getItem(COSName.TYPE), resources))) {
            throw invalidResource();
        }
        COSBase subtype = PdfBoxPageContentSupport.dereference(
                font.getItem(COSName.SUBTYPE), resources);
        if (!(subtype instanceof COSName)
                || !(PdfBoxPageContentSupport.dereference(
                        font.getItem(COSName.BASE_FONT), resources)
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
                font.getItem(COSName.ENCODING), resources);
        if (!IDENTITY_H.equals(encoding) && !IDENTITY_V.equals(encoding)) {
            throw invalidResource();
        }
        COSBase descendants = PdfBoxPageContentSupport.dereference(
                font.getItem(DESCENDANT_FONTS), resources);
        if (!(descendants instanceof COSArray)
                || ((COSArray) descendants).size() != 1) {
            throw invalidResource();
        }
        COSBase descendant = PdfBoxPageContentSupport.dereference(
                ((COSArray) descendants).get(0), resources);
        if (!(descendant instanceof COSDictionary)
                || descendant instanceof COSStream) {
            throw invalidResource();
        }
        COSBase descendantSubtype = PdfBoxPageContentSupport.dereference(
                ((COSDictionary) descendant).getItem(COSName.SUBTYPE),
                resources);
        if (!CID_FONT_TYPE_0.equals(descendantSubtype)
                && !CID_FONT_TYPE_2.equals(descendantSubtype)) {
            throw invalidResource();
        }
        return 2;
    }

    private COSName nameFor(
            COSDictionary fonts,
            COSDictionary target) throws DocumentFailure {
        for (Map.Entry<COSName, COSBase> entry : fonts.entrySet()) {
            resources.checkpoint();
            if (PdfBoxPageContentSupport.dereference(
                    entry.getValue(), resources)
                    == target) {
                return entry.getKey();
            }
        }
        return null;
    }

    private COSName availableFontName(COSDictionary fonts)
            throws DocumentFailure {
        for (int suffix = 1; suffix <= MAXIMUM_FONT_RESOURCES; suffix++) {
            resources.checkpoint();
            COSName name = COSName.getPDFName("FolioT17F" + suffix);
            if (!fonts.containsKey(name)) {
                return name;
            }
        }
        throw invalidResource();
    }

    private WorkflowResourceContext.OwnedBytes serialize(
            CanvasProgram program,
            Map<CanvasFont, COSName> namesByFont,
            IdentityHashMap<CanvasProgram.Instruction, byte[]> glyphCodes)
            throws DocumentFailure {
        try (WorkflowAsciiOutput output = new WorkflowAsciiOutput(
                resources,
                MAXIMUM_PROGRAM_BYTES,
                PdfBoxCanvasOperations::invalidProgram)) {
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
                    output.appendPdfName(
                            fontName,
                            PdfBoxCanvasOperations::invalidResource);
                    output.append(' ');
                    PdfBoxPageContentSupport.appendNumber(
                            output,
                            instruction.getNumbers()[0],
                            PdfBoxCanvasOperations::invalidProgram);
                    output.append(" Tf\n");
                    output.append(instruction.getRenderingMode()
                            .getOperatorValue());
                    output.append(" Tr\n");
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
                    byte[] glyphCode = glyphCodes.get(instruction);
                    if (glyphCode == null) {
                        throw invalidProgram();
                    }
                    for (byte value : glyphCode) {
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
            }
            output.append("Q\n");
            return output.finishWorking();
        }
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

    private static final class WorkerPreservation {

        private final COSDictionary page;
        private final PdfBoxPageContentSupport.ExistingContents existing;
        private final COSDictionary effectiveResources;

        private WorkerPreservation(
                COSDictionary page,
                PdfBoxPageContentSupport.ExistingContents existing,
                COSDictionary effectiveResources) {
            this.page = page;
            this.existing = existing;
            this.effectiveResources = effectiveResources;
        }
    }

    private static final class ValidatedProgram implements AutoCloseable {

        private final Map<CanvasFont, List<byte[]>> glyphsByFont;
        private final IdentityHashMap<CanvasProgram.Instruction, byte[]>
                glyphCodes;
        private final List<WorkflowResourceContext.MemoryReservation>
                memoryReservations;

        ValidatedProgram(
                Map<CanvasFont, List<byte[]>> glyphsByFont,
                IdentityHashMap<CanvasProgram.Instruction, byte[]> glyphCodes,
                List<WorkflowResourceContext.MemoryReservation>
                        memoryReservations) {
            this.glyphsByFont = glyphsByFont;
            this.glyphCodes = glyphCodes;
            this.memoryReservations = memoryReservations;
        }

        @Override
        public void close() {
            releaseMemoryReservations(memoryReservations);
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
        private final IdentityHashMap<CanvasProgram.Instruction, byte[]>
                glyphCodes =
                        new IdentityHashMap<CanvasProgram.Instruction, byte[]>();
        private final List<WorkflowResourceContext.MemoryReservation>
                memoryReservations =
                        new ArrayList<
                                WorkflowResourceContext.MemoryReservation>();

        void reserveMemory(
                WorkflowResourceContext resources,
                int bytes) throws DocumentFailure {
            memoryReservations.add(resources.reserveOwnedMemory(bytes));
        }

        void releaseMemory() {
            releaseMemoryReservations(memoryReservations);
        }
    }

    private static void releaseMemoryReservations(
            List<WorkflowResourceContext.MemoryReservation> reservations) {
        for (int index = reservations.size() - 1; index >= 0; index--) {
            reservations.get(index).close();
        }
        reservations.clear();
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
