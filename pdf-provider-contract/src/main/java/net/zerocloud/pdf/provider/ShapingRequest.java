package net.zerocloud.pdf.provider;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Detached version 1 OpenType shaping input for the external Provider seam.
 * The font is an explicitly supplied single-face sfnt program. Text uses
 * UTF-16 input indices; direction, four-letter script tag and language token
 * are explicit. The OpenType shaper uses default features, monotone grapheme
 * clusters and a scale equal to the font's units per em.
 *
 * <p>The wire format and its absolute bounds are described in
 * {@code docs/harfbuzz-shaping.md}. Provider and Workflow limits may be lower.
 * This value does not load fonts or discover a native installation.</p>
 *
 * @since 0.1.0
 */
public final class ShapingRequest {

    /** Capability selected explicitly to enable Composition shaping. */
    public static final String CAPABILITY_ID = "composition.shaping.harf-buzz";
    /** Absolute HRQ1 byte ceiling, including font, text and payload framing. */
    public static final int MAXIMUM_PAYLOAD_BYTES = 64 * 1024 * 1024;
    /** Absolute logical input length in UTF-16 code units. */
    public static final int MAXIMUM_TEXT_UNITS = 1024 * 1024;
    /** Absolute requested output glyph-count ceiling. */
    public static final int MAXIMUM_GLYPHS = 1024 * 1024;
    private static final int MAGIC = 0x48525131;

    /** Horizontal direction of a single logical input run. */
    public enum Direction {
        /** Increasing logical cluster starts in visual glyph order. */
        LEFT_TO_RIGHT,
        /** Decreasing logical cluster starts in visual glyph order. */
        RIGHT_TO_LEFT
    }

    private final byte[] font;
    private final String text;
    private final String script;
    private final String language;
    private final Direction direction;
    private final int maximumGlyphs;

    private ShapingRequest(byte[] font, String text, String script,
            String language, Direction direction, int maximumGlyphs) {
        Objects.requireNonNull(font, "font");
        this.text = Objects.requireNonNull(text, "text");
        this.script = Objects.requireNonNull(script, "script");
        this.language = Objects.requireNonNull(language, "language");
        this.direction = Objects.requireNonNull(direction, "direction");
        if (font.length == 0 || text.isEmpty()
                || text.length() > MAXIMUM_TEXT_UNITS
                || !script.matches("[A-Za-z]{4}")
                || !language.matches("[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*")
                || language.length() > 63
                || maximumGlyphs < 1 || maximumGlyphs > MAXIMUM_GLYPHS
                || 28L + font.length + 2L * text.length() + language.length()
                        > MAXIMUM_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Invalid or excessive shaping input");
        }
        for (int index = 0; index < text.length(); index++) {
            char unit = text.charAt(index);
            if (Character.isHighSurrogate(unit)) {
                if (++index == text.length()
                        || !Character.isLowSurrogate(text.charAt(index))) {
                    throw new IllegalArgumentException("Malformed shaping UTF-16 input");
                }
            } else if (Character.isLowSurrogate(unit)) {
                throw new IllegalArgumentException("Malformed shaping UTF-16 input");
            }
        }
        this.font = font.clone();
        this.maximumGlyphs = maximumGlyphs;
    }

    /**
     * Creates an immutable request, copying the explicit font immediately.
     *
     * @param font nonempty single-face font program; validity is checked by the engine
     * @param text nonempty, well-formed UTF-16 logical input
     * @param script four ASCII letters identifying an ISO 15924 script
     * @param language at most 63 ASCII alphanumeric characters and separating
     *        hyphens; full BCP 47 validity and registration are not checked
     * @param direction explicit horizontal direction
     * @param maximumGlyphs positive output limit, at most {@link #MAXIMUM_GLYPHS}
     * @return the detached request
     * @throws NullPointerException if a reference argument is null
     * @throws IllegalArgumentException for malformed fields or exceeded absolute bounds
     */
    public static ShapingRequest version1(byte[] font, String text, String script,
            String language, Direction direction, int maximumGlyphs) {
        return new ShapingRequest(font, text, script, language, direction, maximumGlyphs);
    }

    /**
     * Returns the explicit font program.
     * @return a fresh copy of the complete explicit font program
     */
    public byte[] getFont() { return font.clone(); }
    /**
     * Returns the logical input.
     * @return the logical input whose UTF-16 indices identify clusters
     */
    public String getText() { return text; }
    /**
     * Returns the script tag.
     * @return the declared four-letter script tag
     */
    public String getScript() { return script; }
    /**
     * Returns the language token.
     * @return the declared language token
     */
    public String getLanguage() { return language; }
    /**
     * Returns the shaping direction.
     * @return the declared horizontal direction
     */
    public Direction getDirection() { return direction; }
    /**
     * Returns the glyph-count limit.
     * @return the requested glyph-count ceiling
     */
    public int getMaximumGlyphs() { return maximumGlyphs; }

    /**
     * Encodes one bounded big-endian HRQ1 payload for {@link ProviderRequest}.
     * @return fresh payload bytes, without the outer subprocess framing
     */
    public byte[] encode() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(MAGIC);
            output.writeInt(font.length);
            output.writeInt(text.length());
            output.writeInt(maximumGlyphs);
            output.writeInt(direction == Direction.LEFT_TO_RIGHT ? 0 : 1);
            output.write(script.getBytes(StandardCharsets.US_ASCII));
            output.writeInt(language.length());
            output.write(language.getBytes(StandardCharsets.US_ASCII));
            output.write(font);
            output.writeChars(text);
            output.flush();
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("Memory encoding failed", impossible);
        }
    }

    /**
     * Decodes a detached payload, rejecting malformed or excessive input.
     * @param payload complete HRQ1 bytes, without outer subprocess framing
     * @return the immutable decoded request
     * @throws NullPointerException if payload is null
     * @throws IllegalArgumentException for malformed or excessive payloads
     */
    public static ShapingRequest decode(byte[] payload) {
        Objects.requireNonNull(payload, "payload");
        if (payload.length < 28 || payload.length > MAXIMUM_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Invalid shaping payload length");
        }
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload));
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException("Unsupported shaping request version");
            }
            int fontLength = input.readInt();
            int textLength = input.readInt();
            int glyphLimit = input.readInt();
            int direction = input.readInt();
            byte[] script = new byte[4];
            input.readFully(script);
            int languageLength = input.readInt();
            if (fontLength < 1 || textLength < 1 || textLength > MAXIMUM_TEXT_UNITS
                    || languageLength < 1 || languageLength > 63
                    || direction < 0 || direction > 1
                    || 28L + fontLength + 2L * textLength + languageLength != payload.length) {
                throw new IllegalArgumentException("Invalid shaping payload fields");
            }
            byte[] language = new byte[languageLength];
            input.readFully(language);
            byte[] font = new byte[fontLength];
            input.readFully(font);
            StringBuilder text = new StringBuilder(textLength);
            for (int index = 0; index < textLength; index++) { text.append(input.readChar()); }
            return version1(font, text.toString(),
                    new String(script, StandardCharsets.US_ASCII),
                    new String(language, StandardCharsets.US_ASCII),
                    direction == 0 ? Direction.LEFT_TO_RIGHT : Direction.RIGHT_TO_LEFT,
                    glyphLimit);
        } catch (IOException failure) {
            throw new IllegalArgumentException("Truncated shaping payload");
        }
    }
}
