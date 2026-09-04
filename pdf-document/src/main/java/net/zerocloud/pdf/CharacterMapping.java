package net.zerocloud.pdf;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable evidence connecting one encoded PDF character code to Unicode.
 *
 * <p>The source code is exact and defensively copied. Explicit and inferred
 * observations remain independently inspectable. Contradictory or missing
 * evidence has no selected Unicode value.</p>
 *
 * @since 0.1.0
 */
public final class CharacterMapping {

    /** Strength and consistency of the available mapping evidence. */
    public enum Confidence {
        /** A valid ToUnicode mapping supplies the value without disagreement. */
        EXPLICIT,
        /** Supported explicitly declared simple-font encoding evidence supplies it. */
        INFERRED,
        /** Independent explicit and standard evidence disagree. */
        CONTRADICTORY,
        /** No defensible Unicode mapping is available. */
        MISSING
    }

    private final byte[] sourceCode;
    private final Confidence confidence;
    private final String unicode;
    private final String explicitUnicode;
    private final String inferredUnicode;

    CharacterMapping(
            byte[] sourceCode,
            Confidence confidence,
            String unicode,
            String explicitUnicode,
            String inferredUnicode) {
        // The package-private extraction path transfers one immutable,
        // workflow-accounted source-code array into its result values.
        this.sourceCode = Objects.requireNonNull(sourceCode, "sourceCode");
        this.confidence = Objects.requireNonNull(confidence, "confidence");
        this.unicode = unicode;
        this.explicitUnicode = explicitUnicode;
        this.inferredUnicode = inferredUnicode;
    }

    /** Returns the encoded source bytes. @return a defensive copy */
    public byte[] getSourceCode() {
        return Arrays.copyOf(sourceCode, sourceCode.length);
    }

    /** Returns the evidence confidence. @return the confidence */
    public Confidence getConfidence() {
        return confidence;
    }

    /** Returns the selected Unicode only when evidence is defensible. @return value */
    public Optional<String> getUnicode() {
        return Optional.ofNullable(unicode);
    }

    /** Returns the ToUnicode observation when present. @return observation */
    public Optional<String> getExplicitUnicode() {
        return Optional.ofNullable(explicitUnicode);
    }

    /** Returns the independently derived standard observation. @return observation */
    public Optional<String> getInferredUnicode() {
        return Optional.ofNullable(inferredUnicode);
    }
}
