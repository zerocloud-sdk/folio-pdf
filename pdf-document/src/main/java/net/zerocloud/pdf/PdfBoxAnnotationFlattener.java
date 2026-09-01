package net.zerocloud.pdf;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
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

    PdfBoxAnnotationFlattener(
            PDDocument document,
            PdfBoxAnnotationOperations annotationOperations) {
        this.document = document;
        this.annotationOperations = annotationOperations;
    }

    void flatten(FlattenAnnotations command) throws DocumentFailure {
        List<COSBase> pageReferences = pageReferencesForCommand();
        IdentityHashMap<COSDictionary, Integer> pageNumbers =
                new IdentityHashMap<COSDictionary, Integer>();
        for (int index = 0; index < pageReferences.size(); index++) {
            pageNumbers.put(dictionary(pageReferences.get(index)),
                    Integer.valueOf(index + 1));
        }
        Set<String> selected = new HashSet<String>(command.getIdentifiers());
        Map<String, FlattenTarget> targets =
                new LinkedHashMap<String, FlattenTarget>();
        PdfBoxAnnotationDecodePolicy.Budgets budgets =
                PdfBoxAnnotationDecodePolicy.newManagedGraphPass();

        for (int pageIndex = 0; pageIndex < pageReferences.size(); pageIndex++) {
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
                COSDictionary raw;
                Annotation annotation;
                try {
                    raw = dictionary(array.get(index));
                    annotation = publicAnnotation(raw,
                            pageIndex + 1, page,
                            budgets.appearances(), budgets.attachments(),
                            pageNumbers);
                } catch (DocumentFailure invalid) {
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
            if (target.annotation.getType() == Annotation.Type.WIDGET
                    || !target.annotation.getProperties()
                            .getAppearance().isPresent()) {
                throw flatteningUnsupported();
            }
        }

        Map<Integer, FlattenPageChange> changes =
                new LinkedHashMap<Integer, FlattenPageChange>();
        for (FlattenTarget target : targets.values()) {
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
            change.page.setItem(COSName.RESOURCES, change.resources);
            change.page.setItem(COSName.CONTENTS, change.contents);
        }
        for (FlattenPageChange change : changes.values()) {
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
            xObjects.addAll((COSDictionary) rawXObjects);
        }
        resources.setItem(COSName.XOBJECT, xObjects);

        COSArray contents = new COSArray();
        contents.setDirect(true);
        contents.add(contentStream("q\n"));
        COSBase rawContents = page.getItem(COSName.CONTENTS);
        if (rawContents != null) {
            COSBase contentValue = dereference(rawContents);
            if (contentValue instanceof COSStream) {
                contents.add(rawContents);
            } else if (contentValue instanceof COSArray) {
                COSArray existing = (COSArray) contentValue;
                for (int index = 0; index < existing.size(); index++) {
                    if (!(dereference(existing.get(index)) instanceof COSStream)) {
                        throw flatteningUnsupported();
                    }
                    contents.add(existing.get(index));
                }
            } else {
                throw flatteningUnsupported();
            }
        }
        contents.add(contentStream("Q\n"));
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
            copied.addAll((COSDictionary) rawResources);
        }
        return copied;
    }

    private void addFlattenedAppearance(
            FlattenPageChange change,
            FlattenTarget target) throws DocumentFailure {
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
        String operators = "q\n"
                + scaleX.toPlainString() + " 0 0 "
                + scaleY.toPlainString() + " "
                + translateX.toPlainString() + " "
                + translateY.toPlainString() + " cm\n/"
                + resourceName + " Do\nQ\n";
        change.contents.add(contentStream(operators));
    }

    private COSObject contentStream(String operators) throws DocumentFailure {
        try {
            COSStream content = document.getDocument().createCOSStream();
            try (OutputStream output = content.createOutputStream()) {
                output.write(operators.getBytes(StandardCharsets.US_ASCII));
            }
            return new COSObject(content);
        } catch (IOException | RuntimeException failure) {
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

    private static String availableAppearanceName(COSDictionary xObjects) {
        int suffix = 1;
        while (xObjects.containsKey(COSName.getPDFName(
                "FolioT12" + suffix))) {
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
