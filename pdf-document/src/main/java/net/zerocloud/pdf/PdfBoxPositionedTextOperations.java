package net.zerocloud.pdf;

import static net.zerocloud.pdf.PdfBoxFontFailures.formatUnsupported;
import static net.zerocloud.pdf.PdfBoxFontFailures.sourceInvalid;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
    private final Map<FontProgramKey, LoadedFont> loadedFonts =
            new LinkedHashMap<FontProgramKey, LoadedFont>();
    private final IdentityHashMap<FontSource, byte[]> stagedOneShotSources =
            new IdentityHashMap<FontSource, byte[]>();

    PdfBoxPositionedTextOperations(
            PDDocument document,
            List<FontSource> referenceFonts,
            PdfVersion publicationVersion) {
        this.document = document;
        this.referenceFonts = Collections.unmodifiableList(
                new ArrayList<FontSource>(referenceFonts));
        this.publicationVersion = publicationVersion;
    }

    boolean supports(DocumentCommand command) {
        return command instanceof DrawPositionedUnicodeText;
    }

    void finalizeFonts() throws DocumentFailure {
        for (LoadedFont loaded : loadedFonts.values()) {
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
                    loaded.replaceManagedFontEntries();
                    loaded.resourceDictionary.setNeedToBeUpdated(true);
                }
                loaded.finalized = true;
            } catch (IOException | RuntimeException failure) {
                throw sourceInvalid();
            }
        }
    }

    private static void normalizeEmbeddedSubset(COSDictionary type0Font)
            throws IOException, DocumentFailure {
        COSDictionary descendant = descendantFont(type0Font);
        COSDictionary descriptor = dictionary(
                descendant.getItem(COSName.FONT_DESC));
        COSBase rawProgram = PdfBoxPageContentSupport.dereference(
                descriptor.getItem(COSName.FONT_FILE2));
        if (!(rawProgram instanceof COSStream)) {
            throw sourceInvalid();
        }
        COSStream program = (COSStream) rawProgram;
        ByteArrayOutputStream staged = new ByteArrayOutputStream();
        try (InputStream input = program.createInputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                staged.write(buffer, 0, count);
            }
        }
        byte[] normalized = PdfBoxTrueTypePreflight.normalizeSubsetMetrics(
                staged.toByteArray());
        try (OutputStream output = program.createOutputStream(
                COSName.FLATE_DECODE)) {
            output.write(normalized);
        }
        program.setInt(COSName.LENGTH1, normalized.length);
    }

    private static void replaceManagedEntries(
            COSDictionary target,
            COSDictionary replacement,
            ManagedEntryPredicate isManaged) {
        Map<String, PreservedEntry> preserved =
                new TreeMap<String, PreservedEntry>();
        for (COSName name : target.keySet()) {
            if (!isManaged.test(name)) {
                preserved.put(
                        name.getName(),
                        new PreservedEntry(name, target.getItem(name)));
            }
        }
        target.clear();
        target.addAll(replacement);
        for (PreservedEntry entry : preserved.values()) {
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

    private static COSDictionary descendantFont(COSDictionary type0Font) {
        COSBase descendantsBase = PdfBoxPageContentSupport.dereference(
                type0Font.getItem(COSName.DESCENDANT_FONTS));
        if (!(descendantsBase instanceof COSArray)) {
            throw new IllegalStateException("Type 0 font has no descendants");
        }
        COSArray descendants = (COSArray) descendantsBase;
        if (descendants.size() == 0) {
            throw new IllegalStateException("Type 0 font has no descendant");
        }
        return dictionary(descendants.get(0));
    }

    private static COSDictionary dictionary(COSBase value) {
        COSBase resolved = PdfBoxPageContentSupport.dereference(value);
        if (!(resolved instanceof COSDictionary)) {
            throw new IllegalStateException("Expected a font dictionary");
        }
        return (COSDictionary) resolved;
    }

    private static void writeExactWidths(
            COSDictionary type0Font,
            Map<Integer, Integer> widthsByGlyph) {
        COSArray widths = new COSArray();
        COSArray run = null;
        int previous = Integer.MIN_VALUE;
        for (Map.Entry<Integer, Integer> entry
                : new TreeMap<Integer, Integer>(widthsByGlyph).entrySet()) {
            int glyphId = entry.getKey().intValue();
            if (run == null || glyphId != previous + 1) {
                run = new COSArray();
                widths.add(COSInteger.get(glyphId));
                widths.add(run);
            }
            run.add(COSInteger.get(entry.getValue().intValue()));
            previous = glyphId;
        }
        descendantFont(type0Font).setItem(COSName.W, widths);
    }

    void execute(DrawPositionedUnicodeText command) throws DocumentFailure {
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
                        PdfBoxPositionedTextOperations::preservationUnsupported);

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
            ResourcesPlan resources = prepareResources(
                    page.getCOSObject(),
                    runs);
            byte[] operators = serialize(
                    declaration,
                    runs,
                    resources.names,
                    command.getLimits().getMaximumGeneratedContentBytes());
            try {
                PdfBoxContentStreamPreflight.validate(operators);
            } catch (IOException invalidGeneratedContent) {
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
                    PdfBoxPositionedTextOperations::writeFailure);
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
        long totalBytes = 0L;
        try {
            for (FontSource source : sources) {
                byte[] bytes = stage(source, limits.getMaximumSourceBytes() - totalBytes);
                if (bytes.length > limits.getMaximumSourceBytes() - totalBytes) {
                    throw limitFailure();
                }
                totalBytes += bytes.length;
                FontProgramKey key = new FontProgramKey(bytes);
                ParsedProgram program = parsed.get(key);
                if (program == null) {
                    program = parse(key, requestedCodePoints);
                    parsed.put(key, program);
                }
                result.add(new SourceProgram(key, program));
            }
            return result;
        } catch (DocumentFailure failure) {
            closeParsed(parsed.values());
            throw failure;
        }
    }

    private byte[] stage(FontSource source, long remaining)
            throws DocumentFailure {
        if (remaining < 0L) {
            throw limitFailure();
        }
        try {
            switch (source.getSourceKind()) {
                case BYTES:
                    byte[] declared = source.getBytes().get();
                    if (declared.length > remaining) {
                        throw limitFailure();
                    }
                    return declared;
                case PATH:
                    try (InputStream input = Files.newInputStream(
                            source.getPath().get())) {
                        return read(input, remaining);
                    }
                case STREAM:
                case CHANNEL:
                    byte[] staged = stagedOneShotSources.get(source);
                    if (staged == null) {
                        InputStream input = source.getSourceKind()
                                == FontSource.SourceKind.STREAM
                                ? source.getStream().get()
                                : Channels.newInputStream(
                                        source.getChannel().get());
                        staged = read(input, remaining);
                        stagedOneShotSources.put(source, staged);
                    } else if (staged.length > remaining) {
                        throw limitFailure();
                    }
                    return staged;
                default:
                    throw sourceInvalid();
            }
        } catch (DocumentFailure failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            throw sourceInvalid();
        }
    }

    private static byte[] read(InputStream input, long remaining)
            throws IOException, DocumentFailure {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long count = 0L;
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (read > remaining - count) {
                throw limitFailure();
            }
            output.write(buffer, 0, read);
            count += read;
        }
        return output.toByteArray();
    }

    private ParsedProgram parse(
            FontProgramKey key,
            List<Integer> requestedCodePoints) throws DocumentFailure {
        PdfBoxTrueTypePreflight.EmbeddingProfile embeddingProfile =
                PdfBoxTrueTypePreflight.validateForEmbedding(key.bytes);
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
            SelectedGlyph match = null;
            for (SourceProgram program : programs) {
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
            parsedByKey.put(program.key, program.parsed);
        }
        Map<FontProgramKey, Set<Integer>> codePointsByKey =
                new LinkedHashMap<FontProgramKey, Set<Integer>>();
        for (SelectedGlyph glyph : selected) {
            if (glyph.embeddingProfile.requiresFullEmbedding()) {
                continue;
            }
            Set<Integer> codePoints = codePointsByKey.get(glyph.key);
            if (codePoints == null) {
                codePoints = new LinkedHashSet<Integer>();
                LoadedFont loaded = loadedFonts.get(glyph.key);
                if (loaded != null) {
                    codePoints.addAll(loaded.unicodeByGlyph.values());
                }
                codePointsByKey.put(glyph.key, codePoints);
            }
            codePoints.add(glyph.codePoint);
        }
        try {
            for (Map.Entry<FontProgramKey, Set<Integer>> entry
                    : codePointsByKey.entrySet()) {
                ParsedProgram parsed = parsedByKey.get(entry.getKey());
                if (parsed == null) {
                    throw new IOException("font program was not prepared");
                }
                TTFSubsetter subsetter = new TTFSubsetter(
                        parsed.font,
                        SUBSET_TABLES);
                subsetter.addAll(entry.getValue());
                for (int codePoint : FORCED_INVISIBLE_CODE_POINTS) {
                    subsetter.forceInvisible(codePoint);
                }
                subsetter.writeToStream(new ByteArrayOutputStream());
            }
        } catch (IOException | RuntimeException failure) {
            throw sourceInvalid();
        }
    }

    private List<TextRun> encodeRuns(List<SelectedGlyph> selected)
            throws DocumentFailure {
        List<TextRun> runs = new ArrayList<TextRun>();
        for (SelectedGlyph glyph : selected) {
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
                throw sourceInvalid();
            }
            TextRun current = runs.isEmpty() ? null : runs.get(runs.size() - 1);
            if (current == null || current.font != loaded) {
                current = new TextRun(loaded);
                runs.add(current);
            }
            current.encoded.write(encoded, 0, encoded.length);
        }
        return runs;
    }

    private void commitGlyphs(List<SelectedGlyph> selected)
            throws DocumentFailure {
        try {
            for (SelectedGlyph glyph : selected) {
                LoadedFont loaded = loadedFonts.get(glyph.key);
                if (loaded == null) {
                    throw new IllegalStateException("font was not prepared");
                }
                if (loaded.subset && !loaded.finalized) {
                    loaded.font.addToSubset(glyph.codePoint);
                }
            }
            for (SelectedGlyph glyph : selected) {
                LoadedFont loaded = loadedFonts.get(glyph.key);
                loaded.unicodeByGlyph.put(
                        glyph.metric.glyphId,
                        glyph.codePoint);
                loaded.widthByGlyph.put(
                        glyph.metric.glyphId,
                        glyph.metric.width);
            }
            for (LoadedFont loaded
                    : new LinkedHashSet<LoadedFont>(loadedFonts.values())) {
                if (!loaded.widthByGlyph.isEmpty()) {
                    writeExactWidths(
                            loaded.font.getCOSObject(),
                            loaded.widthByGlyph);
                }
            }
        } catch (RuntimeException failure) {
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
                    !glyph.embeddingProfile.requiresFullEmbedding());
            loadedFonts.put(glyph.key, loaded);
            return loaded;
        } catch (IOException | RuntimeException failure) {
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
                font.addToSubset(codePoint.intValue());
            }
            LoadedFont replacement = new LoadedFont(
                    font,
                    finalized.persistentFontResource,
                    finalized.resourceDictionary,
                    true);
            replacement.unicodeByGlyph.putAll(finalized.unicodeByGlyph);
            replacement.widthByGlyph.putAll(finalized.widthByGlyph);
            loadedFonts.put(key, replacement);
            return replacement;
        } catch (IOException | RuntimeException failure) {
            throw sourceInvalid();
        }
    }

    private ResourcesPlan prepareResources(
            COSDictionary page,
            List<TextRun> runs) throws DocumentFailure {
        PdfBoxPageContentSupport.FontResources prepared =
                PdfBoxPageContentSupport.prepareFontResources(
                        page,
                        PdfBoxPositionedTextOperations::preservationUnsupported);
        COSDictionary resources = prepared.resources();
        COSDictionary fonts = prepared.fonts();

        boolean changed = false;
        Map<LoadedFont, COSName> names =
                new IdentityHashMap<LoadedFont, COSName>();
        for (TextRun run : runs) {
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

    private static COSName nameFor(COSDictionary fonts, COSDictionary target) {
        Map<String, COSName> ordered = new TreeMap<String, COSName>();
        for (COSName name : fonts.keySet()) {
            ordered.put(name.getName(), name);
        }
        for (COSName name : ordered.values()) {
            if (PdfBoxPageContentSupport.dereference(fonts.getItem(name))
                    == target) {
                return name;
            }
        }
        return null;
    }

    private static COSName availableFontName(COSDictionary fonts) {
        long maximumSuffix = (long) fonts.size() + 1L;
        for (long suffix = 1L; suffix <= maximumSuffix; suffix++) {
            COSName name = COSName.getPDFName("FolioT19F" + suffix);
            if (!fonts.containsKey(name)) {
                return name;
            }
        }
        throw new IllegalStateException("No available deterministic Font name");
    }

    private static byte[] serialize(
            PositionedUnicodeText declaration,
            List<TextRun> runs,
            Map<LoadedFont, COSName> names,
            long maximumBytes) throws DocumentFailure {
        BoundedAsciiOutput output = new BoundedAsciiOutput(maximumBytes);
        output.append("q\nBT\n");
        TextRun first = runs.get(0);
        appendFont(output, names.get(first.font), declaration.getFontSize());
        output.append(declaration.getRenderingMode().getOperatorValue());
        output.append(" Tr\n");
        StringBuilder matrix = new StringBuilder();
        PdfBoxPageContentSupport.appendMatrix(
                matrix,
                declaration.getTextMatrix(),
                PdfBoxPositionedTextOperations::invalidText);
        output.append(matrix.toString());
        output.append(" Tm\n");
        appendText(output, first.encoded.toByteArray());
        for (int index = 1; index < runs.size(); index++) {
            TextRun run = runs.get(index);
            appendFont(output, names.get(run.font), declaration.getFontSize());
            appendText(output, run.encoded.toByteArray());
        }
        output.append("ET\nQ\n");
        return output.toByteArray();
    }

    private static void appendFont(
            BoundedAsciiOutput output,
            COSName name,
            double size) throws DocumentFailure {
        if (name == null) {
            throw sourceInvalid();
        }
        output.append(PdfBoxPageContentSupport.pdfName(
                name,
                PdfBoxFontFailures::sourceInvalid));
        output.append(' ');
        output.append(PdfBoxPageContentSupport.number(
                size,
                PdfBoxPositionedTextOperations::invalidText));
        output.append(" Tf\n");
    }

    private static void appendText(
            BoundedAsciiOutput output,
            byte[] encoded) throws DocumentFailure {
        output.append('<');
        for (byte value : encoded) {
            int part = value & 0xff;
            output.append(Character.forDigit((part >>> 4) & 0xf, 16));
            output.append(Character.forDigit(part & 0xf, 16));
        }
        output.append("> Tj\n");
    }

    private static final class BoundedAsciiOutput {
        private final long maximumBytes;
        private final ByteArrayOutputStream output =
                new ByteArrayOutputStream();
        private long bytes;

        BoundedAsciiOutput(long maximumBytes) {
            this.maximumBytes = maximumBytes;
        }

        void append(String value) throws DocumentFailure {
            requireCapacity(value.length());
            for (int index = 0; index < value.length(); index++) {
                char character = value.charAt(index);
                if (character > 0x7f) {
                    throw invalidText();
                }
                output.write(character);
            }
        }

        void append(char value) throws DocumentFailure {
            if (value > 0x7f) {
                throw invalidText();
            }
            requireCapacity(1);
            output.write(value);
        }

        void append(int value) throws DocumentFailure {
            append(Integer.toString(value));
        }

        byte[] toByteArray() {
            return output.toByteArray();
        }

        private void requireCapacity(int additional)
                throws DocumentFailure {
            if (additional > maximumBytes - bytes) {
                throw limitFailure();
            }
            bytes += additional;
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

    private static void closePrograms(List<SourceProgram> programs) {
        IdentityHashMap<ParsedProgram, Boolean> closed =
                new IdentityHashMap<ParsedProgram, Boolean>();
        for (SourceProgram program : programs) {
            if (closed.put(program.parsed, Boolean.TRUE) == null) {
                closeQuietly(program.parsed.font);
            }
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

    private static final class FontProgramKey {
        private final byte[] bytes;
        private final int hashCode;

        FontProgramKey(byte[] bytes) {
            this.bytes = Arrays.copyOf(bytes, bytes.length);
            this.hashCode = Arrays.hashCode(this.bytes);
        }

        @Override
        public boolean equals(Object candidate) {
            return candidate instanceof FontProgramKey
                    && Arrays.equals(bytes, ((FontProgramKey) candidate).bytes);
        }

        @Override
        public int hashCode() {
            return hashCode;
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
                boolean subset) {
            this.font = font;
            this.persistentFontResource = persistentFontResource;
            this.resourceDictionary = resourceDictionary;
            makeManagedDictionariesIndirect(resourceDictionary);
            this.descendantsItem = resourceDictionary.getItem(
                    COSName.DESCENDANT_FONTS);
            this.descendantDictionary = descendantFont(resourceDictionary);
            this.descriptorItem = descendantDictionary.getItem(
                    COSName.FONT_DESC);
            this.descriptorDictionary = dictionary(descriptorItem);
            this.subset = subset;
        }

        void replaceManagedFontEntries() {
            COSDictionary replacement = font.getCOSObject();
            COSDictionary replacementDescendant = descendantFont(replacement);
            COSDictionary replacementDescriptor = dictionary(
                    replacementDescendant.getItem(COSName.FONT_DESC));

            replaceManagedEntries(
                    descriptorDictionary,
                    replacementDescriptor,
                    PdfBoxPositionedTextOperations::isManagedDescriptorEntry);
            replacementDescendant.setItem(
                    COSName.FONT_DESC, descriptorItem);
            replaceManagedEntries(
                    descendantDictionary,
                    replacementDescendant,
                    PdfBoxPositionedTextOperations::isManagedDescendantEntry);
            replacement.setItem(
                    COSName.DESCENDANT_FONTS, descendantsItem);
            replaceManagedEntries(
                    resourceDictionary,
                    replacement,
                    PdfBoxPositionedTextOperations::isManagedType0Entry);
        }

        private static void makeManagedDictionariesIndirect(
                COSDictionary type0Font) {
            COSBase rawDescendants = PdfBoxPageContentSupport.dereference(
                    type0Font.getItem(COSName.DESCENDANT_FONTS));
            if (!(rawDescendants instanceof COSArray)) {
                throw new IllegalStateException("Type 0 font has no descendants");
            }
            COSArray descendants = (COSArray) rawDescendants;
            if (descendants.size() == 0) {
                throw new IllegalStateException("Type 0 font has no descendant");
            }
            COSBase rawDescendant = descendants.get(0);
            COSDictionary descendant = dictionary(rawDescendant);
            if (!(rawDescendant instanceof COSObject)) {
                descendants.set(0, new COSObject(descendant));
            }
            COSBase rawDescriptor = descendant.getItem(COSName.FONT_DESC);
            COSDictionary descriptor = dictionary(rawDescriptor);
            if (!(rawDescriptor instanceof COSObject)) {
                descendant.setItem(
                        COSName.FONT_DESC, new COSObject(descriptor));
            }
        }
    }

    private static final class TextRun {
        private final LoadedFont font;
        private final ByteArrayOutputStream encoded = new ByteArrayOutputStream();

        TextRun(LoadedFont font) {
            this.font = font;
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
