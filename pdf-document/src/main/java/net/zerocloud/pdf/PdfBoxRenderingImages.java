package net.zerocloud.pdf;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStreamImpl;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.filter.DecodeResult;
import org.apache.pdfbox.filter.FilterFactory;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDInlineImage;
import org.apache.pdfbox.rendering.PageDrawer;

/** Admission before inline decoding, plus bounded mask/resource discovery. */
final class PdfBoxRenderingImages {
    private PdfBoxRenderingImages() { }

    static void drawInline(Operator operator, PDResources pageResources, PageDrawer drawer,
            WorkflowResourceContext resources, EnumSet<RenderDiagnostic> diagnostics) throws IOException {
        COSDictionary parameters = new COSDictionary(operator.getImageParameters());
        long width = parameters.getInt(COSName.WIDTH, COSName.W, -1);
        long height = parameters.getInt(COSName.HEIGHT, COSName.H, -1);
        if (width <= 0 || height <= 0) { throw new IOException("Invalid inline image dimensions."); }
        resources.consumeDecodedPixelsAsIOException(width * height);
        COSBase filters = parameters.getDictionaryObject(COSName.FILTER, COSName.F);
        if (platformFilter(filters)) {
            requireTerminalPlatformFilter(filters);
            diagnostics.add(RenderDiagnostic.PLATFORM_IMAGE_CODEC);
        }
        byte[] raw = operator.getImageData();
        if (raw == null) { throw new IOException("Missing inline image data."); }
        WorkflowResourceContext.OwnedBytes decoded = null;
        try (WorkflowResourceContext.MemoryReservation adopted = resources.reserveOwnedMemoryAsIOException(raw.length)) {
            byte[] data = raw;
            DecodeResult result = null;
            int count = filters == null ? 0 : filters instanceof COSArray ? ((COSArray) filters).size() : 1;
            for (int index = 0; index < count; index++) {
                resources.checkpointAsIOException();
                COSBase filter = filters instanceof COSArray ? ((COSArray) filters).getObject(index) : filters;
                if (!(filter instanceof COSName)) { throw new IOException("Invalid inline image filter."); }
                if (platformFilter(filter)) { requireCodecGeometry(data, width, height, resources); }
                WorkflowResourceContext.OwnedBytes next;
                try (WorkflowResourceContext.OwnedByteAccumulator output = resources.ownedByteAccumulator()) {
                    result = FilterFactory.INSTANCE.getFilter((COSName) filter).decode(
                            resources.checkpointedInput(new ByteArrayInputStream(data)),
                            new DecodedOutput(output, resources), parameters, index);
                    next = output.finishWorkingAsIOException();
                }
                if (decoded != null) { decoded.close(); }
                decoded = next;
                data = decoded.getBytes();
            }
            if (result != null) { parameters.addAll(result.getParameters()); }
            parameters.removeItem(COSName.FILTER);
            parameters.removeItem(COSName.F);
            parameters.removeItem(COSName.DECODE_PARMS);
            parameters.removeItem(COSName.DP);
            // The inline constructor now receives admitted, fully decoded bytes.
            drawer.drawImage(new PDInlineImage(parameters, data, pageResources));
        } finally {
            if (decoded != null) { decoded.close(); }
        }
    }

    static void inspect(COSDictionary image, WorkflowResourceContext resources,
            EnumSet<RenderDiagnostic> diagnostics) throws IOException {
        try (WorkflowResourceContext.OwnedMemoryScope memory = resources.ownedMemoryScope()) {
            memory.retainAsIOException(512);
            Deque<MaskFrame> stack = new ArrayDeque<MaskFrame>();
            IdentityHashMap<COSDictionary, Boolean> visited = new IdentityHashMap<COSDictionary, Boolean>();
            stack.push(new MaskFrame(image));
            while (!stack.isEmpty()) {
                resources.checkpointAsIOException();
                MaskFrame frame = stack.peek();
                if (frame.next == 0) {
                    Boolean active = visited.get(frame.image);
                    if (Boolean.TRUE.equals(active)) { throw new IOException("Cyclic image mask."); }
                    if (Boolean.FALSE.equals(active)) { stack.pop(); continue; }
                    // The backend itself follows masks recursively; keep its call depth finite.
                    if (stack.size() > 64) { throw new IOException("Image mask depth exceeds the rendering profile."); }
                    visited.put(frame.image, Boolean.TRUE);
                    if (frame.image instanceof COSStream) {
                        long width = frame.image.getInt(COSName.WIDTH, -1);
                        long height = frame.image.getInt(COSName.HEIGHT, -1);
                        if (width <= 0L || height <= 0L) {
                            throw new IOException("Invalid image dimensions.");
                        }
                        resources.consumeImageDimensionsAsIOException(
                                (COSStream) frame.image,
                                width,
                                height);
                    }
                    if (platformFilter(frame.image.getDictionaryObject(COSName.FILTER, COSName.F))) {
                        diagnostics.add(RenderDiagnostic.PLATFORM_IMAGE_CODEC);
                        if (frame.image instanceof COSStream) {
                            validatePlatformImageBeforeDecode((COSStream) frame.image, resources);
                        }
                    }
                }
                if (frame.next < 2) {
                    COSBase mask = frame.image.getDictionaryObject(frame.next++ == 0 ? COSName.SMASK : COSName.MASK);
                    if (mask instanceof COSDictionary) {
                        memory.retainAsIOException(512);
                        stack.push(new MaskFrame((COSDictionary) mask));
                    }
                } else {
                    visited.put(frame.image, Boolean.FALSE);
                    stack.pop();
                }
            }
        }
    }

    static boolean isPlatformImage(COSStream stream) throws IOException {
        return COSName.IMAGE.equals(stream.getCOSName(COSName.SUBTYPE))
                && platformFilter(stream.getDictionaryObject(COSName.FILTER, COSName.F));
    }

    static void validatePlatformImageBeforeDecode(COSStream stream,
            WorkflowResourceContext resources) throws IOException {
        admitPlatformImageDeclaration(stream, resources);
        PdfBoxHostileInputPreflight.validatePlatformImageHeaderBeforeDecode(
                stream,
                resources);
        resources.acceptPlatformImage(stream);
    }

    static void admitPlatformImageDeclaration(COSStream stream,
            WorkflowResourceContext resources) throws IOException {
        requireTerminalPlatformFilter(
                stream.getDictionaryObject(COSName.FILTER, COSName.F));
        long width = stream.getInt(COSName.WIDTH, -1);
        long height = stream.getInt(COSName.HEIGHT, -1);
        if (width <= 0L || height <= 0L) {
            throw new IOException("Invalid platform image dimensions.");
        }
        resources.consumeImageDimensionsAsIOException(stream, width, height);
    }

    static void requireStreamCodecGeometry(
            COSStream stream,
            Path encoded,
            WorkflowResourceContext resources) throws IOException {
        requireCodecGeometry(
                new HeaderInput(encoded, resources),
                stream.getInt(COSName.WIDTH, -1),
                stream.getInt(COSName.HEIGHT, -1));
    }

    private static void requireCodecGeometry(byte[] bytes, long width, long height,
            WorkflowResourceContext resources) throws IOException {
        requireCodecGeometry(new HeaderInput(bytes, resources), width, height);
    }

    private static void requireCodecGeometry(
            HeaderInput input,
            long width,
            long height) throws IOException {
        try (HeaderInput owned = input) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(owned);
            if (!readers.hasNext()) { throw new IOException("No bounded codec header reader is available."); }
            ImageReader reader = readers.next();
            try {
                reader.setInput(owned, false, true);
                if (reader.getWidth(0) != width || reader.getHeight(0) != height) {
                    throw new IOException("Encoded image dimensions disagree with the admitted declaration.");
                }
            } finally { reader.dispose(); }
        }
    }

    private static final class HeaderInput extends ImageInputStreamImpl {
        private final byte[] bytes;
        private final RandomAccessFile file;
        private final long length;
        private final WorkflowResourceContext resources;
        HeaderInput(byte[] bytes, WorkflowResourceContext resources) {
            this.bytes = bytes;
            this.file = null;
            this.length = bytes.length;
            this.resources = resources;
        }
        HeaderInput(Path path, WorkflowResourceContext resources) throws IOException {
            this.bytes = null;
            this.file = new RandomAccessFile(path.toFile(), "r");
            this.length = file.length();
            this.resources = resources;
        }
        @Override public int read() throws IOException {
            checkClosed(); resources.checkpointAsIOException(); bitOffset = 0;
            if (streamPos >= length) { return -1; }
            if (bytes != null) { return bytes[(int) streamPos++] & 255; }
            file.seek(streamPos);
            int value = file.read();
            if (value >= 0) { streamPos++; }
            return value;
        }
        @Override public int read(byte[] target, int offset, int length) throws IOException {
            checkClosed(); resources.checkpointAsIOException(); bitOffset = 0;
            if (offset < 0 || length < 0 || offset > target.length - length) { throw new IndexOutOfBoundsException(); }
            if (length == 0) { return 0; }
            if (streamPos >= this.length) { return -1; }
            int count = (int) Math.min(length, this.length - streamPos);
            if (bytes != null) {
                System.arraycopy(bytes, (int) streamPos, target, offset, count);
            } else {
                file.seek(streamPos);
                count = file.read(target, offset, count);
                if (count < 0) { return -1; }
            }
            streamPos += count;
            return count;
        }
        @Override public long length() { return length; }
        @Override public void close() throws IOException {
            try { super.close(); }
            finally { if (file != null) { file.close(); } }
        }
    }

    private static boolean platformFilter(COSBase filter) throws IOException {
        if (filter instanceof COSArray) {
            boolean platform = false;
            for (int i = 0; i < ((COSArray) filter).size(); i++) {
                COSBase item = ((COSArray) filter).getObject(i);
                if (!(item instanceof COSName)) { throw new IOException("Invalid image filter declaration."); }
                platform |= platformFilter(item);
            }
            return platform;
        }
        return COSName.DCT_DECODE.equals(filter) || COSName.DCT_DECODE_ABBREVIATION.equals(filter)
                || COSName.JPX_DECODE.equals(filter) || COSName.JBIG2_DECODE.equals(filter);
    }

    private static void requireTerminalPlatformFilter(COSBase filters) throws IOException {
        if (!(filters instanceof COSArray)) { return; }
        COSArray array = (COSArray) filters;
        boolean found = false;
        for (int index = 0; index < array.size(); index++) {
            COSBase filter = array.getObject(index);
            if (!(filter instanceof COSName)) { throw new IOException("Invalid image filter declaration."); }
            if (platformFilter(filter)) {
                if (found || index != array.size() - 1) {
                    throw new IOException("Platform image codecs must be the terminal filter.");
                }
                found = true;
            }
        }
    }

    private static final class MaskFrame {
        private final COSDictionary image;
        private int next;
        MaskFrame(COSDictionary image) { this.image = image; }
    }

    private static final class DecodedOutput extends OutputStream {
        private final OutputStream output;
        private final WorkflowResourceContext resources;
        DecodedOutput(OutputStream output, WorkflowResourceContext resources) {
            this.output = output; this.resources = resources;
        }
        @Override public void write(int value) throws IOException {
            resources.consumeDecompressedBytesAsIOException(1);
            output.write(value);
        }
        @Override public void write(byte[] bytes, int offset, int length) throws IOException {
            resources.consumeDecompressedBytesAsIOException(length);
            output.write(bytes, offset, length);
        }
    }
}
