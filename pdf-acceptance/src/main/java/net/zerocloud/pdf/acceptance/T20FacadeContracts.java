package net.zerocloud.pdf.acceptance;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.PublicationReceipt;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.itext7.kernel.exceptions.PdfException;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfDocument;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfReader;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfWriter;

/** Existing Facade operations; no resource-policy control is added to that surface. */
final class T20FacadeContracts {
    private T20FacadeContracts() { }

    static RetainedEvidence record(Path output) throws Exception {
        RetainedEvidence retained = new RetainedEvidence(output);
        Properties observed = new Properties();
        BorrowedOutput stream = new BorrowedOutput();
        PdfDocument created = new PdfDocument(new PdfWriter(stream));
        created.addNewPage(); created.close(); created.close();
        check(!stream.closed, "caller output closed");
        observed.setProperty("stream-ownership.receipts", receipts(created.getPublicationReceipts(), PublicationStatus.COMMITTED));
        closed(created);
        Path success = Files.createDirectory(output.resolve("facade-stream")).resolve("blank.pdf");
        retained.write(success, stream.toByteArray());
        check(T03BlankSemantics.inspect(success, WorkflowExecutionProfile.IN_PROCESS) == EvidenceResult.PASS, "Native reopened Facade result");
        BorrowedInput input = new BorrowedInput(stream.toByteArray());
        try (PdfReader reader = new PdfReader(input); PdfDocument reopened = new PdfDocument(reader)) {
            check(reopened.getNumberOfPages() == 1, "Facade reopened outcome");
        }
        check(!input.closed, "caller input closed");
        observed.setProperty("stream-ownership.result", "pass");
        observed.setProperty("reopened.result", "pass");
        observed.setProperty("closed-views.result", "pass");

        Path first = Files.createDirectory(output.resolve("facade-before-failure")).resolve("blank.pdf");
        Path later = output.resolve("facade-later.sentinel");
        retained.write(later, new byte[] {1, 2, 3});
        BrokenOutput broken = new BrokenOutput();
        Map<String, PdfWriter> targets = new LinkedHashMap<String, PdfWriter>();
        targets.put("first", new PdfWriter(first.toString()));
        targets.put("current", new PdfWriter(broken));
        targets.put("later", new PdfWriter(later.toString()));
        PdfDocument multiple = new PdfDocument(Collections.<String, PdfReader>emptyMap(), null, targets);
        multiple.addNewPage();
        try { multiple.close(); throw new AssertionError("Failed Facade output passed"); }
        catch (PdfException wrapped) {
            check(wrapped.getCause() instanceof DocumentFailure, "Facade dropped the public Native failure");
            DocumentFailure failure = (DocumentFailure) wrapped.getCause();
            check(failure.getCode() == DocumentFailureCode.PUBLICATION_FAILED && failure.getCause() == null
                    && "The validated document could not be written to its stream target.".equals(failure.getDiagnostic()), "Facade unsafe publication failure");
            observed.setProperty("partial-publication.receipts", receipts(failure.getPublicationReceipts(),
                    PublicationStatus.COMMITTED, PublicationStatus.FAILED, PublicationStatus.NOT_ATTEMPTED));
            check(failure.getPublicationReceipts().get(1).isPartialOutputPossible(), "Facade partial receipt");
            observed.setProperty("partial-publication.code", failure.getCode().name());
        }
        check(broken.count == 1 && !broken.closed && Arrays.equals(Files.readAllBytes(later), new byte[] {1, 2, 3}), "Facade ownership or later Target");
        closed(multiple);
        check(T03BlankSemantics.inspect(first, WorkflowExecutionProfile.IN_PROCESS) == EvidenceResult.PASS, "Facade earlier commit lost");
        retained.retain(first, EvidenceFiles.sha256(first));
        observed.setProperty("partial-publication.result", "pass");
        observed.setProperty("execution-profile", "IN_PROCESS");
        retained.write(output.resolve("facade-observations.properties"), observed);
        return retained;
    }

    private static String receipts(List<PublicationReceipt> actual, PublicationStatus... expected) {
        check(actual.size() == expected.length, "Facade receipt cardinality");
        String[] names = expected.length == 1 ? new String[] {"target"} : new String[] {"first", "current", "later"};
        StringBuilder observed = new StringBuilder();
        for (int index = 0; index < expected.length; index++) {
            PublicationReceipt receipt = actual.get(index);
            check(names[index].equals(receipt.getTargetName()) && receipt.getStatus() == expected[index], "Facade receipt target/order");
            check(receipt.isPartialOutputPossible() == (expected[index] == PublicationStatus.FAILED), "Facade receipt partial-output flag");
            if (index > 0) { observed.append(','); }
            observed.append(receipt.getTargetName()).append(':').append(receipt.getStatus().name())
                    .append(':').append(receipt.isPartialOutputPossible());
        }
        return observed.toString();
    }
    private static void closed(PdfDocument document) {
        try { document.getNumberOfPages(); throw new AssertionError("Facade view remained open"); }
        catch (IllegalStateException expected) { check("The facade document is closed.".equals(expected.getMessage()), "Facade lifecycle diagnostic"); }
    }
    private static void check(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
    private static final class BorrowedInput extends ByteArrayInputStream {
        private boolean closed;
        BorrowedInput(byte[] bytes) { super(bytes); }
        @Override public void close() { closed = true; }
    }
    private static final class BorrowedOutput extends ByteArrayOutputStream {
        private boolean closed;
        @Override public void close() { closed = true; }
    }
    private static final class BrokenOutput extends java.io.OutputStream {
        private int count;
        private boolean closed;
        @Override public void write(int value) throws IOException { if (count == 1) { throw new IOException("private-output-detail"); } count++; }
        @Override public void close() { closed = true; }
    }
}
