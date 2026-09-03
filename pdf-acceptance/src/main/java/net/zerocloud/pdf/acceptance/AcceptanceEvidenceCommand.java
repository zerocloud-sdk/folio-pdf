package net.zerocloud.pdf.acceptance;

import static net.zerocloud.pdf.acceptance.EvidenceFiles.fencedEnding;
import static net.zerocloud.pdf.acceptance.EvidenceFiles.finalFencedEnding;
import static net.zerocloud.pdf.acceptance.EvidenceFiles.metadata;
import static net.zerocloud.pdf.acceptance.EvidenceFiles.sha256;
import static net.zerocloud.pdf.acceptance.EvidenceFiles.write;

import java.awt.Color;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
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
import java.util.Locale;
import javax.imageio.ImageIO;
import net.zerocloud.pdf.Annotation;
import net.zerocloud.pdf.AnnotationAppearance;
import net.zerocloud.pdf.AnnotationColor;
import net.zerocloud.pdf.AnnotationProperties;
import net.zerocloud.pdf.AnnotationQuad;
import net.zerocloud.pdf.AnnotationRectangle;
import net.zerocloud.pdf.CredentialAuthority;
import net.zerocloud.pdf.DocumentPatch;
import net.zerocloud.pdf.DocumentPermissions;
import net.zerocloud.pdf.DocumentResource;
import net.zerocloud.pdf.DocumentResourceInventory;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.EmbeddedFile;
import net.zerocloud.pdf.ExtractionLimits;
import net.zerocloud.pdf.FontResource;
import net.zerocloud.pdf.GoToAction;
import net.zerocloud.pdf.ImageByteAccess;
import net.zerocloud.pdf.ImageResource;
import net.zerocloud.pdf.LinkActivation;
import net.zerocloud.pdf.NavigationTarget;
import net.zerocloud.pdf.ObjectReference;
import net.zerocloud.pdf.OutlineItem;
import net.zerocloud.pdf.PageDestination;
import net.zerocloud.pdf.PageRange;
import net.zerocloud.pdf.PasswordCredential;
import net.zerocloud.pdf.PasswordEncryptionAlgorithm;
import net.zerocloud.pdf.PasswordEncryptionScope;
import net.zerocloud.pdf.PasswordSecurityInfo;
import net.zerocloud.pdf.PasswordSecurityPolicy;
import net.zerocloud.pdf.PdfArray;
import net.zerocloud.pdf.PdfDictionary;
import net.zerocloud.pdf.PdfName;
import net.zerocloud.pdf.PdfNumber;
import net.zerocloud.pdf.PdfStream;
import net.zerocloud.pdf.PdfString;
import net.zerocloud.pdf.PdfOutputPolicy;
import net.zerocloud.pdf.PdfVersion;
import net.zerocloud.pdf.PdfVersionInfo;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.ResourceExtractionLimits;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.TextRenderingMode;
import net.zerocloud.pdf.TextStructureExtraction;
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
import net.zerocloud.pdf.composition.CanvasFont;
import net.zerocloud.pdf.composition.CanvasBlendMode;
import net.zerocloud.pdf.composition.CanvasColor;
import net.zerocloud.pdf.composition.CanvasColorSpace;
import net.zerocloud.pdf.composition.CanvasImage;
import net.zerocloud.pdf.composition.CanvasMatrix;
import net.zerocloud.pdf.composition.CanvasMask;
import net.zerocloud.pdf.composition.CanvasProgram;
import net.zerocloud.pdf.composition.CanvasRectangle;
import net.zerocloud.pdf.composition.CanvasResourceLimits;
import net.zerocloud.pdf.composition.CanvasTransparencyGroup;
import net.zerocloud.pdf.composition.CanvasTransparencyState;
import net.zerocloud.pdf.composition.CanvasWindingRule;
import net.zerocloud.pdf.composition.command.DrawCanvas;
import net.zerocloud.pdf.query.ExtractImagesAndResources;
import net.zerocloud.pdf.query.ExtractTextAndStructure;
import net.zerocloud.pdf.query.DocumentSecurity;
import net.zerocloud.pdf.query.DocumentVersion;
import net.zerocloud.pdf.query.PageObjectReference;
import net.zerocloud.pdf.query.PageCount;

/**
 * Repository-only command that records T06/T07 evidence and the T10 through
 * T17 product chains.
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
    private static final String T13_CAPABILITY =
            "document.text-structure.extract";
    private static final String T13_ACCEPTANCE_PROFILE =
            "T13-text-logical-structure";
    private static final String T13_PROFILE_RECORD =
            "capabilities/evidence/T13-text-logical-structure.md";
    private static final String T13_PAGE_TEXT_ARTIFACT =
            "T13-page-text.pdf";
    private static final String T13_TAGGED_ARTIFACT =
            "T13-tagged-structure.pdf";
    private static final String T13_QPDF_FINDINGS =
            "T13-text-logical-structure-qpdf.txt";
    private static final String T14_CAPABILITY =
            "document.images-resources.extract";
    private static final String T14_ACCEPTANCE_PROFILE =
            "T14-image-resource-extraction";
    private static final String T14_PROFILE_RECORD =
            "capabilities/evidence/T14-image-resource-extraction.md";
    private static final String T14_IMAGE_FONT_ARTIFACT =
            "T14-image-font-inventory.pdf";
    private static final String T14_FORM_MASK_ARTIFACT =
            "T14-form-mask-inventory.pdf";
    private static final String T14_QPDF_FINDINGS =
            "T14-image-resource-extraction-qpdf.txt";
    private static final String T15_CAPABILITY =
            "document.incremental-signature.protect";
    private static final String T15_ACCEPTANCE_PROFILE =
            "T15-incremental-signature-protection";
    private static final String T15_PROFILE_RECORD =
            "capabilities/evidence/T15-incremental-signature-protection.md";
    private static final String T15_ORIGINAL_ARTIFACT =
            "T15-incremental-original.pdf";
    private static final String T15_INCREMENTAL_ARTIFACT =
            "T15-incremental-output.pdf";
    private static final String T15_QPDF_FINDINGS =
            "T15-incremental-signature-protection-qpdf.txt";
    private static final String T16_CAPABILITY =
            "document.version-password-security";
    private static final String T16_ACCEPTANCE_PROFILE =
            "T16-pdf-version-password-security";
    private static final String T16_PROFILE_RECORD =
            "capabilities/evidence/T16-pdf-version-password-security.md";
    private static final String T16_PDF17_ARTIFACT =
            "T16-password-security-pdf17.pdf";
    private static final String T16_PDF20_ARTIFACT =
            "T16-password-security-pdf20.pdf";
    private static final String T16_QPDF_FINDINGS =
            "T16-pdf-version-password-security-qpdf.txt";
    private static final String T17_CAPABILITY =
            "composition.canvas.draw-positioned-text";
    private static final String T17_ACCEPTANCE_PROFILE =
            "T17-canvas-vector-positioned-text";
    private static final String T17_PROFILE_RECORD =
            "capabilities/evidence/T17-canvas-vector-positioned-text.md";
    private static final String T17_ARTIFACT =
            "T17-canvas-vector-positioned-text.pdf";
    private static final String T17_QPDF_FINDINGS =
            "T17-canvas-vector-positioned-text-qpdf.txt";
    private static final String T17_SEMANTIC_FINDINGS =
            "T17-canvas-vector-positioned-text-semantic.txt";
    private static final String T18_CAPABILITY =
            "composition.canvas.images-colors-transparency";
    private static final String T18_ACCEPTANCE_PROFILE =
            "T18-canvas-images-colors-transparency";
    private static final String T18_PROFILE_RECORD =
            "capabilities/evidence/T18-canvas-images-colors-transparency.md";
    private static final String T18_ARTIFACT =
            "T18-canvas-images-colors-transparency.pdf";
    private static final String T18_QPDF_FINDINGS =
            "T18-canvas-images-colors-transparency-qpdf.txt";
    private static final String T18_SYNTAX_RECORD =
            "T18-canvas-images-colors-transparency-syntax.md";
    private static final String T18_SEMANTIC_FINDINGS =
            "T18-canvas-images-colors-transparency-semantic.txt";
    private static final String T18_SEMANTIC_RECORD =
            "T18-canvas-images-colors-transparency-semantic.md";

    private AcceptanceEvidenceCommand() {
    }

    /**
     * Runs the built-in T03 Acceptance Profile and the T10 through T16 syntax
     * chains.
     *
     * @param arguments output directory, pinned tool and profile authorities,
     *        and Release Train
     * @throws Exception if the evidence run cannot be completed
     */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 7) {
            throw new IllegalArgumentException(
                    "Usage: AcceptanceEvidenceCommand <output-directory> "
                            + "<qpdf-pin> <pdfium-pin> <imagemagick-pin> "
                            + "<T03-visual-profile> <T18-visual-profile> "
                            + "<release-train>");
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
        VisualProfile t18VisualProfile = VisualProfile.load(
                Paths.get(arguments[5]).toAbsolutePath().normalize());
        if (!T18_ACCEPTANCE_PROFILE.equals(t18VisualProfile.profileId())) {
            throw new IllegalArgumentException(
                    "Visual profile does not describe " + T18_ACCEPTANCE_PROFILE);
        }
        String releaseTrain = arguments[6];
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

        EvidenceResult t13Syntax = recordProductSyntax(
                new ProductChain(
                        "T13",
                        T13_CAPABILITY,
                        T13_ACCEPTANCE_PROFILE,
                        T13_PROFILE_RECORD,
                        T13_PAGE_TEXT_ARTIFACT,
                        T13_TAGGED_ARTIFACT,
                        T13_QPDF_FINDINGS,
                        "T13-text-logical-structure-syntax.md",
                        ProductHashPolicy.ID_NEUTRAL),
                new ProductCreator() {
                    @Override
                    public void create(Path pageText, Path tagged)
                            throws Exception {
                        createT13Products(pageText, tagged);
                    }
                },
                output,
                artifacts,
                qpdfPin,
                releaseTrain);

        EvidenceResult t14Syntax = recordProductSyntax(
                new ProductChain(
                        "T14",
                        T14_CAPABILITY,
                        T14_ACCEPTANCE_PROFILE,
                        T14_PROFILE_RECORD,
                        T14_IMAGE_FONT_ARTIFACT,
                        T14_FORM_MASK_ARTIFACT,
                        T14_QPDF_FINDINGS,
                        "T14-image-resource-extraction-syntax.md",
                        ProductHashPolicy.ID_NEUTRAL),
                new ProductCreator() {
                    @Override
                    public void create(Path imageFont, Path formMask)
                            throws Exception {
                        createT14Products(imageFont, formMask);
                    }
                },
                output,
                artifacts,
                qpdfPin,
                releaseTrain);

        EvidenceResult t15Syntax = recordProductSyntax(
                new ProductChain(
                        "T15",
                        T15_CAPABILITY,
                        T15_ACCEPTANCE_PROFILE,
                        T15_PROFILE_RECORD,
                        T15_ORIGINAL_ARTIFACT,
                        T15_INCREMENTAL_ARTIFACT,
                        T15_QPDF_FINDINGS,
                        "T15-incremental-signature-protection-syntax.md",
                        ProductHashPolicy.REVISION_ID_NEUTRAL),
                new ProductCreator() {
                    @Override
                    public void create(Path original, Path incremental)
                            throws Exception {
                        createT15Products(original, incremental);
                    }
                },
                output,
                artifacts,
                qpdfPin,
                releaseTrain);

        EvidenceResult t16Syntax = recordProductSyntax(
                new ProductChain(
                        "T16",
                        T16_CAPABILITY,
                        T16_ACCEPTANCE_PROFILE,
                        T16_PROFILE_RECORD,
                        T16_PDF17_ARTIFACT,
                        T16_PDF20_ARTIFACT,
                        T16_QPDF_FINDINGS,
                        "T16-pdf-version-password-security-syntax.md",
                        ProductHashPolicy.SECURITY_OBSERVATION,
                        true),
                new ProductCreator() {
                    @Override
                    public void create(Path pdf17, Path pdf20)
                            throws Exception {
                        createT16Products(pdf17, pdf20);
                    }
                },
                output,
                artifacts,
                qpdfPin,
                releaseTrain);

        T17Evidence t17 = recordT17Evidence(
                output,
                artifacts,
                qpdfPin,
                releaseTrain);
        T18Evidence t18 = recordT18Evidence(
                output,
                artifacts,
                qpdfPin,
                pdfiumPin,
                imageMagickPin,
                t18VisualProfile,
                releaseTrain);

        System.out.println("Acceptance Profile determination: "
                + profileDetermination.recordValue());
        System.out.println("T07 visual chain: " + visual.result().recordValue());
        System.out.println("T10 syntax chain: " + t10Syntax.recordValue());
        System.out.println("T11 syntax chain: " + t11Syntax.recordValue());
        System.out.println("T12 syntax chain: " + t12Syntax.recordValue());
        System.out.println("T13 syntax chain: " + t13Syntax.recordValue());
        System.out.println("T14 syntax chain: " + t14Syntax.recordValue());
        System.out.println("T15 syntax chain: " + t15Syntax.recordValue());
        System.out.println("T16 syntax chain: " + t16Syntax.recordValue());
        System.out.println("T17 syntax chain: "
                + t17.syntax.recordValue());
        System.out.println("T17 semantic chain: "
                + t17.semantic.recordValue());
        System.out.println("T18 syntax chain: " + t18.syntax.recordValue());
        System.out.println("T18 semantic chain: " + t18.semantic.recordValue());
        System.out.println("T18 visual chain: " + t18.visual.recordValue());
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
        private final ProductHashPolicy hashPolicy;
        private final boolean passwordProtected;

        ProductChain(
                String label,
                String capability,
                String acceptanceProfile,
                String profileRecord,
                String frontArtifact,
                String backArtifact,
                String qpdfFindings,
                String syntaxRecord) {
            this(
                    label,
                    capability,
                    acceptanceProfile,
                    profileRecord,
                    frontArtifact,
                    backArtifact,
                    qpdfFindings,
                    syntaxRecord,
                    ProductHashPolicy.EXACT,
                    false);
        }

        ProductChain(
                String label,
                String capability,
                String acceptanceProfile,
                String profileRecord,
                String frontArtifact,
                String backArtifact,
                String qpdfFindings,
                String syntaxRecord,
                ProductHashPolicy hashPolicy) {
            this(
                    label,
                    capability,
                    acceptanceProfile,
                    profileRecord,
                    frontArtifact,
                    backArtifact,
                    qpdfFindings,
                    syntaxRecord,
                    hashPolicy,
                    false);
        }

        ProductChain(
                String label,
                String capability,
                String acceptanceProfile,
                String profileRecord,
                String frontArtifact,
                String backArtifact,
                String qpdfFindings,
                String syntaxRecord,
                ProductHashPolicy hashPolicy,
                boolean passwordProtected) {
            this.label = label;
            this.capability = capability;
            this.acceptanceProfile = acceptanceProfile;
            this.profileRecord = profileRecord;
            this.frontArtifact = frontArtifact;
            this.backArtifact = backArtifact;
            this.qpdfFindings = qpdfFindings;
            this.syntaxRecord = syntaxRecord;
            this.hashPolicy = hashPolicy;
            this.passwordProtected = passwordProtected;
        }

        String qpdfInvocation(String artifact) {
            if (passwordProtected) {
                return "qpdf --check --show-encryption "
                        + "--password-file=<redacted-temporary-file> "
                        + artifact;
            }
            return "qpdf --check " + artifact;
        }
    }

    private enum ProductHashPolicy {
        EXACT("Input SHA-256", "Input set SHA-256", null) {
            @Override
            String hash(Path artifact) throws IOException {
                return sha256(artifact);
            }
        },
        ID_NEUTRAL(
                "Input ID-neutral SHA-256",
                "Input ID-neutral set SHA-256",
                EvidenceFiles.inputHashPolicy()) {
            @Override
            String hash(Path artifact) throws IOException {
                return EvidenceFiles.idNeutralPdfSha256(artifact);
            }
        },
        REVISION_ID_NEUTRAL(
                "Input revision-ID-neutral SHA-256",
                "Input revision-ID-neutral set SHA-256",
                EvidenceFiles.revisionInputHashPolicy()) {
            @Override
            String hash(Path artifact) throws IOException {
                return EvidenceFiles.revisionIdNeutralPdfSha256(artifact);
            }
        },
        SECURITY_OBSERVATION(
                "Security observation SHA-256",
                "Security observation set SHA-256",
                "SHA-256 of the project-observed non-secret version, Standard-handler profile, scope, permission word, and public-reopen page count; randomized credential entries, file identifiers, and ciphertext are excluded") {
            @Override
            String hash(Path artifact) throws IOException {
                return t16SecurityObservationSha256(artifact);
            }
        };

        private final String inputHashLabel;
        private final String inputSetHashLabel;
        private final String description;

        ProductHashPolicy(
                String inputHashLabel,
                String inputSetHashLabel,
                String description) {
            this.inputHashLabel = inputHashLabel;
            this.inputSetHashLabel = inputSetHashLabel;
            this.description = description;
        }

        abstract String hash(Path artifact) throws IOException;

        String inputHashMetadata(String hash) {
            return metadata(inputHashLabel, hash);
        }

        String inputSetHashMetadata(String hash) {
            return metadata(inputSetHashLabel, hash);
        }

        String policyMetadata() {
            return description == null
                    ? ""
                    : metadata("Input hash policy", description);
        }
    }

    private static T17Evidence recordT17Evidence(
            Path output,
            Path artifacts,
            QpdfPin qpdfPin,
            String releaseTrain) throws Exception {
        Path artifact = artifacts.resolve(T17_ARTIFACT);
        WorkflowOutcome<Void> creation = createT17Product(artifact);
        String inputHash = EvidenceFiles.idNeutralPdfSha256(artifact);

        CanvasSemanticObservation semantic =
                CanvasSemanticAssertions.inspect(creation, artifact);
        write(artifacts.resolve(T17_SEMANTIC_FINDINGS),
                semantic.findings(inputHash, releaseTrain));
        write(output.resolve(
                        "T17-canvas-vector-positioned-text-semantic.md"),
                t17SemanticRecord(
                        inputHash,
                        releaseTrain,
                        semantic));

        EvidenceResult syntaxResult;
        String observedVersion;
        String syntaxFinding;
        String findings;
        try {
            ProcessResult version = ExternalProcess.run(
                    qpdfPin.executable(), output, "--version");
            observedVersion = qpdfVersion(version.combinedOutput());
            if (version.exitCode != 0
                    || !qpdfPin.version().equals(observedVersion)) {
                syntaxResult = EvidenceResult.INDETERMINATE;
                syntaxFinding = "Expected pinned qpdf version `"
                        + qpdfPin.version() + "`; observed `"
                        + observedVersion + "`.";
                findings = t17IndeterminateToolFindings(
                        inputHash,
                        observedVersion,
                        syntaxFinding,
                        qpdfPin);
            } else {
                ProcessResult check = ExternalProcess.run(
                        qpdfPin.executable(),
                        artifacts,
                        "--check",
                        T17_ARTIFACT);
                if (check.exitCode == 0) {
                    syntaxResult = EvidenceResult.PASS;
                    syntaxFinding = "qpdf completed `--check` for the T17 product with exit code `0`.";
                } else if (check.exitCode == 2 || check.exitCode == 3) {
                    syntaxResult = EvidenceResult.FAIL;
                    syntaxFinding = "qpdf reported warnings or errors for a T17 product.";
                } else {
                    syntaxResult = EvidenceResult.INDETERMINATE;
                    syntaxFinding = "qpdf returned an undocumented status for a T17 product.";
                }
                findings = t17QpdfFindings(
                        inputHash,
                        check,
                        syntaxResult,
                        qpdfPin);
            }
        } catch (IOException unavailable) {
            syntaxResult = EvidenceResult.INDETERMINATE;
            observedVersion = "unavailable";
            syntaxFinding = "The pinned qpdf tool was unavailable.";
            findings = t17IndeterminateToolFindings(
                    inputHash,
                    observedVersion,
                    syntaxFinding,
                    qpdfPin);
        }
        write(artifacts.resolve(T17_QPDF_FINDINGS), findings);
        write(output.resolve(
                        "T17-canvas-vector-positioned-text-syntax.md"),
                t17SyntaxRecord(
                        inputHash,
                        releaseTrain,
                        observedVersion,
                        syntaxResult,
                        syntaxFinding,
                        qpdfPin));
        return new T17Evidence(syntaxResult, semantic.result());
    }

    private static T18Evidence recordT18Evidence(
            Path output,
            Path artifacts,
            QpdfPin qpdfPin,
            PdfiumPin pdfiumPin,
            ImageMagickPin imageMagickPin,
            VisualProfile visualProfile,
            String releaseTrain) throws Exception {
        Path artifact = artifacts.resolve(T18_ARTIFACT);
        WorkflowOutcome<Void> creation = createT18Product(artifact);
        String inputHash = EvidenceFiles.idNeutralPdfSha256(artifact);

        T18CanvasSemanticObservation semantic =
                T18CanvasSemanticAssertions.inspect(creation, artifact);
        write(artifacts.resolve(T18_SEMANTIC_FINDINGS),
                semantic.findings(inputHash, releaseTrain));
        write(output.resolve(T18_SEMANTIC_RECORD),
                t18SemanticRecord(inputHash, releaseTrain, semantic));

        SyntaxEvidence syntax = recordT18Syntax(
                output,
                artifacts,
                qpdfPin,
                inputHash,
                releaseTrain);
        VisualEvidence visual = VisualEvidenceRecorder.record(
                VisualEvidenceChain.t18(),
                artifact,
                inputHash,
                artifacts,
                pdfiumPin,
                imageMagickPin,
                visualProfile,
                releaseTrain);
        VisualEvidenceChain visualChain = VisualEvidenceChain.t18();
        write(artifacts.resolve(visualChain.findingsName()),
                visual.rawFindings());
        write(output.resolve(visualChain.recordName()), visual.record());
        return new T18Evidence(
                syntax.result,
                semantic.result(),
                visual.result());
    }

    private static SyntaxEvidence recordT18Syntax(
            Path output,
            Path artifacts,
            QpdfPin qpdfPin,
            String inputHash,
            String releaseTrain) throws IOException {
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
                findings = t18IndeterminateQpdfFindings(
                        inputHash, observedVersion, finding, qpdfPin);
            } else {
                ProcessResult check = ExternalProcess.run(
                        qpdfPin.executable(), artifacts, "--check", T18_ARTIFACT);
                if (check.exitCode == 0) {
                    result = EvidenceResult.PASS;
                    finding = "qpdf completed `--check` for the T18 product with exit code `0`.";
                } else if (check.exitCode == 2 || check.exitCode == 3) {
                    result = EvidenceResult.FAIL;
                    finding = "qpdf reported warnings or errors for a T18 product.";
                } else {
                    result = EvidenceResult.INDETERMINATE;
                    finding = "qpdf returned an undocumented status for a T18 product.";
                }
                findings = t18QpdfFindings(inputHash, check, result, qpdfPin);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            result = EvidenceResult.INDETERMINATE;
            observedVersion = "unavailable";
            finding = "The pinned qpdf process was interrupted.";
            findings = t18IndeterminateQpdfFindings(
                    inputHash, observedVersion, finding, qpdfPin);
        } catch (IOException unavailable) {
            result = EvidenceResult.INDETERMINATE;
            observedVersion = "unavailable";
            finding = "The pinned qpdf tool was unavailable.";
            findings = t18IndeterminateQpdfFindings(
                    inputHash, observedVersion, finding, qpdfPin);
        }
        write(artifacts.resolve(T18_QPDF_FINDINGS), findings);
        write(output.resolve(T18_SYNTAX_RECORD), t18SyntaxRecord(
                inputHash,
                releaseTrain,
                observedVersion,
                result,
                finding,
                qpdfPin));
        return new SyntaxEvidence(result);
    }

    private static String t18SyntaxRecord(
            String inputHash,
            String releaseTrain,
            String producerVersion,
            EvidenceResult result,
            String finding,
            QpdfPin qpdfPin) {
        return "# T18 qpdf syntax evidence\n\n"
                + metadata("Capability", T18_CAPABILITY)
                + metadata("Acceptance Profile", T18_ACCEPTANCE_PROFILE)
                + metadata("Profile record", T18_PROFILE_RECORD)
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
                + "## Findings and artifact\n\n"
                + "- Product: [`artifacts/" + T18_ARTIFACT + "`](artifacts/"
                + T18_ARTIFACT + ")\n"
                + "- qpdf findings: [`artifacts/" + T18_QPDF_FINDINGS
                + "`](artifacts/" + T18_QPDF_FINDINGS + ")\n"
                + "- " + finding + "\n\n"
                + "This syntax chain does not establish PDF standards conformance.\n";
    }

    private static String t18SemanticRecord(
            String inputHash,
            String releaseTrain,
            T18CanvasSemanticObservation semantic) {
        return "# T18 project semantic evidence\n\n"
                + metadata("Capability", T18_CAPABILITY)
                + metadata("Acceptance Profile", T18_ACCEPTANCE_PROFILE)
                + metadata("Profile record", T18_PROFILE_RECORD)
                + metadata("Release train", releaseTrain)
                + metadata("Chain", "semantic")
                + metadata("Result", semantic.result().recordValue())
                + metadata("Producer kind", "project-test")
                + metadata("Producer", "folio-pdf-t18-semantic-assertions")
                + metadata("Producer version", releaseTrain)
                + metadata("Input ID-neutral SHA-256", inputHash)
                + metadata("Input hash policy", EvidenceFiles.inputHashPolicy())
                + "Final determination: `" + semantic.result().recordValue()
                + "`\n\n## Finding and artifact\n\n"
                + "- Product: [`artifacts/" + T18_ARTIFACT + "`](artifacts/"
                + T18_ARTIFACT + ")\n"
                + "- Semantic findings: [`artifacts/" + T18_SEMANTIC_FINDINGS
                + "`](artifacts/" + T18_SEMANTIC_FINDINGS + ")\n"
                + "- " + semantic.recordFinding() + "\n\n"
                + "Expected semantics are project-owned public Canvas and resource-query values.\n";
    }

    private static String t18QpdfFindings(
            String inputHash,
            ProcessResult check,
            EvidenceResult result,
            QpdfPin qpdfPin) {
        return "# T18 qpdf syntax findings\n\n"
                + metadata("Input ID-neutral SHA-256", inputHash)
                + metadata("Input hash policy", EvidenceFiles.inputHashPolicy())
                + metadata("Tool", "qpdf")
                + metadata("Tool version", qpdfPin.version())
                + metadata("Distribution SHA-256", qpdfPin.archiveSha256())
                + "Final determination: `" + result.recordValue() + "`\n\n"
                + "## " + T18_ARTIFACT + "\n\n"
                + "Invocation: `qpdf --check " + T18_ARTIFACT + "`\n\n"
                + "`" + T18_ARTIFACT + "` exit code: `" + check.exitCode
                + "`\n\n### Standard output\n\n```text\n"
                + check.standardOutput + fencedEnding(check.standardOutput)
                + "### Standard error\n\n```text\n" + check.standardError
                + finalFencedEnding(check.standardError);
    }

    private static String t18IndeterminateQpdfFindings(
            String inputHash,
            String observedVersion,
            String finding,
            QpdfPin qpdfPin) {
        return "# T18 qpdf syntax findings\n\n"
                + metadata("Input ID-neutral SHA-256", inputHash)
                + metadata("Input hash policy", EvidenceFiles.inputHashPolicy())
                + metadata("Tool", "qpdf")
                + metadata("Tool version", observedVersion)
                + metadata("Distribution SHA-256", qpdfPin.archiveSha256())
                + "Final determination: `indeterminate`\n\n" + finding + "\n";
    }

    private static String t17SyntaxRecord(
            String inputHash,
            String releaseTrain,
            String producerVersion,
            EvidenceResult result,
            String finding,
            QpdfPin qpdfPin) {
        return "# T17 qpdf syntax evidence\n\n"
                + metadata("Capability", T17_CAPABILITY)
                + metadata("Acceptance Profile", T17_ACCEPTANCE_PROFILE)
                + metadata("Profile record", T17_PROFILE_RECORD)
                + metadata("Release train", releaseTrain)
                + metadata("Chain", "syntax")
                + metadata("Result", result.recordValue())
                + metadata("Producer kind", "external-tool")
                + metadata("Producer", "qpdf")
                + metadata("Producer version", producerVersion)
                + metadata("Tool distribution SHA-256",
                        qpdfPin.archiveSha256())
                + metadata("Input ID-neutral SHA-256", inputHash)
                + metadata("Input hash policy",
                        EvidenceFiles.inputHashPolicy())
                + "Final determination: `" + result.recordValue() + "`\n\n"
                + "## Findings and artifact\n\n"
                + "- Product: [`artifacts/" + T17_ARTIFACT
                + "`](artifacts/" + T17_ARTIFACT + ")\n"
                + "- qpdf findings: [`artifacts/" + T17_QPDF_FINDINGS
                + "`](artifacts/" + T17_QPDF_FINDINGS + ")\n"
                + "- " + finding + "\n\n"
                + "This syntax chain does not establish PDF standards conformance. The standards and visual chains remain absent.\n";
    }

    private static String t17SemanticRecord(
            String inputHash,
            String releaseTrain,
            CanvasSemanticObservation semantic) {
        return "# T17 project semantic evidence\n\n"
                + metadata("Capability", T17_CAPABILITY)
                + metadata("Acceptance Profile", T17_ACCEPTANCE_PROFILE)
                + metadata("Profile record", T17_PROFILE_RECORD)
                + metadata("Release train", releaseTrain)
                + metadata("Chain", "semantic")
                + metadata("Result", semantic.result().recordValue())
                + metadata("Producer kind", "project-test")
                + metadata("Producer",
                        "folio-pdf-t17-semantic-assertions")
                + metadata("Producer version", releaseTrain)
                + metadata("Input ID-neutral SHA-256", inputHash)
                + metadata("Input hash policy",
                        EvidenceFiles.inputHashPolicy())
                + "Final determination: `"
                + semantic.result().recordValue() + "`\n\n"
                + "## Finding and artifact\n\n"
                + "- Product: [`artifacts/" + T17_ARTIFACT
                + "`](artifacts/" + T17_ARTIFACT + ")\n"
                + "- Semantic findings: [`artifacts/"
                + T17_SEMANTIC_FINDINGS + "`](artifacts/"
                + T17_SEMANTIC_FINDINGS + ")\n"
                + "- " + semantic.recordFinding() + "\n\n"
                + "The expected semantics are project-owned Canvas Program values. The standards and visual chains remain absent.\n";
    }

    private static String t17QpdfFindings(
            String inputHash,
            ProcessResult check,
            EvidenceResult result,
            QpdfPin qpdfPin) {
        return "# T17 qpdf syntax findings\n\n"
                + metadata("Input ID-neutral SHA-256", inputHash)
                + metadata("Input hash policy",
                        EvidenceFiles.inputHashPolicy())
                + metadata("Tool", "qpdf")
                + metadata("Tool version", qpdfPin.version())
                + metadata("Distribution SHA-256",
                        qpdfPin.archiveSha256())
                + "Final determination: `" + result.recordValue() + "`\n\n"
                + "## " + T17_ARTIFACT + "\n\n"
                + "Invocation: `qpdf --check " + T17_ARTIFACT + "`\n\n"
                + "`" + T17_ARTIFACT + "` exit code: `"
                + check.exitCode + "`\n\n"
                + "### Standard output\n\n```text\n"
                + check.standardOutput
                + fencedEnding(check.standardOutput)
                + "### Standard error\n\n```text\n"
                + check.standardError
                + finalFencedEnding(check.standardError);
    }

    private static String t17IndeterminateToolFindings(
            String inputHash,
            String observedVersion,
            String finding,
            QpdfPin qpdfPin) {
        return "# T17 qpdf syntax findings\n\n"
                + metadata("Input ID-neutral SHA-256", inputHash)
                + metadata("Input hash policy",
                        EvidenceFiles.inputHashPolicy())
                + metadata("Tool", "qpdf")
                + metadata("Tool version", observedVersion)
                + metadata("Distribution SHA-256",
                        qpdfPin.archiveSha256())
                + "Final determination: `indeterminate`\n\n"
                + finding + "\n";
    }

    private static final class T17Evidence {

        private final EvidenceResult syntax;
        private final EvidenceResult semantic;

        T17Evidence(
                EvidenceResult syntax,
                EvidenceResult semantic) {
            this.syntax = syntax;
            this.semantic = semantic;
        }
    }

    private static final class SyntaxEvidence {
        private final EvidenceResult result;

        SyntaxEvidence(EvidenceResult result) {
            this.result = result;
        }
    }

    private static final class T18Evidence {
        private final EvidenceResult syntax;
        private final EvidenceResult semantic;
        private final EvidenceResult visual;

        T18Evidence(
                EvidenceResult syntax,
                EvidenceResult semantic,
                EvidenceResult visual) {
            this.syntax = syntax;
            this.semantic = semantic;
            this.visual = visual;
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
        String frontHash = chain.hashPolicy.hash(front);
        String backHash = chain.hashPolicy.hash(back);
        String inputSetHash = sha256(frontHash + "\n" + backHash);

        EvidenceResult result;
        String observedVersion;
        String finding;
        String findings;
        Path passwordFile = chain.passwordProtected
                ? writeT16PasswordFile() : null;
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
                        runProductQpdf(
                                chain,
                                qpdfPin,
                                artifacts,
                                passwordFile,
                                chain.frontArtifact)));
                checks.add(new ProductQpdfResult(
                        chain.backArtifact,
                        backHash,
                        runProductQpdf(
                                chain,
                                qpdfPin,
                                artifacts,
                                passwordFile,
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
        } finally {
            if (passwordFile != null) {
                Files.deleteIfExists(passwordFile);
            }
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

    private static ProcessResult runProductQpdf(
            ProductChain chain,
            QpdfPin qpdfPin,
            Path artifacts,
            Path passwordFile,
            String artifact) throws IOException, InterruptedException {
        if (!chain.passwordProtected) {
            return ExternalProcess.run(
                    qpdfPin.executable(),
                    artifacts,
                    "--check",
                    artifact);
        }
        ProcessResult result = ExternalProcess.run(
                qpdfPin.executable(),
                artifacts,
                "--check",
                "--show-encryption",
                "--password-file=" + passwordFile,
                artifact);
        return new ProcessResult(
                result.exitCode,
                redactProductQpdfOutput(
                        result.standardOutput,
                        passwordFile),
                redactProductQpdfOutput(
                        result.standardError,
                        passwordFile));
    }

    private static String redactProductQpdfOutput(
            String output,
            Path passwordFile) {
        String redactedPath = output.replace(
                passwordFile.toAbsolutePath().normalize().toString(),
                "<redacted-temporary-file>");
        redactedPath = redactedPath.replace(
                passwordFile.toString(),
                "<redacted-temporary-file>");
        if (passwordFile.getFileName() != null) {
            redactedPath = redactedPath.replace(
                    passwordFile.getFileName().toString(),
                    "<redacted-temporary-file>");
        }
        return redactPasswordValues(redactedPath);
    }

    private static String redactPasswordValues(String output) {
        StringBuilder redacted = new StringBuilder(output.length());
        String[] lines = output.split("\\n", -1);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            String lower = line.toLowerCase(Locale.ROOT);
            int assignment = lower.indexOf("password =");
            if (assignment >= 0) {
                redacted.append(line.substring(0, assignment))
                        .append("password = <redacted>");
            } else {
                redacted.append(line);
            }
            if (index + 1 < lines.length) {
                redacted.append('\n');
            }
        }
        return redacted.toString();
    }

    private static Path writeT16PasswordFile() throws IOException {
        Path passwordFile = Files.createTempFile(
                ".folio-t16-qpdf-credential-",
                ".tmp");
        char[] characters = t16UserPassword();
        byte[] ascii = new byte[characters.length];
        boolean written = false;
        try {
            for (int index = 0; index < characters.length; index++) {
                ascii[index] = (byte) characters[index];
            }
            Files.write(passwordFile, ascii);
            written = true;
            return passwordFile;
        } finally {
            Arrays.fill(characters, '\0');
            Arrays.fill(ascii, (byte) 0);
            if (!written) {
                Files.deleteIfExists(passwordFile);
            }
        }
    }

    private static String t16SecurityObservationSha256(Path artifact)
            throws IOException {
        byte[] bytes = Files.readAllBytes(artifact);
        if (bytes.length < 8) {
            throw new IOException("The T16 product has no PDF version marker.");
        }
        String header = new String(
                bytes,
                0,
                8,
                StandardCharsets.US_ASCII);
        String serialized = new String(bytes, StandardCharsets.ISO_8859_1);
        boolean pdf17 = "%PDF-1.7".equals(header);
        boolean pdf20 = "%PDF-2.0".equals(header);
        if ((!pdf17 && !pdf20)
                || !serialized.contains("/V 5")
                || !serialized.contains("/R 6")
                || !serialized.contains("/Length 256")
                || !serialized.contains("/CFM /AESV3")
                || !serialized.contains("/StmF /StdCF")
                || !serialized.contains("/StrF /StdCF")
                || serialized.contains("/EncryptMetadata false")
                || (pdf17 && !serialized.contains("/Extensions"))
                || (pdf20 && serialized.contains("/Extensions"))) {
            throw new IOException(
                    "The T16 product does not match its security observation profile.");
        }
        int permissions = pdfInteger(serialized, "/P ");
        String observation = "header=" + header
                + "\nhandler=Standard"
                + "\nV=5\nR=6\nLength=256\nCFM=AESV3"
                + "\nscope=ALL_CONTENT\nP=" + permissions
                + "\npage-count=1\npublic-reopen=verified";
        return sha256(observation);
    }

    private static int pdfInteger(String serialized, String marker)
            throws IOException {
        int start = serialized.indexOf(marker);
        if (start < 0) {
            throw new IOException("The T16 permission word is missing.");
        }
        start += marker.length();
        int end = start;
        if (end < serialized.length() && serialized.charAt(end) == '-') {
            end++;
        }
        while (end < serialized.length()
                && serialized.charAt(end) >= '0'
                && serialized.charAt(end) <= '9') {
            end++;
        }
        if (end == start || end == start + 1
                && serialized.charAt(start) == '-') {
            throw new IOException("The T16 permission word is malformed.");
        }
        try {
            return Integer.parseInt(serialized.substring(start, end));
        } catch (NumberFormatException malformed) {
            throw new IOException("The T16 permission word is malformed.");
        }
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

    private static void createT15Products(Path original, Path incremental)
            throws Exception {
        WorkflowOutcome<Void> originalOutcome = new DocumentWorkflow().execute(
                WorkflowRequest.create(original, SaveMode.REWRITE),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    return null;
                });
        requireCommitted(originalOutcome, "T15 original");
        byte[] originalRevision = Files.readAllBytes(original);

        WorkflowOutcome<Integer> incrementalOutcome =
                new DocumentWorkflow().execute(
                        WorkflowRequest.builder()
                                .source(
                                        "original",
                                        DocumentSource.path(original))
                                .primarySource("original")
                                .target(
                                        "incremental",
                                        PublicationTarget.path(incremental))
                                .saveMode(SaveMode.INCREMENTAL)
                                .build(),
                        session -> {
                            session.execute(AddBlankPage.INSTANCE);
                            return session.query(PageCount.INSTANCE);
                        });
        requireCommitted(incrementalOutcome, "T15 incremental");
        if (!Integer.valueOf(2).equals(incrementalOutcome.getResult())) {
            throw new IllegalStateException(
                    "T15 incremental product did not contain two pages");
        }
        byte[] appendedRevision = Files.readAllBytes(incremental);
        if (appendedRevision.length <= originalRevision.length
                || !Arrays.equals(
                        originalRevision,
                        Arrays.copyOf(
                                appendedRevision,
                                originalRevision.length))) {
            throw new IllegalStateException(
                    "T15 incremental product did not preserve its Source prefix");
        }
        Integer reopenedPages = new DocumentWorkflow().execute(
                WorkflowRequest.open(incremental, SaveMode.REWRITE),
                session -> session.query(PageCount.INSTANCE)).getResult();
        if (!Integer.valueOf(2).equals(reopenedPages)) {
            throw new IllegalStateException(
                    "T15 incremental product did not reopen with two pages");
        }
    }

    private static void createT16Products(Path pdf17, Path pdf20)
            throws Exception {
        char[] ownerCharacters = t16OwnerPassword();
        char[] userCharacters = t16UserPassword();
        try (PasswordCredential owner = PasswordCredential.of(ownerCharacters);
                PasswordCredential user = PasswordCredential.of(userCharacters)) {
            Arrays.fill(ownerCharacters, '\0');
            Arrays.fill(userCharacters, '\0');
            DocumentPermissions permissions = DocumentPermissions.builder()
                    .allowPrinting(true)
                    .allowContentExtraction(true)
                    .allowAccessibilityExtraction(true)
                    .build();
            createT16Product(
                    pdf17,
                    PdfVersion.PDF_1_7,
                    owner,
                    user,
                    permissions);
            createT16Product(
                    pdf20,
                    PdfVersion.PDF_2_0,
                    owner,
                    user,
                    permissions);
        } finally {
            Arrays.fill(ownerCharacters, '\0');
            Arrays.fill(userCharacters, '\0');
        }
    }

    private static void createT16Product(
            Path target,
            PdfVersion version,
            PasswordCredential owner,
            PasswordCredential user,
            DocumentPermissions permissions) throws Exception {
        PasswordSecurityPolicy security = PasswordSecurityPolicy.builder(
                        owner,
                        user)
                .permissions(permissions)
                .build();
        WorkflowOutcome<Void> creation = new DocumentWorkflow().execute(
                WorkflowRequest.builder()
                        .target("protected", PublicationTarget.path(target))
                        .saveMode(SaveMode.REWRITE)
                        .outputPolicy(PdfOutputPolicy.version(version)
                                .withPasswordSecurity(security))
                        .build(),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    return null;
                });
        requireT16Observation(
                T16_CAPABILITY.equals(creation.getCapabilityId()),
                "The protected product did not report the T16 capability identity.");
        requireCommitted(creation, "T16 protected " + version);
        verifyT16Product(target, version, user, permissions, false);
        verifyT16Product(target, version, owner, permissions, true);
    }

    private static void verifyT16Product(
            Path target,
            PdfVersion expectedVersion,
            PasswordCredential credential,
            DocumentPermissions permissions,
            boolean owner) throws Exception {
        new DocumentWorkflow().execute(
                WorkflowRequest.builder()
                        .source(
                                "protected",
                                DocumentSource.path(target)
                                        .withCredential(credential))
                        .primarySource("protected")
                        .saveMode(SaveMode.REWRITE)
                        .build(),
                session -> {
                    PdfVersionInfo version = session.query(
                            DocumentVersion.INSTANCE);
                    PasswordSecurityInfo security = session.query(
                            DocumentSecurity.INSTANCE);
                    Integer pages = session.query(PageCount.INSTANCE);
                    requireT16Observation(
                            version.getHeaderVersion() == expectedVersion
                                    && version.getEffectiveVersion()
                                            == expectedVersion,
                            "The T16 product did not reopen with its requested version.");
                    requireT16Observation(
                            security.getAlgorithm().orElse(null)
                                            == PasswordEncryptionAlgorithm.AES_256
                                    && security.getSecurityHandlerRevision() == 6
                                    && security.getEncryptionScope()
                                            == PasswordEncryptionScope.ALL_CONTENT
                                    && security.getDeclaredUserPermissions()
                                            .equals(permissions),
                            "The T16 product did not reopen with its requested security policy.");
                    requireT16Observation(
                            owner
                                    ? security.getCredentialAuthority()
                                                    == CredentialAuthority.OWNER
                                            && security.getEffectivePermissions()
                                                    .equals(DocumentPermissions
                                                            .unrestricted())
                                    : security.getCredentialAuthority()
                                                    == CredentialAuthority.USER
                                            && security.getEffectivePermissions()
                                                    .equals(permissions),
                            "The T16 product did not reopen with the expected credential authority.");
                    requireT16Observation(
                            Integer.valueOf(1).equals(pages),
                            "The T16 product did not reopen with one page.");
                    return null;
                });
    }

    private static void requireT16Observation(
            boolean condition,
            String diagnostic) {
        if (!condition) {
            throw new IllegalStateException(diagnostic);
        }
    }

    private static WorkflowOutcome<Void> createT17Product(Path target)
            throws Exception {
        byte[] source = t17Source();
        WorkflowOutcome<Void> creation = new DocumentWorkflow().execute(
                WorkflowRequest.builder()
                        .source(
                                "input",
                                DocumentSource.bytes(source, source.length))
                        .primarySource("input")
                        .target("output", PublicationTarget.path(target))
                        .saveMode(SaveMode.REWRITE)
                        .build(),
                session -> {
                    DocumentResourceInventory inventory = session.query(
                            ExtractImagesAndResources.version1(
                                    t14Limits(),
                                    ImageByteAccess.NONE));
                    if (inventory.getFonts().size() != 1
                            || !inventory.getFonts().get(0)
                                    .getObjectReference().isPresent()) {
                        throw new IllegalStateException(
                                "The T17 source did not expose one indirect Font resource");
                    }
                    CanvasFont font = CanvasFont.version1(
                            inventory.getFonts().get(0)
                                    .getObjectReference().get());
                    session.execute(DrawCanvas.version1(
                            1,
                            t17Program(font)));
                    session.execute(DrawCanvas.version1(
                            1,
                            t17ReuseProgram(font)));
                    return null;
                });
        requireCommitted(creation, "T17 Canvas product");
        if (!T17_CAPABILITY.equals(creation.getCapabilityId())) {
            throw new IllegalStateException(
                    "The T17 Canvas product did not report its capability identity");
        }
        return creation;
    }

    private static WorkflowOutcome<Void> createT18Product(Path target)
            throws Exception {
        byte[] source = t18Source();
        final byte[] jpeg = encodeT18Image(t18Raster(false), "jpeg");
        final byte[] png = encodeT18Image(t18Raster(true), "png");
        final byte[] tiff = encodeT18Image(t18Raster(false), "tiff");
        final byte[] profile = ICC_Profile.getInstance(ColorSpace.CS_sRGB).getData();
        WorkflowOutcome<Void> creation = new DocumentWorkflow().execute(
                WorkflowRequest.builder()
                        .source("input", DocumentSource.bytes(source, source.length))
                        .primarySource("input")
                        .target("output", PublicationTarget.path(target))
                        .saveMode(SaveMode.REWRITE)
                        .build(),
                session -> {
                    DocumentResourceInventory inventory = session.query(
                            ExtractImagesAndResources.version1(
                                    t18ExtractionLimits(),
                                    ImageByteAccess.NONE));
                    if (inventory.getImages().size() != 1
                            || !inventory.getImages().get(0)
                                    .getObjectReference().isPresent()) {
                        throw new IllegalStateException(
                                "The T18 source did not expose its existing Image Resource");
                    }
                    CanvasImage existing = CanvasImage.existing(
                            inventory.getImages().get(0)
                                    .getObjectReference().get());
                    session.execute(DrawCanvas.version2(
                            1,
                            t18Program(jpeg, png, tiff, profile, existing),
                            t18CanvasLimits()));
                    return null;
                });
        requireCommitted(creation, "T18 Canvas product");
        if (!T18_CAPABILITY.equals(creation.getCapabilityId())) {
            throw new IllegalStateException(
                    "The T18 Canvas product did not report its capability identity");
        }
        return creation;
    }

    private static CanvasProgram t18Program(
            byte[] jpeg,
            byte[] png,
            byte[] tiff,
            byte[] profile,
            CanvasImage existing) {
        CanvasColorSpace calGray = CanvasColorSpace.calibratedGray(
                new double[] {0.9505d, 1d, 1.089d},
                new double[] {0d, 0d, 0d},
                2.2d);
        CanvasColorSpace calRgb = CanvasColorSpace.calibratedRgb(
                new double[] {0.9505d, 1d, 1.089d},
                new double[] {0d, 0d, 0d},
                new double[] {2.2d, 2.2d, 2.2d},
                new double[] {
                    0.4124d, 0.2126d, 0.0193d,
                    0.3576d, 0.7152d, 0.1192d,
                    0.1805d, 0.0722d, 0.9505d
                });
        CanvasColorSpace icc = CanvasColorSpace.iccBased(profile);
        CanvasImage rawIcc = CanvasImage.rawSamples(
                2,
                2,
                8,
                icc,
                new byte[] {
                    (byte) 246, 80, 42,
                    45, (byte) 194, 82,
                    48, 96, (byte) 232,
                    (byte) 242, (byte) 205, 48
                });
        CanvasImage explicitMask = CanvasImage.rawSamples(
                2,
                2,
                8,
                CanvasColorSpace.deviceRgb(),
                new byte[] {
                    (byte) 222, 48, 72, 42, (byte) 190, 92,
                    45, 86, (byte) 220, (byte) 236, (byte) 190, 40
                }).withExplicitMask(CanvasMask.explicit(
                        2, 2, false, new byte[] {(byte) 0x80, (byte) 0x40}));
        CanvasImage softMask = CanvasImage.rawSamples(
                2,
                2,
                8,
                CanvasColorSpace.deviceRgb(),
                new byte[] {
                    40, 100, (byte) 230, 40, 100, (byte) 230,
                    40, 100, (byte) 230, 40, 100, (byte) 230
                }).withSoftMask(CanvasMask.soft(
                        2,
                        2,
                        new byte[] {0, 90, (byte) 180, (byte) 255}));
        CanvasProgram.Builder groupProgram = CanvasProgram.version2()
                .setFillColor(CanvasColor.rgb(0.95d, 0.32d, 0.18d));
        rectangle(groupProgram, 0d, 0d, 56d, 56d)
                .fill(CanvasWindingRule.NONZERO)
                .setFillColor(CanvasColor.of(calGray, 0.55d));
        rectangle(groupProgram, 22d, 22d, 56d, 56d)
                .fill(CanvasWindingRule.NONZERO);
        CanvasTransparencyGroup group = CanvasTransparencyGroup.version1(
                CanvasRectangle.of(0d, 0d, 78d, 78d),
                CanvasColorSpace.deviceRgb(),
                true,
                false,
                groupProgram.build());
        CanvasTransparencyState multiply = CanvasTransparencyState.version1(
                0.72d,
                0.84d,
                CanvasBlendMode.MULTIPLY);

        CanvasProgram.Builder program = CanvasProgram.version2()
                .setFillColor(CanvasColor.gray(0.93d));
        rectangle(program, 30d, 360d, 552d, 360d)
                .fill(CanvasWindingRule.NONZERO)
                .setFillColor(CanvasColor.rgb(0.10d, 0.38d, 0.78d));
        rectangle(program, 48d, 672d, 76d, 28d)
                .fill(CanvasWindingRule.NONZERO)
                .setFillColor(CanvasColor.cmyk(0.05d, 0.75d, 0.70d, 0.05d));
        rectangle(program, 142d, 672d, 76d, 28d)
                .fill(CanvasWindingRule.NONZERO)
                .setFillColor(CanvasColor.of(calRgb, 0.15d, 0.70d, 0.28d));
        rectangle(program, 236d, 672d, 76d, 28d)
                .fill(CanvasWindingRule.NONZERO)
                .setStrokeColor(CanvasColor.of(calGray, 0.18d))
                .moveTo(330d, 686d)
                .lineTo(406d, 686d)
                .stroke()
                .setStrokeColor(CanvasColor.of(icc, 0.64d, 0.12d, 0.74d))
                .moveTo(424d, 686d)
                .lineTo(550d, 686d)
                .stroke()
                .drawImage(CanvasImage.jpeg(jpeg), t18Placement(48d, 540d))
                .drawImage(CanvasImage.jpeg(jpeg), t18Placement(48d, 540d))
                .drawImage(CanvasImage.png(png), t18Placement(164d, 540d))
                .drawImage(CanvasImage.tiff(tiff), t18Placement(280d, 540d))
                .drawImage(rawIcc, t18Placement(396d, 540d))
                .drawImage(existing, t18Placement(48d, 408d))
                .drawImage(existing, t18Placement(48d, 408d))
                .drawImage(explicitMask, t18Placement(164d, 408d))
                .drawImage(softMask, t18Placement(280d, 408d))
                .saveState()
                .setTransparency(multiply)
                .setTransparency(multiply)
                .drawTransparencyGroup(
                        group,
                        CanvasMatrix.of(1d, 0d, 0d, 1d, 408d, 416d))
                .restoreState()
                .drawTransparencyGroup(
                        group,
                        CanvasMatrix.of(1d, 0d, 0d, 1d, 484d, 416d));
        return program.build();
    }

    private static CanvasProgram.Builder rectangle(
            CanvasProgram.Builder program,
            double x,
            double y,
            double width,
            double height) {
        return program.moveTo(x, y)
                .lineTo(x + width, y)
                .lineTo(x + width, y + height)
                .lineTo(x, y + height)
                .closePath();
    }

    private static CanvasMatrix t18Placement(double x, double y) {
        return CanvasMatrix.of(96d, 0d, 0d, 96d, x, y);
    }

    private static CanvasResourceLimits t18CanvasLimits() {
        return CanvasResourceLimits.builder()
                .maximumEncodedImageBytes(4L * 1024L * 1024L)
                .maximumDecodedImagePixels(1024L * 1024L)
                .maximumDecodedImageBytes(8L * 1024L * 1024L)
                .maximumIccProfileBytes(1024L * 1024L)
                .maximumMaskBytes(1024L * 1024L)
                .maximumGeneratedContentBytes(1024L * 1024L)
                .maximumResourceDeclarations(64)
                .maximumTransparencyGroupDepth(4)
                .build();
    }

    private static ResourceExtractionLimits t18ExtractionLimits() {
        return ResourceExtractionLimits.builder()
                .maximumPages(4)
                .maximumPageTreeNodes(32)
                .maximumTraversedResourceValues(4096L)
                .maximumResourceTraversalDepth(24)
                .maximumDecodedPixels(4L * 1024L * 1024L)
                .maximumDecompressedBytes(32L * 1024L * 1024L)
                .maximumReturnedBytes(32L * 1024L * 1024L)
                .build();
    }

    private static BufferedImage t18Raster(boolean alpha) {
        BufferedImage image = new BufferedImage(
                4,
                4,
                alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int opacity = alpha && x == 1 && y == 1 ? 88 : 255;
                Color color;
                if (x < 2 && y < 2) {
                    color = new Color(232, 55, 45, opacity);
                } else if (x >= 2 && y < 2) {
                    color = new Color(42, 194, 82, opacity);
                } else if (x < 2) {
                    color = new Color(42, 92, 226, opacity);
                } else {
                    color = new Color(242, 204, 42, opacity);
                }
                image.setRGB(x, y, color.getRGB());
            }
        }
        return image;
    }

    private static byte[] encodeT18Image(BufferedImage image, String format)
            throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, format, output)) {
            throw new IOException("No ImageIO writer is available for " + format);
        }
        return output.toByteArray();
    }

    private static byte[] t18Source() throws IOException {
        String[] objects = {
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                    + "/Resources << /XObject << /Seed 5 0 R >> "
                    + "/FolioKeep /Kept >> /Contents 4 0 R >>",
            t14StreamObject(
                    "q 0.72 0.72 0.72 RG 1 w 24 24 m 588 24 l "
                            + "588 768 l 24 768 l h S Q\n",
                    ""),
            t14StreamObject(
                    "RGBYMCBRYGMC",
                    "/Type /XObject /Subtype /Image /Width 2 /Height 2 "
                            + "/ColorSpace /DeviceRGB /BitsPerComponent 8 ")
        };
        return pdfSource(objects);
    }

    private static CanvasProgram t17Program(CanvasFont font) {
        CanvasProgram.Builder program = CanvasProgram.version1()
                .saveState()
                .transform(CanvasMatrix.of(1d, 0d, 0d, 1d, 6d, 8d))
                .moveTo(10d, 10d)
                .lineTo(80d, 10d)
                .stroke()
                .saveState()
                .moveTo(10d, 20d)
                .curveTo(20d, 30d, 40d, 70d, 80d, 90d)
                .fill(CanvasWindingRule.NONZERO)
                .restoreState()
                .moveTo(100d, 20d).lineTo(160d, 20d)
                .lineTo(160d, 80d).lineTo(100d, 80d).closePath()
                .moveTo(115d, 35d).lineTo(145d, 35d)
                .lineTo(145d, 65d).lineTo(115d, 65d).closePath()
                .fill(CanvasWindingRule.EVEN_ODD)
                .saveState()
                .moveTo(0d, 0d).lineTo(200d, 0d)
                .lineTo(200d, 200d).closePath()
                .clip(CanvasWindingRule.NONZERO)
                .moveTo(0d, 100d).lineTo(200d, 100d).stroke()
                .restoreState()
                .saveState()
                .moveTo(0d, 0d).lineTo(200d, 0d)
                .lineTo(200d, 200d).closePath()
                .clip(CanvasWindingRule.EVEN_ODD)
                .moveTo(100d, 0d).lineTo(100d, 200d).stroke()
                .restoreState()
                .restoreState();
        TextRenderingMode[] modes = TextRenderingMode.values();
        for (int index = 0; index < modes.length; index++) {
            program.saveState()
                    .beginText(
                            font,
                            14d,
                            modes[index],
                            CanvasMatrix.of(
                                    1d, 0d, 0d, 1d,
                                    30d + index * 25d,
                                    120d + index * 8d))
                    .showGlyph(new byte[] {65})
                    .endText()
                    .restoreState();
        }
        return program.build();
    }

    private static CanvasProgram t17ReuseProgram(CanvasFont font) {
        return CanvasProgram.version1()
                .beginText(
                        font,
                        14d,
                        TextRenderingMode.FILL,
                        CanvasMatrix.of(1d, 0d, 0d, 1d, 240d, 40d))
                .showGlyph(new byte[] {65})
                .endText()
                .build();
    }

    private static byte[] t17Source() throws IOException {
        String existing = "q\n2 2 m\n3 3 l\nS\nQ\n";
        String[] objects = {
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 320 320] "
                    + "/Resources << /Font << /F1 5 0 R >> "
                    + "/FolioKeep /Kept >> /Contents 4 0 R >>",
            t13StreamObject(existing, ""),
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica "
                    + "/Encoding /WinAnsiEncoding >>"
        };
        return pdfSource(objects);
    }

    private static char[] t16OwnerPassword() {
        return new char[] {
            'f', 'o', 'l', 'i', 'o', '-', 't', '1', '6', '-',
            'o', 'w', 'n', 'e', 'r', '-', 'e', 'v', 'i', 'd', 'e', 'n', 'c', 'e'
        };
    }

    private static char[] t16UserPassword() {
        return new char[] {
            'f', 'o', 'l', 'i', 'o', '-', 't', '1', '6', '-',
            'u', 's', 'e', 'r', '-', 'e', 'v', 'i', 'd', 'e', 'n', 'c', 'e'
        };
    }

    private static void requireCommitted(
            WorkflowOutcome<?> outcome,
            String product) {
        if (outcome.getPublicationReceipts().size() != 1
                || outcome.getPublicationReceipts().get(0).getStatus()
                        != PublicationStatus.COMMITTED) {
            throw new IllegalStateException(product + " was not committed");
        }
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

    private static void createT13Products(Path pageText, Path tagged)
            throws Exception {
        createT13PageTextProduct(pageText);
        createT13TaggedProduct(tagged);
    }

    private static void createT13PageTextProduct(Path target)
            throws Exception {
        WorkflowOutcome<TextStructureExtraction> outcome =
                new DocumentWorkflow().execute(
                        WorkflowRequest.create(target, SaveMode.REWRITE),
                        session -> {
                            session.execute(AddBlankPage.INSTANCE);
                            session.execute(AddBlankPage.INSTANCE);
                            ObjectReference first = session.query(
                                    PageObjectReference.version1(1));
                            ObjectReference second = session.query(
                                    PageObjectReference.version1(2));
                            PdfDictionary resources = t13Resources();
                            session.execute(DocumentPatch.builder()
                                    .setDictionaryEntry(
                                            first,
                                            PdfName.of("Resources"),
                                            resources)
                                    .setDictionaryEntry(
                                            first,
                                            PdfName.of("Contents"),
                                            PdfArray.of(
                                                    t13Content(
                                                            "BT /F1 12 Tf (A) Tj ET\n"),
                                                    t13Content(
                                                            "BT /F1 12 Tf (B) Tj ET\n")))
                                    .setDictionaryEntry(
                                            second,
                                            PdfName.of("Resources"),
                                            resources)
                                    .setDictionaryEntry(
                                            second,
                                            PdfName.of("Contents"),
                                            t13Content(
                                                    "BT /F1 10 Tf 0 1 -1 0 "
                                                            + "100 200 Tm (C) Tj ET\n"))
                                    .setDictionaryEntry(
                                            second,
                                            PdfName.of("Rotate"),
                                            PdfNumber.of(90L))
                                    .build());
                            return session.query(
                                    ExtractTextAndStructure.version1(
                                            t13Limits()));
                        });
        if (outcome.getResult().getPages().size() != 2
                || !"AB".equals(outcome.getResult().getPages().get(0).getText())
                || !"C".equals(outcome.getResult().getPages().get(1).getText())) {
            throw new IllegalStateException(
                    "T13 page-text product did not satisfy its public query probe");
        }
    }

    private static void createT13TaggedProduct(Path target)
            throws Exception {
        byte[] source = t13TaggedSource();
        WorkflowOutcome<TextStructureExtraction> outcome =
                new DocumentWorkflow().execute(
                        WorkflowRequest.builder()
                                .source(
                                        "input",
                                        DocumentSource.bytes(
                                                source, source.length))
                                .primarySource("input")
                                .target(
                                        "output",
                                        PublicationTarget.path(target))
                                .saveMode(SaveMode.REWRITE)
                                .build(),
                        session -> {
                            return session.query(
                                    ExtractTextAndStructure.version1(
                                            t13Limits()));
                        });
        if (outcome.getResult().getStructureRoots().size() != 1
                || !"Tagged".equals(
                        outcome.getResult().getPages().get(0).getText())
                || !"T13 alternate".equals(outcome.getResult()
                        .getStructureRoots().get(0)
                        .getAlternateText().orElse(null))
                || !"Sect".equals(outcome.getResult()
                        .getStructureRoots().get(0)
                        .getResolvedRole().orElse(null))
                || !outcome.getResult().getStructureRoots().get(0)
                        .getChildren().get(0).getMarkedContent().get()
                        .getMarkedContentSequenceId().isPresent()) {
            throw new IllegalStateException(
                    "T13 tagged product did not satisfy its public query probe");
        }
    }

    private static byte[] t13TaggedSource() throws IOException {
        String[] objects = {
            "<< /Type /Catalog /Pages 2 0 R /StructTreeRoot 6 0 R "
                    + "/MarkInfo << /Marked true >> /Lang (en-US) >>",
            "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                    + "/Resources << /Font << /F1 5 0 R >> >> "
                    + "/Contents 4 0 R /StructParents 0 /Tabs /S >>",
            t13StreamObject(
                    "/Span <</MCID 0 /ActualText (Tagged)>> BDC "
                            + "BT /F1 12 Tf (Visible) Tj ET EMC\n",
                    ""),
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica "
                    + "/Encoding /WinAnsiEncoding /ToUnicode 9 0 R >>",
            "<< /Type /StructTreeRoot /K 7 0 R "
                    + "/RoleMap << /AcceptanceSection /Sect >> "
                    + "/ParentTree 8 0 R /ParentTreeNextKey 1 >>",
            "<< /Type /StructElem /S /AcceptanceSection /P 6 0 R "
                    + "/Pg 3 0 R /Lang (en-GB) /Alt (T13 alternate) "
                    + "/K 0 >>",
            "<< /Nums [0 [7 0 R]] >>",
            t13StreamObject(t13ToUnicodeCMap(), "")
        };
        return pdfSource(objects);
    }

    private static void createT14Products(Path imageFont, Path formMask)
            throws Exception {
        createT14ImageFontProduct(imageFont);
        createT14FormMaskProduct(formMask);
    }

    private static void createT14ImageFontProduct(Path target)
            throws Exception {
        DocumentResourceInventory inventory = createT14Product(
                target,
                t14ImageFontSource());
        if (inventory.getImages().size() != 1
                || inventory.getFonts().size() != 1) {
            throw new IllegalStateException(
                    "T14 image/font product did not expose its inventory");
        }
        ImageResource image = inventory.getImages().get(0);
        FontResource font = inventory.getFonts().get(0);
        if (image.getWidth() != 1
                || image.getHeight() != 1
                || image.getColorSpace().getFamily()
                        != ImageResource.ColorFamily.DEVICE_RGB
                || !PdfName.of("BrandRGB").equals(image.getColorSpace()
                        .getDeclaredName().orElse(null))
                || image.getFilters().size() != 1
                || !PdfName.of("ASCIIHexDecode").equals(
                        image.getFilters().get(0).getName())
                || !Arrays.equals(
                        "524742>".getBytes(StandardCharsets.US_ASCII),
                        image.getEncodedData().getBytes().orElse(null))
                || !Arrays.equals(
                        "RGB".getBytes(StandardCharsets.US_ASCII),
                        image.getDecodedData().getBytes().orElse(null))
                || !Arrays.asList(Integer.valueOf(1)).equals(
                        image.getPageUsage())
                || !font.isSubset()
                || !"ABCDEF".equals(font.getSubsetPrefix().orElse(null))
                || font.getEmbedding() != FontResource.Embedding.NOT_EMBEDDED
                || !PdfName.of("ABCDEF+Helvetica").equals(
                        font.getBaseFontName().orElse(null))) {
            throw new IllegalStateException(
                    "T14 image/font product did not satisfy its public query probe");
        }
    }

    private static void createT14FormMaskProduct(Path target)
            throws Exception {
        DocumentResourceInventory inventory = createT14Product(
                target,
                t14FormMaskSource());
        if (inventory.getImages().size() != 2) {
            throw new IllegalStateException(
                    "T14 Form/mask product did not expose both images");
        }
        ImageResource image = inventory.getImages().get(0);
        ImageResource softMask = inventory.getImages().get(1);
        boolean hasForm = false;
        for (DocumentResource resource : inventory.getResources()) {
            hasForm |= resource.getKind() == DocumentResource.Kind.FORM;
        }
        if (!hasForm
                || image.getWidth() != 2
                || image.getHeight() != 1
                || softMask.getColorSpace().getFamily()
                        != ImageResource.ColorFamily.DEVICE_GRAY
                || !image.getSoftMask().isPresent()
                || image.getSoftMask().get().getKind()
                        != ImageResource.Mask.Kind.SOFT_IMAGE
                || image.getSoftMask().get().getImage().orElse(null) != softMask
                || !Arrays.equals(
                        "RGBRGB".getBytes(StandardCharsets.US_ASCII),
                        image.getDecodedData().getBytes().orElse(null))
                || !Arrays.equals(
                        "AZ".getBytes(StandardCharsets.US_ASCII),
                        softMask.getDecodedData().getBytes().orElse(null))) {
            throw new IllegalStateException(
                    "T14 Form/mask product did not satisfy its public query probe");
        }
    }

    private static DocumentResourceInventory createT14Product(
            Path target,
            byte[] source) throws Exception {
        WorkflowOutcome<DocumentResourceInventory> outcome =
                new DocumentWorkflow().execute(
                        WorkflowRequest.builder()
                                .source(
                                        "input",
                                        DocumentSource.bytes(
                                                source, source.length))
                                .primarySource("input")
                                .target(
                                        "output",
                                        PublicationTarget.path(target))
                                .saveMode(SaveMode.REWRITE)
                                .build(),
                        session -> session.query(
                                ExtractImagesAndResources.version1(
                                        t14Limits(),
                                        ImageByteAccess.ENCODED_AND_DECODED)));
        if (outcome.getPublicationReceipts().size() != 1
                || outcome.getPublicationReceipts().get(0).getStatus()
                        != PublicationStatus.COMMITTED) {
            throw new IllegalStateException(
                    "T14 acceptance product was not committed");
        }
        return outcome.getResult();
    }

    private static byte[] t14ImageFontSource() throws IOException {
        String[] objects = {
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                    + "/Resources << /XObject << /ImFront 5 0 R >> "
                    + "/Font << /F1 6 0 R >> "
                    + "/ColorSpace << /BrandRGB /DeviceRGB >> >> "
                    + "/Contents 4 0 R >>",
            t14StreamObject(
                    "q 100 0 0 100 36 620 cm /ImFront Do Q\n"
                            + "BT /F1 12 Tf 36 600 Td (T14) Tj ET\n",
                    ""),
            t14StreamObject(
                    "524742>",
                    "/Type /XObject /Subtype /Image /Width 1 /Height 1 "
                            + "/ColorSpace /BrandRGB /BitsPerComponent 8 "
                            + "/Filter /ASCIIHexDecode "),
            "<< /Type /Font /Subtype /Type1 "
                    + "/BaseFont /ABCDEF+Helvetica >>"
        };
        return pdfSource(objects);
    }

    private static byte[] t14FormMaskSource() throws IOException {
        String[] objects = {
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                    + "/Resources << /XObject << /Nested 5 0 R >> >> "
                    + "/Contents 4 0 R >>",
            t14StreamObject("q /Nested Do Q\n", ""),
            t14StreamObject(
                    "q /ImNested Do Q\n",
                    "/Type /XObject /Subtype /Form /BBox [0 0 10 10] "
                            + "/Resources << /XObject << /ImNested 6 0 R >> >> "),
            t14StreamObject(
                    "RGBRGB",
                    "/Type /XObject /Subtype /Image /Width 2 /Height 1 "
                            + "/ColorSpace /DeviceRGB /BitsPerComponent 8 "
                            + "/SMask 7 0 R "),
            t14StreamObject(
                    "AZ",
                    "/Type /XObject /Subtype /Image /Width 2 /Height 1 "
                            + "/ColorSpace /DeviceGray /BitsPerComponent 8 ")
        };
        return pdfSource(objects);
    }

    private static String t14StreamObject(String data, String entries) {
        return "<< " + entries + "/Length "
                + data.getBytes(StandardCharsets.US_ASCII).length
                + " >>\nstream\n" + data + "\nendstream";
    }

    private static ResourceExtractionLimits t14Limits() {
        return ResourceExtractionLimits.builder()
                .maximumPages(4)
                .maximumPageTreeNodes(32)
                .maximumTraversedResourceValues(256L)
                .maximumResourceTraversalDepth(16)
                .maximumDecodedPixels(4096L)
                .maximumDecompressedBytes(64L * 1024L)
                .maximumReturnedBytes(64L * 1024L)
                .build();
    }

    private static byte[] pdfSource(String[] objects) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write("%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII));
        int[] offsets = new int[objects.length + 1];
        for (int index = 0; index < objects.length; index++) {
            offsets[index + 1] = output.size();
            output.write(((index + 1) + " 0 obj\n")
                    .getBytes(StandardCharsets.US_ASCII));
            output.write(objects[index].getBytes(StandardCharsets.US_ASCII));
            output.write("\nendobj\n".getBytes(StandardCharsets.US_ASCII));
        }
        int xref = output.size();
        output.write(("xref\n0 " + offsets.length + "\n")
                .getBytes(StandardCharsets.US_ASCII));
        output.write("0000000000 65535 f \n"
                .getBytes(StandardCharsets.US_ASCII));
        for (int index = 1; index < offsets.length; index++) {
            output.write(String.format(
                    Locale.ROOT,
                    "%010d 00000 n \n",
                    Integer.valueOf(offsets[index]))
                    .getBytes(StandardCharsets.US_ASCII));
        }
        output.write(("trailer\n<< /Size " + offsets.length
                + " /Root 1 0 R >>\nstartxref\n" + xref
                + "\n%%EOF\n").getBytes(StandardCharsets.US_ASCII));
        return output.toByteArray();
    }

    private static String t13StreamObject(String data, String entries) {
        return "<< " + entries + "/Length "
                + data.getBytes(StandardCharsets.US_ASCII).length
                + " >>\nstream\n" + data + "endstream";
    }

    private static PdfDictionary t13Resources() {
        String cmap = t13ToUnicodeCMap();
        PdfDictionary font = PdfDictionary.builder()
                .put(PdfName.of("Type"), PdfName.of("Font"))
                .put(PdfName.of("Subtype"), PdfName.of("Type1"))
                .put(PdfName.of("BaseFont"), PdfName.of("Helvetica"))
                .put(PdfName.of("Encoding"), PdfName.of("WinAnsiEncoding"))
                .put(PdfName.of("ToUnicode"), PdfStream.of(
                        PdfDictionary.builder().build(),
                        cmap.getBytes(StandardCharsets.US_ASCII)))
                .build();
        return PdfDictionary.builder()
                .put(PdfName.of("Font"), PdfDictionary.builder()
                        .put(PdfName.of("F1"), font)
                        .build())
                .build();
    }

    private static String t13ToUnicodeCMap() {
        return "/CIDInit /ProcSet findresource begin\n"
                + "12 dict begin\nbegincmap\n"
                + "/CMapName /FolioT13Acceptance def\n/CMapType 2 def\n"
                + "1 begincodespacerange\n<00> <FF>\nendcodespacerange\n"
                + "3 beginbfrange\n<41> <4A> <0041>\n"
                + "<54> <63> <0054>\n<64> <77> <0064>\n"
                + "endbfrange\n"
                + "endcmap\nend\nend\n";
    }

    private static PdfStream t13Content(String operators) {
        return PdfStream.of(
                PdfDictionary.builder().build(),
                operators.getBytes(StandardCharsets.US_ASCII));
    }

    private static ExtractionLimits t13Limits() {
        return ExtractionLimits.builder()
                .maximumPages(4)
                .maximumPageTreeNodes(32)
                .maximumContentStreams(16)
                .maximumContentStreamDepth(4)
                .maximumDecodedBytes(64L * 1024L)
                .maximumTextItems(256)
                .maximumUnicodeCodePoints(4096)
                .maximumMarkedContentSequences(64)
                .maximumMarkedContentDepth(8)
                .maximumStructureElements(64)
                .maximumStructureItems(128)
                .maximumStructureDepth(16)
                .maximumRoleMappings(32)
                .maximumToUnicodeMappings(512)
                .maximumFontDataEntries(512)
                .build();
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
                + chain.hashPolicy.inputSetHashMetadata(inputSetHash)
                + chain.hashPolicy.policyMetadata()
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
                .append(chain.hashPolicy.inputSetHashMetadata(inputSetHash))
                .append(chain.hashPolicy.policyMetadata())
                .append(metadata("Tool", "qpdf"))
                .append(metadata("Tool version", qpdfPin.version()))
                .append(metadata("Distribution SHA-256", qpdfPin.archiveSha256()))
                .append("Final determination: `")
                .append(result.recordValue())
                .append("`\n\n");
        for (int index = 0; index < checks.size(); index++) {
            ProductQpdfResult check = checks.get(index);
            findings.append("## ").append(check.artifactName).append("\n\n")
                    .append(chain.hashPolicy.inputHashMetadata(check.inputHash))
                    .append("Invocation: `")
                    .append(chain.qpdfInvocation(check.artifactName))
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
                    .append(index + 1 == checks.size()
                            ? finalFencedEnding(check.result.standardError)
                            : fencedEnding(check.result.standardError));
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
                + chain.hashPolicy.inputSetHashMetadata(inputSetHash)
                + chain.hashPolicy.policyMetadata()
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
