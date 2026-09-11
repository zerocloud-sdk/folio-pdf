package net.zerocloud.pdf.acceptance;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.SetXmpMetadata;
import net.zerocloud.pdf.itext7.kernel.exceptions.PdfException;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfDocument;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfReader;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfWriter;

/** Public Native and Facade evidence for metadata protection on signed Sources. */
final class T11SignedProtectionEvidence {
    private static final byte[] SENTINEL = new byte[] {70, 79, 76, 73, 79};
    private static final String OFFSET_1 = "AAAAAAAAAA";
    private static final String LENGTH_1 = "BBBBBBBBBB";
    private static final String OFFSET_2 = "CCCCCCCCCC";
    private static final String LENGTH_2 = "DDDDDDDDDD";

    private T11SignedProtectionEvidence() {
    }

    static Properties record(
            Path output,
            WorkflowExecutionProfile nativeExecution) throws Exception {
        Path source = output.resolve("signed-docmdp-p3.pdf");
        Files.write(source, signedFixture());
        String sourceHash = EvidenceFiles.sha256(source);
        Properties result = new Properties();
        result.setProperty("profile", T11Corpus.PROFILE);
        result.setProperty("source-sha256", sourceHash);
        result.setProperty("facade.execution-profile",
                WorkflowExecutionProfile.IN_PROCESS.name());

        Path nativeTarget = output.resolve("native-target.pdf");
        Files.write(nativeTarget, SENTINEL);
        DocumentFailure nativeFailure;
        WorkflowRequest nativeRequest = WorkflowRequest.builder()
                .source("input", DocumentSource.path(source))
                .primarySource("input")
                .target("result", PublicationTarget.path(nativeTarget))
                .executionProfile(nativeExecution)
                .saveMode(SaveMode.REWRITE)
                .build();
        result.setProperty("native.execution-profile",
                nativeRequest.getExecutionProfile().name());
        try {
            new DocumentWorkflow().execute(
                    nativeRequest,
                    session -> {
                        session.execute(SetXmpMetadata.version1(
                                T11MetadataProducts.editedXmp()));
                        return null;
                    });
            throw new IOException("T11 Native rewrote a signed Source");
        } catch (DocumentFailure failure) {
            nativeFailure = failure;
        }
        result.setProperty("native.code", nativeFailure.getCode().name());
        result.setProperty("native.diagnostic", nativeFailure.getDiagnostic());
        result.setProperty("native.target-unchanged",
                Arrays.equals(SENTINEL, Files.readAllBytes(nativeTarget))
                        ? "pass" : "fail");

        Path facadeTarget = output.resolve("facade-target.pdf");
        Files.write(facadeTarget, SENTINEL);
        DocumentFailure facadeFailure = null;
        PdfDocument document;
        try (PdfReader reader = new PdfReader(source.toString())) {
            document = new PdfDocument(
                    reader, new PdfWriter(facadeTarget.toString()));
            try {
                document.setXmpMetadata(T11MetadataProducts.editedXmp());
                throw new IOException("T11 Facade rewrote a signed Source");
            } catch (PdfException failure) {
                facadeFailure = T11FacadeProducts.nativeFailure(failure);
            } finally {
                try {
                    document.close();
                } catch (PdfException closeFailure) {
                    DocumentFailure closeNative = T11FacadeProducts.nativeFailure(closeFailure);
                    if (facadeFailure == null) {
                        facadeFailure = closeNative;
                    } else if (facadeFailure.getCode() != closeNative.getCode()) {
                        throw closeFailure;
                    }
                }
            }
        }
        if (facadeFailure == null) {
            throw new IOException("T11 Facade produced no signed-Source rejection");
        }
        result.setProperty("facade.code", facadeFailure.getCode().name());
        result.setProperty("facade.diagnostic", facadeFailure.getDiagnostic());
        result.setProperty("facade.target-unchanged",
                Arrays.equals(SENTINEL, Files.readAllBytes(facadeTarget))
                        ? "pass" : "fail");
        result.setProperty("source-unchanged",
                sourceHash.equals(EvidenceFiles.sha256(source))
                        ? "pass" : "fail");
        boolean pass = nativeFailure.getCode()
                        == DocumentFailureCode.SIGNED_REWRITE_REJECTED
                && facadeFailure.getCode()
                        == DocumentFailureCode.SIGNED_REWRITE_REJECTED
                && "pass".equals(result.getProperty("source-unchanged"))
                && "pass".equals(result.getProperty("native.target-unchanged"))
                && "pass".equals(result.getProperty("facade.target-unchanged"));
        result.setProperty("result", pass ? "pass" : "fail");
        T11MetadataProducts.save(output.resolve("signed.properties"), result);
        if (!pass) {
            throw new IOException("T11 signed metadata protection did not pass");
        }
        return result;
    }

    private static byte[] signedFixture() throws IOException {
        List<String> objects = new ArrayList<String>();
        objects.add("<< /Type /Catalog /Pages 2 0 R /AcroForm 4 0 R "
                + "/Perms << /DocMDP 6 0 R >> >>");
        objects.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                + "/Resources << >> >>");
        objects.add("<< /Fields [5 0 R] /SigFlags 3 >>");
        objects.add("<< /FT /Sig /T (FolioProjectOwnedCertification) /V 6 0 R >>");
        objects.add("<< /Filter /Adobe.PPKLite /SubFilter /adbe.pkcs7.detached "
                + "/ByteRange [" + OFFSET_1 + " " + LENGTH_1 + " "
                + OFFSET_2 + " " + LENGTH_2 + "] /Contents <"
                + repeated("00", 128) + "> /Reference [<< /Type /SigRef "
                + "/TransformMethod /DocMDP /TransformParams "
                + "<< /Type /TransformParams /P 3 /V /1.2 >> >>] >>");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        write(output, "%PDF-1.7\n%FolioProjectOwnedT11Signature\n");
        List<Integer> offsets = new ArrayList<Integer>();
        offsets.add(Integer.valueOf(0));
        for (int index = 0; index < objects.size(); index++) {
            offsets.add(Integer.valueOf(output.size()));
            write(output, Integer.toString(index + 1) + " 0 obj\n"
                    + objects.get(index) + "\nendobj\n");
        }
        int xref = output.size();
        write(output, "xref\n0 " + (objects.size() + 1)
                + "\n0000000000 65535 f \n");
        for (int index = 1; index < offsets.size(); index++) {
            write(output, fixedWidth(offsets.get(index).intValue())
                    + " 00000 n \n");
        }
        write(output, "trailer\n<< /Size " + (objects.size() + 1)
                + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
        String pdf = new String(output.toByteArray(), StandardCharsets.US_ASCII);
        int range = pdf.indexOf(OFFSET_1);
        int contentsOpen = pdf.indexOf("/Contents <", range)
                + "/Contents ".length();
        int contentsClose = pdf.indexOf('>', contentsOpen);
        int secondOffset = contentsClose + 1;
        pdf = replace(pdf, OFFSET_1, 0);
        pdf = replace(pdf, LENGTH_1, contentsOpen);
        pdf = replace(pdf, OFFSET_2, secondOffset);
        pdf = replace(pdf, LENGTH_2, pdf.length() - secondOffset);
        return pdf.getBytes(StandardCharsets.US_ASCII);
    }

    private static String replace(String value, String token, int number) {
        int index = value.indexOf(token);
        if (index < 0 || value.indexOf(token, index + token.length()) >= 0) {
            throw new IllegalStateException("T11 signature token is not unique");
        }
        return value.substring(0, index) + fixedWidth(number)
                + value.substring(index + token.length());
    }

    private static String fixedWidth(int value) {
        return String.format(Locale.ROOT, "%010d", Integer.valueOf(value));
    }

    private static String repeated(String value, int count) {
        StringBuilder result = new StringBuilder(value.length() * count);
        for (int index = 0; index < count; index++) {
            result.append(value);
        }
        return result.toString();
    }

    private static void write(ByteArrayOutputStream output, String value)
            throws IOException {
        output.write(value.getBytes(StandardCharsets.US_ASCII));
    }
}
