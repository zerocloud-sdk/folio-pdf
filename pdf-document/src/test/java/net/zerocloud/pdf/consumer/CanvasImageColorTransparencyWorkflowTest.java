package net.zerocloud.pdf.consumer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.awt.Color;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.DeflaterOutputStream;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;
import net.zerocloud.pdf.DocumentResourceInventory;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.DocumentSession;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentPermissions;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.ImageByteAccess;
import net.zerocloud.pdf.ImageResource;
import net.zerocloud.pdf.PdfName;
import net.zerocloud.pdf.PdfBoolean;
import net.zerocloud.pdf.PdfArray;
import net.zerocloud.pdf.PdfDictionary;
import net.zerocloud.pdf.PdfDictionaryEntry;
import net.zerocloud.pdf.PdfIndirectReference;
import net.zerocloud.pdf.PdfInspectionLimits;
import net.zerocloud.pdf.PdfNumber;
import net.zerocloud.pdf.PdfOutputPolicy;
import net.zerocloud.pdf.PdfStream;
import net.zerocloud.pdf.PdfValue;
import net.zerocloud.pdf.PdfVersion;
import net.zerocloud.pdf.PasswordCredential;
import net.zerocloud.pdf.PasswordSecurityPolicy;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.ResourceExtractionLimits;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.composition.CanvasColor;
import net.zerocloud.pdf.composition.CanvasColorSpace;
import net.zerocloud.pdf.composition.CanvasImage;
import net.zerocloud.pdf.composition.CanvasImageCapabilities;
import net.zerocloud.pdf.composition.CanvasMatrix;
import net.zerocloud.pdf.composition.CanvasMask;
import net.zerocloud.pdf.composition.CanvasProgram;
import net.zerocloud.pdf.composition.CanvasRectangle;
import net.zerocloud.pdf.composition.CanvasResourceLimits;
import net.zerocloud.pdf.composition.CanvasTransparencyGroup;
import net.zerocloud.pdf.composition.CanvasTransparencyState;
import net.zerocloud.pdf.composition.CanvasBlendMode;
import net.zerocloud.pdf.composition.command.DrawCanvas;
import net.zerocloud.pdf.composition.query.InspectCanvasImageCapabilities;
import net.zerocloud.pdf.query.ExtractImagesAndResources;
import net.zerocloud.pdf.query.InspectObject;
import net.zerocloud.pdf.query.PageObjectReference;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Public-workflow coverage for the T18 Canvas extension. */
public final class CanvasImageColorTransparencyWorkflowTest {

    private static final String CAPABILITY =
            "composition.canvas.images-colors-transparency";
    private static final byte[] SENTINEL = new byte[] {71, 72, 73};

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void declarationsAreVersionedImmutableAndCodecSupportIsExplicit()
            throws Exception {
        byte[] mutable = new byte[] {1, 2, 3};
        CanvasImage image = CanvasImage.rawSamples(
                1,
                1,
                8,
                CanvasColorSpace.deviceRgb(),
                mutable);
        mutable[0] = 9;
        assertArrayEquals(new byte[] {1, 2, 3}, image.getBytes().get());
        byte[] exposed = image.getBytes().get();
        exposed[1] = 9;
        assertArrayEquals(new byte[] {1, 2, 3}, image.getBytes().get());

        double[] components = new double[] {0.1d, 0.2d, 0.3d};
        CanvasColor color = CanvasColor.of(
                CanvasColorSpace.deviceRgb(),
                components);
        components[0] = 0.9d;
        assertArrayEquals(
                new double[] {0.1d, 0.2d, 0.3d},
                color.getComponents(),
                0d);

        double[] whitePoint = new double[] {0.9505d, 1d, 1.089d};
        double[] gamma = new double[] {2.2d, 2.2d, 2.2d};
        double[] matrix = new double[] {
            1d, 0d, 0d,
            0d, 1d, 0d,
            0d, 0d, 1d
        };
        CanvasColorSpace calibrated = CanvasColorSpace.calibratedRgb(
                whitePoint,
                new double[] {0d, 0d, 0d},
                gamma,
                matrix);
        whitePoint[0] = 7d;
        gamma[0] = 7d;
        matrix[0] = 7d;
        assertEquals(0.9505d, calibrated.getWhitePoint()[0], 0d);
        assertEquals(2.2d, calibrated.getGamma()[0], 0d);
        assertEquals(1d, calibrated.getMatrix()[0], 0d);
        double[] exposedWhitePoint = calibrated.getWhitePoint();
        exposedWhitePoint[0] = 8d;
        assertEquals(0.9505d, calibrated.getWhitePoint()[0], 0d);

        byte[] profileBytes = new byte[] {4, 5, 6};
        CanvasColorSpace profile = CanvasColorSpace.iccBased(profileBytes);
        profileBytes[0] = 9;
        byte[] exposedProfile = profile.getIccProfileBytes().get();
        exposedProfile[1] = 9;
        assertArrayEquals(
                new byte[] {4, 5, 6},
                profile.getIccProfileBytes().get());

        byte[] maskBytes = new byte[] {11};
        CanvasMask mask = CanvasMask.soft(1, 1, maskBytes);
        maskBytes[0] = 12;
        byte[] exposedMask = mask.getSamples();
        exposedMask[0] = 13;
        assertArrayEquals(new byte[] {11}, mask.getSamples());

        CanvasProgram snapshot = CanvasProgram.version2()
                .setFillColor(color)
                .drawImage(image, CanvasMatrix.IDENTITY)
                .build();
        assertEquals(CanvasProgram.VERSION_2, snapshot.getVersion());
        assertEquals(2, snapshot.getInstructionCount());
        try {
            snapshot.getInstructions().clear();
            throw new AssertionError("Canvas instructions were mutable");
        } catch (UnsupportedOperationException expected) {
            // Immutable view.
        }

        Path target = path("capabilities.pdf");
        WorkflowOutcome<CanvasImageCapabilities> outcome =
                new DocumentWorkflow().execute(
                        WorkflowRequest.create(target, SaveMode.REWRITE),
                        session -> {
                            CanvasImageCapabilities capabilities = session.query(
                                    InspectCanvasImageCapabilities.version1());
                            session.execute(AddBlankPage.INSTANCE);
                            return capabilities;
                        });
        assertEquals(
                CanvasImageCapabilities.Availability.AVAILABLE,
                outcome.getResult().getSupport(CanvasImage.SourceKind.JPEG)
                        .getAvailability());
        assertEquals(
                CanvasImageCapabilities.Handling.PASS_THROUGH,
                outcome.getResult().getSupport(CanvasImage.SourceKind.JPEG)
                        .getHandling());
        assertEquals(
                CanvasImageCapabilities.Availability.AVAILABLE,
                outcome.getResult().getSupport(CanvasImage.SourceKind.TIFF)
                        .getAvailability());
        assertEquals(
                CanvasImageCapabilities.Handling.NORMALIZE_TO_DEVICE_RGB_8,
                outcome.getResult().getSupport(CanvasImage.SourceKind.PNG)
                        .getHandling());
        assertEquals(
                CanvasImageCapabilities.Handling.NORMALIZE_TO_DEVICE_RGB_8,
                outcome.getResult().getSupport(CanvasImage.SourceKind.TIFF)
                        .getHandling());
    }

    @Test
    public void jpegPngAndTiffRoundTripWithDocumentedConversionAndReuse()
            throws Exception {
        BufferedImage rgb = image(false);
        BufferedImage alpha = image(true);
        byte[] jpeg = encode(rgb, "JPEG");
        byte[] png = encode(alpha, "PNG");
        byte[] tiff = encode(rgb, "TIFF");
        CanvasImage jpegImage = CanvasImage.jpeg(jpeg);
        CanvasImage pngImage = CanvasImage.png(png);
        CanvasImage tiffImage = CanvasImage.tiff(tiff);

        CanvasProgram program = CanvasProgram.version2()
                .drawImage(jpegImage, place(10d, 180d))
                .drawImage(jpegImage, place(60d, 180d))
                .drawImage(pngImage, place(110d, 180d))
                .drawImage(pngImage, place(160d, 180d))
                .drawImage(tiffImage, place(210d, 180d))
                .drawImage(tiffImage, place(10d, 120d))
                .build();
        Path target = path("formats.pdf");
        WorkflowOutcome<Void> creation = new DocumentWorkflow().execute(
                WorkflowRequest.create(target, SaveMode.REWRITE),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    session.execute(DrawCanvas.version2(
                            1,
                            program,
                            limits()));
                    return null;
                });
        assertEquals(CAPABILITY, creation.getCapabilityId());
        assertEquals(
                PublicationStatus.COMMITTED,
                creation.getPublicationReceipts().get(0).getStatus());

        new DocumentWorkflow().execute(
                WorkflowRequest.open(target, SaveMode.REWRITE),
                session -> {
                    DocumentResourceInventory inventory = resources(
                            session,
                            ImageByteAccess.ENCODED_AND_DECODED);
                    assertEquals(4, inventory.getImages().size());
                    ImageResource dct = imageWithFilter(
                            inventory.getImages(),
                            "DCTDecode");
                    assertEquals(2, dct.getWidth());
                    assertEquals(2, dct.getHeight());
                    assertEquals(
                            ImageResource.ColorFamily.DEVICE_RGB,
                            dct.getColorSpace().getFamily());
                    assertArrayEquals(
                            jpeg,
                            dct.getEncodedData().getBytes().get());
                    assertFalse(dct.getSoftMask().isPresent());
                    assertTrue(dct.getObjectReference().isPresent());
                    PdfStream dctStream = (PdfStream) session.query(
                            InspectObject.version1(
                                    dct.getObjectReference().get(),
                                    PdfInspectionLimits.of(
                                            64,
                                            1024L * 1024L)));
                    PdfDictionary dctDictionary = dctStream.getDictionary();
                    assertEquals(
                            PdfName.of("DCTDecode"),
                            dctDictionary.get(PdfName.of("Filter")));
                    PdfDictionary decodeParameters = dictionary(
                            session,
                            dctDictionary.get(PdfName.of("DecodeParms")));
                    assertEquals(
                            PdfNumber.of(1),
                            decodeParameters.get(PdfName.of("ColorTransform")));

                    int flateImages = 0;
                    int softMasks = 0;
                    ImageResource pngOwner = null;
                    ImageResource tiffOwner = null;
                    for (ImageResource resource : inventory.getImages()) {
                        if (hasFilter(resource, "FlateDecode")) {
                            flateImages++;
                        }
                        if (resource.getSoftMask().isPresent()) {
                            softMasks++;
                            pngOwner = resource;
                            ImageResource mask = resource.getSoftMask().get()
                                    .getImage().get();
                            assertEquals(2, mask.getWidth());
                            assertEquals(2, mask.getHeight());
                            assertEquals(
                                    ImageResource.ColorFamily.DEVICE_GRAY,
                                    mask.getColorSpace().getFamily());
                            assertArrayEquals(
                                    new byte[] {
                                        (byte) 255, 96,
                                        (byte) 255, (byte) 255
                                    },
                                    mask.getDecodedData().getBytes().get());
                        } else if (!resource.isImageMask()
                                && hasFilter(resource, "FlateDecode")
                                && resource.getColorSpace().getFamily()
                                        == ImageResource.ColorFamily.DEVICE_RGB) {
                            tiffOwner = resource;
                        }
                        assertEquals(Arrays.asList(1), resource.getPageUsage());
                    }
                    assertEquals(3, flateImages);
                    assertEquals(1, softMasks);
                    assertNotNull(pngOwner);
                    assertEquals(2, pngOwner.getWidth());
                    assertEquals(2, pngOwner.getHeight());
                    assertEquals(
                            ImageResource.ColorFamily.DEVICE_RGB,
                            pngOwner.getColorSpace().getFamily());
                    assertArrayEquals(
                            rgbSamples(alpha),
                            pngOwner.getDecodedData().getBytes().get());
                    assertNotNull(tiffOwner);
                    assertEquals(2, tiffOwner.getWidth());
                    assertEquals(2, tiffOwner.getHeight());
                    assertArrayEquals(
                            rgbSamples(rgb),
                            tiffOwner.getDecodedData().getBytes().get());
                    return null;
                });
    }

    @Test
    public void deviceCalibratedIccAlphaAndTransparencyGroupsRoundTrip()
            throws Exception {
        byte[] profile = ICC_Profile.getInstance(ColorSpace.CS_sRGB).getData();
        CanvasColorSpace icc = CanvasColorSpace.iccBased(profile);
        CanvasColorSpace calGray = CanvasColorSpace.calibratedGray(
                new double[] {0.9505d, 1d, 1.089d},
                new double[] {0d, 0d, 0d},
                2.2d);
        CanvasColorSpace calRgb = CanvasColorSpace.calibratedRgb(
                new double[] {0.9505d, 1d, 1.089d},
                new double[] {0d, 0d, 0d},
                new double[] {2.2d, 2.2d, 2.2d},
                new double[] {
                    0.4124d, 0.2126d, 0.0193d,
                    0.3576d, 0.7152d, 0.1192d,
                    0.1805d, 0.0722d, 0.9505d
                });
        CanvasImage iccImage = CanvasImage.rawSamples(
                2,
                2,
                8,
                icc,
                new byte[] {
                    (byte) 240, 20, 30,
                    20, (byte) 220, 40,
                    30, 50, (byte) 235,
                    (byte) 245, (byte) 220, 30
                });
        CanvasProgram groupProgram = CanvasProgram.version2()
                .setFillColor(CanvasColor.of(calGray, 0.55d))
                .moveTo(0d, 0d)
                .lineTo(45d, 0d)
                .lineTo(45d, 45d)
                .lineTo(0d, 45d)
                .closePath()
                .fill(net.zerocloud.pdf.composition.CanvasWindingRule.NONZERO)
                .build();
        CanvasTransparencyGroup group = CanvasTransparencyGroup.version1(
                CanvasRectangle.of(0d, 0d, 45d, 45d),
                CanvasColorSpace.deviceRgb(),
                true,
                false,
                groupProgram);
        CanvasTransparencyState transparency =
                CanvasTransparencyState.version1(
                        0.6d,
                        0.8d,
                        CanvasBlendMode.MULTIPLY);

        CanvasProgram program = CanvasProgram.version2()
                .setFillColor(CanvasColor.rgb(0.9d, 0.15d, 0.1d))
                .moveTo(15d, 15d)
                .lineTo(100d, 15d)
                .lineTo(100d, 55d)
                .lineTo(15d, 55d)
                .closePath()
                .fill(net.zerocloud.pdf.composition.CanvasWindingRule.NONZERO)
                .setStrokeColor(CanvasColor.of(
                        calRgb,
                        0.1d,
                        0.25d,
                        0.85d))
                .moveTo(15d, 70d)
                .lineTo(145d, 70d)
                .stroke()
                .setTransparency(transparency)
                .drawImage(iccImage, place(20d, 100d))
                .drawImage(iccImage, place(70d, 100d))
                .setTransparency(transparency)
                .drawTransparencyGroup(group, CanvasMatrix.of(
                        1d, 0d, 0d, 1d, 140d, 100d))
                .drawTransparencyGroup(group, CanvasMatrix.of(
                        1d, 0d, 0d, 1d, 200d, 100d))
                .build();

        Path target = path("colors-transparency.pdf");
        new DocumentWorkflow().execute(
                WorkflowRequest.create(target, SaveMode.REWRITE),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    session.execute(DrawCanvas.version2(1, program, limits()));
                    return null;
                });

        new DocumentWorkflow().execute(
                WorkflowRequest.open(target, SaveMode.REWRITE),
                session -> {
                    DocumentResourceInventory inventory = resources(
                            session,
                            ImageByteAccess.NONE);
                    assertEquals(1, inventory.getImages().size());
                    ImageResource image = inventory.getImages().get(0);
                    assertEquals(
                            ImageResource.ColorStatus.SUPPORTED,
                            image.getColorSpace().getStatus());
                    assertEquals(
                            ImageResource.ColorFamily.ICC_BASED,
                            image.getColorSpace().getFamily());
                    assertEquals(3, image.getColorComponents().getAsInt());
                    ImageResource.IccProfile observed = image.getColorSpace()
                            .getIccProfile().get();
                    assertTrue(observed.getObjectReference().isPresent());
                    assertEquals(profile.length, observed.getByteLength());
                    assertEquals(sha256(profile), observed.getSha256());

                    PdfDictionary page = inspectDictionary(
                            session,
                            session.query(PageObjectReference.version1(1)));
                    PdfDictionary resources = dictionary(
                            session,
                            page.get(PdfName.of("Resources")));
                    assertEquals(1, dictionary(
                            session,
                            resources.get(PdfName.of("ColorSpace"))).size());
                    PdfDictionary colorSpaces = dictionary(
                            session,
                            resources.get(PdfName.of("ColorSpace")));
                    PdfArray calibrated = (PdfArray) resolve(
                            session,
                            colorSpaces.getEntry(0).getValue());
                    assertEquals(PdfName.of("CalRGB"), calibrated.get(0));
                    PdfDictionary calibratedParameters = dictionary(
                            session,
                            calibrated.get(1));
                    assertPdfNumbers(
                            session,
                            calibratedParameters.get(PdfName.of("WhitePoint")),
                            0.9505d, 1d, 1.089d);
                    assertPdfNumbers(
                            session,
                            calibratedParameters.get(PdfName.of("Gamma")),
                            2.2d, 2.2d, 2.2d);

                    PdfDictionary states = dictionary(
                            session,
                            resources.get(PdfName.of("ExtGState")));
                    assertEquals(1, states.size());
                    PdfDictionary observedState = dictionary(
                            session,
                            states.getEntry(0).getValue());
                    assertEquals(
                            PdfNumber.of(BigDecimal.valueOf(0.6d)),
                            observedState.get(PdfName.of("ca")));
                    assertEquals(
                            PdfNumber.of(BigDecimal.valueOf(0.8d)),
                            observedState.get(PdfName.of("CA")));
                    assertEquals(
                            PdfName.of("Multiply"),
                            observedState.get(PdfName.of("BM")));
                    PdfDictionary xobjects = dictionary(
                            session,
                            resources.get(PdfName.of("XObject")));
                    assertEquals(2, xobjects.size());
                    PdfStream form = form(session, xobjects);
                    PdfDictionary groupDictionary = dictionary(
                            session,
                            form.getDictionary().get(PdfName.of("Group")));
                    assertEquals(
                            PdfName.of("Transparency"),
                            groupDictionary.get(PdfName.of("S")));
                    assertEquals(
                            PdfBoolean.of(true),
                            groupDictionary.get(PdfName.of("I")));
                    assertEquals(
                            PdfBoolean.of(false),
                            groupDictionary.get(PdfName.of("K")));
                    String groupOperators = new String(
                            form.readBytes(),
                            StandardCharsets.US_ASCII);
                    assertTrue(groupOperators.contains("0.55 scn\n"));

                    String operators = pageContent(session, page);
                    assertTrue(operators.contains("0.9 0.15 0.1 rg\n"));
                    assertTrue(operators.contains(" CS\n"));
                    assertTrue(operators.contains("0.1 0.25 0.85 SCN\n"));
                    assertTrue(operators.contains(" gs\n"));
                    assertEquals(4, occurrences(operators, " Do\n"));
                    return null;
                });
    }

    @Test
    public void explicitAndSoftMasksSurviveReopenAndAreReused()
            throws Exception {
        CanvasMask explicit = CanvasMask.explicit(
                2,
                2,
                false,
                new byte[] {(byte) 0x80, (byte) 0x40});
        CanvasMask soft = CanvasMask.soft(
                2,
                2,
                new byte[] {0, 85, (byte) 170, (byte) 255});
        CanvasImage explicitImage = CanvasImage.rawSamples(
                2,
                2,
                8,
                CanvasColorSpace.deviceRgb(),
                new byte[] {
                    (byte) 230, 20, 20,
                    (byte) 230, 20, 20,
                    (byte) 230, 20, 20,
                    (byte) 230, 20, 20
                }).withExplicitMask(explicit);
        CanvasImage softImage = CanvasImage.rawSamples(
                2,
                2,
                8,
                CanvasColorSpace.deviceRgb(),
                new byte[] {
                    20, 30, (byte) 230,
                    20, 30, (byte) 230,
                    20, 30, (byte) 230,
                    20, 30, (byte) 230
                }).withSoftMask(soft);
        CanvasProgram program = CanvasProgram.version2()
                .drawImage(explicitImage, place(20d, 200d))
                .drawImage(explicitImage, place(70d, 200d))
                .drawImage(softImage, place(120d, 200d))
                .drawImage(softImage, place(170d, 200d))
                .build();

        Path target = path("masks.pdf");
        new DocumentWorkflow().execute(
                WorkflowRequest.create(target, SaveMode.REWRITE),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    session.execute(DrawCanvas.version2(1, program, limits()));
                    return null;
                });

        new DocumentWorkflow().execute(
                WorkflowRequest.open(target, SaveMode.REWRITE),
                session -> {
                    List<ImageResource> images = resources(
                            session,
                            ImageByteAccess.DECODED).getImages();
                    assertEquals(4, images.size());
                    int explicitOwners = 0;
                    int softOwners = 0;
                    int imageMasks = 0;
                    for (ImageResource image : images) {
                        if (image.isImageMask()) {
                            imageMasks++;
                            assertEquals(1, image.getBitsPerComponent().getAsInt());
                        }
                        if (image.getExplicitMask().isPresent()) {
                            explicitOwners++;
                            assertEquals(
                                    ImageResource.Mask.Kind.EXPLICIT_IMAGE,
                                    image.getExplicitMask().get().getKind());
                            assertTrue(image.getExplicitMask().get().getImage()
                                    .get().isImageMask());
                        }
                        if (image.getSoftMask().isPresent()) {
                            softOwners++;
                            ImageResource mask = image.getSoftMask().get()
                                    .getImage().get();
                            assertEquals(
                                    ImageResource.Mask.Kind.SOFT_IMAGE,
                                    image.getSoftMask().get().getKind());
                            assertEquals(
                                    ImageResource.ColorFamily.DEVICE_GRAY,
                                    mask.getColorSpace().getFamily());
                            assertArrayEquals(
                                    soft.getSamples(),
                                    mask.getDecodedData().getBytes().get());
                        }
                    }
                    assertEquals(1, imageMasks);
                    assertEquals(1, explicitOwners);
                    assertEquals(1, softOwners);
                    return null;
                });
    }

    @Test
    public void borrowedUnfilteredFlateAndDctImagesKeepIdentityAndMetadata()
            throws Exception {
        Path source = path("existing-source.pdf");
        Path target = path("existing-output.pdf");
        writeExistingImageFixture(source);

        new DocumentWorkflow().execute(
                rewrite(source, target),
                session -> {
                    List<ImageResource> before = resources(
                            session,
                            ImageByteAccess.NONE).getImages();
                    assertEquals(4, before.size());
                    ImageResource unfiltered = unfilteredOwner(before);
                    ImageResource flate = imageWithFilter(before, "FlateDecode");
                    ImageResource dct = imageWithFilter(before, "DCTDecode");
                    Set<net.zerocloud.pdf.ObjectReference> identities =
                            references(before);

                    CanvasProgram program = CanvasProgram.version2()
                            .drawImage(
                                    CanvasImage.existing(unfiltered
                                            .getObjectReference().get()),
                                    place(15d, 200d))
                            .drawImage(
                                    CanvasImage.existing(unfiltered
                                            .getObjectReference().get()),
                                    place(60d, 200d))
                            .drawImage(
                                    CanvasImage.existing(flate
                                            .getObjectReference().get()),
                                    place(105d, 200d))
                            .drawImage(
                                    CanvasImage.existing(flate
                                            .getObjectReference().get()),
                                    place(150d, 200d))
                            .drawImage(
                                    CanvasImage.existing(dct
                                            .getObjectReference().get()),
                                    place(195d, 200d))
                            .drawImage(
                                    CanvasImage.existing(dct
                                            .getObjectReference().get()),
                                    place(240d, 200d))
                            .build();
                    session.execute(DrawCanvas.version2(1, program, limits()));
                    assertEquals(
                            identities,
                            references(resources(
                                    session,
                                    ImageByteAccess.NONE).getImages()));
                    return null;
                });
        new DocumentWorkflow().execute(
                WorkflowRequest.open(target, SaveMode.REWRITE),
                session -> {
                    List<ImageResource> images = resources(
                            session,
                            ImageByteAccess.NONE).getImages();
                    assertEquals(4, images.size());
                    ImageResource unfiltered = unfilteredOwner(images);
                    assertEquals(
                            ImageResource.ColorFamily.DEVICE_RGB,
                            unfiltered.getColorSpace().getFamily());
                    assertTrue(unfiltered.getExplicitMask().isPresent());
                    assertTrue(unfiltered.getExplicitMask().get().getImage()
                            .get().isImageMask());
                    assertEquals(
                            ImageResource.ColorFamily.DEVICE_RGB,
                            imageWithFilter(images, "FlateDecode")
                                    .getColorSpace().getFamily());
                    assertEquals(
                            ImageResource.ColorFamily.DEVICE_RGB,
                            imageWithFilter(images, "DCTDecode")
                                    .getColorSpace().getFamily());

                    PdfDictionary page = inspectDictionary(
                            session,
                            session.query(PageObjectReference.version1(1)));
                    assertEquals(
                            6,
                            occurrences(pageContent(session, page), " Do\n"));
                    PdfDictionary pageResources = dictionary(
                            session,
                            page.get(PdfName.of("Resources")));
                    assertEquals(
                            3,
                            dictionary(session, pageResources.get(
                                    PdfName.of("XObject"))).size());
                    return null;
                });
    }

    @Test
    public void boundedRepeatedPlacementDoesNotGrowResourceCounts()
            throws Exception {
        CanvasImage image = CanvasImage.rawSamples(
                1,
                1,
                8,
                CanvasColorSpace.deviceRgb(),
                new byte[] {(byte) 240, 30, 20});
        CanvasProgram.Builder builder = CanvasProgram.version2();
        for (int placement = 0; placement < 128; placement++) {
            builder.drawImage(
                    image,
                    CanvasMatrix.of(
                            2d,
                            0d,
                            0d,
                            2d,
                            10d + placement % 16 * 4d,
                            10d + placement / 16 * 4d));
        }
        Path target = path("bounded-reuse.pdf");
        new DocumentWorkflow().execute(
                WorkflowRequest.create(target, SaveMode.REWRITE),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    session.execute(DrawCanvas.version2(
                            1,
                            builder.build(),
                            bounded(
                                    0L,
                                    1L,
                                    3L,
                                    0L,
                                    0L,
                                    32L * 1024L,
                                    1,
                                    0)));
                    return null;
                });
        new DocumentWorkflow().execute(
                WorkflowRequest.open(target, SaveMode.REWRITE),
                session -> {
                    assertEquals(
                            1,
                            resources(session, ImageByteAccess.NONE)
                                    .getImages().size());
                    PdfDictionary page = inspectDictionary(
                            session,
                            session.query(PageObjectReference.version1(1)));
                    assertEquals(
                            128,
                            occurrences(pageContent(session, page), " Do\n"));
                    return null;
                });
    }

    @Test
    public void everyTicketLimitHasExactBoundaryAndFirstExcessCoverage()
            throws Exception {
        CanvasImage raw = CanvasImage.rawSamples(
                1,
                1,
                8,
                CanvasColorSpace.deviceRgb(),
                new byte[] {10, 20, 30});
        CanvasProgram rawProgram = CanvasProgram.version2()
                .drawImage(raw, CanvasMatrix.IDENTITY)
                .build();
        assertDrawSucceeds("pixels-exact", rawProgram,
                bounded(1024L, 1L, 3L, 1024L, 1024L, 1024L, 8, 4));
        assertLimitFailure("pixels-excess", rawProgram,
                bounded(1024L, 0L, 3L, 1024L, 1024L, 1024L, 8, 4));
        assertDrawSucceeds("decoded-exact", rawProgram,
                bounded(1024L, 1L, 3L, 1024L, 1024L, 1024L, 8, 4));
        assertLimitFailure("decoded-excess", rawProgram,
                bounded(1024L, 1L, 2L, 1024L, 1024L, 1024L, 8, 4));
        assertDrawSucceeds("resources-exact", rawProgram,
                bounded(1024L, 1L, 3L, 1024L, 1024L, 1024L, 1, 4));
        assertLimitFailure("resources-excess", rawProgram,
                bounded(1024L, 1L, 3L, 1024L, 1024L, 1024L, 0, 4));

        byte[] jpeg = encode(image(false), "JPEG");
        CanvasProgram jpegProgram = CanvasProgram.version2()
                .drawImage(CanvasImage.jpeg(jpeg), CanvasMatrix.IDENTITY)
                .build();
        assertDrawSucceeds("encoded-exact", jpegProgram,
                bounded(jpeg.length, 4L, 12L, 1024L, 1024L, 1024L, 8, 4));
        assertLimitFailure("encoded-excess", jpegProgram,
                bounded(jpeg.length - 1L, 4L, 12L,
                        1024L, 1024L, 1024L, 8, 4));

        byte[] profile = ICC_Profile.getInstance(ColorSpace.CS_sRGB).getData();
        CanvasProgram profileProgram = CanvasProgram.version2()
                .drawImage(CanvasImage.rawSamples(
                        1,
                        1,
                        8,
                        CanvasColorSpace.iccBased(profile),
                        new byte[] {10, 20, 30}), CanvasMatrix.IDENTITY)
                .build();
        assertDrawSucceeds("profile-exact", profileProgram,
                bounded(1024L, 1L, 3L, profile.length,
                        1024L, 1024L, 2, 4));
        assertLimitFailure("profile-excess", profileProgram,
                bounded(1024L, 1L, 3L, profile.length - 1L,
                        1024L, 1024L, 2, 4));

        CanvasProgram maskProgram = CanvasProgram.version2()
                .drawImage(raw.withSoftMask(CanvasMask.soft(
                        1,
                        1,
                        new byte[] {(byte) 255})), CanvasMatrix.IDENTITY)
                .build();
        assertDrawSucceeds("mask-exact", maskProgram,
                bounded(1024L, 1L, 3L, 1024L, 1L, 1024L, 2, 4));
        assertLimitFailure("mask-excess", maskProgram,
                bounded(1024L, 1L, 3L, 1024L, 0L, 1024L, 2, 4));

        CanvasProgram generated = CanvasProgram.version2()
                .setFillColor(CanvasColor.rgb(1d, 0d, 0d))
                .moveTo(0d, 0d)
                .lineTo(1d, 1d)
                .stroke()
                .build();
        assertDrawSucceeds("generated-exact", generated,
                bounded(0L, 0L, 0L, 0L, 0L, 27L, 0, 0));
        assertLimitFailure("generated-excess", generated,
                bounded(0L, 0L, 0L, 0L, 0L, 26L, 0, 0));

        CanvasTransparencyGroup oneLevel = CanvasTransparencyGroup.version1(
                CanvasRectangle.of(0d, 0d, 2d, 2d),
                CanvasColorSpace.deviceRgb(),
                false,
                false,
                generated);
        CanvasProgram groupProgram = CanvasProgram.version2()
                .drawTransparencyGroup(oneLevel, CanvasMatrix.IDENTITY)
                .build();
        assertDrawSucceeds("group-depth-exact", groupProgram,
                bounded(0L, 0L, 0L, 0L, 0L, 4096L, 1, 1));
        assertLimitFailure("group-depth-excess", groupProgram,
                bounded(0L, 0L, 0L, 0L, 0L, 4096L, 1, 0));
    }

    @Test
    public void depthLimitsCoverDeepAndSharedGroupPathsWithoutStackOverflow()
            throws Exception {
        CanvasProgram leafProgram = CanvasProgram.version2()
                .setFillColor(CanvasColor.rgb(1d, 0d, 0d))
                .build();
        CanvasTransparencyGroup leaf = CanvasTransparencyGroup.version1(
                CanvasRectangle.of(0d, 0d, 1d, 1d),
                CanvasColorSpace.deviceRgb(),
                false,
                false,
                leafProgram);
        CanvasTransparencyGroup parent = CanvasTransparencyGroup.version1(
                CanvasRectangle.of(0d, 0d, 1d, 1d),
                CanvasColorSpace.deviceRgb(),
                false,
                false,
                CanvasProgram.version2()
                        .drawTransparencyGroup(leaf, CanvasMatrix.IDENTITY)
                        .build());
        CanvasProgram sharedAtDifferentDepths = CanvasProgram.version2()
                .drawTransparencyGroup(leaf, CanvasMatrix.IDENTITY)
                .drawTransparencyGroup(parent, CanvasMatrix.IDENTITY)
                .build();
        assertDrawSucceeds(
                "shared-group-depth-exact",
                sharedAtDifferentDepths,
                bounded(0L, 0L, 0L, 0L, 0L, 4096L, 2, 2));
        assertLimitFailure(
                "shared-group-depth-excess",
                sharedAtDifferentDepths,
                bounded(0L, 0L, 0L, 0L, 0L, 4096L, 2, 1));

        CanvasProgram deeplyNested = leafProgram;
        for (int depth = 0; depth < 5000; depth++) {
            CanvasTransparencyGroup nested =
                    CanvasTransparencyGroup.version1(
                            CanvasRectangle.of(0d, 0d, 1d, 1d),
                            CanvasColorSpace.deviceRgb(),
                            false,
                            false,
                            deeplyNested);
            deeplyNested = CanvasProgram.version2()
                    .drawTransparencyGroup(nested, CanvasMatrix.IDENTITY)
                    .build();
        }
        assertLimitFailure(
                "hard-group-depth-excess",
                deeplyNested,
                bounded(
                        0L,
                        0L,
                        0L,
                        0L,
                        0L,
                        1024L * 1024L,
                        6000,
                        CanvasResourceLimits
                                .MAXIMUM_TRANSPARENCY_GROUP_DEPTH_VERSION_1));
    }

    @Test
    public void generatedContentIncludesPreservationWrappersAndHardCeiling()
            throws Exception {
        CanvasProgram generated = CanvasProgram.version2()
                .setFillColor(CanvasColor.rgb(1d, 0d, 0d))
                .moveTo(0d, 0d)
                .lineTo(1d, 1d)
                .stroke()
                .build();
        Path source = path("generated-wrapper-source.pdf");
        writePdf(
                source,
                ascii("<< /Type /Catalog /Pages 2 0 R >>"),
                ascii("<< /Type /Pages /Count 1 /Kids [3 0 R] >>"),
                ascii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 10 10] "
                        + "/Resources << >> /Contents 4 0 R >>"),
                stream(ascii("q\nQ\n"), ""));

        Path exact = path("generated-wrapper-exact.pdf");
        new DocumentWorkflow().execute(
                rewrite(source, exact),
                session -> {
                    session.execute(DrawCanvas.version2(
                            1,
                            generated,
                            bounded(0L, 0L, 0L, 0L, 0L, 33L, 0, 0)));
                    return null;
                });
        Path excess = path("generated-wrapper-excess.pdf");
        Files.write(excess, SENTINEL);
        try {
            new DocumentWorkflow().execute(
                    rewrite(source, excess),
                    session -> {
                        session.execute(DrawCanvas.version2(
                                1,
                                generated,
                                bounded(0L, 0L, 0L, 0L, 0L, 32L, 0, 0)));
                        return null;
                    });
            fail("Expected preservation-wrapper byte limit");
        } catch (DocumentFailure failure) {
            assertFailure(
                    failure,
                    DocumentFailureCode.CANVAS_RESOURCE_LIMIT_EXCEEDED,
                    "The Canvas resource limit was exceeded.");
        }
        assertArrayEquals(SENTINEL, Files.readAllBytes(excess));

        CanvasProgram.Builder oversized = CanvasProgram.version2();
        CanvasMatrix verbose = CanvasMatrix.of(
                0.12345678901234568d,
                0.23456789012345678d,
                0.34567890123456789d,
                0.45678901234567891d,
                0.56789012345678912d,
                0.67890123456789123d);
        for (int instruction = 0; instruction < 10000; instruction++) {
            oversized.transform(verbose);
        }
        assertLimitFailure(
                "hard-generated-content-excess",
                oversized.build(),
                bounded(0L, 0L, 0L, 0L, 0L, Long.MAX_VALUE, 0, 0));
    }

    @Test
    public void badAndIncompatibleIccProfilesFailSafelyBeforePublication()
            throws Exception {
        byte[] valid = ICC_Profile.getInstance(ColorSpace.CS_sRGB).getData();
        byte[] malformed = valid.clone();
        malformed[36] = 0;
        assertDrawFailure(
                "malformed-icc",
                imageProgram(CanvasColorSpace.iccBased(malformed)),
                limits(),
                DocumentFailureCode.CANVAS_GRAPHICS_INVALID,
                "The Canvas color or transparency declaration is invalid.");

        byte[] incompatible = valid.clone();
        incompatible[16] = 'X';
        incompatible[17] = 'Y';
        incompatible[18] = 'Z';
        incompatible[19] = ' ';
        assertDrawFailure(
                "incompatible-icc",
                imageProgram(CanvasColorSpace.iccBased(incompatible)),
                limits(),
                DocumentFailureCode.CANVAS_RESOURCE_UNSUPPORTED,
                "The Canvas image or color resource is unsupported.");
    }

    @Test
    public void absentOptionalTiffCodecIsReportedAndNeverPublished()
            throws Exception {
        byte[] tiff = encode(image(false), "TIFF");
        IIORegistry registry = IIORegistry.getDefaultInstance();
        synchronized (IIORegistry.class) {
            List<ImageReaderSpi> removed = removeTiffProviders(registry);
            try {
                Path capabilityTarget = path("no-tiff-capability.pdf");
                CanvasImageCapabilities capabilities =
                        new DocumentWorkflow().execute(
                                WorkflowRequest.create(
                                        capabilityTarget,
                                        SaveMode.REWRITE),
                                session -> {
                                    CanvasImageCapabilities result = session.query(
                                            InspectCanvasImageCapabilities
                                                    .version1());
                                    session.execute(AddBlankPage.INSTANCE);
                                    return result;
                                }).getResult();
                assertEquals(
                        CanvasImageCapabilities.Availability
                                .OPTIONAL_CODEC_UNAVAILABLE,
                        capabilities.getSupport(CanvasImage.SourceKind.TIFF)
                                .getAvailability());

                assertDrawFailure(
                        "no-tiff-publication",
                        CanvasProgram.version2()
                                .drawImage(
                                        CanvasImage.tiff(tiff),
                                        CanvasMatrix.IDENTITY)
                                .build(),
                        limits(),
                        DocumentFailureCode.CANVAS_IMAGE_CODEC_UNAVAILABLE,
                        "The optional Canvas Image codec is unavailable.");
            } finally {
                for (ImageReaderSpi provider : removed) {
                    registry.registerServiceProvider(provider);
                }
            }
        }
    }

    @Test
    public void imageIoProviderFailuresStayCheckedAndCleanupCannotReplaceSuccess()
            throws Exception {
        byte[] tiff = new byte[] {'I', 'I', 42, 0, 8, 0, 0, 0};
        IIORegistry registry = IIORegistry.getDefaultInstance();
        synchronized (IIORegistry.class) {
            List<ImageReaderSpi> removed = removeTiffProviders(registry);
            ControlledTiffReaderSpi provider = null;
            try {
                provider = new ControlledTiffReaderSpi(true, false, false);
                registry.registerServiceProvider(provider);
                assertDrawFailure(
                        "tiff-provider-selection-failure",
                        CanvasProgram.version2()
                                .drawImage(
                                        CanvasImage.tiff(tiff),
                                        CanvasMatrix.IDENTITY)
                                .build(),
                        limits(),
                        DocumentFailureCode.CANVAS_IMAGE_INVALID,
                        "The Canvas Image is invalid.");

                registry.deregisterServiceProvider(provider);
                provider = new ControlledTiffReaderSpi(false, false, true);
                registry.registerServiceProvider(provider);
                assertDrawSucceeds(
                        "tiff-provider-cleanup-failure",
                        CanvasProgram.version2()
                                .drawImage(
                                        CanvasImage.tiff(tiff),
                                        CanvasMatrix.IDENTITY)
                                .build(),
                        limits());

                registry.deregisterServiceProvider(provider);
                provider = new ControlledTiffReaderSpi(false, true, true);
                registry.registerServiceProvider(provider);
                assertDrawFailure(
                        "tiff-provider-read-and-cleanup-failure",
                        CanvasProgram.version2()
                                .drawImage(
                                        CanvasImage.tiff(tiff),
                                        CanvasMatrix.IDENTITY)
                                .build(),
                        limits(),
                        DocumentFailureCode.CANVAS_IMAGE_INVALID,
                        "The Canvas Image is invalid.");
            } finally {
                if (provider != null) {
                    registry.deregisterServiceProvider(provider);
                }
                for (ImageReaderSpi original : removed) {
                    registry.registerServiceProvider(original);
                }
            }
        }
    }

    @Test
    public void invalidDeclarationsAndUnsafePreservationFailAtomically()
            throws Exception {
        assertDrawFailure(
                "invalid-jpeg",
                CanvasProgram.version2()
                        .drawImage(
                                CanvasImage.jpeg(new byte[] {1, 2, 3}),
                                CanvasMatrix.IDENTITY)
                        .build(),
                limits(),
                DocumentFailureCode.CANVAS_IMAGE_INVALID,
                "The Canvas Image is invalid.");
        assertDrawFailure(
                "invalid-samples",
                CanvasProgram.version2()
                        .drawImage(CanvasImage.rawSamples(
                                2,
                                2,
                                8,
                                CanvasColorSpace.deviceRgb(),
                                new byte[] {1, 2, 3}), CanvasMatrix.IDENTITY)
                        .build(),
                limits(),
                DocumentFailureCode.CANVAS_IMAGE_INVALID,
                "The Canvas Image is invalid.");
        CanvasImage bothMasks = CanvasImage.rawSamples(
                1,
                1,
                8,
                CanvasColorSpace.deviceRgb(),
                new byte[] {1, 2, 3})
                .withExplicitMask(CanvasMask.explicit(
                        1, 1, false, new byte[] {(byte) 0x80}))
                .withSoftMask(CanvasMask.soft(
                        1, 1, new byte[] {(byte) 0xff}));
        assertDrawFailure(
                "conflicting-masks",
                CanvasProgram.version2()
                        .drawImage(bothMasks, CanvasMatrix.IDENTITY)
                        .build(),
                limits(),
                DocumentFailureCode.CANVAS_IMAGE_INVALID,
                "The Canvas Image is invalid.");
        assertDrawFailure(
                "invalid-alpha",
                CanvasProgram.version2()
                        .setTransparency(CanvasTransparencyState.version1(
                                1.01d,
                                1d,
                                CanvasBlendMode.NORMAL))
                        .build(),
                limits(),
                DocumentFailureCode.CANVAS_GRAPHICS_INVALID,
                "The Canvas color or transparency declaration is invalid.");
        assertDrawFailure(
                "version-mismatch",
                CanvasProgram.version1()
                        .moveTo(0d, 0d)
                        .lineTo(1d, 1d)
                        .stroke()
                        .build(),
                limits(),
                DocumentFailureCode.CANVAS_PROGRAM_INVALID,
                "The Canvas Program is invalid.");

        Path unsafe = path("unsafe-source.pdf");
        Path target = path("unsafe-target.pdf");
        writePdf(
                unsafe,
                ascii("<< /Type /Catalog /Pages 2 0 R >>"),
                ascii("<< /Type /Pages /Count 1 /Kids [3 0 R] >>"),
                ascii("<< /Type /Page /Parent 2 0 R "
                        + "/MediaBox [0 0 300 300] /Resources << >> "
                        + "/Contents 4 0 R >>"),
                stream(ascii("q\n"), ""));
        Files.write(target, SENTINEL);
        try {
            new DocumentWorkflow().execute(
                    rewrite(unsafe, target),
                    session -> {
                        session.execute(DrawCanvas.version2(
                                1,
                                CanvasProgram.version2()
                                        .setFillColor(CanvasColor.gray(0.5d))
                                        .build(),
                                limits()));
                        return null;
                    });
            fail("Expected preservation rejection");
        } catch (DocumentFailure failure) {
            assertFailure(
                    failure,
                    DocumentFailureCode.CANVAS_PRESERVATION_UNSUPPORTED,
                    "The page content or resources cannot be preserved safely for Canvas drawing.");
        }
        assertArrayEquals(SENTINEL, Files.readAllBytes(target));
    }

    @Test
    public void rewriteIncrementalSignatureAndPasswordPoliciesRemainAtomic()
            throws Exception {
        Path source = path("incremental-base.pdf");
        new DocumentWorkflow().execute(
                WorkflowRequest.create(source, SaveMode.REWRITE),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    return null;
                });
        byte[] original = Files.readAllBytes(source);
        Path incremental = path("incremental-images.pdf");
        WorkflowOutcome<Void> incrementalOutcome =
                new DocumentWorkflow().execute(
                        WorkflowRequest.builder()
                                .source("input", DocumentSource.path(source))
                                .primarySource("input")
                                .target("output", PublicationTarget.path(
                                        incremental))
                                .saveMode(SaveMode.INCREMENTAL)
                                .build(),
                        session -> {
                            session.execute(DrawCanvas.version2(
                                    1,
                                    imageProgram(CanvasColorSpace.deviceRgb()),
                                    limits()));
                            return null;
                        });
        assertEquals(CAPABILITY, incrementalOutcome.getCapabilityId());
        byte[] appended = Files.readAllBytes(incremental);
        assertTrue(appended.length > original.length);
        assertArrayEquals(original, Arrays.copyOf(appended, original.length));

        Path signed = path("signed-t18.pdf");
        Path signedTarget = path("signed-t18-target.pdf");
        Files.write(signed, ProjectOwnedSignatureFixtures
                .ordinaryApprovalSignature());
        Files.write(signedTarget, SENTINEL);
        try {
            new DocumentWorkflow().execute(
                    WorkflowRequest.builder()
                            .source("input", DocumentSource.path(signed))
                            .primarySource("input")
                            .target("output", PublicationTarget.path(
                                    signedTarget))
                            .saveMode(SaveMode.INCREMENTAL)
                            .build(),
                    session -> {
                        session.execute(DrawCanvas.version2(
                                1,
                                imageProgram(CanvasColorSpace.deviceRgb()),
                                limits()));
                        return null;
                    });
            fail("Expected Existing Signature rejection");
        } catch (DocumentFailure failure) {
            assertFailure(
                    failure,
                    DocumentFailureCode.SIGNATURE_POLICY_REJECTED,
                    "The Existing Signature policy does not permit Canvas drawing.");
        }
        assertArrayEquals(SENTINEL, Files.readAllBytes(signedTarget));

        PasswordCredential owner = PasswordCredential.of(
                new char[] {'o', 'w', 'n', 'e', 'r'});
        PasswordCredential user = PasswordCredential.of(
                new char[] {'u', 's', 'e', 'r'});
        try {
            Path protectedSource = path("protected-t18.pdf");
            PasswordSecurityPolicy security = PasswordSecurityPolicy.builder(
                            owner,
                            user)
                    .permissions(DocumentPermissions.builder().build())
                    .build();
            new DocumentWorkflow().execute(
                    WorkflowRequest.builder()
                            .target("output", PublicationTarget.path(
                                    protectedSource))
                            .saveMode(SaveMode.REWRITE)
                            .outputPolicy(PdfOutputPolicy
                                    .version(PdfVersion.PDF_1_7)
                                    .withPasswordSecurity(security))
                            .build(),
                    session -> {
                        session.execute(AddBlankPage.INSTANCE);
                        return null;
                    });
            Path protectedTarget = path("protected-t18-target.pdf");
            Files.write(protectedTarget, SENTINEL);
            try {
                new DocumentWorkflow().execute(
                        WorkflowRequest.builder()
                                .source("input", DocumentSource
                                        .path(protectedSource)
                                        .withCredential(user))
                                .primarySource("input")
                                .target("output", PublicationTarget.path(
                                        protectedTarget))
                                .saveMode(SaveMode.INCREMENTAL)
                                .build(),
                        session -> {
                            session.execute(DrawCanvas.version2(
                                    1,
                                    imageProgram(
                                            CanvasColorSpace.deviceRgb()),
                                    limits()));
                            return null;
                        });
                fail("Expected password permission rejection");
            } catch (DocumentFailure failure) {
                assertFailure(
                        failure,
                        DocumentFailureCode.DOCUMENT_PERMISSION_DENIED,
                        "The Source credential does not authorize Canvas drawing.");
            }
            assertArrayEquals(SENTINEL, Files.readAllBytes(protectedTarget));
        } finally {
            owner.close();
            user.close();
        }
    }

    private static BufferedImage image(boolean alpha) {
        BufferedImage image = new BufferedImage(
                2,
                2,
                alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, new Color(235, 40, 35, 255).getRGB());
        image.setRGB(1, 0, new Color(40, 210, 60, alpha ? 96 : 255).getRGB());
        image.setRGB(0, 1, new Color(30, 80, 230, 255).getRGB());
        image.setRGB(1, 1, new Color(245, 220, 35, 255).getRGB());
        return image;
    }

    private static byte[] encode(BufferedImage image, String format)
            throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue("No ImageIO writer for " + format,
                ImageIO.write(image, format, output));
        byte[] result = output.toByteArray();
        assertTrue(result.length > 0);
        return result;
    }

    private static boolean supportsFormat(
            ImageReaderSpi provider,
            String expected) {
        for (String name : provider.getFormatNames()) {
            if (expected.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private static List<ImageReaderSpi> removeTiffProviders(
            IIORegistry registry) {
        List<ImageReaderSpi> removed = new ArrayList<ImageReaderSpi>();
        Iterator<ImageReaderSpi> providers = registry.getServiceProviders(
                ImageReaderSpi.class,
                true);
        while (providers.hasNext()) {
            ImageReaderSpi provider = providers.next();
            if (supportsFormat(provider, "TIFF")) {
                removed.add(provider);
            }
        }
        for (ImageReaderSpi provider : removed) {
            registry.deregisterServiceProvider(provider);
        }
        return removed;
    }

    private static byte[] rgbSamples(BufferedImage image) {
        byte[] result = new byte[image.getWidth() * image.getHeight() * 3];
        int offset = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getRGB(x, y);
                result[offset++] = (byte) ((pixel >>> 16) & 0xff);
                result[offset++] = (byte) ((pixel >>> 8) & 0xff);
                result[offset++] = (byte) (pixel & 0xff);
            }
        }
        return result;
    }

    private static CanvasMatrix place(double x, double y) {
        return CanvasMatrix.of(40d, 0d, 0d, 40d, x, y);
    }

    private static CanvasResourceLimits limits() {
        return CanvasResourceLimits.builder()
                .maximumEncodedImageBytes(16L * 1024L * 1024L)
                .maximumDecodedImagePixels(1024L * 1024L)
                .maximumDecodedImageBytes(32L * 1024L * 1024L)
                .maximumIccProfileBytes(4L * 1024L * 1024L)
                .maximumMaskBytes(4L * 1024L * 1024L)
                .maximumGeneratedContentBytes(1024L * 1024L)
                .maximumResourceDeclarations(256)
                .maximumTransparencyGroupDepth(8)
                .build();
    }

    private static CanvasResourceLimits bounded(
            long encoded,
            long pixels,
            long decoded,
            long profile,
            long masks,
            long generated,
            int resources,
            int depth) {
        return CanvasResourceLimits.builder()
                .maximumEncodedImageBytes(encoded)
                .maximumDecodedImagePixels(pixels)
                .maximumDecodedImageBytes(decoded)
                .maximumIccProfileBytes(profile)
                .maximumMaskBytes(masks)
                .maximumGeneratedContentBytes(generated)
                .maximumResourceDeclarations(resources)
                .maximumTransparencyGroupDepth(depth)
                .build();
    }

    private static CanvasProgram imageProgram(CanvasColorSpace colorSpace) {
        return CanvasProgram.version2()
                .drawImage(CanvasImage.rawSamples(
                        1,
                        1,
                        8,
                        colorSpace,
                        new byte[] {10, 20, 30}), CanvasMatrix.IDENTITY)
                .build();
    }

    private void assertDrawSucceeds(
            String name,
            CanvasProgram program,
            CanvasResourceLimits resourceLimits) throws Exception {
        Path target = path(name + ".pdf");
        WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(
                WorkflowRequest.create(target, SaveMode.REWRITE),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    session.execute(DrawCanvas.version2(
                            1,
                            program,
                            resourceLimits));
                    return null;
                });
        assertEquals(CAPABILITY, outcome.getCapabilityId());
        assertEquals(
                PublicationStatus.COMMITTED,
                outcome.getPublicationReceipts().get(0).getStatus());
    }

    private void assertLimitFailure(
            String name,
            CanvasProgram program,
            CanvasResourceLimits resourceLimits) throws Exception {
        assertDrawFailure(
                name,
                program,
                resourceLimits,
                DocumentFailureCode.CANVAS_RESOURCE_LIMIT_EXCEEDED,
                "The Canvas resource limit was exceeded.");
    }

    private void assertDrawFailure(
            String name,
            CanvasProgram program,
            CanvasResourceLimits resourceLimits,
            DocumentFailureCode code,
            String diagnostic) throws Exception {
        Path target = path(name + ".pdf");
        Files.write(target, SENTINEL);
        try {
            new DocumentWorkflow().execute(
                    WorkflowRequest.create(target, SaveMode.REWRITE),
                    session -> {
                        session.execute(AddBlankPage.INSTANCE);
                        session.execute(DrawCanvas.version2(
                                1,
                                program,
                                resourceLimits));
                        return null;
                    });
            fail("Expected Canvas failure for " + name);
        } catch (DocumentFailure failure) {
            assertFailure(failure, code, diagnostic);
            assertEquals(1, failure.getPublicationReceipts().size());
            assertEquals(
                    PublicationStatus.NOT_ATTEMPTED,
                    failure.getPublicationReceipts().get(0).getStatus());
            assertFalse(failure.getDiagnostic().contains("/"));
            assertFalse(failure.getDiagnostic().contains("org.apache"));
        }
        assertArrayEquals(SENTINEL, Files.readAllBytes(target));
    }

    private static void assertFailure(
            DocumentFailure failure,
            DocumentFailureCode code,
            String diagnostic) {
        assertEquals(code, failure.getCode());
        assertEquals(CAPABILITY, failure.getCapabilityId());
        assertEquals(diagnostic, failure.getDiagnostic());
        assertFalse(failure.getDiagnostic().contains("/"));
        assertFalse(failure.getDiagnostic().contains("org.apache"));
    }

    private static DocumentResourceInventory resources(
            DocumentSession session,
            ImageByteAccess access) throws DocumentFailure {
        return session.query(ExtractImagesAndResources.version1(
                ResourceExtractionLimits.builder()
                        .maximumPages(8)
                        .maximumPageTreeNodes(64)
                        .maximumTraversedResourceValues(8192L)
                        .maximumResourceTraversalDepth(32)
                        .maximumDecodedPixels(4L * 1024L * 1024L)
                        .maximumDecompressedBytes(32L * 1024L * 1024L)
                        .maximumReturnedBytes(32L * 1024L * 1024L)
                        .build(),
                access));
    }

    private static ImageResource imageWithFilter(
            List<ImageResource> images,
            String filter) {
        for (ImageResource image : images) {
            if (hasFilter(image, filter)) {
                return image;
            }
        }
        assertNotNull("Missing image with filter " + filter, null);
        return null;
    }

    private static boolean hasFilter(ImageResource image, String filter) {
        for (ImageResource.Filter declared : image.getFilters()) {
            if (PdfName.of(filter).equals(declared.getName())) {
                return true;
            }
        }
        return false;
    }

    private static ImageResource unfilteredOwner(List<ImageResource> images) {
        for (ImageResource image : images) {
            if (!image.isImageMask()
                    && image.getFilters().isEmpty()
                    && image.getExplicitMask().isPresent()) {
                return image;
            }
        }
        throw new AssertionError("Missing unfiltered image owner");
    }

    private static Set<net.zerocloud.pdf.ObjectReference> references(
            List<ImageResource> images) {
        Set<net.zerocloud.pdf.ObjectReference> result =
                new HashSet<net.zerocloud.pdf.ObjectReference>();
        for (ImageResource image : images) {
            assertTrue(image.getObjectReference().isPresent());
            result.add(image.getObjectReference().get());
        }
        return result;
    }

    private static PdfDictionary inspectDictionary(
            DocumentSession session,
            net.zerocloud.pdf.ObjectReference reference)
            throws DocumentFailure {
        return (PdfDictionary) session.query(InspectObject.version1(
                reference,
                PdfInspectionLimits.of(512, 32L * 1024L * 1024L)));
    }

    private static PdfValue resolve(DocumentSession session, PdfValue value)
            throws DocumentFailure {
        if (value instanceof PdfIndirectReference) {
            return session.query(InspectObject.version1(
                    ((PdfIndirectReference) value).getReference(),
                    PdfInspectionLimits.of(512, 32L * 1024L * 1024L)));
        }
        return value;
    }

    private static PdfDictionary dictionary(
            DocumentSession session,
            PdfValue value) throws DocumentFailure {
        return (PdfDictionary) resolve(session, value);
    }

    private static void assertPdfNumbers(
            DocumentSession session,
            PdfValue value,
            double... expected) throws DocumentFailure {
        PdfArray numbers = (PdfArray) resolve(session, value);
        assertEquals(expected.length, numbers.size());
        for (int index = 0; index < expected.length; index++) {
            assertEquals(
                    PdfNumber.of(BigDecimal.valueOf(expected[index])),
                    numbers.get(index));
        }
    }

    private static PdfStream form(
            DocumentSession session,
            PdfDictionary xobjects) throws DocumentFailure {
        for (int index = 0; index < xobjects.size(); index++) {
            PdfDictionaryEntry entry = xobjects.getEntry(index);
            PdfValue value = resolve(session, entry.getValue());
            if (value instanceof PdfStream
                    && PdfName.of("Form").equals(((PdfStream) value)
                            .getDictionary().get(PdfName.of("Subtype")))) {
                return (PdfStream) value;
            }
        }
        throw new AssertionError("Missing transparency Form XObject");
    }

    private static String pageContent(
            DocumentSession session,
            PdfDictionary page) throws DocumentFailure {
        PdfValue value = resolve(session, page.get(PdfName.of("Contents")));
        StringBuilder result = new StringBuilder();
        if (value instanceof PdfStream) {
            return new String(
                    ((PdfStream) value).readBytes(),
                    StandardCharsets.US_ASCII);
        }
        net.zerocloud.pdf.PdfArray array = (net.zerocloud.pdf.PdfArray) value;
        for (int index = 0; index < array.size(); index++) {
            PdfStream stream = (PdfStream) resolve(session, array.get(index));
            result.append(new String(
                    stream.readBytes(),
                    StandardCharsets.US_ASCII));
        }
        return result.toString();
    }

    private static int occurrences(String value, String needle) {
        int result = 0;
        int offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0) {
            result++;
            offset += needle.length();
        }
        return result;
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder value = new StringBuilder(digest.length * 2);
            for (byte part : digest) {
                int unsigned = part & 0xff;
                value.append(Character.forDigit(unsigned >>> 4, 16));
                value.append(Character.forDigit(unsigned & 0xf, 16));
            }
            return value.toString();
        } catch (java.security.NoSuchAlgorithmException unavailable) {
            throw new AssertionError(unavailable);
        }
    }

    private static WorkflowRequest rewrite(Path source, Path target) {
        return WorkflowRequest.builder()
                .source("input", DocumentSource.path(source))
                .primarySource("input")
                .target("output", PublicationTarget.path(target))
                .saveMode(SaveMode.REWRITE)
                .build();
    }

    private static void writeExistingImageFixture(Path target)
            throws Exception {
        byte[] jpeg = encode(image(false), "JPEG");
        byte[] flate = deflate(new byte[] {20, (byte) 210, 40});
        writePdf(
                target,
                ascii("<< /Type /Catalog /Pages 2 0 R >>"),
                ascii("<< /Type /Pages /Count 1 /Kids [3 0 R] >>"),
                ascii("<< /Type /Page /Parent 2 0 R "
                        + "/MediaBox [0 0 300 300] "
                        + "/Resources << /XObject "
                        + "<< /U 5 0 R /F 6 0 R /J 7 0 R >> >> "
                        + "/Contents 4 0 R >>"),
                stream(new byte[0], ""),
                stream(
                        new byte[] {(byte) 230, 25, 30},
                        "/Type /XObject /Subtype /Image /Width 1 /Height 1 "
                                + "/BitsPerComponent 8 /ColorSpace /DeviceRGB "
                                + "/Mask 8 0 R"),
                stream(
                        flate,
                        "/Type /XObject /Subtype /Image /Width 1 /Height 1 "
                                + "/BitsPerComponent 8 /ColorSpace /DeviceRGB "
                                + "/Filter /FlateDecode"),
                stream(
                        jpeg,
                        "/Type /XObject /Subtype /Image /Width 2 /Height 2 "
                                + "/BitsPerComponent 8 /ColorSpace /DeviceRGB "
                                + "/Filter /DCTDecode"),
                stream(
                        new byte[] {(byte) 0x80},
                        "/Type /XObject /Subtype /Image /Width 1 /Height 1 "
                                + "/ImageMask true /BitsPerComponent 1"));
    }

    private static byte[] deflate(byte[] bytes) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(output)) {
            deflater.write(bytes);
        }
        return output.toByteArray();
    }

    private static byte[] stream(byte[] bytes, String dictionary) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(ascii("<< " + dictionary + " /Length "
                + bytes.length + " >>\nstream\n"));
        output.write(bytes);
        output.write(ascii("\nendstream"));
        return output.toByteArray();
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private static void writePdf(Path target, byte[]... objectBodies)
            throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(ascii("%PDF-1.7\n%FolioT18Fixture\n"));
        int[] offsets = new int[objectBodies.length + 1];
        for (int index = 0; index < objectBodies.length; index++) {
            offsets[index + 1] = output.size();
            output.write(ascii((index + 1) + " 0 obj\n"));
            output.write(objectBodies[index]);
            output.write(ascii("\nendobj\n"));
        }
        int xref = output.size();
        output.write(ascii("xref\n0 " + offsets.length + "\n"));
        output.write(ascii("0000000000 65535 f \n"));
        for (int index = 1; index < offsets.length; index++) {
            output.write(ascii(String.format(
                    Locale.ROOT,
                    "%010d 00000 n \n",
                    Integer.valueOf(offsets[index]))));
        }
        output.write(ascii("trailer\n<< /Size " + offsets.length
                + " /Root 1 0 R >>\nstartxref\n" + xref
                + "\n%%EOF\n"));
        Files.write(target, output.toByteArray());
    }

    private static final class ControlledTiffReaderSpi extends ImageReaderSpi {

        private final boolean failAfterAvailabilityProbe;
        private final boolean failOnRead;
        private final boolean failOnDispose;
        private int creations;

        ControlledTiffReaderSpi(
                boolean failAfterAvailabilityProbe,
                boolean failOnRead,
                boolean failOnDispose) {
            super(
                    "ZeroCloud",
                    "1",
                    new String[] {"TIFF", "tiff"},
                    new String[] {"tif", "tiff"},
                    new String[] {"image/tiff"},
                    ControlledTiffReader.class.getName(),
                    STANDARD_INPUT_TYPE,
                    null,
                    false,
                    null,
                    null,
                    null,
                    null,
                    false,
                    null,
                    null,
                    null,
                    null);
            this.failAfterAvailabilityProbe = failAfterAvailabilityProbe;
            this.failOnRead = failOnRead;
            this.failOnDispose = failOnDispose;
        }

        @Override
        public boolean canDecodeInput(Object input) {
            return true;
        }

        @Override
        public ImageReader createReaderInstance(Object extension) {
            creations++;
            if (failAfterAvailabilityProbe && creations > 1) {
                throw new IllegalStateException("provider selection failed");
            }
            return new ControlledTiffReader(this, failOnRead, failOnDispose);
        }

        @Override
        public String getDescription(Locale locale) {
            return "Controlled TIFF reader for failure-contract tests";
        }
    }

    private static final class ControlledTiffReader extends ImageReader {

        private final boolean failOnRead;
        private final boolean failOnDispose;

        ControlledTiffReader(
                ImageReaderSpi provider,
                boolean failOnRead,
                boolean failOnDispose) {
            super(provider);
            this.failOnRead = failOnRead;
            this.failOnDispose = failOnDispose;
        }

        @Override
        public int getNumImages(boolean allowSearch) {
            return 1;
        }

        @Override
        public int getWidth(int imageIndex) {
            return 1;
        }

        @Override
        public int getHeight(int imageIndex) {
            return 1;
        }

        @Override
        public Iterator<ImageTypeSpecifier> getImageTypes(int imageIndex) {
            return Collections.singletonList(
                    ImageTypeSpecifier.createFromBufferedImageType(
                            BufferedImage.TYPE_INT_RGB)).iterator();
        }

        @Override
        public IIOMetadata getStreamMetadata() {
            return null;
        }

        @Override
        public IIOMetadata getImageMetadata(int imageIndex) {
            return null;
        }

        @Override
        public BufferedImage read(int imageIndex, ImageReadParam parameter)
                throws IOException {
            if (failOnRead) {
                throw new IOException("provider read failed");
            }
            BufferedImage result = new BufferedImage(
                    1,
                    1,
                    BufferedImage.TYPE_INT_RGB);
            result.setRGB(0, 0, new Color(20, 40, 60).getRGB());
            return result;
        }

        @Override
        public void dispose() {
            if (failOnDispose) {
                throw new IllegalStateException("provider cleanup failed");
            }
            super.dispose();
        }
    }

    private Path path(String name) {
        return temporaryFolder.getRoot().toPath().resolve(name);
    }
}
