package net.zerocloud.pdf.acceptance;

import java.nio.file.Path;

/** Names and capability metadata for one independent visual evidence chain. */
final class VisualEvidenceChain {

    private final String label;
    private final String capability;
    private final String acceptanceProfile;
    private final String profileRecord;
    private final String inputArtifact;
    private final String recordName;
    private final String findingsName;
    private final String expectedRasterName;
    private final String pdfiumRasterName;
    private final String implementationRasterName;
    private final String differenceRasterName;
    private final String rendererDifferenceRasterName;
    private final Path repositoryRoot;

    private VisualEvidenceChain(
            String label,
            String capability,
            String acceptanceProfile,
            String inputArtifact,
            String visualArtifactStem) {
        this(label, capability, acceptanceProfile, inputArtifact, visualArtifactStem, null);
    }

    private VisualEvidenceChain(String label, String capability, String acceptanceProfile,
            String inputArtifact, String visualArtifactStem, Path repositoryRoot) {
        this.repositoryRoot = repositoryRoot;
        this.label = label;
        this.capability = capability;
        this.acceptanceProfile = acceptanceProfile;
        this.profileRecord = "capabilities/evidence/"
                + ("T23".equals(label) ? "T23-page-rendering"
                    : "T29".equals(label) ? "T29-shaping" : acceptanceProfile) + ".md";
        this.inputArtifact = inputArtifact;
        this.recordName = "T09".equals(label) ? "visual.md" : visualArtifactStem + "-visual.md";
        this.findingsName = "T09".equals(label) ? "visual.txt" : visualArtifactStem + "-visual.txt";
        this.expectedRasterName = visualArtifactStem + "-expected.png";
        this.pdfiumRasterName = visualArtifactStem + "-pdfium.png";
        this.implementationRasterName = visualArtifactStem
                + "-implementation.png";
        this.differenceRasterName = visualArtifactStem + "-difference.png";
        this.rendererDifferenceRasterName = visualArtifactStem
                + "-renderer-difference.png";
    }

    static VisualEvidenceChain t03() {
        return new VisualEvidenceChain(
                "T07",
                "document.blank.create-publish-reopen",
                "T03-document-workflow-transaction",
                "T06-document-blank-output.pdf",
                "T07-document-blank");
    }

    static VisualEvidenceChain t09(Path repositoryRoot) {
        return new VisualEvidenceChain("T09", "document.value.inspect-patch",
                "T09-document-value-inspection-patch", "values.pdf", "T09-values", repositoryRoot);
    }

    String artifactPrefix() {
        return repositoryRoot == null ? "artifacts/" : "";
    }

    String repositoryLink(Path artifacts, String path) {
        return repositoryRoot == null ? "../../" + path : artifacts.relativize(repositoryRoot.resolve(path)).toString();
    }

    String expectedRasterLink(Path artifacts, VisualProfile profile) {
        return repositoryRoot == null ? profile.expectedRasterReference() : artifacts.relativize(profile.expectedRaster()).toString();
    }

    String inputHashLabel() {
        return "T09".equals(label) ? "Input exact SHA-256" : "Input ID-neutral SHA-256";
    }

    String inputHashPolicy() {
        return "T09".equals(label) ? "SHA-256 of the exact unmodified PDF bytes" : EvidenceFiles.inputHashPolicy();
    }

    static VisualEvidenceChain t18() {
        return conventional(
                "T18",
                "composition.canvas.images-colors-transparency",
                "T18-canvas-images-colors-transparency");
    }

    static VisualEvidenceChain t19() {
        return conventional(
                "T19",
                "composition.fonts.load-embed-subset-fallback",
                "T19-font-loading-embedding-subsetting");
    }

    static VisualEvidenceChain t23(String profile) {
        return conventional("T23", "conversion.rendering", profile);
    }

    static VisualEvidenceChain t24(int page) {
        return new VisualEvidenceChain("T24", "composition.layout.paragraph-areas",
                "T24-paragraph-composition", "T24-paragraph-composition.pdf",
                "T24-paragraph-composition-page-" + page);
    }

    static VisualEvidenceChain t26(int page) {
        return new VisualEvidenceChain("T26", "composition.layout.tables", "T26-table-composition",
                "T26-table-composition.pdf", "T26-table-composition-page-" + page);
    }

    static VisualEvidenceChain t27(int page) {
        return new VisualEvidenceChain("T27", "composition.layout.tables", "T27-table-pagination",
                "T27-table-pagination.pdf", "T27-table-pagination-page-" + page);
    }

    static VisualEvidenceChain t28(String profile) {
        return new VisualEvidenceChain("T28", "composition.layout.paragraph-areas", profile, "T28-unicode.pdf", profile);
    }

    static VisualEvidenceChain t29(String profile, boolean worker) {
        String suffix = worker ? "-worker" : "";
        return new VisualEvidenceChain("T29", "composition.shaping.harf-buzz", profile,
                "T29-shaping" + suffix + ".pdf", profile + suffix);
    }

    static VisualEvidenceChain t25(String profile, int page) {
        return new VisualEvidenceChain("T25", "composition.layout.paragraph-pagination", profile, profile + ".pdf",
                profile + "-page-" + page);
    }

    boolean usesPublicRendering() { return "T23".equals(label); }

    private static VisualEvidenceChain conventional(
            String label,
            String capability,
            String artifactStem) {
        return new VisualEvidenceChain(
                label,
                capability,
                artifactStem,
                artifactStem + ".pdf",
                artifactStem);
    }

    String label() {
        return label;
    }

    String capability() {
        return capability;
    }

    String acceptanceProfile() {
        return acceptanceProfile;
    }

    String profileRecord() {
        return profileRecord;
    }

    String inputArtifact() {
        return inputArtifact;
    }

    String recordName() {
        return recordName;
    }

    String findingsName() {
        return findingsName;
    }

    String expectedRasterName() {
        return expectedRasterName;
    }

    String pdfiumRasterName() {
        return pdfiumRasterName;
    }

    String implementationRasterName() {
        return implementationRasterName;
    }

    String differenceRasterName() {
        return differenceRasterName;
    }

    String rendererDifferenceRasterName() {
        return rendererDifferenceRasterName;
    }
}
