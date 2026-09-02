package net.zerocloud.pdf;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.zerocloud.pdf.command.UpdateAnnotations;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNull;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;

/** Conservative COS-level discovery and authorization for Existing Signatures. */
final class PdfBoxSignaturePolicy {

    private static final int MAXIMUM_FIELD_NODES = 4096;
    private static final int MAXIMUM_FIELD_DEPTH = 64;
    private static final int MAXIMUM_PERMISSION_ENTRIES = 16;
    private static final int MAXIMUM_SIGNATURE_DICTIONARY_ENTRIES = 64;
    private static final int MAXIMUM_BYTE_RANGE_ENTRIES = 256;
    private static final int MAXIMUM_SIGNATURE_REFERENCES = 64;
    private static final int MAXIMUM_INDIRECT_DEPTH = 1;
    private static final COSName PERMS = COSName.getPDFName("Perms");
    private static final COSName DOC_MDP = COSName.getPDFName("DocMDP");
    private static final COSName UR3 = COSName.getPDFName("UR3");
    private static final COSName REFERENCE = COSName.getPDFName("Reference");
    private static final COSName TRANSFORM_METHOD = COSName.getPDFName("TransformMethod");
    private static final COSName TRANSFORM_PARAMS = COSName.getPDFName("TransformParams");
    private static final COSName P = COSName.getPDFName("P");
    private static final COSName V = COSName.getPDFName("V");
    private static final COSName VERSION_1_2 = COSName.getPDFName("1.2");

    private enum Permission {
        UNSIGNED,
        DENY,
        NON_WIDGET_ANNOTATIONS
    }

    private final Permission permission;

    private PdfBoxSignaturePolicy(Permission permission) {
        this.permission = permission;
    }

    static PdfBoxSignaturePolicy inspect(PDDocument document, long sourceLength)
            throws DocumentFailure {
        try {
            COSDictionary trailer = document.getDocument().getTrailer();
            COSDictionary catalog = dictionary(trailer.getItem(COSName.ROOT));
            Set<COSDictionary> fieldSignatures = identitySet();
            COSBase rawAcroForm = catalog.getItem(COSName.ACRO_FORM);
            if (rawAcroForm != null) {
                COSDictionary acroForm = dictionary(rawAcroForm);
                COSBase rawFields = acroForm.getItem(COSName.FIELDS);
                if (rawFields != null) {
                    inspectFields(array(rawFields), fieldSignatures);
                }
            }

            PermissionEntries permissionEntries = inspectPermissionEntries(catalog);
            Set<COSDictionary> allSignatures = identitySet();
            allSignatures.addAll(fieldSignatures);
            allSignatures.addAll(permissionEntries.signatures);
            if (allSignatures.size() > MAXIMUM_FIELD_NODES + 2) {
                throw invalidStructure();
            }
            if (allSignatures.isEmpty() && !permissionEntries.unknownHandler) {
                return new PdfBoxSignaturePolicy(Permission.UNSIGNED);
            }

            Map<COSDictionary, SignatureInfo> signatureInfo =
                    new IdentityHashMap<COSDictionary, SignatureInfo>();
            for (COSDictionary signature : allSignatures) {
                signatureInfo.put(signature, validateSignature(signature, sourceLength));
            }

            COSDictionary certification = permissionEntries.docMdp;
            if (certification != null && !fieldSignatures.contains(certification)) {
                throw invalidStructure();
            }
            int docMdpReferences = 0;
            for (Map.Entry<COSDictionary, SignatureInfo> entry : signatureInfo.entrySet()) {
                docMdpReferences += entry.getValue().docMdpReferences;
                if (entry.getValue().docMdpReferences > 0
                        && entry.getKey() != certification) {
                    throw invalidStructure();
                }
            }
            if (docMdpReferences > 1) {
                throw invalidStructure();
            }

            if (certification == null
                    || allSignatures.size() != 1
                    || permissionEntries.unknownHandler
                    || permissionEntries.usageRights != null) {
                return new PdfBoxSignaturePolicy(Permission.DENY);
            }
            SignatureInfo certificationInfo = signatureInfo.get(certification);
            if (certificationInfo.docMdpReferences != 1
                    || certificationInfo.unsupportedRestriction
                    || !certificationInfo.coversFromStart) {
                return new PdfBoxSignaturePolicy(Permission.DENY);
            }
            return new PdfBoxSignaturePolicy(
                    certificationInfo.docMdpPermission == 3
                            ? Permission.NON_WIDGET_ANNOTATIONS
                            : Permission.DENY);
        } catch (DocumentFailure failure) {
            throw failure;
        } catch (RuntimeException backendFailure) {
            throw invalidStructure();
        }
    }

    boolean hasExistingSignatures() {
        return permission != Permission.UNSIGNED;
    }

    boolean permits(DocumentCommand command) {
        return permission == Permission.UNSIGNED
                || (permission == Permission.NON_WIDGET_ANNOTATIONS
                        && command instanceof UpdateAnnotations);
    }

    boolean permitsSignedPublication(boolean mutationOccurred) {
        return permission == Permission.NON_WIDGET_ANNOTATIONS
                && mutationOccurred;
    }

    boolean requiresNonWidgetAnnotationPolicy(DocumentCommand command) {
        return permission == Permission.NON_WIDGET_ANNOTATIONS
                && command instanceof UpdateAnnotations;
    }

    private static void inspectFields(COSArray roots, Set<COSDictionary> signatures)
            throws DocumentFailure {
        Deque<FieldNode> pending = new ArrayDeque<FieldNode>();
        if (roots.size() > MAXIMUM_FIELD_NODES) {
            throw invalidStructure();
        }
        for (int index = roots.size() - 1; index >= 0; index--) {
            pending.push(new FieldNode(dictionary(roots.get(index)), null, null, null, 1));
        }
        Set<COSDictionary> visited = identitySet();
        int fieldCount = 0;
        while (!pending.isEmpty()) {
            FieldNode node = pending.pop();
            fieldCount++;
            if (fieldCount > MAXIMUM_FIELD_NODES
                    || node.depth > MAXIMUM_FIELD_DEPTH
                    || !visited.add(node.field)) {
                throw invalidStructure();
            }
            COSBase rawParent = node.field.getItem(COSName.PARENT);
            if (node.parent == null) {
                if (rawParent != null) {
                    throw invalidStructure();
                }
            } else if (rawParent == null || dictionary(rawParent) != node.parent) {
                throw invalidStructure();
            }

            COSName fieldType = inheritedName(node.field, COSName.FT, node.inheritedType);
            COSBase fieldValue = node.field.containsKey(COSName.V)
                    ? nullable(node.field.getItem(COSName.V)) : node.inheritedValue;
            if (COSName.SIG.equals(fieldType) && fieldValue != null) {
                signatures.add(dictionary(fieldValue));
            }
            COSBase rawKids = node.field.getItem(COSName.KIDS);
            if (rawKids == null) {
                continue;
            }
            COSArray kids = array(rawKids);
            if ((node.depth == MAXIMUM_FIELD_DEPTH && kids.size() > 0)
                    || kids.size()
                    > MAXIMUM_FIELD_NODES - fieldCount - pending.size()) {
                throw invalidStructure();
            }
            for (int index = kids.size() - 1; index >= 0; index--) {
                pending.push(new FieldNode(
                        dictionary(kids.get(index)), fieldType, fieldValue,
                        node.field, node.depth + 1));
            }
        }
    }

    private static PermissionEntries inspectPermissionEntries(COSDictionary catalog)
            throws DocumentFailure {
        COSBase rawPermissions = catalog.getItem(PERMS);
        if (rawPermissions == null) {
            return new PermissionEntries();
        }
        COSDictionary permissions = dictionary(rawPermissions);
        if (permissions.keySet().size() > MAXIMUM_PERMISSION_ENTRIES) {
            throw invalidStructure();
        }
        PermissionEntries entries = new PermissionEntries();
        for (COSName key : permissions.keySet()) {
            if (COSName.TYPE.equals(key)) {
                COSBase type = nullable(permissions.getItem(key));
                if (type != null && !PERMS.equals(type)) {
                    throw invalidStructure();
                }
            } else if (DOC_MDP.equals(key)) {
                COSBase raw = permissions.getItem(key);
                if (!(raw instanceof COSObject)) {
                    throw invalidStructure();
                }
                entries.docMdp = dictionary(raw);
                entries.signatures.add(entries.docMdp);
            } else if (UR3.equals(key)) {
                entries.usageRights = dictionary(permissions.getItem(key));
                entries.signatures.add(entries.usageRights);
            } else {
                entries.unknownHandler = true;
            }
        }
        return entries;
    }

    private static SignatureInfo validateSignature(COSDictionary signature, long sourceLength)
            throws DocumentFailure {
        requireDirectValues(signature);
        COSBase type = nullable(signature.getItem(COSName.TYPE));
        if (type != null && !COSName.SIG.equals(type)) {
            throw invalidStructure();
        }
        if (!(resolve(signature.getItem(COSName.CONTENTS)) instanceof COSString)) {
            throw invalidStructure();
        }
        COSArray byteRange = array(signature.getItem(COSName.BYTERANGE));
        if (byteRange.size() < 2
                || byteRange.size() > MAXIMUM_BYTE_RANGE_ENTRIES
                || byteRange.size() % 2 != 0) {
            throw invalidStructure();
        }
        long previousEnd = -1L;
        boolean coversFromStart = false;
        for (int index = 0; index < byteRange.size(); index += 2) {
            long start = integer(direct(byteRange.get(index)));
            long length = integer(direct(byteRange.get(index + 1)));
            if (start < 0L || length < 0L
                    || start > Long.MAX_VALUE - length
                    || (previousEnd >= 0L && start < previousEnd)
                    || (sourceLength >= 0L && start + length > sourceLength)) {
                throw invalidStructure();
            }
            if (index == 0) {
                coversFromStart = start == 0L;
            }
            previousEnd = start + length;
        }

        SignatureInfo info = new SignatureInfo(coversFromStart);
        COSBase rawReferences = signature.getItem(REFERENCE);
        if (rawReferences == null) {
            return info;
        }
        COSArray references = array(rawReferences);
        if (references.size() > MAXIMUM_SIGNATURE_REFERENCES) {
            throw invalidStructure();
        }
        for (int index = 0; index < references.size(); index++) {
            COSDictionary reference = dictionary(direct(references.get(index)));
            COSBase rawMethod = resolve(direct(
                    reference.getItem(TRANSFORM_METHOD)));
            if (!(rawMethod instanceof COSName)) {
                throw invalidStructure();
            }
            COSName method = (COSName) rawMethod;
            if (DOC_MDP.equals(method)) {
                info.docMdpReferences++;
                inspectDocMdpTransform(reference, info);
            } else {
                info.unsupportedRestriction = true;
            }
        }
        return info;
    }

    private static void inspectDocMdpTransform(COSDictionary reference, SignatureInfo info)
            throws DocumentFailure {
        COSBase rawParameters = reference.getItem(TRANSFORM_PARAMS);
        if (rawParameters == null) {
            info.unsupportedRestriction = true;
            return;
        }
        COSDictionary parameters;
        try {
            parameters = dictionary(direct(rawParameters));
        } catch (DocumentFailure wrongType) {
            info.unsupportedRestriction = true;
            return;
        }
        COSBase rawType = nullable(direct(parameters.getItem(COSName.TYPE)));
        if (rawType != null && !TRANSFORM_PARAMS.equals(rawType)) {
            throw invalidStructure();
        }
        COSBase rawPermission = nullable(direct(parameters.getItem(P)));
        long permission = rawPermission == null ? 2L : integer(rawPermission);
        if (permission < 1L || permission > 3L) {
            throw invalidStructure();
        }
        COSBase rawVersion = nullable(direct(parameters.getItem(V)));
        if (rawVersion != null && !VERSION_1_2.equals(rawVersion)) {
            throw invalidStructure();
        }
        info.docMdpPermission = (int) permission;
    }

    private static COSName inheritedName(COSDictionary dictionary, COSName key, COSName inherited)
            throws DocumentFailure {
        COSBase raw = dictionary.getItem(key);
        if (raw == null) {
            return inherited;
        }
        COSBase value = resolve(raw);
        if (!(value instanceof COSName)) {
            throw invalidStructure();
        }
        return (COSName) value;
    }

    private static COSDictionary dictionary(COSBase raw) throws DocumentFailure {
        COSBase value = resolve(raw);
        if (!(value instanceof COSDictionary)) {
            throw invalidStructure();
        }
        return (COSDictionary) value;
    }

    private static COSArray array(COSBase raw) throws DocumentFailure {
        COSBase value = resolve(raw);
        if (!(value instanceof COSArray)) {
            throw invalidStructure();
        }
        return (COSArray) value;
    }

    private static long integer(COSBase raw) throws DocumentFailure {
        COSBase value = resolve(raw);
        if (!(value instanceof COSInteger)) {
            throw invalidStructure();
        }
        return ((COSInteger) value).longValue();
    }

    private static COSBase nullable(COSBase raw) throws DocumentFailure {
        if (raw == null) {
            return null;
        }
        COSBase value = resolve(raw);
        return value instanceof COSNull ? null : value;
    }

    private static void requireDirectValues(COSDictionary dictionary)
            throws DocumentFailure {
        if (dictionary.keySet().size()
                > MAXIMUM_SIGNATURE_DICTIONARY_ENTRIES) {
            throw invalidStructure();
        }
        for (COSName key : dictionary.keySet()) {
            direct(dictionary.getItem(key));
        }
    }

    private static COSBase direct(COSBase raw) throws DocumentFailure {
        if (raw instanceof COSObject) {
            throw invalidStructure();
        }
        return raw;
    }

    private static COSBase resolve(COSBase raw) throws DocumentFailure {
        if (raw == null) {
            throw invalidStructure();
        }
        COSBase value = raw;
        Set<COSBase> visited = Collections.newSetFromMap(
                new IdentityHashMap<COSBase, Boolean>());
        int depth = 0;
        while (value instanceof COSObject) {
            depth++;
            if (depth > MAXIMUM_INDIRECT_DEPTH || !visited.add(value)) {
                throw invalidStructure();
            }
            value = ((COSObject) value).getObject();
            if (value == null) {
                throw invalidStructure();
            }
        }
        return value;
    }

    private static <T> Set<T> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<T, Boolean>());
    }

    private static DocumentFailure invalidStructure() {
        return PdfBoxWorkflowEngine.incrementalFailure(
                DocumentFailureCode.SIGNATURE_STRUCTURE_INVALID,
                "The Existing Signature policy could not be determined safely.");
    }

    private static final class PermissionEntries {
        private final Set<COSDictionary> signatures = identitySet();
        private COSDictionary docMdp;
        private COSDictionary usageRights;
        private boolean unknownHandler;
    }

    private static final class SignatureInfo {
        private final boolean coversFromStart;
        private int docMdpReferences;
        private int docMdpPermission;
        private boolean unsupportedRestriction;

        private SignatureInfo(boolean coversFromStart) {
            this.coversFromStart = coversFromStart;
        }
    }

    private static final class FieldNode {
        private final COSDictionary field;
        private final COSName inheritedType;
        private final COSBase inheritedValue;
        private final COSDictionary parent;
        private final int depth;

        private FieldNode(COSDictionary field, COSName inheritedType,
                COSBase inheritedValue, COSDictionary parent, int depth) {
            this.field = field;
            this.inheritedType = inheritedType;
            this.inheritedValue = inheritedValue;
            this.parent = parent;
            this.depth = depth;
        }
    }
}
