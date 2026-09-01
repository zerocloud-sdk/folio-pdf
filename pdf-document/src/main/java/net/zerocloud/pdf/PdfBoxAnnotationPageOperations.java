package net.zerocloud.pdf;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.zerocloud.pdf.PdfBoxAnnotationOperations.ByteBudget;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSBoolean;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;

/**
 * Preserves and retargets managed annotations and Actions across page
 * operations.
 */
final class PdfBoxAnnotationPageOperations {

    private static final COSName OPEN_ACTION =
            COSName.getPDFName("OpenAction");
    private static final COSName AA = COSName.getPDFName("AA");
    private static final COSName O = COSName.getPDFName("O");
    private static final COSName C = COSName.getPDFName("C");
    private static final COSName NM = COSName.getPDFName("NM");
    private final PDDocument document;
    private final PdfBoxMetadataOperations metadataOperations;
    private final PdfBoxAnnotationOperations annotationOperations;

    PdfBoxAnnotationPageOperations(
            PDDocument document,
            PdfBoxMetadataOperations metadataOperations,
            PdfBoxAnnotationOperations annotationOperations) {
        this.document = document;
        this.metadataOperations = metadataOperations;
        this.annotationOperations = annotationOperations;
    }

    void requireSafeActionStructures(PDDocument candidate)
            throws DocumentFailure {
        try {
            List<COSBase> pageReferences = metadataOperations.rawPageReferences(
                    candidate,
                    PdfBoxMetadataOperations.StructureFailure.PRESERVE);
            IdentityHashMap<COSDictionary, Integer> pageNumbers =
                    pageNumbers(pageReferences);
            Set<String> namedDestinations =
                    metadataOperations.namedDestinationNames(candidate);
            COSDictionary catalog = candidate.getDocumentCatalog()
                    .getCOSObject();
            if (catalog.getItem(OPEN_ACTION) != null) {
                requireKnownNamedTarget(
                        actionTarget(
                                catalog.getItem(OPEN_ACTION),
                                pageNumbers),
                        namedDestinations);
            }
            for (COSBase pageReference : pageReferences) {
                COSDictionary page = dictionary(pageReference);
                COSBase rawAa = dereference(page.getItem(AA));
                if (rawAa == null) {
                    continue;
                }
                if (!(rawAa instanceof COSDictionary)
                        || rawAa instanceof COSStream) {
                    throw preservationUnsupported();
                }
                COSDictionary aa = (COSDictionary) rawAa;
                if (aa.size() == 0) {
                    throw preservationUnsupported();
                }
                requireOnlyKeys(aa, "O", "C");
                if (aa.getItem(O) != null) {
                    requireKnownNamedTarget(
                            actionTarget(aa.getItem(O), pageNumbers),
                            namedDestinations);
                }
                if (aa.getItem(C) != null) {
                    requireKnownNamedTarget(
                            actionTarget(aa.getItem(C), pageNumbers),
                            namedDestinations);
                }
            }
        } catch (DocumentFailure invalid) {
            throw preservationUnsupported();
        } catch (RuntimeException invalid) {
            throw preservationUnsupported();
        }
    }

    void requireSafeAnnotations(
            PDDocument candidate,
            COSDictionary page,
            COSBase rawAnnotations,
            PdfBoxAnnotationDecodePolicy.Budgets budgets)
            throws DocumentFailure {
        if (rawAnnotations == null) {
            return;
        }
        try {
            List<COSBase> pageReferences = metadataOperations.rawPageReferences(
                    candidate,
                    PdfBoxMetadataOperations.StructureFailure.PRESERVE);
            IdentityHashMap<COSDictionary, Integer> pageNumbers =
                    pageNumbers(pageReferences);
            Set<String> namedDestinations =
                    metadataOperations.namedDestinationNames(candidate);
            Integer pageNumber = pageNumbers.get(page);
            COSBase value = dereference(rawAnnotations);
            if (pageNumber == null || !(value instanceof COSArray)) {
                throw preservationUnsupported();
            }
            COSArray annotations = (COSArray) value;
            Set<String> identifiers = new HashSet<String>();
            for (int index = 0; index < annotations.size(); index++) {
                COSDictionary rawAnnotation = dictionary(
                        annotations.get(index));
                Annotation annotation = managedAnnotationOrNull(
                        rawAnnotation,
                        pageNumber.intValue(),
                        page,
                        budgets,
                        pageNumbers);
                if (annotation == null) {
                    String identifier = identifierOf(rawAnnotation, true);
                    if (identifier != null && !identifiers.add(identifier)) {
                        throw preservationUnsupported();
                    }
                    continue;
                }
                requireKnownNamedTarget(annotation, namedDestinations);
                if (!identifiers.add(
                        annotation.getProperties().getIdentifier())) {
                    throw preservationUnsupported();
                }
            }
        } catch (DocumentFailure invalid) {
            throw preservationUnsupported();
        } catch (RuntimeException invalid) {
            throw preservationUnsupported();
        }
    }

    private Annotation managedAnnotationOrNull(
            COSDictionary annotation,
            int pageNumber,
            COSDictionary page,
            PdfBoxAnnotationDecodePolicy.Budgets budgets,
            IdentityHashMap<COSDictionary, Integer> pageNumbers)
            throws DocumentFailure {
        try {
            return publicAnnotation(
                    annotation,
                    pageNumber,
                    page,
                    budgets.appearances(),
                    budgets.attachments(),
                    pageNumbers);
        } catch (DocumentFailure unsupportedManagedAnnotation) {
            if (unsupportedManagedAnnotation.getCode()
                    == DocumentFailureCode.ANNOTATION_LIMIT_EXCEEDED) {
                throw unsupportedManagedAnnotation;
            }
            requireSafeLegacyTextAnnotation(page, annotation);
            return null;
        }
    }

    private static void requireSafeLegacyTextAnnotation(
            COSDictionary page,
            COSDictionary annotation) throws DocumentFailure {
        if (!COSName.getPDFName("Annot").equals(dereference(
                        annotation.getItem(COSName.TYPE)))
                || !COSName.getPDFName("Text").equals(dereference(
                        annotation.getItem(COSName.SUBTYPE)))) {
            throw preservationUnsupported();
        }
        requireOnlyKeys(annotation,
                "Type", "Subtype", "Rect", "Contents", "P", "NM", "M",
                "F", "C", "CA", "ca", "Name", "Open", "State",
                "StateModel");
        requireLegacyRectangle(annotation.getItem(COSName.RECT));
        requireOptionalType(annotation, COSName.CONTENTS, COSString.class);
        requireOptionalType(annotation, COSName.getPDFName("M"),
                COSString.class);
        requireOptionalType(annotation, COSName.getPDFName("NM"),
                COSString.class);
        requireOptionalType(annotation, COSName.F, COSInteger.class);
        requireOptionalType(annotation, COSName.NAME, COSName.class);
        requireOptionalType(annotation, COSName.getPDFName("Open"),
                COSBoolean.class);
        requireOptionalType(annotation, COSName.getPDFName("State"),
                COSString.class);
        requireOptionalType(annotation, COSName.getPDFName("StateModel"),
                COSString.class);
        requireLegacyColor(annotation.getItem(COSName.C));
        requireLegacyOpacity(annotation.getItem(COSName.getPDFName("CA")));
        requireLegacyOpacity(annotation.getItem(COSName.getPDFName("ca")));

        COSBase identifier = dereference(annotation.getItem(
                COSName.getPDFName("NM")));
        if (identifier instanceof COSString
                && ((COSString) identifier).getString().isEmpty()) {
            throw preservationUnsupported();
        }
        COSBase flags = dereference(annotation.getItem(COSName.F));
        if (flags instanceof COSInteger
                && ((COSInteger) flags).longValue() < 0L) {
            throw preservationUnsupported();
        }
        COSBase annotationPage = annotation.getItem(COSName.getPDFName("P"));
        if (annotationPage != null && dereference(annotationPage) != page) {
            throw preservationUnsupported();
        }
        for (COSName name : annotation.keySet()) {
            if (!COSName.getPDFName("P").equals(name)
                    && !isSafeInlineValue(
                            annotation.getItem(name),
                            new IdentityHashMap<COSBase, Boolean>())) {
                throw preservationUnsupported();
            }
        }
    }

    private static void requireLegacyRectangle(COSBase raw)
            throws DocumentFailure {
        COSBase value = dereference(raw);
        if (!(value instanceof COSArray) || ((COSArray) value).size() != 4) {
            throw preservationUnsupported();
        }
        COSArray rectangle = (COSArray) value;
        double[] coordinates = new double[4];
        for (int index = 0; index < coordinates.length; index++) {
            COSBase coordinate = dereference(rectangle.get(index));
            if (!(coordinate instanceof COSNumber)) {
                throw preservationUnsupported();
            }
            coordinates[index] = ((COSNumber) coordinate).floatValue();
            if (Double.isNaN(coordinates[index])
                    || Double.isInfinite(coordinates[index])) {
                throw preservationUnsupported();
            }
        }
        if (coordinates[2] <= coordinates[0]
                || coordinates[3] <= coordinates[1]) {
            throw preservationUnsupported();
        }
    }

    private static void requireOptionalType(
            COSDictionary dictionary,
            COSName name,
            Class<?> expectedType) throws DocumentFailure {
        COSBase value = dereference(dictionary.getItem(name));
        if (value != null && !expectedType.isInstance(value)) {
            throw preservationUnsupported();
        }
    }

    private static void requireLegacyColor(COSBase raw)
            throws DocumentFailure {
        if (raw == null) {
            return;
        }
        COSBase value = dereference(raw);
        if (!(value instanceof COSArray)) {
            throw preservationUnsupported();
        }
        COSArray color = (COSArray) value;
        if (color.size() != 0 && color.size() != 1
                && color.size() != 3 && color.size() != 4) {
            throw preservationUnsupported();
        }
        for (int index = 0; index < color.size(); index++) {
            requireUnitNumber(color.get(index));
        }
    }

    private static void requireLegacyOpacity(COSBase raw)
            throws DocumentFailure {
        if (raw != null) {
            requireUnitNumber(raw);
        }
    }

    private static void requireUnitNumber(COSBase raw)
            throws DocumentFailure {
        COSBase value = dereference(raw);
        if (!(value instanceof COSNumber)) {
            throw preservationUnsupported();
        }
        double number = ((COSNumber) value).floatValue();
        if (Double.isNaN(number) || Double.isInfinite(number)
                || number < 0.0d || number > 1.0d) {
            throw preservationUnsupported();
        }
    }

    private static boolean isSafeInlineValue(
            COSBase raw,
            IdentityHashMap<COSBase, Boolean> visited) {
        if (raw == null) {
            return true;
        }
        if (raw instanceof COSObject || raw instanceof COSStream) {
            return false;
        }
        if (raw instanceof COSArray) {
            if (visited.put(raw, Boolean.TRUE) != null) {
                return false;
            }
            COSArray array = (COSArray) raw;
            for (int index = 0; index < array.size(); index++) {
                if (!isSafeInlineValue(array.get(index), visited)) {
                    return false;
                }
            }
            return true;
        }
        if (raw instanceof COSDictionary) {
            if (visited.put(raw, Boolean.TRUE) != null) {
                return false;
            }
            for (COSBase entry : ((COSDictionary) raw).getValues()) {
                if (!isSafeInlineValue(entry, visited)) {
                    return false;
                }
            }
        }
        return true;
    }

    void requireNoDestinationConflict(
            PDDocument candidate,
            Set<Integer> removed) throws DocumentFailure {
        List<COSBase> pageReferences = metadataOperations.rawPageReferences(
                candidate,
                PdfBoxMetadataOperations.StructureFailure.PRESERVE);
        IdentityHashMap<COSDictionary, Integer> pageNumbers =
                pageNumbers(pageReferences);
        COSDictionary catalog = candidate.getDocumentCatalog()
                .getCOSObject();
        if (catalog.getItem(OPEN_ACTION) != null) {
            requireTargetSurvives(
                    actionTarget(catalog.getItem(OPEN_ACTION), pageNumbers),
                    removed);
        }

        PdfBoxAnnotationDecodePolicy.Budgets budgets =
                PdfBoxAnnotationDecodePolicy.newManagedGraphPass();
        for (int index = 0; index < pageReferences.size(); index++) {
            if (removed.contains(Integer.valueOf(index))) {
                continue;
            }
            COSDictionary page = dictionary(pageReferences.get(index));
            COSBase rawAa = dereference(page.getItem(AA));
            if (rawAa instanceof COSDictionary) {
                COSDictionary aa = (COSDictionary) rawAa;
                if (aa.getItem(O) != null) {
                    requireTargetSurvives(
                            actionTarget(aa.getItem(O), pageNumbers),
                            removed);
                }
                if (aa.getItem(C) != null) {
                    requireTargetSurvives(
                            actionTarget(aa.getItem(C), pageNumbers),
                            removed);
                }
            }

            COSBase rawAnnotations = dereference(
                    page.getItem(COSName.ANNOTS));
            if (!(rawAnnotations instanceof COSArray)) {
                continue;
            }
            COSArray annotations = (COSArray) rawAnnotations;
            for (int annotationIndex = 0;
                    annotationIndex < annotations.size();
                    annotationIndex++) {
                Annotation annotation = managedAnnotationOrNull(
                        dictionary(annotations.get(annotationIndex)),
                        index + 1,
                        page,
                        budgets,
                        pageNumbers);
                if (annotation != null
                        && annotation.getLinkActivation().isPresent()) {
                    requireTargetSurvives(
                            annotation.getLinkActivation().get().getTarget(),
                            removed);
                }
            }
        }
    }

    void requireNamedDestinationRemovalSafe(List<String> removedNames)
            throws DocumentFailure {
        if (removedNames.isEmpty()) {
            return;
        }
        Set<String> removed = new HashSet<String>(removedNames);
        try {
            List<COSBase> pageReferences =
                    metadataOperations.rawPageReferences(
                            document,
                            PdfBoxMetadataOperations.StructureFailure.PRESERVE);
            IdentityHashMap<COSDictionary, Integer> pageNumbers =
                    pageNumbers(pageReferences);
            Set<String> namedDestinations =
                    metadataOperations.namedDestinationNames(document);
            COSDictionary catalog = document.getDocumentCatalog()
                    .getCOSObject();
            if (catalog.getItem(OPEN_ACTION) != null) {
                requireNamedTargetSurvives(
                        actionTarget(
                                catalog.getItem(OPEN_ACTION),
                                pageNumbers),
                        namedDestinations,
                        removed);
            }

            Set<String> identifiers = new HashSet<String>();
            PdfBoxAnnotationDecodePolicy.Budgets budgets =
                    PdfBoxAnnotationDecodePolicy.newManagedGraphPass();
            for (int pageIndex = 0;
                    pageIndex < pageReferences.size();
                    pageIndex++) {
                COSDictionary page = dictionary(
                        pageReferences.get(pageIndex));
                COSBase rawAa = dereference(page.getItem(AA));
                if (rawAa != null) {
                    if (!(rawAa instanceof COSDictionary)
                            || rawAa instanceof COSStream) {
                        throw preservationUnsupported();
                    }
                    COSDictionary aa = (COSDictionary) rawAa;
                    requireOnlyKeys(aa, "O", "C");
                    if (aa.getItem(O) != null) {
                        requireNamedTargetSurvives(
                                actionTarget(aa.getItem(O), pageNumbers),
                                namedDestinations,
                                removed);
                    }
                    if (aa.getItem(C) != null) {
                        requireNamedTargetSurvives(
                                actionTarget(aa.getItem(C), pageNumbers),
                                namedDestinations,
                                removed);
                    }
                }

                COSBase rawAnnotations = dereference(
                        page.getItem(COSName.ANNOTS));
                if (rawAnnotations == null) {
                    continue;
                }
                if (!(rawAnnotations instanceof COSArray)) {
                    throw preservationUnsupported();
                }
                COSArray annotations = (COSArray) rawAnnotations;
                for (int annotationIndex = 0;
                        annotationIndex < annotations.size();
                        annotationIndex++) {
                    COSDictionary rawAnnotation = dictionary(
                            annotations.get(annotationIndex));
                    Annotation annotation = managedAnnotationOrNull(
                            rawAnnotation,
                            pageIndex + 1,
                            page,
                            budgets,
                            pageNumbers);
                    String identifier = annotation == null
                            ? identifierOf(rawAnnotation, true)
                            : annotation.getProperties().getIdentifier();
                    if (identifier != null && !identifiers.add(identifier)) {
                        throw preservationUnsupported();
                    }
                    if (annotation != null
                            && annotation.getLinkActivation().isPresent()) {
                        requireNamedTargetSurvives(
                                annotation.getLinkActivation().get()
                                        .getTarget(),
                                namedDestinations,
                                removed);
                    }
                }
            }
        } catch (DocumentFailure failure) {
            if (failure.getCode()
                    == DocumentFailureCode.DESTINATION_CONFLICT) {
                throw failure;
            }
            throw preservationUnsupported();
        } catch (RuntimeException failure) {
            throw preservationUnsupported();
        }
    }

    private static void requireNamedTargetSurvives(
            NavigationTarget target,
            Set<String> namedDestinations,
            Set<String> removed) throws DocumentFailure {
        requireKnownNamedTarget(target, namedDestinations);
        if (target.getNamedDestination().isPresent()
                && removed.contains(
                        target.getNamedDestination().get())) {
            throw destinationConflict();
        }
    }

    private static void requireTargetSurvives(
            NavigationTarget target,
            Set<Integer> removed) throws DocumentFailure {
        if (target.getPageDestination().isPresent()
                && removed.contains(Integer.valueOf(
                        target.getPageDestination().get().getPageNumber()
                                - 1))) {
            throw destinationConflict();
        }
    }

    private static void requireKnownNamedTarget(
            Annotation annotation,
            Set<String> namedDestinations) throws DocumentFailure {
        if (annotation.getLinkActivation().isPresent()) {
            requireKnownNamedTarget(
                    annotation.getLinkActivation().get().getTarget(),
                    namedDestinations);
        }
    }

    private static void requireKnownNamedTarget(
            NavigationTarget target,
            Set<String> namedDestinations) throws DocumentFailure {
        if (!isKnownNamedTarget(target, namedDestinations)) {
            throw preservationUnsupported();
        }
    }

    private static boolean isKnownNamedTarget(
            NavigationTarget target,
            Set<String> namedDestinations) {
        return target.getKind() != NavigationTarget.Kind.NAMED
                || namedDestinations.contains(
                        target.getNamedDestination().get());
    }

    private List<AnnotationSlot> captureAnnotationSlots(
            COSDictionary page,
            int pageNumber,
            COSBase rawAnnotations,
            PdfBoxAnnotationDecodePolicy.Budgets budgets,
            IdentityHashMap<COSDictionary, Integer> pageNumbers,
            Set<String> identifiers,
            Set<String> legacyIdentifiers) throws DocumentFailure {
        COSBase value = dereference(rawAnnotations);
        if (value == null) {
            return Collections.emptyList();
        }
        if (!(value instanceof COSArray)) {
            throw preservationUnsupported();
        }
        COSArray annotations = (COSArray) value;
        List<AnnotationSlot> slots = new ArrayList<AnnotationSlot>(
                annotations.size());
        for (int index = 0; index < annotations.size(); index++) {
            COSDictionary rawAnnotation = dictionary(annotations.get(index));
            Annotation managed = managedAnnotationOrNull(
                    rawAnnotation,
                    pageNumber,
                    page,
                    budgets,
                    pageNumbers);
            String identifier = managed == null
                    ? identifierOf(rawAnnotation, true)
                    : managed.getProperties().getIdentifier();
            if (identifier != null && !identifiers.add(identifier)) {
                throw preservationUnsupported();
            }
            if (managed == null && identifier != null
                    && legacyIdentifiers != null) {
                legacyIdentifiers.add(identifier);
            }
            slots.add(new AnnotationSlot(
                    managed,
                    managed == null ? identifier : null));
        }
        return slots;
    }

    CopyStructures snapshotCopyStructures(
            PDDocument source,
            PageRange range) throws DocumentFailure {
        List<COSBase> pageReferences = metadataOperations.rawPageReferences(
                source,
                PdfBoxMetadataOperations.StructureFailure.PRESERVE);
        IdentityHashMap<COSDictionary, Integer> pageNumbers =
                pageNumbers(pageReferences);
        Set<String> identifiers = new HashSet<String>();
        List<CopiedPageStructures> selected =
                new ArrayList<CopiedPageStructures>();
        PdfBoxAnnotationDecodePolicy.Budgets budgets =
                PdfBoxAnnotationDecodePolicy.newManagedGraphPass();

        for (int pageIndex = 0; pageIndex < pageReferences.size(); pageIndex++) {
            COSDictionary page = dictionary(pageReferences.get(pageIndex));
            List<AnnotationSlot> annotationSlots = captureAnnotationSlots(
                    page,
                    pageIndex + 1,
                    page.getItem(COSName.ANNOTS),
                    budgets,
                    pageNumbers,
                    identifiers,
                    null);
            if (pageIndex + 1 >= range.getFirstPageNumber()
                    && pageIndex + 1 <= range.getLastPageNumber()) {
                NavigationTarget open = null;
                NavigationTarget close = null;
                COSBase rawAa = dereference(page.getItem(AA));
                if (rawAa instanceof COSDictionary) {
                    COSDictionary aa = (COSDictionary) rawAa;
                    if (aa.getItem(O) != null) {
                        open = actionTarget(aa.getItem(O), pageNumbers);
                    }
                    if (aa.getItem(C) != null) {
                        close = actionTarget(aa.getItem(C), pageNumbers);
                    }
                }
                selected.add(new CopiedPageStructures(
                        annotationSlots,
                        open,
                        close));
            }
        }
        return new CopyStructures(
                range.getFirstPageNumber(),
                range.getLastPageNumber(),
                identifiers,
                selected);
    }

    private static COSArray requireImportedAnnotations(
            COSDictionary page,
            int expectedCount) throws DocumentFailure {
        COSBase rawAnnotations = dereference(
                page.getItem(COSName.ANNOTS));
        if (rawAnnotations == null && expectedCount == 0) {
            COSArray empty = new COSArray();
            empty.setDirect(true);
            return empty;
        }
        if (!(rawAnnotations instanceof COSArray)
                || ((COSArray) rawAnnotations).size() != expectedCount) {
            throw preservationUnsupported();
        }
        return (COSArray) rawAnnotations;
    }

    private static int legacySlotCount(List<AnnotationSlot> slots) {
        int count = 0;
        for (AnnotationSlot slot : slots) {
            if (!slot.isManaged()) {
                count++;
            }
        }
        return count;
    }

    void applyCopiedStructures(
            CopyStructures snapshot,
            int insertionPageNumber,
            int originalPageCount) throws DocumentFailure {
        List<COSBase> pageReferences = pageReferencesForCommand();
        int copiedPageCount = snapshot.pages.size();
        Set<String> identifiers = new HashSet<String>(snapshot.identifiers);
        for (int offset = 0; offset < copiedPageCount; offset++) {
            int newPageNumber = insertionPageNumber + offset;
            COSBase pageReference = pageReferences.get(newPageNumber - 1);
            COSDictionary page = dictionary(pageReference);
            CopiedPageStructures source = snapshot.pages.get(offset);
            COSArray imported = requireImportedAnnotations(
                    page,
                    source.annotationSlots.size());
            COSArray annotations = new COSArray();
            annotations.setDirect(true);
            for (int slotIndex = 0;
                    slotIndex < source.annotationSlots.size();
                    slotIndex++) {
                AnnotationSlot slot = source.annotationSlots.get(slotIndex);
                if (!slot.isManaged()) {
                    COSBase copiedLegacy = imported.get(slotIndex);
                    if (slot.legacyIdentifier != null) {
                        dictionary(copiedLegacy).setItem(
                                NM,
                                new COSString(copiedIdentifier(
                                        slot.legacyIdentifier,
                                        identifiers)));
                    }
                    annotations.add(copiedLegacy);
                } else {
                    Annotation annotation = slot.managedAnnotation;
                    String identifier = copiedIdentifier(
                            annotation.getProperties().getIdentifier(),
                            identifiers);
                    Annotation copied = copyAnnotation(
                            annotation,
                            identifier,
                            newPageNumber,
                            snapshot,
                            insertionPageNumber,
                            copiedPageCount,
                            originalPageCount);
                    annotations.add(new COSObject(backendAnnotation(
                            copied,
                            pageReference,
                            pageReferences)));
                }
            }
            if (annotations.size() == 0) {
                page.removeItem(COSName.ANNOTS);
            } else {
                page.setItem(COSName.ANNOTS, annotations);
            }

            COSDictionary aa = new COSDictionary();
            if (source.openTarget != null) {
                aa.setItem(O, backendAction(
                        copiedTarget(
                                source.openTarget,
                                snapshot,
                                insertionPageNumber,
                                copiedPageCount,
                                originalPageCount),
                        pageReferences));
            }
            if (source.closeTarget != null) {
                aa.setItem(C, backendAction(
                        copiedTarget(
                                source.closeTarget,
                                snapshot,
                                insertionPageNumber,
                                copiedPageCount,
                                originalPageCount),
                        pageReferences));
            }
            if (aa.size() == 0) {
                page.removeItem(AA);
            } else {
                page.setItem(AA, aa);
            }
        }
    }

    private static String copiedIdentifier(
            String original,
            Set<String> identifiers) {
        int suffix = 1;
        String candidate;
        do {
            candidate = original + "-" + suffix;
            suffix++;
        } while (!identifiers.add(candidate));
        return candidate;
    }

    private static Annotation copyAnnotation(
            Annotation source,
            String identifier,
            int pageNumber,
            CopyStructures snapshot,
            int insertionPageNumber,
            int copiedPageCount,
            int originalPageCount) throws DocumentFailure {
        AnnotationProperties properties = copiedProperties(
                source.getProperties(), identifier, pageNumber);
        NavigationTarget target = source.getLinkActivation().isPresent()
                ? copiedTarget(
                        source.getLinkActivation().get().getTarget(),
                        snapshot,
                        insertionPageNumber,
                        copiedPageCount,
                        originalPageCount)
                : null;
        return reconstructedAnnotation(source, properties, target);
    }

    private static AnnotationProperties copiedProperties(
            AnnotationProperties source,
            String identifier,
            int pageNumber) {
        AnnotationProperties.Builder builder = AnnotationProperties.version1(
                identifier,
                pageNumber,
                source.getRectangle());
        if (source.getContents().isPresent()) {
            builder.contents(source.getContents().get());
        }
        for (AnnotationFlag flag : source.getFlags()) {
            builder.flag(flag);
        }
        if (source.getAppearance().isPresent()) {
            builder.appearance(source.getAppearance().get());
        }
        return builder.build();
    }

    private static NavigationTarget copiedTarget(
            NavigationTarget source,
            CopyStructures snapshot,
            int insertionPageNumber,
            int copiedPageCount,
            int originalPageCount) throws DocumentFailure {
        if (source.getKind() == NavigationTarget.Kind.NAMED) {
            return source;
        }
        PageDestination destination = source.getPageDestination().get();
        int sourcePageNumber = destination.getPageNumber();
        int targetPageNumber;
        if (sourcePageNumber >= snapshot.firstPageNumber
                && sourcePageNumber <= snapshot.lastPageNumber) {
            targetPageNumber = insertionPageNumber
                    + sourcePageNumber - snapshot.firstPageNumber;
        } else if (insertionPageNumber <= sourcePageNumber) {
            targetPageNumber = sourcePageNumber + copiedPageCount;
        } else {
            targetPageNumber = sourcePageNumber;
        }
        if (sourcePageNumber > originalPageCount) {
            throw preservationUnsupported();
        }
        return NavigationTarget.toPage(
                destinationAtPage(destination, targetPageNumber));
    }

    private static PageDestination destinationAtPage(
            PageDestination source,
            int pageNumber) {
        List<BigDecimal> operands = source.getOperands();
        if (source.getStyle() == PageDestination.Style.FIT) {
            return PageDestination.fit(pageNumber);
        }
        if (source.getStyle() == PageDestination.Style.FIT_B) {
            return PageDestination.fitB(pageNumber);
        }
        if (source.getStyle() == PageDestination.Style.FIT_H) {
            return PageDestination.fitH(pageNumber, operands.get(0));
        }
        if (source.getStyle() == PageDestination.Style.FIT_BH) {
            return PageDestination.fitBH(pageNumber, operands.get(0));
        }
        if (source.getStyle() == PageDestination.Style.FIT_V) {
            return PageDestination.fitV(pageNumber, operands.get(0));
        }
        if (source.getStyle() == PageDestination.Style.FIT_BV) {
            return PageDestination.fitBV(pageNumber, operands.get(0));
        }
        if (source.getStyle() == PageDestination.Style.FIT_R) {
            return PageDestination.fitR(
                    pageNumber,
                    operands.get(0),
                    operands.get(1),
                    operands.get(2),
                    operands.get(3));
        }
        return PageDestination.xyz(
                pageNumber,
                operands.get(0),
                operands.get(1),
                operands.get(2));
    }

    MergeStructures extractAndStripMergeStructures(PDDocument source)
            throws DocumentFailure {
        List<COSBase> pageReferences = metadataOperations.rawPageReferences(
                source,
                PdfBoxMetadataOperations.StructureFailure.PRESERVE);
        IdentityHashMap<COSDictionary, Integer> pageNumbers =
                pageNumbers(pageReferences);
        List<MergedPageStructures> pages =
                new ArrayList<MergedPageStructures>();
        Set<String> identifiers = new HashSet<String>();
        Set<String> legacyIdentifiers = new HashSet<String>();
        PdfBoxAnnotationDecodePolicy.Budgets budgets =
                PdfBoxAnnotationDecodePolicy.newManagedGraphPass();

        for (int pageIndex = 0; pageIndex < pageReferences.size(); pageIndex++) {
            COSDictionary page = dictionary(pageReferences.get(pageIndex));
            List<AnnotationSlot> annotationSlots = captureAnnotationSlots(
                    page,
                    pageIndex + 1,
                    page.getItem(COSName.ANNOTS),
                    budgets,
                    pageNumbers,
                    identifiers,
                    legacyIdentifiers);

            NavigationTarget open = null;
            NavigationTarget close = null;
            COSBase rawAa = dereference(page.getItem(AA));
            if (rawAa instanceof COSDictionary) {
                COSDictionary aa = (COSDictionary) rawAa;
                if (aa.getItem(O) != null) {
                    open = actionTarget(aa.getItem(O), pageNumbers);
                }
                if (aa.getItem(C) != null) {
                    close = actionTarget(aa.getItem(C), pageNumbers);
                }
            }
            pages.add(new MergedPageStructures(
                    annotationSlots,
                    open,
                    close));
        }

        COSDictionary catalog = source.getDocumentCatalog().getCOSObject();
        NavigationTarget documentOpen = catalog.getItem(OPEN_ACTION) == null
                ? null
                : actionTarget(catalog.getItem(OPEN_ACTION), pageNumbers);

        catalog.removeItem(OPEN_ACTION);
        for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
            COSDictionary page = dictionary(pageReferences.get(pageIndex));
            MergedPageStructures captured = pages.get(pageIndex);
            COSArray sourceAnnotations = requireImportedAnnotations(
                    page,
                    captured.annotationSlots.size());
            COSArray legacyAnnotations = new COSArray();
            legacyAnnotations.setDirect(true);
            for (int slotIndex = 0;
                    slotIndex < captured.annotationSlots.size();
                    slotIndex++) {
                if (!captured.annotationSlots.get(slotIndex).isManaged()) {
                    legacyAnnotations.add(sourceAnnotations.get(slotIndex));
                }
            }
            if (legacyAnnotations.size() == 0) {
                page.removeItem(COSName.ANNOTS);
            } else {
                page.setItem(COSName.ANNOTS, legacyAnnotations);
            }
            page.removeItem(AA);
        }
        return new MergeStructures(
                pageReferences.size(),
                pages,
                documentOpen,
                legacyIdentifiers);
    }

    void requireMergeIdentifiersSafe(List<MergeStructures> sources)
            throws DocumentFailure {
        Set<String> identifiers = existingIdentifiers(
                pageReferencesForCommand());
        for (MergeStructures source : sources) {
            for (String identifier : source.legacyIdentifiers) {
                if (!identifiers.add(identifier)) {
                    throw preservationUnsupported();
                }
            }
        }
    }

    void applyMergedStructures(
            List<MergeStructures> sources,
            int originalPageCount,
            List<Map<String, String>> destinationRenames)
            throws DocumentFailure {
        if (sources.size() != destinationRenames.size()) {
            throw preservationUnsupported();
        }
        List<COSBase> pageReferences = pageReferencesForCommand();
        Set<String> identifiers = existingIdentifiers(pageReferences);
        COSDictionary catalog = document.getDocumentCatalog().getCOSObject();
        boolean hasDocumentOpen = catalog.getItem(OPEN_ACTION) != null;
        int pageBase = originalPageCount;

        for (int sourceIndex = 0;
                sourceIndex < sources.size();
                sourceIndex++) {
            MergeStructures source = sources.get(sourceIndex);
            Map<String, String> renames = destinationRenames.get(sourceIndex);
            for (int pageIndex = 0;
                    pageIndex < source.pages.size();
                    pageIndex++) {
                int targetPageNumber = pageBase + pageIndex + 1;
                COSBase pageReference = pageReferences.get(
                        targetPageNumber - 1);
                COSDictionary page = dictionary(pageReference);
                MergedPageStructures captured = source.pages.get(pageIndex);
                COSArray imported = requireImportedAnnotations(
                        page,
                        legacySlotCount(captured.annotationSlots));
                int legacyIndex = 0;
                COSArray annotations = new COSArray();
                annotations.setDirect(true);
                for (AnnotationSlot slot : captured.annotationSlots) {
                    if (!slot.isManaged()) {
                        annotations.add(imported.get(legacyIndex));
                        legacyIndex++;
                    } else {
                        Annotation annotation = slot.managedAnnotation;
                        String identifier = availableIdentifier(
                                annotation.getProperties().getIdentifier(),
                                identifiers);
                        Annotation merged = mergedAnnotation(
                                annotation,
                                identifier,
                                targetPageNumber,
                                pageBase,
                                source.pageCount,
                                renames);
                        annotations.add(new COSObject(backendAnnotation(
                                merged,
                                pageReference,
                                pageReferences)));
                    }
                }
                if (annotations.size() == 0) {
                    page.removeItem(COSName.ANNOTS);
                } else {
                    page.setItem(COSName.ANNOTS, annotations);
                }

                COSDictionary aa = new COSDictionary();
                if (captured.openTarget != null) {
                    aa.setItem(O, backendAction(
                            mergedTarget(
                                    captured.openTarget,
                                    pageBase,
                                    source.pageCount,
                                    renames),
                            pageReferences));
                }
                if (captured.closeTarget != null) {
                    aa.setItem(C, backendAction(
                            mergedTarget(
                                    captured.closeTarget,
                                    pageBase,
                                    source.pageCount,
                                    renames),
                            pageReferences));
                }
                if (aa.size() == 0) {
                    page.removeItem(AA);
                } else {
                    page.setItem(AA, aa);
                }
            }

            if (!hasDocumentOpen && source.documentOpenTarget != null) {
                catalog.setItem(OPEN_ACTION, backendAction(
                        mergedTarget(
                                source.documentOpenTarget,
                                pageBase,
                                source.pageCount,
                                renames),
                        pageReferences));
                hasDocumentOpen = true;
            }
            pageBase += source.pageCount;
        }
    }

    private static Set<String> existingIdentifiers(
            List<COSBase> pageReferences) throws DocumentFailure {
        Set<String> identifiers = new HashSet<String>();
        for (COSBase pageReference : pageReferences) {
            COSDictionary page = dictionary(pageReference);
            COSBase rawAnnotations = dereference(
                    page.getItem(COSName.ANNOTS));
            if (!(rawAnnotations instanceof COSArray)) {
                continue;
            }
            COSArray array = (COSArray) rawAnnotations;
            for (int index = 0; index < array.size(); index++) {
                String identifier = identifierOf(
                        dictionary(array.get(index)),
                        true);
                if (identifier != null && !identifiers.add(identifier)) {
                    throw preservationUnsupported();
                }
            }
        }
        return identifiers;
    }

    private static String availableIdentifier(
            String preferred,
            Set<String> identifiers) {
        if (identifiers.add(preferred)) {
            return preferred;
        }
        return copiedIdentifier(preferred, identifiers);
    }

    private static Annotation mergedAnnotation(
            Annotation source,
            String identifier,
            int pageNumber,
            int pageBase,
            int sourcePageCount,
            Map<String, String> renames) throws DocumentFailure {
        AnnotationProperties properties = copiedProperties(
                source.getProperties(), identifier, pageNumber);
        NavigationTarget target = source.getLinkActivation().isPresent()
                ? mergedTarget(
                        source.getLinkActivation().get().getTarget(),
                        pageBase,
                        sourcePageCount,
                        renames)
                : null;
        return reconstructedAnnotation(source, properties, target);
    }

    private static NavigationTarget mergedTarget(
            NavigationTarget source,
            int pageBase,
            int sourcePageCount,
            Map<String, String> renames) throws DocumentFailure {
        if (source.getKind() == NavigationTarget.Kind.NAMED) {
            String renamed = renames.get(
                    source.getNamedDestination().get());
            if (renamed == null) {
                throw preservationUnsupported();
            }
            return NavigationTarget.toNamedDestination(renamed);
        }
        PageDestination destination = source.getPageDestination().get();
        if (destination.getPageNumber() > sourcePageCount) {
            throw preservationUnsupported();
        }
        return NavigationTarget.toPage(destinationAtPage(
                destination,
                pageBase + destination.getPageNumber()));
    }

    MergeStructures snapshotSplitStructures(PDDocument source)
            throws DocumentFailure {
        List<COSBase> pageReferences = metadataOperations.rawPageReferences(
                source,
                PdfBoxMetadataOperations.StructureFailure.PRESERVE);
        List<COSBase> annotations = new ArrayList<COSBase>();
        List<COSBase> actions = new ArrayList<COSBase>();
        for (COSBase pageReference : pageReferences) {
            COSDictionary page = dictionary(pageReference);
            annotations.add(page.getItem(COSName.ANNOTS));
            actions.add(page.getItem(AA));
        }
        COSDictionary catalog = source.getDocumentCatalog().getCOSObject();
        COSBase openAction = catalog.getItem(OPEN_ACTION);
        MergeStructures snapshot = extractAndStripMergeStructures(source);
        if (openAction == null) {
            catalog.removeItem(OPEN_ACTION);
        } else {
            catalog.setItem(OPEN_ACTION, openAction);
        }
        for (int index = 0; index < pageReferences.size(); index++) {
            COSDictionary page = dictionary(pageReferences.get(index));
            if (annotations.get(index) == null) {
                page.removeItem(COSName.ANNOTS);
            } else {
                page.setItem(COSName.ANNOTS, annotations.get(index));
            }
            if (actions.get(index) == null) {
                page.removeItem(AA);
            } else {
                page.setItem(AA, actions.get(index));
            }
        }
        return snapshot;
    }

    void retargetSplitStructures(
            PDDocument product,
            MergeStructures snapshot,
            int[] mapping) throws DocumentFailure {
        PdfBoxMetadataOperations productMetadata =
                new PdfBoxMetadataOperations(product);
        PdfBoxAnnotationOperations productOperations =
                new PdfBoxAnnotationOperations(product, productMetadata);
        PdfBoxAnnotationPageOperations productPageOperations =
                new PdfBoxAnnotationPageOperations(
                        product,
                        productMetadata,
                        productOperations);
        productPageOperations.applySplitStructures(
                snapshot,
                mapping,
                productMetadata.namedDestinationNames(product));
    }

    private void applySplitStructures(
            MergeStructures snapshot,
            int[] mapping,
            Set<String> namedDestinations) throws DocumentFailure {
        if (mapping.length != snapshot.pageCount) {
            throw preservationUnsupported();
        }
        List<COSBase> pageReferences = pageReferencesForCommand();
        for (int sourcePageIndex = 0;
                sourcePageIndex < snapshot.pages.size();
                sourcePageIndex++) {
            int mappedPageNumber = mapping[sourcePageIndex];
            if (mappedPageNumber == 0) {
                continue;
            }
            COSBase pageReference = pageReferences.get(mappedPageNumber - 1);
            COSDictionary page = dictionary(pageReference);
            MergedPageStructures source = snapshot.pages.get(sourcePageIndex);
            COSArray imported = requireImportedAnnotations(
                    page,
                    source.annotationSlots.size());
            COSArray annotations = new COSArray();
            annotations.setDirect(true);
            for (int slotIndex = 0;
                    slotIndex < source.annotationSlots.size();
                    slotIndex++) {
                AnnotationSlot slot = source.annotationSlots.get(slotIndex);
                if (!slot.isManaged()) {
                    annotations.add(imported.get(slotIndex));
                } else {
                    Annotation annotation = slot.managedAnnotation;
                    NavigationTarget target = null;
                    if (annotation.getLinkActivation().isPresent()) {
                        target = splitTarget(
                                annotation.getLinkActivation().get()
                                        .getTarget(),
                                mapping,
                                namedDestinations);
                        if (target == null) {
                            continue;
                        }
                    }
                    Annotation filtered = splitAnnotation(
                            annotation,
                            mappedPageNumber,
                            target);
                    annotations.add(new COSObject(backendAnnotation(
                            filtered,
                            pageReference,
                            pageReferences)));
                }
            }
            if (annotations.size() == 0) {
                page.removeItem(COSName.ANNOTS);
            } else {
                page.setItem(COSName.ANNOTS, annotations);
            }

            COSDictionary aa = new COSDictionary();
            NavigationTarget open = source.openTarget == null
                    ? null
                    : splitTarget(
                            source.openTarget,
                            mapping,
                            namedDestinations);
            NavigationTarget close = source.closeTarget == null
                    ? null
                    : splitTarget(
                            source.closeTarget,
                            mapping,
                            namedDestinations);
            if (open != null) {
                aa.setItem(O, backendAction(open, pageReferences));
            }
            if (close != null) {
                aa.setItem(C, backendAction(close, pageReferences));
            }
            if (aa.size() == 0) {
                page.removeItem(AA);
            } else {
                page.setItem(AA, aa);
            }
        }

        COSDictionary catalog = document.getDocumentCatalog().getCOSObject();
        NavigationTarget documentOpen = snapshot.documentOpenTarget == null
                ? null
                : splitTarget(
                        snapshot.documentOpenTarget,
                        mapping,
                        namedDestinations);
        if (documentOpen == null) {
            catalog.removeItem(OPEN_ACTION);
        } else {
            catalog.setItem(OPEN_ACTION,
                    backendAction(documentOpen, pageReferences));
        }
    }

    private static NavigationTarget splitTarget(
            NavigationTarget source,
            int[] mapping,
            Set<String> namedDestinations) throws DocumentFailure {
        if (source.getKind() == NavigationTarget.Kind.NAMED) {
            return namedDestinations.contains(
                    source.getNamedDestination().get())
                    ? source
                    : null;
        }
        PageDestination destination = source.getPageDestination().get();
        if (destination.getPageNumber() > mapping.length) {
            throw preservationUnsupported();
        }
        int mappedPageNumber = mapping[destination.getPageNumber() - 1];
        return mappedPageNumber == 0
                ? null
                : NavigationTarget.toPage(destinationAtPage(
                        destination,
                        mappedPageNumber));
    }

    private static Annotation splitAnnotation(
            Annotation source,
            int pageNumber,
            NavigationTarget linkTarget) throws DocumentFailure {
        AnnotationProperties properties = copiedProperties(
                source.getProperties(),
                source.getProperties().getIdentifier(),
                pageNumber);
        return reconstructedAnnotation(source, properties, linkTarget);
    }

    private static Annotation reconstructedAnnotation(
            Annotation source,
            AnnotationProperties properties,
            NavigationTarget linkTarget) throws DocumentFailure {
        switch (source.getType()) {
            case TEXT:
                return Annotation.text(
                        properties,
                        source.getTextIcon().get(),
                        source.isOpen());
            case STAMP:
                return Annotation.stamp(
                        properties,
                        source.getStampName().get());
            case HIGHLIGHT:
                return Annotation.highlight(
                        properties,
                        source.getQuads(),
                        source.getColor().get());
            case FILE_ATTACHMENT:
                return Annotation.fileAttachment(
                        properties,
                        source.getAttachment().get(),
                        source.getFileAttachmentIcon().get());
            case WIDGET:
                return Annotation.widget(properties);
            case LINK:
                break;
            default:
                throw preservationUnsupported();
        }
        if (linkTarget == null) {
            throw preservationUnsupported();
        }
        LinkActivation activation = source.getLinkActivation().get();
        return Annotation.link(
                properties,
                activation.getKind() == LinkActivation.Kind.DESTINATION
                        ? LinkActivation.destination(linkTarget)
                        : LinkActivation.action(
                                GoToAction.version1(linkTarget)));
    }

    private static IdentityHashMap<COSDictionary, Integer> pageNumbers(
            List<COSBase> pageReferences) throws DocumentFailure {
        IdentityHashMap<COSDictionary, Integer> pageNumbers =
                new IdentityHashMap<COSDictionary, Integer>();
        for (int index = 0; index < pageReferences.size(); index++) {
            pageNumbers.put(dictionary(pageReferences.get(index)),
                    Integer.valueOf(index + 1));
        }
        return pageNumbers;
    }

    static final class CopyStructures {

        final int firstPageNumber;
        final int lastPageNumber;
        final Set<String> identifiers;
        final List<CopiedPageStructures> pages;

        CopyStructures(
                int firstPageNumber,
                int lastPageNumber,
                Set<String> identifiers,
                List<CopiedPageStructures> pages) {
            this.firstPageNumber = firstPageNumber;
            this.lastPageNumber = lastPageNumber;
            this.identifiers = new HashSet<String>(identifiers);
            this.pages = new ArrayList<CopiedPageStructures>(pages);
        }
    }

    private static final class CopiedPageStructures {

        private final List<AnnotationSlot> annotationSlots;
        private final NavigationTarget openTarget;
        private final NavigationTarget closeTarget;

        CopiedPageStructures(
                List<AnnotationSlot> annotationSlots,
                NavigationTarget openTarget,
                NavigationTarget closeTarget) {
            this.annotationSlots = new ArrayList<AnnotationSlot>(
                    annotationSlots);
            this.openTarget = openTarget;
            this.closeTarget = closeTarget;
        }
    }

    static final class MergeStructures {

        final int pageCount;
        final List<MergedPageStructures> pages;
        final NavigationTarget documentOpenTarget;
        final Set<String> legacyIdentifiers;

        MergeStructures(
                int pageCount,
                List<MergedPageStructures> pages,
                NavigationTarget documentOpenTarget,
                Set<String> legacyIdentifiers) {
            this.pageCount = pageCount;
            this.pages = new ArrayList<MergedPageStructures>(pages);
            this.documentOpenTarget = documentOpenTarget;
            this.legacyIdentifiers = new HashSet<String>(legacyIdentifiers);
        }
    }

    private static final class MergedPageStructures {

        private final List<AnnotationSlot> annotationSlots;
        private final NavigationTarget openTarget;
        private final NavigationTarget closeTarget;

        MergedPageStructures(
                List<AnnotationSlot> annotationSlots,
                NavigationTarget openTarget,
                NavigationTarget closeTarget) {
            this.annotationSlots = new ArrayList<AnnotationSlot>(
                    annotationSlots);
            this.openTarget = openTarget;
            this.closeTarget = closeTarget;
        }
    }

    private static final class AnnotationSlot {

        private final Annotation managedAnnotation;
        private final String legacyIdentifier;

        AnnotationSlot(
                Annotation managedAnnotation,
                String legacyIdentifier) {
            this.managedAnnotation = managedAnnotation;
            this.legacyIdentifier = legacyIdentifier;
        }

        boolean isManaged() {
            return managedAnnotation != null;
        }
    }

    private Annotation publicAnnotation(
            COSDictionary dictionary,
            int pageNumber,
            COSDictionary page,
            ByteBudget appearances,
            ByteBudget attachments,
            IdentityHashMap<COSDictionary, Integer> pageNumbers)
            throws DocumentFailure {
        return annotationOperations.publicAnnotation(
                dictionary,
                pageNumber,
                page,
                appearances,
                attachments,
                pageNumbers);
    }

    private COSDictionary backendAnnotation(
            Annotation annotation,
            COSBase pageReference,
            List<COSBase> pageReferences) throws DocumentFailure {
        return annotationOperations.backendAnnotation(
                annotation,
                pageReference,
                pageReferences);
    }

    private NavigationTarget actionTarget(
            COSBase raw,
            IdentityHashMap<COSDictionary, Integer> pageNumbers)
            throws DocumentFailure {
        return annotationOperations.actionTarget(raw, pageNumbers);
    }

    private COSDictionary backendAction(
            NavigationTarget target,
            List<COSBase> pageReferences) throws DocumentFailure {
        return annotationOperations.backendAction(target, pageReferences);
    }

    private List<COSBase> pageReferencesForCommand()
            throws DocumentFailure {
        return annotationOperations.pageReferencesForCommand();
    }

    private static COSDictionary dictionary(COSBase raw)
            throws DocumentFailure {
        return PdfBoxAnnotationOperations.dictionary(raw);
    }

    private static void requireOnlyKeys(
            COSDictionary dictionary,
            String... names) throws DocumentFailure {
        PdfBoxAnnotationOperations.requireOnlyKeys(dictionary, names);
    }

    private static COSBase dereference(COSBase value) {
        return PdfBoxAnnotationOperations.dereference(value);
    }

    private static String identifierOf(
            COSDictionary dictionary,
            boolean optional) throws DocumentFailure {
        return PdfBoxAnnotationOperations.identifierOf(dictionary, optional);
    }

    private static DocumentFailure preservationUnsupported() {
        return PdfBoxAnnotationOperations.preservationUnsupported();
    }

    private static DocumentFailure destinationConflict() {
        return PdfBoxAnnotationOperations.destinationConflict();
    }
}
