package net.zerocloud.pdf.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.zerocloud.pdf.DocumentCommand;

/**
 * Flattens supported non-form annotations with validated normal appearances.
 *
 * <p>Version 1 isolates pre-existing page content with graphics-state
 * save/restore streams, incorporates each resource-free normal appearance,
 * and only then removes the annotation. Widget annotations are rejected
 * because AcroForm flattening belongs to the Forms context.</p>
 *
 * @since 0.1.0
 */
public final class FlattenAnnotations implements DocumentCommand {

    /** The currently supported command representation version. */
    public static final int VERSION_1 = 1;

    private final List<String> identifiers;

    private FlattenAnnotations(List<String> identifiers) {
        this.identifiers = identifiers;
    }

    /**
     * Creates a version-1 flattening command.
     * @param firstIdentifier the first nonempty annotation identifier
     * @param remainingIdentifiers any additional identifiers
     * @return the immutable command
     */
    public static FlattenAnnotations version1(
            String firstIdentifier,
            String... remainingIdentifiers) {
        Set<String> ordered = new LinkedHashSet<String>();
        add(ordered, firstIdentifier);
        Objects.requireNonNull(remainingIdentifiers, "remainingIdentifiers");
        for (String identifier : remainingIdentifiers) {
            add(ordered, identifier);
        }
        return new FlattenAnnotations(Collections.unmodifiableList(
                new ArrayList<String>(ordered)));
    }

    private static void add(Set<String> identifiers, String identifier) {
        Objects.requireNonNull(identifier, "identifier");
        if (identifier.isEmpty()) {
            throw new IllegalArgumentException(
                    "identifier must not be empty");
        }
        if (!identifiers.add(identifier)) {
            throw new IllegalArgumentException(
                    "identifier must be unique: " + identifier);
        }
    }

    /** Returns the command version. @return {@link #VERSION_1} */
    public int getVersion() {
        return VERSION_1;
    }

    /** Returns identifiers in declaration order. @return the identifiers */
    public List<String> getIdentifiers() {
        return identifiers;
    }
}
