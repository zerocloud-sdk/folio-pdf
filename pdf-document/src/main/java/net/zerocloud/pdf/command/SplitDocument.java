package net.zerocloud.pdf.command;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.zerocloud.pdf.DocumentCommand;
import net.zerocloud.pdf.PageRange;

/**
 * Creates independently publishable page-range products for named workflow
 * Targets.
 *
 * <p>A successful split is terminal for Document Commands in its workflow;
 * later commands fail with
 * {@link net.zerocloud.pdf.DocumentFailureCode#COMMAND_REJECTED}. Queries may
 * still observe the unchanged source Session.</p>
 *
 * @since 0.1.0
 */
public final class SplitDocument implements DocumentCommand {

    /** The currently supported command representation version. */
    public static final int VERSION_1 = 1;

    private final Map<String, PageRange> targetRanges;
    private final int targetDeclarationCount;

    private SplitDocument(Builder builder) {
        this.targetRanges = Collections.unmodifiableMap(
                new LinkedHashMap<String, PageRange>(builder.targetRanges));
        this.targetDeclarationCount = builder.targetDeclarationCount;
    }

    /**
     * Starts a version-1 split command.
     *
     * @return a new ordered product builder
     */
    public static Builder version1() {
        return new Builder();
    }

    /**
     * Returns the command representation version.
     *
     * @return {@link #VERSION_1}
     */
    public int getVersion() {
        return VERSION_1;
    }

    /**
     * Returns the immutable target-to-range mapping in declaration order.
     *
     * @return the split product definitions
     */
    public Map<String, PageRange> getTargetRanges() {
        return targetRanges;
    }

    /**
     * Returns the number of Target declarations supplied to the builder.
     *
     * @return the declaration count, including duplicate names
     */
    public int getTargetDeclarationCount() {
        return targetDeclarationCount;
    }

    /**
     * Builds an ordered version-1 split command.
     *
     * @since 0.1.0
     */
    public static final class Builder {

        private final Map<String, PageRange> targetRanges =
                new LinkedHashMap<String, PageRange>();
        private int targetDeclarationCount;

        private Builder() {
        }

        /**
         * Declares one named Target product and its inclusive page range.
         *
         * @param targetName a request-local publication Target name
         * @param range the inclusive one-based source range
         * @return this builder
         */
        public Builder target(String targetName, PageRange range) {
            targetDeclarationCount++;
            targetRanges.put(
                    Objects.requireNonNull(targetName, "targetName"),
                    Objects.requireNonNull(range, "range"));
            return this;
        }

        /**
         * Builds the immutable split command.
         *
         * @return the split command
         */
        public SplitDocument build() {
            return new SplitDocument(this);
        }
    }
}
