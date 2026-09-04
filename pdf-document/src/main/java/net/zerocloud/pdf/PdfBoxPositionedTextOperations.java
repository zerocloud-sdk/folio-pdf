package net.zerocloud.pdf;

import static net.zerocloud.pdf.PdfBoxFontFailures.formatUnsupported;
import static net.zerocloud.pdf.PdfBoxFontFailures.sourceInvalid;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.zerocloud.pdf.composition.FontLimits;
import net.zerocloud.pdf.composition.FontSelection;
import net.zerocloud.pdf.composition.FontSource;
import net.zerocloud.pdf.composition.PositionedUnicodeText;
import net.zerocloud.pdf.composition.command.DrawPositionedUnicodeText;
import org.apache.fontbox.ttf.CmapLookup;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TTFSubsetter;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

/** Private deterministic T19 font loading and positioned-text implementation. */
final class PdfBoxPositionedTextOperations {

    static final String CAPABILITY_ID =
            "composition.fonts.load-embed-subset-fallback";

    private static final List<String> SUBSET_TABLES = Arrays.asList(
            "head", "hhea", "loca", "maxp", "cvt ", "prep", "glyf",
            "hmtx", "fpgm", "gasp");
    private static final int[] FORCED_INVISIBLE_CODE_POINTS = {
        0x200b, 0x200c, 0x2060, 0xfeff
    };
    private final PDDocument document;
    private final List<FontSource> referenceFonts;
    private final PdfVersion publicationVersion;
    private final WorkflowResourceContext resources;
    private final Map<FontProgramKey, LoadedFont> loadedFonts =
            new LinkedHashMap<FontProgramKey, LoadedFont>();
    private final IdentityHashMap<FontSource, byte[]> stagedOneShotSources =
            new IdentityHashMap<FontSource, byte[]>();

    PdfBoxPositionedTextOperations(
            PDDocument document,
            List<FontSource> referenceFonts,
            PdfVersion publicationVersion,
            WorkflowResourceContext resources) {
        this.document = document;
        this.referenceFonts = Collections.unmodifiableList(
                new ArrayList<FontSource>(referenceFonts));
        this.publicationVersion = publicationVersion;
        this.resources = resources;
    }

    boolean supports(DocumentCommand command) {
        return command instanceof DrawPositionedUnicodeText;
    }

    void finalizeFonts() throws DocumentFailure {
        for (LoadedFont loaded : loadedFonts.values()) {
            resources.checkpoint();
            if (loaded.finalized) {
                continue;
            }
            try {
                if (loaded.subset) {
                    loaded.font.subset();
                    normalizeEmbeddedSubset(loaded.font.getCOSObject());
                }
                writeExactWidths(
                        loaded.font.getCOSObject(), loaded.widthByGlyph);
                if (loaded.resourceDictionary != loaded.font.getCOSObject()) {
                    loaded.replaceManagedFontEntries(resources);
                    loaded.resourceDictionary.setNeedToBeUpdated(true);
                }
                loaded.finalized = true;
            } catch (IOException | RuntimeException failure) {
                resources.rethrowResourceOrTerminalFailure(failure);
                throw sourceInvalid();
            }
        }
    }

    private void normalizeEmbeddedSubset(COSDictionary type0Font)
            throws IOException, DocumentFailure {
        COSDictionary descendant = descendantFont(type0Font, resources);
        COSDictionary descriptor = dictionary(
                descendant.getItem(COSName.FONT_DESC), resources);
        COSBase rawProgram = PdfBoxPageContentSupport.dereference(
                descriptor.getItem(COSName.FONT_FILE2), resources);
        if (!(rawProgram instanceof COSStream)) {
            throw sourceInvalid();
        }
        COSStream program = (COSStream) rawProgram;
        try (WorkflowResourceContext.OwnedByteAccumulator staged =
                resources.ownedByteAccumulator()) {
            PdfBoxHostileInputPreflight.decodeStream(
                    program, resources, staged);
            try (WorkflowResourceContext.OwnedBytes stagedBytes =
                            staged.finishWorking();
                    WorkflowResourceContext.MemoryReservation normalizedBytes =
                            resources.reserveOwnedMemory(
                                    stagedBytes.getBytes().length)) {
                byte[] normalized =
                        PdfBoxTrueTypePreflight.normalizeSubsetMetrics(
                                stagedBytes.getBytes(), resources);
                try (OutputStream output = program.createOutputStream(
                        COSName.FLATE_DECODE)) {
                    resources.writeBytesAsIOException(output, normalized);
                }
                program.setInt(COSName.LENGTH1, normalized.length);
            }
        }
    }

    private static void replaceManagedEntries(
            COSDictionary target,
            COSDictionary replacement,
            ManagedEntryPredicate isManaged,
            WorkflowResourceContext resources) throws DocumentFailure {
        Map<String, PreservedEntry> preserved =
                new TreeMap<String, PreservedEntry>();
        for (COSName name : target.keySet()) {
            resources.checkpoint();
            if (!isManaged.test(name)) {
                preserved.put(
                        name.getName(),
                        new PreservedEntry(name, target.getItem(name)));
            }
        }
        target.clear();
        for (COSName name : replacement.keySet()) {
            resources.checkpoint();
            target.setItem(name, replacement.getItem(name));
        }
        for (PreservedEntry entry : preserved.values()) {
            resources.checkpoint();
            target.setItem(entry.name, entry.value);
        }
    }

    private static boolean isManagedType0Entry(COSName name) {
        return COSName.TYPE.equals(name)
                || COSName.SUBTYPE.equals(name)
                || COSName.BASE_FONT.equals(name)
                || COSName.ENCODING.equals(name)
                || COSName.DESCENDANT_FONTS.equals(name)
                || COSName.TO_UNICODE.equals(name)
                || COSName.NAME.equals(name);
    }

    private static boolean isManagedDescendantEntry(COSName name) {
        switch (name.getName()) {
            case "Type":
            case "Subtype":
            case "BaseFont":
            case "CIDSystemInfo":
            case "FontDescriptor":
            case "DW":
            case "W":
            case "DW2":
            case "W2":
            case "CIDToGIDMap":
            case "CIDSet":
                return true;
            default:
                return false;
        }
    }

    private static boolean isManagedDescriptorEntry(COSName name) {
        switch (name.getName()) {
            case "Type":
            case "FontName":
            case "FontFamily":
            case "FontStretch":
            case "FontWeight":
            case "Flags":
            case "FontBBox":
            case "ItalicAngle":
            case "Ascent":
            case "Descent":
            case "Leading":
            case "CapHeight":
            case "XHeight":
            case "StemV":
            case "StemH":
            case "AvgWidth":
            case "MaxWidth":
            case "MissingWidth":
            case "FontFile":
            case "FontFile2":
            case "FontFile3":
            case "CharSet":
            case "CIDSet":
            case "Style":
            case "Lang":
            case "FD":
            case "FontMatrix":
                return true;
            default:
                return false;
        }
    }

    private static COSDictionary descendantFont(
            COSDictionary type0Font,
            WorkflowResourceContext resources) throws DocumentFailure {
        COSBase descendantsBase = PdfBoxPageContentSupport.dereference(
                type0Font.getItem(COSName.DESCENDANT_FONTS), resources);
        if (!(descendantsBase instanceof COSArray)) {
            throw new IllegalStateException("Type 0 font has no descendants");
        }
        COSArray descendants = (COSArray) descendantsBase;
        if (descendants.size() == 0) {
            throw new IllegalStateException("Type 0 font has no descendant");
        }
        return dictionary(descendants.get(0), resources);
    }

    private static COSDictionary dictionary(
            COSBase value,
            WorkflowResourceContext resources) throws DocumentFailure {
        COSBase resolved = PdfBoxPageContentSupport.dereference(
                value, resources);
        if (!(resolved instanceof COSDictionary)) {
            throw new IllegalStateException("Expected a font dictionary");
        }
        return (COSDictionary) resolved;
    }

    private void writeExactWidths(
            COSDictionary type0Font,
            Map<Integer, Integer> widthsByGlyph) throws DocumentFailure {
        COSArray widths = new COSArray();
        COSArray run = null;
        int previous = Integer.MIN_VALUE;
        TreeMap<Integer, Integer> sorted = new TreeMap<Integer, Integer>();
        for (Map.Entry<Integer, Integer> entry : widthsByGlyph.entrySet()) {
            resources.checkpoint();
            sorted.put(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<Integer, Integer> entry : sorted.entrySet()) {
            resources.checkpoint();
            int glyphId = entry.getKey().intValue();
            if (run == null || glyphId != previous + 1) {
                run = new COSArray();
                widths.add(COSInteger.get(glyphId));
                widths.add(run);
            }
            run.add(COSInteger.get(entry.getValue().intValue()));
            previous = glyphId;
        }
        descendantFont(type0Font, resources).setItem(COSName.W, widths);
    }

    void execute(DrawPositionedUnicodeText command) throws DocumentFailure {
        resources.checkpoint();
        if (command.getVersion() != DrawPositionedUnicodeText.VERSION_1
                || command.getLimits().getVersion() != FontLimits.VERSION_1) {
            throw invalidText();
        }
        PositionedUnicodeText declaration = command.getPositionedUnicodeText();
        List<Integer> codePoints = validateText(
                declaration, command.getLimits());
        PDPage page = selectedPage(command.getPageNumber());
        PdfBoxPageContentSupport.ExistingContents existing =
                PdfBoxPageContentSupport.prepareExistingContents(
                        page.getCOSObject(),
                        PdfBoxPositionedTextOperations::preservationUnsupported,
                        resources);

        List<FontSource> sources = declaration.getFontSelection().getKind()
                == FontSelection.Kind.EXPLICIT
                ? declaration.getFontSelection().getSources()
                : referenceFonts;
        if (sources.size() > command.getLimits().getMaximumFontSources()) {
            throw limitFailure();
        }
        if (sources.isEmpty()) {
            throw missingGlyph();
        }

        List<SourceProgram> programs = stageAndParse(
                sources,
                codePoints,
                command.getLimits());
        List<SelectedGlyph> selected;
        try {
            selected = select(programs, codePoints, command.getLimits());
            validateMappings(selected);
            validatePublicationVersion(selected);
            preflightSubsets(programs, selected);
            List<TextRun> runs = encodeRuns(selected);
            try {
                ResourcesPlan resources = prepareResources(
                        page.getCOSObject(),
                        runs);
                try (WorkflowResourceContext.OwnedBytes ownedOperators =
                        serialize(
                                declaration,
                                runs,
                                resources.names,
                                command.getLimits()
                                        .getMaximumGeneratedContentBytes())) {
                    byte[] operators = ownedOperators.getBytes();
                    try {
                        PdfBoxContentStreamPreflight.validate(
                                operators, this.resources);
                    } catch (IOException invalidGeneratedContent) {
                        this.resources.rethrowResourceOrTerminalFailure(
                                invalidGeneratedContent);
                        throw invalidText();
                    }
                    commitGlyphs(selected);
                    PdfBoxPageContentSupport.apply(
                            document,
                            page,
                            existing,
                            operators,
                            resources.resources,
                            resources.changed,
                            this.resources,
                            PdfBoxPositionedTextOperations::writeFailure);
                }
            } finally {
                closeRuns(runs);
            }
        } finally {
            closePrograms(programs);
        }
    }

    static DocumentFailure signatureFailure() {
        return failure(
                DocumentFailureCode.SIGNATURE_POLICY_REJECTED,
                "The Existing Signature policy does not permit positioned text.");
    }

    static void requireModificationPermission(PasswordSecurityInfo securityInfo)
            throws DocumentFailure {
        if (securityInfo.isPasswordProtected()
                && !securityInfo.getEffectivePermissions().canModify()) {
            throw failure(
                    DocumentFailureCode.DOCUMENT_PERMISSION_DENIED,
                    "The Source credential does not authorize positioned text.");
        }
    }

    private List<Integer> validateText(
            PositionedUnicodeText declaration,
            FontLimits limits) throws DocumentFailure {
        if (declaration == null
                || declaration.getVersion() != PositionedUnicodeText.VERSION_1
                || declaration.getText().isEmpty()
                || declaration.getFontSelection() == null
                || declaration.getRenderingMode() == null
                || declaration.getRenderingMode().getOperatorValue() >= 4
                || declaration.getTextMatrix() == null
                || declaration.getFontSize() <= 0d) {
            throw invalidText();
        }
        PdfBoxPageContentSupport.requireNumber(
                declaration.getFontSize(),
                PdfBoxPositionedTextOperations::invalidText);
        PdfBoxPageContentSupport.requireMatrix(
                declaration.getTextMatrix(),
                PdfBoxPositionedTextOperations::invalidText);

        List<Integer> codePoints = new ArrayList<Integer>();
        String value = declaration.getText();
        int offset = 0;
        while (offset < value.length()) {
            resources.checkpoint();
            char first = value.charAt(offset);
            final int codePoint;
            if (Character.isHighSurrogate(first)) {
                if (offset + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(offset + 1))) {
                    throw invalidText();
                }
                codePoint = Character.toCodePoint(first, value.charAt(offset + 1));
                offset += 2;
            } else if (Character.isLowSurrogate(first)) {
                throw invalidText();
            } else {
                codePoint = first;
                offset++;
            }
            if (codePoints.size() >= limits.getMaximumCodePoints()) {
                throw limitFailure();
            }
            codePoints.add(codePoint);
        }
        if (publicationVersion != null
                && publicationVersion.ordinal()
                        < PdfVersion.PDF_1_2.ordinal()) {
            throw baseVersionUnsupported();
        }
        if (publicationVersion != null
                && publicationVersion.ordinal()
                        < PdfVersion.PDF_1_5.ordinal()) {
            for (Integer codePoint : codePoints) {
                resources.checkpoint();
                if (codePoint.intValue() > Character.MAX_VALUE) {
                    throw supplementaryVersionUnsupported();
                }
            }
        }
        return codePoints;
    }

    private List<SourceProgram> stageAndParse(
            List<FontSource> sources,
            List<Integer> requestedCodePoints,
            FontLimits limits) throws DocumentFailure {
        List<SourceProgram> result = new ArrayList<SourceProgram>(sources.size());
        Map<FontProgramKey, ParsedProgram> parsed =
                new LinkedHashMap<FontProgramKey, ParsedProgram>();
        Map<FontProgramKey, FontProgramKey> canonicalKeys =
                new HashMap<FontProgramKey, FontProgramKey>();
        List<FontProgramKey> createdKeys =
                new ArrayList<FontProgramKey>();
        long totalBytes = 0L;
        try {
            for (FontSource source : sources) {
                resources.checkpoint();
                try (StagedFontBytes staged = stage(
                        source,
                        limits.getMaximumSourceBytes() - totalBytes)) {
                    byte[] bytes = staged.bytes;
                    if (bytes.length
                            > limits.getMaximumSourceBytes() - totalBytes) {
                        throw limitFailure();
                    }
                    totalBytes += bytes.length;
                    FontProgramKey candidate = new FontProgramKey(
                            resources.copyOwnedBytes(bytes), resources);
                    createdKeys.add(candidate);
                    FontProgramKey key = canonicalKeys.get(candidate);
                    if (key == null) {
                        key = candidate;
                        canonicalKeys.put(key, key);
                    } else {
                        candidate.close();
                    }
                    ParsedProgram program = parsed.get(key);
                    if (program == null) {
                        program = parse(key, requestedCodePoints);
                        parsed.put(key, program);
                    }
                    result.add(new SourceProgram(key, program));
                }
            }
            return result;
        } catch (DocumentFailure failure) {
            closeParsed(parsed.values());
            closeKeys(createdKeys);
            throw failure;
        } catch (RuntimeException failure) {
            closeParsed(parsed.values());
            closeKeys(createdKeys);
            resources.rethrowResourceOrTerminalFailure(failure);
            throw failure;
        }
    }

    private StagedFontBytes stage(FontSource source, long remaining)
            throws DocumentFailure {
        if (remaining < 0L) {
            throw limitFailure();
        }
        try {
            switch (source.getSourceKind()) {
                case BYTES:
                    long declaredLength = source.getByteLength().getAsLong();
                    if (declaredLength > remaining) {
                        throw limitFailure();
                    }
                    WorkflowResourceContext.MemoryReservation reservation =
                            resources.reserveOwnedMemory(declaredLength);
                    try {
                        return StagedFontBytes.reserved(
                                source.getBytes().get(), reservation);
                    } catch (RuntimeException failure) {
                        reservation.close();
                        throw failure;
                    }
                case PATH:
                    try (InputStream input = Files.newInputStream(
                            source.getPath().get())) {
                        return read(input, remaining);
                    }
                case STREAM:
                case CHANNEL:
                    byte[] cached = stagedOneShotSources.get(source);
                    if (cached == null) {
                        InputStream input = source.getSourceKind()
                                == FontSource.SourceKind.STREAM
                                ? source.getStream().get()
                                : Channels.newInputStream(
                                        source.getChannel().get());
                        try (StagedFontBytes staged =
                                read(input, remaining)) {
                            resources.retainOwnedMemory(staged.bytes.length);
                            stagedOneShotSources.put(source, staged.bytes);
                            return StagedFontBytes.borrowed(staged.bytes);
                        }
                    } else if (cached.length > remaining) {
                        throw limitFailure();
                    }
                    return StagedFontBytes.borrowed(cached);
                default:
                    throw sourceInvalid();
            }
        } catch (DocumentFailure failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            resources.rethrowResourceOrTerminalFailure(failure);
            throw sourceInvalid();
        }
    }

    private StagedFontBytes read(InputStream input, long remaining)
            throws IOException, DocumentFailure {
        byte[] buffer = new byte[8192];
        long count = 0L;
        int read;
        try (WorkflowResourceContext.OwnedByteAccumulator output =
                resources.ownedByteAccumulator()) {
            InputStream checkpointed = resources.checkpointedInput(input);
            while ((read = checkpointed.read(buffer)) != -1) {
                if (read > remaining - count) {
                    throw limitFailure();
                }
                output.write(buffer, 0, read);
                count += read;
            }
            return StagedFontBytes.owned(output.finishWorking());
        }
    }

    private ParsedProgram parse(
            FontProgramKey key,
            List<Integer> requestedCodePoints) throws DocumentFailure {
        PdfBoxTrueTypePreflight.EmbeddingProfile embeddingProfile =
                PdfBoxTrueTypePreflight.validateForEmbedding(
                        key.bytes, resources);
        TrueTypeFont font = null;
        try {
            font = new TTFParser().parse(new RandomAccessReadBuffer(key.bytes));
            if (font.getHeader() == null
                    || font.getHorizontalHeader() == null
                    || font.getHorizontalMetrics() == null
                    || font.getMaximumProfile() == null
                    || font.getGlyph() == null
                    || font.getNaming() == null
                    || font.getOS2Windows() == null
                    || font.getPostScript() == null
                    || font.getName() == null
                    || font.getName().isEmpty()) {
                closeQuietly(font);
                throw formatUnsupported();
            }
            CmapLookup cmap = font.getUnicodeCmapLookup();
            if (cmap == null) {
                closeQuietly(font);
                throw formatUnsupported();
            }
            int unitsPerEm = font.getUnitsPerEm();
            int glyphCount = font.getNumberOfGlyphs();
            if (unitsPerEm <= 0 || glyphCount <= 0 || glyphCount > 65535) {
                closeQuietly(font);
                throw sourceInvalid();
            }
            Map<Integer, GlyphMetric> glyphs =
                    new HashMap<Integer, GlyphMetric>();
            Set<Integer> forcedInvisibleGlyphs = new HashSet<Integer>();
            for (int codePoint : FORCED_INVISIBLE_CODE_POINTS) {
                int glyphId = cmap.getGlyphId(codePoint);
                if (glyphId != 0) {
                    forcedInvisibleGlyphs.add(glyphId);
                }
            }
            for (Integer codePoint : requestedCodePoints) {
                resources.checkpoint();
                int glyphId = cmap.getGlyphId(codePoint.intValue());
                if (glyphId < 0 || glyphId >= glyphCount) {
                    closeQuietly(font);
                    throw sourceInvalid();
                }
                if (!glyphs.containsKey(codePoint)) {
                    if (glyphId != 0
                            && forcedInvisibleGlyphs.contains(glyphId)
                            && (!isForcedInvisible(codePoint.intValue())
                                    || font.getAdvanceWidth(glyphId) != 0)) {
                        closeQuietly(font);
                        throw mappingUnsupported();
                    }
                    int width = glyphId == 0
                            ? 0
                            : scaleWidth(
                                    font.getAdvanceWidth(glyphId), unitsPerEm);
                    boolean canonicalMapping = true;
                    if (embeddingProfile.requiresFullEmbedding()
                            && glyphId != 0) {
                        List<Integer> mapped = cmap.getCharCodes(glyphId);
                        canonicalMapping = mapped != null
                                && !mapped.isEmpty()
                                && mapped.get(0).intValue()
                                        == codePoint.intValue();
                    }
                    glyphs.put(codePoint, new GlyphMetric(
                            glyphId,
                            width,
                            canonicalMapping));
                }
            }
            return new ParsedProgram(font, glyphs, embeddingProfile);
        } catch (DocumentFailure failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            closeQuietly(font);
            resources.rethrowResourceOrTerminalFailure(failure);
            throw sourceInvalid();
        }
    }

    private static int scaleWidth(int advanceWidth, int unitsPerEm) {
        return (int) ((advanceWidth * 1000L + unitsPerEm / 2L)
                / unitsPerEm);
    }

    private static boolean isForcedInvisible(int codePoint) {
        for (int forced : FORCED_INVISIBLE_CODE_POINTS) {
            if (forced == codePoint) {
                return true;
            }
        }
        return false;
    }

    private List<SelectedGlyph> select(
            List<SourceProgram> programs,
            List<Integer> codePoints,
            FontLimits limits) throws DocumentFailure {
        List<SelectedGlyph> selected =
                new ArrayList<SelectedGlyph>(codePoints.size());
        long checks = 0L;
        for (Integer codePoint : codePoints) {
            resources.checkpoint();
            SelectedGlyph match = null;
            for (SourceProgram program : programs) {
                resources.checkpoint();
                if (checks >= limits.getMaximumFallbackChecks()) {
                    throw limitFailure();
                }
                checks++;
                GlyphMetric glyph = program.parsed.glyphs.get(codePoint);
                if (glyph != null && glyph.glyphId != 0) {
                    match = new SelectedGlyph(
                            codePoint.intValue(),
                            glyph,
                            program.key,
                            program.parsed.embeddingProfile);
                    break;
                }
            }
            if (match == null) {
                throw missingGlyph();
            }
            selected.add(match);
        }
        return selected;
    }

    private void validateMappings(List<SelectedGlyph> selected)
            throws DocumentFailure {
        Map<FontProgramKey, Map<Integer, Integer>> pending =
                new HashMap<FontProgramKey, Map<Integer, Integer>>();
        for (SelectedGlyph glyph : selected) {
            resources.checkpoint();
            if (glyph.embeddingProfile.requiresFullEmbedding()
                    && !glyph.metric.canonicalMapping) {
                throw mappingUnsupported();
            }
            LoadedFont loaded = loadedFonts.get(glyph.key);
            Integer existing = loaded == null
                    ? null : loaded.unicodeByGlyph.get(glyph.metric.glyphId);
            if (existing != null && existing.intValue() != glyph.codePoint) {
                throw mappingUnsupported();
            }
            Map<Integer, Integer> mappings = pending.get(glyph.key);
            if (mappings == null) {
                mappings = new HashMap<Integer, Integer>();
                pending.put(glyph.key, mappings);
            }
            existing = mappings.get(glyph.metric.glyphId);
            if (existing != null && existing.intValue() != glyph.codePoint) {
                throw mappingUnsupported();
            }
            mappings.put(glyph.metric.glyphId, glyph.codePoint);
        }
    }

    private void validatePublicationVersion(List<SelectedGlyph> selected)
            throws DocumentFailure {
        if (publicationVersion == null
                || publicationVersion.ordinal()
                        >= PdfVersion.PDF_1_5.ordinal()) {
            return;
        }
        for (SelectedGlyph glyph : selected) {
            resources.checkpoint();
            if (glyph.embeddingProfile.hasSupplementaryMapping()) {
                throw supplementaryVersionUnsupported();
            }
        }
    }

    private void preflightSubsets(
            List<SourceProgram> programs,
            List<SelectedGlyph> selected) throws DocumentFailure {
        Map<FontProgramKey, ParsedProgram> parsedByKey =
                new HashMap<FontProgramKey, ParsedProgram>();
        for (SourceProgram program : programs) {
            resources.checkpoint();
            parsedByKey.put(program.key, program.parsed);
        }
        Map<FontProgramKey, Set<Integer>> codePointsByKey =
                new LinkedHashMap<FontProgramKey, Set<Integer>>();
        for (SelectedGlyph glyph : selected) {
            resources.checkpoint();
            if (glyph.embeddingProfile.requiresFullEmbedding()) {
                continue;
            }
            Set<Integer> codePoints = codePointsByKey.get(glyph.key);
            if (codePoints == null) {
                codePoints = new LinkedHashSet<Integer>();
                LoadedFont loaded = loadedFonts.get(glyph.key);
                if (loaded != null) {
                    for (Integer codePoint
                            : loaded.unicodeByGlyph.values()) {
                        resources.checkpoint();
                        codePoints.add(codePoint);
                    }
                }
                codePointsByKey.put(glyph.key, codePoints);
            }
            codePoints.add(glyph.codePoint);
        }
        try {
            for (Map.Entry<FontProgramKey, Set<Integer>> entry
                    : codePointsByKey.entrySet()) {
                resources.checkpoint();
                ParsedProgram parsed = parsedByKey.get(entry.getKey());
                if (parsed == null) {
                    throw new IOException("font program was not prepared");
                }
                TTFSubsetter subsetter = new TTFSubsetter(
                        parsed.font,
                        SUBSET_TABLES);
                for (Integer codePoint : entry.getValue()) {
                    resources.checkpoint();
                    subsetter.add(codePoint.intValue());
                }
                for (int codePoint : FORCED_INVISIBLE_CODE_POINTS) {
                    subsetter.forceInvisible(codePoint);
                }
                try (WorkflowResourceContext.OwnedByteAccumulator subset =
                        resources.ownedByteAccumulator()) {
                    subsetter.writeToStream(subset);
                }
            }
        } catch (IOException | RuntimeException failure) {
            resources.rethrowResourceOrTerminalFailure(failure);
            throw sourceInvalid();
        }
    }

    private List<TextRun> encodeRuns(List<SelectedGlyph> selected)
            throws DocumentFailure {
        List<TextRun> runs = new ArrayList<TextRun>();
        boolean complete = false;
        try {
            for (SelectedGlyph glyph : selected) {
                resources.checkpoint();
                LoadedFont loaded = loadedFont(glyph);
                byte[] encoded;
                try {
                    encoded = loaded.font.encode(
                            new String(Character.toChars(glyph.codePoint)));
                    if (encoded.length != 2
                            || (((encoded[0] & 0xff) << 8)
                                    | encoded[1] & 0xff)
                                    != glyph.metric.glyphId) {
                        throw mappingUnsupported();
                    }
                } catch (DocumentFailure failure) {
                    throw failure;
                } catch (IOException | RuntimeException failure) {
                    resources.rethrowResourceOrTerminalFailure(failure);
                    throw sourceInvalid();
                }
                TextRun current = runs.isEmpty()
                        ? null : runs.get(runs.size() - 1);
                if (current == null || current.font != loaded) {
                    current = new TextRun(loaded, resources);
                    runs.add(current);
                }
                current.append(encoded);
            }
            complete = true;
            return runs;
        } finally {
            if (!complete) {
                closeRuns(runs);
            }
        }
    }

    private void commitGlyphs(List<SelectedGlyph> selected)
            throws DocumentFailure {
        try {
            for (SelectedGlyph glyph : selected) {
                resources.checkpoint();
                LoadedFont loaded = loadedFonts.get(glyph.key);
                if (loaded == null) {
                    throw new IllegalStateException("font was not prepared");
                }
                if (loaded.subset && !loaded.finalized) {
                    loaded.font.addToSubset(glyph.codePoint);
                }
            }
            for (SelectedGlyph glyph : selected) {
                resources.checkpoint();
                LoadedFont loaded = loadedFonts.get(glyph.key);
                loaded.unicodeByGlyph.put(
                        glyph.metric.glyphId,
                        glyph.codePoint);
                loaded.widthByGlyph.put(
                        glyph.metric.glyphId,
                        glyph.metric.width);
            }
            Set<LoadedFont> distinctLoaded = new LinkedHashSet<LoadedFont>();
            for (LoadedFont loaded : loadedFonts.values()) {
                resources.checkpoint();
                distinctLoaded.add(loaded);
            }
            for (LoadedFont loaded : distinctLoaded) {
                resources.checkpoint();
                if (!loaded.widthByGlyph.isEmpty()) {
                    writeExactWidths(
                            loaded.font.getCOSObject(),
                            loaded.widthByGlyph);
                }
            }
        } catch (RuntimeException failure) {
            resources.rethrowResourceOrTerminalFailure(failure);
            throw sourceInvalid();
        }
    }

    private LoadedFont loadedFont(SelectedGlyph glyph)
            throws DocumentFailure {
        LoadedFont loaded = loadedFonts.get(glyph.key);
        if (loaded != null) {
            if (!loaded.finalized
                    || !loaded.subset
                    || loaded.unicodeByGlyph.containsKey(
                            glyph.metric.glyphId)) {
                return loaded;
            }
            return replacementFont(glyph.key, loaded);
        }
        try {
            PDType0Font font = PDType0Font.load(
                    document,
                    new ByteArrayInputStream(glyph.key.bytes),
                    !glyph.embeddingProfile.requiresFullEmbedding());
            COSObject raw = new COSObject(font.getCOSObject());
            loaded = new LoadedFont(
                    font,
                    raw,
                    font.getCOSObject(),
                    !glyph.embeddingProfile.requiresFullEmbedding(),
                    resources);
            loadedFonts.put(glyph.key, loaded);
            glyph.key.retainForSession();
            return loaded;
        } catch (IOException | RuntimeException failure) {
            resources.rethrowResourceOrTerminalFailure(failure);
            throw sourceInvalid();
        }
    }

    private LoadedFont replacementFont(
            FontProgramKey key,
            LoadedFont finalized) throws DocumentFailure {
        try {
            PDType0Font font = PDType0Font.load(
                    document,
                    new ByteArrayInputStream(key.bytes),
                    true);
            for (Integer codePoint : finalized.unicodeByGlyph.values()) {
                resources.checkpoint();
                font.addToSubset(codePoint.intValue());
            }
            LoadedFont replacement = new LoadedFont(
                    font,
                    finalized.persistentFontResource,
                    finalized.resourceDictionary,
                    true,
                    resources);
            for (Map.Entry<Integer, Integer> entry
                    : finalized.unicodeByGlyph.entrySet()) {
                resources.checkpoint();
                replacement.unicodeByGlyph.put(
                        entry.getKey(), entry.getValue());
            }
            for (Map.Entry<Integer, Integer> entry
                    : finalized.widthByGlyph.entrySet()) {
                resources.checkpoint();
                replacement.widthByGlyph.put(
                        entry.getKey(), entry.getValue());
            }
            loadedFonts.put(key, replacement);
            return replacement;
        } catch (IOException | RuntimeException failure) {
            resources.rethrowResourceOrTerminalFailure(failure);
            throw sourceInvalid();
        }
    }

    private ResourcesPlan prepareResources(
            COSDictionary page,
            List<TextRun> runs) throws DocumentFailure {
        PdfBoxPageContentSupport.FontResources prepared =
                PdfBoxPageContentSupport.prepareFontResources(
                        page,
                        PdfBoxPositionedTextOperations::preservationUnsupported,
                        resources);
        COSDictionary resources = prepared.resources();
        COSDictionary fonts = prepared.fonts();

        boolean changed = false;
        Map<LoadedFont, COSName> names =
                new IdentityHashMap<LoadedFont, COSName>();
        for (TextRun run : runs) {
            this.resources.checkpoint();
            LoadedFont font = run.font;
            if (names.containsKey(font)) {
                continue;
            }
            COSName name = nameFor(fonts, font.resourceDictionary);
            if (name == null) {
                name = availableFontName(fonts);
                fonts.setItem(name, font.persistentFontResource);
                changed = true;
            }
            names.put(font, name);
        }
        if (changed) {
            resources.setItem(COSName.FONT, fonts);
        }
        return new ResourcesPlan(resources, names, changed);
    }

    private COSName nameFor(COSDictionary fonts, COSDictionary target)
            throws DocumentFailure {
        Map<String, COSName> ordered = new TreeMap<String, COSName>();
        for (COSName name : fonts.keySet()) {
            resources.checkpoint();
            ordered.put(name.getName(), name);
        }
        for (COSName name : ordered.values()) {
            resources.checkpoint();
            if (PdfBoxPageContentSupport.dereference(
                    fonts.getItem(name), resources)
                    == target) {
                return name;
            }
        }
        return null;
    }

    private COSName availableFontName(COSDictionary fonts)
            throws DocumentFailure {
        long maximumSuffix = (long) fonts.size() + 1L;
        for (long suffix = 1L; suffix <= maximumSuffix; suffix++) {
            resources.checkpoint();
            COSName name = COSName.getPDFName("FolioT19F" + suffix);
            if (!fonts.containsKey(name)) {
                return name;
            }
        }
        throw new IllegalStateException("No available deterministic Font name");
    }

    private WorkflowResourceContext.OwnedBytes serialize(
            PositionedUnicodeText declaration,
            List<TextRun> runs,
            Map<LoadedFont, COSName> names,
            long maximumBytes) throws DocumentFailure {
        try (WorkflowAsciiOutput output = new WorkflowAsciiOutput(
                resources,
                maximumBytes,
                PdfBoxPositionedTextOperations::limitFailure)) {
            output.append("q\nBT\n");
            TextRun first = runs.get(0);
            appendFont(output, names.get(first.font), declaration.getFontSize());
            output.append(declaration.getRenderingMode().getOperatorValue());
            output.append(" Tr\n");
            PdfBoxPageContentSupport.appendMatrix(
                    output,
                    declaration.getTextMatrix(),
                    PdfBoxPositionedTextOperations::invalidText);
            output.append(" Tm\n");
            appendText(output, first.finish(), resources);
            for (int index = 1; index < runs.size(); index++) {
                TextRun run = runs.get(index);
                appendFont(
                        output,
                        names.get(run.font),
                        declaration.getFontSize());
                appendText(output, run.finish(), resources);
            }
            output.append("ET\nQ\n");
            return output.finishWorking();
        }
    }

    private static void appendFont(
            WorkflowAsciiOutput output,
            COSName name,
            double size) throws DocumentFailure {
        if (name == null) {
            throw sourceInvalid();
        }
        output.appendPdfName(
                name,
                PdfBoxFontFailures::sourceInvalid);
        output.append(' ');
            PdfBoxPageContentSupport.appendNumber(
                    output,
                    size,
                    PdfBoxPositionedTextOperations::invalidText);
            output.append(" Tf\n");
    }

    private static void appendText(
            WorkflowAsciiOutput output,
            WorkflowResourceContext.OwnedBytes encoded,
            WorkflowResourceContext resources)
            throws DocumentFailure {
        try (WorkflowResourceContext.OwnedBytes bytes = encoded) {
            output.append('<');
            byte[] values = bytes.getBytes();
            for (int index = 0; index < values.length; index++) {
                if ((index & 4095) == 0) {
                    resources.checkpoint();
                }
                byte value = values[index];
                int part = value & 0xff;
                output.append(Character.forDigit((part >>> 4) & 0xf, 16));
                output.append(Character.forDigit(part & 0xf, 16));
            }
            output.append("> Tj\n");
        }
    }

    private PDPage selectedPage(int pageNumber) throws DocumentFailure {
        if (pageNumber < 1 || pageNumber > document.getNumberOfPages()) {
            throw failure(
                    DocumentFailureCode.PAGE_RANGE_INVALID,
                    "The positioned-text page selection is invalid.");
        }
        return document.getPage(pageNumber - 1);
    }

    private void closePrograms(List<SourceProgram> programs) {
        IdentityHashMap<ParsedProgram, Boolean> closed =
                new IdentityHashMap<ParsedProgram, Boolean>();
        IdentityHashMap<FontProgramKey, Boolean> closedKeys =
                new IdentityHashMap<FontProgramKey, Boolean>();
        for (SourceProgram program : programs) {
            if (closed.put(program.parsed, Boolean.TRUE) == null) {
                closeQuietly(program.parsed.font);
            }
            if (closedKeys.put(program.key, Boolean.TRUE) == null) {
                program.key.close();
            }
        }
    }

    private static void closeKeys(List<FontProgramKey> keys) {
        for (FontProgramKey key : keys) {
            key.close();
        }
    }

    private static void closeRuns(List<TextRun> runs) {
        for (TextRun run : runs) {
            run.close();
        }
    }

    private static void closeParsed(Iterable<ParsedProgram> programs) {
        for (ParsedProgram program : programs) {
            closeQuietly(program.font);
        }
    }

    private static void closeQuietly(TrueTypeFont font) {
        if (font != null) {
            try {
                font.close();
            } catch (IOException ignored) {
                // Parsing already failed; diagnostics remain fixed and safe.
            }
        }
    }

    private static DocumentFailure invalidText() {
        return failure(
                DocumentFailureCode.POSITIONED_TEXT_INVALID,
                "The positioned Unicode text declaration is invalid.");
    }

    private static DocumentFailure baseVersionUnsupported() {
        return failure(
                DocumentFailureCode.PDF_VERSION_UNSUPPORTED,
                "Positioned Unicode text requires PDF 1.2 or newer.");
    }

    private static DocumentFailure supplementaryVersionUnsupported() {
        return failure(
                DocumentFailureCode.PDF_VERSION_UNSUPPORTED,
                "Supplementary Unicode mappings require PDF 1.5 or newer.");
    }

    private static DocumentFailure missingGlyph() {
        return failure(
                DocumentFailureCode.FONT_GLYPH_MISSING,
                "No declared font contains every requested Unicode scalar.");
    }

    private static DocumentFailure mappingUnsupported() {
        return failure(
                DocumentFailureCode.FONT_MAPPING_UNSUPPORTED,
                "The requested Unicode mapping cannot be represented safely.");
    }

    private static DocumentFailure limitFailure() {
        return failure(
                DocumentFailureCode.FONT_LIMIT_EXCEEDED,
                "The font operation limit was exceeded.");
    }

    private static DocumentFailure preservationUnsupported() {
        return failure(
                DocumentFailureCode.POSITIONED_TEXT_PRESERVATION_UNSUPPORTED,
                "The page content or resources cannot be preserved safely for positioned text.");
    }

    private static DocumentFailure writeFailure() {
        return failure(
                DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                "The positioned Unicode text could not be applied.");
    }

    private static DocumentFailure failure(
            DocumentFailureCode code,
            String diagnostic) {
        return new DocumentFailure(code, CAPABILITY_ID, diagnostic);
    }

    private static final class PreservedEntry {
        private final COSName name;
        private final COSBase value;

        PreservedEntry(COSName name, COSBase value) {
            this.name = name;
            this.value = value;
        }
    }

    private interface ManagedEntryPredicate {
        boolean test(COSName name);
    }

    private static final class FontProgramKey implements AutoCloseable {
        private final byte[] bytes;
        private final int hashCode;
        private final WorkflowResourceContext resources;
        private WorkflowResourceContext.OwnedBytes ownedBytes;

        FontProgramKey(
                WorkflowResourceContext.OwnedBytes ownedBytes,
                WorkflowResourceContext resources) throws DocumentFailure {
            byte[] source = ownedBytes.getBytes();
            int hash = 1;
            try {
                for (int index = 0; index < source.length; index++) {
                    if ((index & 8191) == 0) {
                        resources.checkpoint();
                    }
                    hash = 31 * hash + source[index];
                }
            } catch (DocumentFailure | RuntimeException failure) {
                ownedBytes.close();
                throw failure;
            }
            this.ownedBytes = ownedBytes;
            this.bytes = source;
            this.resources = resources;
            this.hashCode = hash;
        }

        void retainForSession() {
            ownedBytes = null;
        }

        @Override
        public void close() {
            WorkflowResourceContext.OwnedBytes current = ownedBytes;
            if (current != null) {
                ownedBytes = null;
                current.close();
            }
        }

        @Override
        public boolean equals(Object candidate) {
            if (this == candidate) {
                return true;
            }
            if (!(candidate instanceof FontProgramKey)) {
                return false;
            }
            byte[] other = ((FontProgramKey) candidate).bytes;
            if (bytes.length != other.length) {
                return false;
            }
            for (int index = 0; index < bytes.length; index++) {
                if ((index & 8191) == 0) {
                    resources.checkpointAsRuntimeException();
                }
                if (bytes[index] != other[index]) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    private static final class StagedFontBytes implements AutoCloseable {

        private final byte[] bytes;
        private WorkflowResourceContext.MemoryReservation reservation;
        private WorkflowResourceContext.OwnedBytes owned;

        private StagedFontBytes(
                byte[] bytes,
                WorkflowResourceContext.MemoryReservation reservation,
                WorkflowResourceContext.OwnedBytes owned) {
            this.bytes = bytes;
            this.reservation = reservation;
            this.owned = owned;
        }

        static StagedFontBytes reserved(
                byte[] bytes,
                WorkflowResourceContext.MemoryReservation reservation) {
            return new StagedFontBytes(bytes, reservation, null);
        }

        static StagedFontBytes owned(
                WorkflowResourceContext.OwnedBytes owned) {
            return new StagedFontBytes(owned.getBytes(), null, owned);
        }

        static StagedFontBytes borrowed(byte[] bytes) {
            return new StagedFontBytes(bytes, null, null);
        }

        @Override
        public void close() {
            if (reservation != null) {
                reservation.close();
                reservation = null;
            }
            if (owned != null) {
                owned.close();
                owned = null;
            }
        }
    }

    private static final class ParsedProgram {
        private final TrueTypeFont font;
        private final Map<Integer, GlyphMetric> glyphs;
        private final PdfBoxTrueTypePreflight.EmbeddingProfile
                embeddingProfile;

        ParsedProgram(
                TrueTypeFont font,
                Map<Integer, GlyphMetric> glyphs,
                PdfBoxTrueTypePreflight.EmbeddingProfile embeddingProfile) {
            this.font = font;
            this.glyphs = glyphs;
            this.embeddingProfile = embeddingProfile;
        }
    }

    private static final class SourceProgram {
        private final FontProgramKey key;
        private final ParsedProgram parsed;

        SourceProgram(FontProgramKey key, ParsedProgram parsed) {
            this.key = key;
            this.parsed = parsed;
        }
    }

    private static final class GlyphMetric {
        private final int glyphId;
        private final int width;
        private final boolean canonicalMapping;

        GlyphMetric(int glyphId, int width, boolean canonicalMapping) {
            this.glyphId = glyphId;
            this.width = width;
            this.canonicalMapping = canonicalMapping;
        }
    }

    private static final class SelectedGlyph {
        private final int codePoint;
        private final GlyphMetric metric;
        private final FontProgramKey key;
        private final PdfBoxTrueTypePreflight.EmbeddingProfile
                embeddingProfile;

        SelectedGlyph(
                int codePoint,
                GlyphMetric metric,
                FontProgramKey key,
                PdfBoxTrueTypePreflight.EmbeddingProfile embeddingProfile) {
            this.codePoint = codePoint;
            this.metric = metric;
            this.key = key;
            this.embeddingProfile = embeddingProfile;
        }
    }

    private static final class LoadedFont {
        private final PDType0Font font;
        private final COSObject persistentFontResource;
        private final COSDictionary resourceDictionary;
        private final COSBase descendantsItem;
        private final COSDictionary descendantDictionary;
        private final COSBase descriptorItem;
        private final COSDictionary descriptorDictionary;
        private final boolean subset;
        private final Map<Integer, Integer> unicodeByGlyph =
                new LinkedHashMap<Integer, Integer>();
        private final Map<Integer, Integer> widthByGlyph =
                new LinkedHashMap<Integer, Integer>();
        private boolean finalized;

        LoadedFont(
                PDType0Font font,
                COSObject persistentFontResource,
                COSDictionary resourceDictionary,
                boolean subset,
                WorkflowResourceContext resources) throws DocumentFailure {
            this.font = font;
            this.persistentFontResource = persistentFontResource;
            this.resourceDictionary = resourceDictionary;
            makeManagedDictionariesIndirect(resourceDictionary, resources);
            this.descendantsItem = resourceDictionary.getItem(
                    COSName.DESCENDANT_FONTS);
            this.descendantDictionary = descendantFont(
                    resourceDictionary, resources);
            this.descriptorItem = descendantDictionary.getItem(
                    COSName.FONT_DESC);
            this.descriptorDictionary = dictionary(descriptorItem, resources);
            this.subset = subset;
        }

        void replaceManagedFontEntries(WorkflowResourceContext resources)
                throws DocumentFailure {
            COSDictionary replacement = font.getCOSObject();
            COSDictionary replacementDescendant = descendantFont(
                    replacement, resources);
            COSDictionary replacementDescriptor = dictionary(
                    replacementDescendant.getItem(COSName.FONT_DESC),
                    resources);

            replaceManagedEntries(
                    descriptorDictionary,
                    replacementDescriptor,
                    PdfBoxPositionedTextOperations::isManagedDescriptorEntry,
                    resources);
            replacementDescendant.setItem(
                    COSName.FONT_DESC, descriptorItem);
            replaceManagedEntries(
                    descendantDictionary,
                    replacementDescendant,
                    PdfBoxPositionedTextOperations::isManagedDescendantEntry,
                    resources);
            replacement.setItem(
                    COSName.DESCENDANT_FONTS, descendantsItem);
            replaceManagedEntries(
                    resourceDictionary,
                    replacement,
                    PdfBoxPositionedTextOperations::isManagedType0Entry,
                    resources);
        }

        private static void makeManagedDictionariesIndirect(
                COSDictionary type0Font,
                WorkflowResourceContext resources) throws DocumentFailure {
            COSBase rawDescendants = PdfBoxPageContentSupport.dereference(
                    type0Font.getItem(COSName.DESCENDANT_FONTS), resources);
            if (!(rawDescendants instanceof COSArray)) {
                throw new IllegalStateException("Type 0 font has no descendants");
            }
            COSArray descendants = (COSArray) rawDescendants;
            if (descendants.size() == 0) {
                throw new IllegalStateException("Type 0 font has no descendant");
            }
            COSBase rawDescendant = descendants.get(0);
            COSDictionary descendant = dictionary(rawDescendant, resources);
            if (!(rawDescendant instanceof COSObject)) {
                descendants.set(0, new COSObject(descendant));
            }
            COSBase rawDescriptor = descendant.getItem(COSName.FONT_DESC);
            COSDictionary descriptor = dictionary(rawDescriptor, resources);
            if (!(rawDescriptor instanceof COSObject)) {
                descendant.setItem(
                        COSName.FONT_DESC, new COSObject(descriptor));
            }
        }
    }

    private static final class TextRun implements AutoCloseable {

        private final LoadedFont font;
        private final WorkflowResourceContext resources;
        private final WorkflowResourceContext.OwnedByteAccumulator encoded;

        TextRun(
                LoadedFont font,
                WorkflowResourceContext resources) {
            this.font = font;
            this.resources = resources;
            this.encoded = resources.ownedByteAccumulator();
        }

        void append(byte[] bytes) throws DocumentFailure {
            try {
                encoded.write(bytes, 0, bytes.length);
            } catch (IOException failure) {
                resources.rethrowResourceOrTerminalFailure(failure);
                throw sourceInvalid();
            }
        }

        WorkflowResourceContext.OwnedBytes finish()
                throws DocumentFailure {
            WorkflowResourceContext.OwnedBytes bytes =
                    encoded.finishWorking();
            encoded.close();
            return bytes;
        }

        @Override
        public void close() {
            encoded.close();
        }
    }

    private static final class ResourcesPlan {
        private final COSDictionary resources;
        private final Map<LoadedFont, COSName> names;
        private final boolean changed;

        ResourcesPlan(
                COSDictionary resources,
                Map<LoadedFont, COSName> names,
                boolean changed) {
            this.resources = resources;
            this.names = names;
            this.changed = changed;
        }
    }

}
