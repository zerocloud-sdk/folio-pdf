package net.zerocloud.pdf.itext7.kernel.pdf;

import java.util.Arrays;
import java.util.Objects;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentPatch;
import net.zerocloud.pdf.PdfStreamEncoding;

/** Decoded stream data with a mapped dictionary view. @since 0.1.0 */
public final class PdfStream extends PdfDictionary {
    private final net.zerocloud.pdf.PdfStream stream;
    private byte[] data;

    /** @param bytes decoded bytes, defensively copied */
    public PdfStream(byte[] bytes) {
        stream = null;
        data = Arrays.copyOf(Objects.requireNonNull(bytes, "bytes"), bytes.length);
    }

    PdfStream(FacadeSession session, FacadeLocation location, net.zerocloud.pdf.PdfStream stream) throws DocumentFailure {
        super(session, location, stream.getDictionary());
        this.stream = stream;
    }

    /** @return a new array of decoded bytes under the Session inspection bound */
    public byte[] getBytes() {
        return stream == null ? Arrays.copyOf(data, data.length) : session.view(stream::readBytes);
    }

    /**
     * Replaces decoded data atomically, using engine-owned Flate metadata for
     * an attached stream.
     * @param bytes replacement decoded bytes
     */
    public void setData(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (stream == null) {
            data = Arrays.copyOf(bytes, bytes.length);
            return;
        }
        requireAttached();
        session.call(nativeSession -> {
            nativeSession.execute(DocumentPatch.builder().replaceStreamData(location.path(), bytes, PdfStreamEncoding.FLATE).build());
            for (String name : new String[] {"Length", "Filter", "DecodeParms", "F", "FFilter", "FDecodeParms", "DL"}) {
                location.dictionaryChanged(net.zerocloud.pdf.PdfName.of(name));
            }
            return null;
        });
    }

    @Override
    public byte getType() {
        return STREAM;
    }

    @Override
    net.zerocloud.pdf.PdfStream nativeValue() {
        return stream == null ? (net.zerocloud.pdf.PdfStream) FacadeValues.convert(this) : stream;
    }

    net.zerocloud.pdf.PdfStream withDictionary(net.zerocloud.pdf.PdfDictionary attributes) {
        return net.zerocloud.pdf.PdfStream.of(attributes, data);
    }
}
