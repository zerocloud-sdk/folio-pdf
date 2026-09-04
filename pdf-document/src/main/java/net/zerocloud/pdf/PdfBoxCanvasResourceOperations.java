package net.zerocloud.pdf;

import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageInputStreamImpl;
import net.zerocloud.pdf.composition.CanvasColor;
import net.zerocloud.pdf.composition.CanvasColorSpace;
import net.zerocloud.pdf.composition.CanvasFont;
import net.zerocloud.pdf.composition.CanvasImage;
import net.zerocloud.pdf.composition.CanvasImageCapabilities;
import net.zerocloud.pdf.composition.CanvasMask;
import net.zerocloud.pdf.composition.CanvasMatrix;
import net.zerocloud.pdf.composition.CanvasProgram;
import net.zerocloud.pdf.composition.CanvasRectangle;
import net.zerocloud.pdf.composition.CanvasResourceLimits;
import net.zerocloud.pdf.composition.CanvasTransparencyGroup;
import net.zerocloud.pdf.composition.CanvasTransparencyState;
import net.zerocloud.pdf.composition.CanvasWindingRule;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNull;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;

/** Private bounded image, color, and transparency implementation for Canvas v2. */
final class PdfBoxCanvasResourceOperations {

    static final String CAPABILITY_ID =
            "composition.canvas.images-colors-transparency";

    private static final int MAXIMUM_RESOURCE_NAMES = 4096;
    private static final double MAXIMUM_ABSOLUTE_NUMBER = 1000000000d;
    private static final COSName COLOR_SPACE = COSName.getPDFName("ColorSpace");
    private static final COSName EXT_G_STATE = COSName.getPDFName("ExtGState");
    private static final COSName XOBJECT = COSName.getPDFName("XObject");
    private static final COSName IMAGE_MASK = COSName.getPDFName("ImageMask");
    private static final COSName S_MASK = COSName.getPDFName("SMask");
    private static final COSName MASK = COSName.getPDFName("Mask");
    private static final COSName GROUP = COSName.getPDFName("Group");
    private static final COSName TRANSPARENCY =
            COSName.getPDFName("Transparency");
    private static final COSName I = COSName.getPDFName("I");
    private static final COSName K = COSName.getPDFName("K");
    private static final COSName CA = COSName.getPDFName("CA");
    private static final COSName CA_NONSTROKING = COSName.getPDFName("ca");
    private static final COSName BM = COSName.getPDFName("BM");
    private static final COSName N = COSName.getPDFName("N");
    private static final COSName ALTERNATE = COSName.getPDFName("Alternate");
    private static final COSName WHITE_POINT = COSName.getPDFName("WhitePoint");
    private static final COSName BLACK_POINT = COSName.getPDFName("BlackPoint");
    private static final COSName GAMMA = COSName.getPDFName("Gamma");
    private static final COSName MATRIX = COSName.getPDFName("Matrix");
    private static final COSName FORM_TYPE = COSName.getPDFName("FormType");
    private static final COSName COLOR_TRANSFORM =
            COSName.getPDFName("ColorTransform");

    private final PDDocument document;
    private final PdfBoxValueAdapter valueAdapter;
    private final WorkflowResourceContext workflowResources;
    private final Map<CanvasImage, COSObject> imageCache =
            new IdentityHashMap<CanvasImage, COSObject>();
    private final Map<CanvasMask, COSObject> maskCache =
            new IdentityHashMap<CanvasMask, COSObject>();
    private final Map<CanvasColorSpace, COSBase> colorSpaceCache =
            new IdentityHashMap<CanvasColorSpace, COSBase>();
    private final Map<CanvasTransparencyState, COSObject> transparencyCache =
            new HashMap<CanvasTransparencyState, COSObject>();
    private final IdentityHashMap<CanvasTransparencyGroup, COSObject> groupCache =
            new IdentityHashMap<CanvasTransparencyGroup, COSObject>();

    PdfBoxCanvasResourceOperations(
            PDDocument document,
            PdfBoxValueAdapter valueAdapter,
            WorkflowResourceContext workflowResources) {
        this.document = document;
        this.valueAdapter = valueAdapter;
        this.workflowResources = workflowResources;
    }

    static CanvasImageCapabilities capabilities() {
        return CanvasImageCapabilities.version1(
                tiffAvailable()
                        ? CanvasImageCapabilities.Availability.AVAILABLE
                        : CanvasImageCapabilities.Availability
                                .OPTIONAL_CODEC_UNAVAILABLE);
    }

    Plan prepare(
            COSDictionary effectiveResources,
            CanvasProgram program,
            CanvasResourceLimits limits,
            boolean preserveExistingContent) throws DocumentFailure {
        workflowResources.checkpoint();
        Validation validation = new Validation(limits);
        RequestPlan requestPlan = new RequestPlan();
        ProgramPreflight preflight = null;
        try {
            validation.visitProgram(program, 0);
            preflight = preflightProgram(
                    program,
                    effectiveResources,
                    validation,
                    requestPlan);
            if (preserveExistingContent) {
                validation.accountGenerated(6L);
            }
            ProgramPlan programPlan = materializeProgram(
                    effectiveResources,
                    validation,
                    requestPlan,
                    preflight);
            return new Plan(
                    programPlan.resources,
                    programPlan.changed,
                    programPlan.operatorBytes);
        } finally {
            if (preflight != null) {
                preflight.close();
            }
            requestPlan.close();
            validation.releaseMemory();
        }
    }

    private ProgramPreflight preflightProgram(
            CanvasProgram program,
            COSDictionary baseResources,
            Validation validation,
            RequestPlan requestPlan) throws DocumentFailure {
        COSDictionary resources = new COSDictionary();
        resources.setDirect(true);
        if (baseResources != null) {
            for (Map.Entry<COSName, COSBase> entry
                    : baseResources.entrySet()) {
                workflowResources.checkpoint();
                resources.setItem(entry.getKey(), entry.getValue());
            }
        }

        ProgramBindings bindings = new ProgramBindings();
        Category fonts = null;
        Category colors = null;
        Category states = null;
        Category xobjects = null;

        for (CanvasProgram.Instruction instruction : program.getInstructions()) {
            workflowResources.checkpoint();
            switch (instruction.getKind()) {
                case BEGIN_TEXT:
                    if (bindings.fontNames.containsKey(instruction.getFont())) {
                        break;
                    }
                    if (fonts == null) {
                        fonts = category(resources, COSName.FONT);
                    }
                    COSBase font = validation.fonts.get(instruction.getFont()).raw;
                    COSName fontName = plannedName(
                            fonts,
                            font,
                            "FolioT18F");
                    bindings.fontNames.put(instruction.getFont(), fontName);
                    break;
                case SET_FILL_COLOR:
                case SET_STROKE_COLOR:
                    CanvasColorSpace colorSpace = instruction.getColor()
                            .getColorSpace();
                    colorSpace = validation.canonicalColorSpace(colorSpace);
                    if (!device(colorSpace)
                            && !bindings.colorNames.containsKey(colorSpace)) {
                        if (colors == null) {
                            colors = category(resources, COLOR_SPACE);
                        }
                        COSBase cached = colorSpaceCache.get(colorSpace);
                        COSName colorName = plannedName(
                                colors,
                                cached == null ? new COSDictionary() : cached,
                                "FolioT18CS");
                        bindings.colorNames.put(colorSpace, colorName);
                        bindings.colorOrder.add(colorSpace);
                    }
                    break;
                case SET_TRANSPARENCY:
                    CanvasTransparencyState state =
                            instruction.getTransparencyState();
                    if (bindings.transparencyNames.containsKey(state)) {
                        break;
                    }
                    if (states == null) {
                        states = category(resources, EXT_G_STATE);
                    }
                    COSObject cachedState = transparencyCache.get(state);
                    COSName stateName = plannedName(
                            states,
                            cachedState == null
                                    ? new COSDictionary() : cachedState,
                            "FolioT18GS");
                    bindings.transparencyNames.put(state, stateName);
                    break;
                case DRAW_IMAGE:
                    CanvasImage image = instruction.getImage();
                    image = validation.canonicalImage(image);
                    if (bindings.imageNames.containsKey(image)) {
                        break;
                    }
                    if (xobjects == null) {
                        xobjects = category(resources, XOBJECT);
                    }
                    PreparedImage prepared = validation.images.get(image);
                    COSObject cachedImage = prepared.existing == null
                            ? imageCache.get(image) : prepared.existing;
                    COSName imageName = plannedName(
                            xobjects,
                            cachedImage == null
                                    ? new COSDictionary() : cachedImage,
                            "FolioT18I");
                    bindings.imageNames.put(image, imageName);
                    bindings.imageOrder.add(image);
                    break;
                case DRAW_TRANSPARENCY_GROUP:
                    CanvasTransparencyGroup group =
                            instruction.getTransparencyGroup();
                    if (bindings.groupNames.containsKey(group)) {
                        break;
                    }
                    if (xobjects == null) {
                        xobjects = category(resources, XOBJECT);
                    }
                    COSObject cachedGroup = groupCache.get(group);
                    if (cachedGroup == null
                            && !requestPlan.groupPrograms.containsKey(group)) {
                        requestPlan.groupPrograms.put(group, null);
                        requestPlan.groupPrograms.put(
                                group,
                                preflightProgram(
                                        group.getProgram(),
                                        null,
                                        validation,
                                        requestPlan));
                    }
                    COSName groupName = plannedName(
                            xobjects,
                            cachedGroup == null
                                    ? new COSDictionary() : cachedGroup,
                            "FolioT18G");
                    bindings.groupNames.put(group, groupName);
                    bindings.groupOrder.add(group);
                    break;
                default:
                    break;
            }
        }

        WorkflowResourceContext.OwnedBytes operatorBytes = serialize(
                program,
                bindings,
                validation.remainingGeneratedBytes(),
                validation);
        try {
            byte[] operators = operatorBytes.getBytes();
            validation.accountGenerated(operators.length);
            try {
                PdfBoxContentStreamPreflight.validate(
                        operators, workflowResources);
            } catch (IOException malformedGeneratedContent) {
                workflowResources.rethrowResourceOrTerminalFailure(
                        malformedGeneratedContent);
                throw invalidGraphics();
            }
            ProgramPreflight result = new ProgramPreflight(
                    bindings, operatorBytes);
            operatorBytes = null;
            return result;
        } finally {
            if (operatorBytes != null) {
                operatorBytes.close();
            }
        }
    }

    private ProgramPlan materializeProgram(
            COSDictionary baseResources,
            Validation validation,
            RequestPlan requestPlan,
            ProgramPreflight preflight) throws DocumentFailure {
        COSDictionary resources = new COSDictionary();
        resources.setDirect(true);
        if (baseResources != null) {
            for (Map.Entry<COSName, COSBase> entry
                    : baseResources.entrySet()) {
                workflowResources.checkpoint();
                resources.setItem(entry.getKey(), entry.getValue());
            }
        }
        boolean changed = false;

        if (!preflight.bindings.fontNames.isEmpty()) {
            Category fonts = category(resources, COSName.FONT);
            for (Map.Entry<CanvasFont, COSName> entry
                    : preflight.bindings.fontNames.entrySet()) {
                workflowResources.checkpoint();
                install(fonts, entry.getValue(),
                        validation.fonts.get(entry.getKey()).raw);
            }
            if (fonts.changed) {
                resources.setItem(COSName.FONT, fonts.values);
                changed = true;
            }
        }
        if (!preflight.bindings.colorNames.isEmpty()) {
            Category colors = category(resources, COLOR_SPACE);
            for (CanvasColorSpace colorSpace
                    : preflight.bindings.colorOrder) {
                workflowResources.checkpoint();
                install(colors,
                        preflight.bindings.colorNames.get(colorSpace),
                        materializeColorSpace(
                                colorSpace,
                                validation.colorInfo(colorSpace)));
            }
            if (colors.changed) {
                resources.setItem(COLOR_SPACE, colors.values);
                changed = true;
            }
        }
        if (!preflight.bindings.transparencyNames.isEmpty()) {
            Category states = category(resources, EXT_G_STATE);
            for (Map.Entry<CanvasTransparencyState, COSName> entry
                    : preflight.bindings.transparencyNames.entrySet()) {
                workflowResources.checkpoint();
                install(states, entry.getValue(),
                        materializeTransparency(entry.getKey()));
            }
            if (states.changed) {
                resources.setItem(EXT_G_STATE, states.values);
                changed = true;
            }
        }
        if (!preflight.bindings.imageNames.isEmpty()
                || !preflight.bindings.groupNames.isEmpty()) {
            Category xobjects = category(resources, XOBJECT);
            for (CanvasImage image : preflight.bindings.imageOrder) {
                workflowResources.checkpoint();
                install(xobjects,
                        preflight.bindings.imageNames.get(image),
                        materializeImage(
                        image,
                        validation.images.get(image),
                        validation));
            }
            for (CanvasTransparencyGroup group
                    : preflight.bindings.groupOrder) {
                workflowResources.checkpoint();
                install(xobjects, preflight.bindings.groupNames.get(group),
                        materializeGroup(
                                group,
                                validation,
                                requestPlan));
            }
            if (xobjects.changed) {
                resources.setItem(XOBJECT, xobjects.values);
                changed = true;
            }
        }
        return new ProgramPlan(
                resources, changed, preflight.takeOperators());
    }

    private COSObject materializeGroup(
            CanvasTransparencyGroup group,
            Validation validation,
            RequestPlan requestPlan) throws DocumentFailure {
        COSObject existing = groupCache.get(group);
        if (existing != null) {
            return existing;
        }
        ProgramPreflight preflight = requestPlan.groupPrograms.get(group);
        if (preflight == null) {
            throw invalidGraphics();
        }
        ProgramPlan content = materializeProgram(
                null,
                validation,
                requestPlan,
                preflight);
        COSStream stream;
        try {
            stream = newStream(content.operators(), true);
        } finally {
            content.close();
        }
        stream.setItem(COSName.TYPE, COSName.XOBJECT);
        stream.setItem(COSName.SUBTYPE, COSName.FORM);
        stream.setItem(FORM_TYPE, COSInteger.ONE);
        stream.setItem(COSName.BBOX, rectangle(group.getBox()));
        stream.setItem(COSName.RESOURCES, content.resources);

        COSDictionary attributes = new COSDictionary();
        attributes.setDirect(true);
        attributes.setItem(COSName.S, TRANSPARENCY);
        attributes.setItem(
                COSName.CS,
                materializeColorSpace(
                        validation.canonicalColorSpace(group.getColorSpace()),
                        validation.colorInfo(group.getColorSpace())));
        attributes.setBoolean(I, group.isIsolated());
        attributes.setBoolean(K, group.isKnockout());
        stream.setItem(GROUP, attributes);

        COSObject result = new COSObject(stream);
        groupCache.put(group, result);
        return result;
    }

    private COSObject materializeImage(
            CanvasImage declaration,
            PreparedImage image,
            Validation validation) throws DocumentFailure {
        declaration = validation.canonicalImage(declaration);
        if (image.existing != null) {
            return image.existing;
        }
        COSObject cached = imageCache.get(declaration);
        if (cached != null) {
            return cached;
        }

        COSStream stream = image.jpeg
                ? newStream(image.samples, false)
                : newStream(image.samples, true);
        if (image.jpeg) {
            stream.setItem(COSName.FILTER, COSName.DCT_DECODE);
            if (image.jpegColorTransform >= 0) {
                COSDictionary decodeParameters = new COSDictionary();
                decodeParameters.setDirect(true);
                decodeParameters.setInt(
                        COLOR_TRANSFORM,
                        image.jpegColorTransform);
                stream.setItem(COSName.DECODE_PARMS, decodeParameters);
            }
        }
        stream.setItem(COSName.TYPE, COSName.XOBJECT);
        stream.setItem(COSName.SUBTYPE, COSName.IMAGE);
        stream.setInt(COSName.WIDTH, image.width);
        stream.setInt(COSName.HEIGHT, image.height);
        workflowResources.recordConsumedImagePixels(
                stream,
                (long) image.width * (long) image.height);
        stream.setInt(COSName.BITS_PER_COMPONENT, image.bits);
        stream.setItem(
                COSName.COLORSPACE,
                materializeColorSpace(
                        validation.canonicalColorSpace(image.colorSpace),
                        validation.colorInfo(image.colorSpace)));
        if (image.jpeg && image.components == 4) {
            COSArray decode = new COSArray();
            decode.setDirect(true);
            for (int index = 0; index < 4; index++) {
                decode.add(COSInteger.ONE);
                decode.add(COSInteger.ZERO);
            }
            stream.setItem(COSName.DECODE, decode);
        }
        if (image.explicitMask != null) {
            stream.setItem(
                    MASK,
                    materializeMask(image.explicitMask, validation));
        }
        if (image.softMask != null) {
            stream.setItem(
                    S_MASK,
                    materializeMask(image.softMask, validation));
        }
        COSObject result = new COSObject(stream);
        imageCache.put(declaration, result);
        return result;
    }

    private COSObject materializeMask(
            CanvasMask mask,
            Validation validation) throws DocumentFailure {
        mask = validation.canonicalMask(mask);
        COSObject cached = maskCache.get(mask);
        if (cached != null) {
            return cached;
        }
        byte[] samples = validation.maskSamples.get(mask);
        if (samples == null) {
            throw invalidImage();
        }
        COSStream stream = newStream(samples, true);
        stream.setItem(COSName.TYPE, COSName.XOBJECT);
        stream.setItem(COSName.SUBTYPE, COSName.IMAGE);
        stream.setInt(COSName.WIDTH, mask.getWidth());
        stream.setInt(COSName.HEIGHT, mask.getHeight());
        if (mask.getKind() == CanvasMask.Kind.EXPLICIT_IMAGE) {
            stream.setBoolean(IMAGE_MASK, true);
            stream.setInt(COSName.BITS_PER_COMPONENT, 1);
            if (mask.isInverted()) {
                COSArray decode = new COSArray();
                decode.setDirect(true);
                decode.add(COSInteger.ONE);
                decode.add(COSInteger.ZERO);
                stream.setItem(COSName.DECODE, decode);
            }
        } else {
            stream.setInt(COSName.BITS_PER_COMPONENT, 8);
            stream.setItem(COSName.COLORSPACE, COSName.DEVICEGRAY);
        }
        COSObject result = new COSObject(stream);
        maskCache.put(mask, result);
        return result;
    }

    private COSObject materializeTransparency(CanvasTransparencyState state) {
        COSObject cached = transparencyCache.get(state);
        if (cached != null) {
            return cached;
        }
        COSDictionary dictionary = new COSDictionary();
        dictionary.setItem(COSName.TYPE, EXT_G_STATE);
        dictionary.setItem(
                CA_NONSTROKING,
                new COSFloat((float) state.getFillAlpha()));
        dictionary.setItem(CA, new COSFloat((float) state.getStrokeAlpha()));
        dictionary.setItem(BM, COSName.getPDFName(state.getBlendMode().getPdfName()));
        COSObject result = new COSObject(dictionary);
        transparencyCache.put(state, result);
        return result;
    }

    private COSBase materializeColorSpace(
            CanvasColorSpace declaration,
            ColorInfo info) throws DocumentFailure {
        if (device(declaration)) {
            return deviceName(declaration.getFamily());
        }
        COSBase cached = colorSpaceCache.get(declaration);
        if (cached != null) {
            return cached;
        }
        COSArray result = new COSArray();
        result.setDirect(true);
        if (declaration.getFamily() == CanvasColorSpace.Family.CAL_GRAY
                || declaration.getFamily() == CanvasColorSpace.Family.CAL_RGB) {
            result.add(declaration.getFamily() == CanvasColorSpace.Family.CAL_GRAY
                    ? COSName.getPDFName("CalGray")
                    : COSName.getPDFName("CalRGB"));
            COSDictionary parameters = new COSDictionary();
            parameters.setDirect(true);
            parameters.setItem(WHITE_POINT, numbers(declaration.getWhitePoint()));
            parameters.setItem(BLACK_POINT, numbers(declaration.getBlackPoint()));
            double[] gamma = declaration.getGamma();
            if (gamma.length == 1) {
                parameters.setItem(GAMMA, numberObject(gamma[0]));
            } else {
                parameters.setItem(GAMMA, numbers(gamma));
                parameters.setItem(MATRIX, numbers(declaration.getMatrix()));
            }
            result.add(parameters);
        } else {
            COSStream profile = newStream(info.profile, true);
            profile.setInt(N, info.components);
            profile.setItem(ALTERNATE, deviceNameForComponents(info.components));
            COSObject profileObject = new COSObject(profile);
            result.add(COSName.getPDFName("ICCBased"));
            result.add(profileObject);
        }
        colorSpaceCache.put(declaration, result);
        return result;
    }

    private COSStream newStream(byte[] bytes, boolean flate)
            throws DocumentFailure {
        try {
            COSStream stream = document.getDocument().createCOSStream();
            try (OutputStream output = flate
                    ? stream.createOutputStream(COSName.FLATE_DECODE)
                    : stream.createRawOutputStream()) {
                workflowResources.writeBytesAsIOException(output, bytes);
            }
            return stream;
        } catch (IOException | RuntimeException failure) {
            workflowResources.rethrowResourceOrTerminalFailure(failure);
            throw writeFailure();
        }
    }

    private WorkflowResourceContext.OwnedBytes serialize(
            CanvasProgram program,
            ProgramBindings bindings,
            long maximumBytes,
            Validation validation) throws DocumentFailure {
        try (WorkflowAsciiOutput output = new WorkflowAsciiOutput(
                workflowResources,
                maximumBytes,
                PdfBoxCanvasResourceOperations::limitFailure)) {
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
                            == CanvasWindingRule.EVEN_ODD ? "W* n\n" : "W n\n");
                    break;
                case BEGIN_TEXT:
                    output.append("BT\n");
                    appendName(output, bindings.fontNames.get(instruction.getFont()));
                    output.append(' ');
                    appendNumber(output, instruction.getNumbers()[0]);
                    output.append(" Tf\n");
                    output.append(instruction.getRenderingMode()
                            .getOperatorValue());
                    output.append(" Tr\n");
                    appendMatrix(output, instruction.getMatrix());
                    output.append(" Tm\n");
                    break;
                case SET_TEXT_MATRIX:
                    appendMatrix(output, instruction.getMatrix());
                    output.append(" Tm\n");
                    break;
                case SHOW_GLYPH:
                    output.append('<');
                    for (byte value : validation.glyphCode(instruction)) {
                        int unsigned = value & 0xff;
                        output.append(Character.forDigit((unsigned >>> 4) & 0xf, 16));
                        output.append(Character.forDigit(unsigned & 0xf, 16));
                    }
                    output.append("> Tj\n");
                    break;
                case END_TEXT:
                    output.append("ET\n");
                    break;
                case SET_FILL_COLOR:
                    appendColor(
                            output,
                            instruction.getColor(),
                            false,
                            bindings,
                            validation);
                    break;
                case SET_STROKE_COLOR:
                    appendColor(
                            output,
                            instruction.getColor(),
                            true,
                            bindings,
                            validation);
                    break;
                case SET_TRANSPARENCY:
                    appendName(output, bindings.transparencyNames.get(
                            instruction.getTransparencyState()));
                    output.append(" gs\n");
                    break;
                case DRAW_IMAGE:
                    appendPlacedXObject(
                            output,
                            instruction.getMatrix(),
                            bindings.imageNames.get(
                                    validation.canonicalImage(
                                            instruction.getImage())));
                    break;
                case DRAW_TRANSPARENCY_GROUP:
                    appendPlacedXObject(
                            output,
                            instruction.getMatrix(),
                            bindings.groupNames.get(
                                    instruction.getTransparencyGroup()));
                    break;
                default:
                    throw invalidGraphics();
                }
            }
            output.append("Q\n");
            return output.finishWorking();
        }
    }

    private static void appendPlacedXObject(
            WorkflowAsciiOutput output,
            CanvasMatrix matrix,
            COSName name) throws DocumentFailure {
        output.append("q\n");
        appendMatrix(output, matrix);
        output.append(" cm\n");
        appendName(output, name);
        output.append(" Do\nQ\n");
    }

    private static void appendColor(
            WorkflowAsciiOutput output,
            CanvasColor color,
            boolean stroking,
            ProgramBindings bindings,
            Validation validation) throws DocumentFailure {
        CanvasColorSpace space = validation.canonicalColorSpace(
                color.getColorSpace());
        if (device(space)) {
            appendNumbers(output, color.getComponents());
            switch (space.getFamily()) {
                case DEVICE_GRAY:
                    output.append(stroking ? " G\n" : " g\n");
                    return;
                case DEVICE_RGB:
                    output.append(stroking ? " RG\n" : " rg\n");
                    return;
                case DEVICE_CMYK:
                    output.append(stroking ? " K\n" : " k\n");
                    return;
                default:
                    throw invalidGraphics();
            }
        }
        appendName(output, bindings.colorNames.get(space));
        output.append(stroking ? " CS\n" : " cs\n");
        appendNumbers(output, color.getComponents());
        output.append(stroking ? " SCN\n" : " scn\n");
    }

    private static void appendName(WorkflowAsciiOutput output, COSName name)
            throws DocumentFailure {
        if (name == null) {
            throw invalidGraphics();
        }
        output.appendPdfName(
                name,
                PdfBoxCanvasResourceOperations::invalidGraphics);
    }

    private static void appendMatrix(
            WorkflowAsciiOutput output,
            CanvasMatrix matrix)
            throws DocumentFailure {
        appendNumbers(output, new double[] {
            matrix.getA(), matrix.getB(), matrix.getC(),
            matrix.getD(), matrix.getE(), matrix.getF()
        });
    }

    private static void appendNumbers(
            WorkflowAsciiOutput output,
            double[] values)
            throws DocumentFailure {
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                output.append(' ');
            }
            appendNumber(output, values[index]);
        }
    }

    private static void appendNumber(
            WorkflowAsciiOutput output,
            double value) throws DocumentFailure {
        requireFinite(value);
        if (value == 0d) {
            output.append('0');
            return;
        }
        output.append(BigDecimal.valueOf(value).stripTrailingZeros());
    }

    private Category category(COSDictionary resources, COSName name)
            throws DocumentFailure {
        COSBase raw = resources.getItem(name);
        COSBase value = dereference(raw);
        if (value == null || value instanceof COSNull) {
            COSDictionary result = new COSDictionary();
            result.setDirect(true);
            return new Category(result);
        }
        if (!(value instanceof COSDictionary) || value instanceof COSStream) {
            throw preservationFailure();
        }
        COSDictionary result = new COSDictionary();
        result.setDirect(true);
        for (Map.Entry<COSName, COSBase> entry
                : ((COSDictionary) value).entrySet()) {
            workflowResources.checkpoint();
            result.setItem(entry.getKey(), entry.getValue());
        }
        return new Category(result);
    }

    private COSName nameFor(COSDictionary values, COSBase target)
            throws DocumentFailure {
        for (Map.Entry<COSName, COSBase> entry : values.entrySet()) {
            workflowResources.checkpoint();
            if (dereference(entry.getValue()) == target) {
                return entry.getKey();
            }
        }
        return null;
    }

    private COSName plannedName(
            Category category,
            COSBase target,
            String prefix) throws DocumentFailure {
        COSName name = nameFor(category.values, dereference(target));
        if (name == null) {
            name = availableName(category.values, prefix);
            category.values.setItem(name, target);
        }
        return name;
    }

    private void install(
            Category category,
            COSName name,
            COSBase value) throws DocumentFailure {
        COSBase existing = category.values.getItem(name);
        if (existing == null) {
            category.values.setItem(name, value);
            category.changed = true;
            return;
        }
        if (dereference(existing) != dereference(value)) {
            throw preservationFailure();
        }
    }

    private COSName availableName(
            COSDictionary values,
            String prefix) throws DocumentFailure {
        for (int suffix = 1; suffix <= MAXIMUM_RESOURCE_NAMES; suffix++) {
            workflowResources.checkpoint();
            COSName name = COSName.getPDFName(prefix + suffix);
            if (!values.containsKey(name)) {
                return name;
            }
        }
        throw unsupportedResource();
    }

    private static COSArray rectangle(CanvasRectangle box) {
        return numbers(new double[] {
            box.getLowerLeftX(), box.getLowerLeftY(),
            box.getUpperRightX(), box.getUpperRightY()
        });
    }

    private static COSArray numbers(double[] values) {
        COSArray result = new COSArray();
        result.setDirect(true);
        for (double value : values) {
            result.add(numberObject(value));
        }
        return result;
    }

    private static COSNumber numberObject(double value) {
        if (value == Math.rint(value)
                && value >= Integer.MIN_VALUE
                && value <= Integer.MAX_VALUE) {
            return COSInteger.get((long) value);
        }
        return new COSFloat((float) value);
    }

    private static COSName deviceName(CanvasColorSpace.Family family) {
        switch (family) {
            case DEVICE_GRAY:
                return COSName.DEVICEGRAY;
            case DEVICE_RGB:
                return COSName.DEVICERGB;
            case DEVICE_CMYK:
                return COSName.DEVICECMYK;
            default:
                throw new IllegalArgumentException("Not a device color space");
        }
    }

    private static COSName deviceNameForComponents(int components) {
        return components == 1
                ? COSName.DEVICEGRAY
                : components == 3 ? COSName.DEVICERGB : COSName.DEVICECMYK;
    }

    private static boolean device(CanvasColorSpace colorSpace) {
        CanvasColorSpace.Family family = colorSpace.getFamily();
        return family == CanvasColorSpace.Family.DEVICE_GRAY
                || family == CanvasColorSpace.Family.DEVICE_RGB
                || family == CanvasColorSpace.Family.DEVICE_CMYK;
    }

    private COSBase dereference(COSBase value) throws DocumentFailure {
        COSBase current = value;
        IdentityHashMap<COSBase, Boolean> visited =
                new IdentityHashMap<COSBase, Boolean>();
        while (current instanceof COSObject
                && visited.put(current, Boolean.TRUE) == null) {
            workflowResources.checkpoint();
            current = ((COSObject) current).getObject();
        }
        return current;
    }

    private static boolean tiffAvailable() {
        Iterator<ImageReader> readers;
        try {
            readers = ImageIO.getImageReadersByFormatName("TIFF");
            if (!readers.hasNext()) {
                return false;
            }
        } catch (RuntimeException providerFailure) {
            return false;
        }
        ImageReader reader = null;
        try {
            reader = readers.next();
            return reader != null;
        } catch (RuntimeException providerFailure) {
            return false;
        } finally {
            if (reader != null) {
                try {
                    reader.dispose();
                } catch (RuntimeException ignored) {
                    // Availability must remain a safe project-owned result.
                }
            }
        }
    }

    private final class Validation {

        private final CanvasResourceLimits limits;
        private final Map<CanvasImage, PreparedImage> images =
                new IdentityHashMap<CanvasImage, PreparedImage>();
        private final IdentityHashMap<CanvasImage, CanvasImage>
                canonicalImagesByDeclaration =
                        new IdentityHashMap<CanvasImage, CanvasImage>();
        private final List<CanvasImage> canonicalImages =
                new ArrayList<CanvasImage>();
        private final Map<CanvasColorSpace, ColorInfo> colors =
                new IdentityHashMap<CanvasColorSpace, ColorInfo>();
        private final IdentityHashMap<CanvasColorSpace, CanvasColorSpace>
                canonicalColorsByDeclaration =
                        new IdentityHashMap<
                                CanvasColorSpace, CanvasColorSpace>();
        private final List<CanvasColorSpace> canonicalColors =
                new ArrayList<CanvasColorSpace>();
        private final Map<CanvasFont, FontInfo> fonts =
                new LinkedHashMap<CanvasFont, FontInfo>();
        private final IdentityHashMap<CanvasProgram.Instruction, byte[]>
                glyphCodes =
                        new IdentityHashMap<CanvasProgram.Instruction, byte[]>();
        private final Map<CanvasMask, byte[]> maskSamples =
                new IdentityHashMap<CanvasMask, byte[]>();
        private final IdentityHashMap<CanvasMask, CanvasMask>
                canonicalMasksByDeclaration =
                        new IdentityHashMap<CanvasMask, CanvasMask>();
        private final List<CanvasMask> canonicalMasks =
                new ArrayList<CanvasMask>();
        private final Set<CanvasTransparencyState> states =
                Collections.newSetFromMap(
                        new HashMap<CanvasTransparencyState, Boolean>());
        private final IdentityHashMap<CanvasTransparencyGroup, Integer> groups =
                new IdentityHashMap<CanvasTransparencyGroup, Integer>();
        private final List<WorkflowResourceContext.MemoryReservation>
                memoryReservations =
                        new ArrayList<
                                WorkflowResourceContext.MemoryReservation>();
        private long encodedBytes;
        private long decodedPixels;
        private long decodedBytes;
        private long profileBytes;
        private long maskBytes;
        private long generatedBytes;
        private int resources;

        Validation(CanvasResourceLimits limits) {
            this.limits = limits;
        }

        void visitProgram(CanvasProgram program, int groupDepth)
                throws DocumentFailure {
            if (program == null || program.getVersion() != CanvasProgram.VERSION_2) {
                throw invalidGraphics();
            }
            CanvasFont activeFont = null;
            for (CanvasProgram.Instruction instruction : program.getInstructions()) {
                workflowResources.checkpoint();
                switch (instruction.getKind()) {
                    case BEGIN_TEXT:
                        activeFont = instruction.getFont();
                        if (!fonts.containsKey(activeFont)) {
                            fonts.put(activeFont, validateFont(activeFont));
                            accountResource();
                        }
                        break;
                    case SHOW_GLYPH:
                        FontInfo font = fonts.get(activeFont);
                        int glyphLength = instruction.getGlyphCodeLength();
                        if (font == null
                                || glyphLength != font.codeLength) {
                            throw invalidFont();
                        }
                        if (!glyphCodes.containsKey(instruction)) {
                            retainMemory(glyphLength);
                            byte[] glyphCode = instruction.getGlyphCode();
                            if (glyphCode == null
                                    || glyphCode.length != glyphLength) {
                                throw invalidFont();
                            }
                            glyphCodes.put(instruction, glyphCode);
                        }
                        break;
                    case END_TEXT:
                        activeFont = null;
                        break;
                    case SET_FILL_COLOR:
                    case SET_STROKE_COLOR:
                        validateColor(instruction.getColor());
                        break;
                    case SET_TRANSPARENCY:
                        validateTransparency(instruction.getTransparencyState());
                        break;
                    case DRAW_IMAGE:
                        prepareImage(instruction.getImage());
                        break;
                    case DRAW_TRANSPARENCY_GROUP:
                        CanvasTransparencyGroup group =
                                instruction.getTransparencyGroup();
                        int depth = groupDepth + 1;
                        workflowResources.requireNestingDepth(depth);
                        if (depth > limits.getMaximumTransparencyGroupDepth()) {
                            throw limitFailure();
                        }
                        Integer previousDepth = groups.get(group);
                        if (previousDepth == null) {
                            validateRectangle(group.getBox());
                            validateColorSpace(group.getColorSpace());
                            accountResource();
                        }
                        if (previousDepth == null
                                || depth > previousDepth.intValue()) {
                            groups.put(group, Integer.valueOf(depth));
                            visitProgram(group.getProgram(), depth);
                        }
                        break;
                    default:
                        break;
                }
            }
        }

        void accountGenerated(long count) throws DocumentFailure {
            generatedBytes = add(
                    generatedBytes,
                    count,
                    maximumGeneratedBytes());
        }

        long remainingGeneratedBytes() {
            return maximumGeneratedBytes() - generatedBytes;
        }

        private long maximumGeneratedBytes() {
            return Math.min(
                    limits.getMaximumGeneratedContentBytes(),
                    (long) PdfBoxCanvasOperations.MAXIMUM_PROGRAM_BYTES);
        }

        private void validateColor(CanvasColor color) throws DocumentFailure {
            if (color == null) {
                throw invalidGraphics();
            }
            ColorInfo info = validateColorSpace(color.getColorSpace());
            if (color.getComponentCount() != info.components) {
                throw invalidGraphics();
            }
            double[] components = color.getComponents();
            for (double component : components) {
                requireUnit(component);
            }
        }

        private ColorInfo validateColorSpace(CanvasColorSpace colorSpace)
                throws DocumentFailure {
            if (colorSpace == null) {
                throw invalidGraphics();
            }
            ColorInfo existing = colors.get(colorSpace);
            if (existing != null) {
                return existing;
            }
            CanvasColorSpace canonical = equivalentColorSpace(colorSpace);
            if (canonical != null) {
                existing = colors.get(canonical);
                if (existing == null) {
                    throw invalidGraphics();
                }
                colors.put(colorSpace, existing);
                canonicalColorsByDeclaration.put(colorSpace, canonical);
                return existing;
            }
            ColorInfo info;
            switch (colorSpace.getFamily()) {
                case DEVICE_GRAY:
                    info = new ColorInfo(1, null);
                    break;
                case DEVICE_RGB:
                    info = new ColorInfo(3, null);
                    break;
                case DEVICE_CMYK:
                    info = new ColorInfo(4, null);
                    break;
                case CAL_GRAY:
                    validateCalibrated(colorSpace, 1);
                    info = new ColorInfo(1, null);
                    accountResource();
                    break;
                case CAL_RGB:
                    validateCalibrated(colorSpace, 3);
                    info = new ColorInfo(3, null);
                    accountResource();
                    break;
                case ICC_BASED:
                    int profileLength =
                            colorSpace.getIccProfileByteLength();
                    retainMemory(profileLength);
                    byte[] profile =
                            colorSpace.getIccProfileBytes().orElse(null);
                    if (profile == null) {
                        throw invalidGraphics();
                    }
                    if (profile.length != profileLength
                            || profile.length < 128) {
                        throw invalidGraphics();
                    }
                    profileBytes = add(
                            profileBytes,
                            profile.length,
                            limits.getMaximumIccProfileBytes());
                    info = validateIcc(profile);
                    accountResource();
                    break;
                default:
                    throw unsupportedResource();
            }
            colors.put(colorSpace, info);
            canonicalColorsByDeclaration.put(colorSpace, colorSpace);
            canonicalColors.add(colorSpace);
            return info;
        }

        private void validateCalibrated(
                CanvasColorSpace colorSpace,
                int components) throws DocumentFailure {
            int expectedGamma = components == 1 ? 1 : 3;
            int expectedMatrix = components == 1 ? 0 : 9;
            if (colorSpace.getWhitePointLength() != 3
                    || colorSpace.getBlackPointLength() != 3
                    || colorSpace.getGammaLength() != expectedGamma
                    || colorSpace.getMatrixLength() != expectedMatrix) {
                throw invalidGraphics();
            }
            double[] white = colorSpace.getWhitePoint();
            double[] black = colorSpace.getBlackPoint();
            double[] gamma = colorSpace.getGamma();
            if (white[0] <= 0d
                    || white[1] != 1d
                    || white[2] <= 0d) {
                throw invalidGraphics();
            }
            for (double value : white) {
                requireFinite(value);
            }
            for (double value : black) {
                requireFinite(value);
                if (value < 0d) {
                    throw invalidGraphics();
                }
            }
            if (components == 1) {
                if (gamma[0] <= 0d) {
                    throw invalidGraphics();
                }
                requireFinite(gamma[0]);
            } else {
                for (double value : gamma) {
                    requireFinite(value);
                    if (value <= 0d) {
                        throw invalidGraphics();
                    }
                }
                double[] matrix = colorSpace.getMatrix();
                for (double value : matrix) {
                    requireFinite(value);
                }
            }
        }

        private ColorInfo validateIcc(byte[] profile) throws DocumentFailure {
            if (unsignedInt(profile, 0) != profile.length
                    || profile[36] != 'a'
                    || profile[37] != 'c'
                    || profile[38] != 's'
                    || profile[39] != 'p') {
                throw invalidGraphics();
            }
            try {
                ICC_Profile parsed = ICC_Profile.getInstance(profile);
                int components = parsed.getNumComponents();
                int type = parsed.getColorSpaceType();
                if ((components == 1
                                && type != java.awt.color.ColorSpace.TYPE_GRAY)
                        || (components == 3
                                && type != java.awt.color.ColorSpace.TYPE_RGB)
                        || (components == 4
                                && type != java.awt.color.ColorSpace.TYPE_CMYK)) {
                    throw unsupportedResource();
                }
                if (components != 1 && components != 3 && components != 4) {
                    throw unsupportedResource();
                }
                return new ColorInfo(components, profile);
            } catch (DocumentFailure failure) {
                throw failure;
            } catch (IllegalArgumentException runtimeFailure) {
                throw invalidGraphics();
            }
        }

        private void validateTransparency(CanvasTransparencyState state)
                throws DocumentFailure {
            if (state == null) {
                throw invalidGraphics();
            }
            requireUnit(state.getFillAlpha());
            requireUnit(state.getStrokeAlpha());
            if (state.getBlendMode() == null) {
                throw invalidGraphics();
            }
            if (states.add(state)) {
                accountResource();
            }
        }

        private void validateRectangle(CanvasRectangle box)
                throws DocumentFailure {
            if (box == null) {
                throw invalidGraphics();
            }
            requireFinite(box.getLowerLeftX());
            requireFinite(box.getLowerLeftY());
            requireFinite(box.getUpperRightX());
            requireFinite(box.getUpperRightY());
            if (box.getUpperRightX() <= box.getLowerLeftX()
                    || box.getUpperRightY() <= box.getLowerLeftY()) {
                throw invalidGraphics();
            }
        }

        private PreparedImage prepareImage(CanvasImage image)
                throws DocumentFailure {
            if (image == null) {
                throw invalidImage();
            }
            PreparedImage existing = images.get(image);
            if (existing != null) {
                return existing;
            }
            CanvasImage canonical = equivalentImage(image);
            if (canonical != null) {
                existing = images.get(canonical);
                if (existing == null) {
                    throw invalidImage();
                }
                images.put(image, existing);
                canonicalImagesByDeclaration.put(image, canonical);
                return existing;
            }
            accountResource();
            PreparedImage result;
            switch (image.getSourceKind()) {
                case EXISTING:
                    if (image.getExplicitMask().isPresent()
                            || image.getSoftMask().isPresent()) {
                        throw invalidImage();
                    }
                    result = existingImage(image);
                    break;
                case JPEG:
                    byte[] jpeg = requiredBytes(image);
                    accountEncoded(jpeg.length);
                    JpegInfo jpegInfo = jpegInfo(
                            jpeg, workflowResources);
                    accountDecoded(multiply(
                            multiply(jpegInfo.width, jpegInfo.height),
                            jpegInfo.components));
                    result = new PreparedImage(
                            jpegInfo.width,
                            jpegInfo.height,
                            8,
                            jpegInfo.components,
                            colorSpace(jpegInfo.components),
                            jpeg,
                            true,
                            jpegInfo.colorTransform,
                            null,
                            null,
                            null);
                    result = masks(image, result);
                    break;
                case PNG:
                    byte[] png = requiredBytes(image);
                    accountEncoded(png.length);
                    requirePng(png);
                    result = masks(image, decodeRaster(png, "PNG", false));
                    break;
                case TIFF:
                    byte[] tiff = requiredBytes(image);
                    accountEncoded(tiff.length);
                    requireTiff(tiff);
                    if (!tiffAvailable()) {
                        throw codecUnavailable();
                    }
                    result = masks(image, decodeRaster(tiff, "TIFF", true));
                    break;
                case RAW_SAMPLES:
                    ColorInfo color = validateColorSpace(
                            image.getColorSpace().orElse(null));
                    if (image.getWidth() < 1
                            || image.getHeight() < 1
                            || image.getBitsPerComponent() != 8) {
                        throw invalidImage();
                    }
                    long expected = multiply(
                            multiply(image.getWidth(), image.getHeight()),
                            color.components);
                    byte[] samples = requiredBytes(image);
                    if (expected != samples.length) {
                        throw invalidImage();
                    }
                    accountDecoded(samples.length);
                    result = masks(image, new PreparedImage(
                            image.getWidth(),
                            image.getHeight(),
                            8,
                            color.components,
                            image.getColorSpace().get(),
                            samples,
                            false,
                            -1,
                            null,
                            null,
                            null));
                    break;
                default:
                    throw unsupportedResource();
            }
            if (image.getSourceKind() != CanvasImage.SourceKind.EXISTING) {
                accountPixels(result.width, result.height);
            }
            images.put(image, result);
            canonicalImagesByDeclaration.put(image, image);
            canonicalImages.add(image);
            return result;
        }

        private PreparedImage existingImage(CanvasImage image)
                throws DocumentFailure {
            COSBase raw;
            try {
                raw = valueAdapter.referencedObject(
                        image.getObjectReference().orElse(null));
            } catch (DocumentFailure invalidReference) {
                throw invalidImage();
            }
            if (!(raw instanceof COSObject)) {
                throw invalidImage();
            }
            COSBase value = dereference(raw);
            if (!(value instanceof COSStream)) {
                throw invalidImage();
            }
            COSStream stream = (COSStream) value;
            COSBase type = dereference(stream.getItem(COSName.TYPE));
            if ((type != null && !COSName.XOBJECT.equals(type))
                    || !COSName.IMAGE.equals(
                            dereference(stream.getItem(COSName.SUBTYPE)))
                    || positiveInteger(stream.getItem(COSName.WIDTH)) < 1
                    || positiveInteger(stream.getItem(COSName.HEIGHT)) < 1
                    || dereference(stream.getItem(COSName.F)) != null
                    || !supportedExistingFilter(stream.getItem(COSName.FILTER))) {
                throw invalidImage();
            }
            return new PreparedImage(
                    0, 0, 0, 0, null, null, false, -1,
                    (COSObject) raw, null, null);
        }

        private PreparedImage masks(
                CanvasImage declaration,
                PreparedImage image) throws DocumentFailure {
            CanvasMask explicit = declaration.getExplicitMask().orElse(null);
            CanvasMask soft = declaration.getSoftMask().orElse(null);
            if (explicit != null
                    && (soft != null || image.softMask != null)) {
                throw invalidImage();
            }
            if (explicit != null) {
                validateMask(explicit, CanvasMask.Kind.EXPLICIT_IMAGE,
                        image.width, image.height);
            }
            if (soft != null) {
                validateMask(soft, CanvasMask.Kind.SOFT_IMAGE,
                        image.width, image.height);
            }
            if (soft != null && image.softMask != null) {
                throw invalidImage();
            }
            return new PreparedImage(
                    image.width,
                    image.height,
                    image.bits,
                    image.components,
                    image.colorSpace,
                    image.samples,
                    image.jpeg,
                    image.jpegColorTransform,
                    image.existing,
                    explicit,
                    soft == null ? image.softMask : soft);
        }

        private void validateMask(
                CanvasMask mask,
                CanvasMask.Kind expectedKind,
                int width,
                int height) throws DocumentFailure {
            if (mask.getKind() != expectedKind
                    || mask.getWidth() != width
                    || mask.getHeight() != height
                    || width < 1
                    || height < 1) {
                throw invalidImage();
            }
            long expected = expectedKind == CanvasMask.Kind.EXPLICIT_IMAGE
                    ? multiply((width + 7L) / 8L, height)
                    : multiply(width, height);
            int sampleLength = mask.getSampleByteLength();
            if (expected != sampleLength) {
                throw invalidImage();
            }
            CanvasMask canonical = canonicalMasksByDeclaration.get(mask);
            if (canonical == null) {
                canonical = equivalentMask(mask);
            }
            if (canonical != null) {
                if (maskSamples.get(canonical) == null) {
                    throw invalidImage();
                }
                canonicalMasksByDeclaration.put(mask, canonical);
                return;
            }
            retainMemory(sampleLength);
            byte[] samples = mask.getSamples();
            if (samples.length != sampleLength) {
                throw invalidImage();
            }
            maskSamples.put(mask, samples);
            canonicalMasksByDeclaration.put(mask, mask);
            canonicalMasks.add(mask);
            maskBytes = add(
                    maskBytes,
                    samples.length,
                    limits.getMaximumMaskBytes());
            accountResource();
        }

        private PreparedImage decodeRaster(
                byte[] bytes,
                String format,
                boolean requireSingleImage) throws DocumentFailure {
            ImageReader reader = null;
            try {
                Iterator<ImageReader> readers =
                        ImageIO.getImageReadersByFormatName(format);
                if (!readers.hasNext()) {
                    throw "TIFF".equals(format)
                            ? codecUnavailable() : invalidImage();
                }
                reader = readers.next();
                if (reader == null) {
                    throw invalidImage();
                }
                try (ImageInputStream input = new ByteArrayImageInputStream(
                        bytes, workflowResources)) {
                    reader.setInput(input, false, true);
                    if (requireSingleImage && reader.getNumImages(true) != 1) {
                        throw unsupportedResource();
                    }
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    if (width < 1 || height < 1) {
                        throw invalidImage();
                    }
                    long pixels = multiply(width, height);
                    long colorBytes = multiply(pixels, 3L);
                    accountDecoded(colorBytes);
                    if (colorBytes > Integer.MAX_VALUE) {
                        throw limitFailure();
                    }
                    long decodedMemory = addExactForMemory(
                            colorBytes,
                            multiply(pixels, 4L));
                    decodedMemory = addExactForMemory(
                            decodedMemory,
                            multiply(width, 4L));
                    retainMemory(decodedMemory);
                    BufferedImage decoded = reader.read(0);
                    if (decoded == null
                            || decoded.getWidth() != width
                            || decoded.getHeight() != height) {
                        throw invalidImage();
                    }
                    byte[] samples = new byte[(int) colorBytes];
                    byte[] alpha = null;
                    if (decoded.getColorModel().hasAlpha()) {
                        retainMemory(pixels);
                        alpha = new byte[(int) pixels];
                    }
                    int[] row = new int[width];
                    int sampleOffset = 0;
                    int alphaOffset = 0;
                    int processed = 0;
                    boolean transparent = false;
                    for (int y = 0; y < height; y++) {
                        workflowResources.checkpoint();
                        decoded.getRGB(0, y, width, 1, row, 0, width);
                        for (int pixel : row) {
                            if ((processed++ & 1023) == 0) {
                                workflowResources.checkpoint();
                            }
                            samples[sampleOffset++] =
                                    (byte) ((pixel >>> 16) & 0xff);
                            samples[sampleOffset++] =
                                    (byte) ((pixel >>> 8) & 0xff);
                            samples[sampleOffset++] = (byte) (pixel & 0xff);
                            if (alpha != null) {
                                int opacity = (pixel >>> 24) & 0xff;
                                alpha[alphaOffset++] = (byte) opacity;
                                transparent |= opacity != 255;
                            }
                        }
                    }
                    CanvasMask softMask = null;
                    if (transparent) {
                        // Reserve before CanvasMask takes its defensive copy.
                        retainMemory(pixels);
                        softMask = CanvasMask.soft(width, height, alpha);
                        validateMask(
                                softMask,
                                CanvasMask.Kind.SOFT_IMAGE,
                                width,
                                height);
                    }
                    return new PreparedImage(
                            width,
                            height,
                            8,
                            3,
                            CanvasColorSpace.deviceRgb(),
                            samples,
                            false,
                            -1,
                            null,
                            null,
                            softMask);
                }
            } catch (DocumentFailure failure) {
                throw failure;
            } catch (IOException | RuntimeException decodingFailure) {
                workflowResources.rethrowResourceOrTerminalFailure(
                        decodingFailure);
                throw invalidImage();
            } finally {
                if (reader != null) {
                    try {
                        reader.dispose();
                    } catch (RuntimeException ignored) {
                        // Provider cleanup must not escape the failure contract.
                    }
                }
            }
        }

        private FontInfo validateFont(CanvasFont font) throws DocumentFailure {
            COSBase raw;
            try {
                raw = valueAdapter.referencedObject(font.getObjectReference());
            } catch (DocumentFailure invalidReference) {
                throw invalidFont();
            }
            if (!(raw instanceof COSObject)) {
                throw invalidFont();
            }
            COSBase value = dereference(raw);
            if (!(value instanceof COSDictionary) || value instanceof COSStream) {
                throw invalidFont();
            }
            int codeLength;
            try {
                codeLength = PdfBoxCanvasOperations.validateFontDictionary(
                        (COSDictionary) value,
                        workflowResources);
            } catch (DocumentFailure invalidFont) {
                throw invalidFont();
            }
            return new FontInfo(raw, codeLength);
        }

        private CanvasImage canonicalImage(CanvasImage image)
                throws DocumentFailure {
            CanvasImage canonical = canonicalImagesByDeclaration.get(image);
            if (canonical == null) {
                throw invalidImage();
            }
            return canonical;
        }

        private CanvasColorSpace canonicalColorSpace(
                CanvasColorSpace colorSpace) throws DocumentFailure {
            if (colorSpace == null) {
                throw invalidGraphics();
            }
            CanvasColorSpace canonical =
                    canonicalColorsByDeclaration.get(colorSpace);
            if (canonical != null) {
                return canonical;
            }
            if (device(colorSpace)) {
                return colorSpace;
            }
            throw invalidGraphics();
        }

        private ColorInfo colorInfo(CanvasColorSpace colorSpace)
                throws DocumentFailure {
            CanvasColorSpace canonical = canonicalColorSpace(colorSpace);
            ColorInfo info = colors.get(canonical);
            if (info == null && !device(canonical)) {
                throw invalidGraphics();
            }
            return info;
        }

        private CanvasMask canonicalMask(CanvasMask mask)
                throws DocumentFailure {
            CanvasMask canonical = canonicalMasksByDeclaration.get(mask);
            if (canonical == null) {
                throw invalidImage();
            }
            return canonical;
        }

        private CanvasImage equivalentImage(CanvasImage candidate)
                throws DocumentFailure {
            for (CanvasImage canonical : canonicalImages) {
                workflowResources.checkpoint();
                if (sameImage(candidate, canonical)) {
                    return canonical;
                }
            }
            return null;
        }

        private CanvasColorSpace equivalentColorSpace(
                CanvasColorSpace candidate) throws DocumentFailure {
            for (CanvasColorSpace canonical : canonicalColors) {
                workflowResources.checkpoint();
                if (sameColorSpace(candidate, canonical)) {
                    return canonical;
                }
            }
            return null;
        }

        private CanvasMask equivalentMask(CanvasMask candidate)
                throws DocumentFailure {
            for (CanvasMask canonical : canonicalMasks) {
                workflowResources.checkpoint();
                if (sameMask(candidate, canonical)) {
                    return canonical;
                }
            }
            return null;
        }

        private boolean sameImage(CanvasImage left, CanvasImage right)
                throws DocumentFailure {
            if (left == right) {
                return true;
            }
            if (left.getSourceKind() != right.getSourceKind()
                    || left.getWidth() != right.getWidth()
                    || left.getHeight() != right.getHeight()
                    || left.getBitsPerComponent()
                            != right.getBitsPerComponent()
                    || left.getByteLength() != right.getByteLength()) {
                return false;
            }
            ObjectReference leftReference =
                    left.getObjectReference().orElse(null);
            ObjectReference rightReference =
                    right.getObjectReference().orElse(null);
            if (leftReference == null
                    ? rightReference != null
                    : !leftReference.equals(rightReference)) {
                return false;
            }
            if (!sameNullableColorSpace(
                            left.getColorSpace().orElse(null),
                            right.getColorSpace().orElse(null))
                    || !sameNullableMask(
                            left.getExplicitMask().orElse(null),
                            right.getExplicitMask().orElse(null))
                    || !sameNullableMask(
                            left.getSoftMask().orElse(null),
                            right.getSoftMask().orElse(null))) {
                return false;
            }
            int byteLength = left.getByteLength();
            try (WorkflowResourceContext.MemoryReservation ignored =
                    workflowResources.reserveOwnedMemory(2L * byteLength)) {
                byte[] leftBytes = left.getBytes().orElse(null);
                byte[] rightBytes = right.getBytes().orElse(null);
                return sameBytes(leftBytes, rightBytes);
            }
        }

        private boolean sameNullableColorSpace(
                CanvasColorSpace left,
                CanvasColorSpace right) throws DocumentFailure {
            return left == right
                    || (left != null
                            && right != null
                            && sameColorSpace(left, right));
        }

        private boolean sameColorSpace(
                CanvasColorSpace left,
                CanvasColorSpace right) throws DocumentFailure {
            if (left == right) {
                return true;
            }
            if (left.getFamily() != right.getFamily()
                    || left.getWhitePointLength()
                            != right.getWhitePointLength()
                    || left.getBlackPointLength()
                            != right.getBlackPointLength()
                    || left.getGammaLength() != right.getGammaLength()
                    || left.getMatrixLength() != right.getMatrixLength()
                    || left.getIccProfileByteLength()
                            != right.getIccProfileByteLength()) {
                return false;
            }
            long doubleValues = (long) left.getWhitePointLength()
                    + right.getWhitePointLength()
                    + left.getBlackPointLength()
                    + right.getBlackPointLength()
                    + left.getGammaLength()
                    + right.getGammaLength()
                    + left.getMatrixLength()
                    + right.getMatrixLength();
            long copiedBytes = 8L * doubleValues
                    + left.getIccProfileByteLength()
                    + right.getIccProfileByteLength();
            try (WorkflowResourceContext.MemoryReservation ignored =
                    workflowResources.reserveOwnedMemory(copiedBytes)) {
                if (!sameDoubles(
                                left.getWhitePoint(),
                                right.getWhitePoint())
                        || !sameDoubles(
                                left.getBlackPoint(),
                                right.getBlackPoint())
                        || !sameDoubles(
                                left.getGamma(),
                                right.getGamma())
                        || !sameDoubles(
                                left.getMatrix(),
                                right.getMatrix())) {
                    return false;
                }
                return sameBytes(
                        left.getIccProfileBytes().orElse(null),
                        right.getIccProfileBytes().orElse(null));
            }
        }

        private boolean sameNullableMask(CanvasMask left, CanvasMask right)
                throws DocumentFailure {
            return left == right
                    || (left != null && right != null && sameMask(left, right));
        }

        private boolean sameMask(CanvasMask left, CanvasMask right)
                throws DocumentFailure {
            if (left == right) {
                return true;
            }
            if (left.getKind() != right.getKind()
                    || left.getWidth() != right.getWidth()
                    || left.getHeight() != right.getHeight()
                    || left.isInverted() != right.isInverted()
                    || left.getSampleByteLength()
                            != right.getSampleByteLength()) {
                return false;
            }
            int sampleLength = left.getSampleByteLength();
            try (WorkflowResourceContext.MemoryReservation ignored =
                    workflowResources.reserveOwnedMemory(2L * sampleLength)) {
                return sameBytes(left.getSamples(), right.getSamples());
            }
        }

        private boolean sameBytes(byte[] left, byte[] right)
                throws DocumentFailure {
            if (left == right) {
                return true;
            }
            if (left == null || right == null || left.length != right.length) {
                return false;
            }
            for (int index = 0; index < left.length; index++) {
                if ((index & 8191) == 0) {
                    workflowResources.checkpoint();
                }
                if (left[index] != right[index]) {
                    return false;
                }
            }
            return true;
        }

        private boolean sameDoubles(double[] left, double[] right)
                throws DocumentFailure {
            if (left.length != right.length) {
                return false;
            }
            for (int index = 0; index < left.length; index++) {
                if ((index & 8191) == 0) {
                    workflowResources.checkpoint();
                }
                if (Double.doubleToLongBits(left[index])
                        != Double.doubleToLongBits(right[index])) {
                    return false;
                }
            }
            return true;
        }

        private void accountEncoded(long count) throws DocumentFailure {
            encodedBytes = add(
                    encodedBytes,
                    count,
                    limits.getMaximumEncodedImageBytes());
        }

        private void accountPixels(int width, int height) throws DocumentFailure {
            if (width < 1 || height < 1) {
                throw invalidImage();
            }
            long pixels = multiply(width, height);
            decodedPixels = add(
                    decodedPixels,
                    pixels,
                    limits.getMaximumDecodedImagePixels());
            workflowResources.consumeDecodedPixels(pixels);
        }

        private void accountDecoded(long count) throws DocumentFailure {
            decodedBytes = add(
                    decodedBytes,
                    count,
                    limits.getMaximumDecodedImageBytes());
        }

        private void accountResource() throws DocumentFailure {
            if (resources == limits.getMaximumResourceDeclarations()) {
                throw limitFailure();
            }
            resources++;
        }

        private void retainMemory(long bytes) throws DocumentFailure {
            memoryReservations.add(
                    workflowResources.reserveOwnedMemory(bytes));
        }

        private byte[] requiredBytes(CanvasImage image)
                throws DocumentFailure {
            int byteLength = image.getByteLength();
            if (byteLength == 0) {
                throw invalidImage();
            }
            retainMemory(byteLength);
            byte[] bytes = image.getBytes().orElse(null);
            if (bytes == null || bytes.length != byteLength) {
                throw invalidImage();
            }
            return bytes;
        }

        private byte[] glyphCode(CanvasProgram.Instruction instruction)
                throws DocumentFailure {
            byte[] glyphCode = glyphCodes.get(instruction);
            if (glyphCode == null) {
                throw invalidFont();
            }
            return glyphCode;
        }

        private long addExactForMemory(long left, long right)
                throws DocumentFailure {
            if (left < 0L || right < 0L || left > Long.MAX_VALUE - right) {
                throw workflowResources.policyFailure(
                        DocumentFailureCode.MEMORY_LIMIT_EXCEEDED,
                        "The workflow owned-memory limit was exceeded.");
            }
            return left + right;
        }

        private void releaseMemory() {
            for (int index = memoryReservations.size() - 1;
                    index >= 0;
                    index--) {
                memoryReservations.get(index).close();
            }
            memoryReservations.clear();
        }
    }

    private static JpegInfo jpegInfo(
            byte[] bytes,
            WorkflowResourceContext resources) throws DocumentFailure {
        if (bytes.length < 6
                || (bytes[0] & 0xff) != 0xff
                || (bytes[1] & 0xff) != 0xd8
                || (bytes[bytes.length - 2] & 0xff) != 0xff
                || (bytes[bytes.length - 1] & 0xff) != 0xd9) {
            throw invalidImage();
        }
        int offset = 2;
        int colorTransform = -1;
        while (offset + 3 < bytes.length) {
            resources.checkpoint();
            if ((bytes[offset] & 0xff) != 0xff) {
                throw invalidImage();
            }
            while (offset < bytes.length && (bytes[offset] & 0xff) == 0xff) {
                if ((offset & 1023) == 0) {
                    resources.checkpoint();
                }
                offset++;
            }
            if (offset >= bytes.length) {
                break;
            }
            int marker = bytes[offset++] & 0xff;
            if (marker == 0xd9 || marker == 0xda) {
                break;
            }
            if (marker == 0x01 || (marker >= 0xd0 && marker <= 0xd7)) {
                continue;
            }
            if (offset + 2 > bytes.length) {
                throw invalidImage();
            }
            int length = unsignedShort(bytes, offset);
            if (length < 2 || offset + length > bytes.length) {
                throw invalidImage();
            }
            if (marker == 0xe0
                    && length >= 7
                    && bytes[offset + 2] == 'J'
                    && bytes[offset + 3] == 'F'
                    && bytes[offset + 4] == 'I'
                    && bytes[offset + 5] == 'F'
                    && bytes[offset + 6] == 0) {
                colorTransform = 1;
            } else if (marker == 0xee
                    && length >= 14
                    && bytes[offset + 2] == 'A'
                    && bytes[offset + 3] == 'd'
                    && bytes[offset + 4] == 'o'
                    && bytes[offset + 5] == 'b'
                    && bytes[offset + 6] == 'e') {
                colorTransform = (bytes[offset + 13] & 0xff) == 0 ? 0 : 1;
            }
            if (marker == 0xc0 || marker == 0xc1 || marker == 0xc2) {
                if (length < 8 || (bytes[offset + 2] & 0xff) != 8) {
                    throw unsupportedResource();
                }
                int height = unsignedShort(bytes, offset + 3);
                int width = unsignedShort(bytes, offset + 5);
                int components = bytes[offset + 7] & 0xff;
                if (width < 1 || height < 1
                        || (components != 1 && components != 3 && components != 4)
                        || length != 8 + components * 3) {
                    throw invalidImage();
                }
                return new JpegInfo(
                        width,
                        height,
                        components,
                        colorTransform);
            }
            offset += length;
        }
        throw unsupportedResource();
    }

    private static void requirePng(byte[] bytes) throws DocumentFailure {
        byte[] signature = new byte[] {
            (byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10
        };
        if (bytes.length < 33) {
            throw invalidImage();
        }
        for (int index = 0; index < signature.length; index++) {
            if (bytes[index] != signature[index]) {
                throw invalidImage();
            }
        }
        if (unsignedInt(bytes, 8) != 13L
                || bytes[12] != 'I'
                || bytes[13] != 'H'
                || bytes[14] != 'D'
                || bytes[15] != 'R'
                || unsignedInt(bytes, 16) < 1L
                || unsignedInt(bytes, 20) < 1L) {
            throw invalidImage();
        }
    }

    private static void requireTiff(byte[] bytes) throws DocumentFailure {
        if (bytes.length < 8) {
            throw invalidImage();
        }
        boolean little = bytes[0] == 'I' && bytes[1] == 'I'
                && bytes[2] == 42 && bytes[3] == 0;
        boolean big = bytes[0] == 'M' && bytes[1] == 'M'
                && bytes[2] == 0 && bytes[3] == 42;
        if (!little && !big) {
            throw invalidImage();
        }
    }

    private boolean supportedExistingFilter(COSBase raw)
            throws DocumentFailure {
        COSBase value = dereference(raw);
        if (value == null || value instanceof COSNull) {
            return true;
        }
        if (value instanceof COSName) {
            return COSName.FLATE_DECODE.equals(value)
                    || COSName.DCT_DECODE.equals(value);
        }
        if (value instanceof COSArray && ((COSArray) value).size() == 1) {
            COSBase name = dereference(((COSArray) value).get(0));
            return COSName.FLATE_DECODE.equals(name)
                    || COSName.DCT_DECODE.equals(name);
        }
        return false;
    }

    private long positiveInteger(COSBase raw) throws DocumentFailure {
        COSBase value = dereference(raw);
        if (!(value instanceof COSInteger)) {
            return -1L;
        }
        return ((COSInteger) value).longValue();
    }

    private static CanvasColorSpace colorSpace(int components)
            throws DocumentFailure {
        if (components == 1) {
            return CanvasColorSpace.deviceGray();
        }
        if (components == 3) {
            return CanvasColorSpace.deviceRgb();
        }
        if (components == 4) {
            return CanvasColorSpace.deviceCmyk();
        }
        throw unsupportedResource();
    }

    private static void requireUnit(double value) throws DocumentFailure {
        requireFinite(value);
        if (value < 0d || value > 1d) {
            throw invalidGraphics();
        }
    }

    private static void requireFinite(double value) throws DocumentFailure {
        if (Double.isNaN(value)
                || Double.isInfinite(value)
                || Math.abs(value) > MAXIMUM_ABSOLUTE_NUMBER) {
            throw invalidGraphics();
        }
    }

    private static long multiply(long left, long right) throws DocumentFailure {
        if (left < 0L || right < 0L
                || (right != 0L && left > Long.MAX_VALUE / right)) {
            throw limitFailure();
        }
        return left * right;
    }

    private static long add(long current, long count, long maximum)
            throws DocumentFailure {
        if (count < 0L || current > maximum - count) {
            throw limitFailure();
        }
        return current + count;
    }

    private static int unsignedShort(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff);
    }

    private static long unsignedInt(byte[] bytes, int offset) {
        return ((long) (bytes[offset] & 0xff) << 24)
                | ((long) (bytes[offset + 1] & 0xff) << 16)
                | ((long) (bytes[offset + 2] & 0xff) << 8)
                | (long) (bytes[offset + 3] & 0xff);
    }

    private static DocumentFailure invalidImage() {
        return failure(
                DocumentFailureCode.CANVAS_IMAGE_INVALID,
                "The Canvas Image is invalid.");
    }

    private static DocumentFailure invalidFont() {
        return failure(
                DocumentFailureCode.CANVAS_RESOURCE_INVALID,
                "The Canvas Font resource is invalid.");
    }

    private static DocumentFailure invalidGraphics() {
        return failure(
                DocumentFailureCode.CANVAS_GRAPHICS_INVALID,
                "The Canvas color or transparency declaration is invalid.");
    }

    private static DocumentFailure unsupportedResource() {
        return failure(
                DocumentFailureCode.CANVAS_RESOURCE_UNSUPPORTED,
                "The Canvas image or color resource is unsupported.");
    }

    private static DocumentFailure codecUnavailable() {
        return failure(
                DocumentFailureCode.CANVAS_IMAGE_CODEC_UNAVAILABLE,
                "The optional Canvas Image codec is unavailable.");
    }

    static DocumentFailure limitFailure() {
        return failure(
                DocumentFailureCode.CANVAS_RESOURCE_LIMIT_EXCEEDED,
                "The Canvas resource limit was exceeded.");
    }

    private static DocumentFailure preservationFailure() {
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

    static final class Plan implements AutoCloseable {

        final COSDictionary resources;
        final boolean resourcesChanged;
        final byte[] operators;
        private WorkflowResourceContext.OwnedBytes operatorBytes;

        Plan(
                COSDictionary resources,
                boolean resourcesChanged,
                WorkflowResourceContext.OwnedBytes operatorBytes) {
            this.resources = resources;
            this.resourcesChanged = resourcesChanged;
            this.operatorBytes = operatorBytes;
            this.operators = operatorBytes.getBytes();
        }

        @Override
        public void close() {
            WorkflowResourceContext.OwnedBytes current = operatorBytes;
            if (current != null) {
                operatorBytes = null;
                current.close();
            }
        }
    }

    private static final class ProgramPlan implements AutoCloseable {

        private final COSDictionary resources;
        private final boolean changed;
        private WorkflowResourceContext.OwnedBytes operatorBytes;

        ProgramPlan(
                COSDictionary resources,
                boolean changed,
                WorkflowResourceContext.OwnedBytes operatorBytes) {
            this.resources = resources;
            this.changed = changed;
            this.operatorBytes = operatorBytes;
        }

        private byte[] operators() {
            return operatorBytes.getBytes();
        }

        @Override
        public void close() {
            WorkflowResourceContext.OwnedBytes current = operatorBytes;
            if (current != null) {
                operatorBytes = null;
                current.close();
            }
        }
    }

    private static final class RequestPlan implements AutoCloseable {

        private final IdentityHashMap<CanvasTransparencyGroup, ProgramPreflight>
                groupPrograms =
                new IdentityHashMap<CanvasTransparencyGroup, ProgramPreflight>();

        @Override
        public void close() {
            for (ProgramPreflight preflight : groupPrograms.values()) {
                if (preflight != null) {
                    preflight.close();
                }
            }
        }
    }

    private static final class ProgramPreflight implements AutoCloseable {

        private final ProgramBindings bindings;
        private WorkflowResourceContext.OwnedBytes operatorBytes;

        ProgramPreflight(
                ProgramBindings bindings,
                WorkflowResourceContext.OwnedBytes operatorBytes) {
            this.bindings = bindings;
            this.operatorBytes = operatorBytes;
        }

        private WorkflowResourceContext.OwnedBytes takeOperators() {
            WorkflowResourceContext.OwnedBytes current = operatorBytes;
            if (current == null) {
                throw new IllegalStateException(
                        "Canvas operators were already materialized");
            }
            operatorBytes = null;
            return current;
        }

        @Override
        public void close() {
            WorkflowResourceContext.OwnedBytes current = operatorBytes;
            if (current != null) {
                operatorBytes = null;
                current.close();
            }
        }
    }

    private static final class ProgramBindings {

        private final Map<CanvasFont, COSName> fontNames =
                new LinkedHashMap<CanvasFont, COSName>();
        private final Map<CanvasColorSpace, COSName> colorNames =
                new IdentityHashMap<CanvasColorSpace, COSName>();
        private final List<CanvasColorSpace> colorOrder =
                new ArrayList<CanvasColorSpace>();
        private final Map<CanvasTransparencyState, COSName> transparencyNames =
                new LinkedHashMap<CanvasTransparencyState, COSName>();
        private final Map<CanvasImage, COSName> imageNames =
                new IdentityHashMap<CanvasImage, COSName>();
        private final List<CanvasImage> imageOrder =
                new ArrayList<CanvasImage>();
        private final IdentityHashMap<CanvasTransparencyGroup, COSName> groupNames =
                new IdentityHashMap<CanvasTransparencyGroup, COSName>();
        private final List<CanvasTransparencyGroup> groupOrder =
                new ArrayList<CanvasTransparencyGroup>();
    }

    /** Seekable, zero-copy ImageIO input over an already-accounted array. */
    private static final class ByteArrayImageInputStream
            extends ImageInputStreamImpl {

        private final byte[] bytes;
        private final WorkflowResourceContext resources;

        private ByteArrayImageInputStream(
                byte[] bytes,
                WorkflowResourceContext resources) {
            this.bytes = bytes;
            this.resources = resources;
        }

        @Override
        public int read() throws IOException {
            checkClosed();
            resources.checkpointAsIOException();
            bitOffset = 0;
            if (streamPos >= bytes.length) {
                return -1;
            }
            return bytes[(int) streamPos++] & 0xff;
        }

        @Override
        public int read(byte[] target, int offset, int length)
                throws IOException {
            checkClosed();
            if (target == null
                    || offset < 0
                    || length < 0
                    || offset > target.length - length) {
                throw new IndexOutOfBoundsException();
            }
            resources.checkpointAsIOException();
            bitOffset = 0;
            if (length == 0) {
                return 0;
            }
            if (streamPos >= bytes.length) {
                return -1;
            }
            int count = (int) Math.min(
                    Math.min(length, 8192),
                    bytes.length - streamPos);
            System.arraycopy(bytes, (int) streamPos, target, offset, count);
            streamPos += count;
            return count;
        }

        @Override
        public long length() {
            return bytes.length;
        }
    }

    private static final class Category {

        private final COSDictionary values;
        private boolean changed;

        Category(COSDictionary values) {
            this.values = values;
        }
    }

    private static final class PreparedImage {

        private final int width;
        private final int height;
        private final int bits;
        private final int components;
        private final CanvasColorSpace colorSpace;
        private final byte[] samples;
        private final boolean jpeg;
        private final int jpegColorTransform;
        private final COSObject existing;
        private final CanvasMask explicitMask;
        private final CanvasMask softMask;

        PreparedImage(
                int width,
                int height,
                int bits,
                int components,
                CanvasColorSpace colorSpace,
                byte[] samples,
                boolean jpeg,
                int jpegColorTransform,
                COSObject existing,
                CanvasMask explicitMask,
                CanvasMask softMask) {
            this.width = width;
            this.height = height;
            this.bits = bits;
            this.components = components;
            this.colorSpace = colorSpace;
            this.samples = samples;
            this.jpeg = jpeg;
            this.jpegColorTransform = jpegColorTransform;
            this.existing = existing;
            this.explicitMask = explicitMask;
            this.softMask = softMask;
        }
    }

    private static final class ColorInfo {

        private final int components;
        private final byte[] profile;

        ColorInfo(int components, byte[] profile) {
            this.components = components;
            this.profile = profile;
        }
    }

    private static final class FontInfo {

        private final COSBase raw;
        private final int codeLength;

        FontInfo(COSBase raw, int codeLength) {
            this.raw = raw;
            this.codeLength = codeLength;
        }
    }

    private static final class JpegInfo {

        private final int width;
        private final int height;
        private final int components;
        private final int colorTransform;

        JpegInfo(
                int width,
                int height,
                int components,
                int colorTransform) {
            this.width = width;
            this.height = height;
            this.components = components;
            this.colorTransform = colorTransform;
        }
    }
}
