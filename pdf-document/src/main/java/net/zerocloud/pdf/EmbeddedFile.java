package net.zerocloud.pdf;

import java.util.Objects;
import java.util.Optional;

/**
 * An immutable embedded-file specification for the embed command.
 *
 * <p>The name identifies the file inside the document's EmbeddedFiles name
 * tree; embedding the same name again replaces the existing file.</p>
 *
 * @since 0.1.0
 */
public final class EmbeddedFile {

    /**
     * The relationship between the embedded file and the document, per the
     * associated-files vocabulary of ISO 32000.
     *
     * @since 0.1.0
     */
    public enum Relationship {

        /** The file is the source used to create the document. */
        SOURCE,

        /** The file supplies data displayed by the document. */
        DATA,

        /** The file is an alternative representation of the document. */
        ALTERNATIVE,

        /** The file supplements the document representation. */
        SUPPLEMENT,

        /** No relationship is declared. */
        UNSPECIFIED
    }

    /** The currently supported representation version. */
    public static final int VERSION_1 = 1;

    private final String name;
    private final byte[] content;
    private final String mimeSubtype;
    private final String description;
    private final Relationship relationship;

    private EmbeddedFile(
            String name,
            byte[] content,
            String mimeSubtype,
            String description,
            Relationship relationship) {
        this(name, content, mimeSubtype, description, relationship, true);
    }

    private EmbeddedFile(
            String name,
            byte[] content,
            String mimeSubtype,
            String description,
            Relationship relationship,
            boolean copyContent) {
        if (Objects.requireNonNull(name, "name").isEmpty()) {
            throw new IllegalArgumentException("name must not be empty");
        }
        this.name = name;
        byte[] requiredContent = Objects.requireNonNull(content, "content");
        this.content = copyContent ? requiredContent.clone() : requiredContent;
        this.mimeSubtype = mimeSubtype;
        this.description = description;
        this.relationship = relationship;
    }

    static EmbeddedFile fromOwnedContent(
            String name,
            byte[] content,
            String mimeSubtype,
            String description,
            Relationship relationship) {
        return new EmbeddedFile(
                name,
                content,
                mimeSubtype,
                description,
                relationship,
                false);
    }

    byte[] contentForWorkflow() {
        return content;
    }

    /**
     * Creates a minimal version-1 specification with no MIME subtype,
     * description, or relationship.
     *
     * @param name the embedded-file name
     * @param content the raw file content
     * @return the immutable specification
     */
    public static EmbeddedFile version1(String name, byte[] content) {
        return new EmbeddedFile(name, content, null, null, null);
    }

    /**
     * Creates a version-1 specification with a MIME subtype.
     *
     * @param name the embedded-file name
     * @param content the raw file content
     * @param mimeSubtype the file MIME subtype
     * @return the immutable specification
     */
    public static EmbeddedFile version1(
            String name,
            byte[] content,
            String mimeSubtype) {
        return new EmbeddedFile(
                name,
                content,
                Objects.requireNonNull(mimeSubtype, "mimeSubtype"),
                null,
                null);
    }

    /**
     * Creates a fully described version-1 specification.
     *
     * @param name the embedded-file name
     * @param content the raw file content
     * @param mimeSubtype the file MIME subtype
     * @param description the human-readable file description
     * @param relationship the file relationship
     * @return the immutable specification
     */
    public static EmbeddedFile version1(
            String name,
            byte[] content,
            String mimeSubtype,
            String description,
            Relationship relationship) {
        return new EmbeddedFile(
                name,
                content,
                Objects.requireNonNull(mimeSubtype, "mimeSubtype"),
                Objects.requireNonNull(description, "description"),
                Objects.requireNonNull(relationship, "relationship"));
    }

    /**
     * Returns the representation version.
     *
     * @return {@link #VERSION_1}
     */
    public int getVersion() {
        return VERSION_1;
    }

    /**
     * Returns the embedded-file name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns a copy of the raw file content.
     *
     * @return the content
     */
    public byte[] getContent() {
        return content.clone();
    }

    /**
     * Returns the declared MIME subtype when present.
     *
     * @return the MIME subtype, or empty
     */
    public Optional<String> getMimeSubtype() {
        return Optional.ofNullable(mimeSubtype);
    }

    /**
     * Returns the declared description when present.
     *
     * @return the description, or empty
     */
    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    /**
     * Returns the declared relationship, or
     * {@link Relationship#UNSPECIFIED}.
     *
     * @return the relationship
     */
    public Relationship getRelationship() {
        return relationship == null ? Relationship.UNSPECIFIED : relationship;
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof EmbeddedFile
                && name.equals(((EmbeddedFile) candidate).name)
                && java.util.Arrays.equals(
                        content,
                        ((EmbeddedFile) candidate).content)
                && Objects.equals(
                        mimeSubtype,
                        ((EmbeddedFile) candidate).mimeSubtype)
                && Objects.equals(
                        description,
                        ((EmbeddedFile) candidate).description)
                && Objects.equals(
                        relationship,
                        ((EmbeddedFile) candidate).relationship);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(name, mimeSubtype, description, relationship)
                + java.util.Arrays.hashCode(content);
    }

    @Override
    public String toString() {
        return "EmbeddedFile[name=" + name
                + ", content=" + content.length + " bytes"
                + ", mimeSubtype=" + mimeSubtype
                + ", description=" + description
                + ", relationship=" + relationship + "]";
    }
}
