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
import java.util.LinkedHashSet;
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
import org.apache.pdfbox.cos.COSObjectKey;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;

/** Keeps the low-level public model detached from PDFBox identity and types. */
final class PdfBoxValueAdapter {

    private static final int MAXIMUM_RECURSIVE_MATERIALIZATION_DEPTH = 256;
    private static final String[] ENGINE_OWNED_STREAM_NAMES = {
        "Length", "Filter", "DecodeParms", "F", "FFilter", "FDecodeParms", "DL"
    };

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
    private final Set<COSBase> changedContainers = Collections.newSetFromMap(
            new IdentityHashMap<COSBase, Boolean>());

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
            rejectReferenceCycles(preparedPatch);
            int applied = 0;
            try {
                for (PreparedChange change : prepared) {
                    resources.checkpoint();
                    change.apply();
                    applied++;
                }
                invalidateChangedStreams(preparedPatch);
                validateDocumentStructure();
                IdentityHashMap<COSBase, Boolean> retained = collectDocumentValues();
                preparedPatch.transfer(retained);
                for (COSDictionary dictionary : preparedPatch.dictionaries.keySet()) {
                    if (retained.containsKey(dictionary)) {
                        changedContainers.add(dictionary);
                    }
                }
                for (COSArray array : preparedPatch.arrays.keySet()) {
                    if (retained.containsKey(array)) {
                        changedContainers.add(array);
                    }
                }
                for (Map.Entry<ObjectReference, ReferenceTarget> replacement : preparedPatch.replacementTargets.entrySet()) {
                    references.put(replacement.getValue().rawValue, replacement.getKey());
                }
            } catch (DocumentFailure failure) {
                rollback(prepared, applied);
                invalidateChangedStreams(preparedPatch);
                resources.rethrowTerminalFailure();
                throw failure;
            } catch (RuntimeException applicationFailure) {
                rollback(prepared, applied);
                invalidateChangedStreams(preparedPatch);
                resources.rethrowResourceOrTerminalFailure(applicationFailure);
                throw applicationFailure;
            }
        }
    }

    PdfBoxIncrementalTrailer prepareIncrementalSave() throws DocumentFailure {
        if (!changedContainers.isEmpty()) {
            return PdfBoxIncrementalTrailer.prepare(document.getDocument(), changedContainers, resources);
        }
        return null;
    }

    private void invalidateChangedStreams(PreparedPatch patch) {
        for (COSDictionary target : patch.dictionaries.keySet()) {
            if (target instanceof COSStream) {
                resources.invalidateStreamPreflight((COSStream) target);
            }
        }
    }

    private void validateDocumentStructure() throws DocumentFailure {
        COSBase root = document.getDocument().getTrailer().getDictionaryObject(COSName.ROOT);
        if (!(root instanceof COSDictionary) || root instanceof COSStream
                || !COSName.CATALOG.equals(((COSDictionary) root).getCOSName(COSName.TYPE))) {
            throw invalidDocumentStructure();
        }
        COSBase pages = ((COSDictionary) root).getDictionaryObject(COSName.PAGES);
        if (!(pages instanceof COSDictionary) || pages instanceof COSStream) {
            throw invalidDocumentStructure();
        }
        try {
            for (PdfBoxPageTreePreflight.PageView page : PdfBoxPageTreePreflight.pages((COSDictionary) pages,
                    resources.getPolicy().getMaximumPages(), Integer.MAX_VALUE, resources)) {
                validatePageStructure(page);
            }
            resources.audit(document);
        } catch (PdfBoxPageTreePreflight.LimitExceededException exhausted) {
            throw resources.policyFailure(DocumentFailureCode.PAGE_LIMIT_EXCEEDED,
                    "The workflow page-count limit was exceeded.");
        } catch (IOException malformed) {
            throw invalidDocumentStructure();
        } catch (DocumentFailure failure) {
            if (failure.getCode() == DocumentFailureCode.SOURCE_READ_FAILED) {
                throw invalidDocumentStructure();
            }
            throw failure;
        }
    }

    private void validatePageStructure(PdfBoxPageTreePreflight.PageView page) throws DocumentFailure {
        resources.checkpoint();
        if (page.effective().getDictionaryObject(COSName.RESOURCES) == null) {
            throw invalidDocumentStructure();
        }
        COSBase contents = page.source().getDictionaryObject(COSName.CONTENTS);
        if (contents instanceof COSArray) {
            COSArray streams = (COSArray) contents;
            for (int index = 0; index < streams.size(); index++) {
                resources.checkpoint();
                if (!(dereference(streams.get(index)) instanceof COSStream)) {
                    throw invalidDocumentStructure();
                }
            }
        } else if (contents != null && !(contents instanceof COSNull) && !(contents instanceof COSStream)) {
            throw invalidDocumentStructure();
        }
    }

    private static DocumentFailure invalidDocumentStructure() {
        return failure(DocumentFailureCode.COMMAND_REJECTED,
                "The Document Patch would invalidate the document structure.");
    }

    private PreparedPatch prepare(DocumentPatch patch)
            throws DocumentFailure {
        WorkflowResourceContext.OwnedMemoryScope ownership =
                resources.ownedMemoryScope();
        List<PreparedChange> prepared = new ArrayList<PreparedChange>(
                patch.getChanges().size());
        IdentityHashMap<COSDictionary, Map<COSName, COSBase>> pendingValues =
                new IdentityHashMap<COSDictionary, Map<COSName, COSBase>>();
        IdentityHashMap<COSArray, List<COSBase>> pendingArrays =
                new IdentityHashMap<COSArray, List<COSBase>>();
        PreparedPatch result = new PreparedPatch(prepared, ownership, pendingValues, pendingArrays, resources);
        try {
            IdentityHashMap<COSBase, Boolean> streamMetadata = protectedStreamMetadata();
            for (DocumentPatch.Change change : patch.getChanges()) {
                resources.checkpoint();
                if (change.getOperation() == DocumentPatch.Operation.REPLACE_VALUE) {
                    prepareReplacement(change, result, streamMetadata);
                    continue;
                }
                if (change.getOperation() == DocumentPatch.Operation.REPLACE_STREAM_DATA) {
                    prepareStreamData(change, result, streamMetadata);
                    continue;
                }
                COSBase target = resolvePath(change.getPath(), result);
                if (streamMetadata.containsKey(target)) {
                    throw illegalStreamChange();
                }
                COSName name = change.getName() == null ? null
                        : COSName.getPDFName(change.getName().getValue());
                requireNoVersionSecurityChange(target, name);
                if (change.getOperation().targetsArray()) {
                    if (!(target instanceof COSArray)) {
                        throw invalidPatchPath();
                    }
                    COSArray array = (COSArray) target;
                    List<COSBase> elements = pendingArrays.get(array);
                    if (elements == null) {
                        elements = new ArrayList<COSBase>(array.toList());
                        pendingArrays.put(array, elements);
                    }
                    boolean inserting = change.getOperation() == DocumentPatch.Operation.INSERT_ARRAY_ELEMENT;
                    if (change.getIndex() < 0 || change.getIndex() > elements.size()
                            || (!inserting && change.getIndex() == elements.size())) {
                        throw invalidPatchPath();
                    }
                    COSBase value = null;
                    if (change.getOperation().hasValue()) {
                        requirePatchNesting(change.getValue());
                        value = backendValue(change.getValue(), result);
                    }
                    COSBase previous = null;
                    if (inserting) {
                        elements.add(change.getIndex(), value);
                    } else if (change.getOperation() == DocumentPatch.Operation.REMOVE_ARRAY_ELEMENT) {
                        previous = elements.remove(change.getIndex());
                    } else if (change.getOperation() == DocumentPatch.Operation.SET_ARRAY_ELEMENT) {
                        previous = elements.set(change.getIndex(), value);
                    } else {
                        throw new IllegalStateException("Unknown array Patch operation");
                    }
                    prepared.add(new ArrayChange(array, change.getIndex(), value, previous,
                            change.getOperation()));
                    continue;
                }
                if (!(target instanceof COSDictionary)) {
                    throw failure(
                            DocumentFailureCode.COMMAND_REJECTED,
                            "The Document Patch target is not a dictionary.");
                }
                if (target instanceof COSStream
                        && isEngineOwnedStreamName(change.getName())) {
                    throw illegalStreamChange();
                }
                COSDictionary dictionary = (COSDictionary) target;
                COSBase value = null;
                if (change.getOperation().hasValue()) {
                    requirePatchNesting(change.getValue());
                    value = backendValue(
                            change.getValue(),
                            result);
                }
                result.setDictionaryValue(dictionary, name, value);
            }
            return result;
        } catch (DocumentFailure failure) {
            result.close();
            throw failure;
        } catch (RuntimeException | Error failure) {
            result.close();
            throw failure;
        }
    }

    private ReferenceTarget referenceTarget(ObjectReference reference, PreparedPatch patch) {
        ReferenceTarget replacement = patch.replacementTargets.get(reference);
        return replacement == null ? targets.get(reference) : replacement;
    }

    private void prepareReplacement(DocumentPatch.Change change, PreparedPatch patch,
            IdentityHashMap<COSBase, Boolean> streamMetadata) throws DocumentFailure {
        ObjectReference reference = change.getTarget();
        requireOwned(reference);
        ReferenceTarget original = referenceTarget(reference, patch);
        if (original == null) {
            throw invalidPatchPath();
        }
        if (change.getValue() instanceof PdfIndirectReference) {
            ObjectReference referenced = ((PdfIndirectReference) change.getValue()).getReference();
            requireOwned(referenced);
            if (reference.equals(referenced)) {
                throw referenceCycle();
            }
            throw failure(DocumentFailureCode.PATCH_VALUE_REJECTED,
                    "An indirect object replacement must contain a direct PDF value.");
        }
        COSBase protectedTarget = original.value instanceof COSDictionary
                || original.value instanceof COSArray ? original.value : original.rawValue;
        if (streamMetadata.containsKey(protectedTarget)) {
            throw illegalStreamChange();
        }
        requireNoVersionSecurityChange(protectedTarget, null);
        requirePatchNesting(change.getValue());
        COSBase value = backendValue(change.getValue(), patch);
        if (original.value instanceof COSDictionary && !(original.value instanceof COSStream)
                && value instanceof COSDictionary && !(value instanceof COSStream)) {
            prepareDictionaryReplacement((COSDictionary) original.value, (COSDictionary) value, patch);
            return;
        }
        if (original.value instanceof COSStream && value instanceof COSStream) {
            COSStream stream = (COSStream) original.value;
            COSStream replacement = (COSStream) value;
            prepareDictionaryReplacement(stream, replacement, patch);
            prepareStreamContents(stream, replacement, patch);
            return;
        }
        if (original.value == document.getDocumentCatalog().getCOSObject()) {
            throw invalidDocumentStructure();
        }
        if (original.value instanceof COSArray && value instanceof COSArray) {
            COSArray array = (COSArray) original.value;
            if (sameValue(array, value, patch, 1)) {
                return;
            }
            List<COSBase> elements = patch.arrays.get(array);
            if (elements == null) {
                elements = new ArrayList<COSBase>(array.toList());
                patch.arrays.put(array, elements);
            }
            for (int index = elements.size() - 1; index >= 0; index--) {
                resources.checkpoint();
                COSBase previous = elements.remove(index);
                patch.changes.add(new ArrayChange(array, index, null, previous,
                        DocumentPatch.Operation.REMOVE_ARRAY_ELEMENT));
            }
            COSArray replacement = (COSArray) value;
            for (int index = 0; index < replacement.size(); index++) {
                resources.checkpoint();
                COSBase element = replacement.get(index);
                elements.add(element);
                patch.changes.add(new ArrayChange(array, index, element, null,
                        DocumentPatch.Operation.INSERT_ARRAY_ELEMENT));
            }
            return;
        }
        prepareReferenceReplacement(reference, original, value, patch);
    }

    private void prepareDictionaryReplacement(COSDictionary target, COSDictionary replacement, PreparedPatch patch)
            throws DocumentFailure {
        Set<COSName> names = patch.dictionaryNames(target);
        names.addAll(replacement.keySet());
        for (COSName name : names) {
            resources.checkpoint();
            if (target instanceof COSStream && isEngineOwnedStreamName(name.getName())) {
                continue;
            }
            COSBase current = patch.dictionaryValue(target, name);
            COSBase next = replacement.getItem(name);
            if (!sameValue(current, next, patch, 1)) {
                requireNoVersionSecurityChange(target, name);
                patch.setDictionaryValue(target, name, next);
            }
        }
    }

    private void prepareReferenceReplacement(ObjectReference reference, ReferenceTarget original,
            COSBase value, PreparedPatch patch) throws DocumentFailure {
        COSObject replacement = new COSObject(value);
        ReferenceTarget next = new ReferenceTarget(replacement, value);
        patch.replacementTargets.put(reference, next);
        replaceAliases(original.rawValue, replacement, patch);
        patch.changes.add(new ReferenceChange(reference, original, next));
    }

    private void prepareStreamData(DocumentPatch.Change change, PreparedPatch patch,
            IdentityHashMap<COSBase, Boolean> streamMetadata) throws DocumentFailure {
        COSBase value = resolvePath(change.getPath(), patch);
        if (!(value instanceof COSStream)) {
            throw invalidPatchPath();
        }
        if (streamMetadata.containsKey(value)) {
            throw illegalStreamChange();
        }
        requireNoVersionSecurityChange(value, null);
        COSStream original = (COSStream) value;
        COSStream replacement = preparedStream(patch);
        try (OutputStream output = replacement.createOutputStream(
                change.getStreamEncoding() == PdfStreamEncoding.FLATE ? COSName.FLATE_DECODE : null)) {
            resources.writeBytesAsIOException(output, change.getStreamData());
        } catch (IOException streamFailure) {
            resources.rethrowResourceOrTerminalFailure(streamFailure);
            throw failure(DocumentFailureCode.COMMAND_REJECTED,
                    "The Document Patch stream could not be created.");
        }
        prepareStreamContents(original, replacement, patch);
    }

    private void prepareStreamContents(COSStream original, COSStream replacement, PreparedPatch patch)
            throws DocumentFailure {
        COSStream backup = patch.streamBackups.get(original);
        if (backup == null) {
            backup = preparedStream(patch);
            try {
                copyEncodedStream(original, backup);
            } catch (IOException streamFailure) {
                resources.rethrowResourceOrTerminalFailure(streamFailure);
                throw failure(DocumentFailureCode.COMMAND_REJECTED,
                        "The Document Patch stream could not be created.");
            }
            patch.streamBackups.put(original, backup);
        }
        patch.changes.add(new StreamDataChange(original, replacement, backup));
        for (String name : ENGINE_OWNED_STREAM_NAMES) {
            COSName key = COSName.getPDFName(name);
            patch.setDictionaryValue(original, key, replacement.getItem(key));
        }
    }

    private void copyEncodedStream(COSStream source, COSStream target)
            throws IOException, DocumentFailure {
        try (WorkflowResourceContext.MemoryReservation bufferMemory = resources.reserveOwnedMemory(8192);
                InputStream input = resources.checkpointedInput(source.createRawInputStream());
                OutputStream output = target.createRawOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                resources.writeBytesAsIOException(output, buffer, 0, count);
            }
        }
    }

    private boolean sameValue(COSBase left, COSBase right, PreparedPatch patch, int depth)
            throws DocumentFailure {
        resources.checkpoint();
        if (left == right) {
            return true;
        }
        if (left == null || right == null || left instanceof COSObject || right instanceof COSObject
                || left instanceof COSStream || right instanceof COSStream) {
            return false;
        }
        if (left instanceof COSArray && right instanceof COSArray) {
            requireMaterializationDepth(depth);
            resources.requireNestingDepth(depth);
            COSArray first = (COSArray) left;
            COSArray second = (COSArray) right;
            List<COSBase> earlier = patch.arrays.get(first);
            int size = earlier == null ? first.size() : earlier.size();
            if (size != second.size()) {
                return false;
            }
            for (int index = 0; index < size; index++) {
                COSBase child = earlier == null ? first.get(index) : earlier.get(index);
                if (!sameValue(child, second.get(index), patch, depth + 1)) {
                    return false;
                }
            }
            return true;
        }
        if (left instanceof COSDictionary && right instanceof COSDictionary) {
            requireMaterializationDepth(depth);
            resources.requireNestingDepth(depth);
            COSDictionary first = (COSDictionary) left;
            COSDictionary second = (COSDictionary) right;
            Set<COSName> names = patch.dictionaryNames(first);
            names.addAll(second.keySet());
            for (COSName name : names) {
                COSBase child = patch.dictionaryValue(first, name);
                if (!sameValue(child, second.getItem(name), patch, depth + 1)) {
                    return false;
                }
            }
            return true;
        }
        if (left instanceof COSNumber && right instanceof COSNumber) {
            try {
                BigDecimal first = left instanceof COSInteger
                        ? BigDecimal.valueOf(((COSInteger) left).longValue())
                        : serializedNumber((COSFloat) left, resources);
                BigDecimal second = right instanceof COSInteger
                        ? BigDecimal.valueOf(((COSInteger) right).longValue())
                        : serializedNumber((COSFloat) right, resources);
                return first.compareTo(second) == 0;
            } catch (IOException invalidNumber) {
                resources.rethrowResourceOrTerminalFailure(invalidNumber);
                throw invalidPatchNumber();
            }
        }
        return left.equals(right);
    }

    private void replaceAliases(COSBase original, COSBase replacement, PreparedPatch patch)
            throws DocumentFailure {
        Deque<BackendValueNode> pending = new ArrayDeque<BackendValueNode>();
        pending.push(new BackendValueNode(document.getDocument().getTrailer(), 1));
        for (COSObjectKey key : document.getDocument().getXrefTable().keySet()) {
            resources.checkpoint();
            pending.push(new BackendValueNode(document.getDocument().getObjectFromPool(key), 1));
        }
        for (ObjectReference reference : targets.keySet()) {
            pending.push(new BackendValueNode(referenceTarget(reference, patch).rawValue, 1));
        }
        IdentityHashMap<COSBase, Boolean> visited = new IdentityHashMap<COSBase, Boolean>();
        while (!pending.isEmpty()) {
            resources.checkpoint();
            BackendValueNode node = pending.pop();
            COSBase value = node.value;
            if (value == null || visited.put(value, Boolean.TRUE) != null) {
                continue;
            }
            if (value instanceof COSObject) {
                resources.requireNestingDepth(node.depth);
                pending.push(new BackendValueNode(((COSObject) value).getObject(), node.depth + 1));
            } else if (value instanceof COSDictionary) {
                resources.requireNestingDepth(node.depth);
                COSDictionary dictionary = (COSDictionary) value;
                for (COSName name : patch.dictionaryNames(dictionary)) {
                    resources.checkpoint();
                    COSBase child = patch.dictionaryValue(dictionary, name);
                    if (child == original) {
                        patch.setDictionaryValue(dictionary, name, replacement);
                        child = replacement;
                    }
                    pending.push(new BackendValueNode(child, node.depth + 1));
                }
            } else if (value instanceof COSArray) {
                resources.requireNestingDepth(node.depth);
                COSArray array = (COSArray) value;
                List<COSBase> elements = patch.arrays.get(array);
                int size = elements == null ? array.size() : elements.size();
                for (int index = 0; index < size; index++) {
                    resources.checkpoint();
                    COSBase child = elements == null ? array.get(index) : elements.get(index);
                    if (child == original) {
                        if (elements == null) {
                            elements = new ArrayList<COSBase>(array.toList());
                            patch.arrays.put(array, elements);
                        }
                        elements.set(index, replacement);
                        patch.changes.add(new ArrayChange(array, index, replacement, child,
                                DocumentPatch.Operation.SET_ARRAY_ELEMENT));
                        child = replacement;
                    }
                    pending.push(new BackendValueNode(child, node.depth + 1));
                }
            }
        }
    }

    private COSBase resolvePath(
            PdfValuePath path,
            PreparedPatch patch)
            throws DocumentFailure {
        return dereference(resolveRawPath(path, patch));
    }

    private COSBase resolveRawPath(PdfValuePath path, PreparedPatch patch)
            throws DocumentFailure {
        requireOwned(path.getRoot());
        ReferenceTarget root = referenceTarget(path.getRoot(), patch);
        COSBase value = root == null ? null : root.rawValue;
        int depth = 0;
        for (PdfValuePath.Step step : path.getSteps()) {
            resources.checkpoint();
            resources.requireNestingDepth(++depth);
            value = dereference(value);
            if (step.getName() != null && value instanceof COSDictionary) {
                COSDictionary dictionary = (COSDictionary) value;
                COSName name = COSName.getPDFName(step.getName().getValue());
                value = patch.dictionaryValue(dictionary, name);
            } else if (step.getName() == null && value instanceof COSArray) {
                COSArray array = (COSArray) value;
                List<COSBase> elements = patch.arrays.get(array);
                int size = elements == null ? array.size() : elements.size();
                if (step.getIndex() < 0 || step.getIndex() >= size) {
                    throw invalidPatchPath();
                }
                value = elements == null ? array.get(step.getIndex()) : elements.get(step.getIndex());
            } else {
                throw invalidPatchPath();
            }
        }
        if (value == null) {
            throw invalidPatchPath();
        }
        return value;
    }

    private static DocumentFailure invalidPatchPath() {
        return failure(DocumentFailureCode.COMMAND_REJECTED,
                "The Document Patch target path is invalid.");
    }

    private void requireNoVersionSecurityChange(
            COSBase target,
            COSName name) throws DocumentFailure {
        COSDictionary catalog = document.getDocumentCatalog().getCOSObject();
        COSDictionary encryption = document.getEncryption() == null
                ? null : document.getEncryption().getCOSObject();
        COSDictionary trailer = document.getDocument().getTrailer();
        if ((target == catalog
                        && (COSName.VERSION.equals(name)
                                || COSName.EXTENSIONS.equals(name)))
                || containsValue(
                        catalog.getItem(COSName.VERSION),
                        target,
                        new IdentityHashMap<COSBase, Boolean>())
                || containsValue(
                        catalog.getItem(COSName.EXTENSIONS),
                        target,
                        new IdentityHashMap<COSBase, Boolean>())
                || containsValue(
                        trailer.getItem(COSName.ENCRYPT),
                        target,
                        new IdentityHashMap<COSBase, Boolean>())
                || containsValue(
                        encryption,
                        target,
                        new IdentityHashMap<COSBase, Boolean>())
                || (target == trailer && COSName.ENCRYPT.equals(name))) {
            throw PdfBoxWorkflowEngine.versionFailure(
                    DocumentFailureCode.COMMAND_REJECTED,
                    "A Document Patch cannot change engine-owned version or password-security state.");
        }
    }

    private boolean containsValue(
            COSBase value,
            COSBase target,
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

    private IdentityHashMap<COSBase, Boolean> collectDocumentValues()
            throws DocumentFailure {
        IdentityHashMap<COSBase, Boolean> graph = new IdentityHashMap<COSBase, Boolean>();
        collectValues(document.getDocument().getTrailer(), graph);
        for (COSObjectKey key : document.getDocument().getXrefTable().keySet()) {
            resources.checkpoint();
            collectValues(document.getDocument().getObjectFromPool(key), graph);
        }
        for (ReferenceTarget target : targets.values()) {
            collectValues(target.rawValue, graph);
        }
        for (COSBase previouslyReferenced : references.keySet()) {
            collectValues(previouslyReferenced, graph);
        }
        for (COSBase previouslyChanged : changedContainers) {
            collectValues(previouslyChanged, graph);
        }
        return graph;
    }

    private IdentityHashMap<COSBase, Boolean> protectedStreamMetadata()
            throws DocumentFailure {
        IdentityHashMap<COSBase, Boolean> graph = collectDocumentValues();
        IdentityHashMap<COSBase, Boolean> protectedValues = new IdentityHashMap<COSBase, Boolean>();
        for (COSBase value : graph.keySet()) {
            resources.checkpoint();
            if (value instanceof COSStream) {
                for (Map.Entry<COSName, COSBase> entry : ((COSStream) value).entrySet()) {
                    if (isEngineOwnedStreamName(entry.getKey().getName())) {
                        collectValues(entry.getValue(), protectedValues);
                    }
                }
            }
        }
        return protectedValues;
    }

    private void collectValues(COSBase root, IdentityHashMap<COSBase, Boolean> values)
            throws DocumentFailure {
        Deque<BackendValueNode> pending = new ArrayDeque<BackendValueNode>();
        pending.push(new BackendValueNode(root, 1));
        while (!pending.isEmpty()) {
            resources.checkpoint();
            BackendValueNode node = pending.pop();
            COSBase value = node.value;
            if (value == null || values.put(value, Boolean.TRUE) != null) {
                continue;
            }
            if (value instanceof COSObject) {
                resources.requireNestingDepth(node.depth);
                pending.push(new BackendValueNode(((COSObject) value).getObject(), node.depth + 1));
            } else if (value instanceof COSArray) {
                resources.requireNestingDepth(node.depth);
                COSArray array = (COSArray) value;
                for (int index = array.size() - 1; index >= 0; index--) {
                    pending.push(new BackendValueNode(array.get(index), node.depth + 1));
                }
            } else if (value instanceof COSDictionary) {
                resources.requireNestingDepth(node.depth);
                for (COSBase entry : ((COSDictionary) value).getValues()) {
                    pending.push(new BackendValueNode(entry, node.depth + 1));
                }
            }
        }
    }

    private static void rollback(
            List<PreparedChange> prepared,
            int applied) {
        for (int index = applied - 1; index >= 0; index--) {
            PreparedChange change = prepared.get(index);
            change.rollback();
        }
    }

    private static boolean isEngineOwnedStreamName(PdfName name) {
        return isEngineOwnedStreamName(name.getValue());
    }

    private static boolean isEngineOwnedStreamName(String value) {
        for (String name : ENGINE_OWNED_STREAM_NAMES) {
            if (name.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static DocumentFailure illegalStreamChange() {
        return failure(
                DocumentFailureCode.PATCH_STREAM_CHANGE_REJECTED,
                "The Document Patch cannot change engine-owned stream metadata.");
    }

    private void rejectReferenceCycles(PreparedPatch patch)
            throws DocumentFailure {
        for (Map.Entry<COSDictionary, Map<COSName, COSBase>> dictionary : patch.dictionaries.entrySet()) {
            resources.checkpoint();
            for (Map.Entry<COSName, COSBase> entry : dictionary.getValue().entrySet()) {
                resources.checkpoint();
                COSBase value = entry.getValue();
                if (value != dictionary.getKey().getItem(entry.getKey())
                        && reaches(value, dictionary.getKey(), patch,
                        new IdentityHashMap<COSBase, Boolean>())) {
                    throw referenceCycle();
                }
            }
        }
        for (Map.Entry<COSArray, List<COSBase>> entry : patch.arrays.entrySet()) {
            IdentityHashMap<COSBase, Boolean> originalValues = new IdentityHashMap<COSBase, Boolean>();
            for (COSBase value : entry.getKey()) {
                resources.checkpoint();
                originalValues.put(value, Boolean.TRUE);
            }
            for (COSBase value : entry.getValue()) {
                resources.checkpoint();
                if (!originalValues.containsKey(value)
                        && reaches(value, entry.getKey(), patch,
                                new IdentityHashMap<COSBase, Boolean>())) {
                    throw referenceCycle();
                }
            }
        }
    }

    private static DocumentFailure referenceCycle() {
        return failure(DocumentFailureCode.PATCH_CYCLE_REJECTED,
                "The Document Patch would introduce a reference cycle.");
    }

    private boolean reaches(
            COSBase start,
            COSBase goal,
            PreparedPatch patch,
            IdentityHashMap<COSBase, Boolean> visited)
            throws DocumentFailure {
        Deque<BackendValueNode> pending =
                new ArrayDeque<BackendValueNode>();
        pending.push(new BackendValueNode(start, 1));
        while (!pending.isEmpty()) {
            resources.checkpoint();
            BackendValueNode current = pending.pop();
            COSBase candidate = current.value;
            if (candidate == null) {
                continue;
            }
            if (candidate == goal) {
                return true;
            }
            if (visited.put(candidate, Boolean.TRUE) != null) {
                continue;
            }
            int childDepth = current.depth + 1;
            if (candidate instanceof COSObject) {
                resources.requireNestingDepth(current.depth);
                pending.push(new BackendValueNode(
                        ((COSObject) candidate).getObject(),
                        childDepth));
            } else if (candidate instanceof COSArray) {
                resources.requireNestingDepth(current.depth);
                COSArray array = (COSArray) candidate;
                List<COSBase> elements = patch.arrays.get(array);
                int size = elements == null ? array.size() : elements.size();
                for (int index = size - 1; index >= 0; index--) {
                    resources.checkpoint();
                    pending.push(new BackendValueNode(
                            elements == null ? array.get(index) : elements.get(index),
                            childDepth));
                }
            } else if (candidate instanceof COSDictionary) {
                resources.requireNestingDepth(current.depth);
                COSDictionary dictionary = (COSDictionary) candidate;
                for (COSName name : patch.dictionaryNames(dictionary)) {
                    resources.checkpoint();
                    pending.push(new BackendValueNode(
                            patch.dictionaryValue(dictionary, name),
                            childDepth));
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
            PreparedPatch patch)
            throws DocumentFailure {
        if (value == PdfNull.INSTANCE) {
            return COSNull.NULL;
        }
        if (value instanceof PdfBoolean) {
            return COSBoolean.getBoolean(((PdfBoolean) value).booleanValue());
        }
        if (value instanceof PdfNumber || value instanceof PdfString) {
            return materializedScalar(value, patch);
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
                        patch));
            }
            return converted;
        }
        if (value instanceof PdfDictionary) {
            return backendDictionary(
                    (PdfDictionary) value,
                    patch,
                    false);
        }
        if (value instanceof PdfIndirectReference) {
            ObjectReference reference =
                    ((PdfIndirectReference) value).getReference();
            requireOwned(reference);
            ReferenceTarget target = referenceTarget(reference, patch);
            if (target == null) {
                throw failure(
                        DocumentFailureCode.COMMAND_REJECTED,
                        "The Document Patch contains an unavailable Object Reference.");
            }
            return target.rawValue;
        }
        if (value instanceof PdfStream) {
            PdfStream publicStream = (PdfStream) value;
            COSDictionary attributes = backendDictionary(
                    publicStream.getDictionary(),
                    patch,
                    true);
            COSStream converted = preparedStream(patch);
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

    private COSStream preparedStream(PreparedPatch patch) {
        COSStream stream = document.getDocument().createCOSStream();
        patch.createdStreams.add(stream);
        return stream;
    }

    private COSBase materializedScalar(PdfValue value, PreparedPatch patch)
            throws DocumentFailure {
        WorkflowResourceContext.OwnedMemoryScope ownership = resources.ownedMemoryScope();
        try {
            COSBase converted = value instanceof PdfNumber
                    ? backendNumber((PdfNumber) value, ownership)
                    : PdfBoxStringSupport.backendCopy((PdfString) value, resources, ownership,
                            PdfBoxValueAdapter::invalidPatchValue);
            if (converted instanceof COSInteger) {
                ownership.close();
            } else {
                patch.valueMemory.put(converted, ownership);
            }
            return converted;
        } catch (DocumentFailure failure) {
            ownership.close();
            throw failure;
        } catch (RuntimeException | Error failure) {
            ownership.close();
            throw failure;
        }
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
            PreparedPatch patch,
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
                            patch));
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

    private abstract static class PreparedChange {
        abstract void apply() throws DocumentFailure;

        abstract void rollback();
    }

    private final class StreamDataChange extends PreparedChange {
        private final COSStream target;
        private final COSStream replacement;
        private final COSStream backup;
        private final boolean lengthPresent;
        private final COSBase originalLength;

        StreamDataChange(COSStream target, COSStream replacement, COSStream backup) {
            this.target = target;
            this.replacement = replacement;
            this.backup = backup;
            this.lengthPresent = target.containsKey(COSName.LENGTH);
            this.originalLength = target.getItem(COSName.LENGTH);
        }

        @Override
        void apply() throws DocumentFailure {
            try {
                copyEncodedStream(replacement, target);
            } catch (IOException | RuntimeException writeFailure) {
                rollback();
                resources.rethrowResourceOrTerminalFailure(writeFailure);
                throw failure(DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                        "The Document Patch could not be applied.");
            } catch (DocumentFailure writeFailure) {
                rollback();
                resources.rethrowTerminalFailure();
                throw writeFailure;
            }
        }

        @Override
        void rollback() {
            try {
                copyEncodedStream(backup, target);
            } catch (IOException | DocumentFailure | RuntimeException restoreFailure) {
                resources.terminalFailure(failure(DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                        "The Document Patch could not be applied."));
            } finally {
                if (lengthPresent) {
                    target.setItem(COSName.LENGTH, originalLength);
                } else {
                    target.removeItem(COSName.LENGTH);
                }
            }
        }
    }

    private static final class DictionaryChange extends PreparedChange {
        private final COSDictionary target;
        private final COSName name;
        private final COSBase value;
        private final boolean originallyPresent;
        private final COSBase originalValue;

        DictionaryChange(COSDictionary target, COSName name, COSBase value,
                boolean originallyPresent, COSBase originalValue) {
            this.target = target;
            this.name = name;
            this.value = value;
            this.originallyPresent = originallyPresent;
            this.originalValue = originalValue;
        }

        @Override
        void apply() {
            target.setItem(name, value);
        }

        @Override
        void rollback() {
            if (originallyPresent) {
                target.setItem(name, originalValue);
            } else {
                target.removeItem(name);
            }
        }
    }

    private static final class ArrayChange extends PreparedChange {
        private final COSArray target;
        private final int index;
        private final DocumentPatch.Operation operation;
        private final COSBase value;
        private final COSBase originalValue;

        ArrayChange(COSArray target, int index, COSBase value, COSBase originalValue,
                DocumentPatch.Operation operation) {
            this.target = target;
            this.index = index;
            this.operation = operation;
            this.value = value;
            this.originalValue = originalValue;
        }

        @Override
        void apply() {
            switch (operation) {
                case INSERT_ARRAY_ELEMENT:
                    target.add(index, value);
                    break;
                case REMOVE_ARRAY_ELEMENT:
                    target.remove(index);
                    break;
                case SET_ARRAY_ELEMENT:
                    target.set(index, value);
                    break;
                default:
                    throw new IllegalStateException("Unknown array Patch operation");
            }
        }

        @Override
        void rollback() {
            switch (operation) {
                case INSERT_ARRAY_ELEMENT:
                    target.remove(index);
                    break;
                case REMOVE_ARRAY_ELEMENT:
                    target.add(index, originalValue);
                    break;
                case SET_ARRAY_ELEMENT:
                    target.set(index, originalValue);
                    break;
                default:
                    throw new IllegalStateException("Unknown array Patch operation");
            }
        }
    }

    private final class ReferenceChange extends PreparedChange {
        private final ObjectReference reference;
        private final ReferenceTarget original;
        private final ReferenceTarget replacement;

        ReferenceChange(ObjectReference reference, ReferenceTarget original, ReferenceTarget replacement) {
            this.reference = reference;
            this.original = original;
            this.replacement = replacement;
        }

        @Override
        void apply() {
            targets.put(reference, replacement);
        }

        @Override
        void rollback() {
            targets.put(reference, original);
        }
    }

    private static final class PreparedPatch implements AutoCloseable {

        private final List<PreparedChange> changes;
        private final WorkflowResourceContext resources;
        private final List<COSStream> createdStreams = new ArrayList<COSStream>();
        private final IdentityHashMap<COSStream, COSStream> streamBackups =
                new IdentityHashMap<COSStream, COSStream>();
        private final WorkflowResourceContext.OwnedMemoryScope ownership;
        private final IdentityHashMap<COSBase, WorkflowResourceContext.OwnedMemoryScope> valueMemory =
                new IdentityHashMap<COSBase, WorkflowResourceContext.OwnedMemoryScope>();
        private final IdentityHashMap<COSDictionary, Map<COSName, COSBase>> dictionaries;
        private final IdentityHashMap<COSArray, List<COSBase>> arrays;
        private final Map<ObjectReference, ReferenceTarget> replacementTargets =
                new HashMap<ObjectReference, ReferenceTarget>();

        private PreparedPatch(
                List<PreparedChange> changes,
                WorkflowResourceContext.OwnedMemoryScope ownership,
                IdentityHashMap<COSDictionary, Map<COSName, COSBase>> dictionaries,
                IdentityHashMap<COSArray, List<COSBase>> arrays,
                WorkflowResourceContext resources) {
            this.changes = changes;
            this.ownership = ownership;
            this.dictionaries = dictionaries;
            this.arrays = arrays;
            this.resources = resources;
        }

        private Set<COSName> dictionaryNames(COSDictionary dictionary) {
            Set<COSName> names = new LinkedHashSet<COSName>(dictionary.keySet());
            Map<COSName, COSBase> entries = dictionaries.get(dictionary);
            if (entries != null) {
                names.addAll(entries.keySet());
            }
            return names;
        }

        private COSBase dictionaryValue(COSDictionary dictionary, COSName name) {
            Map<COSName, COSBase> entries = dictionaries.get(dictionary);
            return entries != null && entries.containsKey(name) ? entries.get(name) : dictionary.getItem(name);
        }

        private void setDictionaryValue(COSDictionary dictionary, COSName name, COSBase value) {
            changes.add(new DictionaryChange(dictionary, name, value,
                    dictionary.containsKey(name), dictionary.getItem(name)));
            Map<COSName, COSBase> entries = dictionaries.get(dictionary);
            if (entries == null) {
                entries = new HashMap<COSName, COSBase>();
                dictionaries.put(dictionary, entries);
            }
            entries.put(name, value);
        }

        private void transfer(Map<COSBase, Boolean> retained)
                throws DocumentFailure {
            for (COSStream stream : createdStreams) {
                resources.checkpoint();
                if (!retained.containsKey(stream)) {
                    discardStream(stream);
                }
            }
            for (Map.Entry<COSBase, WorkflowResourceContext.OwnedMemoryScope> allocation : valueMemory.entrySet()) {
                resources.checkpoint();
                if (retained.containsKey(allocation.getKey())) {
                    allocation.getValue().transferTo(ownership);
                }
                allocation.getValue().close();
            }
            valueMemory.clear();
            ownership.transfer();
            createdStreams.clear();
        }

        @Override
        public void close() throws DocumentFailure {
            changes.clear();
            dictionaries.clear();
            arrays.clear();
            replacementTargets.clear();
            streamBackups.clear();
            DocumentFailure streamFailure = null;
            try {
                for (COSStream stream : createdStreams) {
                    try {
                        discardStream(stream);
                    } catch (DocumentFailure failure) {
                        if (streamFailure == null) {
                            streamFailure = failure;
                        }
                    }
                }
            } finally {
                createdStreams.clear();
                for (WorkflowResourceContext.OwnedMemoryScope allocation : valueMemory.values()) {
                    allocation.close();
                }
                valueMemory.clear();
                ownership.close();
            }
            if (streamFailure != null) {
                throw streamFailure;
            }
        }

        private void discardStream(COSStream stream) throws DocumentFailure {
            resources.invalidateStreamPreflight(stream);
            try {
                stream.close();
            } catch (IOException closingFailure) {
                throw resources.terminalFailure(failure(DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                        "The Document Patch could not be applied."));
            } finally {
                stream.clear();
            }
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
