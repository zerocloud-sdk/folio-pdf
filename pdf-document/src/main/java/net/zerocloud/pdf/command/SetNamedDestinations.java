package net.zerocloud.pdf.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.zerocloud.pdf.DocumentCommand;
import net.zerocloud.pdf.PageDestination;

/**
 * Applies ordered named-destination replacements and removals to the
 * document's Dests name tree.
 *
 * <p>Pre-existing destinations that the command does not name are preserved
 * unchanged, and unknown subtrees of the Names dictionary are left intact.
 * Removing the final destination drops the empty Dests name tree.</p>
 *
 * @since 0.1.0
 */
public final class SetNamedDestinations implements DocumentCommand {

    /** The currently supported command representation version. */
    public static final int VERSION_1 = 1;

    private final Map<String, PageDestination> entries;
    private final List<String> removedNames;

    private SetNamedDestinations(Builder builder) {
        this.entries = Collections.unmodifiableMap(
                new LinkedHashMap<String, PageDestination>(builder.entries));
        this.removedNames = Collections.unmodifiableList(
                new ArrayList<String>(builder.removedNames));
    }

    /**
     * Starts a version-1 named-destination update.
     *
     * @return a new ordered update builder
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
     * Returns the destinations to create or replace, in declaration order.
     *
     * @return the immutable name-to-destination mapping
     */
    public Map<String, PageDestination> getEntries() {
        return entries;
    }

    /**
     * Returns the destination names to remove, in declaration order.
     *
     * @return the immutable removed names
     */
    public List<String> getRemovedNames() {
        return removedNames;
    }

    /**
     * Builds an ordered version-1 named-destination update.
     *
     * @since 0.1.0
     */
    public static final class Builder {

        private final Map<String, PageDestination> entries =
                new LinkedHashMap<String, PageDestination>();
        private final List<String> removedNames = new ArrayList<String>();

        private Builder() {
        }

        /**
         * Creates or replaces one named destination.
         *
         * @param name the destination name
         * @param destination the explicit page destination
         * @return this builder
         */
        public Builder set(String name, PageDestination destination) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(destination, "destination");
            if (removedNames.contains(name)) {
                throw new IllegalArgumentException(
                        "The name is already selected for removal: " + name);
            }
            entries.put(name, destination);
            return this;
        }

        /**
         * Removes one named destination.
         *
         * @param name the destination name
         * @return this builder
         */
        public Builder remove(String name) {
            Objects.requireNonNull(name, "name");
            if (entries.containsKey(name)) {
                throw new IllegalArgumentException(
                        "The name is already selected for replacement: " + name);
            }
            if (!removedNames.contains(name)) {
                removedNames.add(name);
            }
            return this;
        }

        /**
         * Builds the immutable update command.
         *
         * @return the update command
         */
        public SetNamedDestinations build() {
            return new SetNamedDestinations(this);
        }
    }
}
