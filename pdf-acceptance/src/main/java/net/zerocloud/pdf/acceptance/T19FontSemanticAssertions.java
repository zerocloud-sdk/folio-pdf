package net.zerocloud.pdf.acceptance;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.zerocloud.pdf.CharacterMapping;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentResourceInventory;
import net.zerocloud.pdf.DocumentSession;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.ExtractionLimits;
import net.zerocloud.pdf.FontResource;
import net.zerocloud.pdf.ImageByteAccess;
import net.zerocloud.pdf.ObjectReference;
import net.zerocloud.pdf.PdfArray;
import net.zerocloud.pdf.PdfDictionary;
import net.zerocloud.pdf.PdfIndirectReference;
import net.zerocloud.pdf.PdfInspectionLimits;
import net.zerocloud.pdf.PdfName;
import net.zerocloud.pdf.PdfStream;
import net.zerocloud.pdf.PdfValue;
import net.zerocloud.pdf.PublicationReceipt;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.ResourceExtractionLimits;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.TextItem;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.query.ExtractImagesAndResources;
import net.zerocloud.pdf.query.ExtractTextAndStructure;
import net.zerocloud.pdf.query.InspectObject;

/** Project-owned public semantic assertions for the T19 font artifact. */
final class T19FontSemanticAssertions {

    private static final String CAPABILITY =
            "composition.fonts.load-embed-subset-fallback";

    private T19FontSemanticAssertions() {
    }

    static T19FontSemanticObservation inspect(
            WorkflowOutcome<Void> creation,
            Path artifact) {
        PublicationStatus publicationStatus = publicationStatus(creation);
        boolean capabilityReported = CAPABILITY.equals(
                creation.getCapabilityId());
        try {
            T19FontSemanticChecks checks = new DocumentWorkflow().execute(
                    WorkflowRequest.open(artifact, SaveMode.REWRITE),
                    T19FontSemanticAssertions::observe).getResult();
            return T19FontSemanticObservation.observed(
                    publicationStatus,
                    capabilityReported,
                    checks);
        } catch (DocumentFailure failure) {
            return T19FontSemanticObservation.reopenFailed(
                    publicationStatus,
                    capabilityReported,
                    failure.getCode());
        } catch (RuntimeException malformedObservation) {
            return T19FontSemanticObservation.reopenFailed(
                    publicationStatus,
                    capabilityReported,
                    null);
        }
    }

    private static T19FontSemanticChecks observe(DocumentSession session)
            throws DocumentFailure {
        DocumentResourceInventory inventory = session.query(
                ExtractImagesAndResources.version1(
                        resourceLimits(),
                        ImageByteAccess.NONE));
        Map<String, FontResource> fonts = new HashMap<String, FontResource>();
        boolean fontResources = inventory.getFonts().size() == 2;
        for (FontResource font : inventory.getFonts()) {
            fontResources = fontResources
                    && font.getFontKind() == FontResource.FontKind.TYPE_0
                    && font.getEmbedding() == FontResource.Embedding.EMBEDDED
                    && font.isSubset()
                    && font.getObjectReference().isPresent();
            if (font.getBaseFontName().isPresent()) {
                String name = font.getBaseFontName().get().getValue();
                if (name.endsWith("+FolioPrimary-Regular")) {
                    fonts.put("primary", font);
                } else if (name.endsWith("+FolioFallback-Regular")) {
                    fonts.put("fallback", font);
                }
            }
        }
        fontResources = fontResources
                && fonts.containsKey("primary")
                && fonts.containsKey("fallback");

        List<TextItem> items = session.query(
                ExtractTextAndStructure.version1(textLimits()))
                .getPages().get(0).getTextItems();
        StringBuilder contribution = new StringBuilder();
        boolean explicit = true;
        for (TextItem item : items) {
            contribution.append(item.getTextContribution());
            explicit = explicit
                    && item.getCharacterMapping().getConfidence()
                            == CharacterMapping.Confidence.EXPLICIT;
        }
        boolean unicodeMappings = explicit
                && "A\u03a9BAB".equals(contribution.toString());
        boolean sourceMetrics = items.size() == 5
                && withinGeometryTolerance(
                        "57.6", items.get(0).getGeometry().getAdvanceX())
                && withinGeometryTolerance(
                        "67.2", items.get(1).getGeometry().getAdvanceX())
                && withinGeometryTolerance(
                        "62.4", items.get(2).getGeometry().getAdvanceX())
                && withinGeometryTolerance(
                        "28.8", items.get(3).getGeometry().getAdvanceX())
                && withinGeometryTolerance(
                        "31.2", items.get(4).getGeometry().getAdvanceX());

        boolean subsetPrograms = false;
        boolean reuse = false;
        if (fontResources) {
            byte[] primary = embeddedFontBytes(session, fonts.get("primary"));
            byte[] fallback = embeddedFontBytes(session, fonts.get("fallback"));
            String primaryCmap = ascii(toUnicodeBytes(
                    session, fonts.get("primary"))).toUpperCase(Locale.ROOT);
            String fallbackCmap = ascii(toUnicodeBytes(
                    session, fonts.get("fallback"))).toUpperCase(Locale.ROOT);
            unicodeMappings = unicodeMappings
                    && primaryCmap.contains("<0041>")
                    && fallbackCmap.contains("<03A9>");
            subsetPrograms = sfntGlyphCount(primary) == 3
                    && sfntGlyphCount(fallback) == 2
                    && primary.length < 972
                    && fallback.length < 1028;
            reuse = fonts.get("primary").getDeclarations().size() == 1
                    && fonts.get("fallback").getDeclarations().size() == 1;
        }
        return new T19FontSemanticChecks(
                fontResources,
                unicodeMappings,
                sourceMetrics,
                subsetPrograms,
                reuse);
    }

    private static byte[] embeddedFontBytes(
            DocumentSession session,
            FontResource font) throws DocumentFailure {
        PdfDictionary type0 = inspectDictionary(
                session, font.getObjectReference().get());
        PdfArray descendants = (PdfArray) resolve(
                session, type0.get(PdfName.of("DescendantFonts")));
        PdfDictionary descendant = (PdfDictionary) resolve(
                session, descendants.get(0));
        PdfDictionary descriptor = (PdfDictionary) resolve(
                session, descendant.get(PdfName.of("FontDescriptor")));
        return ((PdfStream) resolve(
                session, descriptor.get(PdfName.of("FontFile2"))))
                .readBytes();
    }

    private static byte[] toUnicodeBytes(
            DocumentSession session,
            FontResource font) throws DocumentFailure {
        PdfDictionary type0 = inspectDictionary(
                session, font.getObjectReference().get());
        return ((PdfStream) resolve(
                session, type0.get(PdfName.of("ToUnicode"))))
                .readBytes();
    }

    private static PdfDictionary inspectDictionary(
            DocumentSession session,
            ObjectReference reference) throws DocumentFailure {
        return (PdfDictionary) session.query(InspectObject.version1(
                reference,
                PdfInspectionLimits.of(256L, 4L * 1024L * 1024L)));
    }

    private static PdfValue resolve(
            DocumentSession session,
            PdfValue value) throws DocumentFailure {
        PdfValue current = value;
        while (current instanceof PdfIndirectReference) {
            current = session.query(InspectObject.version1(
                    ((PdfIndirectReference) current).getReference(),
                    PdfInspectionLimits.of(256L, 4L * 1024L * 1024L)));
        }
        return current;
    }

    private static int sfntGlyphCount(byte[] font) {
        int maxp = tableOffset(font, "maxp");
        return unsignedShort(font, maxp + 4);
    }

    private static int tableOffset(byte[] font, String wanted) {
        int count = unsignedShort(font, 4);
        for (int index = 0; index < count; index++) {
            int record = 12 + index * 16;
            if (wanted.equals(new String(
                    font, record, 4, StandardCharsets.ISO_8859_1))) {
                return unsignedInt(font, record + 8);
            }
        }
        throw new IllegalArgumentException("Missing sfnt table " + wanted);
    }

    private static int unsignedShort(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) << 8 | bytes[offset + 1] & 0xff;
    }

    private static int unsignedInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) << 24
                | (bytes[offset + 1] & 0xff) << 16
                | (bytes[offset + 2] & 0xff) << 8
                | bytes[offset + 3] & 0xff;
    }

    private static String ascii(byte[] bytes) {
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    private static boolean withinGeometryTolerance(
            String expected,
            BigDecimal actual) {
        return new BigDecimal(expected).subtract(actual).abs()
                .compareTo(new BigDecimal("0.00001")) <= 0;
    }

    private static PublicationStatus publicationStatus(
            WorkflowOutcome<Void> creation) {
        List<PublicationReceipt> receipts = creation.getPublicationReceipts();
        return receipts.size() == 1
                ? receipts.get(0).getStatus() : null;
    }

    private static ResourceExtractionLimits resourceLimits() {
        return ResourceExtractionLimits.builder()
                .maximumPages(2)
                .maximumPageTreeNodes(16)
                .maximumTraversedResourceValues(512L)
                .maximumResourceTraversalDepth(12)
                .maximumDecodedPixels(0L)
                .maximumDecompressedBytes(4L * 1024L * 1024L)
                .maximumReturnedBytes(0L)
                .build();
    }

    private static ExtractionLimits textLimits() {
        return ExtractionLimits.builder()
                .maximumPages(2)
                .maximumPageTreeNodes(16)
                .maximumContentStreams(16)
                .maximumContentStreamDepth(4)
                .maximumDecodedBytes(2L * 1024L * 1024L)
                .maximumTextItems(16)
                .maximumUnicodeCodePoints(32)
                .maximumToUnicodeMappings(32)
                .maximumFontDataEntries(32)
                .maximumMarkedContentSequences(4)
                .maximumMarkedContentDepth(2)
                .maximumStructureElements(4)
                .maximumStructureItems(4)
                .maximumStructureDepth(2)
                .maximumRoleMappings(2)
                .build();
    }

}
