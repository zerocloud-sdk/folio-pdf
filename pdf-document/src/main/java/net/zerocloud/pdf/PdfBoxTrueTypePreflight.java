package net.zerocloud.pdf;

import static net.zerocloud.pdf.PdfBoxFontFailures.embeddingRestricted;
import static net.zerocloud.pdf.PdfBoxFontFailures.formatUnsupported;
import static net.zerocloud.pdf.PdfBoxFontFailures.sourceInvalid;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Closed structural and permission preflight for T19 TrueType programs. */
final class PdfBoxTrueTypePreflight {

    private static final String TRUE_TYPE_SCALER = "\u0000\u0001\u0000\u0000";
    private static final String APPLE_TRUE_TYPE_SCALER = "true";
    private static final List<String> UNSUPPORTED_SIGNATURES = Arrays.asList(
            "OTTO", "ttcf", "wOFF", "wOF2");
    private static final List<String> REQUIRED_TABLES = Arrays.asList(
            "head", "hhea", "maxp", "hmtx", "cmap", "loca", "glyf",
            "name", "OS/2", "post");
    private static final List<String> SUBSET_TABLES = Arrays.asList(
            "head", "hhea", "maxp", "hmtx", "loca", "glyf");
    private static final byte GLYPH_UNSEEN = 0;
    private static final byte GLYPH_VISITING = 1;
    private static final byte GLYPH_RESOLVED = 2;

    private PdfBoxTrueTypePreflight() {
    }

    static byte[] normalizeSubsetMetrics(byte[] bytes)
            throws DocumentFailure {
        byte[] normalized = Arrays.copyOf(bytes, bytes.length);
        ValidatedOutline outline = validateOutline(
                normalized, SUBSET_TABLES);
        HorizontalSummary horizontal = horizontalSummary(
                outline.head, outline.horizontalMetrics, outline.glyphs);
        GlyphBounds bounds = glyphBounds(outline.glyphs);
        TableRange hhea = outline.tables.get("hhea");
        writeShort(normalized, hhea.offset + 10, horizontal.maximumAdvance);
        writeShort(
                normalized,
                hhea.offset + 12,
                horizontal.minimumLeftSideBearing);
        writeShort(
                normalized,
                hhea.offset + 14,
                horizontal.minimumRightSideBearing);
        writeShort(normalized, hhea.offset + 16, horizontal.maximumExtent);
        TableRange headTable = outline.tables.get("head");
        writeShort(normalized, headTable.offset + 36, bounds.minimumX);
        writeShort(normalized, headTable.offset + 38, bounds.minimumY);
        writeShort(normalized, headTable.offset + 40, bounds.maximumX);
        writeShort(normalized, headTable.offset + 42, bounds.maximumY);
        repairChecksums(normalized, outline.tables);
        HeadInfo repairedHead = validateHead(normalized, headTable);
        HorizontalHeader repairedHhea = validateHorizontalHeader(
                normalized, hhea, outline.maximum.glyphCount);
        validateHorizontalMetrics(
                repairedHead,
                repairedHhea,
                outline.horizontalMetrics,
                outline.glyphs);
        validateGlobalBounds(repairedHead, outline.glyphs);
        return normalized;
    }

    static EmbeddingProfile validateForEmbedding(byte[] bytes)
            throws DocumentFailure {
        ValidatedOutline outline = validateOutline(bytes, REQUIRED_TABLES);
        validateHorizontalMetrics(
                outline.head,
                outline.horizontalHeader,
                outline.horizontalMetrics,
                outline.glyphs);
        validateNames(bytes, outline.tables.get("name"));
        validatePost(
                bytes,
                outline.tables.get("post"),
                outline.maximum.glyphCount);
        validateGlobalBounds(outline.head, outline.glyphs);
        CmapInfo cmap = validateCmap(
                bytes,
                outline.tables.get("cmap"),
                outline.maximum.glyphCount);
        boolean noSubsetting = validateOs2(
                bytes,
                outline.tables.get("OS/2"),
                outline.head.macStyle,
                cmap);
        return new EmbeddingProfile(
                noSubsetting,
                cmap.hasSupplementaryMapping());
    }

    private static ValidatedOutline validateOutline(
            byte[] bytes,
            List<String> profileTables) throws DocumentFailure {
        Map<String, TableRange> tables = inspectDirectory(
                bytes, profileTables);
        validateChecksums(bytes, tables.values());
        HeadInfo head = validateHead(bytes, tables.get("head"));
        MaximumProfile maximum = validateMaximumProfile(
                bytes, tables.get("maxp"));
        HorizontalHeader horizontalHeader = validateHorizontalHeader(
                bytes, tables.get("hhea"), maximum.glyphCount);
        HorizontalMetrics horizontalMetrics = validateHorizontalMetrics(
                bytes,
                tables.get("hmtx"),
                maximum.glyphCount,
                horizontalHeader.metricCount);
        int[] glyphOffsets = validateGlyphOffsets(
                bytes,
                tables.get("loca"),
                tables.get("glyf"),
                maximum.glyphCount,
                head.indexToLocFormat);
        GlyphInfo[] glyphs = validateGlyphs(
                bytes, tables.get("glyf"), glyphOffsets, maximum);
        return new ValidatedOutline(
                tables,
                head,
                maximum,
                horizontalHeader,
                horizontalMetrics,
                glyphs);
    }

    private static Map<String, TableRange> inspectDirectory(
            byte[] bytes,
            List<String> profileTables)
            throws DocumentFailure {
        if (bytes.length < 12) {
            throw sourceInvalid();
        }
        String signature = ascii(bytes, 0, 4);
        if (UNSUPPORTED_SIGNATURES.contains(signature)
                || looksLikeUnsupportedRawFont(bytes)) {
            throw formatUnsupported();
        }
        if (!TRUE_TYPE_SCALER.equals(signature)
                && !APPLE_TRUE_TYPE_SCALER.equals(signature)) {
            throw sourceInvalid();
        }
        int tableCount = unsignedShort(bytes, 4);
        long directoryEnd = 12L + 16L * tableCount;
        if (tableCount == 0 || directoryEnd > bytes.length) {
            throw sourceInvalid();
        }
        validateOffsetTable(bytes, tableCount);

        Map<String, TableRange> tables = new HashMap<String, TableRange>();
        List<TableRange> ranges = new ArrayList<TableRange>(tableCount);
        String previousTag = null;
        for (int index = 0; index < tableCount; index++) {
            int record = 12 + 16 * index;
            String tag = ascii(bytes, record, 4);
            long checksum = unsignedInt(bytes, record + 4);
            long offset = unsignedInt(bytes, record + 8);
            long length = unsignedInt(bytes, record + 12);
            if (!validTag(bytes, record)
                    || (previousTag != null
                            && previousTag.compareTo(tag) >= 0)
                    || tables.containsKey(tag)
                    || offset < directoryEnd
                    || (offset & 3L) != 0L
                    || offset > bytes.length
                    || length > bytes.length - offset) {
                throw sourceInvalid();
            }
            long paddedLength = (length + 3L) & ~3L;
            if (paddedLength > bytes.length - offset) {
                throw sourceInvalid();
            }
            TableRange range = new TableRange(
                    (int) offset,
                    (int) length,
                    (int) (offset + paddedLength),
                    checksum,
                    "head".equals(tag));
            tables.put(tag, range);
            ranges.add(range);
            previousTag = tag;
        }
        Collections.sort(ranges, new Comparator<TableRange>() {
            @Override
            public int compare(TableRange left, TableRange right) {
                return Integer.compare(left.offset, right.offset);
            }
        });
        int end = (int) directoryEnd;
        for (TableRange range : ranges) {
            if (range.offset < end) {
                throw sourceInvalid();
            }
            for (int offset = end; offset < range.offset; offset++) {
                if (bytes[offset] != 0) {
                    throw sourceInvalid();
                }
            }
            for (int offset = range.offset + range.length;
                    offset < range.paddedEnd;
                    offset++) {
                if (bytes[offset] != 0) {
                    throw sourceInvalid();
                }
            }
            end = range.paddedEnd;
        }
        if (end != bytes.length) {
            throw sourceInvalid();
        }
        for (String tag : profileTables) {
            TableRange required = tables.get(tag);
            if (required == null || required.length == 0) {
                throw formatUnsupported();
            }
        }
        for (String tag : tables.keySet()) {
            if (!profileTables.contains(tag)) {
                throw formatUnsupported();
            }
        }
        return tables;
    }

    private static void validateOffsetTable(byte[] bytes, int tableCount)
            throws DocumentFailure {
        SearchParameters parameters = searchParameters(tableCount);
        int searchRange = parameters.maximumPowerOfTwo * 16;
        int rangeShift = tableCount * 16 - searchRange;
        if (unsignedShort(bytes, 6) != searchRange
                || unsignedShort(bytes, 8) != parameters.entrySelector
                || unsignedShort(bytes, 10) != rangeShift) {
            throw sourceInvalid();
        }
    }

    private static SearchParameters searchParameters(int entryCount) {
        int maximumPowerOfTwo = Integer.highestOneBit(entryCount);
        return new SearchParameters(
                maximumPowerOfTwo,
                Integer.numberOfTrailingZeros(maximumPowerOfTwo));
    }

    private static void validateChecksums(
            byte[] bytes,
            Iterable<TableRange> tables) throws DocumentFailure {
        for (TableRange table : tables) {
            int zeroOffset = table.head ? table.offset + 8 : -1;
            if (checksum(bytes, table.offset, table.length, zeroOffset)
                    != table.checksum) {
                throw sourceInvalid();
            }
        }
        if (checksum(bytes, 0, bytes.length, -1) != 0xb1b0afbaL) {
            throw sourceInvalid();
        }
    }

    private static HeadInfo validateHead(byte[] bytes, TableRange head)
            throws DocumentFailure {
        if (head.length != 54
                || unsignedInt(bytes, head.offset) != 0x00010000L
                || unsignedInt(bytes, head.offset + 12) != 0x5f0f3cf5L) {
            throw sourceInvalid();
        }
        int flags = unsignedShort(bytes, head.offset + 16);
        int unitsPerEm = unsignedShort(bytes, head.offset + 18);
        int xMin = signedShort(bytes, head.offset + 36);
        int yMin = signedShort(bytes, head.offset + 38);
        int xMax = signedShort(bytes, head.offset + 40);
        int yMax = signedShort(bytes, head.offset + 42);
        int lowestPixelsPerEm = unsignedShort(bytes, head.offset + 46);
        int directionHint = signedShort(bytes, head.offset + 48);
        int indexToLocFormat = signedShort(bytes, head.offset + 50);
        int glyphDataFormat = signedShort(bytes, head.offset + 52);
        if ((flags & 0x8000) != 0) {
            throw sourceInvalid();
        }
        if ((flags & 0x4000) != 0) {
            throw formatUnsupported();
        }
        if ((flags & 0x07fc) != 0) {
            throw formatUnsupported();
        }
        if (unitsPerEm < 16
                || unitsPerEm > 16384
                || xMin > xMax
                || yMin > yMax
                || (unsignedShort(bytes, head.offset + 44) & 0xff80) != 0
                || lowestPixelsPerEm == 0
                || directionHint < -2
                || directionHint > 2
                || (indexToLocFormat != 0 && indexToLocFormat != 1)
                || glyphDataFormat != 0) {
            throw sourceInvalid();
        }
        return new HeadInfo(
                indexToLocFormat,
                new GlyphBounds(xMin, yMin, xMax, yMax),
                unsignedShort(bytes, head.offset + 44),
                flags);
    }

    private static MaximumProfile validateMaximumProfile(
            byte[] bytes,
            TableRange maxp) throws DocumentFailure {
        if (maxp.length != 32
                || unsignedInt(bytes, maxp.offset) != 0x00010000L) {
            throw sourceInvalid();
        }
        int glyphCount = unsignedShort(bytes, maxp.offset + 4);
        int maximumZones = unsignedShort(bytes, maxp.offset + 14);
        if (glyphCount == 0 || maximumZones != 1) {
            throw sourceInvalid();
        }
        if (unsignedShort(bytes, maxp.offset + 16) != 0
                || unsignedShort(bytes, maxp.offset + 18) != 0
                || unsignedShort(bytes, maxp.offset + 20) != 0
                || unsignedShort(bytes, maxp.offset + 22) != 0
                || unsignedShort(bytes, maxp.offset + 24) != 0
                || unsignedShort(bytes, maxp.offset + 26) != 0) {
            throw formatUnsupported();
        }
        return new MaximumProfile(
                glyphCount,
                unsignedShort(bytes, maxp.offset + 6),
                unsignedShort(bytes, maxp.offset + 8),
                unsignedShort(bytes, maxp.offset + 10),
                unsignedShort(bytes, maxp.offset + 12),
                unsignedShort(bytes, maxp.offset + 28),
                unsignedShort(bytes, maxp.offset + 30));
    }

    private static HorizontalHeader validateHorizontalHeader(
            byte[] bytes,
            TableRange hhea,
            int glyphCount) throws DocumentFailure {
        if (hhea.length != 36
                || unsignedInt(bytes, hhea.offset) != 0x00010000L
                || signedShort(bytes, hhea.offset + 24) != 0
                || signedShort(bytes, hhea.offset + 26) != 0
                || signedShort(bytes, hhea.offset + 28) != 0
                || signedShort(bytes, hhea.offset + 30) != 0
                || signedShort(bytes, hhea.offset + 32) != 0) {
            throw sourceInvalid();
        }
        int metricCount = unsignedShort(bytes, hhea.offset + 34);
        if (metricCount == 0 || metricCount > glyphCount) {
            throw sourceInvalid();
        }
        return new HorizontalHeader(
                unsignedShort(bytes, hhea.offset + 10),
                signedShort(bytes, hhea.offset + 12),
                signedShort(bytes, hhea.offset + 14),
                signedShort(bytes, hhea.offset + 16),
                metricCount);
    }

    private static HorizontalMetrics validateHorizontalMetrics(
            byte[] bytes,
            TableRange hmtx,
            int glyphCount,
            int metricCount) throws DocumentFailure {
        long requiredLength = 4L * metricCount
                + 2L * (glyphCount - metricCount);
        if (hmtx.length != requiredLength) {
            throw sourceInvalid();
        }
        int[] advances = new int[glyphCount];
        int[] sideBearings = new int[glyphCount];
        int cursor = hmtx.offset;
        int lastAdvance = 0;
        for (int glyph = 0; glyph < metricCount; glyph++) {
            lastAdvance = unsignedShort(bytes, cursor);
            advances[glyph] = lastAdvance;
            sideBearings[glyph] = signedShort(bytes, cursor + 2);
            cursor += 4;
        }
        for (int glyph = metricCount; glyph < glyphCount; glyph++) {
            advances[glyph] = lastAdvance;
            sideBearings[glyph] = signedShort(bytes, cursor);
            cursor += 2;
        }
        return new HorizontalMetrics(advances, sideBearings);
    }

    private static int[] validateGlyphOffsets(
            byte[] bytes,
            TableRange loca,
            TableRange glyf,
            int glyphCount,
            int indexToLocFormat) throws DocumentFailure {
        int entryBytes = indexToLocFormat == 0 ? 2 : 4;
        long requiredLength = (long) (glyphCount + 1) * entryBytes;
        if (loca.length != requiredLength) {
            throw sourceInvalid();
        }
        int[] result = new int[glyphCount + 1];
        long previous = -1L;
        for (int index = 0; index <= glyphCount; index++) {
            int offset = loca.offset + index * entryBytes;
            long current = indexToLocFormat == 0
                    ? 2L * unsignedShort(bytes, offset)
                    : unsignedInt(bytes, offset);
            if ((current & 1L) != 0L
                    || current < previous
                    || current > glyf.length) {
                throw sourceInvalid();
            }
            result[index] = (int) current;
            previous = current;
        }
        if (result[0] != 0 || result[glyphCount] != glyf.length) {
            throw sourceInvalid();
        }
        return result;
    }

    private static GlyphInfo[] validateGlyphs(
            byte[] bytes,
            TableRange glyf,
            int[] offsets,
            MaximumProfile maximum) throws DocumentFailure {
        GlyphInfo[] glyphs = new GlyphInfo[maximum.glyphCount];
        for (int glyph = 0; glyph < glyphs.length; glyph++) {
            int start = glyf.offset + offsets[glyph];
            int end = glyf.offset + offsets[glyph + 1];
            glyphs[glyph] = parseGlyph(bytes, start, end, glyphs.length);
            if (!glyphs[glyph].composite
                    && (glyphs[glyph].points > maximum.maximumPoints
                            || glyphs[glyph].contours
                                    > maximum.maximumContours)) {
                throw sourceInvalid();
            }
        }
        byte[] states = new byte[glyphs.length];
        for (int glyph = 0; glyph < glyphs.length; glyph++) {
            ResolvedGlyph resolved = resolveGlyph(glyph, glyphs, states);
            GlyphInfo info = glyphs[glyph];
            if (info.composite
                    && (resolved.points > maximum.maximumCompositePoints
                            || resolved.contours
                                    > maximum.maximumCompositeContours
                            || info.components.length
                                    > maximum.maximumComponentElements
                            || resolved.depth
                                    > maximum.maximumComponentDepth)) {
                throw sourceInvalid();
            }
        }
        return glyphs;
    }

    private static GlyphInfo parseGlyph(
            byte[] bytes,
            int start,
            int end,
            int glyphCount) throws DocumentFailure {
        if (start == end) {
            return GlyphInfo.empty();
        }
        if (end - start < 10) {
            throw sourceInvalid();
        }
        int contourCount = signedShort(bytes, start);
        int xMin = signedShort(bytes, start + 2);
        int yMin = signedShort(bytes, start + 4);
        int xMax = signedShort(bytes, start + 6);
        int yMax = signedShort(bytes, start + 8);
        if (xMin > xMax || yMin > yMax) {
            throw sourceInvalid();
        }
        GlyphBounds bounds = new GlyphBounds(xMin, yMin, xMax, yMax);
        if (contourCount >= 0) {
            return parseSimpleGlyph(
                    bytes,
                    start,
                    end,
                    contourCount,
                    bounds);
        }
        if (contourCount != -1) {
            throw formatUnsupported();
        }
        return parseCompositeGlyph(
                bytes,
                start,
                end,
                glyphCount,
                bounds);
    }

    private static GlyphInfo parseSimpleGlyph(
            byte[] bytes,
            int start,
            int end,
            int contourCount,
            GlyphBounds bounds) throws DocumentFailure {
        int cursor = start + 10;
        if (contourCount == 0 && cursor == end) {
            if (!bounds.isZero()) {
                throw sourceInvalid();
            }
            return GlyphInfo.simple(0, 0, GlyphBounds.ZERO);
        }
        if ((long) cursor + 2L * contourCount + 2L > end) {
            throw sourceInvalid();
        }
        int lastEndPoint = -1;
        for (int contour = 0; contour < contourCount; contour++) {
            int endPoint = unsignedShort(bytes, cursor);
            cursor += 2;
            if (endPoint <= lastEndPoint) {
                throw sourceInvalid();
            }
            lastEndPoint = endPoint;
        }
        int pointCount = contourCount == 0 ? 0 : lastEndPoint + 1;
        int instructionLength = unsignedShort(bytes, cursor);
        cursor += 2;
        if (instructionLength > end - cursor) {
            throw sourceInvalid();
        }
        if (instructionLength != 0) {
            throw formatUnsupported();
        }
        cursor += instructionLength;
        byte[] flags = new byte[pointCount];
        int point = 0;
        while (point < pointCount) {
            if (cursor >= end) {
                throw sourceInvalid();
            }
            int flag = bytes[cursor++] & 0xff;
            if ((flag & 0x80) != 0
                    || (point > 0 && (flag & 0x40) != 0)) {
                throw sourceInvalid();
            }
            flags[point++] = (byte) flag;
            if ((flag & 0x08) != 0) {
                if (cursor >= end) {
                    throw sourceInvalid();
                }
                int repeats = bytes[cursor++] & 0xff;
                if (repeats > pointCount - point) {
                    throw sourceInvalid();
                }
                for (int repeat = 0; repeat < repeats; repeat++) {
                    flags[point++] = (byte) flag;
                }
            }
        }

        int[] xs = new int[pointCount];
        int[] ys = new int[pointCount];
        cursor = readCoordinates(bytes, cursor, end, flags, true, xs);
        cursor = readCoordinates(bytes, cursor, end, flags, false, ys);
        requireZeroPadding(bytes, cursor, end);
        if (pointCount == 0) {
            if (!bounds.isZero()) {
                throw sourceInvalid();
            }
        } else if (!bounds.matches(
                minimum(xs), minimum(ys), maximum(xs), maximum(ys))) {
            throw sourceInvalid();
        }
        return GlyphInfo.simple(
                pointCount,
                contourCount,
                bounds);
    }

    private static int readCoordinates(
            byte[] bytes,
            int cursor,
            int end,
            byte[] flags,
            boolean horizontal,
            int[] coordinates) throws DocumentFailure {
        long coordinate = 0L;
        int shortMask = horizontal ? 0x02 : 0x04;
        int sameMask = horizontal ? 0x10 : 0x20;
        for (int point = 0; point < flags.length; point++) {
            int flag = flags[point] & 0xff;
            int delta = 0;
            if ((flag & shortMask) != 0) {
                if (cursor >= end) {
                    throw sourceInvalid();
                }
                delta = bytes[cursor++] & 0xff;
                if ((flag & sameMask) == 0) {
                    delta = -delta;
                }
            } else if ((flag & sameMask) == 0) {
                if (cursor > end - 2) {
                    throw sourceInvalid();
                }
                delta = signedShort(bytes, cursor);
                cursor += 2;
            }
            coordinate += delta;
            if (coordinate < Short.MIN_VALUE
                    || coordinate > Short.MAX_VALUE) {
                throw sourceInvalid();
            }
            coordinates[point] = (int) coordinate;
        }
        return cursor;
    }

    private static GlyphInfo parseCompositeGlyph(
            byte[] bytes,
            int start,
            int end,
            int glyphCount,
            GlyphBounds bounds) throws DocumentFailure {
        int cursor = start + 10;
        List<CompositeComponent> components =
                new ArrayList<CompositeComponent>();
        int flags;
        do {
            if (cursor > end - 4) {
                throw sourceInvalid();
            }
            flags = unsignedShort(bytes, cursor);
            int component = unsignedShort(bytes, cursor + 2);
            cursor += 4;
            if ((flags & ~0x1fef) != 0
                    || Integer.bitCount(flags & 0x00c8) > 1
                    || (flags & 0x1800) == 0x1800
                    || (!components.isEmpty() && (flags & 0x0400) != 0)
                    || component >= glyphCount
                    || ((flags & 0x0100) != 0
                            && (flags & 0x0020) != 0)) {
                throw sourceInvalid();
            }
            if ((flags & 0x00c8) != 0
                    || (flags & 0x1800) != 0
                    || (flags & 0x0200) != 0) {
                throw formatUnsupported();
            }
            if ((flags & 0x0002) == 0) {
                throw formatUnsupported();
            }
            boolean words = (flags & 0x0001) != 0;
            int argumentBytes = words ? 4 : 2;
            if (cursor > end - argumentBytes) {
                throw sourceInvalid();
            }
            int xOffset = words
                    ? signedShort(bytes, cursor)
                    : bytes[cursor];
            int yOffset = words
                    ? signedShort(bytes, cursor + 2)
                    : bytes[cursor + 1];
            cursor += argumentBytes;
            components.add(new CompositeComponent(
                    component, xOffset, yOffset));
        } while ((flags & 0x0020) != 0);

        int instructionLength = 0;
        if ((flags & 0x0100) != 0) {
            if (cursor > end - 2) {
                throw sourceInvalid();
            }
            instructionLength = unsignedShort(bytes, cursor);
            cursor += 2;
            if (instructionLength > end - cursor) {
                throw sourceInvalid();
            }
            if (instructionLength != 0) {
                throw formatUnsupported();
            }
            cursor += instructionLength;
        }
        requireZeroPadding(bytes, cursor, end);
        return GlyphInfo.composite(
                components.toArray(
                        new CompositeComponent[components.size()]),
                bounds);
    }

    private static ResolvedGlyph resolveGlyph(
            int glyph,
            GlyphInfo[] glyphs,
            byte[] states) throws DocumentFailure {
        if (states[glyph] == GLYPH_RESOLVED) {
            return glyphs[glyph].resolved;
        }
        if (states[glyph] == GLYPH_VISITING) {
            throw sourceInvalid();
        }
        states[glyph] = GLYPH_VISITING;
        Deque<ResolveFrame> pending = new ArrayDeque<ResolveFrame>();
        pending.push(new ResolveFrame(glyph));
        while (!pending.isEmpty()) {
            ResolveFrame frame = pending.peek();
            GlyphInfo info = glyphs[frame.glyph];
            if (!info.composite) {
                info.resolved = ResolvedGlyph.simple(info);
                states[frame.glyph] = GLYPH_RESOLVED;
                pending.pop();
                continue;
            }
            if (frame.nextComponent < info.components.length) {
                CompositeComponent component =
                        info.components[frame.nextComponent];
                if (states[component.glyph] == GLYPH_VISITING) {
                    throw sourceInvalid();
                }
                if (states[component.glyph] == GLYPH_UNSEEN) {
                    states[component.glyph] = GLYPH_VISITING;
                    pending.push(new ResolveFrame(component.glyph));
                    continue;
                }
                frame.include(component, glyphs[component.glyph].resolved);
                frame.nextComponent++;
                continue;
            }
            info.resolved = frame.finish(info);
            states[frame.glyph] = GLYPH_RESOLVED;
            pending.pop();
        }
        return glyphs[glyph].resolved;
    }

    private static void validateHorizontalMetrics(
            HeadInfo head,
            HorizontalHeader header,
            HorizontalMetrics metrics,
            GlyphInfo[] glyphs) throws DocumentFailure {
        HorizontalSummary summary = horizontalSummary(head, metrics, glyphs);
        if (header.maximumAdvance != summary.maximumAdvance
                || header.minimumLeftSideBearing
                        != summary.minimumLeftSideBearing
                || header.minimumRightSideBearing
                        != summary.minimumRightSideBearing
                || header.maximumExtent != summary.maximumExtent) {
            throw sourceInvalid();
        }
    }

    private static HorizontalSummary horizontalSummary(
            HeadInfo head,
            HorizontalMetrics metrics,
            GlyphInfo[] glyphs) throws DocumentFailure {
        int maximumAdvance = 0;
        int minimumLeftSideBearing = Integer.MAX_VALUE;
        int minimumRightSideBearing = Integer.MAX_VALUE;
        int maximumExtent = Integer.MIN_VALUE;
        for (int glyph = 0; glyph < glyphs.length; glyph++) {
            int advance = metrics.advances[glyph];
            int leftSideBearing = metrics.sideBearings[glyph];
            maximumAdvance = Math.max(maximumAdvance, advance);
            if (!glyphs[glyph].resolved.hasOutline) {
                if ((head.flags & 0x0002) != 0
                        && glyphs[glyph].hasBounds
                        && glyphs[glyph].bounds.minimumX != leftSideBearing) {
                    throw sourceInvalid();
                }
                continue;
            }
            if ((head.flags & 0x0002) != 0
                    && glyphs[glyph].bounds.minimumX != leftSideBearing) {
                throw sourceInvalid();
            }
            int extent = glyphs[glyph].bounds.width();
            minimumLeftSideBearing = Math.min(
                    minimumLeftSideBearing, leftSideBearing);
            minimumRightSideBearing = Math.min(
                    minimumRightSideBearing,
                    advance - leftSideBearing - extent);
            maximumExtent = Math.max(
                    maximumExtent, leftSideBearing + extent);
        }
        if (minimumLeftSideBearing == Integer.MAX_VALUE) {
            minimumLeftSideBearing = 0;
            minimumRightSideBearing = 0;
            maximumExtent = 0;
        }
        if (minimumLeftSideBearing < Short.MIN_VALUE
                || minimumLeftSideBearing > Short.MAX_VALUE
                || minimumRightSideBearing < Short.MIN_VALUE
                || minimumRightSideBearing > Short.MAX_VALUE
                || maximumExtent < Short.MIN_VALUE
                || maximumExtent > Short.MAX_VALUE) {
            throw sourceInvalid();
        }
        return new HorizontalSummary(
                maximumAdvance,
                minimumLeftSideBearing,
                minimumRightSideBearing,
                maximumExtent);
    }

    private static void validateGlobalBounds(
            HeadInfo head,
            GlyphInfo[] glyphs) throws DocumentFailure {
        GlyphBounds bounds = glyphBounds(glyphs);
        if (!head.bounds.sameAs(bounds)) {
            throw sourceInvalid();
        }
    }

    private static GlyphBounds glyphBounds(GlyphInfo[] glyphs) {
        GlyphBounds bounds = null;
        for (GlyphInfo glyph : glyphs) {
            if (glyph.resolved.hasOutline) {
                bounds = bounds == null
                        ? glyph.bounds
                        : bounds.union(glyph.bounds);
            }
        }
        return bounds == null ? GlyphBounds.ZERO : bounds;
    }

    private static void validateNames(byte[] bytes, TableRange name)
            throws DocumentFailure {
        if (name.length < 6) {
            throw sourceInvalid();
        }
        int format = unsignedShort(bytes, name.offset);
        int count = unsignedShort(bytes, name.offset + 2);
        int storageOffset = unsignedShort(bytes, name.offset + 4);
        long recordsEnd = 6L + 12L * count;
        if (format != 0) {
            throw formatUnsupported();
        }
        if (recordsEnd > name.length) {
            throw sourceInvalid();
        }
        if (storageOffset < recordsEnd || storageOffset > name.length) {
            throw sourceInvalid();
        }
        if (storageOffset != recordsEnd) {
            throw formatUnsupported();
        }
        boolean postScriptName = false;
        NameKey previousKey = null;
        int expectedStringOffset = 0;
        for (int index = 0; index < count; index++) {
            int record = name.offset + 6 + 12 * index;
            int platform = unsignedShort(bytes, record);
            int encoding = unsignedShort(bytes, record + 2);
            int language = unsignedShort(bytes, record + 4);
            int nameId = unsignedShort(bytes, record + 6);
            int length = unsignedShort(bytes, record + 8);
            int offset = unsignedShort(bytes, record + 10);
            NameKey key = new NameKey(
                    platform, encoding, language, nameId);
            if (previousKey != null && previousKey.compareTo(key) >= 0) {
                throw sourceInvalid();
            }
            previousKey = key;
            if (platform != 3
                    || encoding != 1
                    || language != 0x0409
                    || (length & 1) != 0) {
                throw formatUnsupported();
            }
            if (!validNameId(nameId)) {
                throw sourceInvalid();
            }
            if (!supportedNameId(nameId)) {
                throw formatUnsupported();
            }
            requireNameStringRange(length, offset, storageOffset, name.length);
            if (length == 0 || offset != expectedStringOffset) {
                throw formatUnsupported();
            }
            validateBmpUtf16Be(
                    bytes,
                    name.offset + storageOffset + offset,
                    length);
            expectedStringOffset += length;
            if (nameId == 5) {
                validateVersionName(
                        bytes,
                        name.offset + storageOffset + offset,
                        length);
            } else if (nameId == 6) {
                validatePostScriptName(
                        bytes,
                        name.offset + storageOffset + offset,
                        length);
                postScriptName = true;
            }
        }
        if (expectedStringOffset != name.length - storageOffset) {
            throw formatUnsupported();
        }
        if (!postScriptName) {
            throw formatUnsupported();
        }
    }

    private static boolean validNameId(int nameId) {
        return nameId <= 14
                || (nameId >= 16 && nameId <= 25)
                || (nameId >= 256 && nameId <= 32767);
    }

    private static boolean supportedNameId(int nameId) {
        return nameId >= 1 && nameId <= 6;
    }

    private static void validateVersionName(
            byte[] bytes,
            int offset,
            int length) throws DocumentFailure {
        int characters = length / 2;
        int cursor = 0;
        while (cursor < characters) {
            while (cursor < characters
                    && !isAsciiDigit(unsignedShort(
                            bytes, offset + 2 * cursor))) {
                cursor++;
            }
            long major = 0L;
            int majorDigits = 0;
            while (cursor < characters
                    && isAsciiDigit(unsignedShort(
                            bytes, offset + 2 * cursor))) {
                major = Math.min(
                        65535L,
                        major * 10L + unsignedShort(
                                bytes, offset + 2 * cursor) - '0');
                majorDigits++;
                cursor++;
            }
            if (majorDigits == 0
                    || cursor >= characters
                    || unsignedShort(bytes, offset + 2 * cursor) != '.') {
                continue;
            }
            cursor++;
            long minor = 0L;
            int minorDigits = 0;
            while (cursor < characters
                    && isAsciiDigit(unsignedShort(
                            bytes, offset + 2 * cursor))) {
                minor = Math.min(
                        65535L,
                        minor * 10L + unsignedShort(
                                bytes, offset + 2 * cursor) - '0');
                minorDigits++;
                cursor++;
            }
            if (minorDigits > 0 && major < 65535L && minor < 65535L) {
                return;
            }
        }
        throw sourceInvalid();
    }

    private static boolean isAsciiDigit(int value) {
        return value >= '0' && value <= '9';
    }

    private static void validateBmpUtf16Be(
            byte[] bytes,
            int offset,
            int length) throws DocumentFailure {
        for (int index = 0; index < length; index += 2) {
            int codeUnit = unsignedShort(bytes, offset + index);
            if (codeUnit >= 0xd800 && codeUnit <= 0xdfff) {
                throw sourceInvalid();
            }
        }
    }

    private static void validatePostScriptName(
            byte[] bytes,
            int offset,
            int length) throws DocumentFailure {
        int characters = length / 2;
        if (characters == 0 || characters > 63) {
            throw sourceInvalid();
        }
        for (int index = 0; index < characters; index++) {
            int value = unsignedShort(bytes, offset + 2 * index);
            if (!isPostScriptNameCharacter(value)) {
                throw sourceInvalid();
            }
        }
    }

    private static void requireNameStringRange(
            int length,
            int offset,
            int storageOffset,
            int tableLength) throws DocumentFailure {
        long start = (long) storageOffset + offset;
        if (start > tableLength || length > tableLength - start) {
            throw sourceInvalid();
        }
    }

    private static void validatePost(
            byte[] bytes,
            TableRange post,
            int glyphCount) throws DocumentFailure {
        if (post.length < 32
                || unsignedInt(bytes, post.offset + 16)
                        > unsignedInt(bytes, post.offset + 20)
                || unsignedInt(bytes, post.offset + 24)
                        > unsignedInt(bytes, post.offset + 28)) {
            throw sourceInvalid();
        }
        long version = unsignedInt(bytes, post.offset);
        if (version == 0x00010000L) {
            throw formatUnsupported();
        }
        if (version == 0x00030000L) {
            if (post.length != 32) {
                throw sourceInvalid();
            }
            return;
        }
        if (version != 0x00020000L) {
            throw formatUnsupported();
        }
        if (post.length < 34) {
            throw sourceInvalid();
        }
        int declaredGlyphs = unsignedShort(bytes, post.offset + 32);
        if (declaredGlyphs != glyphCount
                || post.length < 34L + 2L * glyphCount) {
            throw sourceInvalid();
        }
        int maximumCustomName = -1;
        int cursor = post.offset + 34;
        for (int glyph = 0; glyph < glyphCount; glyph++) {
            int nameIndex = unsignedShort(bytes, cursor);
            cursor += 2;
            maximumCustomName = Math.max(
                    maximumCustomName,
                    nameIndex - 258);
        }
        for (int nameIndex = 0;
                nameIndex <= maximumCustomName;
                nameIndex++) {
            if (cursor >= post.offset + post.length) {
                throw sourceInvalid();
            }
            int length = bytes[cursor++] & 0xff;
            if (length == 0
                    || length > 63
                    || length > post.offset + post.length - cursor) {
                throw sourceInvalid();
            }
            for (int index = 0; index < length; index++) {
                int value = bytes[cursor++] & 0xff;
                if (!isPostGlyphNameCharacter(value)) {
                    throw sourceInvalid();
                }
            }
        }
        if (cursor != post.offset + post.length) {
            throw sourceInvalid();
        }
    }

    private static boolean isPostScriptNameCharacter(int value) {
        return value >= 33
                && value <= 126
                && "[](){}<>/%".indexOf(value) < 0;
    }

    private static boolean isPostGlyphNameCharacter(int value) {
        return value >= 'A' && value <= 'Z'
                || value >= 'a' && value <= 'z'
                || value >= '0' && value <= '9'
                || value == '.'
                || value == '_';
    }

    private static boolean validateOs2(
            byte[] bytes,
            TableRange os2,
            int macStyle,
            CmapInfo cmap)
            throws DocumentFailure {
        if (os2.length < 2) {
            throw sourceInvalid();
        }
        int version = unsignedShort(bytes, os2.offset);
        int expectedLength;
        if (version == 0 && os2.length == 68) {
            throw formatUnsupported();
        }
        if (version == 0) {
            expectedLength = 78;
        } else if (version == 1) {
            expectedLength = 86;
        } else if (version >= 2 && version <= 4) {
            expectedLength = 96;
        } else if (version == 5) {
            expectedLength = 100;
        } else {
            throw sourceInvalid();
        }
        if (os2.length != expectedLength) {
            throw sourceInvalid();
        }
        validateOs2RangeBits(bytes, os2, version);
        int fsType = unsignedShort(bytes, os2.offset + 8);
        if (version < 2 && (fsType & ~0x000e) != 0) {
            throw formatUnsupported();
        }
        int usage = fsType & 0x000e;
        boolean restricted = version <= 2
                ? (usage & 0x000c) == 0 && (usage & 0x0002) != 0
                : (usage & 0x0002) != 0;
        if (restricted || (version >= 2 && (fsType & 0x0200) != 0)) {
            throw embeddingRestricted();
        }
        int weightClass = unsignedShort(bytes, os2.offset + 4);
        int widthClass = unsignedShort(bytes, os2.offset + 6);
        int familyClass = signedShort(bytes, os2.offset + 30);
        int selection = unsignedShort(bytes, os2.offset + 62);
        int allowedSelection = version >= 4 ? 0x03ff : 0x007f;
        if (familyClass != 0) {
            throw formatUnsupported();
        }
        if ((version >= 2 && (fsType & ~0x030e) != 0)
                || (version >= 3 && Integer.bitCount(usage) > 1)
                || !validVendorTag(bytes, os2.offset + 58)
                || weightClass < 1
                || weightClass > 1000
                || widthClass < 1
                || widthClass > 9
                || (selection & ~allowedSelection) != 0
                || ((selection & 0x0040) != 0
                        && (selection & 0x0021) != 0)
                || unsignedShort(bytes, os2.offset + 64)
                        != cmap.firstCharacterIndex()
                || unsignedShort(bytes, os2.offset + 66)
                        != cmap.lastCharacterIndex()) {
            throw sourceInvalid();
        }
        boolean headBold = (macStyle & 0x0001) != 0;
        boolean headItalic = (macStyle & 0x0002) != 0;
        boolean os2Bold = (selection & 0x0020) != 0;
        boolean os2Italic = (selection & 0x0001) != 0;
        if (headBold != os2Bold || headItalic != os2Italic) {
            throw sourceInvalid();
        }
        if (version == 5) {
            int lower = unsignedShort(bytes, os2.offset + 96);
            int upper = unsignedShort(bytes, os2.offset + 98);
            if (lower >= upper || lower > 0xfffe || upper < 2) {
                throw sourceInvalid();
            }
        }
        return version >= 2 && (fsType & 0x0100) != 0;
    }

    private static void validateOs2RangeBits(
            byte[] bytes,
            TableRange os2,
            int version) throws DocumentFailure {
        long reservedUnicodeBits = unsignedInt(bytes, os2.offset + 54)
                & 0xf8000000L;
        if (reservedUnicodeBits != 0L) {
            throw sourceInvalid();
        }
        if (version >= 1) {
            long codePageRange1 = unsignedInt(bytes, os2.offset + 78);
            long codePageRange2 = unsignedInt(bytes, os2.offset + 82);
            long versionSpecificReserved = version == 1 ? 0x00000100L : 0L;
            if ((codePageRange1
                            & (0x1fc0fe00L | versionSpecificReserved)) != 0L
                    || (codePageRange2 & 0x0000ffffL) != 0L) {
                throw sourceInvalid();
            }
        }
    }

    private static CmapInfo validateCmap(
            byte[] bytes,
            TableRange cmap,
            int glyphCount) throws DocumentFailure {
        if (cmap.length < 4
                || unsignedShort(bytes, cmap.offset) != 0) {
            throw sourceInvalid();
        }
        int count = unsignedShort(bytes, cmap.offset + 2);
        long recordsEnd = 4L + 8L * count;
        if (count == 0 || recordsEnd > cmap.length) {
            throw sourceInvalid();
        }
        if (count != 1) {
            throw formatUnsupported();
        }
        CmapInfo result = new CmapInfo();
        int record = cmap.offset + 4;
        int platform = unsignedShort(bytes, record);
        int encoding = unsignedShort(bytes, record + 2);
        long relativeOffset = unsignedInt(bytes, record + 4);
        if (relativeOffset < recordsEnd
                || relativeOffset > cmap.length - 2L) {
            throw sourceInvalid();
        }
        if (relativeOffset != recordsEnd) {
            throw formatUnsupported();
        }
        int subtable = cmap.offset + (int) relativeOffset;
        int format = unsignedShort(bytes, subtable);
        if (!isUsableUnicodeRecord(platform, encoding, format)) {
            throw formatUnsupported();
        }
        if (format == 4) {
            validateFormat4(bytes, cmap, subtable, glyphCount, result);
        } else {
            validateFormat12(bytes, cmap, subtable, glyphCount, result);
        }
        int language = format == 4
                ? unsignedShort(bytes, subtable + 4)
                : (int) unsignedInt(bytes, subtable + 8);
        if (language != 0) {
            throw sourceInvalid();
        }
        if (!result.hasMapping()) {
            throw formatUnsupported();
        }
        return result;
    }

    private static boolean isUsableUnicodeRecord(
            int platform,
            int encoding,
            int format) {
        return (platform == 0
                        && (encoding == 1 || encoding == 3)
                        && format == 4)
                || (platform == 0 && encoding == 4 && format == 12)
                || (platform == 3 && encoding == 1 && format == 4);
    }

    private static void validateFormat12(
            byte[] bytes,
            TableRange cmap,
            int subtable,
            int glyphCount,
            CmapInfo result) throws DocumentFailure {
        if (subtable > cmap.offset + cmap.length - 16
                || unsignedShort(bytes, subtable + 2) != 0) {
            throw sourceInvalid();
        }
        long length = unsignedInt(bytes, subtable + 4);
        long groups = unsignedInt(bytes, subtable + 12);
        if (length < 16L
                || length > cmap.offset + cmap.length - subtable
                || groups > (length - 16L) / 12L
                || length != 16L + 12L * groups
                || length != cmap.offset + cmap.length - subtable) {
            throw sourceInvalid();
        }
        long previousEnd = -1L;
        for (long index = 0L; index < groups; index++) {
            int group = subtable + 16 + (int) (12L * index);
            long start = unsignedInt(bytes, group);
            long end = unsignedInt(bytes, group + 4);
            long startGlyph = unsignedInt(bytes, group + 8);
            long lastGlyph = startGlyph + end - start;
            if (start > end
                    || start <= previousEnd
                    || end > 0x10ffffL
                    || (start <= 0xdfffL && end >= 0xd800L)
                    || startGlyph >= glyphCount
                    || lastGlyph >= glyphCount) {
                throw sourceInvalid();
            }
            long firstMapped = startGlyph == 0L ? start + 1L : start;
            if (firstMapped <= end) {
                result.include(firstMapped, end);
            }
            previousEnd = end;
        }
    }

    private static void validateFormat4(
            byte[] bytes,
            TableRange cmap,
            int subtable,
            int glyphCount,
            CmapInfo result) throws DocumentFailure {
        if (subtable > cmap.offset + cmap.length - 16) {
            throw sourceInvalid();
        }
        int length = unsignedShort(bytes, subtable + 2);
        int segmentCountX2 = unsignedShort(bytes, subtable + 6);
        if (length < 24
                || (length & 1) != 0
                || length > cmap.offset + cmap.length - subtable
                || segmentCountX2 == 0
                || (segmentCountX2 & 1) != 0
                || length != cmap.offset + cmap.length - subtable) {
            throw sourceInvalid();
        }
        int segmentCount = segmentCountX2 / 2;
        SearchParameters parameters = searchParameters(segmentCount);
        if (unsignedShort(bytes, subtable + 8)
                        != parameters.maximumPowerOfTwo * 2
                || unsignedShort(bytes, subtable + 10)
                        != parameters.entrySelector
                || unsignedShort(bytes, subtable + 12)
                        != segmentCountX2
                                - parameters.maximumPowerOfTwo * 2
                || 16L + 8L * segmentCount > length) {
            throw sourceInvalid();
        }
        int endCodes = subtable + 14;
        int startCodes = endCodes + 2 * segmentCount + 2;
        int deltas = startCodes + 2 * segmentCount;
        int rangeOffsets = deltas + 2 * segmentCount;
        if (unsignedShort(bytes, endCodes + 2 * segmentCount) != 0) {
            throw sourceInvalid();
        }
        int previousEnd = -1;
        for (int segment = 0; segment < segmentCount; segment++) {
            int start = unsignedShort(bytes, startCodes + 2 * segment);
            int end = unsignedShort(bytes, endCodes + 2 * segment);
            if (start > end
                    || start <= previousEnd
                    || (start <= 0xdfff && end >= 0xd800)) {
                throw sourceInvalid();
            }
            previousEnd = end;
            int delta = signedShort(bytes, deltas + 2 * segment);
            int rangeOffset = unsignedShort(
                    bytes,
                    rangeOffsets + 2 * segment);
            if ((rangeOffset & 1) != 0) {
                throw sourceInvalid();
            }
            for (int codePoint = start; codePoint <= end; codePoint++) {
                int glyph;
                if (rangeOffset == 0) {
                    glyph = (codePoint + delta) & 0xffff;
                } else {
                    long address = (long) rangeOffsets + 2L * segment
                            + rangeOffset + 2L * (codePoint - start);
                    long glyphArray = (long) rangeOffsets
                            + 2L * segmentCount;
                    if (address < glyphArray
                            || address > (long) subtable + length - 2L) {
                        throw sourceInvalid();
                    }
                    glyph = unsignedShort(bytes, (int) address);
                    if (glyph != 0) {
                        glyph = (glyph + delta) & 0xffff;
                    }
                }
                if (glyph >= glyphCount) {
                    throw sourceInvalid();
                }
                if (codePoint == 0xffff && glyph != 0) {
                    throw sourceInvalid();
                }
                if (glyph != 0) {
                    result.include(codePoint, codePoint);
                }
            }
        }
        if (previousEnd != 0xffff
                || unsignedShort(
                        bytes,
                        startCodes + 2 * (segmentCount - 1)) != 0xffff
                || unsignedShort(
                        bytes,
                        deltas + 2 * (segmentCount - 1)) != 1
                || unsignedShort(
                        bytes,
                        rangeOffsets + 2 * (segmentCount - 1)) != 0) {
            throw sourceInvalid();
        }
    }

    private static void requireZeroPadding(
            byte[] bytes,
            int cursor,
            int end) throws DocumentFailure {
        if (cursor > end || end - cursor > 3) {
            throw sourceInvalid();
        }
        while (cursor < end) {
            if (bytes[cursor++] != 0) {
                throw sourceInvalid();
            }
        }
    }

    private static int minimum(int[] values) {
        int result = Integer.MAX_VALUE;
        for (int value : values) {
            result = Math.min(result, value);
        }
        return result;
    }

    private static int maximum(int[] values) {
        int result = Integer.MIN_VALUE;
        for (int value : values) {
            result = Math.max(result, value);
        }
        return result;
    }

    private static boolean validTag(byte[] bytes, int offset) {
        boolean sawCharacter = false;
        boolean sawPadding = false;
        for (int index = 0; index < 4; index++) {
            int value = bytes[offset + index] & 0xff;
            if (value < 0x20 || value > 0x7e) {
                return false;
            }
            if (value == 0x20) {
                sawPadding = true;
            } else {
                if (sawPadding) {
                    return false;
                }
                sawCharacter = true;
            }
        }
        return sawCharacter;
    }

    private static boolean validVendorTag(byte[] bytes, int offset) {
        return fourBytesEqual(bytes, offset, 0)
                || fourBytesEqual(bytes, offset, 0x20)
                || validTag(bytes, offset);
    }

    private static boolean fourBytesEqual(
            byte[] bytes,
            int offset,
            int expected) {
        for (int index = 0; index < 4; index++) {
            if ((bytes[offset + index] & 0xff) != expected) {
                return false;
            }
        }
        return true;
    }

    private static boolean looksLikeUnsupportedRawFont(byte[] bytes) {
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        boolean type1 = (first == 0x80
                        && (second == 0x01 || second == 0x02))
                || first == '%' && second == '!';
        boolean cff = (first == 1 || first == 2)
                && second == 0
                && (bytes[2] & 0xff) >= 4
                && (bytes[3] & 0xff) >= 1
                && (bytes[3] & 0xff) <= 4;
        boolean pdfDictionary = first == '<' && second == '<';
        return type1 || cff || pdfDictionary;
    }

    private static long checksum(
            byte[] bytes,
            int offset,
            int length,
            int zeroOffset) {
        long sum = 0L;
        for (int index = 0; index < length; index += 4) {
            long word = 0L;
            for (int part = 0; part < 4; part++) {
                word <<= 8;
                int absolute = offset + index + part;
                if (index + part < length
                        && (zeroOffset < 0
                                || absolute < zeroOffset
                                || absolute >= zeroOffset + 4)) {
                    word |= bytes[absolute] & 0xffL;
                }
            }
            sum = (sum + word) & 0xffffffffL;
        }
        return sum;
    }

    private static void repairChecksums(
            byte[] bytes,
            Map<String, TableRange> tables) {
        TableRange head = tables.get("head");
        writeInt(bytes, head.offset + 8, 0L);
        int tableCount = unsignedShort(bytes, 4);
        for (int index = 0; index < tableCount; index++) {
            int record = 12 + 16 * index;
            TableRange table = tables.get(ascii(bytes, record, 4));
            writeInt(
                    bytes,
                    record + 4,
                    checksum(
                            bytes,
                            table.offset,
                            table.length,
                            table.head ? table.offset + 8 : -1));
        }
        writeInt(
                bytes,
                head.offset + 8,
                0xb1b0afbaL - checksum(bytes, 0, bytes.length, -1));
    }

    private static void writeShort(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 8);
        bytes[offset + 1] = (byte) value;
    }

    private static void writeInt(byte[] bytes, int offset, long value) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
    }

    private static String ascii(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, StandardCharsets.ISO_8859_1);
    }

    private static int unsignedShort(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) << 8 | bytes[offset + 1] & 0xff;
    }

    private static int signedShort(byte[] bytes, int offset) {
        return (short) unsignedShort(bytes, offset);
    }

    private static long unsignedInt(byte[] bytes, int offset) {
        return (long) (bytes[offset] & 0xff) << 24
                | (long) (bytes[offset + 1] & 0xff) << 16
                | (long) (bytes[offset + 2] & 0xff) << 8
                | (long) (bytes[offset + 3] & 0xff);
    }

    private static final class SearchParameters {
        private final int maximumPowerOfTwo;
        private final int entrySelector;

        SearchParameters(int maximumPowerOfTwo, int entrySelector) {
            this.maximumPowerOfTwo = maximumPowerOfTwo;
            this.entrySelector = entrySelector;
        }
    }

    private static final class NameKey implements Comparable<NameKey> {
        private final int platform;
        private final int encoding;
        private final int language;
        private final int nameId;

        NameKey(int platform, int encoding, int language, int nameId) {
            this.platform = platform;
            this.encoding = encoding;
            this.language = language;
            this.nameId = nameId;
        }

        @Override
        public int compareTo(NameKey other) {
            int comparison = Integer.compare(platform, other.platform);
            if (comparison == 0) {
                comparison = Integer.compare(encoding, other.encoding);
            }
            if (comparison == 0) {
                comparison = Integer.compare(language, other.language);
            }
            if (comparison == 0) {
                comparison = Integer.compare(nameId, other.nameId);
            }
            return comparison;
        }
    }

    private static final class ValidatedOutline {
        private final Map<String, TableRange> tables;
        private final HeadInfo head;
        private final MaximumProfile maximum;
        private final HorizontalHeader horizontalHeader;
        private final HorizontalMetrics horizontalMetrics;
        private final GlyphInfo[] glyphs;

        ValidatedOutline(
                Map<String, TableRange> tables,
                HeadInfo head,
                MaximumProfile maximum,
                HorizontalHeader horizontalHeader,
                HorizontalMetrics horizontalMetrics,
                GlyphInfo[] glyphs) {
            this.tables = tables;
            this.head = head;
            this.maximum = maximum;
            this.horizontalHeader = horizontalHeader;
            this.horizontalMetrics = horizontalMetrics;
            this.glyphs = glyphs;
        }
    }

    private static final class TableRange {
        private final int offset;
        private final int length;
        private final int paddedEnd;
        private final long checksum;
        private final boolean head;

        TableRange(
                int offset,
                int length,
                int paddedEnd,
                long checksum,
                boolean head) {
            this.offset = offset;
            this.length = length;
            this.paddedEnd = paddedEnd;
            this.checksum = checksum;
            this.head = head;
        }
    }

    private static final class HeadInfo {
        private final int indexToLocFormat;
        private final GlyphBounds bounds;
        private final int macStyle;
        private final int flags;

        HeadInfo(
                int indexToLocFormat,
                GlyphBounds bounds,
                int macStyle,
                int flags) {
            this.indexToLocFormat = indexToLocFormat;
            this.bounds = bounds;
            this.macStyle = macStyle;
            this.flags = flags;
        }
    }

    private static final class MaximumProfile {
        private final int glyphCount;
        private final int maximumPoints;
        private final int maximumContours;
        private final int maximumCompositePoints;
        private final int maximumCompositeContours;
        private final int maximumComponentElements;
        private final int maximumComponentDepth;

        MaximumProfile(
                int glyphCount,
                int maximumPoints,
                int maximumContours,
                int maximumCompositePoints,
                int maximumCompositeContours,
                int maximumComponentElements,
                int maximumComponentDepth) {
            this.glyphCount = glyphCount;
            this.maximumPoints = maximumPoints;
            this.maximumContours = maximumContours;
            this.maximumCompositePoints = maximumCompositePoints;
            this.maximumCompositeContours = maximumCompositeContours;
            this.maximumComponentElements = maximumComponentElements;
            this.maximumComponentDepth = maximumComponentDepth;
        }
    }

    private static final class HorizontalHeader {
        private final int maximumAdvance;
        private final int minimumLeftSideBearing;
        private final int minimumRightSideBearing;
        private final int maximumExtent;
        private final int metricCount;

        HorizontalHeader(
                int maximumAdvance,
                int minimumLeftSideBearing,
                int minimumRightSideBearing,
                int maximumExtent,
                int metricCount) {
            this.maximumAdvance = maximumAdvance;
            this.minimumLeftSideBearing = minimumLeftSideBearing;
            this.minimumRightSideBearing = minimumRightSideBearing;
            this.maximumExtent = maximumExtent;
            this.metricCount = metricCount;
        }
    }

    private static final class HorizontalMetrics {
        private final int[] advances;
        private final int[] sideBearings;

        HorizontalMetrics(int[] advances, int[] sideBearings) {
            this.advances = advances;
            this.sideBearings = sideBearings;
        }
    }

    private static final class HorizontalSummary {
        private final int maximumAdvance;
        private final int minimumLeftSideBearing;
        private final int minimumRightSideBearing;
        private final int maximumExtent;

        HorizontalSummary(
                int maximumAdvance,
                int minimumLeftSideBearing,
                int minimumRightSideBearing,
                int maximumExtent) {
            this.maximumAdvance = maximumAdvance;
            this.minimumLeftSideBearing = minimumLeftSideBearing;
            this.minimumRightSideBearing = minimumRightSideBearing;
            this.maximumExtent = maximumExtent;
        }
    }

    private static final class GlyphBounds {
        private static final GlyphBounds ZERO =
                new GlyphBounds(0, 0, 0, 0);

        private final int minimumX;
        private final int minimumY;
        private final int maximumX;
        private final int maximumY;

        GlyphBounds(
                int minimumX,
                int minimumY,
                int maximumX,
                int maximumY) {
            this.minimumX = minimumX;
            this.minimumY = minimumY;
            this.maximumX = maximumX;
            this.maximumY = maximumY;
        }

        int width() {
            return maximumX - minimumX;
        }

        boolean isZero() {
            return matches(0, 0, 0, 0);
        }

        boolean matches(
                int candidateMinimumX,
                int candidateMinimumY,
                int candidateMaximumX,
                int candidateMaximumY) {
            return minimumX == candidateMinimumX
                    && minimumY == candidateMinimumY
                    && maximumX == candidateMaximumX
                    && maximumY == candidateMaximumY;
        }

        boolean sameAs(GlyphBounds candidate) {
            return matches(
                    candidate.minimumX,
                    candidate.minimumY,
                    candidate.maximumX,
                    candidate.maximumY);
        }

        GlyphBounds translated(int horizontal, int vertical) {
            return new GlyphBounds(
                    minimumX + horizontal,
                    minimumY + vertical,
                    maximumX + horizontal,
                    maximumY + vertical);
        }

        GlyphBounds union(GlyphBounds other) {
            return new GlyphBounds(
                    Math.min(minimumX, other.minimumX),
                    Math.min(minimumY, other.minimumY),
                    Math.max(maximumX, other.maximumX),
                    Math.max(maximumY, other.maximumY));
        }
    }

    private static final class CmapInfo {
        private long minimum = Long.MAX_VALUE;
        private long maximum = Long.MIN_VALUE;

        void include(long first, long last) {
            minimum = Math.min(minimum, first);
            maximum = Math.max(maximum, last);
        }

        int firstCharacterIndex() {
            return minimum == Long.MAX_VALUE || minimum > 0xffffL
                    ? 0xffff
                    : (int) minimum;
        }

        int lastCharacterIndex() {
            return maximum == Long.MIN_VALUE || maximum > 0xffffL
                    ? 0xffff
                    : (int) maximum;
        }

        boolean hasMapping() {
            return minimum != Long.MAX_VALUE;
        }

        boolean hasSupplementaryMapping() {
            return maximum > Character.MAX_VALUE;
        }
    }

    /** Validated publication-relevant properties of a source font program. */
    static final class EmbeddingProfile {
        private final boolean fullEmbedding;
        private final boolean supplementaryMapping;

        EmbeddingProfile(
                boolean fullEmbedding,
                boolean supplementaryMapping) {
            this.fullEmbedding = fullEmbedding;
            this.supplementaryMapping = supplementaryMapping;
        }

        boolean requiresFullEmbedding() {
            return fullEmbedding;
        }

        boolean hasSupplementaryMapping() {
            return supplementaryMapping;
        }
    }

    private static final class GlyphInfo {
        private final boolean composite;
        private final boolean hasBounds;
        private final int points;
        private final int contours;
        private final CompositeComponent[] components;
        private final GlyphBounds bounds;
        private ResolvedGlyph resolved;

        private GlyphInfo(
                boolean composite,
                boolean hasBounds,
                int points,
                int contours,
                CompositeComponent[] components,
                GlyphBounds bounds) {
            this.composite = composite;
            this.hasBounds = hasBounds;
            this.points = points;
            this.contours = contours;
            this.components = components;
            this.bounds = bounds;
        }

        static GlyphInfo empty() {
            return new GlyphInfo(
                    false,
                    false,
                    0,
                    0,
                    new CompositeComponent[0],
                    GlyphBounds.ZERO);
        }

        static GlyphInfo simple(
                int points,
                int contours,
                GlyphBounds bounds) {
            return new GlyphInfo(
                    false,
                    true,
                    points,
                    contours,
                    new CompositeComponent[0],
                    bounds);
        }

        static GlyphInfo composite(
                CompositeComponent[] components,
                GlyphBounds bounds) {
            return new GlyphInfo(
                    true,
                    true,
                    0,
                    0,
                    components,
                    bounds);
        }
    }

    private static final class CompositeComponent {
        private final int glyph;
        private final int xOffset;
        private final int yOffset;

        CompositeComponent(int glyph, int xOffset, int yOffset) {
            this.glyph = glyph;
            this.xOffset = xOffset;
            this.yOffset = yOffset;
        }
    }

    private static final class ResolvedGlyph {
        private final int points;
        private final int contours;
        private final int depth;
        private final boolean hasOutline;
        private final GlyphBounds bounds;

        ResolvedGlyph(
                int points,
                int contours,
                int depth,
                boolean hasOutline,
                GlyphBounds bounds) {
            this.points = points;
            this.contours = contours;
            this.depth = depth;
            this.hasOutline = hasOutline;
            this.bounds = bounds;
        }

        static ResolvedGlyph simple(GlyphInfo info) {
            boolean outline = info.points > 0;
            return new ResolvedGlyph(
                    info.points,
                    info.contours,
                    0,
                    outline,
                    outline ? info.bounds : GlyphBounds.ZERO);
        }
    }

    private static final class ResolveFrame {
        private final int glyph;
        private int nextComponent;
        private long points;
        private long contours;
        private int depth;
        private boolean hasOutline;
        private GlyphBounds bounds;

        ResolveFrame(int glyph) {
            this.glyph = glyph;
        }

        void include(
                CompositeComponent component,
                ResolvedGlyph nested) throws DocumentFailure {
            points += nested.points;
            contours += nested.contours;
            depth = Math.max(depth, nested.depth + 1);
            if (points > 0xffffL || contours > 0xffffL) {
                throw sourceInvalid();
            }
            if (!nested.hasOutline) {
                return;
            }
            GlyphBounds shifted = nested.bounds.translated(
                    component.xOffset, component.yOffset);
            bounds = hasOutline ? bounds.union(shifted) : shifted;
            hasOutline = true;
        }

        ResolvedGlyph finish(GlyphInfo info) throws DocumentFailure {
            GlyphBounds actual = hasOutline ? bounds : GlyphBounds.ZERO;
            if (!actual.sameAs(info.bounds)) {
                throw sourceInvalid();
            }
            return new ResolvedGlyph(
                    (int) points,
                    (int) contours,
                    depth,
                    hasOutline,
                    actual);
        }
    }
}
