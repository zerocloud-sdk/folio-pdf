package net.zerocloud.pdf.acceptance;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import net.zerocloud.pdf.Annotation;
import net.zerocloud.pdf.AnnotationAppearance;
import net.zerocloud.pdf.AnnotationProperties;
import net.zerocloud.pdf.AnnotationRectangle;
import net.zerocloud.pdf.DocumentPatch;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.ObjectReference;
import net.zerocloud.pdf.PdfArray;
import net.zerocloud.pdf.PdfName;
import net.zerocloud.pdf.PdfNumber;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.command.UpdateAnnotations;
import net.zerocloud.pdf.composition.CanvasColor;
import net.zerocloud.pdf.composition.CanvasProgram;
import net.zerocloud.pdf.composition.CanvasResourceLimits;
import net.zerocloud.pdf.composition.CanvasWindingRule;
import net.zerocloud.pdf.composition.command.DrawCanvas;
import net.zerocloud.pdf.query.PageObjectReference;

/** Records T23 public Rendering against the separately pinned PDFium tool. */
public final class T23RenderingEvidenceCommand {
    private T23RenderingEvidenceCommand() { }

    /** Receives output, qpdf pin, PDFium pin, ImageMagick pin, profiles directory, and release train. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 6) { throw new IllegalArgumentException("Expected six T23 evidence arguments"); }
        Path output = Paths.get(arguments[0]);
        Path artifacts = output.resolve("artifacts");
        Files.createDirectories(artifacts);
        QpdfPin qpdf = QpdfPin.load(Paths.get(arguments[1]));
        PdfiumPin pdfium = PdfiumPin.load(Paths.get(arguments[2]));
        ImageMagickPin comparator = ImageMagickPin.load(Paths.get(arguments[3]));
        Path profiles = Paths.get(arguments[4]);
        createProduct(artifacts.resolve("T23-page-rendering.pdf"));
        Files.copy(artifacts.resolve("T18-canvas-images-colors-transparency.pdf"),
                artifacts.resolve("T23-page-rendering-images.pdf"), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(artifacts.resolve("T19-font-loading-embedding-subsetting.pdf"),
                artifacts.resolve("T23-page-rendering-fonts.pdf"), StandardCopyOption.REPLACE_EXISTING);
        for (String name : Arrays.asList("T23-page-rendering", "T23-page-rendering-images", "T23-page-rendering-fonts")) {
            VisualProfile profile = VisualProfile.load(profiles.resolve(name + "-visual.properties"));
            VisualEvidenceChain chain = VisualEvidenceChain.t23(name);
            Path pdf = artifacts.resolve(chain.inputArtifact());
            String hash = EvidenceFiles.idNeutralPdfSha256(pdf);
            EvidenceResult syntax = QpdfSyntaxRecorder.record(output, artifacts, qpdf, hash, arguments[5],
                    new QpdfSyntaxRecorder.Profile("T23", "conversion.rendering", name,
                            "capabilities/evidence/T23-page-rendering.md", chain.inputArtifact(),
                            name + "-syntax.md", name + "-qpdf.txt"));
            VisualEvidence evidence = VisualEvidenceRecorder.record(chain, pdf, hash,
                    artifacts, pdfium, comparator, profile, arguments[5]);
            EvidenceFiles.write(artifacts.resolve(chain.findingsName()), evidence.rawFindings());
            EvidenceFiles.write(output.resolve(chain.recordName()), evidence.record());
            System.out.println(name + ": syntax=" + syntax + ", visual=" + evidence.result());
        }
    }

    private static void createProduct(Path target) throws Exception {
        new DocumentWorkflow().execute(WorkflowRequest.create(target, SaveMode.REWRITE), session -> {
            session.execute(AddBlankPage.INSTANCE);
            ObjectReference page = session.query(PageObjectReference.version1(1));
            session.execute(DocumentPatch.builder().setDictionaryEntry(page, PdfName.of("MediaBox"),
                    PdfArray.of(PdfNumber.of(0), PdfNumber.of(0), PdfNumber.of(612), PdfNumber.of(792)))
                    .build());
            session.execute(DrawCanvas.version2(1, CanvasProgram.version2().setFillColor(CanvasColor.rgb(1, 0, 0))
                    .moveTo(0, 0).lineTo(306, 0).lineTo(306, 792).lineTo(0, 792).closePath()
                    .fill(CanvasWindingRule.NONZERO).build(), CanvasResourceLimits.builder()
                    .maximumEncodedImageBytes(0).maximumDecodedImagePixels(0).maximumDecodedImageBytes(0)
                    .maximumIccProfileBytes(0).maximumMaskBytes(0).maximumGeneratedContentBytes(4096)
                    .maximumResourceDeclarations(4).maximumTransparencyGroupDepth(0).build()));
            AnnotationAppearance appearance = AnnotationAppearance.version1(AnnotationRectangle.of(0, 0, 306, 396),
                    "0 1 0 rg 0 0 306 396 re f\n".getBytes(StandardCharsets.US_ASCII));
            session.execute(UpdateAnnotations.version1().put(Annotation.stamp(AnnotationProperties.version1(
                    "t23-visible-stamp", 1, AnnotationRectangle.of(306, 396, 612, 792))
                    .appearance(appearance).build(), "FolioRendering")).build());
            return null;
        });
    }
}
