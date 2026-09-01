package net.zerocloud.pdf;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable properties shared by every supported annotation type.
 *
 * <p>The identifier is a nonempty document-wide annotation name. Page
 * numbers are one-based.</p>
 *
 * @since 0.1.0
 */
public final class AnnotationProperties {

    /** The currently supported representation version. */
    public static final int VERSION_1 = 1;

    private final String identifier;
    private final int pageNumber;
    private final AnnotationRectangle rectangle;
    private final String contents;
    private final Set<AnnotationFlag> flags;
    private final AnnotationAppearance appearance;

    private AnnotationProperties(Builder builder) {
        this.identifier = builder.identifier;
        this.pageNumber = builder.pageNumber;
        this.rectangle = builder.rectangle;
        this.contents = builder.contents;
        this.flags = Collections.unmodifiableSet(
                builder.flags.isEmpty()
                        ? EnumSet.noneOf(AnnotationFlag.class)
                        : EnumSet.copyOf(builder.flags));
        this.appearance = builder.appearance;
    }

    /**
     * Starts a version-1 property value.
     *
     * @param identifier the nonempty document-wide annotation identifier
     * @param pageNumber the one-based containing page number
     * @param rectangle the annotation rectangle
     * @return a new builder
     */
    public static Builder version1(
            String identifier,
            int pageNumber,
            AnnotationRectangle rectangle) {
        if (Objects.requireNonNull(identifier, "identifier").isEmpty()) {
            throw new IllegalArgumentException("identifier must not be empty");
        }
        if (pageNumber < 1) {
            throw new IllegalArgumentException("pageNumber must be at least 1");
        }
        return new Builder(identifier, pageNumber,
                Objects.requireNonNull(rectangle, "rectangle"));
    }

    /** Returns the representation version. @return {@link #VERSION_1} */
    public int getVersion() {
        return VERSION_1;
    }

    /** Returns the document-wide identifier. @return the identifier */
    public String getIdentifier() {
        return identifier;
    }

    /** Returns the one-based containing page number. @return the page number */
    public int getPageNumber() {
        return pageNumber;
    }

    /** Returns the annotation rectangle. @return the rectangle */
    public AnnotationRectangle getRectangle() {
        return rectangle;
    }

    /** Returns the optional human-readable contents. @return the contents */
    public Optional<String> getContents() {
        return Optional.ofNullable(contents);
    }

    /** Returns the immutable annotation flags. @return the flags */
    public Set<AnnotationFlag> getFlags() {
        return flags;
    }

    /** Returns the optional normal appearance. @return the appearance */
    public Optional<AnnotationAppearance> getAppearance() {
        return Optional.ofNullable(appearance);
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof AnnotationProperties
                && identifier.equals(
                        ((AnnotationProperties) candidate).identifier)
                && pageNumber == ((AnnotationProperties) candidate).pageNumber
                && rectangle.equals(
                        ((AnnotationProperties) candidate).rectangle)
                && Objects.equals(contents,
                        ((AnnotationProperties) candidate).contents)
                && flags.equals(((AnnotationProperties) candidate).flags)
                && Objects.equals(appearance,
                        ((AnnotationProperties) candidate).appearance);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier, Integer.valueOf(pageNumber), rectangle,
                contents, flags, appearance);
    }

    @Override
    public String toString() {
        return "AnnotationProperties[id=" + identifier + ", page="
                + pageNumber + ", rectangle=" + rectangle + "]";
    }

    /** Builds immutable version-1 annotation properties. @since 0.1.0 */
    public static final class Builder {

        private final String identifier;
        private final int pageNumber;
        private final AnnotationRectangle rectangle;
        private final EnumSet<AnnotationFlag> flags =
                EnumSet.noneOf(AnnotationFlag.class);
        private String contents;
        private AnnotationAppearance appearance;

        private Builder(
                String identifier,
                int pageNumber,
                AnnotationRectangle rectangle) {
            this.identifier = identifier;
            this.pageNumber = pageNumber;
            this.rectangle = rectangle;
        }

        /**
         * Sets the human-readable annotation contents.
         * @param value the contents
         * @return this builder
         */
        public Builder contents(String value) {
            contents = Objects.requireNonNull(value, "value");
            return this;
        }

        /**
         * Adds one annotation flag.
         * @param value the flag
         * @return this builder
         */
        public Builder flag(AnnotationFlag value) {
            flags.add(Objects.requireNonNull(value, "value"));
            return this;
        }

        /**
         * Sets the resource-free normal appearance.
         * @param value the appearance
         * @return this builder
         */
        public Builder appearance(AnnotationAppearance value) {
            appearance = Objects.requireNonNull(value, "value");
            return this;
        }

        /** Builds the immutable properties. @return the properties */
        public AnnotationProperties build() {
            return new AnnotationProperties(this);
        }
    }
}
