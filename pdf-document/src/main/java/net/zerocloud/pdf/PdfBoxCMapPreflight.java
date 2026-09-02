package net.zerocloud.pdf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Bounds the eager mappings created by FontBox's CMap parser.
 *
 * <p>This is deliberately a small parser for the token boundaries and the two
 * mapping operators that allocate entries. FontBox remains the authoritative
 * parser after this preflight succeeds.</p>
 */
final class PdfBoxCMapPreflight {

    private final TokenReader tokens;
    private final int maximumMappings;
    private int mappings;

    private PdfBoxCMapPreflight(byte[] bytes, int maximumMappings) {
        this.tokens = new TokenReader(bytes, this);
        this.maximumMappings = maximumMappings;
    }

    static int countMappings(byte[] bytes, int maximumMappings)
            throws IOException, LimitExceededException {
        if (bytes == null) {
            throw new NullPointerException("bytes");
        }
        if (maximumMappings < 0) {
            throw new IllegalArgumentException(
                    "maximumMappings must not be negative");
        }
        PdfBoxCMapPreflight preflight =
                new PdfBoxCMapPreflight(bytes, maximumMappings);
        preflight.validate();
        return preflight.mappings;
    }

    private void validate() throws IOException, LimitExceededException {
        Token previous = null;
        Token token;
        while ((token = tokens.next()) != null) {
            if (token.isWord("endcmap")) {
                return;
            }
            if (token.isWord("usecmap")
                    && previous != null
                    && previous.kind == Token.NAME) {
                throw new IOException("ToUnicode usecmap is outside version 1");
            }
            if (previous != null && previous.kind == Token.NUMBER) {
                if (token.isWord("beginbfchar")) {
                    validateCharacters(previous.number);
                } else if (token.isWord("beginbfrange")) {
                    validateRanges(previous.number);
                }
            }
            previous = token;
        }
    }

    private void validateCharacters(int count)
            throws IOException, LimitExceededException {
        requireDeclaredCount(count, "bfchar");
        for (int index = 0; index < count; index++) {
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
        }
        requireTerminator("endbfchar");
    }

    private void validateRanges(int count)
            throws IOException, LimitExceededException {
        requireDeclaredCount(count, "bfrange");
        for (int index = 0; index < count; index++) {
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
            int start = fontBoxCode(sourceStart.bytes);
            int end = fontBoxCode(sourceEnd.bytes);
            if (end < start) {
                throw new IOException("ToUnicode range is reversed");
            }
            long rangeSize = (long) end - (long) start + 1L;
            Token target = requiredToken("bfrange target");
            if (target.kind == Token.HEX) {
                accountScalarRange(
                        rangeSize,
                        target.bytes);
            } else if (target.kind == Token.ARRAY) {
                if (target.elements.size() != rangeSize) {
                    throw new IOException(
                            "ToUnicode range array has the wrong size");
                }
                for (Token element : target.elements) {
                    if (element.kind != Token.HEX) {
                        throw new IOException(
                                "ToUnicode range array is malformed");
                    }
                    requireUnicodeDestination(element.bytes);
                }
                account(target.elements.size());
            } else {
                throw new IOException("Invalid bfrange target");
            }
        }
        requireTerminator("endbfrange");
    }

    private void accountScalarRange(
            long rangeSize,
            byte[] initialTarget)
            throws IOException, LimitExceededException {
        account(rangeSize);
        byte[] target = Arrays.copyOf(initialTarget, initialTarget.length);
        for (long index = 0L; index < rangeSize; index++) {
            requireUnicodeDestination(target);
            if (index + 1L < rangeSize) {
                incrementLikeEmbeddedFontCMap(target);
            }
        }
    }

    private static void incrementLikeEmbeddedFontCMap(byte[] value) {
        for (int position = value.length - 1; position >= 0; position--) {
            if (position > 0 && (value[position] & 0xff) == 0xff) {
                value[position] = 0;
            } else {
                value[position] = (byte) (value[position] + 1);
                return;
            }
        }
    }

    private static void requireUnicodeDestination(byte[] bytes)
            throws IOException {
        if (bytes.length == 0 || (bytes.length & 1) != 0) {
            throw new IOException(
                    "ToUnicode destination is not nonempty UTF-16BE");
        }
        for (int index = 0; index < bytes.length; index += 2) {
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

        TokenReader(byte[] bytes, PdfBoxCMapPreflight preflight) {
            this.bytes = bytes;
            this.preflight = preflight;
        }

        Token next() throws IOException, LimitExceededException {
            skipWhitespaceAndComments();
            if (position >= bytes.length) {
                return null;
            }
            int current = unsigned(bytes[position++]);
            if (current == '<') {
                if (position < bytes.length
                        && unsigned(bytes[position]) == '<') {
                    position++;
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
                skipLiteralString();
                return Token.other();
            }
            if (current == '/') {
                return Token.name(readName());
            }
            if (isNumberStart(current)) {
                return readNumber(current);
            }
            if (current == '>') {
                throw new IOException("Unexpected CMap dictionary end");
            }
            return Token.word(readWord(current));
        }

        private Token readArray()
                throws IOException, LimitExceededException {
            List<Token> values = new ArrayList<Token>();
            readingArray = true;
            try {
                while (true) {
                    Token value = next();
                    if (value == null) {
                        throw new IOException("Unterminated CMap array");
                    }
                    if (value.kind == Token.END_ARRAY) {
                        return Token.array(values);
                    }
                    preflight.requireCapacity(values.size() + 1L);
                    values.add(value);
                }
            } finally {
                readingArray = false;
            }
        }

        private Token readNumber(int first) throws IOException {
            int start = position - 1;
            while (position < bytes.length) {
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
            String value = ascii(start, position);
            try {
                if (value.indexOf('.') >= 0) {
                    return Token.number(Double.valueOf(value).intValue());
                }
                return Token.number(Integer.parseInt(value));
            } catch (NumberFormatException malformed) {
                throw new IOException("Invalid CMap number");
            }
        }

        private String readName() {
            int start = position;
            while (position < bytes.length
                    && !isWhitespace(unsigned(bytes[position]))
                    && !isDelimiter(unsigned(bytes[position]))) {
                position++;
            }
            return ascii(start, position);
        }

        private String readWord(int first) {
            int start = position - 1;
            while (position < bytes.length) {
                int current = unsigned(bytes[position]);
                if (isWhitespace(current)
                        || isDelimiter(current)) {
                    break;
                }
                position++;
            }
            return ascii(start, position);
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
            byte[] result = new byte[length];
            int nibble = 0;
            for (int index = contentStart; index < scan; index++) {
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
            while (position < bytes.length) {
                int current = unsigned(bytes[position++]);
                // This is the same boundary used by FontBox's CMap parser.
                if (current == ')') {
                    return;
                }
            }
            throw new IOException("Unterminated CMap string");
        }

        private void skipWhitespaceAndComments() {
            while (position < bytes.length) {
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

        private void skipComment() {
            while (position < bytes.length) {
                int current = unsigned(bytes[position++]);
                if (current == '\r' || current == '\n') {
                    return;
                }
            }
        }

        private String ascii(int start, int end) {
            return new String(
                    bytes,
                    start,
                    end - start,
                    StandardCharsets.ISO_8859_1);
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

        private final int kind;
        private final int number;
        private final byte[] bytes;
        private final String text;
        private final List<Token> elements;

        private Token(
                int kind,
                int number,
                byte[] bytes,
                String text,
                List<Token> elements) {
            this.kind = kind;
            this.number = number;
            this.bytes = bytes;
            this.text = text;
            this.elements = elements;
        }

        boolean isWord(String value) {
            return kind == WORD && value.equals(text);
        }

        static Token other() {
            return new Token(OTHER, 0, null, null, null);
        }

        static Token number(int value) {
            return new Token(NUMBER, value, null, null, null);
        }

        static Token hex(byte[] value) {
            return new Token(HEX, 0, value, null, null);
        }

        static Token name(String value) {
            return new Token(NAME, 0, null, value, null);
        }

        static Token word(String value) {
            return new Token(WORD, 0, null, value, null);
        }

        static Token array(List<Token> value) {
            return new Token(ARRAY, 0, null, null, value);
        }

        static Token endArray() {
            return new Token(END_ARRAY, 0, null, null, null);
        }
    }

    static final class LimitExceededException extends IOException {

        private static final long serialVersionUID = 1L;
    }
}
