package net.zerocloud.pdf.acceptance;

import static net.zerocloud.pdf.acceptance.EvidenceFiles.fencedEnding;
import static net.zerocloud.pdf.acceptance.EvidenceFiles.finalFencedEnding;
import static net.zerocloud.pdf.acceptance.EvidenceFiles.metadata;
import static net.zerocloud.pdf.acceptance.EvidenceFiles.sha256;
import static net.zerocloud.pdf.acceptance.EvidenceFiles.write;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.zerocloud.pdf.Annotation;
import net.zerocloud.pdf.AnnotationAppearance;
import net.zerocloud.pdf.AnnotationColor;
import net.zerocloud.pdf.AnnotationProperties;
import net.zerocloud.pdf.AnnotationQuad;
import net.zerocloud.pdf.AnnotationRectangle;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.EmbeddedFile;
import net.zerocloud.pdf.GoToAction;
import net.zerocloud.pdf.LinkActivation;
import net.zerocloud.pdf.NavigationTarget;
import net.zerocloud.pdf.OutlineItem;
import net.zerocloud.pdf.PageDestination;
import net.zerocloud.pdf.PageRange;
import net.zerocloud.pdf.PdfString;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.command.CopyPages;
import net.zerocloud.pdf.command.EmbedFile;
import net.zerocloud.pdf.command.FlattenAnnotations;
import net.zerocloud.pdf.command.InsertBlankPage;
import net.zerocloud.pdf.command.MergeDocuments;
import net.zerocloud.pdf.command.MovePages;
import net.zerocloud.pdf.command.RemovePages;
import net.zerocloud.pdf.command.ReplaceOutlineTree;
import net.zerocloud.pdf.command.SetNamedDestinations;
import net.zerocloud.pdf.command.SetXmpMetadata;
import net.zerocloud.pdf.command.SplitDocument;
import net.zerocloud.pdf.command.UpdateDocumentInfo;
import net.zerocloud.pdf.command.UpdateActions;
import net.zerocloud.pdf.command.UpdateAnnotations;

/**
 * Repository-only command that records T06/T07 evidence and the T10, T11,
 * and T12 syntax chains.
 */
public final class AcceptanceEvidenceCommand {

    private static final String CAPABILITY = "document.blank.create-publish-reopen";
    private static final String ACCEPTANCE_PROFILE = "T03-document-workflow-transaction";
    private static final String PROFILE_RECORD =
            "capabilities/evidence/T03-document-workflow-transaction.md";
    private static final String ARTIFACT_NAME = "T06-document-blank-output.pdf";
    private static final String QPDF_FINDINGS_NAME = "T06-document-blank-qpdf.txt";
    private static final String SEMANTIC_FINDINGS_NAME = "T06-document-blank-semantic.txt";
    private static final String T10_CAPABILITY =
            "document.page.manipulate-merge-split";
    private static final String T10_ACCEPTANCE_PROFILE =
            "T10-page-manipulation-merge-split";
    private static final String T10_PROFILE_RECORD =
            "capabilities/evidence/T10-page-manipulation-merge-split.md";
    private static final String T10_FRONT_ARTIFACT =
            "T10-page-manipulation-front.pdf";
    private static final String T10_BACK_ARTIFACT =
            "T10-page-manipulation-back.pdf";
    private static final String T10_QPDF_FINDINGS =
            "T10-page-manipulation-merge-split-qpdf.txt";
    private static final String T11_CAPABILITY =
            "document.metadata.outlines-destinations-attachments";
    private static final String T11_ACCEPTANCE_PROFILE =
            "T11-metadata-outlines-destinations-attachments";
    private static final String T11_PROFILE_RECORD =
            "capabilities/evidence/T11-metadata-outlines-destinations-attachments.md";
    private static final String T11_FRONT_ARTIFACT =
            "T11-metadata-front.pdf";
    private static final String T11_BACK_ARTIFACT =
            "T11-metadata-back.pdf";
    private static final String T11_QPDF_FINDINGS =
            "T11-metadata-outlines-destinations-attachments-qpdf.txt";
    private static final String T12_CAPABILITY =
            "document.annotations-actions.manage";
    private static final String T12_ACCEPTANCE_PROFILE =
            "T12-annotations-document-actions";
    private static final String T12_PROFILE_RECORD =
            "capabilities/evidence/T12-annotations-document-actions.md";
    private static final String T12_FRONT_ARTIFACT =
            "T12-annotations-actions-front.pdf";
    private static final String T12_BACK_ARTIFACT =
            "T12-annotations-actions-back.pdf";
    private static final String T12_QPDF_FINDINGS =
            "T12-annotations-document-actions-qpdf.txt";

    private AcceptanceEvidenceCommand() {
    }

    /**
     * Runs the built-in T03 Acceptance Profile and the T10 through T12 syntax
     * chains.
     *
     * @param arguments output directory, pinned tool and profile authorities,
     *        and Release Train
     * @throws Exception if the evidence run cannot be completed
     */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 6) {
            throw new IllegalArgumentException(
                    "Usage: AcceptanceEvidenceCommand <output-directory> "
                            + "<qpdf-pin> <pdfium-pin> <imagemagick-pin> "
                            + "<visual-profile> <release-train>");
        }

        Path output = Paths.get(arguments[0]).toAbsolutePath().normalize();
        QpdfPin qpdfPin = QpdfPin.load(
                Paths.get(arguments[1]).toAbsolutePath().normalize());
        PdfiumPin pdfiumPin = PdfiumPin.load(
                Paths.get(arguments[2]).toAbsolutePath().normalize());
        ImageMagickPin imageMagickPin = ImageMagickPin.load(
                Paths.get(arguments[3]).toAbsolutePath().normalize());
        VisualProfile visualProfile = VisualProfile.load(
                Paths.get(arguments[4]).toAbsolutePath().normalize());
        if (!ACCEPTANCE_PROFILE.equals(visualProfile.profileId())) {
            throw new IllegalArgumentException(
                    "Visual profile does not describe " + ACCEPTANCE_PROFILE);
        }
        String releaseTrain = arguments[5];
        Path artifacts = output.resolve("artifacts");
        Files.createDirectories(artifacts);
        Path pdf = artifacts.resolve(ARTIFACT_NAME);

        WorkflowOutcome<Void> creation = new DocumentWorkflow().execute(
                WorkflowRequest.create(pdf, SaveMode.REWRITE),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    return null;
                });
        String inputHash = EvidenceFiles.idNeutralPdfSha256(pdf);

        EvidenceResult syntaxResult;
        String observedVersion;
        String syntaxFinding;
        try {
            ProcessResult version = ExternalProcess.run(
                    qpdfPin.executable(), output, "--version");
            observedVersion = qpdfVersion(version.combinedOutput());
            if (version.exitCode != 0 || !qpdfPin.version().equals(observedVersion)) {
                syntaxResult = EvidenceResult.INDETERMINATE;
                syntaxFinding = "Expected pinned qpdf version `" + qpdfPin.version()
                        + "`; observed `" + observedVersion + "`.";
                write(artifacts.resolve(QPDF_FINDINGS_NAME),
                        unpinnedQpdfFindings(inputHash, observedVersion, qpdfPin));
            } else {
                ProcessResult syntax = ExternalProcess.run(
                        qpdfPin.executable(),
                        artifacts,
                        "--check",
                        ARTIFACT_NAME);
                syntaxResult = syntax.exitCode == 0
                        ? EvidenceResult.PASS
                        : syntax.exitCode == 2 || syntax.exitCode == 3
                                ? EvidenceResult.FAIL : EvidenceResult.INDETERMINATE;
                if (syntax.exitCode == 3) {
                    syntaxFinding = "qpdf reported warnings (exit code `3`).";
                } else if (syntax.exitCode == 2) {
                    syntaxFinding = "qpdf reported errors (exit code `2`).";
                } else if (syntax.exitCode != 0) {
                    syntaxFinding = "qpdf did not return a documented inspection status "
                            + "(exit code `" + syntax.exitCode + "`).";
                } else {
                    syntaxFinding = "qpdf completed `--check` with exit code `"
                            + syntax.exitCode + "`.";
                }
                write(artifacts.resolve(QPDF_FINDINGS_NAME),
                        qpdfFindings(syntax, inputHash, qpdfPin));
            }
        } catch (IOException unavailable) {
            syntaxResult = EvidenceResult.INDETERMINATE;
            observedVersion = "unavailable";
            syntaxFinding = "The pinned qpdf tool was unavailable.";
            write(artifacts.resolve(QPDF_FINDINGS_NAME),
                    unavailableQpdfFindings(inputHash, qpdfPin));
        }

        SemanticObservation semantic = SemanticAssertions.inspect(creation, pdf);
        write(artifacts.resolve(SEMANTIC_FINDINGS_NAME),
                semantic.findings(inputHash, releaseTrain));

        VisualEvidence visual = VisualEvidenceRecorder.record(
                pdf,
                inputHash,
                artifacts,
                pdfiumPin,
                imageMagickPin,
                visualProfile,
                releaseTrain);
        write(artifacts.resolve(VisualEvidenceRecorder.FINDINGS_NAME),
                visual.rawFindings());
        write(output.resolve(VisualEvidenceRecorder.RECORD_NAME),
                visual.record());

        EvidenceResult profileDetermination = syntaxResult == EvidenceResult.FAIL
                || semantic.result() == EvidenceResult.FAIL
                || visual.result() == EvidenceResult.FAIL
                        ? EvidenceResult.FAIL : EvidenceResult.INDETERMINATE;

        write(output.resolve("T06-document-blank-syntax.md"),
                syntaxRecord(inputHash, releaseTrain, observedVersion,
                        syntaxResult, syntaxFinding, qpdfPin));
        write(output.resolve("T06-document-blank-semantic.md"),
                semanticRecord(inputHash, releaseTrain, semantic));
        write(output.resolve("T06-document-blank-determination.md"),
                determinationRecord(inputHash, releaseTrain, syntaxResult,
                        semantic.result(), visual.result(), profileDetermination));

        EvidenceResult t10Syntax = recordProductSyntax(
                new ProductChain(
                        "T10",
                        T10_CAPABILITY,
                        T10_ACCEPTANCE_PROFILE,
                        T10_PROFILE_RECORD,
                        T10_FRONT_ARTIFACT,
                        T10_BACK_ARTIFACT,
                        T10_QPDF_FINDINGS,
                        "T10-page-manipulation-merge-split-syntax.md"),
                new ProductCreator() {
                    @Override
                    public void create(Path front, Path back) throws Exception {
                        createT10Products(front, back);
                    }
                },
                output,
                artifacts,
                qpdfPin,
                releaseTrain);

        EvidenceResult t11Syntax = recordProductSyntax(
                new ProductChain(
                        "T11",
                        T11_CAPABILITY,
                        T11_ACCEPTANCE_PROFILE,
                        T11_PROFILE_RECORD,
                        T11_FRONT_ARTIFACT,
                        T11_BACK_ARTIFACT,
                        T11_QPDF_FINDINGS,
                        "T11-metadata-outlines-destinations-attachments-syntax.md"),
                new ProductCreator() {
                    @Override
                    public void create(Path front, Path back) throws Exception {
                        createT11Products(front, back);
                    }
                },
                output,
                artifacts,
                qpdfPin,
                releaseTrain);

        EvidenceResult t12Syntax = recordProductSyntax(
                new ProductChain(
                        "T12",
                        T12_CAPABILITY,
                        T12_ACCEPTANCE_PROFILE,
                        T12_PROFILE_RECORD,
                        T12_FRONT_ARTIFACT,
                        T12_BACK_ARTIFACT,
                        T12_QPDF_FINDINGS,
                        "T12-annotations-document-actions-syntax.md"),
                new ProductCreator() {
                    @Override
                    public void create(Path front, Path back) throws Exception {
                        createT12Products(front, back);
                    }
                },
                output,
                artifacts,
                qpdfPin,
                releaseTrain);

        System.out.println("Acceptance Profile determination: "
                + profileDetermination.recordValue());
        System.out.println("T07 visual chain: " + visual.result().recordValue());
        System.out.println("T10 syntax chain: " + t10Syntax.recordValue());
        System.out.println("T11 syntax chain: " + t11Syntax.recordValue());
        System.out.println("T12 syntax chain: " + t12Syntax.recordValue());
    }

    private interface ProductCreator {

        void create(Path front, Path back) throws Exception;
    }

    private static final class ProductChain {

        private final String label;
        private final String capability;
        private final String acceptanceProfile;
        private final String profileRecord;
        private final String frontArtifact;
        private final String backArtifact;
        private final String qpdfFindings;
        private final String syntaxRecord;

        ProductChain(
                String label,
                String capability,
                String acceptanceProfile,
                String profileRecord,
                String frontArtifact,
                String backArtifact,
                String qpdfFindings,
                String syntaxRecord) {
            this.label = label;
            this.capability = capability;
            this.acceptanceProfile = acceptanceProfile;
            this.profileRecord = profileRecord;
            this.frontArtifact = frontArtifact;
            this.backArtifact = backArtifact;
            this.qpdfFindings = qpdfFindings;
            this.syntaxRecord = syntaxRecord;
        }
    }

    private static EvidenceResult recordProductSyntax(
            ProductChain chain,
            ProductCreator creator,
            Path output,
            Path artifacts,
            QpdfPin qpdfPin,
            String releaseTrain) throws Exception {
        Path front = artifacts.resolve(chain.frontArtifact);
        Path back = artifacts.resolve(chain.backArtifact);
        creator.create(front, back);
        String frontHash = sha256(front);
        String backHash = sha256(back);
        String inputSetHash = sha256(frontHash + "\n" + backHash);

        EvidenceResult result;
        String observedVersion;
        String finding;
        String findings;
        try {
            ProcessResult version = ExternalProcess.run(
                    qpdfPin.executable(), output, "--version");
            observedVersion = qpdfVersion(version.combinedOutput());
            if (version.exitCode != 0
                    || !qpdfPin.version().equals(observedVersion)) {
                result = EvidenceResult.INDETERMINATE;
                finding = "Expected pinned qpdf version `" + qpdfPin.version()
                        + "`; observed `" + observedVersion + "`.";
                findings = productIndeterminateToolFindings(
                        chain,
                        inputSetHash,
                        observedVersion,
                        finding,
                        qpdfPin);
            } else {
                List<ProductQpdfResult> checks =
                        new ArrayList<ProductQpdfResult>(2);
                checks.add(new ProductQpdfResult(
                        chain.frontArtifact,
                        frontHash,
                        ExternalProcess.run(
                                qpdfPin.executable(),
                                artifacts,
                                "--check",
                                chain.frontArtifact)));
                checks.add(new ProductQpdfResult(
                        chain.backArtifact,
                        backHash,
                        ExternalProcess.run(
                                qpdfPin.executable(),
                                artifacts,
                                "--check",
                                chain.backArtifact)));
                result = aggregateQpdfResults(checks);
                finding = productSyntaxFinding(chain, result);
                findings = productQpdfFindings(
                        chain,
                        inputSetHash,
                        checks,
                        result,
                        qpdfPin);
            }
        } catch (IOException unavailable) {
            result = EvidenceResult.INDETERMINATE;
            observedVersion = "unavailable";
            finding = "The pinned qpdf tool was unavailable.";
            findings = productIndeterminateToolFindings(
                    chain,
                    inputSetHash,
                    observedVersion,
                    finding,
                    qpdfPin);
        }

        write(artifacts.resolve(chain.qpdfFindings), findings);
        write(output.resolve(chain.syntaxRecord),
                productSyntaxRecord(
                        chain,
                        inputSetHash,
                        releaseTrain,
                        observedVersion,
                        result,
                        finding,
                        qpdfPin));
        return result;
    }

    private static void createT10Products(Path front, Path back)
            throws Exception {
        byte[] primary = blankDocument(3);
        byte[] appendix = blankDocument(1);
        WorkflowRequest request = WorkflowRequest.builder()
                .source(
                        "primary",
                        DocumentSource.bytes(primary, primary.length))
                .source(
                        "appendix",
                        DocumentSource.bytes(appendix, appendix.length))
                .primarySource("primary")
                .target("front", PublicationTarget.path(front))
                .target("back", PublicationTarget.path(back))
                .saveMode(SaveMode.REWRITE)
                .build();
        new DocumentWorkflow().execute(request, session -> {
            session.execute(InsertBlankPage.version1(2));
            session.execute(RemovePages.version1(PageRange.of(4, 4)));
            session.execute(MovePages.version1(PageRange.of(1, 1), 3));
            session.execute(CopyPages.version1(PageRange.of(1, 1), 4));
            session.execute(MergeDocuments.version1("appendix"));
            session.execute(SplitDocument.version1()
                    .target("front", PageRange.of(1, 2))
                    .target("back", PageRange.of(3, 5))
                    .build());
            return null;
        });
    }

    private static byte[] blankDocument(int pageCount) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        WorkflowRequest request = WorkflowRequest.builder()
                .target("document", PublicationTarget.stream(output))
                .saveMode(SaveMode.REWRITE)
                .build();
        new DocumentWorkflow().execute(request, session -> {
            for (int page = 0; page < pageCount; page++) {
                session.execute(AddBlankPage.INSTANCE);
            }
            return null;
        });
        return output.toByteArray();
    }

    private static void createT11Products(Path front, Path back)
            throws Exception {
        byte[] primary = t11PrimarySource();
        byte[] appendix = t11AppendixSource();
        WorkflowRequest request = WorkflowRequest.builder()
                .source(
                        "primary",
                        DocumentSource.bytes(primary, primary.length))
                .source(
                        "appendix",
                        DocumentSource.bytes(appendix, appendix.length))
                .primarySource("primary")
                .target("front", PublicationTarget.path(front))
                .target("back", PublicationTarget.path(back))
                .saveMode(SaveMode.REWRITE)
                .build();
        new DocumentWorkflow().execute(request, session -> {
            session.execute(MergeDocuments.version1("appendix"));
            session.execute(SplitDocument.version1()
                    .target("front", PageRange.of(1, 2))
                    .target("back", PageRange.of(3, 5))
                    .build());
            return null;
        });
    }

    private static byte[] t11PrimarySource() throws Exception {
        byte[] blank = blankDocument(3);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        WorkflowRequest request = WorkflowRequest.builder()
                .source("input", DocumentSource.bytes(blank, blank.length))
                .primarySource("input")
                .target("output", PublicationTarget.stream(output))
                .saveMode(SaveMode.REWRITE)
                .build();
        new DocumentWorkflow().execute(request, session -> {
            session.execute(SetNamedDestinations.version1()
                    .set("chapter-one", PageDestination.fit(1))
                    .set("chapter-two", PageDestination.fit(2))
                    .set("chapter-three", PageDestination.fit(3))
                    .build());
            session.execute(UpdateDocumentInfo.version1()
                    .set("Author", PdfString.of(
                            "T11 Primary".getBytes(StandardCharsets.UTF_8)))
                    .build());
            session.execute(SetXmpMetadata.version1(
                    t11XmpPacket("t11-primary-xmp")));
            session.execute(EmbedFile.version1(EmbeddedFile.version1(
                    "notes.txt",
                    "primary-notes".getBytes(StandardCharsets.UTF_8))));
            session.execute(ReplaceOutlineTree.version1(Arrays.asList(
                    OutlineItem.toPage(
                            "Part One",
                            PageDestination.fit(1),
                            Arrays.<OutlineItem>asList()),
                    OutlineItem.toPage(
                            "Part Two",
                            PageDestination.fit(3),
                            Arrays.<OutlineItem>asList()))));
            return null;
        });
        return output.toByteArray();
    }

    private static byte[] t11AppendixSource() throws Exception {
        byte[] blank = blankDocument(2);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        WorkflowRequest request = WorkflowRequest.builder()
                .source("input", DocumentSource.bytes(blank, blank.length))
                .primarySource("input")
                .target("output", PublicationTarget.stream(output))
                .saveMode(SaveMode.REWRITE)
                .build();
        new DocumentWorkflow().execute(request, session -> {
            session.execute(SetNamedDestinations.version1()
                    .set("chapter-one", PageDestination.fit(1))
                    .set("appendix-local", PageDestination.fit(2))
                    .build());
            session.execute(UpdateDocumentInfo.version1()
                    .set("Author", PdfString.of(
                            "T11 Appendix".getBytes(StandardCharsets.UTF_8)))
                    .set("T11Note", PdfString.of(
                            "from appendix".getBytes(StandardCharsets.UTF_8)))
                    .build());
            session.execute(SetXmpMetadata.version1(
                    t11XmpPacket("t11-appendix-xmp")));
            session.execute(EmbedFile.version1(EmbeddedFile.version1(
                    "notes.txt",
                    "appendix-notes".getBytes(StandardCharsets.UTF_8))));
            session.execute(ReplaceOutlineTree.version1(Arrays.asList(
                    OutlineItem.toPage(
                            "Appendix",
                            PageDestination.fit(2),
                            Arrays.<OutlineItem>asList()))));
            return null;
        });
        return output.toByteArray();
    }

    private static byte[] t11XmpPacket(String marker) {
        return ("<?xpacket begin=\"﻿\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n"
                + "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">\n"
                + "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n"
                + "<!-- " + marker + " -->\n"
                + "</rdf:RDF>\n"
                + "</x:xmpmeta>\n"
                + "<?xpacket end=\"w\"?>").getBytes(StandardCharsets.UTF_8);
    }

    private static void createT12Products(Path front, Path back)
            throws Exception {
        byte[] source = blankDocument(4);
        WorkflowRequest request = WorkflowRequest.builder()
                .source("input", DocumentSource.bytes(source, source.length))
                .primarySource("input")
                .target("front", PublicationTarget.path(front))
                .target("back", PublicationTarget.path(back))
                .saveMode(SaveMode.REWRITE)
                .build();
        new DocumentWorkflow().execute(request, session -> {
            session.execute(SetNamedDestinations.version1()
                    .set("appendix", PageDestination.fit(4))
                    .build());
            session.execute(UpdateAnnotations.version1()
                    .put(Annotation.text(
                            AnnotationProperties.version1(
                                            "acceptance-note",
                                            1,
                                            AnnotationRectangle.of(
                                                    36L, 700L, 72L, 736L))
                                    .contents("T12 acceptance note")
                                    .appearance(t12Appearance(
                                            36L,
                                            36L,
                                            "q 1 1 0 rg 0 0 36 36 re f Q\n"))
                                    .build(),
                            Annotation.TextIcon.NOTE,
                            false))
                    .put(Annotation.link(
                            AnnotationProperties.version1(
                                            "acceptance-link",
                                            1,
                                            AnnotationRectangle.of(
                                                    90L, 700L, 250L, 724L))
                                    .appearance(t12Appearance(
                                            160L,
                                            24L,
                                            "q 0 0 1 RG 1 w 0 1 m 160 1 l S Q\n"))
                                    .build(),
                            LinkActivation.action(GoToAction.version1(
                                    NavigationTarget.toPage(
                                            PageDestination.fit(2))))))
                    .put(Annotation.stamp(
                            AnnotationProperties.version1(
                                            "acceptance-stamp",
                                            2,
                                            AnnotationRectangle.of(
                                                    36L, 640L, 156L, 680L))
                                    .appearance(t12Appearance(
                                            120L,
                                            40L,
                                            "q 0.8 0 0 RG 2 w 1 1 118 38 re S Q\n"))
                                    .build(),
                            "Approved"))
                    .put(Annotation.highlight(
                            AnnotationProperties.version1(
                                            "acceptance-highlight",
                                            2,
                                            AnnotationRectangle.of(
                                                    36L, 600L, 176L, 620L))
                                    .appearance(t12Appearance(
                                            140L,
                                            20L,
                                            "q 1 1 0 rg 0 0 140 20 re f Q\n"))
                                    .build(),
                            Arrays.asList(AnnotationQuad.of(
                                    36L, 620L, 176L, 620L,
                                    36L, 600L, 176L, 600L)),
                            AnnotationColor.rgb(
                                    BigDecimal.ONE,
                                    BigDecimal.ONE,
                                    BigDecimal.ZERO)))
                    .put(Annotation.fileAttachment(
                            AnnotationProperties.version1(
                                            "acceptance-attachment",
                                            3,
                                            AnnotationRectangle.of(
                                                    36L, 700L, 68L, 732L))
                                    .appearance(t12Appearance(
                                            32L,
                                            32L,
                                            "q 0 0 0 RG 1 w 2 2 28 28 re S Q\n"))
                                    .build(),
                            EmbeddedFile.version1(
                                    "acceptance.txt",
                                    "T12 attachment".getBytes(
                                            StandardCharsets.UTF_8),
                                    "text/plain",
                                    "T12 acceptance attachment",
                                    EmbeddedFile.Relationship.SUPPLEMENT),
                            Annotation.FileAttachmentIcon.PAPERCLIP))
                    .put(Annotation.widget(
                            AnnotationProperties.version1(
                                            "acceptance-widget",
                                            4,
                                            AnnotationRectangle.of(
                                                    36L, 650L, 220L, 686L))
                                    .appearance(t12Appearance(
                                            184L,
                                            36L,
                                            "q 0 0 0 RG 1 w 1 1 182 34 re S Q\n"))
                                    .build()))
                    .build());
            session.execute(UpdateActions.version1()
                    .setDocumentOpenAction(GoToAction.version1(
                            NavigationTarget.toPage(PageDestination.fit(2))))
                    .setPageOpenAction(1, GoToAction.version1(
                            NavigationTarget.toPage(PageDestination.fit(2))))
                    .setPageCloseAction(1, GoToAction.version1(
                            NavigationTarget.toNamedDestination("appendix")))
                    .build());
            session.execute(FlattenAnnotations.version1(
                    "acceptance-stamp"));
            session.execute(CopyPages.version1(PageRange.of(1, 2), 5));
            session.execute(SplitDocument.version1()
                    .target("front", PageRange.of(1, 3))
                    .target("back", PageRange.of(4, 6))
                    .build());
            return null;
        });
    }

    private static AnnotationAppearance t12Appearance(
            long width,
            long height,
            String operators) {
        return AnnotationAppearance.version1(
                AnnotationRectangle.of(0L, 0L, width, height),
                operators.getBytes(StandardCharsets.US_ASCII));
    }

    private static EvidenceResult aggregateQpdfResults(
            List<ProductQpdfResult> checks) {
        boolean indeterminate = false;
        for (ProductQpdfResult check : checks) {
            int exitCode = check.result.exitCode;
            if (exitCode == 2 || exitCode == 3) {
                return EvidenceResult.FAIL;
            }
            if (exitCode != 0) {
                indeterminate = true;
            }
        }
        return indeterminate
                ? EvidenceResult.INDETERMINATE
                : EvidenceResult.PASS;
    }

    private static String productSyntaxFinding(
            ProductChain chain,
            EvidenceResult result) {
        if (result == EvidenceResult.PASS) {
            return "qpdf completed `--check` for both " + chain.label
                    + " products with exit code `0`.";
        }
        if (result == EvidenceResult.FAIL) {
            return "qpdf reported warnings or errors for a " + chain.label
                    + " product.";
        }
        return "qpdf returned an undocumented status for a " + chain.label
                + " product.";
    }

    private static String productSyntaxRecord(
            ProductChain chain,
            String inputSetHash,
            String releaseTrain,
            String producerVersion,
            EvidenceResult result,
            String finding,
            QpdfPin qpdfPin) {
        return "# " + chain.label + " qpdf syntax evidence\n\n"
                + metadata("Capability", chain.capability)
                + metadata("Acceptance Profile", chain.acceptanceProfile)
                + metadata("Profile record", chain.profileRecord)
                + metadata("Release train", releaseTrain)
                + metadata("Chain", "syntax")
                + metadata("Result", result.recordValue())
                + metadata("Producer kind", "external-tool")
                + metadata("Producer", "qpdf")
                + metadata("Producer version", producerVersion)
                + metadata("Tool distribution SHA-256", qpdfPin.archiveSha256())
                + metadata("Input set SHA-256", inputSetHash)
                + "Final determination: `" + result.recordValue() + "`\n\n"
                + "## Findings and artifacts\n\n"
                + "- Front product: [`artifacts/" + chain.frontArtifact
                + "`](artifacts/" + chain.frontArtifact + ")\n"
                + "- Back product: [`artifacts/" + chain.backArtifact
                + "`](artifacts/" + chain.backArtifact + ")\n"
                + "- qpdf findings: [`artifacts/" + chain.qpdfFindings
                + "`](artifacts/" + chain.qpdfFindings + ")\n"
                + "- " + finding + "\n\n"
                + "This syntax chain does not establish PDF standards conformance. "
                + "The mandatory standards, semantic, and visual chains remain absent.\n";
    }

    private static String productQpdfFindings(
            ProductChain chain,
            String inputSetHash,
            List<ProductQpdfResult> checks,
            EvidenceResult result,
            QpdfPin qpdfPin) {
        StringBuilder findings = new StringBuilder();
        findings.append("# ").append(chain.label)
                .append(" qpdf syntax findings\n\n")
                .append(metadata("Input set SHA-256", inputSetHash))
                .append(metadata("Tool", "qpdf"))
                .append(metadata("Tool version", qpdfPin.version()))
                .append(metadata("Distribution SHA-256", qpdfPin.archiveSha256()))
                .append("Final determination: `")
                .append(result.recordValue())
                .append("`\n\n");
        for (ProductQpdfResult check : checks) {
            findings.append("## ").append(check.artifactName).append("\n\n")
                    .append(metadata("Input SHA-256", check.inputHash))
                    .append("Invocation: `qpdf --check ")
                    .append(check.artifactName)
                    .append("`\n\n")
                    .append('`').append(check.artifactName)
                    .append("` exit code: `")
                    .append(check.result.exitCode)
                    .append("`\n\n")
                    .append("### Standard output\n\n```text\n")
                    .append(check.result.standardOutput)
                    .append(fencedEnding(check.result.standardOutput))
                    .append("### Standard error\n\n```text\n")
                    .append(check.result.standardError)
                    .append(fencedEnding(check.result.standardError));
        }
        return findings.toString();
    }

    private static String productIndeterminateToolFindings(
            ProductChain chain,
            String inputSetHash,
            String observedVersion,
            String finding,
            QpdfPin qpdfPin) {
        return "# " + chain.label + " qpdf syntax findings\n\n"
                + metadata("Input set SHA-256", inputSetHash)
                + metadata("Tool", "qpdf")
                + metadata("Tool version", observedVersion)
                + metadata("Distribution SHA-256", qpdfPin.archiveSha256())
                + "Final determination: `indeterminate`\n\n"
                + finding + "\n";
    }

    private static String syntaxRecord(
            String inputHash,
            String releaseTrain,
            String producerVersion,
            EvidenceResult result,
            String finding,
            QpdfPin qpdfPin) {
        return "# T06 qpdf syntax evidence\n\n"
                + metadata("Capability", CAPABILITY)
                + metadata("Acceptance Profile", ACCEPTANCE_PROFILE)
                + metadata("Profile record", PROFILE_RECORD)
                + metadata("Release train", releaseTrain)
                + metadata("Chain", "syntax")
                + metadata("Result", result.recordValue())
                + metadata("Producer kind", "external-tool")
                + metadata("Producer", "qpdf")
                + metadata("Producer version", producerVersion)
                + metadata("Tool distribution SHA-256", qpdfPin.archiveSha256())
                + metadata("Input ID-neutral SHA-256", inputHash)
                + metadata("Input hash policy", EvidenceFiles.inputHashPolicy())
                + "Final determination: `" + result.recordValue() + "`\n\n"
                + "## Findings and artifacts\n\n"
                + "- Input PDF: [`artifacts/" + ARTIFACT_NAME + "`](artifacts/"
                + ARTIFACT_NAME + ")\n"
                + "- qpdf findings: [`artifacts/" + QPDF_FINDINGS_NAME + "`](artifacts/"
                + QPDF_FINDINGS_NAME + ")\n"
                + "- " + finding + "\n";
    }

    private static String semanticRecord(
            String inputHash,
            String releaseTrain,
            SemanticObservation observation) {
        EvidenceResult result = observation.result();
        return "# T06 project semantic evidence\n\n"
                + metadata("Capability", CAPABILITY)
                + metadata("Acceptance Profile", ACCEPTANCE_PROFILE)
                + metadata("Profile record", PROFILE_RECORD)
                + metadata("Release train", releaseTrain)
                + metadata("Chain", "semantic")
                + metadata("Result", result.recordValue())
                + metadata("Producer kind", "project-test")
                + metadata("Producer", "folio-pdf-semantic-assertions")
                + metadata("Producer version", releaseTrain)
                + metadata("Input ID-neutral SHA-256", inputHash)
                + metadata("Input hash policy", EvidenceFiles.inputHashPolicy())
                + "Final determination: `" + result.recordValue() + "`\n\n"
                + "## Findings and artifacts\n\n"
                + "- Input PDF: [`artifacts/" + ARTIFACT_NAME + "`](artifacts/"
                + ARTIFACT_NAME + ")\n"
                + "- Semantic findings: [`artifacts/" + SEMANTIC_FINDINGS_NAME
                + "`](artifacts/" + SEMANTIC_FINDINGS_NAME + ")\n"
                + "- " + observation.recordFinding() + "\n";
    }

    private static String determinationRecord(
            String inputHash,
            String releaseTrain,
            EvidenceResult syntaxResult,
            EvidenceResult semanticResult,
            EvidenceResult visualResult,
            EvidenceResult profileDetermination) {
        return "# T03 Acceptance Profile determination\n\n"
                + metadata("Capability", CAPABILITY)
                + metadata("Acceptance Profile", ACCEPTANCE_PROFILE)
                + metadata("Profile record", PROFILE_RECORD)
                + metadata("Release train", releaseTrain)
                + metadata("Input ID-neutral SHA-256", inputHash)
                + metadata("Input hash policy", EvidenceFiles.inputHashPolicy())
                + "Final determination: `" + profileDetermination.recordValue() + "`\n\n"
                + passingChains(syntaxResult, semanticResult, visualResult)
                + (syntaxResult == EvidenceResult.FAIL
                        ? "Failing mandatory chains: `syntax`\n\n" : "")
                + (semanticResult == EvidenceResult.FAIL
                        ? "Failing mandatory chains: `semantic`\n\n" : "")
                + (visualResult == EvidenceResult.FAIL
                        ? "Failing mandatory chains: `visual`\n\n" : "")
                + (syntaxResult == EvidenceResult.INDETERMINATE
                        ? "Indeterminate mandatory chains: `syntax`\n\n" : "")
                + (visualResult == EvidenceResult.INDETERMINATE
                        ? "Indeterminate mandatory chains: `visual`\n\n" : "")
                + "Missing mandatory chains: `standards`\n\n"
                + "The capability remains `experimental`; qpdf syntax evidence is not "
                + "a standards-compliance claim, and visual evidence cannot replace the "
                + "missing independent standards chain.\n";
    }

    private static String passingChains(
            EvidenceResult syntaxResult,
            EvidenceResult semanticResult,
            EvidenceResult visualResult) {
        List<String> passing = new ArrayList<String>(3);
        if (syntaxResult == EvidenceResult.PASS) {
            passing.add("syntax");
        }
        if (semanticResult == EvidenceResult.PASS) {
            passing.add("semantic");
        }
        if (visualResult == EvidenceResult.PASS) {
            passing.add("visual");
        }
        if (passing.isEmpty()) {
            return "Passing chains: none\n\n";
        }
        StringBuilder value = new StringBuilder("Passing chains: ");
        for (int index = 0; index < passing.size(); index++) {
            if (index > 0) {
                value.append(", ");
            }
            value.append('`').append(passing.get(index)).append('`');
        }
        return value.append("\n\n").toString();
    }

    private static String qpdfFindings(
            ProcessResult result,
            String inputHash,
            QpdfPin qpdfPin) {
        return "# qpdf syntax findings\n\n"
                + metadata("Input ID-neutral SHA-256", inputHash)
                + metadata("Input hash policy", EvidenceFiles.inputHashPolicy())
                + metadata("Tool", "qpdf")
                + metadata("Tool version", qpdfPin.version())
                + metadata("Distribution SHA-256", qpdfPin.archiveSha256())
                + "Invocation: `qpdf --check " + ARTIFACT_NAME + "`\n\n"
                + "Exit code: `" + result.exitCode + "`\n\n"
                + "## Standard output\n\n```text\n"
                + result.standardOutput + fencedEnding(result.standardOutput)
                + "## Standard error\n\n```text\n"
                + result.standardError + finalFencedEnding(result.standardError);
    }

    private static String unavailableQpdfFindings(String inputHash, QpdfPin qpdfPin) {
        return "# qpdf syntax findings\n\n"
                + metadata("Input ID-neutral SHA-256", inputHash)
                + metadata("Input hash policy", EvidenceFiles.inputHashPolicy())
                + metadata("Tool", "qpdf")
                + metadata("Tool version", "unavailable")
                + metadata("Distribution SHA-256", qpdfPin.archiveSha256())
                + "Final determination: `indeterminate`\n\n"
                + "The pinned qpdf tool was unavailable.\n";
    }

    private static String unpinnedQpdfFindings(
            String inputHash,
            String observedVersion,
            QpdfPin qpdfPin) {
        return "# qpdf syntax findings\n\n"
                + metadata("Input ID-neutral SHA-256", inputHash)
                + metadata("Input hash policy", EvidenceFiles.inputHashPolicy())
                + metadata("Tool", "qpdf")
                + metadata("Tool version", observedVersion)
                + metadata("Distribution SHA-256", qpdfPin.archiveSha256())
                + "Final determination: `indeterminate`\n\n"
                + "Expected pinned qpdf version `" + qpdfPin.version()
                + "`; observed `" + observedVersion + "`.\n";
    }

    private static String qpdfVersion(String output) {
        String prefix = "qpdf version ";
        for (String line : output.split("\\r?\\n")) {
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length()).trim();
            }
        }
        return "unavailable";
    }

    private static final class ProductQpdfResult {
        private final String artifactName;
        private final String inputHash;
        private final ProcessResult result;

        ProductQpdfResult(
                String artifactName,
                String inputHash,
                ProcessResult result) {
            this.artifactName = artifactName;
            this.inputHash = inputHash;
            this.result = result;
        }
    }
}
