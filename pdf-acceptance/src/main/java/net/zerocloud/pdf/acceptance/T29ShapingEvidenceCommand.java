package net.zerocloud.pdf.acceptance;

import static net.zerocloud.pdf.acceptance.EvidenceFiles.metadata;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.provider.ShapingRequest;

/** Repository-only native, reopened, subset, syntax and raster observations for T29. */
public final class T29ShapingEvidenceCommand {
    private static final String PROFILE = "T29-shaping";
    private static final String ROOT = "/net/zerocloud/pdf/acceptance/";

    /** Receives output, three tool pins, profiles directory, release, native helper and Python executable. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 8) { throw new IllegalArgumentException("Expected eight T29 evidence arguments"); }
        Path output = Paths.get(arguments[0]).toAbsolutePath();
        Path artifacts = Files.createDirectories(output.resolve("artifacts"));
        QpdfPin qpdf = QpdfPin.load(Paths.get(arguments[1]));
        PdfiumPin pdfium = PdfiumPin.load(Paths.get(arguments[2]));
        ImageMagickPin comparator = ImageMagickPin.load(Paths.get(arguments[3]));
        Path profiles = Paths.get(arguments[4]).toAbsolutePath();
        String release = arguments[5];
        Path helper = Paths.get(arguments[6]).toAbsolutePath();
        Path python = Paths.get(arguments[7]).toAbsolutePath();
        Path subsetScript = profiles.getParent().getParent().resolve("scripts/t29-verify-subsets.py");
        boolean workerApplicable = "Linux".equals(System.getProperty("os.name"));
        StringBuilder provenance = new StringBuilder(metadata("OS name", System.getProperty("os.name")))
                .append(metadata("OS version", System.getProperty("os.version")))
                .append(metadata("Architecture", System.getProperty("os.arch")))
                .append(metadata("Java runtime version", System.getProperty("java.runtime.version")))
                .append(metadata("Worker support envelope", "Linux; executable /usr/bin/prlimit; JDK 8,11,17,21 with legacy Security Manager"))
                .append(metadata("Worker applicability", workerApplicable ? "required on this Linux host"
                        : "not applicable; this OS is outside the existing Worker envelope"))
                .append(metadata("Expected HarfBuzz version", "10.2.0"))
                .append(metadata("Native helper executable", helper.toString()))
                .append(metadata("Native helper executable SHA-256", Files.isRegularFile(helper) ? EvidenceFiles.sha256(helper) : "unavailable"))
                .append(metadata("Native numeric tolerance in font units", "0"))
                .append(metadata("Reopened geometry tolerance in points", "0.0001"));
        for (String name : new String[] {"T29-corpus.json", "T29-oracle.json", "T29-corpus.properties", "T29-glyphs.tsv",
                "T29-reference-receipt.json", "T29-shaping-reference.pdf"}) {
            copy("shaping/" + name, artifacts.resolve(name));
            provenance.append(metadata(name + " SHA-256", EvidenceFiles.sha256(artifacts.resolve(name))));
        }
        copy("fonts/noto/fonts.properties", artifacts.resolve("T29-fonts.properties"));
        provenance.append(metadata("Font manifest SHA-256", EvidenceFiles.sha256(artifacts.resolve("T29-fonts.properties"))));
        installation(output, artifacts, python, subsetScript.resolveSibling("t29-native-observation.py"),
                helper, release, provenance.toString());
        Path staging = Files.createTempDirectory(output, "T29-native-staging-");
        try {
            T29NativeShapingAssertions.Observation nativeResult = T29NativeShapingAssertions.inspect(helper, staging);
            EvidenceFiles.write(artifacts.resolve(PROFILE + "-native.txt"), nativeResult.findings);
            record(output, "native", nativeResult.result, "project-test", "folio-pdf-t29-native-assertions", release, release,
                    provenance + "\nEvery run compares source GIDs, UTF-16 clusters, advances, offsets, direction and engine version "
                    + "with the official hb-shape oracle.\n\n[Native observations](artifacts/T29-shaping-native.txt)\n");
            if (nativeResult.result != EvidenceResult.PASS) {
                for (String chain : new String[] {"semantic", "syntax", "subsets", "visual"}) {
                    record(output, chain, EvidenceResult.INDETERMINATE, "project-test", "folio-pdf-t29-evidence", release, release,
                            provenance + "\nThe native prerequisite did not pass. No product PDF was generated for this chain.\n");
                }
                return;
            }
            EvidenceResult semantic = EvidenceResult.PASS;
            EvidenceResult syntax = EvidenceResult.PASS;
            EvidenceResult subsets = EvidenceResult.PASS;
            EvidenceResult visual = EvidenceResult.PASS;
            StringBuilder observations = new StringBuilder();
            StringBuilder syntaxLinks = new StringBuilder();
            StringBuilder subsetLinks = new StringBuilder();
            StringBuilder visualLinks = new StringBuilder();
            StringBuilder coveredModes = new StringBuilder();
            for (WorkflowExecutionProfile mode : WorkflowExecutionProfile.values()) {
                boolean worker = mode == WorkflowExecutionProfile.HARDENED_WORKER;
                if (worker && !workerApplicable) { continue; }
                String label = worker ? "worker" : "in-process";
                String stem = PROFILE + (worker ? "-worker" : "");
                Path artifact = artifacts.resolve(stem + ".pdf");
                WorkflowOutcome<Void> creation;
                try { creation = T29ShapingProducts.create(artifact, mode, helper, staging); }
                catch (DocumentFailure failure) {
                    if (!worker || failure.getCode() != DocumentFailureCode.WORKER_UNAVAILABLE) { throw failure; }
                    provenance.append(metadata(mode + " outcome", "indeterminate: WORKER_UNAVAILABLE"));
                    observations.append(mode).append(": WORKER_UNAVAILABLE; no product was published.\n");
                    semantic = combine(semantic, EvidenceResult.INDETERMINATE);
                    syntax = combine(syntax, EvidenceResult.INDETERMINATE);
                    subsets = combine(subsets, EvidenceResult.INDETERMINATE);
                    visual = combine(visual, EvidenceResult.INDETERMINATE);
                    continue;
                }
                if (coveredModes.length() > 0) { coveredModes.append(','); }
                coveredModes.append(mode);
                String hash = EvidenceFiles.idNeutralPdfSha256(artifact);
                provenance.append(metadata(mode + " input ID-neutral SHA-256", hash))
                        .append(metadata(mode + " peak owned memory in bytes", Long.toString(creation.getResourceUsage().getPeakOwnedMemoryBytes())));
                T29ShapingSemanticAssertions.Observation observed = T29ShapingSemanticAssertions.inspect(creation, artifact, mode);
                observations.append(mode).append('\n').append(observed.findings).append('\n');
                semantic = combine(semantic, observed.result());
                provenance.append(metadata(mode + " outcome", observed.result().recordValue()));
                String syntaxName = PROFILE + "-" + label + "-syntax.md";
                EvidenceResult modeSyntax = QpdfSyntaxRecorder.record(output, artifacts, qpdf, hash, release,
                        new QpdfSyntaxRecorder.Profile("T29", ShapingRequest.CAPABILITY_ID, PROFILE,
                                "capabilities/evidence/T29-shaping.md", stem + ".pdf", syntaxName, stem + "-qpdf.txt"));
                syntax = combine(syntax, modeSyntax);
                syntaxLinks.append("- [").append(mode).append("](").append(syntaxName).append(")\n");
                subsets = combine(subsets, subsets(python, subsetScript, qpdf, artifact, artifacts, modeSyntax));
                subsetLinks.append("- [").append(mode).append("](artifacts/").append(stem).append("-subsets.txt)\n");
                int page = 0;
                for (String script : new String[] {"arabic", "hebrew", "devanagari", "thai"}) {
                    for (int local = 1; local <= 2; local++) {
                        page++;
                        String id = PROFILE + "-" + script + "-page-" + local;
                        VisualProfile profile = VisualProfile.load(profiles.resolve(id + "-visual.properties"));
                        if (!profile.profileId().equals(id) || profile.pageNumber() != page || profile.pageCount() != 8) {
                            throw new IllegalArgumentException("T29 visual selection does not match the frozen page");
                        }
                        VisualEvidenceChain chain = VisualEvidenceChain.t29(id, worker);
                        VisualEvidence evidence = VisualEvidenceRecorder.record(chain, artifact, hash, artifacts, pdfium, comparator, profile, release);
                        EvidenceFiles.write(artifacts.resolve(chain.findingsName()), evidence.rawFindings());
                        EvidenceFiles.write(output.resolve(chain.recordName()), evidence.record());
                        visual = combine(visual, evidence.result());
                        visualLinks.append("- [").append(mode).append(' ').append(script).append(' ').append(local)
                                .append("](").append(chain.recordName()).append(")\n");
                    }
                }
            }
            provenance.append(metadata("Input hash policy", EvidenceFiles.inputHashPolicy()))
                    .append(metadata("Mode coverage", coveredModes.toString()));
            EvidenceFiles.write(artifacts.resolve(PROFILE + "-semantic.txt"), observations.toString());
            record(output, "semantic", semantic, "project-test", "folio-pdf-t29-semantic-assertions", release, release,
                    provenance + "\nEvery painted item is compared after public Workflow reopening: logical cluster mapping, "
                    + "matrix, baseline, advance, page box, exact item count and explicit embedded subset names. "
                    + "Aggregate ActualText is compared with the fixed logical runs.\n\n[Applicable mode observations](artifacts/T29-shaping-semantic.txt)\n");
            record(output, "syntax", syntax, "external-tool", "qpdf", qpdf.version(), release, provenance + "\n" + syntaxLinks);
            record(output, "subsets", subsets, "external-tool", "qpdf-and-fontTools", "12.4.0/4.59.2", release,
                    provenance + metadata("Subset verifier source SHA-256", EvidenceFiles.sha256(subsetScript))
                    + "\nThe independent verifier follows the painted CIDToGID mapping, compares outlines and transitive composite "
                    + "dependencies with pinned source fonts, and checks the ToUnicode logical input clusters.\n\n" + subsetLinks);
            record(output, "visual", visual, "external-tool", "pdfium-cli", pdfium.producerVersion(), release,
                    provenance + "\nAll eight pages in each mode require zero-fuzz AE 0 against the independent reference. "
                    + "The fixed secondary-renderer disagreement limit is 3000 changed pixels.\n\n" + visualLinks);
            System.out.println("T29 local chains: native=" + nativeResult.result + ", semantic=" + semantic
                    + ", syntax=" + syntax + ", subsets=" + subsets + ", visual=" + visual);
        } finally { Files.delete(staging); }
    }

    private static void installation(Path output, Path artifacts, Path python, Path script,
            Path helper, String release, String provenance) throws IOException {
        EvidenceResult result = EvidenceResult.INDETERMINATE;
        String findings = "The native installation observer or its Python executable is unavailable.\n";
        if (Files.isRegularFile(script)) {
            try {
                ProcessResult check = ExternalProcess.run(python, artifacts, script.toString(), helper.toString());
                findings = check.combinedOutput();
                if (check.exitCode == 0 && check.standardOutput.contains("\"result\": \"pass\"")) {
                    result = EvidenceResult.PASS;
                } else if (check.exitCode == 1 && check.standardOutput.contains("\"result\": \"fail\"")) {
                    result = EvidenceResult.FAIL;
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt(); findings = "The native installation observer was interrupted.\n";
            } catch (IOException unavailable) { findings = "The native installation observer was unavailable.\n"; }
        }
        EvidenceFiles.write(artifacts.resolve(PROFILE + "-installation.txt"), findings);
        record(output, "installation", result, "project-test", "folio-pdf-t29-native-installation-observer", release, release,
                provenance + metadata("Native observation source SHA-256", Files.isRegularFile(script) ? EvidenceFiles.sha256(script) : "unavailable")
                + "\nThis separate invocation checks installed artifacts and observes the helper's live startup mappings "
                + "in the inherited loader environment. It does not inspect every Workflow subprocess. "
                + "Unimplemented platform observation remains INDETERMINATE.\n\n"
                + "[Installation and live engine observations](artifacts/T29-shaping-installation.txt)\n");
    }

    private static EvidenceResult subsets(Path python, Path script, QpdfPin qpdf, Path artifact,
            Path artifacts, EvidenceResult syntax) throws IOException {
        EvidenceResult result = EvidenceResult.INDETERMINATE;
        String findings = "Pinned qpdf syntax did not pass; the embedded program could not be independently checked.\n";
        if (syntax == EvidenceResult.PASS) {
            try {
                ProcessResult version = ExternalProcess.run(python, artifacts, "-c", "import fontTools; print(fontTools.__version__)");
                if (version.exitCode == 0 && "4.59.2".equals(version.standardOutput.trim()) && Files.isRegularFile(script)) {
                    ProcessResult check = ExternalProcess.run(python, artifacts, script.toString(), qpdf.executable().toString(),
                            artifact.toString(), artifacts.resolve("T29-oracle.json").toString());
                    findings = check.combinedOutput();
                    result = check.exitCode == 0 ? EvidenceResult.PASS : EvidenceResult.FAIL;
                } else { findings = "The independent verifier requires fontTools 4.59.2 and its recorded script.\n"; }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt(); findings = "The independent subset verifier was interrupted.\n";
            } catch (IOException unavailable) { findings = "The independent subset verifier was unavailable.\n"; }
        }
        String stem = artifact.getFileName().toString().replace(".pdf", "");
        EvidenceFiles.write(artifacts.resolve(stem + "-subsets.txt"), "Result: " + result.recordValue() + "\n" + findings);
        return result;
    }

    private static EvidenceResult combine(EvidenceResult first, EvidenceResult second) {
        if (first == EvidenceResult.FAIL || second == EvidenceResult.FAIL) { return EvidenceResult.FAIL; }
        return first == EvidenceResult.INDETERMINATE || second == EvidenceResult.INDETERMINATE
                ? EvidenceResult.INDETERMINATE : EvidenceResult.PASS;
    }

    private static void copy(String resource, Path target) throws IOException {
        try (InputStream input = T29ShapingEvidenceCommand.class.getResourceAsStream(ROOT + resource)) { Files.copy(input, target); }
    }

    private static void record(Path output, String chain, EvidenceResult result, String kind, String producer,
            String version, String release, String content) throws IOException {
        EvidenceFiles.write(output.resolve(PROFILE + "-" + chain + ".md"), "# T29 " + chain + " evidence\n\n"
                + metadata("Capability", ShapingRequest.CAPABILITY_ID) + metadata("Acceptance Profile", PROFILE)
                + metadata("Profile record", "capabilities/evidence/T29-shaping.md") + metadata("Release train", release)
                + metadata("Chain", chain) + metadata("Result", result.recordValue()) + metadata("Producer kind", kind)
                + metadata("Producer", producer) + metadata("Producer version", version) + content
                + "\nFinal determination: `" + result.recordValue() + "`\n");
    }

    private T29ShapingEvidenceCommand() { }
}
