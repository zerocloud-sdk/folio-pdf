package net.zerocloud.pdf;

import static net.zerocloud.pdf.PdfBoxFontFailures.formatUnsupported;
import static net.zerocloud.pdf.PdfBoxFontFailures.sourceInvalid;

/** Bounds and allocation model for the GSUB data eagerly read by pinned FontBox. */
final class PdfBoxGsubPreflight {
    private final byte[] bytes;
    private final int start;
    private final int end;
    private final int glyphs;
    private final WorkflowResourceContext resources;
    private long memory;

    private PdfBoxGsubPreflight(byte[] bytes, int offset, int length, int glyphs,
            WorkflowResourceContext resources) {
        this.bytes = bytes;
        this.start = offset;
        this.end = offset + length;
        this.glyphs = glyphs;
        this.resources = resources;
    }

    static long validate(byte[] bytes, int offset, int length, int glyphs,
            WorkflowResourceContext resources) throws DocumentFailure {
        return new PdfBoxGsubPreflight(bytes, offset, length, glyphs, resources).validate();
    }

    private long validate() throws DocumentFailure {
        range(start, 10);
        int minor = u16(start + 2);
        if (u16(start) != 1 || minor > 1) { throw formatUnsupported(); }
        if (minor == 1 && u32(start + 10) != 0) { throw formatUnsupported(); }
        int lookupList = relative(start, u16(start + 8), 2);
        int lookupCount = records(lookupList, 2);
        long[] lookupCosts = new long[lookupCount];
        for (int index = 0; index < lookupCount; index++) {
            lookupCosts[index] = lookup(relative(lookupList, u16(lookupList + 2 + 2 * index), 6));
        }
        int featureList = relative(start, u16(start + 6), 2);
        int featureCount = records(featureList, 6);
        long[] featureCosts = new long[featureCount];
        for (int index = 0; index < featureCount; index++) {
            int feature = relative(featureList, u16(featureList + 6 + 6 * index), 4);
            int parameters = u16(feature);
            if (parameters != 0) { relative(feature, parameters, 2); }
            int count = records(feature + 2, 2);
            long cost = 1;
            for (int item = 0; item < count; item++) {
                int lookup = u16(feature + 4 + 2 * item);
                if (lookup >= lookupCount) { throw sourceInvalid(); }
                cost += lookupCosts[lookup];
                checkMemory(memory + 256L * cost);
            }
            featureCosts[index] = cost;
        }
        int scriptList = relative(start, u16(start + 4), 2);
        int scriptCount = records(scriptList, 6);
        long largestScript = 0;
        for (int index = 0; index < scriptCount; index++) {
            int script = relative(scriptList, u16(scriptList + 6 + 6 * index), 4);
            int count = records(script + 2, 6);
            charge(featureCount);
            boolean[] selected = new boolean[featureCount];
            int defaultLanguage = u16(script);
            if (defaultLanguage != 0) { language(relative(script, defaultLanguage, 6), selected); }
            for (int item = 0; item < count; item++) {
                language(relative(script, u16(script + 8 + 6 * item), 6), selected);
            }
            long cost = 0;
            long largestFeature = 0;
            for (int feature = 0; feature < featureCount; feature++) {
                resources.checkpoint();
                if (selected[feature]) {
                    cost += featureCosts[feature];
                    largestFeature = Math.max(largestFeature, featureCosts[feature]);
                    checkMemory(memory + 256L * (cost + largestFeature));
                }
            }
            // FontBox materializes one script; allow an old and a replacement feature map to coexist.
            largestScript = Math.max(largestScript, cost + largestFeature);
        }
        charge(256L * largestScript);
        return memory;
    }

    private void language(int offset, boolean[] selected) throws DocumentFailure {
        if (u16(offset) != 0) { throw sourceInvalid(); }
        int required = u16(offset + 2);
        if (required != 65535) { select(required, selected); }
        int count = records(offset + 4, 2);
        for (int index = 0; index < count; index++) { select(u16(offset + 6 + 2 * index), selected); }
    }

    private void select(int index, boolean[] selected) throws DocumentFailure {
        resources.checkpoint();
        if (index >= selected.length) { throw sourceInvalid(); }
        selected[index] = true;
    }

    private long lookup(int offset) throws DocumentFailure {
        int type = u16(offset);
        int flags = u16(offset + 2);
        if (type < 1 || type > 8 || (flags & 0x00e0) != 0) { throw sourceInvalid(); }
        int count = records(offset + 4, 2);
        if ((flags & 0x0010) != 0) { u16(offset + 6 + 2 * count); }
        long cost = 0;
        for (int index = 0; index < count; index++) {
            int sub = relative(offset, u16(offset + 6 + 2 * index), 2);
            int subType = type;
            if (type == 7) {
                range(sub, 8);
                subType = u16(sub + 2);
                if (u16(sub) != 1 || subType < 1 || subType > 8 || subType == 7) { throw sourceInvalid(); }
                sub = relative(sub, u32(sub + 4), 2);
            }
            cost += substitution(sub, subType);
            checkMemory(memory + 256L * cost);
        }
        return cost;
    }

    private long substitution(int offset, int type) throws DocumentFailure {
        resources.checkpoint();
        // Pinned FontBox does not read contextual/reverse-chaining bodies, nor does this adapter apply them.
        if (type == 5 || type == 6 || type == 8) { return 0; }
        range(offset, 6);
        int format = u16(offset);
        if ((type == 1 && format != 1 && format != 2) || (type != 1 && format != 1)) {
            throw sourceInvalid();
        }
        int delta = type == 1 && format == 1 ? (short) u16(offset + 4) : 0;
        int covered = coverage(relative(offset, u16(offset + 2), 4), delta);
        if (type == 1 && format == 1) { return covered; }
        int count = records(offset + 4, 2);
        if (count != covered) { throw sourceInvalid(); }
        if (type == 1) {
            glyphArray(offset + 6, count);
            return count;
        }
        long cost = 0;
        for (int index = 0; index < count; index++) {
            int set = relative(offset, u16(offset + 6 + 2 * index), 2);
            int size = records(set, 2);
            if (type == 2 || type == 3) {
                glyphArray(set + 2, size);
                cost++;
            } else {
                for (int ligature = 0; ligature < size; ligature++) {
                    int item = relative(set, u16(set + 2 + 2 * ligature), 4);
                    glyphArray(item, 1);
                    int components = u16(item + 2);
                    if (components < 1 || components > 100) { throw formatUnsupported(); }
                    range(item + 4, 2L * (components - 1));
                    charge(256L * components);
                    glyphArray(item + 4, components - 1);
                    cost += components + 1L;
                }
            }
        }
        return cost;
    }

    private int coverage(int offset, int delta) throws DocumentFailure {
        int format = u16(offset);
        if (format != 1 && format != 2) { throw sourceInvalid(); }
        int count = records(offset + 2, format == 1 ? 2 : 6);
        int last = -1;
        int covered = 0;
        for (int index = 0; index < count; index++) {
            resources.checkpoint();
            int record = offset + 4 + index * (format == 1 ? 2 : 6);
            int first = u16(record);
            int endGlyph = format == 1 ? first : u16(record + 2);
            if (first <= last || endGlyph < first || endGlyph >= glyphs
                    || (format == 2 && u16(record + 4) != covered)) { throw sourceInvalid(); }
            int firstTarget = (first + delta) & 65535;
            int lastTarget = (endGlyph + delta) & 65535;
            // OpenType adds delta modulo 65536; a wrapped interval contains unaddressable GID 65535.
            if (firstTarget > lastTarget || lastTarget >= glyphs) { throw sourceInvalid(); }
            covered += endGlyph - first + 1;
            last = endGlyph;
        }
        return covered;
    }

    private void glyphArray(int offset, int count) throws DocumentFailure {
        range(offset, 2L * count);
        for (int index = 0; index < count; index++) {
            resources.checkpoint();
            if (u16(offset + 2 * index) >= glyphs) { throw sourceInvalid(); }
        }
    }

    private int records(int offset, int stride) throws DocumentFailure {
        int count = u16(offset);
        range(offset + 2, (long) stride * count);
        charge(256L * (count + 1));
        return count;
    }

    private void charge(long bytes) throws DocumentFailure {
        memory += bytes;
        checkMemory(memory);
    }

    private void checkMemory(long bytes) throws DocumentFailure {
        resources.checkpoint();
        try (WorkflowResourceContext.MemoryReservation checked = resources.reserveOwnedMemory(bytes)) {
            // No backend allocation occurs until the caller reserves the complete returned model.
        }
    }

    private int relative(int base, long offset, int length) throws DocumentFailure {
        long absolute = base + offset;
        if (offset == 0 || absolute > end - length) { throw sourceInvalid(); }
        range((int) absolute, length);
        return (int) absolute;
    }

    private void range(int offset, long length) throws DocumentFailure {
        if (offset < start || length < 0 || offset > end - length) { throw sourceInvalid(); }
    }

    private int u16(int offset) throws DocumentFailure {
        range(offset, 2);
        return (bytes[offset] & 255) << 8 | (bytes[offset + 1] & 255);
    }

    private long u32(int offset) throws DocumentFailure {
        return (long) u16(offset) << 16 | u16(offset + 2);
    }
}
