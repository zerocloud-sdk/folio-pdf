package net.zerocloud.pdf;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.zerocloud.pdf.PdfBoxAnnotationOperations.ByteBudget;
import net.zerocloud.pdf.command.FlattenAnnotations;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;

/** Incorporates validated non-form annotation appearances into page content. */
final class PdfBoxAnnotationFlattener {

    private static final COSName AP = COSName.getPDFName("AP");
    private static final COSName N = COSName.getPDFName("N");
    private final PDDocument document;
    private final PdfBoxAnnotationOperations annotationOperations;
    private final WorkflowResourceContext resources;

    PdfBoxAnnotationFlattener(
            PDDocument document,
            PdfBoxAnnotationOperations annotationOperations,
            WorkflowResourceContext resources) {
        this.document = document;
        this.annotationOperations = annotationOperations;
        this.resources = resources;
    }

    void flatten(FlattenAnnotations command) throws DocumentFailure {
        List<Annotation> decodedAnnotations = new ArrayList<Annotation>();
        try {
            flatten(command, decodedAnnotations);
        } finally {
            for (Annotation annotation : decodedAnnotations) {
                annotationOperations.releaseAnnotationBytes(annotation);
            }
        }
    }

    private void flatten(
            FlattenAnnotations command,
            List<Annotation> decodedAnnotations) throws DocumentFailure {
        resources.checkpoint();
        List<COSBase> pageReferences = pageReferencesForCommand();
        IdentityHashMap<COSDictionary, Integer> pageNumbers =
                new IdentityHashMap<COSDictionary, Integer>();
        for (int index = 0; index < pageReferences.size(); index++) {
            resources.checkpoint();
            pageNumbers.put(dictionary(pageReferences.get(index)),
                    Integer.valueOf(index + 1));
        }
        Set<String> selected = new HashSet<String>(command.getIdentifiers());
        Map<String, FlattenTarget> targets =
                new LinkedHashMap<String, FlattenTarget>();
        PdfBoxAnnotationDecodePolicy.Budgets budgets =
                PdfBoxAnnotationDecodePolicy.newManagedGraphPass();

        for (int pageIndex = 0; pageIndex < pageReferences.size(); pageIndex++) {
            resources.checkpoint();
            COSDictionary page = dictionary(pageReferences.get(pageIndex));
            COSBase rawAnnotations = dereference(page.getItem(COSName.ANNOTS));
            if (rawAnnotations == null) {
                continue;
            }
            if (!(rawAnnotations instanceof COSArray)) {
                throw flatteningUnsupported();
            }
            COSArray array = (COSArray) rawAnnotations;
            for (int index = 0; index < array.size(); index++) {
                resources.checkpoint();
                COSDictionary raw;
                Annotation annotation;
                try {
                    raw = dictionary(array.get(index));
                    annotation = publicAnnotation(raw,
                            pageIndex + 1, page,
                            budgets.appearances(), budgets.attachments(),
                            pageNumbers);
                    decodedAnnotations.add(annotation);
                } catch (DocumentFailure invalid) {
                    resources.rethrowTerminalFailure();
                    throw flatteningUnsupported();
                }
                String identifier = annotation.getProperties().getIdentifier();
                if (selected.contains(identifier)) {
                    if (targets.put(identifier,
                            new FlattenTarget(pageIndex, raw, annotation))
                            != null) {
                        throw flatteningUnsupported();
                    }
                }
            }
        }
        if (!targets.keySet().containsAll(selected)) {
            throw annotationNotFound();
        }
        for (FlattenTarget target : targets.values()) {
            resources.checkpoint();
            if (target.annotation.getType() == Annotation.Type.WIDGET
                    || !target.annotation.getProperties()
                            .getAppearance().isPresent()) {
                throw flatteningUnsupported();
            }
        }

        Map<Integer, FlattenPageChange> changes =
                new LinkedHashMap<Integer, FlattenPageChange>();
        for (FlattenTarget target : targets.values()) {
            resources.checkpoint();
            Integer pageIndex = Integer.valueOf(target.pageIndex);
            FlattenPageChange change = changes.get(pageIndex);
            if (change == null) {
                change = flattenPageChange(target.pageIndex, pageReferences,
                        selected);
                changes.put(pageIndex, change);
            }
            addFlattenedAppearance(change, target);
        }

        for (FlattenPageChange change : changes.values()) {
            resources.checkpoint();
            change.page.setItem(COSName.RESOURCES, change.resources);
            change.page.setItem(COSName.CONTENTS, change.contents);
        }
        for (FlattenPageChange change : changes.values()) {
            resources.checkpoint();
            if (change.annotations.size() == 0) {
                change.page.removeItem(COSName.ANNOTS);
            } else {
                change.page.setItem(COSName.ANNOTS, change.annotations);
            }
        }
    }

    private FlattenPageChange flattenPageChange(
            int pageIndex,
            List<COSBase> pageReferences,
            Set<String> selected) throws DocumentFailure {
        COSDictionary page = dictionary(pageReferences.get(pageIndex));
        COSArray annotations = new COSArray();
        annotations.setDirect(true);
        COSBase rawAnnotations = dereference(page.getItem(COSName.ANNOTS));
        if (!(rawAnnotations instanceof COSArray)) {
            throw flatteningUnsupported();
        }
        COSArray existingAnnotations = (COSArray) rawAnnotations;
        for (int index = 0; index < existingAnnotations.size(); index++) {
            resources.checkpoint();
            COSBase raw = existingAnnotations.get(index);
            COSDictionary annotation;
            String identifier;
            try {
                annotation = dictionary(raw);
                identifier = identifierOf(annotation, false);
            } catch (DocumentFailure invalid) {
                throw flatteningUnsupported();
            }
            if (!selected.contains(identifier)) {
                annotations.add(raw);
            }
        }

        COSDictionary resources = effectiveResources(pageIndex, page);
        COSDictionary xObjects = new COSDictionary();
        xObjects.setDirect(true);
        COSBase rawXObjects = dereference(resources.getItem(COSName.XOBJECT));
        if (rawXObjects != null) {
            if (!(rawXObjects instanceof COSDictionary)
                    || rawXObjects instanceof COSStream) {
                throw flatteningUnsupported();
            }
            copyEntries((COSDictionary) rawXObjects, xObjects);
        }
        resources.setItem(COSName.XOBJECT, xObjects);

        COSArray contents = new COSArray();
        contents.setDirect(true);
        contents.add(contentStream(new byte[] {'q', '\n'}));
        COSBase rawContents = page.getItem(COSName.CONTENTS);
        if (rawContents != null) {
            COSBase contentValue = dereference(rawContents);
            if (contentValue instanceof COSStream) {
                contents.add(rawContents);
            } else if (contentValue instanceof COSArray) {
                COSArray existing = (COSArray) contentValue;
                for (int index = 0; index < existing.size(); index++) {
                    this.resources.checkpoint();
                    if (!(dereference(existing.get(index)) instanceof COSStream)) {
                        throw flatteningUnsupported();
                    }
                    contents.add(existing.get(index));
                }
            } else {
                throw flatteningUnsupported();
            }
        }
        contents.add(contentStream(new byte[] {'Q', '\n'}));
        return new FlattenPageChange(page, annotations,
                resources, xObjects, contents);
    }

    private COSDictionary effectiveResources(
            int pageIndex,
            COSDictionary page) throws DocumentFailure {
        COSBase rawResources = dereference(page.getItem(COSName.RESOURCES));
        if (rawResources == null) {
            PDPage backendPage = document.getPage(pageIndex);
            PDResources inherited = backendPage.getResources();
            rawResources = inherited == null ? null : inherited.getCOSObject();
        }
        if (rawResources != null
                && (!(rawResources instanceof COSDictionary)
                        || rawResources instanceof COSStream)) {
            throw flatteningUnsupported();
        }
        COSDictionary copied = new COSDictionary();
        copied.setDirect(true);
        if (rawResources != null) {
            copyEntries((COSDictionary) rawResources, copied);
        }
        return copied;
    }

    private void copyEntries(COSDictionary source, COSDictionary target)
            throws DocumentFailure {
        for (COSName name : source.keySet()) {
            resources.checkpoint();
            target.setItem(name, source.getItem(name));
        }
    }

    private void addFlattenedAppearance(
            FlattenPageChange change,
            FlattenTarget target) throws DocumentFailure {
        resources.checkpoint();
        COSStream appearance = normalAppearanceStream(target.dictionary);
        String resourceName = availableAppearanceName(change.xObjects);
        change.xObjects.setItem(COSName.getPDFName(resourceName), appearance);

        AnnotationRectangle rectangle =
                target.annotation.getProperties().getRectangle();
        AnnotationRectangle box = target.annotation.getProperties()
                .getAppearance().get().getBoundingBox();
        BigDecimal boxWidth = box.getRight().subtract(box.getLeft());
        BigDecimal boxHeight = box.getTop().subtract(box.getBottom());
        BigDecimal scaleX = rectangle.getRight().subtract(rectangle.getLeft())
                .divide(boxWidth, MathContext.DECIMAL128);
        BigDecimal scaleY = rectangle.getTop().subtract(rectangle.getBottom())
                .divide(boxHeight, MathContext.DECIMAL128);
        BigDecimal translateX = rectangle.getLeft().subtract(
                box.getLeft().multiply(scaleX));
        BigDecimal translateY = rectangle.getBottom().subtract(
                box.getBottom().multiply(scaleY));
        try (WorkflowAsciiOutput operators = new WorkflowAsciiOutput(
                resources,
                Integer.MAX_VALUE - 8L,
                PdfBoxAnnotationFlattener::flatteningUnsupported)) {
            operators.append("q\n");
            operators.append(scaleX);
            operators.append(" 0 0 ");
            operators.append(scaleY);
            operators.append(' ');
            operators.append(translateX);
            operators.append(' ');
            operators.append(translateY);
            operators.append(" cm\n/");
            operators.append(resourceName);
            operators.append(" Do\nQ\n");
            try (WorkflowResourceContext.OwnedBytes content =
                    operators.finishWorking()) {
                change.contents.add(contentStream(content.getBytes()));
            }
        }
    }

    private COSObject contentStream(byte[] operators) throws DocumentFailure {
        try {
            COSStream content = document.getDocument().createCOSStream();
            try (OutputStream output = content.createOutputStream()) {
                for (int offset = 0;
                        offset < operators.length;
                        offset += 8192) {
                    resources.checkpoint();
                    int length = Math.min(8192, operators.length - offset);
                    output.write(operators, offset, length);
                }
                resources.checkpoint();
            }
            return new COSObject(content);
        } catch (IOException | RuntimeException failure) {
            resources.rethrowResourceOrTerminalFailure(failure);
            throw flatteningUnsupported();
        }
    }

    private static COSStream normalAppearanceStream(COSDictionary annotation)
            throws DocumentFailure {
        COSBase appearances = dereference(annotation.getItem(AP));
        if (!(appearances instanceof COSDictionary)
                || appearances instanceof COSStream) {
            throw flatteningUnsupported();
        }
        COSBase normal = dereference(((COSDictionary) appearances).getItem(N));
        if (!(normal instanceof COSStream)) {
            throw flatteningUnsupported();
        }
        return (COSStream) normal;
    }

    private String availableAppearanceName(COSDictionary xObjects)
            throws DocumentFailure {
        int suffix = 1;
        while (xObjects.containsKey(COSName.getPDFName(
                "FolioT12" + suffix))) {
            resources.checkpoint();
            suffix++;
        }
        return "FolioT12" + suffix;
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

    private List<COSBase> pageReferencesForCommand()
            throws DocumentFailure {
        return annotationOperations.pageReferencesForCommand();
    }

    private static COSDictionary dictionary(COSBase raw)
            throws DocumentFailure {
        return PdfBoxAnnotationOperations.dictionary(raw);
    }

    private static COSBase dereference(COSBase value) {
        return PdfBoxAnnotationOperations.dereference(value);
    }

    private static String identifierOf(
            COSDictionary dictionary,
            boolean optional) throws DocumentFailure {
        return PdfBoxAnnotationOperations.identifierOf(dictionary, optional);
    }

    private static DocumentFailure annotationNotFound() {
        return PdfBoxAnnotationOperations.annotationNotFound();
    }

    private static DocumentFailure flatteningUnsupported() {
        return PdfBoxAnnotationOperations.flatteningUnsupported();
    }

    private static final class FlattenTarget {

        private final int pageIndex;
        private final COSDictionary dictionary;
        private final Annotation annotation;

        FlattenTarget(
                int pageIndex,
                COSDictionary dictionary,
                Annotation annotation) {
            this.pageIndex = pageIndex;
            this.dictionary = dictionary;
            this.annotation = annotation;
        }
    }

    private static final class FlattenPageChange {

        private final COSDictionary page;
        private final COSArray annotations;
        private final COSDictionary resources;
        private final COSDictionary xObjects;
        private final COSArray contents;

        FlattenPageChange(
                COSDictionary page,
                COSArray annotations,
                COSDictionary resources,
                COSDictionary xObjects,
                COSArray contents) {
            this.page = page;
            this.annotations = annotations;
            this.resources = resources;
            this.xObjects = xObjects;
            this.contents = contents;
        }
    }
}
