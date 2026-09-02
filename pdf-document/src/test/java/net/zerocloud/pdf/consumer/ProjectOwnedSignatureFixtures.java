package net.zerocloud.pdf.consumer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Deterministic structural signature fixtures authored by the Folio PDF project. */
final class ProjectOwnedSignatureFixtures {

    private static final String FIRST_OFFSET = "AAAAAAAAAA";
    private static final String FIRST_LENGTH = "BBBBBBBBBB";
    private static final String SECOND_OFFSET = "CCCCCCCCCC";
    private static final String SECOND_LENGTH = "DDDDDDDDDD";
    private static final String THIRD_OFFSET = "EEEEEEEEEE";
    private static final String THIRD_LENGTH = "FFFFFFFFFF";
    private static final String FOURTH_OFFSET = "GGGGGGGGGG";
    private static final String FOURTH_LENGTH = "HHHHHHHHHH";

    private ProjectOwnedSignatureFixtures() {
    }

    static byte[] ordinaryApprovalSignature() throws IOException {
        return oneSignature(
                "",
                "",
                " /Kids [7 0 R]",
                "<< /Parent 5 0 R /T (InheritedProjectOwnedApproval) >>");
    }

    static byte[] docMdpSignature(int permission) throws IOException {
        if (permission < 1 || permission > 3) {
            throw new IllegalArgumentException("permission must be 1, 2, or 3");
        }
        return oneSignature(
                " /Perms << /DocMDP 6 0 R >>",
                " /Reference [<< /Type /SigRef /TransformMethod /DocMDP "
                        + "/TransformParams << /Type /TransformParams /P "
                        + permission + " /V /1.2 >> >>]",
                "");
    }

    static byte[] docMdpDefaultPermission() throws IOException {
        return oneSignature(
                " /Perms << /DocMDP 6 0 R >>",
                " /Reference [<< /TransformMethod /DocMDP "
                        + "/TransformParams << /V /1.2 >> >>]",
                "");
    }

    static byte[] outOfBoundsByteRange() throws IOException {
        return oneSignature("", "", " /MalformedRange true");
    }

    static byte[] cyclicSignatureFieldTree() throws IOException {
        return oneSignature("", "", " /Kids [5 0 R] /Parent 5 0 R");
    }

    static byte[] contradictoryDocMdpReference() throws IOException {
        return oneSignature(
                " /Perms << /DocMDP 7 0 R >>",
                " /Reference [<< /TransformMethod /DocMDP "
                        + "/TransformParams << /P 3 /V /1.2 >> >>]",
                "",
                "<< /ByteRange [0 1] /Contents <00> >>");
    }

    static byte[] indirectDocMdpReference() throws IOException {
        return oneSignature(
                " /Perms << /DocMDP 6 0 R >>",
                " /Reference 7 0 R",
                "",
                "[<< /TransformMethod /DocMDP /TransformParams "
                        + "<< /P 3 /V /1.2 >> >>]");
    }

    static byte[] unsupportedTransform() throws IOException {
        return oneSignature(
                "",
                " /Reference [<< /TransformMethod /ProjectOwnedUnknown >>]",
                "");
    }

    static byte[] excessiveFieldRoots() throws IOException {
        return fieldRoots(4097);
    }

    static byte[] maximumFieldRoots() throws IOException {
        return fieldRoots(4096);
    }

    private static byte[] fieldRoots(int fieldCount) throws IOException {
        List<String> objects = new ArrayList<String>();
        objects.add("<< /Type /Catalog /Pages 2 0 R /AcroForm 4 0 R >>");
        objects.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                + "/Resources << >> >>");
        StringBuilder fields = new StringBuilder("<< /Fields [");
        for (int index = 0; index < fieldCount; index++) {
            fields.append(index + 5).append(" 0 R ");
        }
        fields.append("] >>");
        objects.add(fields.toString());
        for (int index = 0; index < fieldCount; index++) {
            objects.add("<< /FT /Tx /T (ProjectOwnedField" + index + ") >>");
        }
        return finish(objects);
    }

    static byte[] maximumFieldChildren() throws IOException {
        return fieldChildren(4095);
    }

    static byte[] excessiveFieldChildren() throws IOException {
        return fieldChildren(4096);
    }

    private static byte[] fieldChildren(int childCount) throws IOException {
        List<String> objects = new ArrayList<String>();
        objects.add("<< /Type /Catalog /Pages 2 0 R /AcroForm 4 0 R >>");
        objects.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                + "/Resources << >> >>");
        objects.add("<< /Fields [5 0 R] >>");
        StringBuilder root = new StringBuilder(
                "<< /FT /Tx /T (ProjectOwnedRoot) /Kids [");
        for (int index = 0; index < childCount; index++) {
            root.append(index + 6).append(" 0 R ");
        }
        root.append("] >>");
        objects.add(root.toString());
        for (int index = 0; index < childCount; index++) {
            objects.add("<< /Parent 5 0 R /T (ProjectOwnedChild"
                    + index + ") >>");
        }
        return finish(objects);
    }

    static byte[] maximumFieldDepth() throws IOException {
        return fieldDepth(64);
    }

    static byte[] excessiveFieldDepth() throws IOException {
        return fieldDepth(65);
    }

    private static byte[] fieldDepth(int depth) throws IOException {
        List<String> objects = new ArrayList<String>();
        objects.add("<< /Type /Catalog /Pages 2 0 R /AcroForm 4 0 R >>");
        objects.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                + "/Resources << >> >>");
        objects.add("<< /Fields [5 0 R] >>");
        for (int index = 0; index < depth; index++) {
            int objectNumber = index + 5;
            String parent = index == 0
                    ? "" : " /Parent " + (objectNumber - 1) + " 0 R";
            String kids = index + 1 == depth
                    ? "" : " /Kids [" + (objectNumber + 1) + " 0 R]";
            objects.add("<< /FT /Tx /T (ProjectOwnedDepth" + index + ")"
                    + parent + kids + " >>");
        }
        return finish(objects);
    }

    static byte[] excessiveByteRangeEntries() throws IOException {
        return byteRangeEntries(258);
    }

    static byte[] maximumByteRangeEntries() throws IOException {
        return byteRangeEntries(256);
    }

    private static byte[] byteRangeEntries(int entryCount) throws IOException {
        StringBuilder byteRange = new StringBuilder("<< /ByteRange [");
        for (int index = 0; index < entryCount / 2; index++) {
            byteRange.append("0 0 ");
        }
        byteRange.append("] /Contents <00> >>");
        return oneSignatureWithDictionary("", "", byteRange.toString(), null);
    }

    static byte[] excessiveSignatureReferences() throws IOException {
        return signatureReferences(65);
    }

    static byte[] maximumSignatureReferences() throws IOException {
        return signatureReferences(64);
    }

    private static byte[] signatureReferences(int referenceCount)
            throws IOException {
        StringBuilder references = new StringBuilder(" /Reference [");
        for (int index = 0; index < referenceCount; index++) {
            references.append(
                    "<< /TransformMethod /ProjectOwnedUnknown >> ");
        }
        references.append("]");
        return oneSignature("", references.toString(), "");
    }

    static byte[] maximumSignatureDictionaryEntries() throws IOException {
        return signatureDictionaryEntries(64);
    }

    static byte[] excessiveSignatureDictionaryEntries() throws IOException {
        return signatureDictionaryEntries(65);
    }

    private static byte[] signatureDictionaryEntries(int entryCount)
            throws IOException {
        StringBuilder entries = new StringBuilder();
        for (int index = 0; index < entryCount - 4; index++) {
            entries.append(" /ProjectOwnedSignatureEntry")
                    .append(index)
                    .append(" null");
        }
        return oneSignature("", entries.toString(), "");
    }

    static byte[] maximumPermissionEntries() throws IOException {
        return permissionEntries(16);
    }

    static byte[] excessivePermissionEntries() throws IOException {
        return permissionEntries(17);
    }

    private static byte[] permissionEntries(int entryCount) throws IOException {
        StringBuilder permissions = new StringBuilder(" /Perms <<");
        for (int index = 0; index < entryCount; index++) {
            permissions.append(" /ProjectOwnedPermission")
                    .append(index)
                    .append(" null");
        }
        permissions.append(" >>");
        return documentWithCatalogEntries(permissions.toString());
    }

    static byte[] maximumIndirectResolution() throws IOException {
        return documentWithIndirectFields(false);
    }

    static byte[] excessiveIndirectResolution() throws IOException {
        return documentWithIndirectFields(true);
    }

    private static byte[] documentWithIndirectFields(boolean nested)
            throws IOException {
        List<String> objects = new ArrayList<String>();
        objects.add("<< /Type /Catalog /Pages 2 0 R /AcroForm 4 0 R >>");
        objects.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                + "/Resources << >> >>");
        objects.add("<< /Fields 5 0 R >>");
        objects.add(nested ? "6 0 R" : "[]");
        if (nested) {
            objects.add("[]");
        }
        return finish(objects);
    }

    private static byte[] documentWithCatalogEntries(String catalogEntries)
            throws IOException {
        List<String> objects = new ArrayList<String>();
        objects.add("<< /Type /Catalog /Pages 2 0 R"
                + catalogEntries + " >>");
        objects.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                + "/Resources << >> >>");
        return finish(objects);
    }

    static byte[] docMdpP3WithOrdinaryApproval() throws IOException {
        List<String> objects = new ArrayList<String>();
        objects.add("<< /Type /Catalog /Pages 2 0 R /AcroForm 4 0 R "
                + "/Perms << /DocMDP 6 0 R >> >>");
        objects.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                + "/Resources << >> >>");
        objects.add("<< /Fields [5 0 R 7 0 R] /SigFlags 3 >>");
        objects.add("<< /FT /Sig /T (ProjectOwnedCertification) /V 6 0 R >>");
        objects.add(signatureDictionary(
                FIRST_OFFSET,
                FIRST_LENGTH,
                SECOND_OFFSET,
                SECOND_LENGTH,
                " /Reference [<< /TransformMethod /DocMDP "
                        + "/TransformParams << /P 3 /V /1.2 >> >>]"));
        objects.add("<< /FT /Sig /T (ProjectOwnedApproval) /V 8 0 R >>");
        objects.add(signatureDictionary(
                THIRD_OFFSET,
                THIRD_LENGTH,
                FOURTH_OFFSET,
                FOURTH_LENGTH,
                ""));
        return finish(objects);
    }

    private static byte[] oneSignature(
            String catalogEntries,
            String signatureEntries,
            String fieldEntries) throws IOException {
        return oneSignature(catalogEntries, signatureEntries, fieldEntries, null);
    }

    private static byte[] oneSignature(
            String catalogEntries,
            String signatureEntries,
            String fieldEntries,
            String additionalObject) throws IOException {
        return oneSignatureWithDictionary(
                catalogEntries,
                fieldEntries,
                signatureDictionary(
                        FIRST_OFFSET,
                        FIRST_LENGTH,
                        SECOND_OFFSET,
                        SECOND_LENGTH,
                        signatureEntries),
                additionalObject);
    }

    private static byte[] oneSignatureWithDictionary(
            String catalogEntries,
            String fieldEntries,
            String signatureDictionary,
            String additionalObject) throws IOException {
        List<String> objects = new ArrayList<String>();
        objects.add("<< /Type /Catalog /Pages 2 0 R /AcroForm 4 0 R"
                + catalogEntries + " >>");
        objects.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                + "/Resources << >> >>");
        objects.add("<< /Fields [5 0 R] /SigFlags 3 >>");
        objects.add("<< /FT /Sig /T (ProjectOwnedApproval) "
                + "/V 6 0 R" + fieldEntries + " >>");
        objects.add(signatureDictionary);
        if (additionalObject != null) {
            objects.add(additionalObject);
        }
        byte[] pdf = finish(objects);
        if (fieldEntries.contains("MalformedRange")) {
            String value = new String(pdf, StandardCharsets.US_ASCII);
            int byteRange = value.indexOf("/ByteRange [");
            int closing = value.indexOf(']', byteRange);
            String invalid = "/ByteRange [0 9999999999]";
            StringBuilder padded = new StringBuilder(invalid);
            while (padded.length() < closing + 1 - byteRange) {
                padded.insert(padded.length() - 1, ' ');
            }
            value = value.substring(0, byteRange) + padded.toString()
                    + value.substring(closing + 1);
            return value.getBytes(StandardCharsets.US_ASCII);
        }
        return pdf;
    }

    private static String signatureDictionary(
            String firstOffset,
            String firstLength,
            String secondOffset,
            String secondLength,
            String signatureEntries) {
        return "<< /Filter /Adobe.PPKLite /SubFilter /adbe.pkcs7.detached "
                + "/ByteRange [" + firstOffset + " " + firstLength + " "
                + secondOffset + " " + secondLength + "] /Contents <"
                + repeatedHexByte(128) + ">" + signatureEntries + " >>";
    }

    private static byte[] finish(List<String> objects) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        write(output, "%PDF-1.7\n%FolioSignatureFixture\n");
        List<Integer> offsets = new ArrayList<Integer>();
        offsets.add(Integer.valueOf(0));
        for (int index = 0; index < objects.size(); index++) {
            offsets.add(Integer.valueOf(output.size()));
            write(output, Integer.toString(index + 1));
            write(output, " 0 obj\n");
            write(output, objects.get(index));
            write(output, "\nendobj\n");
        }
        int xref = output.size();
        write(output, "xref\n0 " + (objects.size() + 1) + "\n");
        write(output, "0000000000 65535 f \n");
        for (int index = 1; index < offsets.size(); index++) {
            write(output, fixedWidth(offsets.get(index).intValue()));
            write(output, " 00000 n \n");
        }
        write(output, "trailer\n<< /Size " + (objects.size() + 1)
                + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");

        String pdf = new String(output.toByteArray(), StandardCharsets.US_ASCII);
        if (pdf.contains(FIRST_OFFSET)) {
            pdf = patchByteRange(
                    pdf,
                    FIRST_OFFSET,
                    FIRST_LENGTH,
                    SECOND_OFFSET,
                    SECOND_LENGTH);
        }
        if (pdf.contains(THIRD_OFFSET)) {
            pdf = patchByteRange(
                    pdf,
                    THIRD_OFFSET,
                    THIRD_LENGTH,
                    FOURTH_OFFSET,
                    FOURTH_LENGTH);
        }
        return pdf.getBytes(StandardCharsets.US_ASCII);
    }

    private static String patchByteRange(
            String pdf,
            String firstOffset,
            String firstLength,
            String secondOffsetToken,
            String secondLength) {
        int range = pdf.indexOf(firstOffset);
        int contentsOpen = pdf.indexOf("/Contents <", range)
                + "/Contents ".length();
        int contentsClose = pdf.indexOf('>', contentsOpen);
        int secondOffset = contentsClose + 1;
        pdf = replace(pdf, firstOffset, 0);
        pdf = replace(pdf, firstLength, contentsOpen);
        pdf = replace(pdf, secondOffsetToken, secondOffset);
        return replace(pdf, secondLength, pdf.length() - secondOffset);
    }

    private static String replace(String value, String token, int number) {
        int index = value.indexOf(token);
        if (index < 0 || value.indexOf(token, index + token.length()) >= 0) {
            throw new IllegalStateException("Signature fixture token is not unique");
        }
        return value.substring(0, index) + fixedWidth(number)
                + value.substring(index + token.length());
    }

    private static String fixedWidth(int value) {
        return String.format(Locale.ROOT, "%010d", Integer.valueOf(value));
    }

    private static String repeatedHexByte(int count) {
        StringBuilder value = new StringBuilder(count * 2);
        for (int index = 0; index < count; index++) {
            value.append("00");
        }
        return value.toString();
    }

    private static void write(ByteArrayOutputStream output, String value)
            throws IOException {
        output.write(value.getBytes(StandardCharsets.US_ASCII));
    }
}
