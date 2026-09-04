package net.zerocloud.pdf;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import org.apache.pdfbox.cos.COSName;

/** Accounted bounded ASCII serialization for transaction-owned payloads. */
final class WorkflowAsciiOutput implements AutoCloseable {

    private final WorkflowResourceContext resources;
    private final WorkflowResourceContext.OwnedByteAccumulator output;
    private final long maximumBytes;
    private final PdfBoxPageContentSupport.FailureFactory limitFailure;
    private long bytes;

    WorkflowAsciiOutput(
            WorkflowResourceContext resources,
            long maximumBytes,
            PdfBoxPageContentSupport.FailureFactory limitFailure) {
        if (maximumBytes < 0L) {
            throw new IllegalArgumentException(
                    "maximumBytes must not be negative");
        }
        this.resources = resources;
        this.output = resources.ownedByteAccumulator();
        this.maximumBytes = maximumBytes;
        this.limitFailure = limitFailure;
    }

    void append(String value) throws DocumentFailure {
        if (value == null) {
            throw new NullPointerException("value");
        }
        for (int index = 0; index < value.length(); index++) {
            if ((index & 1023) == 0) {
                resources.checkpoint();
            }
            if (value.charAt(index) > 0x7f) {
                throw limitFailure.create();
            }
        }
        requireCapacity(value.length());
        try {
            for (int index = 0; index < value.length(); index++) {
                if ((index & 1023) == 0) {
                    resources.checkpoint();
                }
                output.write(value.charAt(index));
            }
        } catch (IOException failure) {
            resources.rethrowResourceOrTerminalFailure(failure);
            throw limitFailure.create();
        }
    }

    void append(BigDecimal value) throws DocumentFailure {
        if (value == null) {
            throw new NullPointerException("value");
        }
        resources.checkpoint();
        long characters = PdfBoxValueAdapter.plainStringLength(value);
        requireCapacity(characters);
        try (WorkflowResourceContext.MemoryReservation serialization =
                resources.reserveOwnedMemory(4L * characters)) {
            if (characters > Integer.MAX_VALUE - 8L) {
                throw limitFailure.create();
            }
            String lexical = value.toPlainString();
            resources.checkpoint();
            if (lexical.length() != (int) characters) {
                throw limitFailure.create();
            }
            for (int index = 0; index < lexical.length(); index++) {
                if ((index & 1023) == 0) {
                    resources.checkpoint();
                }
                char character = lexical.charAt(index);
                if (character > 0x7f) {
                    throw limitFailure.create();
                }
                try {
                    output.write(character);
                } catch (IOException failure) {
                    resources.rethrowResourceOrTerminalFailure(failure);
                    throw limitFailure.create();
                }
            }
        }
    }

    void append(char value) throws DocumentFailure {
        if (value > 0x7f) {
            throw limitFailure.create();
        }
        requireCapacity(1L);
        try {
            output.write(value);
        } catch (IOException failure) {
            resources.rethrowResourceOrTerminalFailure(failure);
            throw limitFailure.create();
        }
    }

    void append(int value) throws DocumentFailure {
        append(Integer.toString(value));
    }

    void appendPdfName(
            COSName name,
            PdfBoxPageContentSupport.FailureFactory serializationFailure)
            throws DocumentFailure {
        if (name == null) {
            throw new NullPointerException("name");
        }
        try {
            name.writePDF(new OutputStream() {
                private int emitted;

                @Override
                public void write(int value) throws IOException {
                    if ((emitted++ & 1023) == 0) {
                        resources.checkpointAsIOException();
                    }
                    if (value < 0 || value > 0x7f) {
                        throw new NameSerializationIOException();
                    }
                    try {
                        requireCapacity(1L);
                    } catch (DocumentFailure exhausted) {
                        throw new LocalLimitIOException();
                    }
                    output.write(value);
                }
            });
        } catch (LocalLimitIOException exhausted) {
            throw limitFailure.create();
        } catch (NameSerializationIOException invalidName) {
            throw serializationFailure.create();
        } catch (IOException failure) {
            resources.rethrowResourceOrTerminalFailure(failure);
            throw serializationFailure.create();
        } catch (RuntimeException failure) {
            resources.rethrowTerminalFailure();
            throw serializationFailure.create();
        }
    }

    byte[] finishRetained() throws DocumentFailure {
        return output.finishRetained();
    }

    WorkflowResourceContext.OwnedBytes finishWorking()
            throws DocumentFailure {
        return output.finishWorking();
    }

    @Override
    public void close() {
        output.close();
    }

    private void requireCapacity(long additional) throws DocumentFailure {
        if (additional < 0L || bytes > maximumBytes - additional) {
            throw limitFailure.create();
        }
        bytes += additional;
    }

    private static final class LocalLimitIOException extends IOException {
        private static final long serialVersionUID = 1L;
    }

    private static final class NameSerializationIOException
            extends IOException {
        private static final long serialVersionUID = 1L;
    }
}
