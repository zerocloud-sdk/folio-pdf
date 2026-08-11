package net.zerocloud.pdf.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import net.zerocloud.pdf.DocumentCommand;

/**
 * Appends complete named non-primary Sources to the current document in the
 * declared command order.
 *
 * @since 0.1.0
 */
public final class MergeDocuments implements DocumentCommand {

    /** The currently supported command representation version. */
    public static final int VERSION_1 = 1;

    private final List<String> sourceNames;

    private MergeDocuments(String[] sourceNames) {
        List<String> copied = new ArrayList<String>(sourceNames.length);
        for (String sourceName : sourceNames) {
            copied.add(Objects.requireNonNull(sourceName, "sourceName"));
        }
        this.sourceNames = Collections.unmodifiableList(copied);
    }

    /**
     * Creates a version-1 ordered merge command.
     *
     * @param sourceNames request-local non-primary Source names to append
     * @return the immutable command
     */
    public static MergeDocuments version1(String... sourceNames) {
        return new MergeDocuments(Objects.requireNonNull(
                sourceNames,
                "sourceNames").clone());
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
     * Returns the immutable ordered Source names.
     *
     * @return the request-local Source names
     */
    public List<String> getSourceNames() {
        return sourceNames;
    }
}
