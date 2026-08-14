package net.zerocloud.pdf;

import java.util.Optional;

/**
 * An immutable detached summary of one embedded file.
 *
 * @since 0.1.0
 */
public final class EmbeddedFileSummary {

    private final String name;
    private final String mimeSubtype;
    private final String description;
    private final EmbeddedFile.Relationship relationship;
    private final long size;
    private final String md5Hex;

    EmbeddedFileSummary(
            String name,
            String mimeSubtype,
            String description,
            EmbeddedFile.Relationship relationship,
            long size,
            String md5Hex) {
        this.name = name;
        this.mimeSubtype = mimeSubtype;
        this.description = description;
        this.relationship = relationship;
        this.size = size;
        this.md5Hex = md5Hex;
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
     * {@link EmbeddedFile.Relationship#UNSPECIFIED}.
     *
     * @return the relationship
     */
    public EmbeddedFile.Relationship getRelationship() {
        return relationship == null
                ? EmbeddedFile.Relationship.UNSPECIFIED
                : relationship;
    }

    /**
     * Returns the unfiltered byte length of the embedded content.
     *
     * @return the size in bytes
     */
    public long getSize() {
        return size;
    }

    /**
     * Returns the lowercase hexadecimal MD5 checksum declared by the
     * document, or empty when the document declares none.
     *
     * @return the 32-character checksum, or empty
     */
    public Optional<String> getMd5Hex() {
        return Optional.ofNullable(md5Hex);
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof EmbeddedFileSummary
                && name.equals(((EmbeddedFileSummary) candidate).name)
                && java.util.Objects.equals(
                        mimeSubtype,
                        ((EmbeddedFileSummary) candidate).mimeSubtype)
                && java.util.Objects.equals(
                        description,
                        ((EmbeddedFileSummary) candidate).description)
                && java.util.Objects.equals(
                        relationship,
                        ((EmbeddedFileSummary) candidate).relationship)
                && size == ((EmbeddedFileSummary) candidate).size
                && java.util.Objects.equals(
                        md5Hex,
                        ((EmbeddedFileSummary) candidate).md5Hex);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(
                name,
                mimeSubtype,
                description,
                relationship,
                Long.valueOf(size),
                md5Hex);
    }

    @Override
    public String toString() {
        return "EmbeddedFileSummary[name=" + name
                + ", mimeSubtype=" + mimeSubtype
                + ", description=" + description
                + ", relationship=" + relationship
                + ", size=" + size
                + ", md5Hex=" + md5Hex + "]";
    }
}
