package net.zerocloud.pdf;

/** Central decoded-byte policy for managed annotation graph passes. */
final class PdfBoxAnnotationDecodePolicy {

    private static final long MAX_APPEARANCE_BYTES = 8L * 1024L * 1024L;
    private static final long MAX_ATTACHMENT_BYTES = 8L * 1024L * 1024L;

    private PdfBoxAnnotationDecodePolicy() {
    }

    static Budgets newManagedGraphPass() {
        return new Budgets(
                new PdfBoxAnnotationOperations.ByteBudget(
                        MAX_APPEARANCE_BYTES),
                new PdfBoxAnnotationOperations.ByteBudget(
                        MAX_ATTACHMENT_BYTES));
    }

    static final class Budgets {

        private final PdfBoxAnnotationOperations.ByteBudget appearances;
        private final PdfBoxAnnotationOperations.ByteBudget attachments;

        private Budgets(
                PdfBoxAnnotationOperations.ByteBudget appearances,
                PdfBoxAnnotationOperations.ByteBudget attachments) {
            this.appearances = appearances;
            this.attachments = attachments;
        }

        PdfBoxAnnotationOperations.ByteBudget appearances() {
            return appearances;
        }

        PdfBoxAnnotationOperations.ByteBudget attachments() {
            return attachments;
        }
    }
}
