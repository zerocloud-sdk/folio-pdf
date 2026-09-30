package net.zerocloud.pdf.acceptance;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import net.zerocloud.pdf.CredentialAuthority;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentPermissions;
import net.zerocloud.pdf.DocumentSession;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.LegacySecurityMode;
import net.zerocloud.pdf.PasswordCredential;
import net.zerocloud.pdf.PasswordEncryptionAlgorithm;
import net.zerocloud.pdf.PasswordEncryptionScope;
import net.zerocloud.pdf.PasswordSecurityInfo;
import net.zerocloud.pdf.PasswordSecurityPolicy;
import net.zerocloud.pdf.PdfOutputPolicy;
import net.zerocloud.pdf.PdfVersion;
import net.zerocloud.pdf.PublicationReceipt;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfDocument;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfReader;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfWriter;
import net.zerocloud.pdf.itext7.kernel.pdf.ReaderProperties;
import net.zerocloud.pdf.itext7.kernel.pdf.StampingProperties;
import net.zerocloud.pdf.itext7.kernel.pdf.WriterProperties;
import net.zerocloud.pdf.query.DocumentSecurity;
import net.zerocloud.pdf.query.DocumentVersion;
import net.zerocloud.pdf.query.PageCount;

/** Original public products, kept separate from independent acceptance observations. */
final class PasswordProducts {
    private PasswordProducts() { }

    static RetainedEvidence create(Path root, Path output, WorkflowExecutionProfile execution, boolean includeScope) throws Exception {
        Path corpus = root.resolve("capabilities/profiles/" + (includeScope ? "T79-clear-metadata" : "T78-password"));
        Properties definitions = new Properties();
        try (InputStream input = Files.newInputStream(corpus.resolve("products.properties"))) { definitions.load(input); }
        RetainedEvidence retained = new RetainedEvidence(output);
        for (String api : new String[] {"native", "facade"}) {
            for (String name : definitions.getProperty("cases").split(",")) {
                Product product = new Product(definitions, name, includeScope);
                Path source = corpus.resolve(product.source);
                String sourceHash = EvidenceFiles.sha256(source);
                Path directory = Files.createDirectory(output.resolve(api + "-" + name));
                Path pdf = directory.resolve("product.pdf");
                List<PublicationReceipt> receipts;
                WorkflowExecutionProfile actual;
                Properties reopened;
                if ("native".equals(api)) {
                    try (PasswordCredential owner = PasswordCredential.of(product.owner().toCharArray());
                            PasswordCredential user = PasswordCredential.of(product.user().toCharArray());
                            PasswordCredential opening = PasswordCredential.of((product.incremental ? "baseline-user" : "baseline-owner").toCharArray())) {
                        WorkflowRequest.Builder request = WorkflowRequest.builder().executionProfile(execution)
                                .source("source", product.protectedSource() ? DocumentSource.path(source).withCredential(opening)
                                        : DocumentSource.path(source)).primarySource("source").saveMode(product.mode())
                                .target("target", PublicationTarget.path(pdf));
                        if (!product.incremental) {
                            PdfOutputPolicy policy = PdfOutputPolicy.version(product.version());
                            if (product.protectedOutput()) {
                                policy = policy.withPasswordSecurity(PasswordSecurityPolicy.builder(owner, user)
                                        .algorithm(product.algorithm()).permissions(permissions(product.mask))
                                        .encryptionScope(product.scope()).build());
                            }
                            if (product.explicit || product.protectedOutput()) { request.outputPolicy(policy); }
                            if (product.legacy()) { request.legacySecurityMode(LegacySecurityMode.ALLOW_OBSOLETE_PASSWORD_ENCRYPTION); }
                        }
                        WorkflowOutcome<Void> result = new DocumentWorkflow().execute(request.build(), session -> {
                            if (product.incremental) { session.execute(AddBlankPage.INSTANCE); }
                            return null;
                        });
                        actual = result.getExecutionProfile();
                        require(actual == execution && result.getSaveMode() == product.mode(), "Native publication mode changed.");
                        receipts = result.getPublicationReceipts();
                        reopened = new DocumentWorkflow().execute(WorkflowRequest.builder().executionProfile(execution)
                                .source("source", product.protectedOutput() ? DocumentSource.path(pdf).withCredential(user) : DocumentSource.path(pdf))
                                .primarySource("source").saveMode(SaveMode.REWRITE).build(), session -> observe(session, includeScope)).getResult();
                        if (product.protectedOutput() && !"empty-owner".equals(product.credential)) {
                            PasswordSecurityInfo proof = new DocumentWorkflow().execute(WorkflowRequest.builder().executionProfile(execution)
                                    .source("source", DocumentSource.path(pdf).withCredential(owner)).primarySource("source")
                                    .saveMode(SaveMode.REWRITE).build(), session -> session.query(DocumentSecurity.INSTANCE)).getResult();
                            require(proof.getCredentialAuthority() == CredentialAuthority.OWNER, "Owner authentication was not independently proved by the Native contract.");
                        }
                    }
                } else {
                    PdfDocument document;
                    try (ReaderProperties reading = new ReaderProperties(); WriterProperties writing = new WriterProperties()) {
                        if (product.protectedSource()) {
                            reading.setPassword((product.incremental ? "baseline-user" : "baseline-owner").getBytes(StandardCharsets.UTF_8));
                        }
                        if (!product.incremental) {
                            if (product.explicit) { writing.setPdfVersion(net.zerocloud.pdf.itext7.kernel.pdf.PdfVersion.fromString("PDF-" + product.version)); }
                            if (product.protectedOutput()) {
                                writing.setStandardEncryption(product.bytes(false), product.bytes(true), product.mask, product.selector());
                            }
                            if (product.legacy()) { writing.setLegacySecurityMode(LegacySecurityMode.ALLOW_OBSOLETE_PASSWORD_ENCRYPTION); }
                        }
                        try (PdfReader reader = new PdfReader(source.toString(), reading)) {
                            document = new PdfDocument(reader, new PdfWriter(pdf.toString(), writing),
                                    product.incremental ? new StampingProperties().useAppendMode() : new StampingProperties());
                            try (PdfDocument opened = document) {
                                if (product.incremental) { opened.addNewPage(); }
                            }
                        }
                    }
                    actual = WorkflowExecutionProfile.IN_PROCESS;
                    receipts = document.getPublicationReceipts();
                    try (ReaderProperties reading = new ReaderProperties()) {
                        if (product.protectedOutput()) { reading.setPassword(product.bytes(false)); }
                        try (PdfReader reader = new PdfReader(pdf.toString(), reading); PdfDocument opened = new PdfDocument(reader)) {
                            reopened = new Properties();
                            reopened.setProperty("version", opened.getPdfVersion().toPdfName().getValue());
                            reopened.setProperty("pages", Integer.toString(opened.getNumberOfPages()));
                            reopened.setProperty("encrypted", Boolean.toString(reader.isEncrypted()));
                            reopened.setProperty("mask", Integer.toString((int) reader.getPermissions()));
                            reopened.setProperty("owner", Boolean.toString(reader.isOpenedWithFullPermission()));
                            reopened.setProperty("algorithm", reader.isEncrypted() ? product.algorithmName : "NONE");
                            if (includeScope) {
                                int mode = reader.getCryptoMode();
                                reopened.setProperty("algorithm", PasswordEncryptionAlgorithm.values()[3 - (mode & 3)].name());
                                reopened.setProperty("crypto-mode", Integer.toString(mode));
                                reopened.setProperty("scope", (mode & 8) == 0 ? "ALL_CONTENT" : "ALL_EXCEPT_METADATA");
                                reopened.setProperty("revision", "not-exposed");
                            }
                        }
                    }
                    if (product.protectedOutput() && !"empty-owner".equals(product.credential)) {
                        try (ReaderProperties reading = new ReaderProperties().setPassword(product.bytes(true));
                                PdfReader owner = new PdfReader(pdf.toString(), reading)) {
                            require(owner.isOpenedWithFullPermission(), "Facade owner proof was missing.");
                        }
                    }
                }
                Properties expected = product.expected("native".equals(api));
                require(reopened.equals(expected), "A reopened public product differs from the frozen expectation.");
                require(sourceHash.equals(EvidenceFiles.sha256(source)), "An original Source changed.");
                if (product.incremental) {
                    byte[] before = Files.readAllBytes(source), after = Files.readAllBytes(pdf);
                    require(after.length > before.length && Arrays.equals(before, Arrays.copyOf(after, before.length)), "Encrypted incremental publication lost the original prefix.");
                }
                require(receipts.size() == 1, "Unexpected publication receipt count.");
                PublicationReceipt receipt = receipts.get(0);
                require("target".equals(receipt.getTargetName()) && receipt.getStatus() == PublicationStatus.COMMITTED
                        && !receipt.isPartialOutputPossible(), "A public product has an incomplete publication receipt.");
                Properties publication = new Properties();
                publication.setProperty("execution-profile", actual.name());
                publication.setProperty("save-mode", product.mode().name());
                publication.setProperty("status", "COMMITTED");
                publication.setProperty("target-name", "target");
                publication.setProperty("partial-output-possible", "false");
                publication.setProperty("source-sha256", sourceHash);
                publication.setProperty("output-sha256", EvidenceFiles.sha256(pdf));
                publication.setProperty("source-preserved", "pass");
                publication.setProperty("reopened", "pass");
                retained.retain(pdf, publication.getProperty("output-sha256"));
                retained.write(directory.resolve("publication.properties"), publication);
                retained.write(directory.resolve("reopened.properties"), reopened);
            }
        }
        Properties phase = new Properties();
        phase.setProperty("phase", "products-only");
        phase.setProperty("native-execution-profile", execution.name());
        phase.setProperty("facade-execution-profile", "IN_PROCESS");
        retained.write(output.resolve("products.properties"), phase);
        retained.verify();
        return retained;
    }

    private static Properties observe(DocumentSession session, boolean includeScope) throws DocumentFailure {
        PasswordSecurityInfo security = session.query(DocumentSecurity.INSTANCE);
        Properties values = new Properties();
        values.setProperty("version", session.query(DocumentVersion.INSTANCE).getEffectiveVersion().toString());
        values.setProperty("pages", session.query(PageCount.INSTANCE).toString());
        values.setProperty("encrypted", Boolean.toString(security.isPasswordProtected()));
        values.setProperty("mask", Integer.toString(security.isPasswordProtected() ? security.getDeclaredUserPermissions().getStandardMask() : 0));
        values.setProperty("owner", Boolean.toString(!security.isPasswordProtected() || security.getCredentialAuthority() == CredentialAuthority.OWNER));
        values.setProperty("algorithm", security.getAlgorithm().isPresent() ? security.getAlgorithm().get().name() : "NONE");
        if (includeScope) {
            values.setProperty("scope", security.getEncryptionScope().name());
            values.setProperty("revision", Integer.toString(security.getSecurityHandlerRevision()));
            values.setProperty("crypto-mode", Integer.toString(3 - security.getAlgorithm().get().ordinal()
                    | (security.getEncryptionScope() == PasswordEncryptionScope.ALL_EXCEPT_METADATA ? 8 : 0)));
        }
        return values;
    }

    private static DocumentPermissions permissions(int mask) {
        return DocumentPermissions.builder().allowPrinting((mask & 4) != 0).allowModification((mask & 8) != 0)
                .allowContentExtraction((mask & 16) != 0).allowAnnotationModification((mask & 32) != 0)
                .allowFormFilling((mask & 256) != 0).allowAccessibilityExtraction((mask & 512) != 0)
                .allowDocumentAssembly((mask & 1024) != 0).allowFaithfulPrinting((mask & 2048) != 0).build();
    }

    private static void require(boolean condition, String message) throws IOException { if (!condition) { throw new IOException(message); } }

    private static final class Product {
        final String algorithmName, version, credential, source;
        final int mask, pages, revision;
        final boolean incremental, explicit, metadata, includeScope;
        Product(Properties definitions, String name, boolean includeScope) {
            this.includeScope = includeScope;
            algorithmName = definitions.getProperty(name + ".algorithm");
            version = definitions.getProperty(name + ".version");
            credential = definitions.getProperty(name + ".credential");
            source = definitions.getProperty(name + ".source");
            mask = Integer.parseInt(definitions.getProperty(name + ".mask"));
            pages = Integer.parseInt(definitions.getProperty(name + ".pages"));
            incremental = "INCREMENTAL".equals(definitions.getProperty(name + ".mode"));
            explicit = Boolean.parseBoolean(definitions.getProperty(name + ".explicit"));
            metadata = Boolean.parseBoolean(definitions.getProperty(name + ".metadata", "true"));
            revision = Integer.parseInt(definitions.getProperty(name + ".revision"));
        }
        boolean protectedOutput() { return !"NONE".equals(algorithmName); }
        boolean protectedSource() { return !source.startsWith("version-") && !source.startsWith("plain"); }
        boolean legacy() { return protectedOutput() && !"AES_256".equals(algorithmName); }
        PasswordEncryptionAlgorithm algorithm() { return PasswordEncryptionAlgorithm.valueOf(algorithmName); }
        int selector() { return 3 - algorithm().ordinal() | (metadata ? 0 : 8); }
        PasswordEncryptionScope scope() { return metadata ? PasswordEncryptionScope.ALL_CONTENT : PasswordEncryptionScope.ALL_EXCEPT_METADATA; }
        PdfVersion version() { return "2.0".equals(version) ? PdfVersion.PDF_2_0 : PdfVersion.PDF_1_7; }
        SaveMode mode() { return incremental ? SaveMode.INCREMENTAL : SaveMode.REWRITE; }
        String owner() { return "empty-owner".equals(credential) ? "" : "equal".equals(credential) ? user() : "baseline-owner"; }
        String user() {
            if ("empty-user".equals(credential)) { return ""; }
            if ("equal".equals(credential)) { return "equal-baseline"; }
            if ("unicode".equals(credential)) { return "I\u00adX\u00a0\u2168"; }
            if ("unicode-127".equals(credential)) { return repeat('a', 127); }
            if ("unicode-split".equals(credential)) { return repeat('a', 126) + "\u00e9"; }
            if ("bidi".equals(credential)) { return "\u05d0\u05d1"; }
            if ("long".equals(credential)) { return repeat('a', 128); }
            if ("legacy-32".equals(credential)) { return repeat('\u00e9', 33); }
            return "baseline-user";
        }
        byte[] bytes(boolean owner) { return (owner ? owner() : user()).getBytes(legacy() ? StandardCharsets.ISO_8859_1 : StandardCharsets.UTF_8); }
        Properties expected(boolean nativeApi) {
            Properties values = new Properties();
            values.setProperty("version", version); values.setProperty("pages", Integer.toString(pages));
            values.setProperty("encrypted", Boolean.toString(protectedOutput())); values.setProperty("mask", Integer.toString(mask));
            values.setProperty("owner", Boolean.toString(!protectedOutput() || "equal".equals(credential)));
            values.setProperty("algorithm", algorithmName);
            if (includeScope) {
                values.setProperty("scope", scope().name());
                values.setProperty("crypto-mode", Integer.toString(selector()));
                values.setProperty("revision", nativeApi ? Integer.toString(revision) : "not-exposed");
            }
            return values;
        }
        private static String repeat(char value, int count) { char[] chars = new char[count]; Arrays.fill(chars, value); return new String(chars); }
    }
}
