package net.zerocloud.pdf.itext7.kernel.pdf;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import net.zerocloud.pdf.PasswordCredential;
import net.zerocloud.pdf.PasswordSecurityInfo;
import net.zerocloud.pdf.PdfVersionInfo;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.query.DocumentVersion;
import net.zerocloud.pdf.query.DocumentSecurity;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.WorkflowResourcePolicy;
import net.zerocloud.pdf.query.PageCount;

/** A bounded, validated Source snapshot transferred from Reader to Document. */
final class FacadeSource implements AutoCloseable {
    private static final WorkflowResourcePolicy DEFAULTS = WorkflowResourcePolicy.safeDefaults();
    private final Path directory;
    final Path path;
    final int pageCount;
    final long bytes;
    final PasswordSecurityInfo security;
    final PdfVersionInfo version;
    private final PasswordCredential credential;
    private final boolean ownsCredential;

    private FacadeSource(Path directory, Path path, Observation observation, long bytes, PasswordCredential credential, boolean ownsCredential) {
        this.directory = directory;
        this.path = path;
        this.pageCount = observation.pages;
        this.security = observation.security;
        this.version = observation.version;
        this.credential = credential;
        this.ownsCredential = ownsCredential;
        this.bytes = bytes;
    }

    static FacadeSource capture(Path original) throws IOException {
        return capture(original, new ReaderProperties());
    }

    static FacadeSource capture(Path original, ReaderProperties properties) throws IOException {
        FacadeSource captured = null;
        try (InputStream input = Files.newInputStream(original)) {
            captured = capture(input, properties);
            return captured;
        } catch (IOException failure) {
            if (captured != null) {
                try { captured.close(); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            }
            if (failure.getCause() instanceof DocumentFailure
                    || (failure.getMessage() != null && failure.getMessage().startsWith("CREDENTIAL_REJECTED:"))) {
                throw failure;
            }
            throw readFailure();
        }
    }

    static FacadeSource capture(InputStream input) throws IOException {
        return capture(input, new ReaderProperties());
    }

    static FacadeSource capture(InputStream input, ReaderProperties properties) throws IOException {
        byte[] encoded = properties.password();
        PasswordCredential credential = properties.credential();
        boolean owned = encoded != null;
        boolean unicodeFirst = true;
        Path directory = null;
        Path snapshot = null;
        boolean retained = false;
        Throwable primaryFailure = null;
        try {
            if (owned) {
                try { credential = FacadePasswords.credential(encoded, true); }
                catch (IllegalArgumentException invalidUtf8) {
                    unicodeFirst = false;
                    credential = FacadePasswords.credential(encoded, false);
                }
            }
            // The default temporary-directory attributes restrict access to
            // its owner on POSIX systems, before any Source bytes are written.
            directory = Files.createTempDirectory(".folio-pdf-facade-");
            snapshot = directory.resolve("source.pdf");
            Observation observation = null;
            DocumentFailure rejected = null;
            try (OutputStream output = Files.newOutputStream(snapshot)) {
                InputStream copying = new FilterInputStream(input) {
                    private long copied;

                    @Override
                    public int read() throws IOException {
                        int value = in.read();
                        if (value >= 0 && copied < DEFAULTS.getMaximumInputBytes()) {
                            output.write(value);
                            copied++;
                        }
                        return value;
                    }

                    @Override
                    public int read(byte[] buffer, int offset, int length) throws IOException {
                        int count = in.read(buffer, offset, length);
                        if (count > 0) {
                            int retained = (int) Math.min(count, DEFAULTS.getMaximumInputBytes() - copied);
                            output.write(buffer, offset, retained);
                            copied += retained;
                        }
                        return count;
                    }
                };
                try {
                    observation = observe(DocumentSource.stream(copying, DEFAULTS.getMaximumInputBytes()), credential,
                            DEFAULTS.getMaximumInputBytes());
                } catch (DocumentFailure failure) {
                    if (!owned || failure.getCode() != DocumentFailureCode.CREDENTIAL_REJECTED) { throw failure; }
                    rejected = failure;
                }
            }
            // Interpret encoded bytes only under the observed revision. In
            // particular, valid UTF-8 bytes cannot authorize a different legacy
            // password through the Unicode interpretation.
            if (owned && (rejected != null || (observation.security.isPasswordProtected()
                    && (observation.security.getSecurityHandlerRevision() >= 5) != unicodeFirst))) {
                if (!unicodeFirst) {
                    if (rejected != null) { throw rejected; }
                    throw new IOException("CREDENTIAL_REJECTED: The supplied Source credential was not accepted.");
                }
                credential.close();
                credential = FacadePasswords.credential(encoded, false);
                observation = observe(DocumentSource.path(snapshot), credential, Files.size(snapshot));
                if (observation.security.getSecurityHandlerRevision() >= 5) {
                    if (rejected != null) { throw rejected; }
                    throw new IOException("CREDENTIAL_REJECTED: The supplied Source credential was not accepted.");
                }
            }
            FacadeSource source = new FacadeSource(directory, snapshot, observation, Files.size(snapshot), credential, owned);
            retained = true;
            return source;
        } catch (DocumentFailure failure) {
            IOException mapped = new IOException(failure.getCode().name() + ": " + failure.getDiagnostic(), failure);
            primaryFailure = mapped;
            throw mapped;
        } catch (IOException failure) {
            IOException mapped = failure.getMessage() != null && failure.getMessage().startsWith("CREDENTIAL_REJECTED:")
                    ? failure : readFailure();
            primaryFailure = mapped;
            throw mapped;
        } catch (RuntimeException | Error failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            if (encoded != null) { Arrays.fill(encoded, (byte) 0); }
            if (!retained) {
                if (owned && credential != null) { credential.close(); }
                cleanup(snapshot, directory, primaryFailure);
            }
        }
    }

    DocumentSource documentSource() {
        DocumentSource source = DocumentSource.path(path);
        return credential == null ? source : source.withCredential(credential);
    }

    private static Observation observe(DocumentSource source, PasswordCredential credential, long reserved)
            throws DocumentFailure {
        return new DocumentWorkflow().execute(WorkflowRequest.builder()
                .source("source", credential == null ? source : source.withCredential(credential))
                .primarySource("source").saveMode(SaveMode.REWRITE).resourcePolicy(policyWithSnapshot(reserved)).build(),
                session -> new Observation(session.query(PageCount.INSTANCE).intValue(),
                        session.query(DocumentSecurity.INSTANCE), session.query(DocumentVersion.INSTANCE))).getResult();
    }

    private static final class Observation {
        final int pages;
        final PasswordSecurityInfo security;
        final PdfVersionInfo version;
        Observation(int pages, PasswordSecurityInfo security, PdfVersionInfo version) {
            this.pages = pages; this.security = security; this.version = version;
        }
    }

    static WorkflowResourcePolicy policyWithSnapshot(long reserved) {
        return WorkflowResourcePolicy.builder()
                .maximumInputBytes(DEFAULTS.getMaximumInputBytes())
                .maximumPages(DEFAULTS.getMaximumPages())
                .maximumObjects(DEFAULTS.getMaximumObjects())
                .maximumNestingDepth(DEFAULTS.getMaximumNestingDepth())
                .maximumDecompressedBytes(DEFAULTS.getMaximumDecompressedBytes())
                .maximumDecodedPixels(DEFAULTS.getMaximumDecodedPixels())
                .maximumOwnedMemoryBytes(DEFAULTS.getMaximumOwnedMemoryBytes())
                .maximumTemporaryStorageBytes(DEFAULTS.getMaximumTemporaryStorageBytes() - reserved)
                .maximumElapsedTime(DEFAULTS.getMaximumElapsedTime())
                .maximumConcurrentWorkflows(DEFAULTS.getMaximumConcurrentWorkflows()).build();
    }

    @Override
    public void close() throws IOException {
        try { cleanup(path, directory, null); }
        finally { if (ownsCredential) { credential.close(); } }
    }

    private static IOException readFailure() {
        return new IOException("SOURCE_READ_FAILED: The source could not be opened as a PDF document.");
    }

    private static void cleanup(Path snapshot, Path directory, Throwable failure) throws IOException {
        IOException first = null;
        for (Path owned : new Path[] {snapshot, directory}) {
            if (owned == null) {
                continue;
            }
            try {
                Files.deleteIfExists(owned);
            } catch (IOException closingFailure) {
                IOException safe = new IOException(
                        "RESOURCE_CLOSE_FAILED: A library-owned document resource could not be closed cleanly.");
                if (failure != null) {
                    failure.addSuppressed(safe);
                } else if (first == null) {
                    first = safe;
                } else {
                    first.addSuppressed(safe);
                }
            }
        }
        if (first != null) {
            throw first;
        }
    }
}
