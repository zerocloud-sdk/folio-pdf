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
            String profileRecord,
            String inputArtifact,
            String recordName,
            String findingsName,
            String expectedRasterName,
            String pdfiumRasterName,
            String implementationRasterName,
            String differenceRasterName,
            String rendererDifferenceRasterName) {
        this.label = label;
        this.capability = capability;
        this.acceptanceProfile = acceptanceProfile;
        this.profileRecord = profileRecord;
        this.inputArtifact = inputArtifact;
        this.recordName = recordName;
        this.findingsName = findingsName;
        this.expectedRasterName = expectedRasterName;
        this.pdfiumRasterName = pdfiumRasterName;
        this.implementationRasterName = implementationRasterName;
        this.differenceRasterName = differenceRasterName;
        this.rendererDifferenceRasterName = rendererDifferenceRasterName;
    }

    static VisualEvidenceChain t03() {
        return new VisualEvidenceChain(
                "T07",
                "document.blank.create-publish-reopen",
                "T03-document-workflow-transaction",
                "capabilities/evidence/T03-document-workflow-transaction.md",
                "T06-document-blank-output.pdf",
                "T07-document-blank-visual.md",
                "T07-document-blank-visual.txt",
                "T07-document-blank-expected.png",
                "T07-document-blank-pdfium.png",
                "T07-document-blank-implementation.png",
                "T07-document-blank-difference.png",
                "T07-document-blank-renderer-difference.png");
    }

    static VisualEvidenceChain t18() {
        return new VisualEvidenceChain(
                "T18",
                "composition.canvas.images-colors-transparency",
                "T18-canvas-images-colors-transparency",
                "capabilities/evidence/T18-canvas-images-colors-transparency.md",
                "T18-canvas-images-colors-transparency.pdf",
                "T18-canvas-images-colors-transparency-visual.md",
                "T18-canvas-images-colors-transparency-visual.txt",
                "T18-canvas-images-colors-transparency-expected.png",
                "T18-canvas-images-colors-transparency-pdfium.png",
                "T18-canvas-images-colors-transparency-implementation.png",
                "T18-canvas-images-colors-transparency-difference.png",
                "T18-canvas-images-colors-transparency-renderer-difference.png");
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
