package net.zerocloud.pdf.acceptance;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Records an independent PDFium/ImageMagick visual evidence chain. */
final class VisualEvidenceRecorder {

    static final String RECORD_NAME = "T07-document-blank-visual.md";
    static final String FINDINGS_NAME = "T07-document-blank-visual.txt";
    static final String EXPECTED_RASTER_NAME = "T07-document-blank-expected.png";
    static final String PDFIUM_RASTER_NAME = "T07-document-blank-pdfium.png";
    static final String IMPLEMENTATION_RASTER_NAME =
            "T07-document-blank-implementation.png";
    static final String DIFFERENCE_RASTER_NAME =
            "T07-document-blank-difference.png";
    static final String RENDERER_DIFFERENCE_RASTER_NAME =
            "T07-document-blank-renderer-difference.png";

    private static final Pattern AE_METRIC = Pattern.compile(
            "^([0-9]+(?:\\.[0-9]+)?)(?:\\s+\\([^\\r\\n]*\\))?\\s*$");

    private VisualEvidenceRecorder() {
    }

    static VisualEvidence record(
            Path pdf,
            String inputHash,
            Path artifacts,
            PdfiumPin pdfiumPin,
            ImageMagickPin imageMagickPin,
            VisualProfile profile,
            String releaseTrain) throws IOException {
        return record(
                VisualEvidenceChain.t03(),
                pdf,
                inputHash,
                artifacts,
                pdfiumPin,
                imageMagickPin,
                profile,
                releaseTrain);
    }

    static VisualEvidence record(
            VisualEvidenceChain chain,
            Path pdf,
            String inputHash,
            Path artifacts,
            PdfiumPin pdfiumPin,
            ImageMagickPin imageMagickPin,
            VisualProfile profile,
            String releaseTrain) throws IOException {
        RunState state = new RunState(
                chain,
                inputHash,
                pdfiumPin,
                imageMagickPin,
                profile,
                releaseTrain);
        Path expected = artifacts.resolve(chain.expectedRasterName());
        Path actual = artifacts.resolve(chain.pdfiumRasterName());
        Path implementation = artifacts.resolve(chain.implementationRasterName());
        Path difference = artifacts.resolve(chain.differenceRasterName());
        Path rendererDifference = artifacts.resolve(
                chain.rendererDifferenceRasterName());
        clearOutputs(expected, actual, implementation, difference, rendererDifference);

        if (chain.usesPublicRendering() && !inputHash.equals(profile.inputIdNeutralSha256())) {
            return indeterminate(state, "The T23 input did not match its pinned ID-neutral SHA-256.", artifacts);
        }

        try {
            state.expectedHash = EvidenceFiles.sha256(profile.expectedRaster());
            if (!profile.expectedRasterSha256().equals(state.expectedHash)) {
                return indeterminate(
                        state,
                        "The project-owned expected raster did not match its pinned SHA-256.",
                        artifacts);
            }
            PngRaster.requireProfileRaster(
                    profile.expectedRaster(),
                    profile.rasterWidth(),
                    profile.rasterHeight());
            Files.copy(
                    profile.expectedRaster(),
                    expected,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException unusableExpected) {
            state.transcript.append("Expected raster validation: `indeterminate`\n\n")
                    .append(safeMessage(unusableExpected)).append("\n\n");
            return indeterminate(
                    state,
                    "The project-owned expected raster was missing or unusable.",
                    artifacts);
        }

        ProcessResult pdfiumVersion;
        try {
            pdfiumVersion = ExternalProcess.run(
                    pdfiumPin.executable(), artifacts, "--version");
            appendInvocation(
                    state.transcript,
                    "PDFium identity",
                    "pdfium --version",
                    pdfiumVersion);
        } catch (IOException unavailable) {
            state.transcript.append("PDFium identity: tool unavailable.\n\n");
            return indeterminate(
                    state,
                    "The pinned PDFium renderer was unavailable.",
                    artifacts);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            state.transcript.append("PDFium identity: process interrupted.\n\n");
            return indeterminate(
                    state,
                    "The pinned PDFium renderer was interrupted.",
                    artifacts);
        }
        state.observedPdfiumVersion = pdfiumVersion(pdfiumVersion.combinedOutput());
        if (pdfiumVersion.exitCode != 0
                || !pdfiumPin.cliVersion().equals(state.observedPdfiumVersion)) {
            return indeterminate(
                    state,
                    "Expected pinned PDFium CLI version `" + pdfiumPin.cliVersion()
                            + "`; observed `" + state.observedPdfiumVersion + "`.",
                    artifacts);
        }

        ProcessResult render;
        try {
            List<String> arguments = new ArrayList<String>(Arrays.asList(
                    "render",
                    pdf.getFileName().toString(),
                    chain.pdfiumRasterName(),
                    "--dpi",
                    Integer.toString(profile.dpi()),
                    "--file-type",
                    "png",
                    "--pages",
                    profile.pageNumber() == 1 ? "first" : Integer.toString(profile.pageNumber())));
            if (chain.usesPublicRendering()) { arguments.add("--render-annotations"); }
            render = ExternalProcess.run(pdfiumPin.executable(), artifacts, arguments.toArray(new String[0]));
            appendInvocation(
                    state.transcript,
                    "PDFium render",
                    "pdfium render " + pdf.getFileName() + " "
                            + chain.pdfiumRasterName() + " --dpi " + profile.dpi()
                            + " --file-type png --pages "
                            + (profile.pageNumber() == 1 ? "first" : Integer.toString(profile.pageNumber()))
                            + (chain.usesPublicRendering() ? " --render-annotations" : ""),
                    render);
        } catch (IOException unavailable) {
            state.transcript.append("PDFium render: tool unavailable.\n\n");
            return indeterminate(
                    state,
                    "The pinned PDFium renderer was unavailable during rendering.",
                    artifacts);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            state.transcript.append("PDFium render: process interrupted.\n\n");
            return indeterminate(
                    state,
                    "The pinned PDFium renderer was interrupted during rendering.",
                    artifacts);
        }
        if (render.exitCode != 0) {
            return indeterminate(
                    state,
                    "PDFium returned an unexpected render status `"
                            + render.exitCode + "`.",
                    artifacts);
        }
        try {
            PngRaster.requireProfileRaster(
                    actual,
                    profile.rasterWidth(),
                    profile.rasterHeight());
            state.actualHash = EvidenceFiles.sha256(actual);
        } catch (IOException unusableRaster) {
            state.transcript.append("PDFium raster validation: `indeterminate`\n\n")
                    .append(safeMessage(unusableRaster)).append("\n\n");
            return indeterminate(
                    state,
                    "PDFium produced a malformed or wrong-sized raster.",
                    artifacts);
        }

        try {
            state.implementationVersion = chain.usesPublicRendering()
                    ? PublicRenderingRenderer.render(pdf, implementation, profile)
                    : ImplementationRenderer.render(pdf, implementation, profile);
            PngRaster.requireProfileRaster(
                    implementation,
                    profile.rasterWidth(),
                    profile.rasterHeight());
            state.implementationHash = EvidenceFiles.sha256(implementation);
            if (chain.usesPublicRendering()) {
                state.transcript.append("Implementation under test: `DocumentWorkflow.execute` / `RenderPage.version1`; "
                        + "Provider `folio.pdfbox-renderer`; PNG consumed through `RenderedPage.writePngTo`.\n\n");
                state.transcript.append("Pinned/observed diagnostics: `")
                        .append(profile.renderDiagnostics()).append("`.\n\n")
                        .append("Scale: `1`; page selection: `1`; alpha: `OPAQUE`; annotations: `SHOW`; ")
                        .append("rounding: `").append(VisualProfile.T23_ROUNDING).append("`.\n\n");
            }
            state.transcript.append(chain.usesPublicRendering()
                    ? "Default Rendering Provider engine: `Apache PDFBox "
                    : "Secondary implementation renderer: `Apache PDFBox ")
                    .append(state.implementationVersion)
                    .append("` at `")
                    .append(profile.dpi())
                    .append(" DPI RGB`\n\n");
        } catch (IOException unusableSecondary) {
            state.transcript.append(chain.usesPublicRendering()
                    ? "Public Rendering validation: `indeterminate`\n\n"
                    : "Secondary renderer validation: `indeterminate`\n\n")
                    .append(safeMessage(unusableSecondary)).append("\n\n");
            return indeterminate(
                    state,
                    chain.usesPublicRendering()
                            ? "The public Rendering output was unusable."
                            : "The secondary implementation-renderer evidence was unusable.",
                    artifacts);
        } catch (RuntimeException unexpectedSecondary) {
            state.transcript.append(chain.usesPublicRendering()
                    ? "Public Rendering validation: `indeterminate`\n\n"
                    : "Secondary renderer validation: `indeterminate`\n\n");
            return indeterminate(
                    state,
                    chain.usesPublicRendering()
                            ? "The public Rendering path ended unexpectedly."
                            : "The secondary implementation renderer ended unexpectedly.",
                    artifacts);
        }

        ProcessResult imageMagickVersion;
        try {
            imageMagickVersion = ExternalProcess.run(
                    imageMagickPin.executable(),
                    artifacts,
                    "--version");
            appendInvocation(
                    state.transcript,
                    "ImageMagick identity",
                    "magick --version",
                    imageMagickVersion);
        } catch (IOException unavailable) {
            state.transcript.append("ImageMagick identity: tool unavailable.\n\n");
            return indeterminate(
                    state,
                    "The pinned ImageMagick comparator was unavailable.",
                    artifacts);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            state.transcript.append("ImageMagick identity: process interrupted.\n\n");
            return indeterminate(
                    state,
                    "The pinned ImageMagick comparator was interrupted.",
                    artifacts);
        }
        state.observedImageMagickVersion = imageMagickVersion(
                imageMagickVersion.combinedOutput());
        if (imageMagickVersion.exitCode != 0
                || !imageMagickPin.version().equals(
                        state.observedImageMagickVersion)) {
            return indeterminate(
                    state,
                    "Expected pinned ImageMagick version `"
                            + imageMagickPin.version() + "`; observed `"
                            + state.observedImageMagickVersion + "`.",
                    artifacts);
        }

        ComparisonObservation primary = compare(
                imageMagickPin.executable(),
                artifacts,
                chain.expectedRasterName(),
                chain.pdfiumRasterName(),
                chain.differenceRasterName(),
                "Expected-to-PDFium raster comparison",
                state.transcript,
                profile);
        if (!primary.usable) {
            return indeterminate(state, primary.finding, artifacts);
        }
        state.primaryMetric = metricText(primary.absoluteError);
        state.differenceHash = EvidenceFiles.sha256(difference);

        ComparisonObservation rendererAgreement = compare(
                imageMagickPin.executable(),
                artifacts,
                chain.pdfiumRasterName(),
                chain.implementationRasterName(),
                chain.rendererDifferenceRasterName(),
                chain.usesPublicRendering()
                        ? "PDFium-to-public Rendering comparison"
                        : "PDFium-to-implementation renderer comparison",
                state.transcript,
                profile);
        if (!rendererAgreement.usable) {
            return indeterminate(state, rendererAgreement.finding, artifacts);
        }
        state.rendererAgreementMetric = metricText(
                rendererAgreement.absoluteError);
        state.rendererDifferenceHash = EvidenceFiles.sha256(rendererDifference);

        if (profile.requiresExactChangedPixels()) {
            // The pinned AE measures summed magnitudes. These profiles also enforce the
            // originally declared changed-pixel bounds without weakening them.
            long primaryPixels;
            long secondaryPixels;
            try {
                primaryPixels = PngRaster.changedPixels(expected, actual, profile.rasterWidth(), profile.rasterHeight());
                secondaryPixels = PngRaster.changedPixels(actual, implementation, profile.rasterWidth(), profile.rasterHeight());
            } catch (IOException failure) {
                return indeterminate(state, "The exact changed-pixel observation was unavailable.", artifacts);
            }
            state.transcript.append("Exact changed RGB pixels, expected to PDFium: ").append(primaryPixels)
                    .append("\nExact changed RGB pixels, PDFium to secondary: ").append(secondaryPixels).append("\n\n");
            if (secondaryPixels > profile.rendererAgreementThreshold()) {
                state.reviewRequired = true;
                return indeterminate(state, "Secondary rendering exceeded the fixed changed-pixel bound.", artifacts);
            }
            if (primaryPixels > profile.comparisonThreshold()) {
                return finish(state, EvidenceResult.FAIL, "The PDFium raster exceeded the fixed changed-pixel bound.", artifacts);
            }
        }
        if (rendererAgreement.absoluteError.compareTo(BigDecimal.valueOf(
                profile.rendererAgreementThreshold())) > 0) {
            state.reviewRequired = true;
            return finish(
                    state,
                    EvidenceResult.INDETERMINATE,
                    (chain.usesPublicRendering()
                            ? "PDFium and the public Rendering output disagreed at AE `"
                            : "PDFium and the secondary implementation renderer disagreed at AE `")
                            + rendererAgreement.absoluteError
                            + "`; review is required and the visual chain cannot pass.",
                    artifacts);
        }
        if (primary.absoluteError.compareTo(BigDecimal.valueOf(
                profile.comparisonThreshold())) > 0) {
            return finish(
                    state,
                    EvidenceResult.FAIL,
                    "The PDFium raster exceeded the capability threshold with AE `"
                            + primary.absoluteError + "`; the raster difference is retained.",
                    artifacts);
        }
        return finish(
                state,
                EvidenceResult.PASS,
                "The PDFium raster matched the project-owned expectation at AE `"
                        + primary.absoluteError
                        + (chain.usesPublicRendering()
                                ? "`, and the public Rendering agreement AE was `"
                                : "`, and the secondary renderer agreement AE was `")
                        + rendererAgreement.absoluteError + "`.",
                artifacts);
    }

    private static ComparisonObservation compare(
            Path imageMagick,
            Path artifacts,
            String expected,
            String actual,
            String difference,
            String label,
            StringBuilder transcript,
            VisualProfile profile) {
        ProcessResult comparison;
        try {
            comparison = ExternalProcess.run(
                    imageMagick,
                    artifacts,
                    "compare",
                    "-metric",
                    profile.comparisonMetric(),
                    "-fuzz",
                    profile.comparisonFuzzPercent() + "%",
                    "-highlight-color",
                    "#ff0000",
                    "-lowlight-color",
                    "#ffffff",
                    "-define",
                    "png:exclude-chunk=time,date",
                    expected,
                    actual,
                    difference);
            appendInvocation(
                    transcript,
                    label,
                    "magick compare -metric " + profile.comparisonMetric()
                            + " -fuzz " + profile.comparisonFuzzPercent()
                            + "% -highlight-color #ff0000 "
                            + "-lowlight-color #ffffff -define "
                            + "png:exclude-chunk=time,date " + expected + " "
                            + actual + " " + difference,
                    comparison);
        } catch (IOException unavailable) {
            transcript.append(label).append(": tool unavailable.\n\n");
            return ComparisonObservation.unusable(
                    "The pinned ImageMagick comparator was unavailable during comparison.");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            transcript.append(label).append(": process interrupted.\n\n");
            return ComparisonObservation.unusable(
                    "The pinned ImageMagick comparator was interrupted.");
        }
        if (comparison.exitCode != 0 && comparison.exitCode != 1) {
            return ComparisonObservation.unusable(
                    "ImageMagick returned an unexpected comparison status `"
                            + comparison.exitCode + "`.");
        }
        Matcher metric = AE_METRIC.matcher(comparison.standardError.trim());
        if (!metric.matches()) {
            return ComparisonObservation.unusable(
                    "ImageMagick did not emit a usable absolute-error metric.");
        }
        BigDecimal absoluteError;
        try {
            absoluteError = new BigDecimal(metric.group(1));
        } catch (NumberFormatException invalidMetric) {
            return ComparisonObservation.unusable(
                    "ImageMagick emitted an out-of-range absolute-error metric.");
        }
        // ImageMagick 7.1.2-30 bases its status on normalized distortion at 1e-6,
        // while the printed AE is scaled by the compared raster area. Status 0
        // does not round the measured AE to zero for our capability thresholds.
        BigDecimal statusCutoff = BigDecimal.valueOf((long) profile.rasterWidth() * profile.rasterHeight()).movePointLeft(6);
        if ((comparison.exitCode == 0 && absoluteError.compareTo(statusCutoff) > 0)
                || (comparison.exitCode == 1 && absoluteError.signum() == 0)) {
            return ComparisonObservation.unusable(
                    "ImageMagick comparison status and metric disagreed.");
        }
        try {
            PngRaster.requireDifferenceRaster(
                    artifacts.resolve(difference),
                    profile.rasterWidth(),
                    profile.rasterHeight());
        } catch (IOException unusableDifference) {
            transcript.append("Difference raster validation: `indeterminate`\n\n")
                    .append(safeMessage(unusableDifference)).append("\n\n");
            return ComparisonObservation.unusable(
                    "ImageMagick did not produce a reviewable fixed-size difference raster.");
        }
        return ComparisonObservation.usable(absoluteError);
    }

    private static VisualEvidence indeterminate(
            RunState state,
            String finding,
            Path artifacts) {
        return finish(state, EvidenceResult.INDETERMINATE, finding, artifacts);
    }

    private static VisualEvidence finish(
            RunState state,
            EvidenceResult result,
            String finding,
            Path artifacts) {
        state.transcript.append("Final visual finding: ")
                .append(finding)
                .append("\n\n")
                .append("Final determination: `")
                .append(result.recordValue())
                .append("`\n");
        String record = record(state, result, finding, artifacts);
        String rawFindings = rawFindings(state);
        return new VisualEvidence(result, record, rawFindings);
    }

    private static String record(
            RunState state,
            EvidenceResult result,
            String finding,
            Path artifacts) {
        VisualProfile profile = state.profile;
        PdfiumPin pdfium = state.pdfiumPin;
        ImageMagickPin imageMagick = state.imageMagickPin;
        StringBuilder record = new StringBuilder();
        VisualEvidenceChain chain = state.chain;
        record.append("# ").append(chain.label())
                .append(" PDFium visual evidence\n\n")
                .append(EvidenceFiles.metadata("Capability", chain.capability()))
                .append(EvidenceFiles.metadata(
                        "Acceptance Profile", chain.acceptanceProfile()))
                .append(EvidenceFiles.metadata("Profile record", chain.profileRecord()))
                .append(EvidenceFiles.metadata(
                        "Release train", state.releaseTrain))
                .append(EvidenceFiles.metadata("Chain", "visual"))
                .append(EvidenceFiles.metadata("Result", result.recordValue()))
                .append(EvidenceFiles.metadata("Producer kind", "external-tool"))
                .append(EvidenceFiles.metadata("Producer", "pdfium-cli"))
                .append(EvidenceFiles.metadata(
                        "Producer version", pdfium.producerVersion()))
                .append(EvidenceFiles.metadata(
                        "PDFium CLI version", state.observedPdfiumVersion))
                .append(EvidenceFiles.metadata(
                        "PDFium engine version", pdfium.engineVersion()))
                .append(EvidenceFiles.metadata(
                        "PDFium engine distribution",
                        pdfium.engineDistributionName()))
                .append(EvidenceFiles.metadata(
                        "PDFium engine distribution SHA-256",
                        pdfium.engineArchiveSha256()))
                .append(EvidenceFiles.metadata(
                        "PDFium distribution", pdfium.distributionName()))
                .append(EvidenceFiles.metadata(
                        "PDFium distribution SHA-256", pdfium.archiveSha256()))
                .append(EvidenceFiles.metadata(
                        "PDFium executable SHA-256", pdfium.executableSha256()))
                .append(EvidenceFiles.metadata(
                        "PDFium distribution license",
                        pdfium.distributionLicense()))
                .append(EvidenceFiles.metadata(
                        "PDFium engine license", pdfium.engineLicense()))
                .append(EvidenceFiles.metadata(
                        "PDFium notice manifest", pdfium.noticeManifest()))
                .append(EvidenceFiles.metadata(
                        "ImageMagick version", state.observedImageMagickVersion))
                .append(EvidenceFiles.metadata(
                        "ImageMagick distribution", imageMagick.distributionName()))
                .append(EvidenceFiles.metadata(
                        "ImageMagick distribution SHA-256",
                        imageMagick.archiveSha256()))
                .append(EvidenceFiles.metadata(
                        "ImageMagick executable SHA-256",
                        imageMagick.executableSha256()))
                .append(EvidenceFiles.metadata(
                        "ImageMagick distribution license",
                        imageMagick.distributionLicense()))
                .append(EvidenceFiles.metadata(
                        "ImageMagick notice manifest",
                        imageMagick.noticeManifest()))
                .append(EvidenceFiles.metadata(
                        "Implementation renderer version",
                        state.implementationVersion))
                .append(EvidenceFiles.metadata(
                        "Input ID-neutral SHA-256", state.inputHash))
                .append(EvidenceFiles.metadata(
                        "Input hash policy", EvidenceFiles.inputHashPolicy()))
                .append(EvidenceFiles.metadata(
                        "Expected raster SHA-256", state.expectedHash))
                .append(EvidenceFiles.metadata(
                        "PDFium raster SHA-256", state.actualHash))
                .append(EvidenceFiles.metadata(
                        "Implementation raster SHA-256",
                        state.implementationHash))
                .append(EvidenceFiles.metadata(
                        "Expected comparison AE", state.primaryMetric))
                .append(EvidenceFiles.metadata(
                        "Renderer agreement AE", state.rendererAgreementMetric))
                .append(EvidenceFiles.metadata(
                        "Review required", Boolean.toString(state.reviewRequired)))
                .append("Final determination: `")
                .append(result.recordValue())
                .append("`\n\n")
                .append("## Visual profile\n\n")
                .append("- Page box: ").append(profile.pageBox()).append(".\n")
                .append(profile.pageCount() == 1 ? "" : "- Page selection: `" + profile.pageNumber()
                        + "` of `" + profile.pageCount() + "`.\n")
                .append("- DPI: `").append(profile.dpi()).append("`.\n")
                .append("- Color policy: ").append(profile.colorPolicy()).append(".\n")
                .append("- Font policy: ").append(profile.fontPolicy()).append(".\n")
                .append("- Antialiasing policy: ")
                .append(profile.antialiasingPolicy()).append(".\n")
                .append("- Background: ").append(profile.background()).append(".\n")
                .append("- Raster dimensions: `")
                .append(profile.rasterWidth()).append('x')
                .append(profile.rasterHeight()).append("`.\n")
                .append("- Comparison metric: ")
                .append(profile.comparisonDescription()).append(".\n")
                .append("- Capability threshold: `")
                .append(profile.comparisonThreshold()).append("` changed pixels.\n")
                .append("- Renderer-agreement threshold: `")
                .append(profile.rendererAgreementThreshold())
                .append("` changed pixels.\n\n")
                .append("## Findings and artifacts\n\n")
                .append("- Input PDF: [`artifacts/")
                .append(chain.inputArtifact()).append("`](artifacts/")
                .append(chain.inputArtifact()).append(")\n")
                .append("- Expected-raster authority: [`")
                .append(profile.expectedRasterReference()).append("`](")
                .append(profile.expectedRasterReference()).append(")\n")
                .append("- PDFium notice manifest: [`")
                .append(pdfium.noticeManifest()).append("`](../../")
                .append(pdfium.noticeManifest()).append(")\n")
                .append("- ImageMagick notice manifest: [`")
                .append(imageMagick.noticeManifest()).append("`](../../")
                .append(imageMagick.noticeManifest()).append(")\n");
        appendArtifact(record, artifacts, chain.expectedRasterName());
        appendArtifact(record, artifacts, chain.pdfiumRasterName());
        appendArtifact(record, artifacts, chain.implementationRasterName());
        appendArtifact(record, artifacts, chain.differenceRasterName());
        appendArtifact(record, artifacts, chain.rendererDifferenceRasterName());
        record.append("- Raw findings: [`artifacts/")
                .append(chain.findingsName()).append("`](artifacts/")
                .append(chain.findingsName()).append(")\n")
                .append("- ").append(finding).append("\n\n")
                .append("ImageMagick receives only validated PNG raster paths in both ")
                .append("comparison invocations; it is never given the PDF. ");
        if (chain.usesPublicRendering()) {
            record.append("The public Rendering output is the implementation under test and ")
                    .append("must satisfy the renderer-agreement ceiling; only the independent ")
                    .append("PDFium comparison against the project-owned expectation determines ")
                    .append("the primary capability threshold.\n");
        } else {
            record.append("Apache PDFBox Renderer is secondary disagreement evidence only and ")
                    .append("cannot make this chain pass.\n");
        }
        return record.toString();
    }

    private static String rawFindings(RunState state) {
        VisualProfile profile = state.profile;
        return "# " + state.chain.label() + " visual-chain raw findings\n\n"
                + EvidenceFiles.metadata(
                        "Input ID-neutral SHA-256", state.inputHash)
                + EvidenceFiles.metadata(
                        "Input hash policy", EvidenceFiles.inputHashPolicy())
                + EvidenceFiles.metadata("Expected raster SHA-256", state.expectedHash)
                + EvidenceFiles.metadata("PDFium raster SHA-256", state.actualHash)
                + EvidenceFiles.metadata(
                        "Implementation raster SHA-256", state.implementationHash)
                + EvidenceFiles.metadata("Difference raster SHA-256", state.differenceHash)
                + EvidenceFiles.metadata(
                        "Renderer difference raster SHA-256",
                        state.rendererDifferenceHash)
                + EvidenceFiles.metadata("Profile", profile.profileId())
                + EvidenceFiles.metadata("Page box", profile.pageBox())
                + EvidenceFiles.metadata("DPI", Integer.toString(profile.dpi()))
                + EvidenceFiles.metadata("Color policy", profile.colorPolicy())
                + EvidenceFiles.metadata("Font policy", profile.fontPolicy())
                + EvidenceFiles.metadata(
                        "Antialiasing policy", profile.antialiasingPolicy())
                + EvidenceFiles.metadata(
                        "Raster dimensions",
                        profile.rasterWidth() + "x" + profile.rasterHeight())
                + EvidenceFiles.metadata(
                        "Comparison metric", profile.comparisonMetric())
                + EvidenceFiles.metadata(
                        "Comparison fuzz percent",
                        Integer.toString(profile.comparisonFuzzPercent()))
                + EvidenceFiles.metadata(
                        "Comparison threshold",
                        Long.toString(profile.comparisonThreshold()))
                + EvidenceFiles.metadata(
                        "Renderer agreement threshold",
                        Long.toString(profile.rendererAgreementThreshold()))
                + EvidenceFiles.metadata("Expected comparison AE", state.primaryMetric)
                + EvidenceFiles.metadata(
                        "Renderer agreement AE", state.rendererAgreementMetric)
                + EvidenceFiles.metadata(
                        "PDFium CLI version", state.observedPdfiumVersion)
                + EvidenceFiles.metadata(
                        "PDFium engine version", state.pdfiumPin.engineVersion())
                + EvidenceFiles.metadata(
                        "ImageMagick version", state.observedImageMagickVersion)
                + EvidenceFiles.metadata(
                        "Implementation renderer version",
                        state.implementationVersion)
                + EvidenceFiles.metadata(
                        "Review required", Boolean.toString(state.reviewRequired))
                + "## Process findings\n\n"
                + state.transcript;
    }

    private static void appendArtifact(
            StringBuilder record,
            Path artifacts,
            String name) {
        if (Files.isRegularFile(artifacts.resolve(name))) {
            record.append("- Raster artifact: [`artifacts/")
                    .append(name).append("`](artifacts/")
                    .append(name).append(")\n");
        }
    }

    private static void appendInvocation(
            StringBuilder output,
            String label,
            String invocation,
            ProcessResult result) {
        String standardOutput = markdownProcessOutput(result.standardOutput);
        String standardError = markdownProcessOutput(result.standardError);
        output.append("## ").append(label).append("\n\n")
                .append("Invocation: `").append(invocation).append("`\n\n")
                .append("Exit code: `").append(result.exitCode).append("`\n\n")
                .append("### Standard output\n\n```text\n")
                .append(standardOutput)
                .append(EvidenceFiles.fencedEnding(standardOutput))
                .append("### Standard error\n\n```text\n")
                .append(standardError)
                .append(EvidenceFiles.fencedEnding(standardError));
    }

    private static String markdownProcessOutput(String value) {
        return value.replaceAll("(?m)[\\t ]+$", "");
    }

    private static String pdfiumVersion(String output) {
        String prefix = "pdfium version ";
        for (String line : output.split("\\r?\\n")) {
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length()).trim();
            }
        }
        return "unavailable";
    }

    private static String imageMagickVersion(String output) {
        String prefix = "Version: ImageMagick ";
        for (String line : output.split("\\r?\\n")) {
            if (line.startsWith(prefix)) {
                String remainder = line.substring(prefix.length()).trim();
                int separator = remainder.indexOf(' ');
                return separator < 0 ? remainder : remainder.substring(0, separator);
            }
        }
        return "unavailable";
    }

    private static void clearOutputs(Path... outputs) throws IOException {
        for (Path output : outputs) {
            Files.deleteIfExists(output);
        }
    }

    private static String safeMessage(IOException failure) {
        String message = failure.getMessage();
        return message == null || message.trim().isEmpty()
                ? "The raster operation failed."
                : message;
    }

    private static String metricText(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static final class ComparisonObservation {
        private final boolean usable;
        private final BigDecimal absoluteError;
        private final String finding;

        private ComparisonObservation(
                boolean usable,
                BigDecimal absoluteError,
                String finding) {
            this.usable = usable;
            this.absoluteError = absoluteError;
            this.finding = finding;
        }

        static ComparisonObservation usable(BigDecimal absoluteError) {
            return new ComparisonObservation(true, absoluteError, "");
        }

        static ComparisonObservation unusable(String finding) {
            return new ComparisonObservation(false, BigDecimal.valueOf(-1L), finding);
        }
    }

    private static final class RunState {
        private final VisualEvidenceChain chain;
        private final String inputHash;
        private final PdfiumPin pdfiumPin;
        private final ImageMagickPin imageMagickPin;
        private final VisualProfile profile;
        private final String releaseTrain;
        private final StringBuilder transcript = new StringBuilder();
        private String observedPdfiumVersion = "unavailable";
        private String observedImageMagickVersion = "unavailable";
        private String implementationVersion = "unavailable";
        private String expectedHash = "unavailable";
        private String actualHash = "unavailable";
        private String implementationHash = "unavailable";
        private String differenceHash = "unavailable";
        private String rendererDifferenceHash = "unavailable";
        private String primaryMetric = "unavailable";
        private String rendererAgreementMetric = "unavailable";
        private boolean reviewRequired;

        RunState(
                VisualEvidenceChain chain,
                String inputHash,
                PdfiumPin pdfiumPin,
                ImageMagickPin imageMagickPin,
                VisualProfile profile,
                String releaseTrain) {
            this.chain = chain;
            this.inputHash = inputHash;
            this.pdfiumPin = pdfiumPin;
            this.imageMagickPin = imageMagickPin;
            this.profile = profile;
            this.releaseTrain = releaseTrain;
        }
    }
}
