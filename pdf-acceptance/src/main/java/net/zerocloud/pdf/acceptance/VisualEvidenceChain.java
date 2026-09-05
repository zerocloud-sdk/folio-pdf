package net.zerocloud.pdf.acceptance;

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

    private VisualEvidenceChain(
            String label,
            String capability,
            String acceptanceProfile,
            String inputArtifact,
            String visualArtifactStem) {
        this.label = label;
        this.capability = capability;
        this.acceptanceProfile = acceptanceProfile;
        this.profileRecord = "capabilities/evidence/"
                + ("T23".equals(label) ? "T23-page-rendering" : acceptanceProfile) + ".md";
        this.inputArtifact = inputArtifact;
        this.recordName = visualArtifactStem + "-visual.md";
        this.findingsName = visualArtifactStem + "-visual.txt";
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
