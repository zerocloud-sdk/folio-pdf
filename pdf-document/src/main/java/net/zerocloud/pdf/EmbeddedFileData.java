package net.zerocloud.pdf;

import java.util.Objects;
import java.util.Optional;

/**
 * An immutable detached read of one embedded file, including its decoded
 * content and checksums.
 *
 * @since 0.1.0
 */
public final class EmbeddedFileData {

    private final String name;
    private final String mimeSubtype;
    private final String description;
    private final EmbeddedFile.Relationship relationship;
    private final long size;
    private final String md5Hex;
    private final String sha256Hex;
    private final byte[] content;

    EmbeddedFileData(
            String name,
            String mimeSubtype,
            String description,
            EmbeddedFile.Relationship relationship,
            long size,
            String md5Hex,
            String sha256Hex,
            byte[] content) {
        this.name = name;
        this.mimeSubtype = mimeSubtype;
        this.description = description;
        this.relationship = relationship;
        this.size = size;
        this.md5Hex = md5Hex;
        this.sha256Hex = sha256Hex;
        this.content = content.clone();
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

    /**
     * Returns the lowercase hexadecimal SHA-256 checksum computed over the
     * decoded content by this library.
     *
     * @return the 64-character checksum
     */
    public String getSha256Hex() {
        return sha256Hex;
    }

    /**
     * Returns a copy of the decoded file content.
     *
     * @return the content
     */
    public byte[] getContent() {
        return content.clone();
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof EmbeddedFileData
                && name.equals(((EmbeddedFileData) candidate).name)
                && Objects.equals(
                        mimeSubtype,
                        ((EmbeddedFileData) candidate).mimeSubtype)
                && Objects.equals(
                        description,
                        ((EmbeddedFileData) candidate).description)
                && Objects.equals(
                        relationship,
                        ((EmbeddedFileData) candidate).relationship)
                && size == ((EmbeddedFileData) candidate).size
                && Objects.equals(
                        md5Hex,
                        ((EmbeddedFileData) candidate).md5Hex)
                && sha256Hex.equals(((EmbeddedFileData) candidate).sha256Hex)
                && java.util.Arrays.equals(
                        content,
                        ((EmbeddedFileData) candidate).content);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(
                name,
                mimeSubtype,
                description,
                relationship,
                Long.valueOf(size),
                md5Hex,
                sha256Hex)
                + java.util.Arrays.hashCode(content);
    }

    @Override
    public String toString() {
        return "EmbeddedFileData[name=" + name
                + ", mimeSubtype=" + mimeSubtype
                + ", description=" + description
                + ", relationship=" + relationship
                + ", size=" + size
                + ", md5Hex=" + md5Hex
                + ", sha256Hex=" + sha256Hex
                + ", content=" + content.length + " bytes]";
    }
}
