package net.zerocloud.pdf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSBoolean;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNull;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;

/** Keeps the low-level public model detached from PDFBox identity and types. */
final class PdfBoxValueAdapter {

    private static final int MAXIMUM_RECURSIVE_MATERIALIZATION_DEPTH = 256;

    static final String CAPABILITY_ID = "document.value.inspect-patch";

    private final PDDocument document;
    private final PdfBoxDocumentSession session;
    private final WorkflowResourceContext resources;
    private final Object sessionIdentity = new Object();
    private final IdentityHashMap<COSBase, ObjectReference> references =
            new IdentityHashMap<COSBase, ObjectReference>();
    private final Map<ObjectReference, ReferenceTarget> targets =
            new HashMap<ObjectReference, ReferenceTarget>();
    private long nextReferenceIdentity = 1L;

    PdfBoxValueAdapter(
            PDDocument document,
            PdfBoxDocumentSession session,
            WorkflowResourceContext resources) {
        this.document = document;
        this.session = session;
        this.resources = resources;
    }

    ObjectReference documentRootReference() throws DocumentFailure {
        COSBase root = document.getDocument().getTrailer().getItem(COSName.ROOT);
        if (root == null) {
            root = document.getDocumentCatalog().getCOSObject();
        }
        return referenceFor(root);
    }

    ObjectReference pageReference(COSBase pageTreeReference)
            throws DocumentFailure {
        return referenceFor(pageTreeReference);
    }

    ObjectReference resourceReference(COSObject resource)
            throws DocumentFailure {
        return referenceFor(resource);
    }

    COSBase referencedObject(ObjectReference reference)
            throws DocumentFailure {
        requireOwned(reference);
        ReferenceTarget target = targets.get(reference);
        if (target == null) {
            throw failure(
                    DocumentFailureCode.QUERY_FAILED,
                    "The Object Reference is unavailable.");
        }
        return target.rawValue;
    }

    PdfValue inspect(
            ObjectReference reference,
            PdfInspectionLimits limits) throws DocumentFailure {
        requireOwned(reference);
        ReferenceTarget target = targets.get(reference);
        if (target == null) {
            throw failure(
                    DocumentFailureCode.QUERY_FAILED,
                    "The Object Reference could not be inspected.");
        }
        return publicValue(
                target.value,
                new InspectionBudget(limits),
                reference);
    }

    void apply(DocumentPatch patch) throws DocumentFailure {
        resources.checkpoint();
        try (PreparedPatch preparedPatch = prepare(patch)) {
            List<PreparedChange> prepared = preparedPatch.changes;
            rejectReferenceCycles(prepared);
            int applied = 0;
            try {
                for (PreparedChange change : prepared) {
                    resources.checkpoint();
                    change.target.setItem(change.name, change.value);
                    applied++;
                }
                preparedPatch.transfer();
            } catch (DocumentFailure failure) {
                rollback(prepared, applied);
                throw failure;
            } catch (RuntimeException applicationFailure) {
                rollback(prepared, applied);
                throw applicationFailure;
            }
        }
    }

    private PreparedPatch prepare(DocumentPatch patch)
            throws DocumentFailure {
        WorkflowResourceContext.OwnedMemoryScope ownership =
                resources.ownedMemoryScope();
        List<PreparedChange> prepared = new ArrayList<PreparedChange>(
                patch.getChanges().size());
        try {
            for (DocumentPatch.DictionaryEntryChange change
                    : patch.getChanges()) {
                resources.checkpoint();
                requireOwned(change.getTarget());
                ReferenceTarget target = targets.get(change.getTarget());
                if (target == null || !(target.value instanceof COSDictionary)) {
                    throw failure(
                            DocumentFailureCode.COMMAND_REJECTED,
                            "The Document Patch target is not a dictionary.");
                }
                if (target.value instanceof COSStream
                        && isEngineOwnedStreamName(change.getName())) {
                    throw illegalStreamChange();
                }
                COSDictionary dictionary = (COSDictionary) target.value;
                COSName name = COSName.getPDFName(change.getName().getValue());
                requireNoVersionSecurityChange(dictionary, name);
                List<ObjectReference> referencedObjects =
                        new ArrayList<ObjectReference>();
                requirePatchNesting(change.getValue());
                COSBase value = backendValue(
                        change.getValue(),
                        referencedObjects,
                        ownership);
                prepared.add(new PreparedChange(
                        change.getTarget(),
                        dictionary,
                        name,
                        value,
                        referencedObjects,
                        dictionary.containsKey(name),
                        dictionary.getItem(name)));
            }
            return new PreparedPatch(prepared, ownership);
        } catch (DocumentFailure failure) {
            ownership.close();
            throw failure;
        } catch (RuntimeException | Error failure) {
            ownership.close();
            throw failure;
        }
    }

    private void requireNoVersionSecurityChange(
            COSDictionary dictionary,
            COSName name) throws DocumentFailure {
        COSDictionary catalog = document.getDocumentCatalog().getCOSObject();
        COSDictionary encryption = document.getEncryption() == null
                ? null : document.getEncryption().getCOSObject();
        COSDictionary trailer = document.getDocument().getTrailer();
        if ((dictionary == catalog
                        && (COSName.VERSION.equals(name)
                                || COSName.EXTENSIONS.equals(name)))
                || containsDictionary(
                        catalog.getItem(COSName.EXTENSIONS),
                        dictionary,
                        new IdentityHashMap<COSBase, Boolean>())
                || containsDictionary(
                        trailer.getItem(COSName.ENCRYPT),
                        dictionary,
                        new IdentityHashMap<COSBase, Boolean>())
                || containsDictionary(
                        encryption,
                        dictionary,
                        new IdentityHashMap<COSBase, Boolean>())
                || (dictionary == trailer && COSName.ENCRYPT.equals(name))) {
            throw PdfBoxWorkflowEngine.versionFailure(
                    DocumentFailureCode.COMMAND_REJECTED,
                    "A Document Patch cannot change engine-owned version or password-security state.");
        }
    }

    private boolean containsDictionary(
            COSBase value,
            COSDictionary target,
            IdentityHashMap<COSBase, Boolean> visited)
            throws DocumentFailure {
        Deque<BackendValueNode> pending =
                new ArrayDeque<BackendValueNode>();
        pending.push(new BackendValueNode(value, 1));
        while (!pending.isEmpty()) {
            resources.checkpoint();
            BackendValueNode current = pending.pop();
            COSBase candidate = current.value;
            if (candidate == null) {
                continue;
            }
            if (candidate == target) {
                return true;
            }
            if (visited.put(candidate, Boolean.TRUE) != null) {
                continue;
            }
            if (candidate instanceof COSObject) {
                resources.requireNestingDepth(current.depth);
                pending.push(new BackendValueNode(
                        ((COSObject) candidate).getObject(),
                        current.depth + 1));
            } else if (candidate instanceof COSArray) {
                resources.requireNestingDepth(current.depth);
                COSArray array = (COSArray) candidate;
                for (int index = array.size() - 1; index >= 0; index--) {
                    resources.checkpoint();
                    pending.push(new BackendValueNode(
                            array.get(index),
                            current.depth + 1));
                }
            } else if (candidate instanceof COSDictionary) {
                resources.requireNestingDepth(current.depth);
                COSDictionary dictionary = (COSDictionary) candidate;
                for (COSBase entry : dictionary.getValues()) {
                    resources.checkpoint();
                    pending.push(new BackendValueNode(
                            entry,
                            current.depth + 1));
                }
            }
        }
        return false;
    }

    private static void rollback(
            List<PreparedChange> prepared,
            int applied) {
        for (int index = applied - 1; index >= 0; index--) {
            PreparedChange change = prepared.get(index);
            if (change.originallyPresent) {
                change.target.setItem(change.name, change.originalValue);
            } else {
                change.target.removeItem(change.name);
            }
        }
    }

    private static boolean isEngineOwnedStreamName(PdfName name) {
        String value = name.getValue();
        return "Length".equals(value)
                || "Filter".equals(value)
                || "DecodeParms".equals(value)
                || "F".equals(value)
                || "FFilter".equals(value)
                || "FDecodeParms".equals(value)
                || "DL".equals(value);
    }

    private static DocumentFailure illegalStreamChange() {
        return failure(
                DocumentFailureCode.PATCH_STREAM_CHANGE_REJECTED,
                "The Document Patch cannot change engine-owned stream metadata.");
    }

    private void rejectReferenceCycles(List<PreparedChange> prepared)
            throws DocumentFailure {
        IdentityHashMap<COSDictionary, Map<COSName, PreparedChange>>
                finalChanges =
                new IdentityHashMap<COSDictionary,
                        Map<COSName, PreparedChange>>();
        for (PreparedChange change : prepared) {
            resources.checkpoint();
            Map<COSName, PreparedChange> dictionaryChanges =
                    finalChanges.get(change.target);
            if (dictionaryChanges == null) {
                dictionaryChanges = new HashMap<COSName, PreparedChange>();
                finalChanges.put(change.target, dictionaryChanges);
            }
            dictionaryChanges.put(change.name, change);
        }

        IdentityHashMap<COSDictionary, Map<COSName, COSBase>> finalValues =
                new IdentityHashMap<COSDictionary, Map<COSName, COSBase>>();
        for (Map.Entry<COSDictionary, Map<COSName, PreparedChange>> entry
                : finalChanges.entrySet()) {
            resources.checkpoint();
            Map<COSName, COSBase> values = new HashMap<COSName, COSBase>();
            for (PreparedChange change : entry.getValue().values()) {
                resources.checkpoint();
                values.put(change.name, change.value);
            }
            finalValues.put(entry.getKey(), values);
        }

        for (Map<COSName, PreparedChange> dictionaryChanges
                : finalChanges.values()) {
            resources.checkpoint();
            for (PreparedChange change : dictionaryChanges.values()) {
                resources.checkpoint();
                for (ObjectReference referencedObject
                        : change.referencedObjects) {
                    resources.checkpoint();
                    if (reaches(
                            referencedObject,
                            change.targetReference,
                            finalValues,
                            new IdentityHashMap<COSBase, Boolean>())) {
                        throw failure(
                                DocumentFailureCode.PATCH_CYCLE_REJECTED,
                                "The Document Patch would introduce a reference cycle.");
                    }
                }
            }
        }
    }

    private boolean reaches(
            ObjectReference start,
            ObjectReference goal,
            IdentityHashMap<COSDictionary, Map<COSName, COSBase>> finalValues,
            IdentityHashMap<COSBase, Boolean> visited)
            throws DocumentFailure {
        if (start.equals(goal)) {
            return true;
        }
        ReferenceTarget startTarget = targets.get(start);
        ReferenceTarget goalTarget = targets.get(goal);
        if (startTarget == null || goalTarget == null) {
            return false;
        }

        Deque<BackendValueNode> pending =
                new ArrayDeque<BackendValueNode>();
        pending.push(new BackendValueNode(startTarget.rawValue, 1));
        while (!pending.isEmpty()) {
            resources.checkpoint();
            BackendValueNode current = pending.pop();
            COSBase candidate = current.value;
            if (candidate == null) {
                continue;
            }
            if (candidate == goalTarget.rawValue
                    || candidate == goalTarget.value) {
                return true;
            }
            if (visited.put(candidate, Boolean.TRUE) != null) {
                continue;
            }
            int childDepth = current.depth + 1;
            if (candidate instanceof COSObject) {
                resources.requireNestingDepth(current.depth);
                ObjectReference reference = referenceFor(candidate);
                if (reference.equals(goal)) {
                    return true;
                }
                pending.push(new BackendValueNode(
                        ((COSObject) candidate).getObject(),
                        childDepth));
            } else if (candidate instanceof COSArray) {
                resources.requireNestingDepth(current.depth);
                COSArray array = (COSArray) candidate;
                for (int index = array.size() - 1; index >= 0; index--) {
                    resources.checkpoint();
                    pending.push(new BackendValueNode(
                            array.get(index),
                            childDepth));
                }
            } else if (candidate instanceof COSDictionary) {
                resources.requireNestingDepth(current.depth);
                COSDictionary dictionary = (COSDictionary) candidate;
                Map<COSName, COSBase> replacements =
                        finalValues.get(dictionary);
                for (Map.Entry<COSName, COSBase> entry
                        : dictionary.entrySet()) {
                    resources.checkpoint();
                    COSBase finalValue = replacements != null
                            && replacements.containsKey(entry.getKey())
                            ? replacements.get(entry.getKey())
                            : entry.getValue();
                    pending.push(new BackendValueNode(
                            finalValue,
                            childDepth));
                }
                if (replacements != null) {
                    for (Map.Entry<COSName, COSBase> replacement
                            : replacements.entrySet()) {
                        resources.checkpoint();
                        if (!dictionary.containsKey(replacement.getKey())) {
                            pending.push(new BackendValueNode(
                                    replacement.getValue(),
                                    childDepth));
                        }
                    }
                }
            }
        }
        return false;
    }

    private ObjectReference referenceFor(COSBase rawValue)
            throws DocumentFailure {
        if (rawValue == null) {
            throw failure(
                    DocumentFailureCode.QUERY_FAILED,
                    "The requested PDF object is unavailable.");
        }
        COSBase value = rawValue instanceof COSObject
                ? ((COSObject) rawValue).getObject()
                : rawValue;
        if (value == null) {
            value = COSNull.NULL;
        }
        boolean pageDictionary = isPageDictionary(value);
        ObjectReference existing = references.get(rawValue);
        if (existing == null && pageDictionary) {
            existing = references.get(value);
        }
        if (existing != null) {
            references.put(rawValue, existing);
            if (pageDictionary) {
                references.put(value, existing);
            }
            return existing;
        }
        ObjectReference created = new ObjectReference(
                sessionIdentity,
                nextReferenceIdentity++);
        references.put(rawValue, created);
        if (pageDictionary) {
            references.put(value, created);
        }
        targets.put(created, new ReferenceTarget(rawValue, value));
        return created;
    }

    private static boolean isPageDictionary(COSBase value) {
        return value instanceof COSDictionary
                && COSName.PAGE.equals(dereference(
                        ((COSDictionary) value).getItem(COSName.TYPE)));
    }

    private static COSBase dereference(COSBase value) {
        return value instanceof COSObject
                ? ((COSObject) value).getObject()
                : value;
    }

    private PdfValue publicValue(
            COSBase value,
            InspectionBudget budget) throws DocumentFailure {
        return publicValue(value, budget, null);
    }

    private PdfValue publicValue(
            COSBase value,
            InspectionBudget budget,
            ObjectReference owningReference) throws DocumentFailure {
        if (value instanceof COSObject) {
            return PdfIndirectReference.of(referenceFor(value));
        }
        if (value == null || value instanceof COSNull) {
            return PdfNull.INSTANCE;
        }
        if (value instanceof COSBoolean) {
            return PdfBoolean.of(((COSBoolean) value).getValue());
        }
        if (value instanceof COSInteger) {
            return PdfNumber.of(((COSInteger) value).longValue());
        }
        if (value instanceof COSNumber) {
            try {
                return PdfNumber.of(serializedNumber(
                        (COSFloat) value, resources));
            } catch (DocumentFailure failure) {
                throw failure;
            } catch (IOException | NumberFormatException invalidNumber) {
                resources.rethrowResourceOrTerminalFailure(invalidNumber);
                throw failure(
                        DocumentFailureCode.QUERY_FAILED,
                        "The PDF number could not be inspected.");
            }
        }
        if (value instanceof COSString) {
            return PdfBoxStringSupport.detached(
                    (COSString) value,
                    resources,
                    () -> failure(
                            DocumentFailureCode.QUERY_FAILED,
                            "The PDF string could not be inspected."));
        }
        if (value instanceof COSName) {
            return PdfName.of(((COSName) value).getName());
        }
        if (value instanceof COSArray) {
            return new PdfArray(new ArrayView((COSArray) value, budget));
        }
        if (value instanceof COSStream) {
            ObjectReference reference = owningReference == null
                    ? referenceFor(value)
                    : owningReference;
            return new PdfStream(new StreamView(
                    (COSStream) value,
                    budget,
                    reference));
        }
        if (value instanceof COSDictionary) {
            return new PdfDictionary(new DictionaryView(
                    (COSDictionary) value,
                    budget));
        }
        throw failure(
                DocumentFailureCode.QUERY_FAILED,
                "The PDF Value kind is not supported by this workflow version.");
    }

    private COSBase backendValue(
            PdfValue value,
            List<ObjectReference> referencedObjects,
            WorkflowResourceContext.OwnedMemoryScope ownership)
            throws DocumentFailure {
        if (value == PdfNull.INSTANCE) {
            return COSNull.NULL;
        }
        if (value instanceof PdfBoolean) {
            return COSBoolean.getBoolean(((PdfBoolean) value).booleanValue());
        }
        if (value instanceof PdfNumber) {
            return backendNumber((PdfNumber) value, ownership);
        }
        if (value instanceof PdfString) {
            return PdfBoxStringSupport.backendCopy(
                    (PdfString) value,
                    resources,
                    ownership,
                    PdfBoxValueAdapter::invalidPatchValue);
        }
        if (value instanceof PdfName) {
            return COSName.getPDFName(((PdfName) value).getValue());
        }
        if (value instanceof PdfArray) {
            PdfArray array = (PdfArray) value;
            COSArray converted = new COSArray();
            for (int index = 0; index < array.size(); index++) {
                resources.checkpoint();
                converted.add(backendValue(
                        array.get(index),
                        referencedObjects,
                        ownership));
            }
            return converted;
        }
        if (value instanceof PdfDictionary) {
            return backendDictionary(
                    (PdfDictionary) value,
                    referencedObjects,
                    ownership,
                    false);
        }
        if (value instanceof PdfIndirectReference) {
            ObjectReference reference =
                    ((PdfIndirectReference) value).getReference();
            requireOwned(reference);
            ReferenceTarget target = targets.get(reference);
            if (target == null) {
                throw failure(
                        DocumentFailureCode.COMMAND_REJECTED,
                        "The Document Patch contains an unavailable Object Reference.");
            }
            referencedObjects.add(reference);
            return target.rawValue;
        }
        if (value instanceof PdfStream) {
            PdfStream publicStream = (PdfStream) value;
            COSDictionary attributes = backendDictionary(
                    publicStream.getDictionary(),
                    referencedObjects,
                    ownership,
                    true);
            COSStream converted = document.getDocument().createCOSStream();
            for (COSName name : attributes.keySet()) {
                resources.checkpoint();
                converted.setItem(name, attributes.getItem(name));
            }
            try (OutputStream output = converted.createOutputStream();
                    WorkflowResourceContext.OwnedBytes decoded =
                            publicStream.readBytesForWorkflow(resources)) {
                resources.writeBytesAsIOException(
                        output, decoded.getBytes());
            } catch (IOException streamFailure) {
                resources.rethrowResourceOrTerminalFailure(streamFailure);
                throw failure(
                        DocumentFailureCode.COMMAND_REJECTED,
                        "The Document Patch stream could not be created.");
            }
            return converted;
        }
        throw failure(
                DocumentFailureCode.PATCH_VALUE_REJECTED,
                "The Document Patch contains a value not owned by Folio PDF.");
    }

    private COSBase backendNumber(
            PdfNumber publicNumber,
            WorkflowResourceContext.OwnedMemoryScope ownership)
            throws DocumentFailure {
        return backendNumber(
                publicNumber.decimalValue(),
                resources,
                ownership,
                PdfBoxValueAdapter::invalidPatchNumber);
    }

    private COSDictionary backendDictionary(
            PdfDictionary dictionary,
            List<ObjectReference> referencedObjects,
            WorkflowResourceContext.OwnedMemoryScope ownership,
            boolean streamAttributes)
            throws DocumentFailure {
        COSDictionary converted = new COSDictionary();
        converted.setDirect(true);
        for (int index = 0; index < dictionary.size(); index++) {
            resources.checkpoint();
            PdfDictionaryEntry entry = dictionary.getEntry(index);
            if (streamAttributes
                    && isEngineOwnedStreamName(entry.getName())) {
                throw illegalStreamChange();
            }
            converted.setItem(
                    COSName.getPDFName(entry.getName().getValue()),
                    backendValue(
                            entry.getValue(),
                            referencedObjects,
                            ownership));
        }
        return converted;
    }

    private void requirePatchNesting(PdfValue root)
            throws DocumentFailure {
        Deque<PublicValueNode> pending = new ArrayDeque<PublicValueNode>();
        IdentityHashMap<PdfValue, Integer> maximumExpandedDepth =
                new IdentityHashMap<PdfValue, Integer>();
        pending.push(new PublicValueNode(root, 1));
        while (!pending.isEmpty()) {
            resources.checkpoint();
            PublicValueNode node = pending.pop();
            PdfValue value = node.value;
            Integer previousDepth = maximumExpandedDepth.get(value);
            if (previousDepth != null
                    && previousDepth.intValue() >= node.depth) {
                continue;
            }
            maximumExpandedDepth.put(value, Integer.valueOf(node.depth));
            if (value instanceof PdfArray) {
                requireMaterializationDepth(node.depth);
                resources.requireNestingDepth(node.depth);
                PdfArray array = (PdfArray) value;
                int childDepth = node.depth + 1;
                for (int index = array.size() - 1; index >= 0; index--) {
                    resources.checkpoint();
                    pending.push(new PublicValueNode(
                            array.get(index),
                            childDepth));
                }
            } else if (value instanceof PdfDictionary) {
                requireMaterializationDepth(node.depth);
                resources.requireNestingDepth(node.depth);
                PdfDictionary dictionary = (PdfDictionary) value;
                int childDepth = node.depth + 1;
                for (int index = dictionary.size() - 1;
                        index >= 0;
                        index--) {
                    resources.checkpoint();
                    pending.push(new PublicValueNode(
                            dictionary.getEntry(index).getValue(),
                            childDepth));
                }
            } else if (value instanceof PdfStream) {
                requireMaterializationDepth(node.depth);
                resources.requireNestingDepth(node.depth);
                pending.push(new PublicValueNode(
                        ((PdfStream) value).getDictionary(),
                        node.depth + 1));
            }
        }
    }

    private void requireMaterializationDepth(int depth)
            throws DocumentFailure {
        if (depth > MAXIMUM_RECURSIVE_MATERIALIZATION_DEPTH) {
            throw resources.policyFailure(
                    DocumentFailureCode.NESTING_LIMIT_EXCEEDED,
                    "The workflow nesting-depth limit was exceeded.");
        }
    }

    static BigDecimal serializedNumber(
            COSFloat number,
            WorkflowResourceContext resources)
            throws IOException, DocumentFailure {
        resources.checkpoint();
        try (WorkflowResourceContext.OwnedByteAccumulator output =
                resources.ownedByteAccumulator()) {
            number.writePDF(output);
            resources.checkpoint();
            try (WorkflowResourceContext.OwnedBytes working =
                    output.finishWorking()) {
                byte[] bytes = working.getBytes();
                try (WorkflowResourceContext.MemoryReservation characters =
                        resources.reserveOwnedMemory(2L * bytes.length)) {
                    char[] lexical = new char[bytes.length];
                    for (int index = 0; index < bytes.length; index++) {
                        if ((index & 1023) == 0) {
                            resources.checkpoint();
                        }
                        int value = bytes[index] & 0xff;
                        if (value > 0x7f) {
                            throw new NumberFormatException(
                                    "A PDF number must be ASCII.");
                        }
                        lexical[index] = (char) value;
                    }
                    return new BigDecimal(lexical);
                }
            }
        } catch (IOException | RuntimeException failure) {
            resources.rethrowResourceOrTerminalFailure(failure);
            throw failure;
        }
    }

    static COSBase backendNumber(
            BigDecimal number,
            WorkflowResourceContext resources,
            WorkflowResourceContext.OwnedMemoryScope ownership,
            PdfBoxPageContentSupport.FailureFactory failureFactory)
            throws DocumentFailure {
        resources.checkpoint();
        if (number.scale() <= 0) {
            try {
                return COSInteger.get(number.longValueExact());
            } catch (ArithmeticException outsideIntegerRange) {
                // Values outside the backend integer range use a lexical real.
            }
        }

        float parsed = number.floatValue();
        resources.checkpoint();
        if (Float.isInfinite(parsed)
                || Float.isNaN(parsed)
                || (parsed != 0.0f && Math.abs(parsed) < Float.MIN_NORMAL)) {
            throw failureFactory.create();
        }

        long characters = plainStringLength(number);
        long retainedBytes = 2L * characters;
        long workingBytes = retainedBytes;
        long totalBytes = retainedBytes + workingBytes;
        ownership.retain(totalBytes);
        boolean keepRetained = false;
        try {
            if (characters > Integer.MAX_VALUE - 8L) {
                throw failureFactory.create();
            }
            String lexical = number.toPlainString();
            resources.checkpoint();
            if (lexical.length() != (int) characters) {
                throw failureFactory.create();
            }
            COSFloat converted = new COSFloat(lexical);
            resources.checkpoint();
            keepRetained = true;
            return converted;
        } catch (DocumentFailure failure) {
            throw failure;
        } catch (IOException | RuntimeException invalidNumber) {
            resources.rethrowResourceOrTerminalFailure(invalidNumber);
            throw failureFactory.create();
        } finally {
            ownership.release(keepRetained ? workingBytes : totalBytes);
        }
    }

    static long plainStringLength(BigDecimal number) {
        long precision = number.precision();
        long scale = number.scale();
        long length;
        if (scale <= 0L) {
            length = precision - scale;
        } else if (scale < precision) {
            length = precision + 1L;
        } else {
            length = scale + 2L;
        }
        return number.signum() < 0 ? length + 1L : length;
    }

    private static DocumentFailure invalidPatchNumber() {
        return failure(
                DocumentFailureCode.COMMAND_REJECTED,
                "The Document Patch contains an invalid PDF number.");
    }

    private static DocumentFailure invalidPatchValue() {
        return failure(
                DocumentFailureCode.PATCH_VALUE_REJECTED,
                "The Document Patch contains an invalid PDF string.");
    }

    private void requireOwned(ObjectReference reference)
            throws DocumentFailure {
        if (reference.getSessionIdentity() != sessionIdentity) {
            throw failure(
                    DocumentFailureCode.OBJECT_REFERENCE_OWNERSHIP_INVALID,
                    "The Object Reference does not belong to this Session.");
        }
    }

    static DocumentFailure failure(
            DocumentFailureCode code,
            String diagnostic) {
        return new DocumentFailure(code, CAPABILITY_ID, diagnostic);
    }

    private final class DictionaryView implements PdfDictionaryAccess {

        private final COSDictionary dictionary;
        private final InspectionBudget budget;

        DictionaryView(
                COSDictionary dictionary,
                InspectionBudget budget) {
            this.dictionary = dictionary;
            this.budget = budget;
        }

        @Override
        public int size() throws DocumentFailure {
            session.requireActiveValueView();
            return dictionary.size();
        }

        @Override
        public PdfValue get(PdfName name) throws DocumentFailure {
            session.requireActiveValueView();
            budget.consumeValue();
            COSBase value = dictionary.getItem(
                    COSName.getPDFName(name.getValue()));
            return value == null ? null : publicValue(value, budget);
        }

        @Override
        public PdfDictionaryEntry getEntry(int index)
                throws DocumentFailure {
            session.requireActiveValueView();
            budget.consumeValue();
            int current = 0;
            for (Map.Entry<COSName, COSBase> entry : dictionary.entrySet()) {
                resources.checkpoint();
                if (current == index) {
                    return new PdfDictionaryEntry(
                            PdfName.of(entry.getKey().getName()),
                            publicValue(entry.getValue(), budget));
                }
                current++;
            }
            throw new IndexOutOfBoundsException("index: " + index);
        }
    }

    private final class ArrayView implements PdfArrayAccess {

        private final COSArray array;
        private final InspectionBudget budget;

        ArrayView(COSArray array, InspectionBudget budget) {
            this.array = array;
            this.budget = budget;
        }

        @Override
        public int size() throws DocumentFailure {
            session.requireActiveValueView();
            return array.size();
        }

        @Override
        public PdfValue get(int index) throws DocumentFailure {
            session.requireActiveValueView();
            budget.consumeValue();
            return publicValue(array.get(index), budget);
        }
    }

    private final class StreamView implements PdfStreamAccess {

        private final COSStream stream;
        private final InspectionBudget budget;
        private final ObjectReference reference;

        StreamView(
                COSStream stream,
                InspectionBudget budget,
                ObjectReference reference) {
            this.stream = stream;
            this.budget = budget;
            this.reference = reference;
        }

        @Override
        public PdfDictionary getDictionary() throws DocumentFailure {
            session.requireActiveValueView();
            return new PdfDictionary(new DictionaryView(stream, budget));
        }

        @Override
        public byte[] readBytes() throws DocumentFailure {
            session.requireActiveValueView();
            try (WorkflowResourceContext.OwnedByteAccumulator bytes =
                    resources.ownedByteAccumulator()) {
                PdfBoxHostileInputPreflight.decodeStream(
                        stream,
                        resources,
                        new StreamResultOutput(bytes, budget));
                return bytes.finishRetained();
            } catch (StreamResultIOException failure) {
                throw failure.failure;
            } catch (DocumentFailure failure) {
                throw failure;
            } catch (IOException | RuntimeException streamFailure) {
                resources.rethrowResourceOrTerminalFailure(streamFailure);
                throw failure(
                        DocumentFailureCode.QUERY_FAILED,
                        "The PDF stream could not be decoded.");
            }
        }

        @Override
        public WorkflowResourceContext.OwnedBytes readBytesForWorkflow(
                WorkflowResourceContext workflowResources)
                throws DocumentFailure {
            session.requireActiveValueView();
            try (WorkflowResourceContext.OwnedByteAccumulator bytes =
                    workflowResources.ownedByteAccumulator()) {
                PdfBoxHostileInputPreflight.decodeStream(
                        stream,
                        workflowResources,
                        new StreamResultOutput(bytes, budget));
                return bytes.finishWorking();
            } catch (StreamResultIOException failure) {
                throw failure.failure;
            } catch (DocumentFailure failure) {
                throw failure;
            } catch (IOException | RuntimeException streamFailure) {
                workflowResources.rethrowResourceOrTerminalFailure(
                        streamFailure);
                throw failure(
                        DocumentFailureCode.COMMAND_REJECTED,
                        "The Document Patch stream could not be decoded.");
            }
        }

        @Override
        public Optional<ObjectReference> getReference() {
            return Optional.of(reference);
        }
    }

    private static final class StreamResultOutput extends OutputStream {

        private final WorkflowResourceContext.OwnedByteAccumulator output;
        private final InspectionBudget budget;

        private StreamResultOutput(
                WorkflowResourceContext.OwnedByteAccumulator output,
                InspectionBudget budget) {
            this.output = output;
            this.budget = budget;
        }

        @Override
        public void write(int value) throws IOException {
            account(1);
            output.write(value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length)
                throws IOException {
            account(length);
            output.write(bytes, offset, length);
        }

        private void account(int length) throws IOException {
            try {
                budget.consumeStreamBytes(length);
            } catch (DocumentFailure failure) {
                throw new StreamResultIOException(failure);
            }
        }
    }

    private static final class StreamResultIOException extends IOException {

        private static final long serialVersionUID = 1L;
        private final DocumentFailure failure;

        private StreamResultIOException(DocumentFailure failure) {
            super(failure.getDiagnostic());
            this.failure = failure;
        }
    }

    private static final class InspectionBudget {

        private long remainingValues;
        private long remainingDecodedStreamBytes;

        InspectionBudget(PdfInspectionLimits limits) {
            this.remainingValues = limits.getMaximumTraversedValues();
            this.remainingDecodedStreamBytes =
                    limits.getMaximumDecodedStreamBytes();
        }

        void consumeValue() throws DocumentFailure {
            if (remainingValues == 0L) {
                throw failure(
                        DocumentFailureCode.PDF_VALUE_LIMIT_EXCEEDED,
                        "The PDF Value inspection limit was exceeded.");
            }
            remainingValues--;
        }

        void consumeStreamBytes(int byteCount) throws DocumentFailure {
            if (byteCount > remainingDecodedStreamBytes) {
                remainingDecodedStreamBytes = 0L;
                throw failure(
                        DocumentFailureCode.PDF_VALUE_LIMIT_EXCEEDED,
                        "The PDF Value inspection limit was exceeded.");
            }
            remainingDecodedStreamBytes -= byteCount;
        }
    }

    private static final class PublicValueNode {

        private final PdfValue value;
        private final int depth;

        private PublicValueNode(PdfValue value, int depth) {
            this.value = value;
            this.depth = depth;
        }
    }

    private static final class BackendValueNode {

        private final COSBase value;
        private final int depth;

        private BackendValueNode(COSBase value, int depth) {
            this.value = value;
            this.depth = depth;
        }
    }

    private static final class PreparedChange {

        private final ObjectReference targetReference;
        private final COSDictionary target;
        private final COSName name;
        private final COSBase value;
        private final List<ObjectReference> referencedObjects;
        private final boolean originallyPresent;
        private final COSBase originalValue;

        PreparedChange(
                ObjectReference targetReference,
                COSDictionary target,
                COSName name,
                COSBase value,
                List<ObjectReference> referencedObjects,
                boolean originallyPresent,
                COSBase originalValue) {
            this.targetReference = targetReference;
            this.target = target;
            this.name = name;
            this.value = value;
            this.referencedObjects = referencedObjects;
            this.originallyPresent = originallyPresent;
            this.originalValue = originalValue;
        }
    }

    private static final class PreparedPatch implements AutoCloseable {

        private final List<PreparedChange> changes;
        private final WorkflowResourceContext.OwnedMemoryScope ownership;

        private PreparedPatch(
                List<PreparedChange> changes,
                WorkflowResourceContext.OwnedMemoryScope ownership) {
            this.changes = changes;
            this.ownership = ownership;
        }

        private void transfer() throws DocumentFailure {
            ownership.transfer();
        }

        @Override
        public void close() {
            ownership.close();
        }
    }

    private static final class ReferenceTarget {

        private final COSBase rawValue;
        private final COSBase value;

        ReferenceTarget(COSBase rawValue, COSBase value) {
            this.rawValue = rawValue;
            this.value = value;
        }
    }
}
