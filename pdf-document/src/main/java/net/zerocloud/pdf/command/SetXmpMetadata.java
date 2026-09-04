package net.zerocloud.pdf.command;

import java.util.Objects;
import net.zerocloud.pdf.DocumentCommand;

/**
 * Creates or replaces the document catalog XMP metadata stream.
 *
 * <p>The packet is validated as a well-formed UTF-8 XMP metadata packet
 * before the catalog changes. A pre-existing metadata stream is replaced
 * atomically; every other document structure is preserved unchanged.</p>
 *
 * @since 0.1.0
 */
public final class SetXmpMetadata implements DocumentCommand {

    /** The currently supported command representation version. */
    public static final int VERSION_1 = 1;

    private final byte[] xmpPacket;

    private SetXmpMetadata(byte[] xmpPacket) {
        this.xmpPacket = xmpPacket.clone();
    }

    /**
     * Creates a version-1 XMP metadata replacement command.
     *
     * @param xmpPacket the complete UTF-8 XMP metadata packet
     * @return the immutable command
     */
    public static SetXmpMetadata version1(byte[] xmpPacket) {
        return new SetXmpMetadata(
                Objects.requireNonNull(xmpPacket, "xmpPacket"));
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
     * Returns a copy of the XMP metadata packet bytes.
     *
     * @return the XMP packet
     */
    public byte[] getXmpPacket() {
        return xmpPacket.clone();
    }

    /**
     * Returns the exact packet byte length without copying its content.
     *
     * @return the packet byte length
     */
    public int getXmpPacketLength() {
        return xmpPacket.length;
    }
}
