package net.zerocloud.pdf.consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import net.zerocloud.pdf.CharacterMapping;
import net.zerocloud.pdf.DocumentResourceInventory;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.DocumentPermissions;
import net.zerocloud.pdf.DocumentPatch;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.ExtractionLimits;
import net.zerocloud.pdf.FontResource;
import net.zerocloud.pdf.ImageByteAccess;
import net.zerocloud.pdf.ObjectReference;
import net.zerocloud.pdf.PasswordCredential;
import net.zerocloud.pdf.PasswordSecurityPolicy;
import net.zerocloud.pdf.PdfArray;
import net.zerocloud.pdf.PdfDictionary;
import net.zerocloud.pdf.PdfIndirectReference;
import net.zerocloud.pdf.PdfInspectionLimits;
import net.zerocloud.pdf.PdfName;
import net.zerocloud.pdf.PdfOutputPolicy;
import net.zerocloud.pdf.PdfStream;
import net.zerocloud.pdf.PdfValue;
import net.zerocloud.pdf.PdfVersion;
import net.zerocloud.pdf.ResourceExtractionLimits;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.TextItem;
import net.zerocloud.pdf.TextRenderingMode;
import net.zerocloud.pdf.WorkflowEnvironment;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.composition.CanvasMatrix;
import net.zerocloud.pdf.composition.FontLimits;
import net.zerocloud.pdf.composition.FontSelection;
import net.zerocloud.pdf.composition.FontSource;
import net.zerocloud.pdf.composition.PositionedUnicodeText;
import net.zerocloud.pdf.composition.ReferenceFontSet;
import net.zerocloud.pdf.composition.command.DrawPositionedUnicodeText;
import net.zerocloud.pdf.query.ExtractImagesAndResources;
import net.zerocloud.pdf.query.ExtractTextAndStructure;
import net.zerocloud.pdf.query.InspectObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Public-workflow consumer coverage for deterministic T19 font handling. */
public final class FontLoadingWorkflowTest {

    private static final String CAPABILITY =
            "composition.fonts.load-embed-subset-fallback";
    private static final byte[] SENTINEL = {13, 37, 42};

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void explicitBytesEmbedSubsetMetricsAndToUnicodeThroughPublicQueries()
            throws Exception {
        byte[] callerBytes = fontBytes("FolioPrimary.ttf.base64");
        int originalFontLength = callerBytes.length;
        FontSource source = FontSource.bytes(callerBytes);
        callerBytes[0] = 0;
        Path target = temporaryFolder.newFile("explicit-font.pdf").toPath();

        WorkflowOutcome<Void> creation = new DocumentWorkflow().execute(
                WorkflowRequest.create(target, SaveMode.REWRITE),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    session.execute(DrawPositionedUnicodeText.version1(
                            1,
                            PositionedUnicodeText.version1(
                                    "A",
                                    FontSelection.explicit(source),
                                    12d,
                                    TextRenderingMode.FILL,
                                    CanvasMatrix.of(
                                            1d, 0d, 0d, 1d, 36d, 72d)),
                            limits(1, 972L, 1, 1L, 60L)));
                    return null;
                });

        assertEquals(CAPABILITY, creation.getCapabilityId());
        new DocumentWorkflow().execute(
                WorkflowRequest.open(target, SaveMode.REWRITE),
                session -> {
                    DocumentResourceInventory inventory = session.query(
                            ExtractImagesAndResources.version1(
                                    resourceLimits(),
                                    ImageByteAccess.NONE));
                    assertEquals(1, inventory.getFonts().size());
                    FontResource font = inventory.getFonts().get(0);
                    assertEquals(FontResource.FontKind.TYPE_0, font.getFontKind());
                    assertEquals(FontResource.Embedding.EMBEDDED, font.getEmbedding());
                    assertTrue(font.isSubset());
                    assertTrue(font.getBaseFontName().get().getValue()
                            .endsWith("+FolioPrimary-Regular"));
                    byte[] embedded = embeddedFontBytes(session, font);
                    assertTrue(embedded.length < originalFontLength);
                    assertTrue(sfntGlyphCount(embedded) < 5);
                    int embeddedHead = tableOffset(embedded, "head");
                    int embeddedHhea = tableOffset(embedded, "hhea");
                    assertEquals(40, signedShort(embedded, embeddedHead + 36));
                    assertEquals(560, signedShort(embedded, embeddedHead + 40));
                    assertEquals(600, unsignedShort(embedded, embeddedHhea + 10));
                    assertEquals(0, signedShort(embedded, embeddedHhea + 12));
                    assertEquals(80, signedShort(embedded, embeddedHhea + 14));
                    assertEquals(520, signedShort(embedded, embeddedHhea + 16));
                    assertEquals(0xb1b0afbaL,
                            checksum(embedded, 0, embedded.length));
                    String toUnicode = new String(
                            toUnicodeBytes(session, font),
                            StandardCharsets.ISO_8859_1);
                    assertTrue(toUnicode.contains("<0041>"));

                    TextItem item = session.query(
                            ExtractTextAndStructure.version1(textLimits()))
                            .getPages().get(0).getTextItems().get(0);
                    assertEquals("A", item.getUnicode().get());
                    assertEquals("A", item.getTextContribution());
                    assertEquals(
                            CharacterMapping.Confidence.EXPLICIT,
                            item.getCharacterMapping().getConfidence());
                    assertDecimal("36", item.getGeometry().getE());
                    assertDecimal("72", item.getGeometry().getF());
                    assertDecimal("7.2", item.getGeometry().getAdvanceX());
                    assertDecimal("0", item.getGeometry().getAdvanceY());
                    return null;
                });
    }

    private static byte[] readOnlyEmbeddedFont(Path source) throws Exception {
        return new DocumentWorkflow().execute(
                WorkflowRequest.open(source, SaveMode.REWRITE),
                session -> {
                    FontResource font = session.query(
                            ExtractImagesAndResources.version1(
                                    resourceLimits(),
                                    ImageByteAccess.NONE))
                            .getFonts().get(0);
                    return embeddedFontBytes(session, font);
                }).getResult();
    }

    @Test
    public void compactHorizontalMetricsUseTheLastDeclaredAdvance()
            throws Exception {
        byte[] compact = withCompactHorizontalMetrics(
                fontBytes("FolioPrimary.ttf.base64"));
        Path target = temporaryFolder.newFile("compact-hmtx.pdf").toPath();

        createOne(
                target,
                FontSource.bytes(compact),
                "Z",
                limits(1, compact.length, 1, 1L, 4096L));

        new DocumentWorkflow().execute(
                WorkflowRequest.open(target, SaveMode.REWRITE),
                session -> {
                    TextItem item = session.query(
                            ExtractTextAndStructure.version1(textLimits()))
                            .getPages().get(0).getTextItems().get(0);
                    assertEquals("Z", item.getTextContribution());
                    assertDecimal("7.2", item.getGeometry().getAdvanceX());
                    return null;
                });
    }

    @Test
    public void format12SupplementaryMappingPublishesAndExtractsToUnicode()
            throws Exception {
        byte[] font = mapZToSupplementary(
                fontBytes("FolioPrimary.ttf.base64"));
        String emoji = new String(Character.toChars(0x1f600));
        Path target = temporaryFolder.newFile(
                "supplementary-format12.pdf").toPath();

        createOne(
                target,
                FontSource.bytes(font),
                emoji,
                limits(1, font.length, 1, 1L, 4096L));

        new DocumentWorkflow().execute(
                WorkflowRequest.open(target, SaveMode.REWRITE),
                session -> {
                    FontResource embedded = session.query(
                            ExtractImagesAndResources.version1(
                                    resourceLimits(),
                                    ImageByteAccess.NONE))
                            .getFonts().get(0);
                    String toUnicode = new String(
                            toUnicodeBytes(session, embedded),
                            StandardCharsets.ISO_8859_1)
                            .toUpperCase(Locale.ROOT);
                    assertTrue(toUnicode.contains("<D83DDE00>"));

                    TextItem item = session.query(
                            ExtractTextAndStructure.version1(textLimits()))
                            .getPages().get(0).getTextItems().get(0);
                    assertEquals(emoji, item.getUnicode().get());
                    assertEquals(emoji, item.getTextContribution());
                    assertEquals(
                            CharacterMapping.Confidence.EXPLICIT,
                            item.getCharacterMapping().getConfidence());
                    assertDecimal("7.44", item.getGeometry().getAdvanceX());
                    return null;
                });
    }

    @Test
    public void configuredReferenceFontsUseStrictOrderedFallbackWithoutSystemFonts()
            throws Exception {
        FontSource primary = FontSource.bytes(
                fontBytes("FolioPrimary.ttf.base64"));
        FontSource fallback = FontSource.bytes(
                fontBytes("FolioFallback.ttf.base64"));
        WorkflowEnvironment environment = WorkflowEnvironment.builder()
                .referenceFontSet(ReferenceFontSet.version1(primary, fallback))
                .build();
        Path target = temporaryFolder.newFile("reference-fallback.pdf").toPath();

        new DocumentWorkflow(environment).execute(
                WorkflowRequest.create(target, SaveMode.REWRITE),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    session.execute(DrawPositionedUnicodeText.version1(
                            1,
                            PositionedUnicodeText.version1(
                                    "A\u03a9B",
                                    FontSelection.referenceFontSet(),
                                    12d,
                                    TextRenderingMode.FILL,
                                    CanvasMatrix.of(
                                            1d, 0d, 0d, 1d, 36d, 72d)),
                            limits(2, 2000L, 3, 4L, 116L)));
                    return null;
                });

        new DocumentWorkflow().execute(
                WorkflowRequest.open(target, SaveMode.REWRITE),
                session -> {
                    DocumentResourceInventory inventory = session.query(
                            ExtractImagesAndResources.version1(
                                    resourceLimits(),
                                    ImageByteAccess.NONE));
                    assertEquals(2, inventory.getFonts().size());
                    assertTrue(inventory.getFonts().stream().allMatch(
                            font -> font.getEmbedding()
                                    == FontResource.Embedding.EMBEDDED));

                    java.util.List<TextItem> items = session.query(
                            ExtractTextAndStructure.version1(textLimits()))
                            .getPages().get(0).getTextItems();
                    StringBuilder text = new StringBuilder();
                    for (TextItem item : items) {
                        text.append(item.getTextContribution());
                    }
                    assertEquals("A\u03a9B", text.toString());
                    assertDecimal("7.2", items.get(0).getGeometry().getAdvanceX());
                    assertDecimal("8.4", items.get(1).getGeometry().getAdvanceX());
                    assertDecimal("7.8", items.get(2).getGeometry().getAdvanceX());
                    return null;
                });

        Path missing = temporaryFolder.newFile("no-system-font.pdf").toPath();
        try {
            new DocumentWorkflow().execute(
                    WorkflowRequest.create(missing, SaveMode.REWRITE),
                    session -> {
                        session.execute(AddBlankPage.INSTANCE);
                        session.execute(DrawPositionedUnicodeText.version1(
                                1,
                                PositionedUnicodeText.version1(
                                        "A",
                                        FontSelection.referenceFontSet(),
                                        12d,
                                        TextRenderingMode.FILL,
                                        CanvasMatrix.of(
                                                1d, 0d, 0d, 1d, 36d, 72d)),
                                limits(0, 0L, 1, 0L, 4096L)));
                        return null;
                    });
            org.junit.Assert.fail("empty defaults must not discover a system font");
        } catch (net.zerocloud.pdf.DocumentFailure failure) {
            assertEquals(
                    net.zerocloud.pdf.DocumentFailureCode.FONT_GLYPH_MISSING,
                    failure.getCode());
            assertEquals(CAPABILITY, failure.getCapabilityId());
        }
    }

    @Test
    public void callerOwnedSourcesStayOpenAndEqualProgramsReuseOneResource()
            throws Exception {
        byte[] bytes = fontBytes("FolioPrimary.ttf.base64");
        TrackingInputStream input = new TrackingInputStream(bytes);
        TrackingChannel channel = new TrackingChannel(bytes);
        FontSource streamSource = FontSource.stream(input);
        FontSource channelSource = FontSource.channel(channel);
        Path target = temporaryFolder.newFile("source-ownership.pdf").toPath();

        new DocumentWorkflow().execute(
                WorkflowRequest.create(target, SaveMode.REWRITE),
                session -> {
                    for (int page = 1; page <= 3; page++) {
                        session.execute(AddBlankPage.INSTANCE);
                    }
                    session.execute(positioned(1, "A", streamSource));
                    session.execute(positioned(2, "B", streamSource));
                    session.execute(positioned(3, "Z", channelSource));
                    return null;
                });

        assertTrue("caller stream must stay open", !input.closed);
        assertEquals("identical stream declaration is staged once",
                bytes.length, input.bytesRead);
        assertTrue("caller channel must stay open", channel.isOpen());
        assertEquals(bytes.length, channel.bytesRead);

        new DocumentWorkflow().execute(
                WorkflowRequest.open(target, SaveMode.REWRITE),
                session -> {
                    DocumentResourceInventory inventory = session.query(
                            ExtractImagesAndResources.version1(
                                    resourceLimits(),
                                    ImageByteAccess.NONE));
                    assertEquals(1, inventory.getFonts().size());
                    assertEquals(Arrays.asList(1, 2, 3),
                            inventory.getFonts().get(0).getPageUsage());
                    assertEquals("A", extractedPageText(session, 0));
                    assertEquals("B", extractedPageText(session, 1));
                    assertEquals("Z", extractedPageText(session, 2));
                    return null;
                });
    }

    @Test
    public void metricScalingUsesExactRationalRounding() throws Exception {
        byte[] unusualUnits = withUnitsAndGlyphAdvance(
                fontBytes("FolioPrimary.ttf.base64"),
                17,
                2,
                17836);
        Path target = temporaryFolder.getRoot().toPath()
                .resolve("exact-rational-width.pdf");
        createOne(
                target,
                FontSource.bytes(unusualUnits),
                "A",
                limits(1, 972L, 1, 1L, 4096L));

        new DocumentWorkflow().execute(
                WorkflowRequest.open(target, SaveMode.REWRITE),
                session -> {
                    TextItem item = session.query(
                            ExtractTextAndStructure.version1(textLimits()))
                            .getPages().get(0).getTextItems().get(0);
                    assertEquals("A", item.getTextContribution());
                    assertDecimal(
                            "12590.112",
                            item.getGeometry().getAdvanceX());
                    return null;
                });
    }

    @Test
    public void supportedSfntProfileVariantsPublishReopenAndKeepGeometry()
            throws Exception {
        byte[] primary = fontBytes("FolioPrimary.ttf.base64");
        byte[][] variants = {
            withAppleTrueTypeScaler(primary),
            withShortLoca(primary),
            withFormat4Cmap(primary, 0, 1),
            withFormat4Cmap(primary, 0, 3),
            withFormat4Cmap(primary, 3, 1),
            withFormat12Platform(primary, 0, 4),
            withPostVersion3(primary),
            withPostCustomName(primary, "A_alt"),
            withPostMemoryEstimates(primary),
            withTableInt(primary, "OS/2", 58, 0x41424320),
            withTableInt(primary, "OS/2", 58, 0x20202020),
            withTableInt(primary, "OS/2", 58, 0),
            withOs2Version(primary, 1),
            withOs2Version(primary, 2),
            withOs2Version(primary, 3),
            withOs2Version(primary, 4),
            withOs2Version(primary, 5),
            withOs2VersionAndSelection(primary, 4, 0x0201, 0x0002),
            withFsType(primary, 0x000c),
            withTranslatedComposite(primary),
            withHeaderOnlyZeroContour(primary),
            withTableShort(primary, "maxp", 6, 25)
        };
        for (int index = 0; index < variants.length; index++) {
            byte[] variant = variants[index];
            Path target = temporaryFolder.getRoot().toPath()
                    .resolve("supported-profile-" + index + ".pdf");
            try {
                createOne(
                        target,
                        FontSource.bytes(variant),
                        "A",
                        limits(1, variant.length, 1, 1L, 4096L));
            } catch (DocumentFailure failure) {
                throw new AssertionError(
                        "supported profile variant " + index, failure);
            }
            new DocumentWorkflow().execute(
                    WorkflowRequest.open(target, SaveMode.REWRITE),
                    session -> {
                        TextItem item = session.query(
                                ExtractTextAndStructure.version1(textLimits()))
                                .getPages().get(0).getTextItems().get(0);
                        assertEquals("A", item.getTextContribution());
                        assertDecimal(
                                "7.2", item.getGeometry().getAdvanceX());
                        return null;
                    });
        }
    }

    @Test
    public void pathSourceIsWorkflowStagedAndFolioClosesItsHandle()
            throws Exception {
        Path fontPath = temporaryFolder.newFile("path-source.ttf").toPath();
        Files.write(fontPath, fontBytes("FolioPrimary.ttf.base64"));
        Path target = temporaryFolder.newFile("path-font.pdf").toPath();

        createOne(target, FontSource.path(fontPath), "A",
                limits(1, 972L, 1, 1L, 60L));

        Path moved = fontPath.resolveSibling("path-source-moved.ttf");
        Files.move(fontPath, moved);
        assertTrue(Files.exists(moved));
        assertEquals("A", new DocumentWorkflow().execute(
                WorkflowRequest.open(target, SaveMode.REWRITE),
                session -> extractedPageText(session, 0)).getResult());
    }

    @Test
    public void closedFailuresAreAtomicFixedAndRedacted() throws Exception {
        byte[] primary = fontBytes("FolioPrimary.ttf.base64");

        assertFontFailure(
                "corrupt-secret.pdf",
                FontSource.bytes(Arrays.copyOf(primary, 8)),
                "customer-secret",
                limits(1, 8L, 15, 15L, 4096L),
                DocumentFailureCode.FONT_SOURCE_INVALID,
                "The font source could not be loaded safely.");

        for (String signature : Arrays.asList(
                "OTTO", "ttcf", "wOFF", "wOF2")) {
            byte[] unsupported = Arrays.copyOf(primary, primary.length);
            byte[] marker = signature.getBytes(StandardCharsets.ISO_8859_1);
            System.arraycopy(marker, 0, unsupported, 0, marker.length);
            assertFontFailure(
                    "unsupported-" + signature + ".pdf",
                    FontSource.bytes(unsupported),
                    "A",
                    limits(1, 972L, 1, 1L, 4096L),
                    DocumentFailureCode.FONT_FORMAT_UNSUPPORTED,
                    "The font format or profile is unsupported.");
        }

        byte[] rawType1 = new byte[16];
        System.arraycopy("%!PS".getBytes(StandardCharsets.ISO_8859_1),
                0, rawType1, 0, 4);
        assertFontFailure(
                "unsupported-type1.pdf",
                FontSource.bytes(rawType1),
                "A",
                limits(1, 16L, 1, 1L, 4096L),
                DocumentFailureCode.FONT_FORMAT_UNSUPPORTED,
                "The font format or profile is unsupported.");

        assertFontFailure(
                "unused-corrupt-fallback.pdf",
                FontSelection.explicit(
                        FontSource.bytes(primary),
                        FontSource.bytes(Arrays.copyOf(primary, 8))),
                "A",
                limits(2, 980L, 1, 1L, 4096L),
                DocumentFailureCode.FONT_SOURCE_INVALID,
                "The font source could not be loaded safely.");

        assertFontFailure(
                "restricted-secret.pdf",
                FontSource.bytes(withFsType(primary, 0x0002)),
                "A",
                limits(1, 972L, 1, 1L, 4096L),
                DocumentFailureCode.FONT_EMBEDDING_RESTRICTED,
                "The font embedding permissions reject this operation.");

        byte[] bitmapOnly = withFsType(withOs2Version(primary, 2), 0x0200);
        assertFontFailure(
                "bitmap-only-secret.pdf",
                FontSource.bytes(bitmapOnly),
                "A",
                limits(1, bitmapOnly.length, 1, 1L, 4096L),
                DocumentFailureCode.FONT_EMBEDDING_RESTRICTED,
                "The font embedding permissions reject this operation.");

        createOne(
                temporaryFolder.getRoot().toPath()
                        .resolve("legacy-combined-fstype.pdf"),
                FontSource.bytes(withFsType(primary, 0x0006)),
                "A",
                limits(1, 972L, 1, 1L, 4096L));

        byte[] invalidHeadMagic = Arrays.copyOf(primary, primary.length);
        writeInt(invalidHeadMagic, tableOffset(invalidHeadMagic, "head") + 12, 0);
        repairChecksums(invalidHeadMagic);
        assertFontFailure(
                "invalid-head-magic-secret.pdf",
                FontSource.bytes(invalidHeadMagic),
                "A",
                limits(1, 972L, 1, 1L, 4096L),
                DocumentFailureCode.FONT_SOURCE_INVALID,
                "The font source could not be loaded safely.");

        byte[] invalidUnitsPerEm = Arrays.copyOf(primary, primary.length);
        writeShort(invalidUnitsPerEm,
                tableOffset(invalidUnitsPerEm, "head") + 18, 1);
        repairChecksums(invalidUnitsPerEm);
        assertFontFailure(
                "invalid-units-per-em-secret.pdf",
                FontSource.bytes(invalidUnitsPerEm),
                "A",
                limits(1, 972L, 1, 1L, 4096L),
                DocumentFailureCode.FONT_SOURCE_INVALID,
                "The font source could not be loaded safely.");

        byte[] invalidMaxpVersion = Arrays.copyOf(primary, primary.length);
        writeInt(invalidMaxpVersion,
                tableOffset(invalidMaxpVersion, "maxp"), 0x00005000);
        repairChecksums(invalidMaxpVersion);
        assertFontFailure(
                "invalid-maxp-version-secret.pdf",
                FontSource.bytes(invalidMaxpVersion),
                "A",
                limits(1, 972L, 1, 1L, 4096L),
                DocumentFailureCode.FONT_SOURCE_INVALID,
                "The font source could not be loaded safely.");

        byte[] invalidHheaVersion = Arrays.copyOf(primary, primary.length);
        writeInt(invalidHheaVersion,
                tableOffset(invalidHheaVersion, "hhea"), 0);
        repairChecksums(invalidHheaVersion);
        assertFontFailure(
                "invalid-hhea-version-secret.pdf",
                FontSource.bytes(invalidHheaVersion),
                "A",
                limits(1, 972L, 1, 1L, 4096L),
                DocumentFailureCode.FONT_SOURCE_INVALID,
                "The font source could not be loaded safely.");

        byte[] invalidMetricDataFormat = Arrays.copyOf(
                primary, primary.length);
        writeShort(invalidMetricDataFormat,
                tableOffset(invalidMetricDataFormat, "hhea") + 32, 1);
        repairChecksums(invalidMetricDataFormat);
        assertFontFailure(
                "invalid-metric-data-format-secret.pdf",
                FontSource.bytes(invalidMetricDataFormat),
                "A",
                limits(1, 972L, 1, 1L, 4096L),
                DocumentFailureCode.FONT_SOURCE_INVALID,
                "The font source could not be loaded safely.");

        byte[] invalidHorizontalMetrics = Arrays.copyOf(
                primary, primary.length);
        writeShort(invalidHorizontalMetrics,
                tableOffset(invalidHorizontalMetrics, "hhea") + 34, 0);
        repairChecksums(invalidHorizontalMetrics);
        assertFontFailure(
                "invalid-horizontal-metrics-secret.pdf",
                FontSource.bytes(invalidHorizontalMetrics),
                "A",
                limits(1, 972L, 1, 1L, 4096L),
                DocumentFailureCode.FONT_SOURCE_INVALID,
                "The font source could not be loaded safely.");

        assertFontProfileFailures(
                DocumentFailureCode.FONT_SOURCE_INVALID,
                "The font source could not be loaded safely.",
                profileCase("understated-maxp-points-maximum",
                        withTableShort(primary, "maxp", 6, 0)),
                profileCase("invalid-hhea-maximum-advance",
                        withTableShort(primary, "hhea", 10, 0)),
                profileCase("invalid-loca-terminal-offset",
                        withTableInt(primary, "loca", 20, 168)),
                profileCase("invalid-glyph-bounds",
                        withTableShort(primary, "glyf", 50, 600)),
                profileCase("invalid-name-storage-offset",
                        withTableShort(primary, "name", 4, 0)),
                profileCase("truncated-name-table",
                        withTableLength(primary, "name", 153)),
                profileCase("truncated-post-table",
                        withTableLength(primary, "post", 32)),
                profileCase("zero-os2-weight-class",
                        withTableShort(primary, "OS/2", 4, 0)),
                profileCase("zero-os2-width-class",
                        withTableShort(primary, "OS/2", 6, 0)),
                profileCase("contradictory-os2-selection",
                        withTableShort(primary, "OS/2", 62, 0x0060)),
                profileCase("truncated-os2-table",
                        withTableLength(primary, "OS/2", 10)),
                profileCase("invalid-format4-length",
                        withTableShort(primary, "cmap", 14, 1)),
                profileCase("invalid-format4-segment-count",
                        withTableInt(primary, "cmap", 16, 16)),
                profileCase("truncated-cmap-table",
                        withTableLength(primary, "cmap", 14)),
                profileCase("invalid-format4-segment-order",
                        withTableInt(primary, "cmap", 28, 0x60)),
                profileCase("out-of-range-cmap-glyph",
                        withTableInt(primary, "cmap", 52, 99)),
                profileCase("self-referential-composite",
                        withSelfReferentialComposite(primary, 2)));

        assertFontProfileFailures(
                DocumentFailureCode.FONT_FORMAT_UNSUPPORTED,
                "The font format or profile is unsupported.",
                profileCase("unsupported-head-layout-flag",
                        withTableShort(primary, "head", 16, 0x0011)),
                profileCase("unsupported-glyph-marker",
                        withTableShort(primary, "glyf", 48, 0xfffe)),
                profileCase("unsupported-name-format",
                        withTableShort(primary, "name", 0, 2)),
                profileCase("unsupported-post-version",
                        withTableInt(primary, "post", 0, 0x00050000)),
                profileCase("unsupported-cmap-encoding",
                        withTableShort(primary, "cmap", 6, 1)));

        byte[] invalidOffsetSearch = Arrays.copyOf(primary, primary.length);
        Arrays.fill(invalidOffsetSearch, 6, 12, (byte) 0);
        repairChecksums(invalidOffsetSearch);
        assertFontFailure(
                "invalid-offset-search-secret.pdf",
                FontSource.bytes(invalidOffsetSearch),
                "A",
                limits(1, 972L, 1, 1L, 4096L),
                DocumentFailureCode.FONT_SOURCE_INVALID,
                "The font source could not be loaded safely.");

        byte[] invalidTableChecksum = Arrays.copyOf(primary, primary.length);
        int nameRecord = tableRecordOffset(invalidTableChecksum, "name");
        invalidTableChecksum[nameRecord + 4] ^= 1;
        assertFontFailure(
                "invalid-table-checksum-secret.pdf",
                FontSource.bytes(invalidTableChecksum),
                "A",
                limits(1, 972L, 1, 1L, 4096L),
                DocumentFailureCode.FONT_SOURCE_INVALID,
                "The font source could not be loaded safely.");

        assertFontFailure(
                "missing-secret.pdf",
                FontSource.bytes(primary),
                "\u03a9",
                limits(1, 972L, 1, 1L, 4096L),
                DocumentFailureCode.FONT_GLYPH_MISSING,
                "No declared font contains every requested Unicode scalar.");

        assertFontFailure(
                "invalid-secret.pdf",
                FontSource.bytes(primary),
                "\ud800customer-secret",
                limits(1, 972L, 32, 32L, 4096L),
                DocumentFailureCode.POSITIONED_TEXT_INVALID,
                "The positioned Unicode text declaration is invalid.");

        assertFontFailure(
                "ambiguous-mapping.pdf",
                FontSource.bytes(mapZToGlyphA(primary)),
                "AZ",
                limits(1, 972L, 2, 2L, 4096L),
                DocumentFailureCode.FONT_MAPPING_UNSUPPORTED,
                "The requested Unicode mapping cannot be represented safely.");

        assertFontFailure(
                "symbol-cmap-secret.pdf",
                FontSource.bytes(symbolOnlyCmap(primary)),
                "A",
                limits(1, 972L, 1, 1L, 4096L),
                DocumentFailureCode.FONT_FORMAT_UNSUPPORTED,
                "The font format or profile is unsupported.");

        assertFontFailure(
                "forced-invisible-alias-secret.pdf",
                FontSource.bytes(mapZeroWidthSpaceToGlyphA(primary)),
                "A",
                limits(1, 972L, 1, 1L, 4096L),
                DocumentFailureCode.FONT_MAPPING_UNSUPPORTED,
                "The requested Unicode mapping cannot be represented safely.");

        byte[] ambiguousFull = withFsType(
                withOs2Version(mapZToGlyphA(primary), 2), 0x0100);
        assertFontFailure(
                "ambiguous-full-embedding.pdf",
                FontSource.bytes(ambiguousFull),
                "Z",
                limits(1, ambiguousFull.length, 1, 1L, 4096L),
                DocumentFailureCode.FONT_MAPPING_UNSUPPORTED,
                "The requested Unicode mapping cannot be represented safely.");
    }

    @Test
    public void closedSfntProfileRejectsCorruptionAndKnownOutsideVariants()
            throws Exception {
        byte[] primary = fontBytes("FolioPrimary.ttf.base64");
        assertFontProfileFailures(
                DocumentFailureCode.FONT_SOURCE_INVALID,
                "The font source could not be loaded safely.",
                profileCase("head-global-bounds-mismatch",
                        withTableShort(primary, "head", 36, 39)),
                profileCase("invalid-loca-format",
                        withTableShort(primary, "head", 44, 1)),
                profileCase("reserved-head-flag",
                        withTableShort(primary, "head", 16, 0x8001)),
                profileCase("inconsistent-head-lsb-flag",
                        withTableShort(primary, "head", 16, 3)),
                profileCase("invalid-hhea-caret-slope",
                        withTableShort(primary, "hhea", 14, 79)),
                profileCase("invalid-maxp-zones",
                        withTableShort(primary, "maxp", 14, 2)),
                profileCase("unpaired-name-surrogate",
                        withInvalidNameSurrogate(primary)),
                profileCase("supplementary-name-character",
                        withSupplementaryNameCharacter(primary)),
                profileCase("reserved-name-id-15",
                        withAdditionalNameRecord(primary, 15)),
                profileCase("reserved-name-id-26",
                        withAdditionalNameRecord(primary, 26)),
                profileCase("out-of-range-name-id",
                        withAdditionalNameRecord(primary, 32768)),
                profileCase("invalid-version-name",
                        withInvalidVersionName(primary)),
                profileCase("invalid-postscript-name",
                        withInvalidPostScriptName(primary)),
                profileCase("invalid-post-custom-glyph-name",
                        withPostCustomName(primary, "A-alt")),
                profileCase("invalid-post-memory-estimate",
                        withInvalidPostMemory(primary)),
                profileCase("incorrect-os2-character-range",
                        withTableShort(primary, "OS/2", 64, 0x21)),
                profileCase("reserved-os2-unicode-range-bit",
                        withTableInt(primary, "OS/2", 54, 0x80000000)),
                profileCase("reserved-os2-code-page-bit-9",
                        withTableInt(
                                withOs2Version(primary, 1),
                                "OS/2",
                                78,
                                0x00000200)),
                profileCase("unassigned-os2-version-1-code-page-bit-8",
                        withTableInt(
                                withOs2Version(primary, 1),
                                "OS/2",
                                78,
                                0x00000100)),
                profileCase("reserved-os2-code-page-bit-22",
                        withTableInt(
                                withOs2Version(primary, 1),
                                "OS/2",
                                78,
                                0x00400000)),
                profileCase("reserved-os2-code-page-bit-32",
                        withTableInt(
                                withOs2Version(primary, 1),
                                "OS/2",
                                82,
                                0x00000001)),
                profileCase("invalid-os2-vendor-tag",
                        withTableInt(primary, "OS/2", 58, 0x004f4c49)),
                profileCase("leading-space-os2-vendor-tag",
                        withTableInt(primary, "OS/2", 58, 0x20414243)),
                profileCase("internal-space-os2-vendor-tag",
                        withTableInt(primary, "OS/2", 58, 0x41204243)),
                profileCase("invalid-os2-optical-range",
                        withInvalidVersion5OpticalRange(primary)),
                profileCase("invalid-format4-sentinel",
                        withInvalidFormat4Sentinel(primary)),
                profileCase("invalid-format4-sentinel-range-offset",
                        withInvalidFormat4SentinelRangeOffset(primary)),
                profileCase("format4-surrogate-segment",
                        withFormat4SurrogateSegment(primary)),
                profileCase("format4-range-offset-into-header",
                        withFormat4RangeOffsetIntoHeader(primary)),
                profileCase("composite-bounds-mismatch",
                        withTableShort(
                                withTranslatedComposite(primary),
                                "glyf",
                                54,
                                571)),
                profileCase("late-composite-overlap-flag",
                        withLateCompositeOverlapFlag(primary)),
                profileCase("nonzero-directory-gap",
                        withNonzeroDirectoryGap(primary)));

        assertFontProfileFailures(
                DocumentFailureCode.FONT_FORMAT_UNSUPPORTED,
                "The font format or profile is unsupported.",
                profileCase("unsupported-head-flag-14",
                        withTableShort(primary, "head", 16, 0x4001)),
                profileCase("unsorted-name-records",
                        withSwappedNameRecords(primary)),
                profileCase("name-format-1",
                        withFormat1Name(primary)),
                profileCase("padded-name-storage",
                        withPaddedFormat0Name(primary)),
                profileCase("trailing-name-storage",
                        withTrailingNameStorage(primary)),
                profileCase("defined-name-id-outside-profile",
                        withAdditionalNameRecord(primary, 20)),
                profileCase("variations-name-id-outside-profile",
                        withAdditionalNameRecord(primary, 25)),
                profileCase("font-specific-name-id-outside-profile",
                        withAdditionalNameRecord(primary, 256)),
                profileCase("post-version-1",
                        withPostVersion1(primary)),
                profileCase("legacy-short-os2-version-0",
                        withLegacyShortOs2Version0(primary)),
                profileCase("reserved-fstype-bit",
                        withFsType(primary, 0x0010)),
                profileCase("nonzero-os2-family-class",
                        withTableShort(primary, "OS/2", 30, 0x0800)),
                profileCase("glyph-instructions",
                        withGlyphInstructions(primary)),
                profileCase("nonzero-maxp-vm-maximum",
                        withTableShort(primary, "maxp", 24, 1)),
                profileCase("alternate-composite-marker",
                        withAlternateCompositeMarker(primary)),
                profileCase("composite-transform",
                        withUnsupportedCompositeTransform(primary)),
                profileCase("composite-point-attachment",
                        withUnsupportedCompositePointAttachment(primary)),
                profileCase("composite-use-my-metrics",
                        withUseMyMetricsComposite(primary)),
                profileCase("missing-unicode-cmap-mappings",
                        withoutUnicodeMappings(primary)),
                profileCase("unsupported-windows-format12-cmap",
                        withFormat12Platform(primary, 3, 10)),
                profileCase("extra-sfnt-table",
                        withExtraTable(primary)));
    }

    @Test
    public void clippingRenderingModesAreRejectedBeforePublication()
            throws Exception {
        FontSource source = FontSource.bytes(
                fontBytes("FolioPrimary.ttf.base64"));
        TextRenderingMode[] clippingModes = {
            TextRenderingMode.FILL_CLIP,
            TextRenderingMode.STROKE_CLIP,
            TextRenderingMode.FILL_STROKE_CLIP,
            TextRenderingMode.CLIP
        };
        for (TextRenderingMode mode : clippingModes) {
            Path target = temporaryFolder.getRoot().toPath().resolve(
                    "unsupported-" + mode.name() + ".pdf");
            try {
                new DocumentWorkflow().execute(
                        WorkflowRequest.create(target, SaveMode.REWRITE),
                        session -> {
                            session.execute(AddBlankPage.INSTANCE);
                            session.execute(DrawPositionedUnicodeText.version1(
                                    1,
                                    PositionedUnicodeText.version1(
                                            "A",
                                            FontSelection.explicit(source),
                                            12d,
                                            mode,
                                            CanvasMatrix.of(
                                                    1d, 0d, 0d, 1d,
                                                    36d, 72d)),
                                    limits(1, 972L, 1, 1L, 4096L)));
                            return null;
                        });
                fail("expected clipping mode rejection");
            } catch (DocumentFailure failure) {
                assertEquals(DocumentFailureCode.POSITIONED_TEXT_INVALID,
                        failure.getCode());
                assertEquals(CAPABILITY, failure.getCapabilityId());
                assertEquals(
                        "The positioned Unicode text declaration is invalid.",
                        failure.getDiagnostic());
                assertNull(failure.getCause());
                assertEquals(PublicationStatus.NOT_ATTEMPTED,
                        failure.getPublicationReceipts().get(0).getStatus());
            }
            assertFalse("failed command must not publish", Files.exists(target));
        }
    }

    @Test
    public void queriesObservePriorFontEmbeddingAndUnicodeText()
            throws Exception {
        FontSource source = FontSource.bytes(
                fontBytes("FolioPrimary.ttf.base64"));
        Path target = temporaryFolder.getRoot().toPath()
                .resolve("font-query-barrier.pdf");

        new DocumentWorkflow().execute(
                WorkflowRequest.create(target, SaveMode.REWRITE),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    session.execute(positioned(1, "A", source));
                    DocumentResourceInventory inventory = session.query(
                            ExtractImagesAndResources.version1(
                                    resourceLimits(),
                                    ImageByteAccess.NONE));
                    assertEquals(1, inventory.getFonts().size());
                    assertEquals(FontResource.Embedding.EMBEDDED,
                            inventory.getFonts().get(0).getEmbedding());
                    assertTrue(inventory.getFonts().get(0).isSubset());
                    assertEquals("A", extractedPageText(session, 0));

                    session.execute(positioned(1, "B", source));
                    DocumentResourceInventory expanded = session.query(
                            ExtractImagesAndResources.version1(
                                    resourceLimits(),
                                    ImageByteAccess.NONE));
                    assertEquals(1, expanded.getFonts().size());
                    assertEquals(FontResource.Embedding.EMBEDDED,
                            expanded.getFonts().get(0).getEmbedding());
                    assertTrue(expanded.getFonts().get(0).isSubset());
                    assertEquals("AB", extractedPageText(session, 0));
                    return null;
                });
    }

    @Test
    public void laterDocumentCommandSurvivesFontFinalization()
            throws Exception {
        FontSource source = FontSource.bytes(
                fontBytes("FolioPrimary.ttf.base64"));
        Path target = temporaryFolder.getRoot().toPath()
                .resolve("font-command-order.pdf");

        new DocumentWorkflow().execute(
                WorkflowRequest.create(target, SaveMode.REWRITE),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    session.execute(positioned(1, "A", source));
                    FontResource font = session.query(
                            ExtractImagesAndResources.version1(
                                    resourceLimits(),
                                    ImageByteAccess.NONE))
                            .getFonts().get(0);
                    PdfDictionary type0 = dictionary(
                            session,
                            inspect(session, font.getObjectReference().get()));
                    PdfArray descendants = (PdfArray) type0.get(
                            PdfName.of("DescendantFonts"));
                    PdfIndirectReference descendantReference =
                            (PdfIndirectReference) descendants.get(0);
                    PdfDictionary descendant = dictionary(
                            session,
                            inspect(
                                    session,
                                    descendantReference.getReference()));
                    PdfIndirectReference descriptorReference =
                            (PdfIndirectReference) descendant.get(
                                    PdfName.of("FontDescriptor"));
                    session.execute(positioned(1, "B", source));
                    PdfName type0Probe = PdfName.of("FolioOrderProbe");
                    PdfName descendantProbe = PdfName.of(
                            "FolioDescendantOrderProbe");
                    PdfName descriptorProbe = PdfName.of(
                            "FolioDescriptorOrderProbe");
                    PdfName type0Expected = PdfName.of("AfterFontCommand");
                    PdfName descendantExpected = PdfName.of(
                            "AfterDescendantFontCommand");
                    PdfName descriptorExpected = PdfName.of(
                            "AfterDescriptorFontCommand");
                    session.execute(DocumentPatch.builder()
                            .setDictionaryEntry(
                                    font.getObjectReference().get(),
                                    type0Probe,
                                    type0Expected)
                            .setDictionaryEntry(
                                    descendantReference.getReference(),
                                    descendantProbe,
                                    descendantExpected)
                            .setDictionaryEntry(
                                    descriptorReference.getReference(),
                                    descriptorProbe,
                                    descriptorExpected)
                            .build());

                    PdfDictionary observed = dictionary(
                            session,
                            inspect(session, font.getObjectReference().get()));
                    assertEquals(type0Expected, observed.get(type0Probe));
                    assertEquals(
                            descendantExpected,
                            dictionary(
                                    session,
                                    inspect(
                                            session,
                                            descendantReference.getReference()))
                                    .get(descendantProbe));
                    assertEquals(
                            descriptorExpected,
                            dictionary(
                                    session,
                                    inspect(
                                            session,
                                            descriptorReference.getReference()))
                                    .get(descriptorProbe));
                    assertEquals("AB", extractedPageText(session, 0));

                    session.execute(positioned(1, "Z", source));
                    PdfDictionary afterLaterFontCommand = dictionary(
                            session,
                            inspect(session, font.getObjectReference().get()));
                    assertEquals(
                            type0Expected,
                            afterLaterFontCommand.get(type0Probe));
                    assertEquals(
                            descendantExpected,
                            dictionary(
                                    session,
                                    inspect(
                                            session,
                                            descendantReference.getReference()))
                                    .get(descendantProbe));
                    assertEquals(
                            descriptorExpected,
                            dictionary(
                                    session,
                                    inspect(
                                            session,
                                            descriptorReference.getReference()))
                                    .get(descriptorProbe));
                    assertEquals("ABZ", extractedPageText(session, 0));
                    return null;
                });
    }

    @Test
    public void everyLimitAcceptsExactBoundaryAndRejectsFirstExcess()
            throws Exception {
        byte[] primary = fontBytes("FolioPrimary.ttf.base64");
        Path exact = temporaryFolder.getRoot().toPath().resolve("exact-limits.pdf");
        createOne(exact, FontSource.bytes(primary), "A",
                limits(1, 972L, 1, 1L, 60L));
        assertTrue(Files.size(exact) > 0L);

        FontLimits[] firstExcess = {
            limits(0, 972L, 1, 1L, 60L),
            limits(1, 971L, 1, 1L, 60L),
            limits(1, 972L, 0, 1L, 60L),
            limits(1, 972L, 1, 0L, 60L),
            limits(1, 972L, 1, 1L, 59L)
        };
        for (int index = 0; index < firstExcess.length; index++) {
            assertFontFailure(
                    "limit-" + index + ".pdf",
                    FontSource.bytes(primary),
                    "A",
                    firstExcess[index],
                    DocumentFailureCode.FONT_LIMIT_EXCEEDED,
                    "The font operation limit was exceeded.");
        }

        FontSource duplicate = FontSource.bytes(primary);
        Path duplicateExact = temporaryFolder.getRoot().toPath()
                .resolve("duplicate-source-exact.pdf");
        createOne(
                duplicateExact,
                FontSelection.explicit(duplicate, duplicate),
                "A",
                limits(2, 1944L, 1, 1L, 60L));
        assertFontFailure(
                "duplicate-source-count-excess.pdf",
                FontSelection.explicit(duplicate, duplicate),
                "A",
                limits(1, 1944L, 1, 1L, 60L),
                DocumentFailureCode.FONT_LIMIT_EXCEEDED,
                "The font operation limit was exceeded.");
        assertFontFailure(
                "duplicate-source-bytes-excess.pdf",
                FontSelection.explicit(duplicate, duplicate),
                "A",
                limits(2, 1943L, 1, 1L, 60L),
                DocumentFailureCode.FONT_LIMIT_EXCEEDED,
                "The font operation limit was exceeded.");
    }

    @Test
    public void noSubsettingPermissionEmbedsTheCompleteProgram()
            throws Exception {
        byte[] complete = withFsType(withOs2Version(
                fontBytes("FolioPrimary.ttf.base64"), 2), 0x0100);
        Path target = temporaryFolder.getRoot().toPath()
                .resolve("no-subsetting.pdf");
        createOne(target, FontSource.bytes(complete), "A",
                limits(1, complete.length, 1, 1L, 60L));

        new DocumentWorkflow().execute(
                WorkflowRequest.open(target, SaveMode.REWRITE),
                session -> {
                    FontResource font = session.query(
                            ExtractImagesAndResources.version1(
                                    resourceLimits(),
                                    ImageByteAccess.NONE))
                            .getFonts().get(0);
                    assertEquals(FontResource.Embedding.EMBEDDED,
                            font.getEmbedding());
                    assertFalse(font.isSubset());
                    assertTrue(font.getBaseFontName().get().getValue()
                            .endsWith("FolioPrimary-Regular"));
                    assertEquals(5, sfntGlyphCount(
                            embeddedFontBytes(session, font)));
                    return null;
                });
    }

    @Test
    public void referenceSetRejectsCallerOwnedOneShotSources() throws Exception {
        byte[] bytes = fontBytes("FolioPrimary.ttf.base64");
        try {
            ReferenceFontSet.version1(FontSource.stream(
                    new ByteArrayInputStream(bytes)));
            fail("streams cannot be installed in a shared environment");
        } catch (IllegalArgumentException expected) {
            assertEquals("Reference Font sources must be reusable.",
                    expected.getMessage());
        }
        try {
            ReferenceFontSet.version1(FontSource.channel(
                    new TrackingChannel(bytes)));
            fail("channels cannot be installed in a shared environment");
        } catch (IllegalArgumentException expected) {
            assertEquals("Reference Font sources must be reusable.",
                    expected.getMessage());
        }
    }

    @Test
    public void caughtPreflightFailureDoesNotLeakGlyphsIntoLaterSubset()
            throws Exception {
        FontSource source = FontSource.bytes(
                fontBytes("FolioPrimary.ttf.base64"));
        Path target = temporaryFolder.getRoot().toPath()
                .resolve("atomic-command.pdf");

        new DocumentWorkflow().execute(
                WorkflowRequest.create(target, SaveMode.REWRITE),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    try {
                        session.execute(DrawPositionedUnicodeText.version1(
                                1,
                                PositionedUnicodeText.version1(
                                        "Z",
                                        FontSelection.explicit(source),
                                        12d,
                                        TextRenderingMode.FILL,
                                        CanvasMatrix.of(
                                                1d, 0d, 0d, 1d, 36d, 72d)),
                                limits(1, 972L, 1, 1L, 59L)));
                        fail("generated-byte preflight must reject Z");
                    } catch (DocumentFailure expected) {
                        assertEquals(DocumentFailureCode.FONT_LIMIT_EXCEEDED,
                                expected.getCode());
                    }
                    session.execute(positioned(1, "A", source));
                    return null;
                });

        new DocumentWorkflow().execute(
                WorkflowRequest.open(target, SaveMode.REWRITE),
                session -> {
                    FontResource font = session.query(
                            ExtractImagesAndResources.version1(
                                    resourceLimits(),
                                    ImageByteAccess.NONE))
                            .getFonts().get(0);
                    assertTrue(sfntGlyphCount(
                            embeddedFontBytes(session, font)) < 5);
                    assertEquals("A", extractedPageText(session, 0));
                    return null;
                });

        Path clean = temporaryFolder.getRoot().toPath()
                .resolve("atomic-command-clean.pdf");
        createOne(clean, FontSource.bytes(
                fontBytes("FolioPrimary.ttf.base64")), "A",
                limits(1, 972L, 1, 1L, 60L));
        assertArrayEquals(readOnlyEmbeddedFont(clean),
                readOnlyEmbeddedFont(target));
    }

    @Test
    public void unsignedIncrementalPublicationPreservesRevisionAndNewFont()
            throws Exception {
        byte[] bytes = fontBytes("FolioPrimary.ttf.base64");
        Path source = temporaryFolder.getRoot().toPath()
                .resolve("incremental-font-source.pdf");
        Path target = temporaryFolder.getRoot().toPath()
                .resolve("incremental-font-output.pdf");
        createOne(source, FontSource.bytes(bytes), "A",
                limits(1, 972L, 1, 1L, 60L));
        byte[] original = Files.readAllBytes(source);

        WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(
                WorkflowRequest.builder()
                        .source("input", DocumentSource.path(source))
                        .primarySource("input")
                        .target("output", PublicationTarget.path(target))
                        .saveMode(SaveMode.INCREMENTAL)
                        .build(),
                session -> {
                    session.execute(DrawPositionedUnicodeText.version1(
                            1,
                            PositionedUnicodeText.version1(
                                    "B",
                                    FontSelection.explicit(
                                            FontSource.bytes(bytes)),
                                    12d,
                                    TextRenderingMode.FILL,
                                    CanvasMatrix.of(
                                            1d, 0d, 0d, 1d, 36d, 90d)),
                            limits(1, 972L, 1, 1L, 60L)));
                    return null;
                });

        assertEquals(CAPABILITY, outcome.getCapabilityId());
        assertEquals(PublicationStatus.COMMITTED,
                outcome.getPublicationReceipts().get(0).getStatus());
        byte[] published = Files.readAllBytes(target);
        assertTrue(published.length > original.length);
        assertArrayEquals(original, Arrays.copyOf(published, original.length));

        new DocumentWorkflow().execute(
                WorkflowRequest.open(target, SaveMode.REWRITE),
                session -> {
                    DocumentResourceInventory inventory = session.query(
                            ExtractImagesAndResources.version1(
                                    resourceLimits(),
                                    ImageByteAccess.NONE));
                    assertEquals(2, inventory.getFonts().size());
                    assertTrue(inventory.getFonts().stream().allMatch(font ->
                            font.getEmbedding()
                                    == FontResource.Embedding.EMBEDDED));
                    String pageText = extractedPageText(session, 0);
                    assertTrue(pageText.contains("A"));
                    assertTrue(pageText.contains("B"));
                    return null;
                });
    }

    @Test
    public void supplementaryMappingsRejectIncrementalPdfBeforeVersion15()
            throws Exception {
        byte[] primary = fontBytes("FolioPrimary.ttf.base64");
        byte[] supplementary = mapZToSupplementary(primary);
        assertPdf14MappingVersionFailure(
                "requested-supplementary",
                new String(Character.toChars(0x1f600)),
                supplementary);
        assertPdf14MappingVersionFailure(
                "full-embedding-supplementary-cmap",
                "A",
                withFsType(
                        withOs2Version(supplementary, 2),
                        0x0100));
        assertPdf14MappingVersionFailure(
                "composite-dependency-supplementary-cmap",
                "A",
                mapZToSupplementary(withTranslatedComposite(primary)));
    }

    @Test
    public void positionedTextRejectsIncrementalPdfBeforeVersion12()
            throws Exception {
        byte[] sourceBytes = minimalPdf("1.1");
        Path source = temporaryFolder.getRoot().toPath()
                .resolve("pdf11-positioned-source.pdf");
        Path target = temporaryFolder.getRoot().toPath()
                .resolve("pdf11-positioned-target.pdf");
        Files.write(source, sourceBytes);
        Files.write(target, SENTINEL);
        FontSource font = FontSource.bytes(
                fontBytes("FolioPrimary.ttf.base64"));

        try {
            new DocumentWorkflow().execute(
                    WorkflowRequest.builder()
                            .source("input", DocumentSource.path(source))
                            .primarySource("input")
                            .target("output", PublicationTarget.path(target))
                            .saveMode(SaveMode.INCREMENTAL)
                            .build(),
                    session -> {
                        session.execute(positioned(1, "A", font));
                        return null;
                    });
            fail("Type 0 fonts require PDF 1.2");
        } catch (DocumentFailure failure) {
            assertPolicyFailure(
                    failure,
                    DocumentFailureCode.PDF_VERSION_UNSUPPORTED,
                    "Positioned Unicode text requires PDF 1.2 or newer.");
        }

        assertArrayEquals(sourceBytes, Files.readAllBytes(source));
        assertArrayEquals(SENTINEL, Files.readAllBytes(target));
    }

    @Test
    public void signatureAndPasswordPoliciesRejectBeforePublication()
            throws Exception {
        FontSource font = FontSource.bytes(
                fontBytes("FolioPrimary.ttf.base64"));
        Path signed = temporaryFolder.getRoot().toPath()
                .resolve("signed-font-source.pdf");
        Path signedTarget = temporaryFolder.getRoot().toPath()
                .resolve("signed-font-target.pdf");
        Files.write(signed,
                ProjectOwnedSignatureFixtures.ordinaryApprovalSignature());
        Files.write(signedTarget, SENTINEL);
        byte[] signedBefore = Files.readAllBytes(signed);

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
                        session.execute(positioned(1, "A", font));
                        return null;
                    });
            fail("Existing Signatures must reject positioned text");
        } catch (DocumentFailure failure) {
            assertPolicyFailure(
                    failure,
                    DocumentFailureCode.SIGNATURE_POLICY_REJECTED,
                    "The Existing Signature policy does not permit positioned text.");
        }
        assertArrayEquals(signedBefore, Files.readAllBytes(signed));
        assertArrayEquals(SENTINEL, Files.readAllBytes(signedTarget));

        PasswordCredential owner = PasswordCredential.of(
                new char[] {'o', 'w', 'n', 'e', 'r'});
        PasswordCredential user = PasswordCredential.of(
                new char[] {'u', 's', 'e', 'r'});
        try {
            Path protectedSource = temporaryFolder.getRoot().toPath()
                    .resolve("protected-font-source.pdf");
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

            Path protectedTarget = temporaryFolder.getRoot().toPath()
                    .resolve("protected-font-target.pdf");
            Files.write(protectedTarget, SENTINEL);
            byte[] protectedBefore = Files.readAllBytes(protectedSource);
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
                            session.execute(positioned(1, "A", font));
                            return null;
                        });
                fail("restricted user must not modify positioned text");
            } catch (DocumentFailure failure) {
                assertPolicyFailure(
                        failure,
                        DocumentFailureCode.DOCUMENT_PERMISSION_DENIED,
                        "The Source credential does not authorize positioned text.");
            }
            assertArrayEquals(protectedBefore, Files.readAllBytes(
                    protectedSource));
            assertArrayEquals(SENTINEL, Files.readAllBytes(protectedTarget));
        } finally {
            owner.close();
            user.close();
        }
    }

    private static DrawPositionedUnicodeText positioned(
            int page,
            String text,
            FontSource source) {
        return DrawPositionedUnicodeText.version1(
                page,
                PositionedUnicodeText.version1(
                        text,
                        FontSelection.explicit(source),
                        12d,
                        TextRenderingMode.FILL,
                        CanvasMatrix.of(1d, 0d, 0d, 1d, 36d, 72d)),
                limits(1, 972L, 1, 1L, 4096L));
    }

    private void assertPdf14MappingVersionFailure(
            String caseName,
            String text,
            byte[] fontBytes) throws Exception {
        byte[] sourceBytes = minimalPdf("1.4");
        Path source = temporaryFolder.getRoot().toPath()
                .resolve(caseName + "-source.pdf");
        Path target = temporaryFolder.getRoot().toPath()
                .resolve(caseName + "-target.pdf");
        Files.write(source, sourceBytes);
        Files.write(target, SENTINEL);
        int codePoints = text.codePointCount(0, text.length());

        try {
            new DocumentWorkflow().execute(
                    WorkflowRequest.builder()
                            .source("input", DocumentSource.path(source))
                            .primarySource("input")
                            .target("output", PublicationTarget.path(target))
                            .saveMode(SaveMode.INCREMENTAL)
                            .build(),
                    session -> {
                        session.execute(DrawPositionedUnicodeText.version1(
                                1,
                                PositionedUnicodeText.version1(
                                        text,
                                        FontSelection.explicit(
                                                FontSource.bytes(fontBytes)),
                                        12d,
                                        TextRenderingMode.FILL,
                                        CanvasMatrix.of(
                                                1d, 0d, 0d, 1d, 36d, 72d)),
                                limits(
                                        1,
                                        fontBytes.length,
                                        codePoints,
                                        codePoints,
                                        4096L)));
                        return null;
                    });
            fail(caseName + " requires PDF 1.5");
        } catch (DocumentFailure failure) {
            assertPolicyFailure(
                    failure,
                    DocumentFailureCode.PDF_VERSION_UNSUPPORTED,
                    "Supplementary Unicode mappings require PDF 1.5 or newer.");
        }

        assertArrayEquals(sourceBytes, Files.readAllBytes(source));
        assertArrayEquals(SENTINEL, Files.readAllBytes(target));
    }

    private void createOne(
            Path target,
            FontSource source,
            String text,
            FontLimits limits) throws Exception {
        createOne(target, FontSelection.explicit(source), text, limits);
    }

    private void createOne(
            Path target,
            FontSelection selection,
            String text,
            FontLimits limits) throws Exception {
        new DocumentWorkflow().execute(
                WorkflowRequest.create(target, SaveMode.REWRITE),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    session.execute(DrawPositionedUnicodeText.version1(
                            1,
                            PositionedUnicodeText.version1(
                                    text,
                                    selection,
                                    12d,
                                    TextRenderingMode.FILL,
                                    CanvasMatrix.of(
                                            1d, 0d, 0d, 1d, 36d, 72d)),
                            limits));
                    return null;
                });
    }

    private void assertFontFailure(
            String targetName,
            FontSource source,
            String text,
            FontLimits limits,
            DocumentFailureCode code,
            String diagnostic) throws Exception {
        assertFontFailure(
                targetName,
                FontSelection.explicit(source),
                text,
                limits,
                code,
                diagnostic);
    }

    private void assertFontProfileFailures(
            DocumentFailureCode code,
            String diagnostic,
            FontProfileCase... profiles) throws Exception {
        for (FontProfileCase profile : profiles) {
            try {
                assertFontFailure(
                        profile.name + "-secret.pdf",
                        FontSource.bytes(profile.font),
                        "A",
                        limits(1, profile.font.length, 1, 1L, 4096L),
                        code,
                        diagnostic);
            } catch (AssertionError failure) {
                throw new AssertionError(
                        "font profile case " + profile.name, failure);
            }
        }
    }

    private static FontProfileCase profileCase(String name, byte[] font) {
        return new FontProfileCase(name, font);
    }

    private void assertFontFailure(
            String targetName,
            FontSelection selection,
            String text,
            FontLimits limits,
            DocumentFailureCode code,
            String diagnostic) throws Exception {
        Path target = temporaryFolder.getRoot().toPath().resolve(targetName);
        try {
            createOne(target, selection, text, limits);
            fail("expected " + code);
        } catch (DocumentFailure failure) {
            assertEquals(code, failure.getCode());
            assertEquals(CAPABILITY, failure.getCapabilityId());
            assertEquals(diagnostic, failure.getDiagnostic());
            assertEquals(diagnostic, failure.getMessage());
            assertNull(failure.getCause());
            assertEquals(1, failure.getPublicationReceipts().size());
            assertEquals(PublicationStatus.NOT_ATTEMPTED,
                    failure.getPublicationReceipts().get(0).getStatus());
            assertFalse(failure.getDiagnostic().contains("secret"));
        }
        assertFalse("failed command must not publish", Files.exists(target));
    }

    private static void assertPolicyFailure(
            DocumentFailure failure,
            DocumentFailureCode code,
            String diagnostic) {
        assertEquals(code, failure.getCode());
        assertEquals(CAPABILITY, failure.getCapabilityId());
        assertEquals(diagnostic, failure.getDiagnostic());
        assertNull(failure.getCause());
        assertEquals(1, failure.getPublicationReceipts().size());
        assertEquals(PublicationStatus.NOT_ATTEMPTED,
                failure.getPublicationReceipts().get(0).getStatus());
    }

    private static String extractedPageText(
            net.zerocloud.pdf.DocumentSession session,
            int pageIndex) throws net.zerocloud.pdf.DocumentFailure {
        return session.query(ExtractTextAndStructure.version1(textLimits()))
                .getPages().get(pageIndex).getText();
    }

    private static FontLimits limits(
            int sources,
            long bytes,
            int codePoints,
            long checks,
            long generatedBytes) {
        return FontLimits.builder()
                .maximumFontSources(sources)
                .maximumSourceBytes(bytes)
                .maximumCodePoints(codePoints)
                .maximumFallbackChecks(checks)
                .maximumGeneratedContentBytes(generatedBytes)
                .build();
    }

    private static ResourceExtractionLimits resourceLimits() {
        return ResourceExtractionLimits.builder()
                .maximumPages(4)
                .maximumPageTreeNodes(16)
                .maximumTraversedResourceValues(256L)
                .maximumResourceTraversalDepth(8)
                .maximumDecodedPixels(0L)
                .maximumDecompressedBytes(2L * 1024L * 1024L)
                .maximumReturnedBytes(0L)
                .build();
    }

    private static ExtractionLimits textLimits() {
        return ExtractionLimits.builder()
                .maximumPages(4)
                .maximumPageTreeNodes(16)
                .maximumContentStreams(16)
                .maximumContentStreamDepth(4)
                .maximumDecodedBytes(2L * 1024L * 1024L)
                .maximumTextItems(16)
                .maximumUnicodeCodePoints(64)
                .maximumToUnicodeMappings(64)
                .maximumFontDataEntries(64)
                .maximumMarkedContentSequences(8)
                .maximumMarkedContentDepth(4)
                .maximumStructureElements(8)
                .maximumStructureItems(8)
                .maximumStructureDepth(4)
                .maximumRoleMappings(4)
                .build();
    }

    private static byte[] fontBytes(String name) throws Exception {
        String resource = "/net/zerocloud/pdf/fixtures/" + name;
        try (InputStream input = FontLoadingWorkflowTest.class
                .getResourceAsStream(resource)) {
            if (input == null) {
                throw new AssertionError("Missing test font " + resource);
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                bytes.write(buffer, 0, count);
            }
            return Base64.getMimeDecoder().decode(bytes.toByteArray());
        }
    }

    private static byte[] withAppleTrueTypeScaler(byte[] original) {
        byte[] copy = Arrays.copyOf(original, original.length);
        byte[] scaler = "true".getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(scaler, 0, copy, 0, scaler.length);
        repairChecksums(copy);
        return copy;
    }

    private static byte[] withShortLoca(byte[] original) {
        int[] offsets = glyphOffsets(original);
        byte[] loca = new byte[2 * offsets.length];
        for (int index = 0; index < offsets.length; index++) {
            if ((offsets[index] & 1) != 0 || offsets[index] > 0x1fffe) {
                throw new AssertionError("fixture cannot use short loca");
            }
            writeShort(loca, 2 * index, offsets[index] / 2);
        }
        byte[] copy = replaceTable(original, "loca", loca);
        writeShort(copy, tableOffset(copy, "head") + 50, 0);
        repairChecksums(copy);
        return copy;
    }

    private static byte[] withFormat4Cmap(
            byte[] original,
            int platform,
            int encoding) {
        byte[] cmap = new byte[60];
        writeShort(cmap, 2, 1);
        writeShort(cmap, 4, platform);
        writeShort(cmap, 6, encoding);
        writeInt(cmap, 8, 12);
        int subtable = 12;
        writeShort(cmap, subtable, 4);
        writeShort(cmap, subtable + 2, 48);
        writeShort(cmap, subtable + 6, 8);
        writeShort(cmap, subtable + 8, 8);
        writeShort(cmap, subtable + 10, 2);
        int[] ends = {0x20, 0x42, 0x5a, 0xffff};
        int[] starts = {0x20, 0x41, 0x5a, 0xffff};
        int[] deltas = {-31, -63, -86, 1};
        for (int index = 0; index < ends.length; index++) {
            writeShort(cmap, subtable + 14 + 2 * index, ends[index]);
            writeShort(cmap, subtable + 24 + 2 * index, starts[index]);
            writeShort(cmap, subtable + 32 + 2 * index, deltas[index]);
        }
        return replaceTable(original, "cmap", cmap);
    }

    private static byte[] withFormat12Platform(
            byte[] original,
            int platform,
            int encoding) {
        byte[] copy = Arrays.copyOf(original, original.length);
        int cmap = tableOffset(copy, "cmap");
        writeShort(copy, cmap + 4, platform);
        writeShort(copy, cmap + 6, encoding);
        repairChecksums(copy);
        return copy;
    }

    private static byte[] withPostVersion3(byte[] original) {
        byte[] post = Arrays.copyOf(tableBytes(original, "post"), 32);
        writeInt(post, 0, 0x00030000);
        return replaceTable(original, "post", post);
    }

    private static byte[] withPostVersion1(byte[] original) {
        byte[] post = Arrays.copyOf(tableBytes(original, "post"), 32);
        writeInt(post, 0, 0x00010000);
        return replaceTable(original, "post", post);
    }

    private static byte[] withPostCustomName(
            byte[] original,
            String customName) {
        byte[] current = tableBytes(original, "post");
        byte[] name = customName.getBytes(StandardCharsets.US_ASCII);
        byte[] post = Arrays.copyOf(current, current.length + 1 + name.length);
        writeShort(post, 34 + 2 * 2, 258);
        post[current.length] = (byte) name.length;
        System.arraycopy(name, 0, post, current.length + 1, name.length);
        return replaceTable(original, "post", post);
    }

    private static byte[] withPostMemoryEstimates(byte[] original) {
        byte[] copy = Arrays.copyOf(original, original.length);
        int post = tableOffset(copy, "post");
        writeInt(copy, post + 16, 1);
        writeInt(copy, post + 20, 2);
        writeInt(copy, post + 24, 3);
        writeInt(copy, post + 28, 4);
        repairChecksums(copy);
        return copy;
    }

    private static byte[] withInvalidPostMemory(byte[] original) {
        byte[] copy = Arrays.copyOf(original, original.length);
        int post = tableOffset(copy, "post");
        writeInt(copy, post + 16, 2);
        writeInt(copy, post + 20, 1);
        repairChecksums(copy);
        return copy;
    }

    private static byte[] withSwappedNameRecords(byte[] original) {
        byte[] copy = Arrays.copyOf(original, original.length);
        int name = tableOffset(copy, "name");
        byte[] first = Arrays.copyOfRange(copy, name + 6, name + 18);
        System.arraycopy(copy, name + 18, copy, name + 6, 12);
        System.arraycopy(first, 0, copy, name + 18, 12);
        repairChecksums(copy);
        return copy;
    }

    private static byte[] withAdditionalNameRecord(
            byte[] original,
            int nameId) {
        byte[] current = tableBytes(original, "name");
        int count = unsignedShort(current, 2);
        int storage = unsignedShort(current, 4);
        int lastRecord = 6 + 12 * (count - 1);
        if (count == 0
                || storage != 6 + 12 * count
                || unsignedShort(current, lastRecord + 6) >= nameId) {
            throw new AssertionError("fixture cannot append sorted name ID");
        }
        int storageLength = current.length - storage;
        byte[] name = new byte[current.length + 14];
        System.arraycopy(current, 0, name, 0, storage);
        writeShort(name, 2, count + 1);
        writeShort(name, 4, storage + 12);
        int record = storage;
        writeShort(name, record, 3);
        writeShort(name, record + 2, 1);
        writeShort(name, record + 4, 0x0409);
        writeShort(name, record + 6, nameId);
        writeShort(name, record + 8, 2);
        writeShort(name, record + 10, storageLength);
        System.arraycopy(
                current,
                storage,
                name,
                storage + 12,
                storageLength);
        writeShort(name, name.length - 2, 'X');
        return replaceTable(original, "name", name);
    }

    private static byte[] withInvalidPostScriptName(byte[] original) {
        byte[] copy = Arrays.copyOf(original, original.length);
        int name = tableOffset(copy, "name");
        int count = unsignedShort(copy, name + 2);
        int storage = unsignedShort(copy, name + 4);
        for (int index = 0; index < count; index++) {
            int record = name + 6 + 12 * index;
            if (unsignedShort(copy, record + 6) == 6) {
                int offset = unsignedShort(copy, record + 10);
                writeShort(copy, name + storage + offset, '[');
                repairChecksums(copy);
                return copy;
            }
        }
        throw new AssertionError("fixture has no PostScript name");
    }

    private static byte[] withInvalidVersionName(byte[] original) {
        byte[] copy = Arrays.copyOf(original, original.length);
        int name = tableOffset(copy, "name");
        int count = unsignedShort(copy, name + 2);
        int storage = unsignedShort(copy, name + 4);
        for (int index = 0; index < count; index++) {
            int record = name + 6 + 12 * index;
            if (unsignedShort(copy, record + 6) == 5) {
                int length = unsignedShort(copy, record + 8);
                int offset = unsignedShort(copy, record + 10);
                for (int character = 0;
                        character < length / 2;
                        character++) {
                    writeShort(
                            copy,
                            name + storage + offset + 2 * character,
                            'X');
                }
                repairChecksums(copy);
                return copy;
            }
        }
        throw new AssertionError("fixture has no version name");
    }

    private static byte[] withInvalidNameSurrogate(byte[] original) {
        return withFirstNameCodeUnits(original, 0xd800);
    }

    private static byte[] withSupplementaryNameCharacter(byte[] original) {
        return withFirstNameCodeUnits(original, 0xd83d, 0xde00);
    }

    private static byte[] withFirstNameCodeUnits(
            byte[] original,
            int... codeUnits) {
        byte[] copy = Arrays.copyOf(original, original.length);
        int name = tableOffset(copy, "name");
        int storage = unsignedShort(copy, name + 4);
        int firstRecord = name + 6;
        int length = unsignedShort(copy, firstRecord + 8);
        int offset = unsignedShort(copy, firstRecord + 10);
        if (2 * codeUnits.length > length) {
            throw new AssertionError("fixture name record is too short");
        }
        for (int index = 0; index < codeUnits.length; index++) {
            writeShort(
                    copy,
                    name + storage + offset + 2 * index,
                    codeUnits[index]);
        }
        repairChecksums(copy);
        return copy;
    }

    private static byte[] withFormat1Name(byte[] original) {
        byte[] current = tableBytes(original, "name");
        int recordsEnd = 6 + 12 * unsignedShort(current, 2);
        byte[] name = new byte[current.length + 2];
        System.arraycopy(current, 0, name, 0, recordsEnd);
        writeShort(name, 0, 1);
        writeShort(name, 4, recordsEnd + 2);
        writeShort(name, recordsEnd, 0);
        System.arraycopy(
                current,
                recordsEnd,
                name,
                recordsEnd + 2,
                current.length - recordsEnd);
        return replaceTable(original, "name", name);
    }

    private static byte[] withPaddedFormat0Name(byte[] original) {
        byte[] current = tableBytes(original, "name");
        int recordsEnd = 6 + 12 * unsignedShort(current, 2);
        byte[] name = new byte[current.length + 2];
        System.arraycopy(current, 0, name, 0, recordsEnd);
        writeShort(name, 4, recordsEnd + 2);
        System.arraycopy(
                current,
                recordsEnd,
                name,
                recordsEnd + 2,
                current.length - recordsEnd);
        return replaceTable(original, "name", name);
    }

    private static byte[] withTrailingNameStorage(byte[] original) {
        byte[] current = tableBytes(original, "name");
        return replaceTable(
                original,
                "name",
                Arrays.copyOf(current, current.length + 2));
    }

    private static byte[] withOs2Version(byte[] original, int version) {
        int length;
        if (version == 0) {
            length = 78;
        } else if (version == 1) {
            length = 86;
        } else if (version <= 4) {
            length = 96;
        } else if (version == 5) {
            length = 100;
        } else {
            throw new AssertionError("unsupported fixture OS/2 version");
        }
        byte[] os2 = Arrays.copyOf(tableBytes(original, "OS/2"), length);
        writeShort(os2, 0, version);
        if (version == 5) {
            writeShort(os2, 96, 0);
            writeShort(os2, 98, 0xffff);
        }
        return replaceTable(original, "OS/2", os2);
    }

    private static byte[] withLegacyShortOs2Version0(byte[] original) {
        byte[] os2 = Arrays.copyOf(tableBytes(original, "OS/2"), 68);
        return replaceTable(original, "OS/2", os2);
    }

    private static byte[] withInvalidVersion5OpticalRange(byte[] original) {
        byte[] copy = withOs2Version(original, 5);
        int os2 = tableOffset(copy, "OS/2");
        writeShort(copy, os2 + 96, 0);
        writeShort(copy, os2 + 98, 0);
        repairChecksums(copy);
        return copy;
    }

    private static byte[] withOs2VersionAndSelection(
            byte[] original,
            int version,
            int selection,
            int macStyle) {
        byte[] copy = withOs2Version(original, version);
        writeShort(copy, tableOffset(copy, "OS/2") + 62, selection);
        writeShort(copy, tableOffset(copy, "head") + 44, macStyle);
        repairChecksums(copy);
        return copy;
    }

    private static byte[] withTranslatedComposite(byte[] original) {
        byte[] glyph = translatedCompositeGlyph(0x0003, 18);
        return withProfiledComposite(original, glyph, 8, 1, 0, 1);
    }

    private static byte[] withGlyphInstructions(byte[] original) {
        byte[] glyph = translatedCompositeGlyph(0x0103, 32);
        writeShort(glyph, 18, 12);
        return withProfiledComposite(original, glyph, 8, 1, 12, 1);
    }

    private static byte[] withAlternateCompositeMarker(byte[] original) {
        byte[] copy = withTranslatedComposite(original);
        int glyph = tableOffset(copy, "glyf") + glyphOffsets(copy)[2];
        writeShort(copy, glyph, 0xfffe);
        repairChecksums(copy);
        return copy;
    }

    private static byte[] withUnsupportedCompositeTransform(byte[] original) {
        byte[] glyph = translatedCompositeGlyph(0x000b, 20);
        writeShort(glyph, 18, 0x4000);
        byte[] copy = replaceGlyph(original, 2, glyph);
        repairChecksums(copy);
        return copy;
    }

    private static byte[] withUnsupportedCompositePointAttachment(
            byte[] original) {
        byte[] glyph = twoComponentCompositeGlyph(
                0x0023, 4, 0x0001, 4);
        byte[] copy = replaceGlyph(original, 2, glyph);
        repairChecksums(copy);
        return copy;
    }

    private static byte[] withUseMyMetricsComposite(byte[] original) {
        byte[] copy = withTranslatedComposite(original);
        int glyph = tableOffset(copy, "glyf") + glyphOffsets(copy)[2];
        writeShort(copy, glyph + 10, 0x0203);
        repairChecksums(copy);
        return copy;
    }

    private static byte[] withLateCompositeOverlapFlag(byte[] original) {
        byte[] glyph = twoComponentCompositeGlyph(
                0x0023, 0, 0x0403, 4);
        return withProfiledComposite(original, glyph, 11, 2, 0, 2);
    }

    private static byte[] withProfiledComposite(
            byte[] original,
            byte[] glyph,
            int maximumPoints,
            int maximumContours,
            int maximumInstructionBytes,
            int maximumElements) {
        byte[] copy = replaceGlyph(original, 2, glyph);
        int maxp = tableOffset(copy, "maxp");
        writeShort(copy, maxp + 10, maximumPoints);
        writeShort(copy, maxp + 12, maximumContours);
        writeShort(copy, maxp + 26, maximumInstructionBytes);
        writeShort(copy, maxp + 28, maximumElements);
        writeShort(copy, maxp + 30, 1);
        int head = tableOffset(copy, "head");
        writeShort(copy, head + 36, Math.min(
                signedShort(copy, head + 36), signedShort(glyph, 2)));
        writeShort(copy, head + 38, Math.min(
                signedShort(copy, head + 38), signedShort(glyph, 4)));
        writeShort(copy, head + 40, Math.max(
                signedShort(copy, head + 40), signedShort(glyph, 6)));
        writeShort(copy, head + 42, Math.max(
                signedShort(copy, head + 42), signedShort(glyph, 8)));
        repairChecksums(copy);
        return copy;
    }

    private static byte[] twoComponentCompositeGlyph(
            int firstFlags,
            int firstGlyph,
            int secondFlags,
            int secondGlyph) {
        byte[] glyph = new byte[26];
        writeShort(glyph, 0, 0xffff);
        writeShort(glyph, 2, 40);
        writeShort(glyph, 6, 560);
        writeShort(glyph, 8, 700);
        writeShort(glyph, 10, firstFlags);
        writeShort(glyph, 12, firstGlyph);
        writeShort(glyph, 18, secondFlags);
        writeShort(glyph, 20, secondGlyph);
        return glyph;
    }

    private static byte[] translatedCompositeGlyph(int flags, int length) {
        byte[] glyph = new byte[length];
        writeShort(glyph, 0, 0xffff);
        writeShort(glyph, 2, 50);
        writeShort(glyph, 4, 5);
        writeShort(glyph, 6, 570);
        writeShort(glyph, 8, 705);
        writeShort(glyph, 10, flags);
        writeShort(glyph, 12, 4);
        writeShort(glyph, 14, 10);
        writeShort(glyph, 16, 5);
        return glyph;
    }

    private static byte[] withInvalidFormat4Sentinel(byte[] original) {
        byte[] current = tableBytes(withFormat4Cmap(original, 0, 3), "cmap");
        byte[] cmap = Arrays.copyOf(current, current.length + 4);
        int subtable = 12;
        writeShort(cmap, subtable + 2, 52);
        writeShort(cmap, subtable + 30, 0xfffe);
        writeShort(cmap, subtable + 46, 2);
        return replaceTable(original, "cmap", cmap);
    }

    private static byte[] withInvalidFormat4SentinelRangeOffset(
            byte[] original) {
        byte[] current = tableBytes(withFormat4Cmap(original, 0, 3), "cmap");
        byte[] cmap = Arrays.copyOf(current, current.length + 2);
        int subtable = 12;
        writeShort(cmap, subtable + 2, 50);
        writeShort(cmap, subtable + 46, 2);
        return replaceTable(original, "cmap", cmap);
    }

    private static byte[] withFormat4SurrogateSegment(byte[] original) {
        byte[] copy = withFormat4Cmap(original, 0, 3);
        int subtable = tableOffset(copy, "cmap") + 12;
        writeShort(copy, subtable + 18, 0xd800);
        writeShort(copy, subtable + 28, 0xd800);
        writeShort(copy, subtable + 36, 4 - 0xd800);
        repairChecksums(copy);
        return copy;
    }

    private static byte[] withFormat4RangeOffsetIntoHeader(byte[] original) {
        byte[] copy = withFormat4Cmap(original, 0, 3);
        int subtable = tableOffset(copy, "cmap") + 12;
        writeShort(copy, subtable + 40, 2);
        repairChecksums(copy);
        return copy;
    }

    private static byte[] withoutUnicodeMappings(byte[] original) {
        byte[] cmap = new byte[40];
        writeShort(cmap, 2, 1);
        writeShort(cmap, 6, 4);
        writeInt(cmap, 8, 12);
        writeShort(cmap, 12, 12);
        writeInt(cmap, 16, 28);
        writeInt(cmap, 24, 1);
        writeInt(cmap, 28, 0x20);
        writeInt(cmap, 32, 0x20);
        byte[] copy = replaceTable(original, "cmap", cmap);
        int os2 = tableOffset(copy, "OS/2");
        writeShort(copy, os2 + 64, 0xffff);
        writeShort(copy, os2 + 66, 0xffff);
        repairChecksums(copy);
        return copy;
    }

    private static byte[] withHeaderOnlyZeroContour(byte[] original) {
        int[] offsets = glyphOffsets(original);
        if (offsets[2] - offsets[1] != 12) {
            throw new AssertionError("unexpected project fixture space glyph");
        }
        byte[] glyf = tableBytes(original, "glyf");
        byte[] shortened = new byte[glyf.length - 2];
        System.arraycopy(glyf, 0, shortened, 0, offsets[1] + 10);
        System.arraycopy(
                glyf,
                offsets[2],
                shortened,
                offsets[1] + 10,
                glyf.length - offsets[2]);
        for (int index = 2; index < offsets.length; index++) {
            offsets[index] -= 2;
        }
        byte[] copy = replaceTable(original, "glyf", shortened);
        copy = replaceTable(copy, "loca", encodeLoca(copy, offsets));
        repairChecksums(copy);
        return copy;
    }

    private static byte[] replaceGlyph(
            byte[] original,
            int glyphId,
            byte[] replacement) {
        if ((replacement.length & 1) != 0) {
            throw new AssertionError("replacement glyph is not word-aligned");
        }
        int[] offsets = glyphOffsets(original);
        int start = offsets[glyphId];
        int end = offsets[glyphId + 1];
        byte[] glyf = tableBytes(original, "glyf");
        byte[] changed = new byte[
                glyf.length - (end - start) + replacement.length];
        System.arraycopy(glyf, 0, changed, 0, start);
        System.arraycopy(
                replacement, 0, changed, start, replacement.length);
        System.arraycopy(
                glyf,
                end,
                changed,
                start + replacement.length,
                glyf.length - end);
        int delta = replacement.length - (end - start);
        for (int index = glyphId + 1; index < offsets.length; index++) {
            offsets[index] += delta;
        }
        byte[] copy = replaceTable(original, "glyf", changed);
        return replaceTable(copy, "loca", encodeLoca(copy, offsets));
    }

    private static int[] glyphOffsets(byte[] font) {
        int glyphCount = unsignedShort(tableBytes(font, "maxp"), 4);
        int[] result = new int[glyphCount + 1];
        int loca = tableOffset(font, "loca");
        boolean shortLoca = unsignedShort(
                font, tableOffset(font, "head") + 50) == 0;
        for (int index = 0; index < result.length; index++) {
            result[index] = shortLoca
                    ? 2 * unsignedShort(font, loca + 2 * index)
                    : unsignedInt(font, loca + 4 * index);
        }
        return result;
    }

    private static byte[] encodeLoca(byte[] font, int[] offsets) {
        boolean shortLoca = unsignedShort(
                font, tableOffset(font, "head") + 50) == 0;
        byte[] result = new byte[offsets.length * (shortLoca ? 2 : 4)];
        for (int index = 0; index < offsets.length; index++) {
            if (shortLoca) {
                writeShort(result, 2 * index, offsets[index] / 2);
            } else {
                writeInt(result, 4 * index, offsets[index]);
            }
        }
        return result;
    }

    private static byte[] tableBytes(byte[] font, String table) {
        int record = tableRecordOffset(font, table);
        int offset = unsignedInt(font, record + 8);
        int length = unsignedInt(font, record + 12);
        return Arrays.copyOfRange(font, offset, offset + length);
    }

    private static byte[] replaceTable(
            byte[] original,
            String wanted,
            byte[] replacement) {
        int tableCount = unsignedShort(original, 4);
        int cursor = 12 + 16 * tableCount;
        int total = cursor;
        for (int index = 0; index < tableCount; index++) {
            int record = 12 + 16 * index;
            String tag = new String(
                    original, record, 4, StandardCharsets.ISO_8859_1);
            int length = wanted.equals(tag)
                    ? replacement.length
                    : unsignedInt(original, record + 12);
            total += (length + 3) & ~3;
        }
        byte[] result = new byte[total];
        System.arraycopy(original, 0, result, 0, 12);
        for (int index = 0; index < tableCount; index++) {
            int oldRecord = 12 + 16 * index;
            int newRecord = oldRecord;
            String tag = new String(
                    original, oldRecord, 4, StandardCharsets.ISO_8859_1);
            byte[] table = wanted.equals(tag)
                    ? replacement
                    : tableBytes(original, tag);
            System.arraycopy(original, oldRecord, result, newRecord, 4);
            writeInt(result, newRecord + 8, cursor);
            writeInt(result, newRecord + 12, table.length);
            System.arraycopy(table, 0, result, cursor, table.length);
            cursor += (table.length + 3) & ~3;
        }
        repairChecksums(result);
        return result;
    }

    private static byte[] withExtraTable(byte[] original) {
        int oldCount = unsignedShort(original, 4);
        String[] tags = new String[oldCount + 1];
        for (int index = 0; index < oldCount; index++) {
            tags[index] = new String(
                    original,
                    12 + 16 * index,
                    4,
                    StandardCharsets.ISO_8859_1);
        }
        tags[oldCount] = "kern";
        Arrays.sort(tags);
        int directoryEnd = 12 + 16 * tags.length;
        int total = directoryEnd + 4;
        for (int index = 0; index < oldCount; index++) {
            int length = unsignedInt(original, 12 + 16 * index + 12);
            total += (length + 3) & ~3;
        }
        byte[] result = new byte[total];
        System.arraycopy(original, 0, result, 0, 4);
        writeShort(result, 4, tags.length);
        writeShort(result, 6, 128);
        writeShort(result, 8, 3);
        writeShort(result, 10, 16 * tags.length - 128);
        int cursor = directoryEnd;
        for (int index = 0; index < tags.length; index++) {
            String tag = tags[index];
            byte[] tagBytes = tag.getBytes(StandardCharsets.ISO_8859_1);
            byte[] table = "kern".equals(tag)
                    ? new byte[4]
                    : tableBytes(original, tag);
            int record = 12 + 16 * index;
            System.arraycopy(tagBytes, 0, result, record, 4);
            writeInt(result, record + 8, cursor);
            writeInt(result, record + 12, table.length);
            System.arraycopy(table, 0, result, cursor, table.length);
            cursor += (table.length + 3) & ~3;
        }
        repairChecksums(result);
        return result;
    }

    private static byte[] withNonzeroDirectoryGap(byte[] original) {
        int tableCount = unsignedShort(original, 4);
        int directoryEnd = 12 + 16 * tableCount;
        byte[] result = new byte[original.length + 4];
        System.arraycopy(original, 0, result, 0, directoryEnd);
        result[directoryEnd] = 1;
        System.arraycopy(
                original,
                directoryEnd,
                result,
                directoryEnd + 4,
                original.length - directoryEnd);
        for (int index = 0; index < tableCount; index++) {
            int record = 12 + 16 * index;
            writeInt(
                    result,
                    record + 8,
                    unsignedInt(result, record + 8) + 4);
        }
        repairChecksums(result);
        return result;
    }

    private static byte[] withFsType(byte[] original, int fsType) {
        byte[] copy = Arrays.copyOf(original, original.length);
        int tableCount = unsignedShort(copy, 4);
        for (int index = 0; index < tableCount; index++) {
            int record = 12 + 16 * index;
            String tag = new String(
                    copy, record, 4, StandardCharsets.ISO_8859_1);
            if ("OS/2".equals(tag)) {
                int offset = unsignedInt(copy, record + 8);
                copy[offset + 8] = (byte) (fsType >>> 8);
                copy[offset + 9] = (byte) fsType;
                repairChecksums(copy);
                return copy;
            }
        }
        throw new AssertionError("fixture has no OS/2 table");
    }

    private static byte[] withCompactHorizontalMetrics(byte[] original) {
        int glyphCount = unsignedShort(tableBytes(original, "maxp"), 4);
        int retainedMetrics = 3;
        byte[] current = tableBytes(original, "hmtx");
        if (glyphCount != 5 || current.length != 4 * glyphCount) {
            throw new AssertionError("unexpected project fixture metrics");
        }
        byte[] compact = new byte[4 * retainedMetrics
                + 2 * (glyphCount - retainedMetrics)];
        System.arraycopy(current, 0, compact, 0, 4 * retainedMetrics);
        for (int glyph = retainedMetrics; glyph < glyphCount; glyph++) {
            writeShort(
                    compact,
                    4 * retainedMetrics + 2 * (glyph - retainedMetrics),
                    unsignedShort(current, 4 * glyph + 2));
        }
        byte[] copy = replaceTable(original, "hmtx", compact);
        int hhea = tableOffset(copy, "hhea");
        int maximumAdvance = 0;
        for (int metric = 0; metric < retainedMetrics; metric++) {
            maximumAdvance = Math.max(
                    maximumAdvance, unsignedShort(current, 4 * metric));
        }
        writeShort(copy, hhea + 10, maximumAdvance);
        writeShort(copy, hhea + 34, retainedMetrics);
        repairChecksums(copy);
        return copy;
    }

    private static byte[] withTableShort(
            byte[] original,
            String table,
            int relativeOffset,
            int value) {
        byte[] copy = Arrays.copyOf(original, original.length);
        writeShort(copy, tableOffset(copy, table) + relativeOffset, value);
        repairChecksums(copy);
        return copy;
    }

    private static byte[] withTableInt(
            byte[] original,
            String table,
            int relativeOffset,
            int value) {
        byte[] copy = Arrays.copyOf(original, original.length);
        writeInt(copy, tableOffset(copy, table) + relativeOffset, value);
        repairChecksums(copy);
        return copy;
    }

    private static byte[] withTableLength(
            byte[] original,
            String table,
            int length) {
        byte[] copy = Arrays.copyOf(original, original.length);
        writeInt(copy, tableRecordOffset(copy, table) + 12, length);
        repairChecksums(copy);
        return copy;
    }

    private static byte[] withUnitsAndGlyphAdvance(
            byte[] original,
            int unitsPerEm,
            int glyphId,
            int advance) {
        byte[] copy = Arrays.copyOf(original, original.length);
        writeShort(copy, tableOffset(copy, "head") + 18, unitsPerEm);
        writeShort(copy, tableOffset(copy, "hhea") + 10, advance);
        writeShort(copy, tableOffset(copy, "hhea") + 14, 100);
        writeShort(copy,
                tableOffset(copy, "hmtx") + 4 * glyphId,
                advance);
        repairChecksums(copy);
        return copy;
    }

    private static byte[] withSelfReferentialComposite(
            byte[] original,
            int glyphId) {
        byte[] glyph = new byte[18];
        writeShort(glyph, 0, 0xffff);
        writeShort(glyph, 2, 40);
        writeShort(glyph, 6, 560);
        writeShort(glyph, 8, 700);
        writeShort(glyph, 10, 0x0003);
        writeShort(glyph, 12, glyphId);
        byte[] copy = replaceGlyph(original, glyphId, glyph);
        int maxp = tableOffset(copy, "maxp");
        writeShort(copy, maxp + 10, 24);
        writeShort(copy, maxp + 12, 8);
        writeShort(copy, maxp + 26, 0);
        writeShort(copy, maxp + 28, 1);
        writeShort(copy, maxp + 30, 1);
        repairChecksums(copy);
        return copy;
    }

    private static int unsignedShort(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) << 8 | bytes[offset + 1] & 0xff;
    }

    private static int signedShort(byte[] bytes, int offset) {
        return (short) unsignedShort(bytes, offset);
    }

    private static int unsignedInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) << 24
                | (bytes[offset + 1] & 0xff) << 16
                | (bytes[offset + 2] & 0xff) << 8
                | bytes[offset + 3] & 0xff;
    }

    private static byte[] mapZToGlyphA(byte[] original) {
        byte[] copy = Arrays.copyOf(original, original.length);
        int cmap = tableOffset(copy, "cmap");
        int subtable = cmap + unsignedInt(copy, cmap + 8);
        if (unsignedShort(copy, subtable) != 12) {
            throw new AssertionError("fixture cmap is not format 12");
        }
        int groupCount = unsignedInt(copy, subtable + 12);
        for (int group = 0; group < groupCount; group++) {
            int entry = subtable + 16 + group * 12;
            if (unsignedInt(copy, entry) == 'Z'
                    && unsignedInt(copy, entry + 4) == 'Z') {
                writeInt(copy, entry + 8, 2);
                repairChecksums(copy);
                return copy;
            }
        }
        throw new AssertionError("fixture has no Z cmap group");
    }

    private static byte[] symbolOnlyCmap(byte[] original) {
        byte[] copy = Arrays.copyOf(original, original.length);
        int cmap = tableOffset(copy, "cmap");
        if (unsignedShort(copy, cmap + 4) != 0
                || unsignedShort(copy, cmap + 6) != 4) {
            throw new AssertionError("fixture cmap is not Unicode full");
        }
        writeShort(copy, cmap + 4, 3);
        writeShort(copy, cmap + 6, 0);
        repairChecksums(copy);
        return copy;
    }

    private static byte[] mapZeroWidthSpaceToGlyphA(byte[] original) {
        byte[] copy = Arrays.copyOf(original, original.length);
        int cmap = tableOffset(copy, "cmap");
        int subtable = cmap + unsignedInt(copy, cmap + 8);
        int groupCount = unsignedInt(copy, subtable + 12);
        for (int group = 0; group < groupCount; group++) {
            int entry = subtable + 16 + group * 12;
            if (unsignedInt(copy, entry) == 'Z'
                    && unsignedInt(copy, entry + 4) == 'Z') {
                writeInt(copy, entry, 0x200b);
                writeInt(copy, entry + 4, 0x200b);
                writeInt(copy, entry + 8, 2);
                writeShort(copy, tableOffset(copy, "OS/2") + 66, 0x200b);
                repairChecksums(copy);
                return copy;
            }
        }
        throw new AssertionError("fixture has no Z cmap group");
    }

    private static byte[] mapZToSupplementary(byte[] original) {
        byte[] copy = Arrays.copyOf(original, original.length);
        int cmap = tableOffset(copy, "cmap");
        int subtable = cmap + unsignedInt(copy, cmap + 8);
        int groupCount = unsignedInt(copy, subtable + 12);
        for (int group = 0; group < groupCount; group++) {
            int entry = subtable + 16 + group * 12;
            if (unsignedInt(copy, entry) == 'Z'
                    && unsignedInt(copy, entry + 4) == 'Z') {
                writeInt(copy, entry, 0x1f600);
                writeInt(copy, entry + 4, 0x1f600);
                writeShort(copy, tableOffset(copy, "OS/2") + 66, 0xffff);
                repairChecksums(copy);
                return copy;
            }
        }
        throw new AssertionError("fixture has no Z cmap group");
    }

    private static byte[] embeddedFontBytes(
            net.zerocloud.pdf.DocumentSession session,
            FontResource font) throws DocumentFailure {
        PdfDictionary type0 = dictionary(session, inspect(
                session, font.getObjectReference().get()));
        PdfArray descendants = (PdfArray) resolve(
                session, type0.get(net.zerocloud.pdf.PdfName.of(
                        "DescendantFonts")));
        PdfDictionary descendant = dictionary(
                session, resolve(session, descendants.get(0)));
        PdfDictionary descriptor = dictionary(session, resolve(
                session,
                descendant.get(net.zerocloud.pdf.PdfName.of(
                        "FontDescriptor"))));
        PdfStream program = (PdfStream) resolve(
                session,
                descriptor.get(net.zerocloud.pdf.PdfName.of("FontFile2")));
        return program.readBytes();
    }

    private static byte[] toUnicodeBytes(
            net.zerocloud.pdf.DocumentSession session,
            FontResource font) throws DocumentFailure {
        PdfDictionary type0 = dictionary(session, inspect(
                session, font.getObjectReference().get()));
        PdfStream cmap = (PdfStream) resolve(
                session,
                type0.get(net.zerocloud.pdf.PdfName.of("ToUnicode")));
        return cmap.readBytes();
    }

    private static PdfDictionary dictionary(
            net.zerocloud.pdf.DocumentSession session,
            PdfValue value) throws DocumentFailure {
        return (PdfDictionary) resolve(session, value);
    }

    private static PdfValue inspect(
            net.zerocloud.pdf.DocumentSession session,
            ObjectReference reference) throws DocumentFailure {
        return session.query(InspectObject.version1(
                reference,
                PdfInspectionLimits.of(256L, 4L * 1024L * 1024L)));
    }

    private static PdfValue resolve(
            net.zerocloud.pdf.DocumentSession session,
            PdfValue value) throws DocumentFailure {
        PdfValue current = value;
        while (current instanceof PdfIndirectReference) {
            current = inspect(
                    session,
                    ((PdfIndirectReference) current).getReference());
        }
        return current;
    }

    private static int sfntGlyphCount(byte[] font) {
        int maxp = tableOffset(font, "maxp");
        return unsignedShort(font, maxp + 4);
    }

    private static int tableOffset(byte[] font, String wanted) {
        int record = tableRecordOffset(font, wanted);
        return unsignedInt(font, record + 8);
    }

    private static int tableRecordOffset(byte[] font, String wanted) {
        int tableCount = unsignedShort(font, 4);
        for (int index = 0; index < tableCount; index++) {
            int record = 12 + 16 * index;
            String tag = new String(
                    font, record, 4, StandardCharsets.ISO_8859_1);
            if (wanted.equals(tag)) {
                return record;
            }
        }
        throw new AssertionError("missing table " + wanted);
    }

    private static byte[] minimalPdf(String version) {
        String[] objects = {
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 72 72]"
                    + " /Resources << >> >>"
        };
        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        writePdfText(pdf, "%PDF-" + version + "\n%FolioT19Fixture\n");
        int[] offsets = new int[objects.length + 1];
        for (int index = 0; index < objects.length; index++) {
            offsets[index + 1] = pdf.size();
            writePdfText(pdf, (index + 1) + " 0 obj\n"
                    + objects[index] + "\nendobj\n");
        }
        int xref = pdf.size();
        writePdfText(pdf, "xref\n0 " + (objects.length + 1) + "\n");
        writePdfText(pdf, "0000000000 65535 f \n");
        for (int index = 1; index < offsets.length; index++) {
            writePdfText(pdf, String.format(
                    "%010d 00000 n \n", offsets[index]));
        }
        writePdfText(pdf, "trailer\n<< /Size " + (objects.length + 1)
                + " /Root 1 0 R >>\nstartxref\n" + xref
                + "\n%%EOF\n");
        return pdf.toByteArray();
    }

    private static void writePdfText(
            ByteArrayOutputStream output,
            String value) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        output.write(bytes, 0, bytes.length);
    }

    private static void writeInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
    }

    private static void writeShort(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 8);
        bytes[offset + 1] = (byte) value;
    }

    private static void repairChecksums(byte[] font) {
        int head = tableOffset(font, "head");
        writeInt(font, head + 8, 0);
        int tableCount = unsignedShort(font, 4);
        for (int index = 0; index < tableCount; index++) {
            int record = 12 + 16 * index;
            int offset = unsignedInt(font, record + 8);
            int length = unsignedInt(font, record + 12);
            writeInt(font, record + 4, (int) checksum(font, offset, length));
        }
        long adjustment = (0xb1b0afbaL
                - checksum(font, 0, font.length)) & 0xffffffffL;
        writeInt(font, head + 8, (int) adjustment);
    }

    private static long checksum(byte[] bytes, int offset, int length) {
        long sum = 0L;
        for (int index = 0; index < length; index += 4) {
            long word = 0L;
            for (int part = 0; part < 4; part++) {
                word <<= 8;
                if (index + part < length) {
                    word |= bytes[offset + index + part] & 0xffL;
                }
            }
            sum = (sum + word) & 0xffffffffL;
        }
        return sum;
    }

    private static void assertDecimal(String expected, BigDecimal actual) {
        BigDecimal difference = new BigDecimal(expected)
                .subtract(actual)
                .abs();
        assertTrue(
                "expected " + expected + " but observed " + actual,
                difference.compareTo(new BigDecimal("0.00001")) <= 0);
    }

    private static final class FontProfileCase {
        private final String name;
        private final byte[] font;

        FontProfileCase(String name, byte[] font) {
            this.name = name;
            this.font = font;
        }
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {
        private boolean closed;
        private int bytesRead;

        TrackingInputStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public synchronized int read(byte[] bytes, int offset, int length) {
            int count = super.read(bytes, offset, length);
            if (count > 0) {
                bytesRead += count;
            }
            return count;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class TrackingChannel implements ReadableByteChannel {
        private final ByteBuffer bytes;
        private boolean open = true;
        private int bytesRead;

        TrackingChannel(byte[] value) {
            this.bytes = ByteBuffer.wrap(value);
        }

        @Override
        public int read(ByteBuffer target) {
            if (!bytes.hasRemaining()) {
                return -1;
            }
            int count = Math.min(target.remaining(), bytes.remaining());
            byte[] part = new byte[count];
            bytes.get(part);
            target.put(part);
            bytesRead += count;
            return count;
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
        }
    }
}
