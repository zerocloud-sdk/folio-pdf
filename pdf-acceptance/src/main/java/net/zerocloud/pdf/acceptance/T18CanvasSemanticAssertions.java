package net.zerocloud.pdf.acceptance;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentResource;
import net.zerocloud.pdf.DocumentResourceInventory;
import net.zerocloud.pdf.DocumentSession;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.ImageByteAccess;
import net.zerocloud.pdf.ImageResource;
import net.zerocloud.pdf.ObjectReference;
import net.zerocloud.pdf.PdfArray;
import net.zerocloud.pdf.PdfBoolean;
import net.zerocloud.pdf.PdfDictionary;
import net.zerocloud.pdf.PdfIndirectReference;
import net.zerocloud.pdf.PdfInspectionLimits;
import net.zerocloud.pdf.PdfName;
import net.zerocloud.pdf.PdfNumber;
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

/** Project-owned semantic assertions for the T18 Canvas artifact. */
final class T18CanvasSemanticAssertions {

    private static final String CAPABILITY =
            "composition.canvas.images-colors-transparency";

    private T18CanvasSemanticAssertions() {
    }

    static T18CanvasSemanticObservation inspect(
            WorkflowOutcome<Void> creation,
            Path artifact) {
        PublicationStatus publicationStatus = publicationStatus(creation);
        boolean capabilityReported = CAPABILITY.equals(creation.getCapabilityId());
        try {
            Observations observations = new DocumentWorkflow().execute(
                    WorkflowRequest.open(artifact, SaveMode.REWRITE),
                    T18CanvasSemanticAssertions::observe).getResult();
            return T18CanvasSemanticObservation.observed(
                    publicationStatus,
                    capabilityReported,
                    observations.imageSemantics,
                    observations.colorSemantics,
                    observations.maskSemantics,
                    observations.transparencySemantics,
                    observations.resourceReuse,
                    observations.preservation);
        } catch (DocumentFailure failure) {
            return T18CanvasSemanticObservation.reopenFailed(
                    publicationStatus,
                    capabilityReported,
                    failure.getCode());
        } catch (RuntimeException malformedObservation) {
            return T18CanvasSemanticObservation.reopenFailed(
                    publicationStatus,
                    capabilityReported,
                    null);
        }
    }

    private static Observations observe(DocumentSession session)
            throws DocumentFailure {
        DocumentResourceInventory inventory = session.query(
                ExtractImagesAndResources.version1(
                        limits(),
                        ImageByteAccess.ENCODED_AND_DECODED));
        List<ImageResource> images = inventory.getImages();
        int dct = 0;
        int flate = 0;
        int explicitOwners = 0;
        int softOwners = 0;
        int icc = 0;
        boolean validIccProfile = false;
        boolean jpegSemantics = false;
        boolean pngSemantics = false;
        boolean tiffSemantics = false;
        boolean existingSemantics = false;
        boolean rawIccSemantics = false;
        boolean explicitOwnerSemantics = false;
        boolean softOwnerSemantics = false;
        boolean explicitMaskSemantics = false;
        boolean declaredSoftMaskSemantics = false;
        PdfName jpegResourceName = null;
        PdfName existingResourceName = null;
        byte[] expectedAlpha = expectedPngAlpha();
        byte[] expectedRgb = expectedRasterRgb();
        byte[] expectedProfile = ICC_Profile.getInstance(
                ColorSpace.CS_sRGB).getData();
        String expectedProfileHash = sha256(expectedProfile);
        for (ImageResource image : images) {
            dct += hasFilter(image, "DCTDecode") ? 1 : 0;
            flate += hasFilter(image, "FlateDecode") ? 1 : 0;
            explicitOwners += image.getExplicitMask().isPresent() ? 1 : 0;
            softOwners += image.getSoftMask().isPresent() ? 1 : 0;
            boolean currentJpegSemantics = image.getWidth() == 4
                    && image.getHeight() == 4
                    && exactFilter(image, "DCTDecode")
                    && deviceRgb8(image)
                    && !image.getExplicitMask().isPresent()
                    && !image.getSoftMask().isPresent();
            if (currentJpegSemantics) {
                jpegSemantics = true;
                jpegResourceName = pageXObjectName(image);
            }
            if (image.getWidth() == 4
                    && image.getHeight() == 4
                    && exactFilter(image, "FlateDecode")
                    && deviceRgb8(image)) {
                if (image.getSoftMask().isPresent()) {
                    ImageResource mask = image.getSoftMask().get()
                            .getImage().orElse(null);
                    pngSemantics |= mask != null
                            && image.getSoftMask().get().getKind()
                                    == ImageResource.Mask.Kind.SOFT_IMAGE
                            && gray8(mask, 4, 4)
                            && Arrays.equals(
                                    expectedAlpha,
                                    mask.getDecodedData().getBytes()
                                            .orElse(null))
                            && Arrays.equals(
                                    expectedRgb,
                                    image.getDecodedData().getBytes()
                                            .orElse(null));
                } else {
                    tiffSemantics |= Arrays.equals(
                            expectedRgb,
                            image.getDecodedData().getBytes().orElse(null));
                }
            }
            boolean currentExistingSemantics = image.getWidth() == 2
                    && image.getHeight() == 2
                    && image.getFilters().isEmpty()
                    && deviceRgb8(image)
                    && Arrays.equals(
                            "RGBYMCBRYGMC".getBytes(StandardCharsets.US_ASCII),
                            image.getDecodedData().getBytes().orElse(null));
            if (currentExistingSemantics) {
                existingSemantics = true;
                existingResourceName = pageXObjectName(image);
            }
            if (image.getExplicitMask().isPresent()) {
                ImageResource mask = image.getExplicitMask().get()
                        .getImage().orElse(null);
                explicitOwnerSemantics |= image.getWidth() == 2
                        && image.getHeight() == 2
                        && exactFilter(image, "FlateDecode")
                        && deviceRgb8(image)
                        && !image.getSoftMask().isPresent()
                        && Arrays.equals(
                                expectedExplicitOwnerSamples(),
                                image.getDecodedData().getBytes().orElse(null));
                explicitMaskSemantics |= mask != null
                        && image.getExplicitMask().get().getKind()
                                == ImageResource.Mask.Kind.EXPLICIT_IMAGE
                        && mask.isImageMask()
                        && mask.getWidth() == 2
                        && mask.getHeight() == 2
                        && mask.getBitsPerComponent().orElse(-1) == 1
                        && Arrays.equals(
                                new byte[] {(byte) 0x80, (byte) 0x40},
                                mask.getDecodedData().getBytes().orElse(null));
            }
            if (image.getWidth() == 2
                    && image.getHeight() == 2
                    && image.getSoftMask().isPresent()) {
                ImageResource mask = image.getSoftMask().get()
                        .getImage().orElse(null);
                softOwnerSemantics |= exactFilter(image, "FlateDecode")
                        && deviceRgb8(image)
                        && !image.getExplicitMask().isPresent()
                        && Arrays.equals(
                                expectedSoftOwnerSamples(),
                                image.getDecodedData().getBytes().orElse(null));
                declaredSoftMaskSemantics |= mask != null
                        && gray8(mask, 2, 2)
                        && Arrays.equals(
                                new byte[] {0, 90, (byte) 180, (byte) 255},
                                mask.getDecodedData().getBytes().orElse(null));
            }
            if (image.getColorSpace().getFamily()
                    == ImageResource.ColorFamily.ICC_BASED) {
                icc++;
                ImageResource.IccProfile profile = image.getColorSpace()
                        .getIccProfile().orElse(null);
                rawIccSemantics |= image.getWidth() == 2
                        && image.getHeight() == 2
                        && image.getBitsPerComponent().orElse(-1) == 8
                        && image.getColorComponents().orElse(-1) == 3
                        && exactFilter(image, "FlateDecode")
                        && !image.getExplicitMask().isPresent()
                        && !image.getSoftMask().isPresent()
                        && Arrays.equals(
                                expectedRawIccSamples(),
                                image.getDecodedData().getBytes().orElse(null));
                validIccProfile |= image.getWidth() == 2
                        && image.getHeight() == 2
                        && image.getColorSpace().getStatus()
                                == ImageResource.ColorStatus.SUPPORTED
                        && image.getColorComponents().orElse(-1) == 3
                        && profile != null
                        && profile.getObjectReference().isPresent()
                        && profile.getByteLength() == expectedProfile.length
                        && expectedProfileHash.equals(profile.getSha256());
            }
        }

        PdfDictionary page = inspectDictionary(
                session,
                session.query(PageObjectReference.version1(1)));
        PdfDictionary resources = dictionary(
                session,
                page.get(PdfName.of("Resources")));
        PdfDictionary xobjects = dictionary(
                session,
                resources.get(PdfName.of("XObject")));
        PdfDictionary colorSpaces = dictionary(
                session,
                resources.get(PdfName.of("ColorSpace")));
        PdfDictionary states = dictionary(
                session,
                resources.get(PdfName.of("ExtGState")));
        String content = pageContent(session, page);

        boolean hasTransparencyGroup = false;
        boolean groupColorSemantics = false;
        boolean groupResourceReuse = false;
        for (int index = 0; index < xobjects.size(); index++) {
            net.zerocloud.pdf.PdfDictionaryEntry entry = xobjects.getEntry(index);
            PdfValue value = resolve(session, entry.getValue());
            if (!(value instanceof PdfStream)) {
                continue;
            }
            PdfStream stream = (PdfStream) value;
            if (!PdfName.of("Form").equals(
                    stream.getDictionary().get(PdfName.of("Subtype")))) {
                continue;
            }
            PdfDictionary group = dictionary(
                    session,
                    stream.getDictionary().get(PdfName.of("Group")));
            boolean currentGroupSemantics = PdfName.of("Transparency").equals(
                            group.get(PdfName.of("S")))
                    && PdfName.of("DeviceRGB").equals(
                            group.get(PdfName.of("CS")))
                    && PdfBoolean.of(true).equals(group.get(PdfName.of("I")))
                    && PdfBoolean.of(false).equals(group.get(PdfName.of("K")))
                    && exactNumbers(
                            session,
                            stream.getDictionary().get(PdfName.of("BBox")),
                            0d, 0d, 78d, 78d);
            hasTransparencyGroup |= currentGroupSemantics;
            groupResourceReuse |= currentGroupSemantics
                    && resourceOperatorOccurrences(
                            content,
                            entry.getName(),
                            "Do") == 2;
            String groupContent = ascii(stream.readBytes());
            groupColorSemantics |= groupContent.contains(
                            "0.95 0.32 0.18 rg\n")
                    && groupContent.contains("0.55 scn\n")
                    && calibratedGraySemantics(
                            session,
                            dictionary(
                                    session,
                                    stream.getDictionary().get(
                                            PdfName.of("Resources"))));
        }

        boolean imageSemantics = images.size() == 10
                && dct == 1
                && flate == 8
                && jpegSemantics
                && pngSemantics
                && tiffSemantics
                && existingSemantics
                && rawIccSemantics
                && explicitOwnerSemantics
                && softOwnerSemantics
                && contentOccurrences(content, " Do\n") == 11;
        boolean colorSemantics = icc == 1
                && validIccProfile
                && colorSpaces.size() == 3
                && pageColorSpaceSemantics(session, colorSpaces, images)
                && groupColorSemantics
                && content.contains("0.93 g\n")
                && content.contains("0.1 0.38 0.78 rg\n")
                && content.contains("0.05 0.75 0.7 0.05 k\n")
                && content.contains("0.15 0.7 0.28 scn\n")
                && content.contains("0.18 SCN\n")
                && content.contains("0.64 0.12 0.74 SCN\n");
        boolean maskSemantics = explicitOwners == 1
                && softOwners == 2
                && explicitMaskSemantics
                && declaredSoftMaskSemantics
                && pngSemantics;
        boolean stateResourceReuse = states.size() == 1
                && resourceOperatorOccurrences(
                        content,
                        states.getEntry(0).getName(),
                        "gs") == 2;
        boolean transparencySemantics = states.size() == 1
                && transparencyStateSemantics(session, states)
                && hasTransparencyGroup
                && stateResourceReuse;
        boolean resourceReuse = xobjects.size() == 8
                && jpegResourceName != null
                && resourceOperatorOccurrences(
                        content,
                        jpegResourceName,
                        "Do") == 2
                && existingResourceName != null
                && resourceOperatorOccurrences(
                        content,
                        existingResourceName,
                        "Do") == 2
                && groupResourceReuse
                && stateResourceReuse;
        boolean preservation = PdfName.of("Kept").equals(
                        resources.get(PdfName.of("FolioKeep")))
                && content.contains("24 24 m")
                && hasResourceKind(inventory, DocumentResource.Kind.FORM);
        return new Observations(
                imageSemantics,
                colorSemantics,
                maskSemantics,
                transparencySemantics,
                resourceReuse,
                preservation);
    }

    private static boolean pageColorSpaceSemantics(
            DocumentSession session,
            PdfDictionary colorSpaces,
            List<ImageResource> images) throws DocumentFailure {
        boolean calGray = false;
        boolean calRgb = false;
        boolean icc = false;
        ObjectReference expectedProfileReference = null;
        for (ImageResource image : images) {
            if (image.getColorSpace().getFamily()
                    == ImageResource.ColorFamily.ICC_BASED
                    && image.getColorSpace().getIccProfile().isPresent()) {
                expectedProfileReference = image.getColorSpace()
                        .getIccProfile().get().getObjectReference().orElse(null);
            }
        }
        for (int index = 0; index < colorSpaces.size(); index++) {
            PdfValue raw = resolve(
                    session,
                    colorSpaces.getEntry(index).getValue());
            if (!(raw instanceof PdfArray)) {
                return false;
            }
            PdfArray declaration = (PdfArray) raw;
            if (declaration.size() != 2
                    || !(declaration.get(0) instanceof PdfName)) {
                return false;
            }
            PdfName family = (PdfName) declaration.get(0);
            if (PdfName.of("CalGray").equals(family)) {
                calGray |= calibratedGrayParameters(
                        session,
                        dictionary(session, declaration.get(1)));
            } else if (PdfName.of("CalRGB").equals(family)) {
                PdfDictionary parameters = dictionary(
                        session,
                        declaration.get(1));
                calRgb |= exactNumbers(
                                session,
                                parameters.get(PdfName.of("WhitePoint")),
                                0.9505d, 1d, 1.089d)
                        && exactNumbers(
                                session,
                                parameters.get(PdfName.of("BlackPoint")),
                                0d, 0d, 0d)
                        && exactNumbers(
                                session,
                                parameters.get(PdfName.of("Gamma")),
                                2.2d, 2.2d, 2.2d)
                        && exactNumbers(
                                session,
                                parameters.get(PdfName.of("Matrix")),
                                0.4124d, 0.2126d, 0.0193d,
                                0.3576d, 0.7152d, 0.1192d,
                                0.1805d, 0.0722d, 0.9505d);
            } else if (PdfName.of("ICCBased").equals(family)) {
                PdfValue profileValue = declaration.get(1);
                ObjectReference observedReference =
                        profileValue instanceof PdfIndirectReference
                                ? ((PdfIndirectReference) profileValue)
                                        .getReference()
                                : null;
                PdfValue resolved = resolve(session, profileValue);
                icc |= observedReference != null
                        && observedReference.equals(expectedProfileReference)
                        && resolved instanceof PdfStream
                        && PdfNumber.of(3L).equals(((PdfStream) resolved)
                                .getDictionary().get(PdfName.of("N")))
                        && PdfName.of("DeviceRGB").equals(
                                ((PdfStream) resolved).getDictionary().get(
                                        PdfName.of("Alternate")));
            }
        }
        return calGray && calRgb && icc;
    }

    private static boolean calibratedGraySemantics(
            DocumentSession session,
            PdfDictionary resources) throws DocumentFailure {
        PdfDictionary colorSpaces = dictionary(
                session,
                resources.get(PdfName.of("ColorSpace")));
        for (int index = 0; index < colorSpaces.size(); index++) {
            PdfValue raw = resolve(
                    session,
                    colorSpaces.getEntry(index).getValue());
            if (raw instanceof PdfArray) {
                PdfArray declaration = (PdfArray) raw;
                if (declaration.size() == 2
                        && PdfName.of("CalGray").equals(declaration.get(0))
                        && calibratedGrayParameters(
                                session,
                                dictionary(session, declaration.get(1)))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean calibratedGrayParameters(
            DocumentSession session,
            PdfDictionary parameters) throws DocumentFailure {
        return exactNumbers(
                        session,
                        parameters.get(PdfName.of("WhitePoint")),
                        0.9505d, 1d, 1.089d)
                && exactNumbers(
                        session,
                        parameters.get(PdfName.of("BlackPoint")),
                        0d, 0d, 0d)
                && PdfNumber.of(BigDecimal.valueOf(2.2d)).equals(
                        parameters.get(PdfName.of("Gamma")));
    }

    private static boolean transparencyStateSemantics(
            DocumentSession session,
            PdfDictionary states) throws DocumentFailure {
        PdfDictionary state = dictionary(
                session,
                states.getEntry(0).getValue());
        return PdfNumber.of(BigDecimal.valueOf(0.72d)).equals(
                        state.get(PdfName.of("ca")))
                && PdfNumber.of(BigDecimal.valueOf(0.84d)).equals(
                        state.get(PdfName.of("CA")))
                && PdfName.of("Multiply").equals(
                        state.get(PdfName.of("BM")));
    }

    private static boolean exactNumbers(
            DocumentSession session,
            PdfValue raw,
            double... expected) throws DocumentFailure {
        PdfValue value = resolve(session, raw);
        if (!(value instanceof PdfArray)) {
            return false;
        }
        PdfArray numbers = (PdfArray) value;
        if (numbers.size() != expected.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if (!PdfNumber.of(BigDecimal.valueOf(expected[index])).equals(
                    numbers.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean exactFilter(ImageResource image, String name) {
        return image.getFilters().size() == 1 && hasFilter(image, name);
    }

    private static boolean deviceRgb8(ImageResource image) {
        return !image.isImageMask()
                && image.getBitsPerComponent().orElse(-1) == 8
                && image.getColorComponents().orElse(-1) == 3
                && image.getColorSpace().getStatus()
                        == ImageResource.ColorStatus.SUPPORTED
                && image.getColorSpace().getFamily()
                        == ImageResource.ColorFamily.DEVICE_RGB;
    }

    private static boolean gray8(
            ImageResource image,
            int width,
            int height) {
        return image.getWidth() == width
                && image.getHeight() == height
                && image.getBitsPerComponent().orElse(-1) == 8
                && image.getColorComponents().orElse(-1) == 1
                && image.getColorSpace().getFamily()
                        == ImageResource.ColorFamily.DEVICE_GRAY;
    }

    private static byte[] expectedPngAlpha() {
        byte[] result = new byte[16];
        Arrays.fill(result, (byte) 255);
        result[5] = 88;
        return result;
    }

    private static byte[] expectedRasterRgb() {
        byte[] result = new byte[4 * 4 * 3];
        int offset = 0;
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                int[] color;
                if (x < 2 && y < 2) {
                    color = new int[] {232, 55, 45};
                } else if (x >= 2 && y < 2) {
                    color = new int[] {42, 194, 82};
                } else if (x < 2) {
                    color = new int[] {42, 92, 226};
                } else {
                    color = new int[] {242, 204, 42};
                }
                result[offset++] = (byte) color[0];
                result[offset++] = (byte) color[1];
                result[offset++] = (byte) color[2];
            }
        }
        return result;
    }

    private static byte[] expectedRawIccSamples() {
        return new byte[] {
            (byte) 246, 80, 42,
            45, (byte) 194, 82,
            48, 96, (byte) 232,
            (byte) 242, (byte) 205, 48
        };
    }

    private static byte[] expectedExplicitOwnerSamples() {
        return new byte[] {
            (byte) 222, 48, 72, 42, (byte) 190, 92,
            45, 86, (byte) 220, (byte) 236, (byte) 190, 40
        };
    }

    private static byte[] expectedSoftOwnerSamples() {
        return new byte[] {
            40, 100, (byte) 230, 40, 100, (byte) 230,
            40, 100, (byte) 230, 40, 100, (byte) 230
        };
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder value = new StringBuilder(digest.length * 2);
            for (byte part : digest) {
                int current = part & 0xff;
                value.append(Character.forDigit(current >>> 4, 16));
                value.append(Character.forDigit(current & 0xf, 16));
            }
            return value.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static PublicationStatus publicationStatus(
            WorkflowOutcome<Void> creation) {
        for (PublicationReceipt receipt : creation.getPublicationReceipts()) {
            if (receipt.getStatus() == PublicationStatus.COMMITTED) {
                return PublicationStatus.COMMITTED;
            }
        }
        return creation.getPublicationReceipts().isEmpty()
                ? PublicationStatus.NOT_ATTEMPTED
                : creation.getPublicationReceipts().get(0).getStatus();
    }

    private static boolean hasFilter(ImageResource image, String name) {
        for (ImageResource.Filter filter : image.getFilters()) {
            if (PdfName.of(name).equals(filter.getName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasResourceKind(
            DocumentResourceInventory inventory,
            DocumentResource.Kind kind) {
        for (DocumentResource resource : inventory.getResources()) {
            if (resource.getKind() == kind) {
                return true;
            }
        }
        return false;
    }

    private static int contentOccurrences(String value, String fragment) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(fragment, offset)) >= 0) {
            count++;
            offset += fragment.length();
        }
        return count;
    }

    private static int resourceOperatorOccurrences(
            String content,
            PdfName resourceName,
            String operator) {
        return contentOccurrences(
                content,
                "/" + resourceName.getValue() + " " + operator + "\n");
    }

    private static PdfName pageXObjectName(ImageResource image) {
        for (net.zerocloud.pdf.ResourceDeclaration declaration
                : image.getDeclarations()) {
            List<net.zerocloud.pdf.ResourceDeclaration.Segment> path =
                    declaration.getPath();
            if (declaration.getPageNumber() == 1
                    && path.size() == 1
                    && PdfName.of("XObject").equals(
                            path.get(0).getCategory())) {
                return path.get(0).getName();
            }
        }
        return null;
    }

    private static String pageContent(
            DocumentSession session,
            PdfDictionary page) throws DocumentFailure {
        PdfValue contents = resolve(session, page.get(PdfName.of("Contents")));
        StringBuilder combined = new StringBuilder();
        if (contents instanceof PdfStream) {
            combined.append(ascii(((PdfStream) contents).readBytes()));
        } else {
            PdfArray streams = (PdfArray) contents;
            for (int index = 0; index < streams.size(); index++) {
                PdfStream stream = (PdfStream) resolve(session, streams.get(index));
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
                PdfInspectionLimits.of(256, 16L * 1024L * 1024L)));
    }

    private static PdfDictionary dictionary(
            DocumentSession session,
            PdfValue value) throws DocumentFailure {
        return (PdfDictionary) resolve(session, value);
    }

    private static PdfValue resolve(
            DocumentSession session,
            PdfValue value) throws DocumentFailure {
        if (value instanceof PdfIndirectReference) {
            return session.query(InspectObject.version1(
                    ((PdfIndirectReference) value).getReference(),
                    PdfInspectionLimits.of(256, 16L * 1024L * 1024L)));
        }
        return value;
    }

    private static String ascii(byte[] value) {
        return new String(value, StandardCharsets.US_ASCII);
    }

    private static ResourceExtractionLimits limits() {
        return ResourceExtractionLimits.builder()
                .maximumPages(4)
                .maximumPageTreeNodes(32)
                .maximumTraversedResourceValues(8192L)
                .maximumResourceTraversalDepth(32)
                .maximumDecodedPixels(4L * 1024L * 1024L)
                .maximumDecompressedBytes(32L * 1024L * 1024L)
                .maximumReturnedBytes(32L * 1024L * 1024L)
                .build();
    }

    private static final class Observations {
        private final boolean imageSemantics;
        private final boolean colorSemantics;
        private final boolean maskSemantics;
        private final boolean transparencySemantics;
        private final boolean resourceReuse;
        private final boolean preservation;

        Observations(
                boolean imageSemantics,
                boolean colorSemantics,
                boolean maskSemantics,
                boolean transparencySemantics,
                boolean resourceReuse,
                boolean preservation) {
            this.imageSemantics = imageSemantics;
            this.colorSemantics = colorSemantics;
            this.maskSemantics = maskSemantics;
            this.transparencySemantics = transparencySemantics;
            this.resourceReuse = resourceReuse;
            this.preservation = preservation;
        }
    }
}
