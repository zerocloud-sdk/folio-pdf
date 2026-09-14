package net.zerocloud.pdf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bounds CMap construction and retains exact declared Unicode observations.
 *
 * <p>This is deliberately a small parser for the token boundaries and the two
 * mapping operators that allocate entries. Unicode keys include source length;
 * the pinned backend conflates some three-byte and four-byte codes.</p>
 */
final class PdfBoxCMapPreflight {

    private final TokenReader tokens;
    private final int maximumMappings;
    private final int maximumFontDataEntries;
    private final WorkflowResourceContext resources;
    private final CMapProgram program;
    private final UnicodeMappings unicode;
    private final EncodingMappings encoding;
    private final boolean predefined;
    private final List<WorkflowResourceContext.MemoryReservation>
            memoryReservations =
                    new ArrayList<WorkflowResourceContext.MemoryReservation>();
    private int mappings;
    private int fontDataEntries;
    private boolean hasCodespaces;

    private PdfBoxCMapPreflight(
            byte[] bytes,
            int maximumMappings,
            int maximumFontDataEntries,
            WorkflowResourceContext resources,
            CMapProgram program,
            boolean predefined) {
        this.tokens = new TokenReader(bytes, this);
        this.maximumMappings = maximumMappings;
        this.maximumFontDataEntries = maximumFontDataEntries;
        this.resources = resources;
        this.program = program;
        this.unicode = program instanceof UnicodeMappings ? (UnicodeMappings) program : null;
        this.encoding = program instanceof EncodingMappings ? (EncodingMappings) program : null;
        this.predefined = predefined;
    }

    static int countMappings(byte[] bytes, int maximumMappings)
            throws IOException, LimitExceededException {
        return countMappings(bytes, maximumMappings, null);
    }

    static int countMappings(
            byte[] bytes,
            int maximumMappings,
            WorkflowResourceContext resources)
            throws IOException, LimitExceededException {
        return inspectToUnicode(bytes, maximumMappings, Integer.MAX_VALUE, resources).mappings;
    }

    static Inspection inspectToUnicode(byte[] bytes, int maximumMappings, int maximumFontDataEntries,
            WorkflowResourceContext resources) throws IOException, LimitExceededException {
        return inspectCMap(bytes, maximumMappings, maximumFontDataEntries, resources, null, false);
    }

    static Inspection parseToUnicode(byte[] bytes, int maximumMappings, int maximumFontDataEntries,
            WorkflowResourceContext resources) throws IOException, LimitExceededException {
        return parseToUnicode(bytes, maximumMappings, maximumFontDataEntries, resources, false);
    }

    static Inspection parseEncoding(byte[] bytes, int maximumFontDataEntries,
            WorkflowResourceContext resources) throws IOException {
        EncodingMappings encoding = new EncodingMappings(resources);
        try {
            return inspectCMap(bytes, 0, maximumFontDataEntries, resources, encoding, false);
        } catch (IOException | RuntimeException | Error failure) {
            encoding.close();
            throw failure;
        }
    }

    static Inspection parseToUnicode(byte[] bytes, int maximumMappings, int maximumFontDataEntries,
            WorkflowResourceContext resources, boolean predefined) throws IOException, LimitExceededException {
        UnicodeMappings unicode = new UnicodeMappings(resources);
        try {
            return inspectCMap(bytes, maximumMappings, maximumFontDataEntries, resources, unicode, predefined);
        } catch (IOException | RuntimeException | Error failure) {
            unicode.close();
            throw failure;
        }
    }

    private static Inspection inspectCMap(byte[] bytes, int maximumMappings, int maximumFontDataEntries,
            WorkflowResourceContext resources, CMapProgram program, boolean predefined)
            throws IOException, LimitExceededException {
        if (bytes == null) {
            throw new NullPointerException("bytes");
        }
        if (maximumMappings < 0 || maximumFontDataEntries < 0) {
            throw new IllegalArgumentException(
                    "CMap entry limits must not be negative");
        }
        PdfBoxCMapPreflight preflight =
                new PdfBoxCMapPreflight(
                        bytes, maximumMappings, maximumFontDataEntries, resources, program, predefined);
        try {
            preflight.validate();
            return new Inspection(preflight.mappings, preflight.fontDataEntries, preflight.hasCodespaces, preflight.unicode, preflight.encoding);
        } finally {
            preflight.releaseMemory();
        }
    }

    private void validate() throws IOException, LimitExceededException {
        Token previous = null;
        Token token;
        boolean begun = false;
        boolean mappingOperation = false;
        boolean charactersMapped = false;
        while ((token = tokens.nextOutsideMapping()) != null) {
            checkpoint();
            if (token.isWord("begincmap")) {
                if (begun) {
                    throw new IOException("Nested CMap program");
                }
                begun = true;
            }
            if (token.isWord("endcmap")) {
                if (program != null && !begun) {
                    throw new IOException("CMap program has no beginning");
                }
                return;
            }
            if (token.isWord("usecmap")) {
                if (program == null || !begun || mappingOperation
                        || previous == null || previous.kind != Token.NAME
                        || program.parentName != null) {
                    throw new IOException("Invalid ToUnicode usecmap declaration");
                }
                program.parentName = program.retainName(previous);
            }
            if (program != null && token.isName("CMapName")) {
                Token name = requiredToken("CMapName");
                if (name.kind != Token.NAME || program.name != null) {
                    throw new IOException("Malformed CMapName");
                }
                program.name = program.retainName(name);
                requireTerminator("def");
                previous = null;
                continue;
            }
            if (program != null && token.isName("WMode")) {
                Token mode = requiredToken("WMode");
                if (!mode.isInteger() || mode.number < 0 || mode.number > 1 || program.hasWritingMode) {
                    throw new IOException("Malformed CMap WMode");
                }
                program.writingMode = mode.number;
                program.hasWritingMode = true;
                requireTerminator("def");
                previous = null;
                continue;
            }
            if (encoding != null && token.isName("CMapType")) {
                Token type = requiredToken("CMapType");
                if (!type.isInteger() || type.number != 1 || program.cmapType != null) {
                    throw new IOException("Encoding CMapType is not 1");
                }
                program.cmapType = Integer.valueOf(type.number);
                requireTerminator("def");
                previous = null;
                continue;
            }
            if (program != null && token.isName("CIDSystemInfo")) {
                readCharacterCollection();
                previous = null;
                continue;
            }
            if (token.isWord("beginbfchar") || token.isWord("beginbfrange")
                    || token.isWord("begincodespacerange")) {
                if (previous == null || previous.kind != Token.NUMBER
                        || (program != null && !begun) || (encoding != null && !previous.isInteger())) {
                    throw new IOException("CMap mapping declaration has no count or program");
                }
                if (program != null && token.isWord("begincodespacerange") && charactersMapped) {
                    throw new IOException("Codespace follows character mappings");
                }
                charactersMapped |= !token.isWord("begincodespacerange");
                mappingOperation = true;
                if (encoding != null && !token.isWord("begincodespacerange")) {
                    throw new IOException("Unicode mapping operator in Encoding CMap");
                }
                if (token.isWord("beginbfchar")) {
                    validateCharacters(previous.number);
                } else if (token.isWord("beginbfrange")) {
                    validateRanges(previous.number);
                } else if (token.isWord("begincodespacerange")) {
                    validateCodespaces(previous.number);
                }
            } else if (encoding != null && (token.isWord("begincidchar") || token.isWord("begincidrange")
                    || token.isWord("beginnotdefchar") || token.isWord("beginnotdefrange"))) {
                if (!begun || previous == null || !previous.isInteger() || previous.number < 0) {
                    throw new IOException("Encoding mapping has no count or program");
                }
                mappingOperation = true;
                charactersMapped = true;
                boolean range = token.isWord("begincidrange") || token.isWord("beginnotdefrange");
                boolean notdef = token.isWord("beginnotdefchar") || token.isWord("beginnotdefrange");
                validateCidMappings(previous.number, range, notdef);
            } else if (encoding != null && token.isWord("usefont")) {
                if (!begun || previous == null || !previous.isInteger() || previous.number != 0) {
                    throw new IOException("Encoding CMap font number is not zero");
                }
            } else if (token.isWord("endbfchar") || token.isWord("endbfrange")
                    || token.isWord("endcodespacerange") || token.isWord("begincidchar")
                    || token.isWord("begincidrange") || token.isWord("beginnotdefchar")
                    || token.isWord("beginnotdefrange") || token.isWord("endcidchar")
                    || token.isWord("endcidrange") || token.isWord("endnotdefchar")
                    || token.isWord("endnotdefrange") || token.isWord("beginrearrangedfont")
                    || token.isWord("endrearrangedfont") || token.isWord("beginusematrix")
                    || token.isWord("endusematrix")) {
                throw new IOException("Invalid ToUnicode mapping operator");
            }
            previous = token;
        }
        if (program != null) {
            throw new IOException("Unterminated CMap program");
        }
    }

    private void validateCidMappings(int count, boolean range, boolean notdef) throws IOException {
        if (count > maximumFontDataEntries - fontDataEntries) {
            throw new LimitExceededException();
        }
        for (int index = 0; index < count; index++) {
            checkpoint();
            Token start = requiredToken("CID source");
            requireSourceCode(start, "CID source");
            Token end = range ? requiredToken("CID range end") : start;
            requireSourceCode(end, "CID range end");
            long first = fontBoxCode(start.bytes) & 0xffffffffL;
            long last = fontBoxCode(end.bytes) & 0xffffffffL;
            if (start.bytes.length != end.bytes.length || last < first) {
                throw new IOException("Malformed CID range");
            }
            long cardinality = last - first + 1;
            if (cardinality > maximumFontDataEntries - fontDataEntries) {
                throw new LimitExceededException();
            }
            fontDataEntries += (int) cardinality;
            Token cid = requiredToken("CID selector");
            if (!cid.isInteger() || cid.number < 0 || cid.number > 65535
                    || (!notdef && cid.number + cardinality - 1 > 65535)) {
                throw new IOException("CID selector is outside its range");
            }
            encoding.ranges.add(new CidRange(start.bytes.length, first, last, cid.number, notdef));
        }
        requireTerminator(notdef ? (range ? "endnotdefrange" : "endnotdefchar")
                : (range ? "endcidrange" : "endcidchar"));
    }

    private void readCharacterCollection() throws IOException {
        if (program.registry != null) {
            throw new IOException("Duplicate CMap CIDSystemInfo");
        }
        tokens.readingMetadata = true;
        try {
            Token start = requiredToken("CIDSystemInfo");
            boolean literal = start.kind == Token.DICTIONARY_START;
            if (!literal) {
                if (!start.isInteger() || start.number < 3) {
                    throw new IOException("Malformed CMap CIDSystemInfo");
                }
                requireTerminator("dict");
                requireTerminator("dup");
                requireTerminator("begin");
            }
            while (true) {
                Token key = requiredToken("CIDSystemInfo entry");
                if (literal ? key.kind == Token.DICTIONARY_END : key.isWord("end")) {
                    break;
                }
                Token value = requiredToken("CIDSystemInfo value");
                if (key.isName("Registry") && program.registry == null) {
                    program.registry = program.retainString(value);
                } else if (key.isName("Ordering") && program.ordering == null) {
                    program.ordering = program.retainString(value);
                } else if (key.isName("Supplement") && program.supplement == null
                        && value.isInteger() && value.number >= 0) {
                    program.supplement = Integer.valueOf(value.number);
                } else {
                    throw new IOException("Invalid or unsupported CMap CIDSystemInfo entry");
                }
                if (!literal) {
                    requireTerminator("def");
                }
            }
            if (program.registry == null || program.ordering == null || program.supplement == null) {
                throw new IOException("Incomplete CMap CIDSystemInfo");
            }
            requireTerminator("def");
        } finally {
            tokens.readingMetadata = false;
        }
    }

    private void validateCodespaces(int count) throws IOException, LimitExceededException {
        if (count < 0) {
            throw new IOException("codespacerange count is negative");
        }
        if (count > maximumFontDataEntries - fontDataEntries) {
            throw new LimitExceededException();
        }
        for (int index = 0; index < count; index++) {
            checkpoint();
            fontDataEntries++;
            hasCodespaces = true;
            Token start = requiredToken("codespacerange start");
            Token end = requiredToken("codespacerange end");
            requireSourceCode(start, "codespacerange start");
            requireSourceCode(end, "codespacerange end");
            if (start.bytes.length != end.bytes.length) {
                throw new IOException("Codespace endpoints have different widths");
            }
            for (int offset = 0; offset < start.bytes.length; offset++) {
                if ((start.bytes[offset] & 0xff) > (end.bytes[offset] & 0xff)) {
                    throw new IOException("Codespace byte interval is reversed");
                }
            }
            if (program != null) {
                program.codespaces.add(new Codespace(start.bytes.length,
                        fontBoxCode(start.bytes) & 0xffffffffL, fontBoxCode(end.bytes) & 0xffffffffL));
            }
        }
        requireTerminator("endcodespacerange");
    }

    private void validateCharacters(int count)
            throws IOException, LimitExceededException {
        requireDeclaredCount(count, "bfchar");
        for (int index = 0; index < count; index++) {
            checkpoint();
            Token source = requiredToken("bfchar source");
            if (source.isWord("endbfchar")) {
                throw new IOException("bfchar ended before its declared count");
            }
            requireSourceCode(source, "bfchar source");
            Token target = requiredToken("bfchar target");
            if (target.kind != Token.HEX) {
                throw new IOException("Invalid bfchar target");
            }
            requireUnicodeDestination(target.bytes);
            account(1L);
            if (unicode != null) {
                unicode.put(source.bytes, target.bytes);
            }
        }
        requireTerminator("endbfchar");
    }

    private void validateRanges(int count)
            throws IOException, LimitExceededException {
        requireDeclaredCount(count, "bfrange");
        for (int index = 0; index < count; index++) {
            checkpoint();
            Token sourceStart = requiredToken("bfrange start");
            if (sourceStart.isWord("endbfrange")) {
                throw new IOException(
                        "bfrange ended before its declared count");
            }
            requireSourceCode(sourceStart, "bfrange start");
            Token sourceEnd = requiredToken("bfrange end");
            if (sourceEnd.isWord("endbfrange")) {
                throw new IOException(
                        "bfrange ended before its declared count");
            }
            requireSourceCode(sourceEnd, "bfrange end");
            if (sourceStart.bytes.length != sourceEnd.bytes.length) {
                throw new IOException(
                        "ToUnicode range endpoints have different widths");
            }
            long start = unicode == null ? fontBoxCode(sourceStart.bytes) : fontBoxCode(sourceStart.bytes) & 0xffffffffL;
            long end = unicode == null ? fontBoxCode(sourceEnd.bytes) : fontBoxCode(sourceEnd.bytes) & 0xffffffffL;
            if (end < start) {
                throw new IOException("ToUnicode range is reversed");
            }
            long rangeSize = (long) end - (long) start + 1L;
            Token target = requiredToken("bfrange target");
            if (target.kind == Token.HEX) {
                accountScalarRange(
                        rangeSize,
                        sourceStart.bytes,
                        target.bytes);
            } else if (target.kind == Token.ARRAY) {
                if (target.elements.size() != rangeSize) {
                    throw new IOException(
                            "ToUnicode range array has the wrong size");
                }
                for (Token element : target.elements) {
                    checkpoint();
                    if (element.kind != Token.HEX) {
                        throw new IOException(
                                "ToUnicode range array is malformed");
                    }
                    requireUnicodeDestination(element.bytes);
                }
                account(target.elements.size());
                if (unicode != null) {
                    reserveScratch(sourceStart.bytes.length);
                    byte[] source = Arrays.copyOf(sourceStart.bytes, sourceStart.bytes.length);
                    for (Token element : target.elements) {
                        checkpoint();
                        unicode.put(source, element.bytes);
                        incrementLikeEmbeddedFontCMap(source);
                    }
                }
            } else {
                throw new IOException("Invalid bfrange target");
            }
        }
        requireTerminator("endbfrange");
    }

    private void accountScalarRange(
            long rangeSize,
            byte[] initialSource,
            byte[] initialTarget)
            throws IOException, LimitExceededException {
        account(rangeSize);
        reserveScratch(initialTarget.length);
        byte[] target = Arrays.copyOf(initialTarget, initialTarget.length);
        reserveScratch(initialSource.length);
        byte[] source = Arrays.copyOf(initialSource, initialSource.length);
        requireUnicodeDestination(initialTarget);
        // Inline PDF destinations increment only their final byte. The pinned
        // predefined Adobe resources use full carrying string increments.
        // Retain the existing conservative full-range construction charge.
        long retained = predefined ? rangeSize : 256 - (initialTarget[initialTarget.length - 1] & 0xff);
        for (long index = 0L; index < rangeSize; index++) {
            checkpoint();
            requireUnicodeDestination(target);
            if (unicode != null) {
                if (index < retained) {
                    unicode.put(source, target);
                } else {
                    unicode.mask(source);
                }
                incrementLikeEmbeddedFontCMap(source);
            }
            if (index + 1L < rangeSize) {
                incrementLikeEmbeddedFontCMap(target);
            }
        }
    }

    private void incrementLikeEmbeddedFontCMap(byte[] value)
            throws IOException {
        for (int position = value.length - 1; position >= 0; position--) {
            checkpointPeriodically(value.length - 1L - position);
            if (position > 0 && (value[position] & 0xff) == 0xff) {
                value[position] = 0;
            } else {
                value[position] = (byte) (value[position] + 1);
                return;
            }
        }
    }

    private void requireUnicodeDestination(byte[] bytes)
            throws IOException {
        if (bytes.length == 0 || (bytes.length & 1) != 0) {
            throw new IOException(
                    "ToUnicode destination is not nonempty UTF-16BE");
        }
        for (int index = 0; index < bytes.length; index += 2) {
            checkpointPeriodically(index);
            int unit = ((bytes[index] & 0xff) << 8)
                    | (bytes[index + 1] & 0xff);
            if (unit >= 0xd800 && unit <= 0xdbff) {
                if (index + 3 >= bytes.length) {
                    throw new IOException(
                            "ToUnicode destination has an unpaired surrogate");
                }
                int next = ((bytes[index + 2] & 0xff) << 8)
                        | (bytes[index + 3] & 0xff);
                if (next < 0xdc00 || next > 0xdfff) {
                    throw new IOException(
                            "ToUnicode destination has an unpaired surrogate");
                }
                index += 2;
            } else if (unit >= 0xdc00 && unit <= 0xdfff) {
                throw new IOException(
                        "ToUnicode destination has an unpaired surrogate");
            }
        }
    }

    private void checkpoint() throws IOException {
        if (resources != null) {
            resources.checkpointAsIOException();
        }
    }

    private void checkpointPeriodically(long progress) throws IOException {
        if ((progress & 1023L) == 0L) {
            checkpoint();
        }
    }

    private void reserveScratch(int length) throws IOException {
        if (resources != null) {
            memoryReservations.add(
                    resources.reserveOwnedMemoryAsIOException(length));
        }
    }

    private WorkflowResourceContext.MemoryReservation reserveWorkingCharacters(
            int length) throws IOException {
        if (resources == null) {
            return null;
        }
        return resources.reserveOwnedMemoryAsIOException(2L * length);
    }

    private void releaseMemory() {
        for (int index = memoryReservations.size() - 1;
                index >= 0;
                index--) {
            memoryReservations.get(index).close();
        }
        memoryReservations.clear();
    }

    private Token requiredToken(String description)
            throws IOException, LimitExceededException {
        Token token = tokens.next();
        if (token == null) {
            throw new IOException("Missing " + description);
        }
        return token;
    }

    private void requireTerminator(String expected)
            throws IOException, LimitExceededException {
        Token token = requiredToken(expected);
        if (!token.isWord(expected)) {
            throw new IOException("Missing " + expected);
        }
    }

    private void requireDeclaredCount(int count, String operator)
            throws IOException, LimitExceededException {
        if (count < 0) {
            throw new IOException(operator + " count is negative");
        }
        requireCapacity(count);
    }

    private void account(long count) throws LimitExceededException {
        requireCapacity(count);
        mappings += (int) count;
    }

    private void requireCapacity(long count) throws LimitExceededException {
        if (count < 0L || count > maximumMappings - (long) mappings) {
            throw new LimitExceededException();
        }
    }

    private static void requireSourceCode(Token token, String description)
            throws IOException {
        if (token.kind != Token.HEX
                || token.bytes.length < 1
                || token.bytes.length > 4) {
            throw new IOException("Invalid " + description);
        }
    }

    private static int fontBoxCode(byte[] bytes) {
        int value = 0;
        for (byte current : bytes) {
            value = (value << 8) | (current & 0xff);
        }
        return value;
    }

    private static final class TokenReader {

        private final byte[] bytes;
        private final PdfBoxCMapPreflight preflight;
        private int position;
        private boolean readingArray;
        private boolean readingMetadata;
        private boolean skippingArrayValues;

        TokenReader(byte[] bytes, PdfBoxCMapPreflight preflight) {
            this.bytes = bytes;
            this.preflight = preflight;
        }

        Token nextOutsideMapping() throws IOException {
            skippingArrayValues = true;
            try {
                return next();
            } finally {
                skippingArrayValues = false;
            }
        }

        Token next() throws IOException, LimitExceededException {
            preflight.checkpoint();
            skipWhitespaceAndComments();
            if (position >= bytes.length) {
                return null;
            }
            int current = unsigned(bytes[position++]);
            if (current == '<') {
                if (position < bytes.length
                        && unsigned(bytes[position]) == '<') {
                    position++;
                    if (readingMetadata) {
                        return Token.dictionary(true);
                    }
                    skipDictionary();
                    return Token.other();
                }
                return Token.hex(readHexadecimal());
            }
            if (current == '[') {
                if (readingArray) {
                    throw new IOException("Nested CMap arrays are unsupported");
                }
                return readArray();
            }
            if (current == ']') {
                return Token.endArray();
            }
            if (current == '(') {
                if (readingMetadata) {
                    return Token.string(readLiteralString());
                }
                skipLiteralString();
                return Token.other();
            }
            if (current == '/') {
                int start = position;
                skipName();
                if (position - start > 512) {
                    throw new IOException("CMap name exceeds backend policy");
                }
                return Token.name(bytes, start, position);
            }
            if (isNumberStart(current)) {
                return readNumber(current);
            }
            if (current == '>') {
                if (readingMetadata && position < bytes.length && unsigned(bytes[position]) == '>') {
                    position++;
                    return Token.dictionary(false);
                }
                throw new IOException("Unexpected CMap dictionary end");
            }
            return readWord();
        }

        private Token readArray()
                throws IOException, LimitExceededException {
            List<Token> values = skippingArrayValues ? null : new ArrayList<Token>();
            readingArray = true;
            try {
                while (true) {
                    Token value = next();
                    if (value == null) {
                        throw new IOException("Unterminated CMap array");
                    }
                    if (value.kind == Token.END_ARRAY) {
                        return values == null ? Token.other() : Token.array(values);
                    }
                    if (values != null) {
                        preflight.requireCapacity(values.size() + 1L);
                        values.add(value);
                    }
                }
            } finally {
                readingArray = false;
            }
        }

        private Token readNumber(int first) throws IOException {
            int start = position - 1;
            while (position < bytes.length) {
                preflight.checkpointPeriodically(position);
                int current = unsigned(bytes[position]);
                if ((current >= '0' && current <= '9') || current == '.') {
                    position++;
                } else {
                    break;
                }
            }
            if (position < bytes.length) {
                int next = unsigned(bytes[position]);
                if (!isWhitespace(next) && !isDelimiter(next)) {
                    throw new IOException("Invalid CMap number");
                }
            }
            WorkflowResourceContext.MemoryReservation reservation =
                    preflight.reserveWorkingCharacters(position - start);
            try {
                preflight.checkpoint();
                String value = new String(
                        bytes,
                        start,
                        position - start,
                        StandardCharsets.ISO_8859_1);
                preflight.checkpoint();
                if (value.indexOf('.') >= 0) {
                    return Token.number(Double.valueOf(value).intValue(), bytes, start, position);
                }
                return Token.number(Integer.parseInt(value), bytes, start, position);
            } catch (NumberFormatException malformed) {
                throw new IOException("Invalid CMap number");
            } finally {
                if (reservation != null) {
                    reservation.close();
                }
            }
        }

        private void skipName() throws IOException {
            while (position < bytes.length
                    && !isWhitespace(unsigned(bytes[position]))
                    && !isDelimiter(unsigned(bytes[position]))) {
                preflight.checkpointPeriodically(position);
                position++;
            }
        }

        private Token readWord() throws IOException {
            int start = position - 1;
            while (position < bytes.length) {
                preflight.checkpointPeriodically(position);
                int current = unsigned(bytes[position]);
                if (isWhitespace(current)
                        || isDelimiter(current)) {
                    break;
                }
                position++;
            }
            return Token.word(bytes, start, position);
        }

        private boolean isNumberStart(int first) {
            if (first >= '0' && first <= '9') {
                return true;
            }
            if (first != '+' && first != '-' && first != '.') {
                return false;
            }
            if (position >= bytes.length) {
                return false;
            }
            int next = unsigned(bytes[position]);
            return (next >= '0' && next <= '9')
                    || ((first == '+' || first == '-') && next == '.');
        }

        private byte[] readHexadecimal() throws IOException {
            int contentStart = position;
            int scan = position;
            int digits = 0;
            while (scan < bytes.length && unsigned(bytes[scan]) != '>') {
                preflight.checkpointPeriodically(scan);
                int current = unsigned(bytes[scan]);
                if (!isWhitespace(current)) {
                    if (hexadecimal(current) < 0) {
                        throw new IOException(
                                "Invalid CMap hexadecimal value");
                    }
                    digits++;
                }
                scan++;
            }
            if (scan >= bytes.length) {
                throw new IOException(
                        "Unterminated CMap hexadecimal value");
            }
            int length = (digits + 1) / 2;
            if (length > 512) {
                throw new IOException("CMap token exceeds backend policy");
            }
            preflight.reserveScratch(length);
            byte[] result = new byte[length];
            int nibble = 0;
            for (int index = contentStart; index < scan; index++) {
                preflight.checkpointPeriodically(index);
                int value = hexadecimal(unsigned(bytes[index]));
                if (value < 0) {
                    continue;
                }
                if ((nibble & 1) == 0) {
                    result[nibble / 2] = (byte) (value << 4);
                } else {
                    result[nibble / 2] |= (byte) value;
                }
                nibble++;
            }
            position = scan + 1;
            return result;
        }

        private void skipDictionary() throws IOException {
            int arrayDepth = 0;
            while (position < bytes.length) {
                preflight.checkpointPeriodically(position);
                int current = unsigned(bytes[position++]);
                if (current == '%') {
                    skipComment();
                } else if (current == '(') {
                    skipLiteralString();
                } else if (current == '<') {
                    if (position < bytes.length
                            && unsigned(bytes[position]) == '<') {
                        throw new IOException(
                                "Nested CMap dictionaries are unsupported");
                    }
                    readHexadecimal();
                } else if (current == '[') {
                    if (arrayDepth != 0) {
                        throw new IOException(
                                "Nested CMap arrays are unsupported");
                    }
                    arrayDepth = 1;
                } else if (current == ']') {
                    if (arrayDepth == 0) {
                        throw new IOException("Unexpected CMap array end");
                    }
                    arrayDepth = 0;
                } else if (current == '>'
                        && position < bytes.length
                        && unsigned(bytes[position]) == '>') {
                    position++;
                    if (arrayDepth != 0) {
                        throw new IOException("Unterminated CMap array");
                    }
                    return;
                }
            }
            throw new IOException("Unterminated CMap dictionary");
        }

        private void skipLiteralString() throws IOException {
            readLiteralString(false);
        }

        private byte[] readLiteralString() throws IOException {
            return readLiteralString(true);
        }

        private byte[] readLiteralString(boolean retain) throws IOException {
            if (retain) {
                preflight.reserveScratch(512);
            }
            byte[] result = retain ? new byte[512] : null;
            int length = 0;
            int depth = 1;
            while (position < bytes.length) {
                preflight.checkpointPeriodically(position);
                int current = unsigned(bytes[position++]);
                if (current == '\\') {
                    if (position >= bytes.length) {
                        break;
                    }
                    current = unsigned(bytes[position++]);
                    if (current == '\r' || current == '\n') {
                        if (current == '\r' && position < bytes.length && unsigned(bytes[position]) == '\n') {
                            position++;
                        }
                        continue;
                    }
                    if (current >= '0' && current <= '7') {
                        int octal = current - '0';
                        for (int digit = 1; digit < 3 && position < bytes.length; digit++) {
                            int next = unsigned(bytes[position]);
                            if (next < '0' || next > '7') {
                                break;
                            }
                            position++;
                            octal = (octal << 3) | (next - '0');
                        }
                        current = octal & 0xff;
                    } else if (current == 'n') {
                        current = '\n';
                    } else if (current == 'r') {
                        current = '\r';
                    } else if (current == 't') {
                        current = '\t';
                    } else if (current == 'b') {
                        current = '\b';
                    } else if (current == 'f') {
                        current = '\f';
                    }
                } else if (current == '(') {
                    depth++;
                } else if (current == ')') {
                    if (--depth == 0) {
                        if (retain) {
                            preflight.reserveScratch(length);
                            return Arrays.copyOf(result, length);
                        }
                        return null;
                    }
                } else if (current == '\r') {
                    if (position < bytes.length && unsigned(bytes[position]) == '\n') {
                        position++;
                    }
                    current = '\n';
                }
                if (retain) {
                    if (length == result.length) {
                        throw new IOException("CMap token exceeds backend policy");
                    }
                    result[length++] = (byte) current;
                }
            }
            throw new IOException("Unterminated CMap string");
        }

        private void skipWhitespaceAndComments() throws IOException {
            while (position < bytes.length) {
                preflight.checkpointPeriodically(position);
                int current = unsigned(bytes[position]);
                if (isWhitespace(current)) {
                    position++;
                } else if (current == '%') {
                    position++;
                    skipComment();
                } else {
                    return;
                }
            }
        }

        private void skipComment() throws IOException {
            while (position < bytes.length) {
                preflight.checkpointPeriodically(position);
                int current = unsigned(bytes[position++]);
                if (current == '\r' || current == '\n') {
                    return;
                }
            }
        }

        private static boolean isWhitespace(int value) {
            return value == 0 || value == 9 || value == 10
                    || value == 12 || value == 13 || value == 32;
        }

        private static boolean isDelimiter(int value) {
            return value == '(' || value == ')' || value == '<'
                    || value == '>' || value == '[' || value == ']'
                    || value == '{' || value == '}' || value == '/'
                    || value == '%';
        }

        private static int hexadecimal(int value) {
            if (value >= '0' && value <= '9') {
                return value - '0';
            }
            if (value >= 'A' && value <= 'F') {
                return value - 'A' + 10;
            }
            if (value >= 'a' && value <= 'f') {
                return value - 'a' + 10;
            }
            return -1;
        }

        private static int unsigned(byte value) {
            return value & 0xff;
        }
    }

    private static final class Token {

        private static final int OTHER = 0;
        private static final int NUMBER = 1;
        private static final int HEX = 2;
        private static final int NAME = 3;
        private static final int WORD = 4;
        private static final int ARRAY = 5;
        private static final int END_ARRAY = 6;
        private static final int DICTIONARY_START = 7;
        private static final int DICTIONARY_END = 8;
        private static final int STRING = 9;

        private final int kind;
        private final int number;
        private final byte[] bytes;
        private final List<Token> elements;
        private final byte[] wordSource;
        private final int wordStart;
        private final int wordEnd;

        private Token(
                int kind,
                int number,
                byte[] bytes,
                List<Token> elements,
                byte[] wordSource,
                int wordStart,
                int wordEnd) {
            this.kind = kind;
            this.number = number;
            this.bytes = bytes;
            this.elements = elements;
            this.wordSource = wordSource;
            this.wordStart = wordStart;
            this.wordEnd = wordEnd;
        }

        boolean isWord(String value) {
            return matches(WORD, value);
        }

        boolean isName(String value) {
            return matches(NAME, value);
        }

        boolean isInteger() {
            if (kind != NUMBER) {
                return false;
            }
            for (int index = wordStart; index < wordEnd; index++) {
                if (wordSource[index] == '.') {
                    return false;
                }
            }
            return true;
        }

        private boolean matches(int expectedKind, String value) {
            if (kind != expectedKind || wordEnd - wordStart != value.length()) {
                return false;
            }
            for (int index = 0; index < value.length(); index++) {
                if ((wordSource[wordStart + index] & 0xff)
                        != value.charAt(index)) {
                    return false;
                }
            }
            return true;
        }

        static Token other() {
            return new Token(OTHER, 0, null, null, null, 0, 0);
        }

        static Token number(int value, byte[] source, int start, int end) {
            return new Token(NUMBER, value, null, null, source, start, end);
        }

        static Token hex(byte[] value) {
            return new Token(HEX, 0, value, null, null, 0, 0);
        }

        static Token name(byte[] source, int start, int end) {
            return new Token(NAME, 0, null, null, source, start, end);
        }

        static Token word(byte[] source, int start, int end) {
            return new Token(WORD, 0, null, null, source, start, end);
        }

        static Token array(List<Token> value) {
            return new Token(ARRAY, 0, null, value, null, 0, 0);
        }

        static Token endArray() {
            return new Token(END_ARRAY, 0, null, null, null, 0, 0);
        }

        static Token dictionary(boolean start) {
            return new Token(start ? DICTIONARY_START : DICTIONARY_END, 0, null, null, null, 0, 0);
        }

        static Token string(byte[] value) {
            return new Token(STRING, 0, value, null, null, 0, 0);
        }
    }

    static final class Inspection {

        final int mappings;
        final int fontDataEntries;
        final boolean hasCodespaces;
        final UnicodeMappings unicode;
        final EncodingMappings encoding;

        Inspection(int mappings, int fontDataEntries, boolean hasCodespaces, UnicodeMappings unicode,
                EncodingMappings encoding) {
            this.mappings = mappings;
            this.fontDataEntries = fontDataEntries;
            this.hasCodespaces = hasCodespaces;
            this.unicode = unicode;
            this.encoding = encoding;
        }
    }

    static final class EncodingMappings extends CMapProgram {

        private final List<CidRange> ranges = new ArrayList<CidRange>();

        EncodingMappings(WorkflowResourceContext resources) {
            super(resources);
        }

        @Override
        void validateMappings(List<Codespace> adopted) throws IOException {
            for (CidRange range : ranges) {
                for (long code = range.first; code <= range.last; code++) {
                    requireCodespace(adopted, range.length, code);
                }
            }
        }

        Integer lookup(int length, long code, boolean notdef) throws IOException {
            for (int index = ranges.size() - 1; index >= 0; index--) {
                resources.checkpointAsIOException();
                CidRange range = ranges.get(index);
                if (range.notdef == notdef && range.length == length && code >= range.first && code <= range.last) {
                    return Integer.valueOf(range.cid + (notdef ? 0 : (int) (code - range.first)));
                }
            }
            return null;
        }

        @Override
        public void close() {
            super.close();
            ranges.clear();
        }
    }

    static List<Codespace> validateGraph(List<? extends CMapProgram> layers,
            WorkflowResourceContext resources) throws IOException {
        List<Codespace> codespaces = layers.get(layers.size() - 1).codespaces;
        for (int index = 0; index < codespaces.size(); index++) {
            for (int other = 0; other < index; other++) {
                resources.checkpointAsIOException();
                if (codespaces.get(index).overlaps(codespaces.get(other))) {
                    throw new IOException("CMap codespaces overlap");
                }
            }
        }
        for (CMapProgram layer : layers) {
            layer.validateMappings(codespaces);
        }
        return codespaces;
    }

    static final class Codespace {
        final int length;
        final long first;
        final long last;

        Codespace(int length, long first, long last) {
            this.length = length;
            this.first = first;
            this.last = last;
        }

        boolean matchesPrefix(int prefixLength, long code) {
            if (prefixLength > length) {
                return false;
            }
            for (int index = 0; index < prefixLength; index++) {
                int value = (int) ((code >>> (8 * (prefixLength - index - 1))) & 0xff);
                int shift = 8 * (length - index - 1);
                if (value < ((first >>> shift) & 0xff) || value > ((last >>> shift) & 0xff)) {
                    return false;
                }
            }
            return true;
        }

        boolean overlaps(Codespace other) {
            for (int index = 0; index < Math.min(length, other.length); index++) {
                int shift = 8 * (length - index - 1);
                int otherShift = 8 * (other.length - index - 1);
                if (((first >>> shift) & 0xff) > ((other.last >>> otherShift) & 0xff)
                        || ((other.first >>> otherShift) & 0xff) > ((last >>> shift) & 0xff)) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final class CidRange {
        private final int length;
        private final long first;
        private final long last;
        private final int cid;
        private final boolean notdef;

        CidRange(int length, long first, long last, int cid, boolean notdef) {
            this.length = length;
            this.first = first;
            this.last = last;
            this.cid = cid;
            this.notdef = notdef;
        }
    }

    abstract static class CMapProgram implements AutoCloseable {

        final WorkflowResourceContext resources;
        final List<Codespace> codespaces = new ArrayList<Codespace>();
        private long metadataBytes;
        String name;
        String parentName;
        int writingMode;
        boolean hasWritingMode;
        Integer cmapType;
        String registry;
        String ordering;
        Integer supplement;

        CMapProgram(WorkflowResourceContext resources) {
            this.resources = resources;
        }

        abstract void validateMappings(List<Codespace> adopted) throws IOException;

        final void requireCodespace(List<Codespace> adopted, int length, long code) throws IOException {
            for (Codespace codespace : adopted) {
                resources.checkpointAsIOException();
                if (codespace.length == length && codespace.matchesPrefix(length, code)) {
                    return;
                }
            }
            throw new IOException("CMap mapping is outside its adopted codespace");
        }

        private String retainName(Token token) throws IOException {
            int length = token.wordEnd - token.wordStart;
            if (resources != null) {
                resources.retainOwnedMemoryAsIOException(2L * length);
            }
            metadataBytes += 2L * length;
            return new String(token.wordSource, token.wordStart, length, StandardCharsets.ISO_8859_1);
        }

        private String retainString(Token token) throws IOException {
            if (token.kind != Token.STRING && token.kind != Token.HEX) {
                throw new IOException("CMap CIDSystemInfo value is not a string");
            }
            if (resources != null) {
                resources.retainOwnedMemoryAsIOException(2L * token.bytes.length);
            }
            metadataBytes += 2L * token.bytes.length;
            return new String(token.bytes, StandardCharsets.ISO_8859_1);
        }

        @Override
        public void close() {
            if (resources != null) {
                resources.releaseRetainedOwnedMemory(metadataBytes);
            }
            metadataBytes = 0L;
            name = null;
            parentName = null;
            registry = null;
            ordering = null;
            supplement = null;
            codespaces.clear();
        }
    }

    static final class UnicodeMappings extends CMapProgram {

        private final Map<Long, String> values = new HashMap<Long, String>();
        private final Set<Long> observed = new HashSet<Long>();
        private long textBytes;
        private long observedTextBytes;

        UnicodeMappings(WorkflowResourceContext resources) {
            super(resources);
        }

        @Override
        void validateMappings(List<Codespace> adopted) throws IOException {
            for (Long key : values.keySet()) {
                requireCodespace(adopted, (int) (key.longValue() >>> 32), key.longValue() & 0xffffffffL);
            }
        }

        void put(byte[] source, byte[] target) throws IOException {
            if (resources != null) {
                resources.retainOwnedMemoryAsIOException(target.length);
            }
            textBytes += target.length;
            String previous = values.put(key(source), new String(target, StandardCharsets.UTF_16BE));
            if (previous != null) {
                long released = 2L * previous.length();
                textBytes -= released;
                if (resources != null) {
                    resources.releaseRetainedOwnedMemory(released);
                }
            }
        }

        String lookup(byte[] source) {
            Long key = key(source);
            String value = values.get(key);
            if (value != null && observed.add(key)) {
                observedTextBytes += 2L * value.length();
            }
            return value;
        }

        boolean containsSource(byte[] source) {
            return values.containsKey(key(source));
        }

        void mask(byte[] source) {
            String previous = values.put(key(source), null);
            if (previous != null) {
                long released = 2L * previous.length();
                textBytes -= released;
                if (resources != null) {
                    resources.releaseRetainedOwnedMemory(released);
                }
            }
        }

        private static long key(byte[] source) {
            return ((long) source.length << 32) | (fontBoxCode(source) & 0xffffffffL);
        }

        @Override
        public void close() {
            close(false);
        }

        void close(boolean resultReturned) {
            if (resources != null) {
                resources.releaseRetainedOwnedMemory(textBytes - (resultReturned ? observedTextBytes : 0L));
            }
            textBytes = 0L;
            observedTextBytes = 0L;
            super.close();
            observed.clear();
            values.clear();
        }
    }

    static final class LimitExceededException extends IOException {

        private static final long serialVersionUID = 1L;
    }
}
