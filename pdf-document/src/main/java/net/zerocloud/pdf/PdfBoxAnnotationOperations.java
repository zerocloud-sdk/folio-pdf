package net.zerocloud.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.zerocloud.pdf.command.UpdateAnnotations;
import net.zerocloud.pdf.command.UpdateActions;
import net.zerocloud.pdf.command.FlattenAnnotations;
import net.zerocloud.pdf.query.Actions;
import net.zerocloud.pdf.query.Annotations;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSBoolean;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;

/** PDFBox-hidden implementation of the T12 annotation and Action seam. */
final class PdfBoxAnnotationOperations {

    static final String CAPABILITY_ID =
            "document.annotations-actions.manage";

    private static final COSName ANNOT = COSName.getPDFName("Annot");
    private static final COSName TEXT = COSName.getPDFName("Text");
    private static final COSName STAMP = COSName.getPDFName("Stamp");
    private static final COSName HIGHLIGHT = COSName.getPDFName("Highlight");
    private static final COSName FILE_ATTACHMENT =
            COSName.getPDFName("FileAttachment");
    private static final COSName WIDGET = COSName.getPDFName("Widget");
    private static final COSName LINK = COSName.getPDFName("Link");
    private static final COSName QUAD_POINTS = COSName.getPDFName("QuadPoints");
    private static final COSName NM = COSName.getPDFName("NM");
    private static final COSName P = COSName.getPDFName("P");
    private static final COSName AP = COSName.getPDFName("AP");
    private static final COSName N = COSName.getPDFName("N");
    private static final COSName OPEN = COSName.getPDFName("Open");
    private static final COSName FORM_TYPE = COSName.getPDFName("FormType");
    private static final COSName FS = COSName.getPDFName("FS");
    private static final COSName AF_RELATIONSHIP =
            COSName.getPDFName("AFRelationship");
    private static final COSName BORDER = COSName.getPDFName("Border");
    private static final COSName A = COSName.getPDFName("A");
    private static final COSName D = COSName.getPDFName("D");
    private static final COSName S = COSName.getPDFName("S");
    private static final COSName GO_TO = COSName.getPDFName("GoTo");
    private static final COSName OPEN_ACTION = COSName.getPDFName("OpenAction");
    private static final int MAX_APPEARANCE_CONTENT_BYTES = 1024 * 1024;
    private static final char[] HEX_DIGITS =
            "0123456789abcdef".toCharArray();

    private final PDDocument document;
    private final PdfBoxMetadataOperations metadataOperations;
    private final PdfBoxAnnotationFlattener flattener;
    private final PdfBoxDocumentActionOperations actionOperations;

    PdfBoxAnnotationOperations(
            PDDocument document,
            PdfBoxMetadataOperations metadataOperations) {
        this.document = document;
        this.metadataOperations = metadataOperations;
        this.flattener = new PdfBoxAnnotationFlattener(document, this);
        this.actionOperations = new PdfBoxDocumentActionOperations(
                document,
                metadataOperations,
                this);
    }

    static boolean isManagedCatalogEntry(COSName name) {
        return OPEN_ACTION.equals(name);
    }

    static boolean isKnownNamedTarget(
            NavigationTarget target,
            Set<String> namedDestinations) {
        return target.getKind() != NavigationTarget.Kind.NAMED
                || namedDestinations.contains(
                        target.getNamedDestination().get());
    }

    boolean supports(DocumentCommand command) {
        return command instanceof UpdateAnnotations
                || command instanceof UpdateActions
                || command instanceof FlattenAnnotations;
    }

    boolean supportsQuery(DocumentQuery<?> query) {
        return query instanceof Annotations || query instanceof Actions;
    }

    void requireNonWidgetSignatureUpdate(UpdateAnnotations command)
            throws DocumentFailure {
        Set<String> selected = new HashSet<String>(
                command.getRemovedIdentifiers());
        for (Annotation annotation : command.getAnnotations()) {
            if (annotation.getType() == Annotation.Type.WIDGET) {
                throw PdfBoxWorkflowEngine.signaturePolicyFailure();
            }
            selected.add(annotation.getProperties().getIdentifier());
        }
        if (selected.isEmpty()) {
            return;
        }
        try {
            for (COSBase rawPage : pageReferencesForCommand()) {
                COSDictionary page = dictionary(rawPage);
                COSBase rawAnnotations = page.getItem(COSName.ANNOTS);
                if (rawAnnotations == null) {
                    continue;
                }
                COSBase value = dereference(rawAnnotations);
                if (!(value instanceof COSArray)) {
                    continue;
                }
                COSArray annotations = (COSArray) value;
                for (int index = 0; index < annotations.size(); index++) {
                    COSDictionary existing = dictionary(annotations.get(index));
                    COSBase rawIdentifier = dereference(existing.getItem(NM));
                    if (!(rawIdentifier instanceof COSString)
                            || !selected.contains(
                                    ((COSString) rawIdentifier).getString())) {
                        continue;
                    }
                    if (WIDGET.equals(dereference(
                            existing.getItem(COSName.SUBTYPE)))) {
                        throw PdfBoxWorkflowEngine.signaturePolicyFailure();
                    }
                }
            }
        } catch (DocumentFailure failure) {
            throw failure;
        } catch (RuntimeException backendFailure) {
            throw PdfBoxWorkflowEngine.signaturePolicyFailure();
        }
    }

    void execute(DocumentCommand command) throws DocumentFailure {
        if (!supports(command)) {
            throw new IllegalArgumentException("Unsupported annotation command.");
        }
        try {
            if (command instanceof UpdateAnnotations) {
                update((UpdateAnnotations) command);
            } else if (command instanceof UpdateActions) {
                actionOperations.update((UpdateActions) command);
            } else {
                flattener.flatten((FlattenAnnotations) command);
            }
        } catch (DocumentFailure failure) {
            throw failure;
        } catch (RuntimeException backendFailure) {
            throw failure(
                    DocumentFailureCode.DOCUMENT_WRITE_FAILED,
                    "The annotation update could not be completed safely.");
        }
    }

    Object evaluate(DocumentQuery<?> query) throws DocumentFailure {
        if (!supportsQuery(query)) {
            throw new IllegalArgumentException("Unsupported annotation query.");
        }
        try {
            return query instanceof Annotations
                    ? annotations((Annotations) query)
                    : actionOperations.evaluate((Actions) query);
        } catch (DocumentFailure failure) {
            if (query instanceof Actions
                    && failure.getCode() == DocumentFailureCode.QUERY_FAILED) {
                throw invalidActionQuery();
            }
            throw failure;
        } catch (RuntimeException backendFailure) {
            throw invalidQuery();
        }
    }

    private void update(UpdateAnnotations command) throws DocumentFailure {
        List<COSBase> pageReferences = pageReferencesForCommand();
        Set<String> namedDestinations;
        try {
            namedDestinations = metadataOperations.namedDestinationNames(
                    document);
        } catch (DocumentFailure malformedDestinations) {
            throw invalidCommand();
        }
        Map<Integer, COSArray> replacements =
                new LinkedHashMap<Integer, COSArray>();
        Set<String> identifiers = new HashSet<String>();
        Set<String> replacing = new HashSet<String>();
        Set<String> removing = new HashSet<String>(
                command.getRemovedIdentifiers());
        IdentityHashMap<COSDictionary, Integer> pageNumbers =
                new IdentityHashMap<COSDictionary, Integer>();
        for (int index = 0; index < pageReferences.size(); index++) {
            pageNumbers.put(dictionary(pageReferences.get(index)),
                    Integer.valueOf(index + 1));
        }
        PdfBoxAnnotationDecodePolicy.Budgets budgets =
                PdfBoxAnnotationDecodePolicy.newManagedGraphPass();
        for (Annotation annotation : command.getAnnotations()) {
            int pageNumber = annotation.getProperties().getPageNumber();
            if (pageNumber > pageReferences.size()) {
                throw invalidCommand();
            }
            if (!replacing.add(annotation.getProperties().getIdentifier())) {
                throw invalidCommand();
            }
            if (removing.contains(annotation.getProperties().getIdentifier())) {
                throw invalidCommand();
            }
            if (annotation.getLinkActivation().isPresent()
                    && !isKnownNamedTarget(
                            annotation.getLinkActivation().get().getTarget(),
                            namedDestinations)) {
                throw invalidCommand();
            }
        }

        for (int pageIndex = 0; pageIndex < pageReferences.size(); pageIndex++) {
            COSDictionary page = dictionary(pageReferences.get(pageIndex));
            COSArray copied = new COSArray();
            copied.setDirect(true);
            COSBase rawAnnotations = page.getItem(COSName.ANNOTS);
            if (rawAnnotations != null) {
                COSBase value = dereference(rawAnnotations);
                if (!(value instanceof COSArray)) {
                    throw invalidCommand();
                }
                COSArray existing = (COSArray) value;
                for (int index = 0; index < existing.size(); index++) {
                    COSBase raw = existing.get(index);
                    COSDictionary annotation;
                    String identifier;
                    try {
                        annotation = dictionary(raw);
                        identifier = identifierOf(annotation, true);
                    } catch (DocumentFailure malformedAnnotation) {
                        throw invalidCommand();
                    }
                    if (identifier != null && !identifiers.add(identifier)) {
                        throw invalidCommand();
                    }
                    boolean selected = identifier != null
                            && (replacing.contains(identifier)
                                    || removing.contains(identifier));
                    if (selected) {
                        try {
                            Annotation existingAnnotation = publicAnnotation(
                                    annotation,
                                    pageIndex + 1,
                                    page,
                                    budgets.appearances(),
                                    budgets.attachments(),
                                    pageNumbers);
                            if (existingAnnotation.getLinkActivation()
                                            .isPresent()
                                    && !isKnownNamedTarget(
                                            existingAnnotation
                                                    .getLinkActivation()
                                                    .get()
                                                    .getTarget(),
                                            namedDestinations)) {
                                throw invalidCommand();
                            }
                        } catch (DocumentFailure invalidExistingAnnotation) {
                            throw invalidCommand();
                        }
                    } else {
                        copied.add(raw);
                    }
                }
            }
            replacements.put(Integer.valueOf(pageIndex), copied);
        }
        if (!identifiers.containsAll(removing)) {
            throw annotationNotFound();
        }

        for (Annotation annotation : command.getAnnotations()) {
            int pageIndex = annotation.getProperties().getPageNumber() - 1;
            COSBase pageReference = pageReferences.get(pageIndex);
            replacements.get(Integer.valueOf(pageIndex)).add(
                    new COSObject(backendAnnotation(
                            annotation,
                            pageReference,
                            pageReferences)));
        }

        for (Map.Entry<Integer, COSArray> replacement
                : replacements.entrySet()) {
            COSDictionary page = dictionary(
                    pageReferences.get(replacement.getKey().intValue()));
            if (replacement.getValue().size() == 0) {
                page.removeItem(COSName.ANNOTS);
            } else {
                page.setItem(COSName.ANNOTS, replacement.getValue());
            }
        }
    }

    private List<Annotation> annotations(Annotations query)
            throws DocumentFailure {
        List<COSBase> pageReferences = pageReferencesForQuery();
        Set<String> namedDestinations;
        try {
            namedDestinations = metadataOperations.namedDestinationNames(
                    document);
        } catch (DocumentFailure invalidDestinations) {
            throw invalidQuery();
        }
        List<Annotation> values = new ArrayList<Annotation>();
        Set<String> identifiers = new HashSet<String>();
        ByteBudget appearances = new ByteBudget(
                query.getMaximumAppearanceBytes());
        ByteBudget attachments = new ByteBudget(
                query.getMaximumAttachmentBytes());
        IdentityHashMap<COSDictionary, Integer> pageNumbers =
                new IdentityHashMap<COSDictionary, Integer>();
        for (int index = 0; index < pageReferences.size(); index++) {
            pageNumbers.put(dictionary(pageReferences.get(index)),
                    Integer.valueOf(index + 1));
        }
        for (int pageIndex = 0; pageIndex < pageReferences.size(); pageIndex++) {
            COSDictionary page = dictionary(pageReferences.get(pageIndex));
            COSBase rawAnnotations = page.getItem(COSName.ANNOTS);
            if (rawAnnotations == null) {
                continue;
            }
            COSBase rawArray = dereference(rawAnnotations);
            if (!(rawArray instanceof COSArray)) {
                throw invalidQuery();
            }
            COSArray array = (COSArray) rawArray;
            for (int index = 0; index < array.size(); index++) {
                if (values.size() >= query.getMaximumAnnotations()) {
                    throw limitExceeded();
                }
                Annotation annotation = publicAnnotation(
                        dictionary(array.get(index)),
                        pageIndex + 1,
                        page,
                        appearances,
                        attachments,
                        pageNumbers);
                if (annotation.getLinkActivation().isPresent()
                        && !isKnownNamedTarget(
                                annotation.getLinkActivation().get()
                                        .getTarget(),
                                namedDestinations)) {
                    throw invalidQuery();
                }
                if (!identifiers.add(
                        annotation.getProperties().getIdentifier())) {
                    throw invalidQuery();
                }
                values.add(annotation);
            }
        }
        return Collections.unmodifiableList(values);
    }

    Annotation publicAnnotation(
            COSDictionary dictionary,
            int pageNumber,
            COSDictionary page,
            ByteBudget appearances,
            ByteBudget attachments,
            IdentityHashMap<COSDictionary, Integer> pageNumbers)
            throws DocumentFailure {
        COSBase type = dereference(dictionary.getItem(COSName.TYPE));
        COSBase subtype = dereference(dictionary.getItem(COSName.SUBTYPE));
        if (!ANNOT.equals(type)
                || (!TEXT.equals(subtype)
                        && !STAMP.equals(subtype)
                        && !HIGHLIGHT.equals(subtype)
                        && !FILE_ATTACHMENT.equals(subtype)
                        && !WIDGET.equals(subtype)
                        && !LINK.equals(subtype))) {
            throw invalidQuery();
        }
        requireAnnotationKeys(dictionary, subtype);
        COSBase annotationPage = dictionary.getItem(P);
        if (annotationPage != null && dereference(annotationPage) != page) {
            throw invalidQuery();
        }
        String identifier = identifierOf(dictionary, false);
        AnnotationProperties.Builder properties = AnnotationProperties.version1(
                identifier,
                pageNumber,
                rectangle(dictionary.getItem(COSName.RECT)));
        COSBase contents = dereference(dictionary.getItem(COSName.CONTENTS));
        if (contents != null) {
            if (!(contents instanceof COSString)) {
                throw invalidQuery();
            }
            properties.contents(((COSString) contents).getString());
        }
        for (AnnotationFlag flag : flagsOf(dictionary.getItem(COSName.F))) {
            properties.flag(flag);
        }
        COSBase rawAppearance = dictionary.getItem(AP);
        if (rawAppearance != null) {
            properties.appearance(appearance(rawAppearance, appearances));
        }
        if (TEXT.equals(subtype)) {
            Annotation.TextIcon icon = textIcon(
                    dereference(dictionary.getItem(COSName.NAME)));
            COSBase rawOpen = dereference(dictionary.getItem(OPEN));
            if (rawOpen != null && !(rawOpen instanceof COSBoolean)) {
                throw invalidQuery();
            }
            return Annotation.text(
                    properties.build(),
                    icon,
                    rawOpen != null && ((COSBoolean) rawOpen).getValue());
        }
        if (STAMP.equals(subtype)) {
            if (dictionary.getItem(OPEN) != null) {
                throw invalidQuery();
            }
            COSBase rawName = dereference(dictionary.getItem(COSName.NAME));
            if (!(rawName instanceof COSName)
                    || ((COSName) rawName).getName().isEmpty()) {
                throw invalidQuery();
            }
            return Annotation.stamp(
                    properties.build(),
                    ((COSName) rawName).getName());
        }
        if (HIGHLIGHT.equals(subtype)) {
            if (dictionary.getItem(OPEN) != null
                    || dictionary.getItem(COSName.NAME) != null
                    || dictionary.getItem(FS) != null) {
                throw invalidQuery();
            }
            return Annotation.highlight(
                    properties.build(),
                    quads(dictionary.getItem(QUAD_POINTS)),
                    color(dictionary.getItem(COSName.C)));
        }
        if (WIDGET.equals(subtype)) {
            if (dictionary.getItem(OPEN) != null
                    || dictionary.getItem(COSName.NAME) != null
                    || dictionary.getItem(QUAD_POINTS) != null
                    || dictionary.getItem(COSName.C) != null
                    || dictionary.getItem(FS) != null) {
                throw invalidQuery();
            }
            return Annotation.widget(properties.build());
        }
        if (LINK.equals(subtype)) {
            if (dictionary.getItem(OPEN) != null
                    || dictionary.getItem(COSName.NAME) != null
                    || dictionary.getItem(QUAD_POINTS) != null
                    || dictionary.getItem(COSName.C) != null
                    || dictionary.getItem(FS) != null) {
                throw invalidQuery();
            }
            requireZeroBorder(dictionary.getItem(BORDER));
            COSBase destination = dictionary.getItem(COSName.DEST);
            COSBase action = dictionary.getItem(A);
            if ((destination == null) == (action == null)) {
                throw invalidQuery();
            }
            return Annotation.link(
                    properties.build(),
                    destination != null
                            ? LinkActivation.destination(navigationTarget(
                                    destination,
                                    pageNumbers))
                            : LinkActivation.action(GoToAction.version1(
                                    actionTarget(action, pageNumbers))));
        }
        if (dictionary.getItem(OPEN) != null
                || dictionary.getItem(QUAD_POINTS) != null
                || dictionary.getItem(COSName.C) != null) {
            throw invalidQuery();
        }
        return Annotation.fileAttachment(
                properties.build(),
                fileSpecification(dictionary.getItem(FS), attachments),
                fileAttachmentIcon(dereference(
                        dictionary.getItem(COSName.NAME))));
    }

    private static void requireAnnotationKeys(
            COSDictionary dictionary,
            COSBase subtype) throws DocumentFailure {
        if (TEXT.equals(subtype)) {
            requireOnlyKeys(dictionary,
                    "Type", "Subtype", "Rect", "Contents", "P", "NM",
                    "F", "AP", "Name", "Open");
        } else if (STAMP.equals(subtype)) {
            requireOnlyKeys(dictionary,
                    "Type", "Subtype", "Rect", "Contents", "P", "NM",
                    "F", "AP", "Name");
        } else if (HIGHLIGHT.equals(subtype)) {
            requireOnlyKeys(dictionary,
                    "Type", "Subtype", "Rect", "Contents", "P", "NM",
                    "F", "AP", "QuadPoints", "C");
        } else if (FILE_ATTACHMENT.equals(subtype)) {
            requireOnlyKeys(dictionary,
                    "Type", "Subtype", "Rect", "Contents", "P", "NM",
                    "F", "AP", "FS", "Name");
        } else if (WIDGET.equals(subtype)) {
            requireOnlyKeys(dictionary,
                    "Type", "Subtype", "Rect", "Contents", "P", "NM",
                    "F", "AP");
        } else {
            requireOnlyKeys(dictionary,
                    "Type", "Subtype", "Rect", "Contents", "P", "NM",
                    "F", "AP", "Dest", "Border", "A");
        }
    }

    COSDictionary backendAnnotation(
            Annotation annotation,
            COSBase pageReference,
            List<COSBase> pageReferences) throws DocumentFailure {
        AnnotationProperties properties = annotation.getProperties();
        COSDictionary dictionary = new COSDictionary();
        dictionary.setItem(COSName.TYPE, ANNOT);
        if (annotation.getType() == Annotation.Type.TEXT) {
            dictionary.setItem(COSName.SUBTYPE, TEXT);
        } else if (annotation.getType() == Annotation.Type.STAMP) {
            dictionary.setItem(COSName.SUBTYPE, STAMP);
        } else if (annotation.getType() == Annotation.Type.HIGHLIGHT) {
            dictionary.setItem(COSName.SUBTYPE, HIGHLIGHT);
        } else if (annotation.getType() == Annotation.Type.FILE_ATTACHMENT) {
            dictionary.setItem(COSName.SUBTYPE, FILE_ATTACHMENT);
        } else if (annotation.getType() == Annotation.Type.WIDGET) {
            dictionary.setItem(COSName.SUBTYPE, WIDGET);
        } else if (annotation.getType() == Annotation.Type.LINK) {
            dictionary.setItem(COSName.SUBTYPE, LINK);
        } else {
            throw invalidCommand();
        }
        dictionary.setItem(COSName.RECT,
                backendRectangle(properties.getRectangle()));
        dictionary.setItem(P, pageReference);
        dictionary.setItem(NM, new COSString(properties.getIdentifier()));
        if (properties.getContents().isPresent()) {
            dictionary.setItem(COSName.CONTENTS,
                    new COSString(properties.getContents().get()));
        }
        int flags = flagBits(properties.getFlags());
        if (flags != 0) {
            dictionary.setItem(COSName.F, COSInteger.get(flags));
        }
        if (annotation.getType() == Annotation.Type.TEXT) {
            dictionary.setItem(COSName.NAME, COSName.getPDFName(
                    textIconName(annotation.getTextIcon().get())));
            if (annotation.isOpen()) {
                dictionary.setItem(OPEN, COSBoolean.TRUE);
            }
        } else if (annotation.getType() == Annotation.Type.STAMP) {
            dictionary.setItem(COSName.NAME, COSName.getPDFName(
                    annotation.getStampName().get()));
        } else if (annotation.getType() == Annotation.Type.HIGHLIGHT) {
            dictionary.setItem(QUAD_POINTS,
                    backendQuads(annotation.getQuads()));
            dictionary.setItem(COSName.C,
                    backendColor(annotation.getColor().get()));
        } else if (annotation.getType() == Annotation.Type.FILE_ATTACHMENT) {
            dictionary.setItem(FS,
                    backendFileSpecification(annotation.getAttachment().get()));
            dictionary.setItem(COSName.NAME, COSName.getPDFName(
                    fileAttachmentIconName(
                            annotation.getFileAttachmentIcon().get())));
        } else if (annotation.getType() == Annotation.Type.WIDGET) {
            // Standalone Widget support deliberately adds no field entries.
        } else if (annotation.getType() == Annotation.Type.LINK) {
            LinkActivation activation = annotation.getLinkActivation().get();
            if (activation.getKind() == LinkActivation.Kind.DESTINATION) {
                dictionary.setItem(COSName.DEST, backendNavigationTarget(
                        activation.getTarget(), pageReferences));
            } else {
                dictionary.setItem(A, backendAction(
                        activation.getTarget(), pageReferences));
            }
            COSArray border = new COSArray();
            border.setDirect(true);
            border.add(COSInteger.ZERO);
            border.add(COSInteger.ZERO);
            border.add(COSInteger.ZERO);
            dictionary.setItem(BORDER, border);
        } else {
            throw invalidCommand();
        }
        if (properties.getAppearance().isPresent()) {
            dictionary.setItem(AP,
                    backendAppearance(properties.getAppearance().get()));
        }
        return dictionary;
    }

    NavigationTarget actionTarget(
            COSBase raw,
            IdentityHashMap<COSDictionary, Integer> pageNumbers)
            throws DocumentFailure {
        COSBase value = dereference(raw);
        if (!(value instanceof COSDictionary) || value instanceof COSStream) {
            throw invalidQuery();
        }
        COSDictionary action = (COSDictionary) value;
        requireOnlyKeys(action, "S", "D");
        if (!GO_TO.equals(dereference(action.getItem(S)))) {
            throw invalidQuery();
        }
        return navigationTarget(action.getItem(D), pageNumbers);
    }

    COSDictionary backendAction(
            NavigationTarget target,
            List<COSBase> pageReferences) throws DocumentFailure {
        COSDictionary action = new COSDictionary();
        action.setItem(S, GO_TO);
        action.setItem(D, backendNavigationTarget(target, pageReferences));
        return action;
    }

    private NavigationTarget navigationTarget(
            COSBase raw,
            IdentityHashMap<COSDictionary, Integer> pageNumbers)
            throws DocumentFailure {
        COSBase value = dereference(raw);
        if (value instanceof COSString) {
            String name = ((COSString) value).getString();
            if (name.isEmpty()) {
                throw invalidQuery();
            }
            return NavigationTarget.toNamedDestination(name);
        }
        if (value instanceof COSName) {
            String name = ((COSName) value).getName();
            if (name.isEmpty()) {
                throw invalidQuery();
            }
            return NavigationTarget.toNamedDestination(name);
        }
        PageDestination destination = metadataOperations.destinationFromArray(
                raw,
                pageNumbers);
        if (destination == null) {
            throw invalidQuery();
        }
        return NavigationTarget.toPage(destination);
    }

    private COSBase backendNavigationTarget(
            NavigationTarget target,
            List<COSBase> pageReferences) throws DocumentFailure {
        if (target.getKind() == NavigationTarget.Kind.NAMED) {
            return new COSString(target.getNamedDestination().get());
        }
        PageDestination destination = target.getPageDestination().get();
        if (destination.getPageNumber() > pageReferences.size()) {
            throw invalidCommand();
        }
        try {
            return metadataOperations.destinationToArray(
                    destination,
                    pageReferences.get(destination.getPageNumber() - 1));
        } catch (DocumentFailure failure) {
            throw invalidCommand();
        }
    }

    private static void requireZeroBorder(COSBase raw)
            throws DocumentFailure {
        if (raw == null) {
            return;
        }
        COSBase value = dereference(raw);
        if (!(value instanceof COSArray) || ((COSArray) value).size() != 3) {
            throw invalidQuery();
        }
        COSArray border = (COSArray) value;
        for (int index = 0; index < border.size(); index++) {
            BigDecimal number = decimal(border.get(index));
            if (number == null || number.compareTo(BigDecimal.ZERO) != 0) {
                throw invalidQuery();
            }
        }
    }

    private COSDictionary backendFileSpecification(EmbeddedFile file)
            throws DocumentFailure {
        try {
            COSStream stream = document.getDocument().createCOSStream();
            stream.setItem(COSName.TYPE, COSName.EMBEDDED_FILE);
            if (file.getMimeSubtype().isPresent()) {
                stream.setItem(COSName.SUBTYPE,
                        mimeSubtypeName(file.getMimeSubtype().get()));
            }
            try (OutputStream output = stream.createOutputStream()) {
                output.write(file.getContent());
            }

            COSDictionary ef = new COSDictionary();
            ef.setDirect(true);
            ef.setItem(COSName.F, stream);

            COSDictionary specification = new COSDictionary();
            specification.setItem(COSName.TYPE,
                    COSName.getPDFName("Filespec"));
            specification.setItem(COSName.F,
                    new COSString(file.getName()));
            specification.setItem(COSName.EF, ef);
            if (file.getDescription().isPresent()) {
                specification.setItem(COSName.DESC,
                        new COSString(file.getDescription().get()));
            }
            if (file.getRelationship()
                    != EmbeddedFile.Relationship.UNSPECIFIED) {
                specification.setItem(AF_RELATIONSHIP,
                        relationshipName(file.getRelationship()));
            }
            return specification;
        } catch (IOException | RuntimeException failure) {
            throw invalidCommand();
        }
    }

    private static EmbeddedFile fileSpecification(
            COSBase raw,
            ByteBudget budget) throws DocumentFailure {
        COSBase value = dereference(raw);
        if (!(value instanceof COSDictionary) || value instanceof COSStream) {
            throw invalidQuery();
        }
        COSDictionary specification = (COSDictionary) value;
        requireOnlyKeys(specification,
                "Type", "F", "UF", "EF", "Desc", "AFRelationship");
        COSBase type = dereference(specification.getItem(COSName.TYPE));
        if (type != null
                && !COSName.getPDFName("Filespec").equals(type)) {
            throw invalidQuery();
        }
        COSBase rawName = dereference(specification.getItem(COSName.F));
        COSBase rawDescription = dereference(
                specification.getItem(COSName.DESC));
        if (!(rawName instanceof COSString)
                || ((COSString) rawName).getString().isEmpty()
                || (rawDescription != null
                        && !(rawDescription instanceof COSString))) {
            throw invalidQuery();
        }
        COSBase rawRelationship = dereference(
                specification.getItem(AF_RELATIONSHIP));
        EmbeddedFile.Relationship relationship = rawRelationship == null
                ? EmbeddedFile.Relationship.UNSPECIFIED
                : relationship(rawRelationship);
        if (relationship == null) {
            throw invalidQuery();
        }
        COSBase rawEf = dereference(specification.getItem(COSName.EF));
        if (!(rawEf instanceof COSDictionary)) {
            throw invalidQuery();
        }
        COSDictionary ef = (COSDictionary) rawEf;
        requireOnlyKeys(ef, "F");
        COSBase rawStream = dereference(ef.getItem(COSName.F));
        if (!(rawStream instanceof COSStream)) {
            throw invalidQuery();
        }
        COSStream stream = (COSStream) rawStream;
        requireOnlyKeys(stream, "Type", "Subtype", "Length", "Filter",
                "DecodeParms", "DL");
        COSBase streamType = dereference(stream.getItem(COSName.TYPE));
        if (streamType != null && !COSName.EMBEDDED_FILE.equals(streamType)) {
            throw invalidQuery();
        }
        COSBase rawMime = dereference(stream.getItem(COSName.SUBTYPE));
        if (rawMime != null && !(rawMime instanceof COSName)) {
            throw invalidQuery();
        }
        String name = ((COSString) rawName).getString();
        String description = rawDescription == null
                ? null : ((COSString) rawDescription).getString();
        String mime = rawMime == null
                ? null : mimeSubtypeFromName((COSName) rawMime);
        byte[] content = decodedBytes(stream, budget);
        if (mime == null) {
            if (description != null
                    || relationship != EmbeddedFile.Relationship.UNSPECIFIED) {
                throw invalidQuery();
            }
            return EmbeddedFile.version1(name, content);
        }
        if (description == null
                && relationship == EmbeddedFile.Relationship.UNSPECIFIED) {
            return EmbeddedFile.version1(name, content, mime);
        }
        if (description == null) {
            throw invalidQuery();
        }
        return EmbeddedFile.version1(
                name,
                content,
                mime,
                description,
                relationship);
    }

    private static List<AnnotationQuad> quads(COSBase raw)
            throws DocumentFailure {
        COSBase value = dereference(raw);
        if (!(value instanceof COSArray)
                || ((COSArray) value).size() == 0
                || ((COSArray) value).size() % 8 != 0) {
            throw invalidQuery();
        }
        COSArray array = (COSArray) value;
        List<AnnotationQuad> quads = new ArrayList<AnnotationQuad>(
                array.size() / 8);
        for (int offset = 0; offset < array.size(); offset += 8) {
            BigDecimal[] coordinates = new BigDecimal[8];
            for (int index = 0; index < coordinates.length; index++) {
                coordinates[index] = decimal(array.get(offset + index));
                if (coordinates[index] == null) {
                    throw invalidQuery();
                }
            }
            quads.add(AnnotationQuad.of(
                    coordinates[0], coordinates[1],
                    coordinates[2], coordinates[3],
                    coordinates[4], coordinates[5],
                    coordinates[6], coordinates[7]));
        }
        return quads;
    }

    private static COSArray backendQuads(List<AnnotationQuad> quads)
            throws DocumentFailure {
        COSArray array = new COSArray();
        array.setDirect(true);
        for (AnnotationQuad quad : quads) {
            for (BigDecimal coordinate : quad.getCoordinates()) {
                array.add(backendNumber(coordinate));
            }
        }
        return array;
    }

    private static AnnotationColor color(COSBase raw)
            throws DocumentFailure {
        COSBase value = dereference(raw);
        if (!(value instanceof COSArray)) {
            throw invalidQuery();
        }
        COSArray array = (COSArray) value;
        List<BigDecimal> components = new ArrayList<BigDecimal>(array.size());
        for (int index = 0; index < array.size(); index++) {
            BigDecimal component = decimal(array.get(index));
            if (component == null) {
                throw invalidQuery();
            }
            components.add(component);
        }
        try {
            if (components.size() == 1) {
                return AnnotationColor.gray(components.get(0));
            }
            if (components.size() == 3) {
                return AnnotationColor.rgb(
                        components.get(0), components.get(1), components.get(2));
            }
            if (components.size() == 4) {
                return AnnotationColor.cmyk(
                        components.get(0), components.get(1),
                        components.get(2), components.get(3));
            }
        } catch (IllegalArgumentException invalid) {
            throw invalidQuery();
        }
        throw invalidQuery();
    }

    private static COSArray backendColor(AnnotationColor color)
            throws DocumentFailure {
        COSArray array = new COSArray();
        array.setDirect(true);
        for (BigDecimal component : color.getComponents()) {
            array.add(backendNumber(component));
        }
        return array;
    }

    private COSDictionary backendAppearance(AnnotationAppearance appearance)
            throws DocumentFailure {
        byte[] content = appearance.getContent();
        requireSafeAppearanceContent(content, true);
        try {
            COSStream stream = document.getDocument().createCOSStream();
            stream.setItem(COSName.TYPE, COSName.XOBJECT);
            stream.setItem(COSName.SUBTYPE, COSName.FORM);
            stream.setItem(FORM_TYPE, COSInteger.ONE);
            stream.setItem(COSName.BBOX,
                    backendRectangle(appearance.getBoundingBox()));
            COSDictionary resources = new COSDictionary();
            resources.setDirect(true);
            stream.setItem(COSName.RESOURCES, resources);
            try (OutputStream output = stream.createOutputStream()) {
                output.write(content);
            }
            COSDictionary appearances = new COSDictionary();
            appearances.setDirect(true);
            appearances.setItem(N, stream);
            return appearances;
        } catch (IOException | RuntimeException backendFailure) {
            throw invalidCommand();
        }
    }

    private AnnotationAppearance appearance(
            COSBase rawAppearance,
            ByteBudget budget) throws DocumentFailure {
        COSBase value = dereference(rawAppearance);
        if (!(value instanceof COSDictionary) || value instanceof COSStream) {
            throw invalidQuery();
        }
        COSDictionary appearances = (COSDictionary) value;
        requireOnlyKeys(appearances, "N");
        COSBase normal = dereference(appearances.getItem(N));
        if (!(normal instanceof COSStream)) {
            throw invalidQuery();
        }
        COSStream stream = (COSStream) normal;
        requireOnlyKeys(stream, "Length", "Filter", "DecodeParms", "DL",
                "Type", "Subtype", "FormType", "BBox", "Resources");
        if (!COSName.XOBJECT.equals(dereference(stream.getItem(COSName.TYPE)))
                || !COSName.FORM.equals(dereference(
                        stream.getItem(COSName.SUBTYPE)))) {
            throw invalidQuery();
        }
        COSBase formType = dereference(stream.getItem(FORM_TYPE));
        if (!(formType instanceof COSInteger)
                || ((COSInteger) formType).longValue() != 1L) {
            throw invalidQuery();
        }
        COSBase resources = dereference(stream.getItem(COSName.RESOURCES));
        if (!(resources instanceof COSDictionary)
                || ((COSDictionary) resources).size() != 0) {
            throw invalidQuery();
        }
        byte[] content = decodedBytes(stream, budget);
        requireSafeAppearanceContent(content, false);
        return AnnotationAppearance.version1(
                rectangle(stream.getItem(COSName.BBOX)),
                content);
    }

    private static void requireSafeAppearanceContent(
            byte[] content,
            boolean command) throws DocumentFailure {
        if (content.length == 0
                || content.length > MAX_APPEARANCE_CONTENT_BYTES) {
            throw command ? invalidCommand() : invalidQuery();
        }
        PDFStreamParser parser = new PDFStreamParser(content);
        try {
            List<Object> operands = new ArrayList<Object>();
            int savedGraphicsStates = 0;
            for (Object token : parser.parse()) {
                if (!(token instanceof Operator)) {
                    operands.add(token);
                    continue;
                }
                String name = ((Operator) token).getName();
                if (!validAppearanceOperands(name, operands)) {
                    throw command ? invalidCommand() : invalidQuery();
                }
                if ("q".equals(name)) {
                    savedGraphicsStates++;
                } else if ("Q".equals(name)) {
                    if (savedGraphicsStates == 0) {
                        throw command ? invalidCommand() : invalidQuery();
                    }
                    savedGraphicsStates--;
                }
                operands.clear();
            }
            if (!operands.isEmpty() || savedGraphicsStates != 0) {
                throw command ? invalidCommand() : invalidQuery();
            }
        } catch (DocumentFailure failure) {
            throw failure;
        } catch (IOException | RuntimeException invalidSyntax) {
            throw command ? invalidCommand() : invalidQuery();
        } finally {
            try {
                parser.close();
            } catch (IOException ignored) {
                // The byte-array parser owns no caller resource.
            }
        }
    }

    private static boolean validAppearanceOperands(
            String operator,
            List<Object> operands) {
        if (isOneOf(operator,
                "q", "Q", "h", "S", "s", "f", "F", "f*",
                "B", "B*", "b", "b*", "n", "W", "W*")) {
            return operands.isEmpty();
        }
        if ("w".equals(operator)) {
            return numericOperandAtLeast(operands, BigDecimal.ZERO);
        }
        if (isOneOf(operator, "J", "j")) {
            return integerOperandBetween(operands, 0, 2);
        }
        if ("M".equals(operator)) {
            return numericOperandAtLeast(operands, BigDecimal.ONE);
        }
        if ("i".equals(operator)) {
            return numericOperandsBetween(
                    operands,
                    1,
                    BigDecimal.ZERO,
                    BigDecimal.valueOf(100L));
        }
        if (isOneOf(operator, "G", "g")) {
            return numericOperandsBetween(
                    operands,
                    1,
                    BigDecimal.ZERO,
                    BigDecimal.ONE);
        }
        if (isOneOf(operator, "m", "l")) {
            return numericOperands(operands, 2);
        }
        if (isOneOf(operator, "RG", "rg")) {
            return numericOperandsBetween(
                    operands,
                    3,
                    BigDecimal.ZERO,
                    BigDecimal.ONE);
        }
        if (isOneOf(operator, "v", "y", "re")) {
            return numericOperands(operands, 4);
        }
        if (isOneOf(operator, "K", "k")) {
            return numericOperandsBetween(
                    operands,
                    4,
                    BigDecimal.ZERO,
                    BigDecimal.ONE);
        }
        if (isOneOf(operator, "cm", "c")) {
            return numericOperands(operands, 6);
        }
        if ("ri".equals(operator)) {
            return operands.size() == 1 && operands.get(0) instanceof COSName;
        }
        if ("d".equals(operator)
                && operands.size() == 2
                && operands.get(0) instanceof COSArray
                && operands.get(1) instanceof COSNumber) {
            COSArray pattern = (COSArray) operands.get(0);
            BigDecimal phase = appearanceNumber(operands.get(1));
            if (phase == null || phase.compareTo(BigDecimal.ZERO) < 0) {
                return false;
            }
            boolean positive = false;
            for (int index = 0; index < pattern.size(); index++) {
                BigDecimal length = decimal(pattern.get(index));
                if (length == null
                        || length.compareTo(BigDecimal.ZERO) < 0) {
                    return false;
                }
                positive |= length.compareTo(BigDecimal.ZERO) > 0;
            }
            return pattern.size() == 0 || positive;
        }
        return false;
    }

    private static boolean numericOperandAtLeast(
            List<Object> operands,
            BigDecimal minimum) {
        if (operands.size() != 1) {
            return false;
        }
        BigDecimal value = appearanceNumber(operands.get(0));
        return value != null && value.compareTo(minimum) >= 0;
    }

    private static boolean integerOperandBetween(
            List<Object> operands,
            int minimum,
            int maximum) {
        if (operands.size() != 1) {
            return false;
        }
        BigDecimal value = appearanceNumber(operands.get(0));
        if (value == null || value.stripTrailingZeros().scale() > 0) {
            return false;
        }
        return value.compareTo(BigDecimal.valueOf(minimum)) >= 0
                && value.compareTo(BigDecimal.valueOf(maximum)) <= 0;
    }

    private static boolean numericOperandsBetween(
            List<Object> operands,
            int count,
            BigDecimal minimum,
            BigDecimal maximum) {
        if (operands.size() != count) {
            return false;
        }
        for (Object operand : operands) {
            BigDecimal value = appearanceNumber(operand);
            if (value == null
                    || value.compareTo(minimum) < 0
                    || value.compareTo(maximum) > 0) {
                return false;
            }
        }
        return true;
    }

    private static BigDecimal appearanceNumber(Object operand) {
        return operand instanceof COSBase
                ? decimal((COSBase) operand)
                : null;
    }

    private static boolean numericOperands(
            List<Object> operands,
            int count) {
        if (operands.size() != count) {
            return false;
        }
        for (Object operand : operands) {
            if (!(operand instanceof COSNumber)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isOneOf(String candidate, String... values) {
        for (String value : values) {
            if (value.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static byte[] decodedBytes(COSStream stream, ByteBudget budget)
            throws DocumentFailure {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        try (InputStream input = stream.createInputStream()) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                budget.consume(read);
                bytes.write(buffer, 0, read);
            }
            return bytes.toByteArray();
        } catch (DocumentFailure failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            throw invalidQuery();
        }
    }

    private static AnnotationRectangle rectangle(COSBase raw)
            throws DocumentFailure {
        COSBase value = dereference(raw);
        if (!(value instanceof COSArray) || ((COSArray) value).size() != 4) {
            throw invalidQuery();
        }
        COSArray array = (COSArray) value;
        BigDecimal left = decimal(array.get(0));
        BigDecimal bottom = decimal(array.get(1));
        BigDecimal right = decimal(array.get(2));
        BigDecimal top = decimal(array.get(3));
        if (left == null || bottom == null || right == null || top == null) {
            throw invalidQuery();
        }
        try {
            return AnnotationRectangle.of(left, bottom, right, top);
        } catch (IllegalArgumentException invalid) {
            throw invalidQuery();
        }
    }

    private static COSArray backendRectangle(AnnotationRectangle rectangle)
            throws DocumentFailure {
        COSArray array = new COSArray();
        array.setDirect(true);
        array.add(backendNumber(rectangle.getLeft()));
        array.add(backendNumber(rectangle.getBottom()));
        array.add(backendNumber(rectangle.getRight()));
        array.add(backendNumber(rectangle.getTop()));
        return array;
    }

    private static COSBase backendNumber(BigDecimal decimal)
            throws DocumentFailure {
        if (decimal.scale() <= 0) {
            try {
                return COSInteger.get(decimal.longValueExact());
            } catch (ArithmeticException outsideIntegerRange) {
                // A lexical real retains exact values outside the integer range.
            }
        }
        try {
            return new COSFloat(decimal.toPlainString());
        } catch (IOException | NumberFormatException invalid) {
            throw invalidCommand();
        }
    }

    private static BigDecimal decimal(COSBase raw) {
        COSBase value = dereference(raw);
        if (value instanceof COSInteger) {
            return BigDecimal.valueOf(((COSInteger) value).longValue());
        }
        if (value instanceof COSFloat) {
            try {
                return PdfBoxValueAdapter.serializedNumber((COSFloat) value);
            } catch (IOException | NumberFormatException invalid) {
                return null;
            }
        }
        return null;
    }

    private static EnumSet<AnnotationFlag> flagsOf(COSBase raw)
            throws DocumentFailure {
        if (raw == null) {
            return EnumSet.noneOf(AnnotationFlag.class);
        }
        COSBase value = dereference(raw);
        if (!(value instanceof COSInteger)) {
            throw invalidQuery();
        }
        long bits = ((COSInteger) value).longValue();
        if (bits < 0L || (bits & ~0x3ffL) != 0L) {
            throw invalidQuery();
        }
        EnumSet<AnnotationFlag> flags = EnumSet.noneOf(AnnotationFlag.class);
        AnnotationFlag[] values = AnnotationFlag.values();
        for (int index = 0; index < values.length; index++) {
            if ((bits & (1L << index)) != 0L) {
                flags.add(values[index]);
            }
        }
        return flags;
    }

    private static int flagBits(Set<AnnotationFlag> flags) {
        int bits = 0;
        AnnotationFlag[] values = AnnotationFlag.values();
        for (int index = 0; index < values.length; index++) {
            if (flags.contains(values[index])) {
                bits |= 1 << index;
            }
        }
        return bits;
    }

    private static Annotation.TextIcon textIcon(COSBase value)
            throws DocumentFailure {
        if (value == null) {
            return Annotation.TextIcon.NOTE;
        }
        if (!(value instanceof COSName)) {
            throw invalidQuery();
        }
        String name = ((COSName) value).getName();
        for (Annotation.TextIcon icon : Annotation.TextIcon.values()) {
            if (textIconName(icon).equals(name)) {
                return icon;
            }
        }
        throw invalidQuery();
    }

    private static String textIconName(Annotation.TextIcon icon) {
        switch (icon) {
            case COMMENT: return "Comment";
            case KEY: return "Key";
            case NOTE: return "Note";
            case HELP: return "Help";
            case NEW_PARAGRAPH: return "NewParagraph";
            case PARAGRAPH: return "Paragraph";
            default: return "Insert";
        }
    }

    private static Annotation.FileAttachmentIcon fileAttachmentIcon(
            COSBase value) throws DocumentFailure {
        if (value == null) {
            return Annotation.FileAttachmentIcon.PUSHPIN;
        }
        if (!(value instanceof COSName)) {
            throw invalidQuery();
        }
        String name = ((COSName) value).getName();
        for (Annotation.FileAttachmentIcon icon
                : Annotation.FileAttachmentIcon.values()) {
            if (fileAttachmentIconName(icon).equals(name)) {
                return icon;
            }
        }
        throw invalidQuery();
    }

    private static String fileAttachmentIconName(
            Annotation.FileAttachmentIcon icon) {
        switch (icon) {
            case GRAPH: return "Graph";
            case PUSHPIN: return "PushPin";
            case PAPERCLIP: return "Paperclip";
            default: return "Tag";
        }
    }

    private static COSName mimeSubtypeName(String mimeSubtype)
            throws DocumentFailure {
        StringBuilder encoded = new StringBuilder(mimeSubtype.length() + 4);
        for (int index = 0; index < mimeSubtype.length(); index++) {
            char character = mimeSubtype.charAt(index);
            if (character > 0x7e) {
                throw invalidCommand();
            }
            if (character <= 0x20
                    || "()<>[]{}/%#".indexOf(character) >= 0) {
                encoded.append('#');
                encoded.append(HEX_DIGITS[(character >> 4) & 0xf]);
                encoded.append(HEX_DIGITS[character & 0xf]);
            } else {
                encoded.append(character);
            }
        }
        try {
            return COSName.getPDFName(encoded.toString());
        } catch (RuntimeException invalid) {
            throw invalidCommand();
        }
    }

    private static String mimeSubtypeFromName(COSName name) {
        String encoded = name.getName();
        StringBuilder decoded = new StringBuilder(encoded.length());
        for (int index = 0; index < encoded.length(); index++) {
            char character = encoded.charAt(index);
            if (character == '#' && index + 2 < encoded.length()) {
                int high = Character.digit(encoded.charAt(index + 1), 16);
                int low = Character.digit(encoded.charAt(index + 2), 16);
                if (high >= 0 && low >= 0) {
                    decoded.append((char) (high * 16 + low));
                    index += 2;
                    continue;
                }
            }
            decoded.append(character);
        }
        return decoded.toString();
    }

    private static COSName relationshipName(
            EmbeddedFile.Relationship relationship) {
        switch (relationship) {
            case SOURCE: return COSName.getPDFName("Source");
            case DATA: return COSName.getPDFName("Data");
            case ALTERNATIVE: return COSName.getPDFName("Alternative");
            default: return COSName.getPDFName("Supplement");
        }
    }

    private static EmbeddedFile.Relationship relationship(COSBase value) {
        if (!(value instanceof COSName)) {
            return null;
        }
        String name = ((COSName) value).getName();
        if ("Source".equals(name)) {
            return EmbeddedFile.Relationship.SOURCE;
        }
        if ("Data".equals(name)) {
            return EmbeddedFile.Relationship.DATA;
        }
        if ("Alternative".equals(name)) {
            return EmbeddedFile.Relationship.ALTERNATIVE;
        }
        if ("Supplement".equals(name)) {
            return EmbeddedFile.Relationship.SUPPLEMENT;
        }
        if ("Unspecified".equals(name)) {
            return EmbeddedFile.Relationship.UNSPECIFIED;
        }
        return null;
    }

    static String identifierOf(
            COSDictionary dictionary,
            boolean optional) throws DocumentFailure {
        COSBase value = dereference(dictionary.getItem(NM));
        if (value == null && optional) {
            return null;
        }
        if (!(value instanceof COSString)
                || ((COSString) value).getString().isEmpty()) {
            throw optional ? invalidCommand() : invalidQuery();
        }
        return ((COSString) value).getString();
    }

    List<COSBase> pageReferencesForCommand() throws DocumentFailure {
        try {
            return metadataOperations.rawPageReferences(
                    document,
                    PdfBoxMetadataOperations.StructureFailure.COMMAND);
        } catch (DocumentFailure failure) {
            throw invalidCommand();
        }
    }

    List<COSBase> pageReferencesForQuery() throws DocumentFailure {
        try {
            return metadataOperations.rawPageReferences(
                    document,
                    PdfBoxMetadataOperations.StructureFailure.QUERY);
        } catch (DocumentFailure failure) {
            throw invalidQuery();
        }
    }

    static COSDictionary dictionary(COSBase raw)
            throws DocumentFailure {
        COSBase value = dereference(raw);
        if (!(value instanceof COSDictionary)) {
            throw invalidQuery();
        }
        return (COSDictionary) value;
    }

    static void requireOnlyKeys(
            COSDictionary dictionary,
            String... names) throws DocumentFailure {
        for (COSName key : dictionary.keySet()) {
            boolean found = false;
            for (String name : names) {
                if (COSName.getPDFName(name).equals(key)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw invalidQuery();
            }
        }
    }

    static COSBase dereference(COSBase value) {
        return value instanceof COSObject
                ? ((COSObject) value).getObject()
                : value;
    }

    private static DocumentFailure invalidCommand() {
        return failure(
                DocumentFailureCode.ANNOTATION_INVALID,
                "The supported annotations could not be updated safely.");
    }

    private static DocumentFailure invalidQuery() {
        return failure(
                DocumentFailureCode.QUERY_FAILED,
                "The supported annotations could not be inspected safely.");
    }

    private static DocumentFailure limitExceeded() {
        return failure(
                DocumentFailureCode.ANNOTATION_LIMIT_EXCEEDED,
                "The annotation query exceeded a caller-declared bound.");
    }

    static DocumentFailure annotationNotFound() {
        return failure(
                DocumentFailureCode.ANNOTATION_NOT_FOUND,
                "An annotation selected for removal does not exist.");
    }

    static DocumentFailure flatteningUnsupported() {
        return failure(
                DocumentFailureCode.ANNOTATION_FLATTENING_UNSUPPORTED,
                "The annotation cannot be flattened safely under the version-1 contract.");
    }

    static DocumentFailure invalidActionCommand() {
        return failure(
                DocumentFailureCode.ACTION_INVALID,
                "The supported Actions could not be updated safely.");
    }

    static DocumentFailure invalidActionQuery() {
        return failure(
                DocumentFailureCode.QUERY_FAILED,
                "The supported Actions could not be inspected safely.");
    }

    static DocumentFailure actionLimitExceeded() {
        return failure(
                DocumentFailureCode.ACTION_LIMIT_EXCEEDED,
                "The Action query exceeded its caller-declared bound.");
    }

    static DocumentFailure preservationUnsupported() {
        return failure(
                DocumentFailureCode.PRESERVATION_UNSUPPORTED,
                "The document contains annotation or Action structures that this page operation cannot preserve safely.");
    }

    static DocumentFailure destinationConflict() {
        return failure(
                DocumentFailureCode.DESTINATION_CONFLICT,
                "A destination removal conflicts with an existing managed annotation or Action target.");
    }

    private static DocumentFailure failure(
            DocumentFailureCode code,
            String diagnostic) {
        return new DocumentFailure(code, CAPABILITY_ID, diagnostic);
    }

    static final class ByteBudget {

        private final long maximum;
        private long consumed;

        ByteBudget(long maximum) {
            this.maximum = maximum;
        }

        void consume(int count) throws DocumentFailure {
            if (count < 0 || consumed > maximum - count) {
                throw limitExceeded();
            }
            consumed += count;
        }
    }

}
