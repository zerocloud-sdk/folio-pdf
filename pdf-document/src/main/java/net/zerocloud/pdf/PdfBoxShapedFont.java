package net.zerocloud.pdf;

import java.io.IOException;
import java.io.FilterOutputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.zerocloud.pdf.composition.CanvasMatrix;
import net.zerocloud.pdf.provider.ShapingResult;
import org.apache.fontbox.ttf.TTFSubsetter;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

/** Glyph-ID embedding and positioned painting for an already validated T19 font. */
final class PdfBoxShapedFont {
    private final PDDocument document;
    private final WorkflowResourceContext resources;
    private final TrueTypeFont source;
    private final byte[] sourceBytes;
    private final boolean fullEmbedding;
    private final boolean extended;
    private final List<PdfBoxShapedFont> pendingFonts;
    private final PdfBoxSubsetNames subsetNames;
    private final COSDictionary type0 = new COSDictionary();
    private final COSDictionary descendant = new COSDictionary();
    private final COSObject fontResource = new COSObject(type0);
    private final Map<Integer, Mapping> mappings = new LinkedHashMap<Integer, Mapping>();
    private final Map<String, Integer> codes = new LinkedHashMap<String, Integer>();
    private boolean finalized;

    PdfBoxShapedFont(PDDocument document, TrueTypeFont source, byte[] sourceBytes,
            boolean fullEmbedding, boolean extended, WorkflowResourceContext resources,
            List<PdfBoxShapedFont> pendingFonts, PdfBoxSubsetNames subsetNames) {
        this.document = document;
        this.source = source;
        this.sourceBytes = sourceBytes;
        this.fullEmbedding = fullEmbedding;
        this.extended = extended;
        this.resources = resources;
        this.pendingFonts = pendingFonts;
        this.subsetNames = subsetNames;
    }

    long draw(PDPage page, String runText, int first, int end, List<ShapingResult.Glyph> glyphs,
            double size, double x, double y, long maximumBytes) throws DocumentFailure {
        if (!pendingFonts.contains(this)) { pendingFonts.add(this); }
        PdfBoxPageContentSupport.FontResources prepared = PdfBoxPageContentSupport.prepareFontResources(
                page.getCOSObject(), PdfBoxFontFailures::sourceInvalid, resources);
        COSDictionary fonts = prepared.fonts();
        COSName name = null;
        for (COSName candidate : fonts.keySet()) {
            if (PdfBoxPageContentSupport.dereference(fonts.getItem(candidate), resources) == type0) {
                name = candidate; break;
            }
        }
        boolean changed = name == null;
        if (name == null) {
            for (int suffix = 1; suffix <= fonts.size() + 1; suffix++) {
                COSName candidate = COSName.getPDFName("FolioT29F" + suffix);
                if (!fonts.containsKey(candidate)) { name = candidate; break; }
            }
            fonts.setItem(name, fontResource);
            prepared.resources().setItem(COSName.FONT, fonts);
        }
        try (WorkflowAsciiOutput output = new WorkflowAsciiOutput(resources, maximumBytes,
                PdfBoxFontFailures::operationLimitExceeded)) {
            output.append("q\n/Span << /ActualText <feff");
            hexText(output, runText.substring(first, end));
            output.append("> >> BDC\nBT\n");
            output.appendPdfName(name, PdfBoxFontFailures::sourceInvalid);
            output.append(' ');
            PdfBoxPageContentSupport.appendNumber(output, size, PdfBoxFontFailures::sourceInvalid);
            output.append(" Tf\n0 Tr\n");
            double scale = size / source.getUnitsPerEm();
            double penX = x;
            double penY = y;
            for (ShapingResult.Glyph glyph : glyphs) {
                resources.checkpoint();
                int code = code(glyph, runText.substring(glyph.getClusterStart(), glyph.getClusterEnd()));
                PdfBoxPageContentSupport.appendMatrix(output, CanvasMatrix.of(1, 0, 0, 1,
                        penX + glyph.getXOffset() * scale, penY + glyph.getYOffset() * scale),
                        PdfBoxFontFailures::sourceInvalid);
                output.append(" Tm <"); hex(output, code); output.append("> Tj\n");
                penX += glyph.getXAdvance() * scale;
                penY += glyph.getYAdvance() * scale;
            }
            output.append("ET\nEMC\nQ\n");
            try (WorkflowResourceContext.OwnedBytes operators = output.finishWorking()) {
                PdfBoxPageContentSupport.appendIsolated(document, page, operators.getBytes(),
                        prepared.resources(), changed, resources, PdfBoxFontFailures::sourceInvalid);
                return operators.getBytes().length;
            }
        } catch (IOException failure) { throw PdfBoxFontFailures.sourceInvalid(); }
    }

    private int code(ShapingResult.Glyph glyph, String logical) throws DocumentFailure {
        String key = glyph.getGlyphId() + ":" + glyph.getXAdvance() + ":" + logical;
        Integer existing = codes.get(key);
        if (existing != null) { return existing; }
        int code = glyph.getGlyphId();
        if (mappings.containsKey(code)) {
            for (code = 1; code <= 65535 && mappings.containsKey(code); code++) { resources.checkpoint(); }
        }
        if (code > 65535) { throw PdfBoxFontFailures.operationLimitExceeded(); }
        resources.retainOwnedMemory(128L + 4L * logical.length());
        codes.put(key, code);
        mappings.put(code, new Mapping(glyph.getGlyphId(), glyph.getXAdvance(), logical));
        finalized = false;
        return code;
    }

    void finish() throws DocumentFailure {
        if (finalized || mappings.isEmpty()) { return; }
        try {
            Set<Integer> glyphs = new LinkedHashSet<Integer>();
            for (Mapping mapping : mappings.values()) { glyphs.add(mapping.glyph); }
            Map<Integer, Integer> originalToSubset = new LinkedHashMap<Integer, Integer>();
            COSStream embedded;
            if (fullEmbedding) {
                embedded = stream(sourceBytes);
                for (int glyph : glyphs) { originalToSubset.put(glyph, glyph); }
            } else {
                TTFSubsetter subsetter = new TTFSubsetter(source, Arrays.asList(
                        "head", "hhea", "loca", "maxp", "cvt ", "prep", "glyf", "hmtx", "fpgm", "gasp"));
                subsetter.addGlyphIds(glyphs);
                for (Map.Entry<Integer, Integer> entry : subsetter.getGIDMap().entrySet()) {
                    originalToSubset.put(entry.getValue(), entry.getKey());
                }
                try (WorkflowResourceContext.OwnedByteAccumulator bytes = resources.ownedByteAccumulator()) {
                    // FontBox closes its destination. This scope owns the
                    // accumulator and must still detach its completed bytes.
                    subsetter.writeToStream(new FilterOutputStream(bytes) {
                        @Override public void close() throws IOException { flush(); }
                    });
                    try (WorkflowResourceContext.OwnedBytes raw = bytes.finishWorking();
                            WorkflowResourceContext.MemoryReservation normalized = resources.reserveOwnedMemory(
                                    3L * raw.getBytes().length + 512L * 65535)) {
                        embedded = stream(PdfBoxTrueTypePreflight.normalizeSubsetMetrics(
                                raw.getBytes(), resources, extended));
                    }
                }
            }
            int maximumCode = 0;
            for (int code : mappings.keySet()) { maximumCode = Math.max(maximumCode, code); }
            try (WorkflowResourceContext.MemoryReservation mapMemory = resources.reserveOwnedMemory(2L * (maximumCode + 1))) {
                byte[] map = new byte[2 * (maximumCode + 1)];
                COSArray widths = new COSArray();
                for (Map.Entry<Integer, Mapping> entry : mappings.entrySet()) {
                    resources.checkpoint();
                    int code = entry.getKey();
                    int gid = originalToSubset.get(entry.getValue().glyph);
                    map[2 * code] = (byte) (gid >>> 8);
                    map[2 * code + 1] = (byte) gid;
                    widths.add(COSInteger.get(code));
                    COSArray width = new COSArray();
                    width.add(new org.apache.pdfbox.cos.COSFloat(
                            (float) (entry.getValue().advance * 1000d / source.getUnitsPerEm())));
                    widths.add(width);
                }
                descendant.setItem(COSName.CID_TO_GID_MAP, stream(map));
                descendant.setItem(COSName.W, widths);
            }
            String name = (fullEmbedding ? "" : subsetNames.reserve(sourceBytes, originalToSubset.keySet())) + source.getName();
            COSDictionary descriptor = new COSDictionary();
            descriptor.setItem(COSName.TYPE, COSName.FONT_DESC);
            descriptor.setName(COSName.FONT_NAME, name);
            descriptor.setInt(COSName.FLAGS, 4);
            COSArray box = new COSArray();
            double scale = 1000d / source.getUnitsPerEm();
            for (int coordinate : new int[] {source.getHeader().getXMin(), source.getHeader().getYMin(),
                    source.getHeader().getXMax(), source.getHeader().getYMax()}) {
                box.add(new org.apache.pdfbox.cos.COSFloat((float) (coordinate * scale)));
            }
            descriptor.setItem(COSName.FONT_BBOX, box);
            descriptor.setFloat(COSName.ITALIC_ANGLE, source.getPostScript().getItalicAngle());
            descriptor.setFloat(COSName.ASCENT, (float) (source.getHeader().getYMax() * scale));
            descriptor.setFloat(COSName.DESCENT, (float) (source.getHeader().getYMin() * scale));
            descriptor.setFloat(COSName.CAP_HEIGHT, (float) (source.getOS2Windows().getCapHeight() * scale));
            descriptor.setInt(COSName.STEM_V, 80);
            descriptor.setItem(COSName.FONT_FILE2, embedded);
            descendant.setItem(COSName.TYPE, COSName.FONT);
            descendant.setItem(COSName.SUBTYPE, COSName.CID_FONT_TYPE2);
            descendant.setName(COSName.BASE_FONT, name);
            COSDictionary system = new COSDictionary();
            system.setItem(COSName.REGISTRY, new COSString("Adobe"));
            system.setItem(COSName.ORDERING, new COSString("Identity"));
            system.setInt(COSName.SUPPLEMENT, 0);
            descendant.setItem(COSName.CIDSYSTEMINFO, system);
            descendant.setItem(COSName.FONT_DESC, new COSObject(descriptor));
            descendant.setInt(COSName.DW, 0);
            type0.setItem(COSName.TYPE, COSName.FONT);
            type0.setItem(COSName.SUBTYPE, COSName.TYPE0);
            type0.setName(COSName.BASE_FONT, name);
            type0.setName(COSName.ENCODING, "Identity-H");
            COSArray descendants = new COSArray(); descendants.add(new COSObject(descendant));
            type0.setItem(COSName.DESCENDANT_FONTS, descendants);
            type0.setItem(COSName.TO_UNICODE, unicodeMap());
            finalized = true;
        } catch (IOException | RuntimeException failure) {
            resources.rethrowResourceOrTerminalFailure(failure);
            throw PdfBoxFontFailures.sourceInvalid();
        }
    }

    private COSStream unicodeMap() throws DocumentFailure, IOException {
        try (WorkflowAsciiOutput output = new WorkflowAsciiOutput(resources, Integer.MAX_VALUE,
                PdfBoxFontFailures::operationLimitExceeded)) {
            output.append("/CIDInit /ProcSet findresource begin\n12 dict begin\nbegincmap\n"
                    + "/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def\n"
                    + "/CMapName /FolioShapedUnicode def\n/CMapType 2 def\n"
                    + "1 begincodespacerange\n<0000> <ffff>\nendcodespacerange\n");
            for (Map.Entry<Integer, Mapping> entry : mappings.entrySet()) {
                output.append("1 beginbfchar\n<"); hex(output, entry.getKey());
                output.append("> <"); hexText(output, entry.getValue().logical);
                output.append(">\nendbfchar\n");
            }
            output.append("endcmap\nCMapName currentdict /CMap defineresource pop\nend\nend\n");
            try (WorkflowResourceContext.OwnedBytes bytes = output.finishWorking()) { return stream(bytes.getBytes()); }
        }
    }

    private COSStream stream(byte[] bytes) throws IOException, DocumentFailure {
        COSStream stream = document.getDocument().createCOSStream();
        try (OutputStream output = stream.createOutputStream(COSName.FLATE_DECODE)) {
            resources.writeBytesAsIOException(output, bytes);
        }
        stream.setInt(COSName.LENGTH1, bytes.length);
        return stream;
    }

    private static void hexText(WorkflowAsciiOutput output, String text) throws DocumentFailure {
        for (int index = 0; index < text.length(); index++) { hex(output, text.charAt(index)); }
    }

    private static void hex(WorkflowAsciiOutput output, int value) throws DocumentFailure {
        for (int shift = 12; shift >= 0; shift -= 4) { output.append(Character.forDigit(value >>> shift & 15, 16)); }
    }

    private static final class Mapping {
        private final int glyph;
        private final int advance;
        private final String logical;
        Mapping(int glyph, int advance, String logical) {
            this.glyph = glyph; this.advance = advance; this.logical = logical;
        }
    }
}
