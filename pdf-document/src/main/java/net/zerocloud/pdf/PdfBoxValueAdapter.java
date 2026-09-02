package net.zerocloud.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSArray;
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

    static final String CAPABILITY_ID = "document.value.inspect-patch";

    private final PDDocument document;
    private final PdfBoxDocumentSession session;
    private final Object sessionIdentity = new Object();
    private final IdentityHashMap<COSBase, ObjectReference> references =
            new IdentityHashMap<COSBase, ObjectReference>();
    private final Map<ObjectReference, ReferenceTarget> targets =
            new HashMap<ObjectReference, ReferenceTarget>();
    private long nextReferenceIdentity = 1L;

    PdfBoxValueAdapter(PDDocument document, PdfBoxDocumentSession session) {
        this.document = document;
        this.session = session;
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
        List<PreparedChange> prepared = prepare(patch);
        rejectReferenceCycles(prepared);
        int applied = 0;
        try {
            for (PreparedChange change : prepared) {
                change.target.setItem(change.name, change.value);
                applied++;
            }
        } catch (RuntimeException applicationFailure) {
            rollback(prepared, applied);
            throw applicationFailure;
        }
    }

    private List<PreparedChange> prepare(DocumentPatch patch)
            throws DocumentFailure {
        List<PreparedChange> prepared = new ArrayList<PreparedChange>(
                patch.getChanges().size());
        for (DocumentPatch.DictionaryEntryChange change : patch.getChanges()) {
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
            List<ObjectReference> referencedObjects =
                    new ArrayList<ObjectReference>();
            COSBase value = backendValue(
                    change.getValue(),
                    referencedObjects);
            prepared.add(new PreparedChange(
                    change.getTarget(),
                    dictionary,
                    name,
                    value,
                    referencedObjects,
                    dictionary.containsKey(name),
                    dictionary.getItem(name)));
        }
        return prepared;
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
            Map<COSName, COSBase> values = new HashMap<COSName, COSBase>();
            for (PreparedChange change : entry.getValue().values()) {
                values.put(change.name, change.value);
            }
            finalValues.put(entry.getKey(), values);
        }

        for (Map<COSName, PreparedChange> dictionaryChanges
                : finalChanges.values()) {
            for (PreparedChange change : dictionaryChanges.values()) {
                for (ObjectReference referencedObject
                        : change.referencedObjects) {
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
        return startTarget != null
                && goalTarget != null
                && reaches(
                        startTarget.rawValue,
                        goal,
                        goalTarget,
                        finalValues,
                        visited);
    }

    private boolean reaches(
            COSBase value,
            ObjectReference goal,
            ReferenceTarget goalTarget,
            IdentityHashMap<COSDictionary, Map<COSName, COSBase>> finalValues,
            IdentityHashMap<COSBase, Boolean> visited)
            throws DocumentFailure {
        if (value == null) {
            return false;
        }
        if (value == goalTarget.rawValue || value == goalTarget.value) {
            return true;
        }
        if (visited.put(value, Boolean.TRUE) != null) {
            return false;
        }
        if (value instanceof COSObject) {
            ObjectReference reference = referenceFor(value);
            return reference.equals(goal)
                    || reaches(
                            ((COSObject) value).getObject(),
                            goal,
                            goalTarget,
                            finalValues,
                            visited);
        }
        if (value instanceof COSArray) {
            COSArray array = (COSArray) value;
            for (int index = 0; index < array.size(); index++) {
                if (reaches(
                        array.get(index),
                        goal,
                        goalTarget,
                        finalValues,
                        visited)) {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof COSDictionary) {
            COSDictionary dictionary = (COSDictionary) value;
            Map<COSName, COSBase> replacements = finalValues.get(dictionary);
            for (Map.Entry<COSName, COSBase> entry : dictionary.entrySet()) {
                COSBase finalValue = replacements != null
                        && replacements.containsKey(entry.getKey())
                        ? replacements.get(entry.getKey())
                        : entry.getValue();
                if (reaches(
                        finalValue,
                        goal,
                        goalTarget,
                        finalValues,
                        visited)) {
                    return true;
                }
            }
            if (replacements != null) {
                for (Map.Entry<COSName, COSBase> replacement
                        : replacements.entrySet()) {
                    if (!dictionary.containsKey(replacement.getKey())
                            && reaches(
                                    replacement.getValue(),
                                    goal,
                                    goalTarget,
                                    finalValues,
                                    visited)) {
                        return true;
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
                return PdfNumber.of(serializedNumber((COSFloat) value));
            } catch (IOException | NumberFormatException invalidNumber) {
                throw failure(
                        DocumentFailureCode.QUERY_FAILED,
                        "The PDF number could not be inspected.");
            }
        }
        if (value instanceof COSString) {
            return PdfString.of(((COSString) value).getBytes());
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
            List<ObjectReference> referencedObjects)
            throws DocumentFailure {
        if (value == PdfNull.INSTANCE) {
            return COSNull.NULL;
        }
        if (value instanceof PdfBoolean) {
            return COSBoolean.getBoolean(((PdfBoolean) value).booleanValue());
        }
        if (value instanceof PdfNumber) {
            return backendNumber((PdfNumber) value);
        }
        if (value instanceof PdfString) {
            return new COSString(((PdfString) value).getBytes());
        }
        if (value instanceof PdfName) {
            return COSName.getPDFName(((PdfName) value).getValue());
        }
        if (value instanceof PdfArray) {
            PdfArray array = (PdfArray) value;
            COSArray converted = new COSArray();
            for (int index = 0; index < array.size(); index++) {
                converted.add(backendValue(
                        array.get(index),
                        referencedObjects));
            }
            return converted;
        }
        if (value instanceof PdfDictionary) {
            return backendDictionary(
                    (PdfDictionary) value,
                    referencedObjects,
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
                    true);
            COSStream converted = document.getDocument().createCOSStream();
            converted.addAll(attributes);
            try (OutputStream output = converted.createOutputStream()) {
                output.write(publicStream.readBytes());
            } catch (IOException streamFailure) {
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

    private COSBase backendNumber(PdfNumber publicNumber)
            throws DocumentFailure {
        BigDecimal number = publicNumber.decimalValue();
        if (number.scale() <= 0) {
            try {
                return COSInteger.get(number.longValueExact());
            } catch (ArithmeticException outsideIntegerRange) {
                // A valid PDF number outside the backend integer range is
                // represented as a lexical real when it remains exact.
            }
        }
        try {
            COSFloat converted = new COSFloat(number.toPlainString());
            if (serializedNumber(converted).compareTo(number) != 0) {
                throw invalidPatchNumber();
            }
            return converted;
        } catch (IOException | NumberFormatException invalidNumber) {
            throw invalidPatchNumber();
        }
    }

    private COSDictionary backendDictionary(
            PdfDictionary dictionary,
            List<ObjectReference> referencedObjects,
            boolean streamAttributes)
            throws DocumentFailure {
        COSDictionary converted = new COSDictionary();
        converted.setDirect(true);
        for (int index = 0; index < dictionary.size(); index++) {
            PdfDictionaryEntry entry = dictionary.getEntry(index);
            if (streamAttributes
                    && isEngineOwnedStreamName(entry.getName())) {
                throw illegalStreamChange();
            }
            converted.setItem(
                    COSName.getPDFName(entry.getName().getValue()),
                    backendValue(entry.getValue(), referencedObjects));
        }
        return converted;
    }

    static BigDecimal serializedNumber(COSFloat number)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        number.writePDF(bytes);
        return new BigDecimal(new String(
                bytes.toByteArray(),
                StandardCharsets.ISO_8859_1));
    }

    private static DocumentFailure invalidPatchNumber() {
        return failure(
                DocumentFailureCode.COMMAND_REJECTED,
                "The Document Patch contains an invalid PDF number.");
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
            try (InputStream input = stream.createInputStream();
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    budget.consumeStreamBytes(read);
                    bytes.write(buffer, 0, read);
                }
                return bytes.toByteArray();
            } catch (IOException | RuntimeException streamFailure) {
                throw failure(
                        DocumentFailureCode.QUERY_FAILED,
                        "The PDF stream could not be decoded.");
            }
        }

        @Override
        public Optional<ObjectReference> getReference() {
            return Optional.of(reference);
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

    private static final class ReferenceTarget {

        private final COSBase rawValue;
        private final COSBase value;

        ReferenceTarget(COSBase rawValue, COSBase value) {
            this.rawValue = rawValue;
            this.value = value;
        }
    }
}
