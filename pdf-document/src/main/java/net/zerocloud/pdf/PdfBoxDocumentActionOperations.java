package net.zerocloud.pdf;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.zerocloud.pdf.command.UpdateActions;
import net.zerocloud.pdf.query.Actions;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;

/** Reads and updates the complete version-1 local GoTo Action allowlist. */
final class PdfBoxDocumentActionOperations {

    private static final COSName OPEN_ACTION =
            COSName.getPDFName("OpenAction");
    private static final COSName AA = COSName.getPDFName("AA");
    private static final COSName O = COSName.getPDFName("O");
    private static final COSName C = COSName.getPDFName("C");

    private final PDDocument document;
    private final PdfBoxMetadataOperations metadataOperations;
    private final PdfBoxAnnotationOperations annotationOperations;
    private final WorkflowResourceContext resources;

    PdfBoxDocumentActionOperations(
            PDDocument document,
            PdfBoxMetadataOperations metadataOperations,
            PdfBoxAnnotationOperations annotationOperations,
            WorkflowResourceContext resources) {
        this.document = document;
        this.metadataOperations = metadataOperations;
        this.annotationOperations = annotationOperations;
        this.resources = resources;
    }

    void update(UpdateActions command) throws DocumentFailure {
        resources.checkpoint();
        List<COSBase> pageReferences = pageReferencesForCommand();
        int pageCount = pageReferences.size();
        Set<String> namedDestinations;
        try {
            namedDestinations = metadataOperations.namedDestinationNames(
                    document);
        } catch (DocumentFailure malformedDestinations) {
            resources.rethrowTerminalFailure();
            throw invalidActionCommand();
        }
        requireActionTargets(command, pageCount, namedDestinations);

        Map<Integer, COSDictionary> pageReplacements =
                new LinkedHashMap<Integer, COSDictionary>();
        Set<Integer> touchedPages = new HashSet<Integer>();
        addTouchedPages(touchedPages, command.getPageOpenActions().keySet());
        addTouchedPages(touchedPages, command.getPageCloseActions().keySet());
        addTouchedPages(touchedPages, command.getRemovedPageOpenActions());
        addTouchedPages(touchedPages, command.getRemovedPageCloseActions());
        for (Integer pageNumber : touchedPages) {
            resources.checkpoint();
            if (pageNumber.intValue() > pageCount) {
                throw invalidActionCommand();
            }
            COSDictionary page = dictionary(
                    pageReferences.get(pageNumber.intValue() - 1));
            COSBase rawAa = dereference(page.getItem(AA));
            COSDictionary replacement = new COSDictionary();
            if (rawAa != null) {
                if (!(rawAa instanceof COSDictionary)
                        || rawAa instanceof COSStream) {
                    throw invalidActionCommand();
                }
                try {
                    requireOnlyKeys((COSDictionary) rawAa, "O", "C");
                } catch (DocumentFailure invalid) {
                    resources.rethrowTerminalFailure();
                    throw invalidActionCommand();
                }
                COSDictionary existing = (COSDictionary) rawAa;
                for (COSName name : existing.keySet()) {
                    resources.checkpoint();
                    replacement.setItem(name, existing.getItem(name));
                }
            }
            GoToAction open = command.getPageOpenActions().get(pageNumber);
            if (open != null) {
                replacement.setItem(O,
                        backendAction(open.getTarget(), pageReferences));
            } else if (command.getRemovedPageOpenActions()
                    .contains(pageNumber)) {
                replacement.removeItem(O);
            }
            GoToAction close = command.getPageCloseActions().get(pageNumber);
            if (close != null) {
                replacement.setItem(C,
                        backendAction(close.getTarget(), pageReferences));
            } else if (command.getRemovedPageCloseActions()
                    .contains(pageNumber)) {
                replacement.removeItem(C);
            }
            pageReplacements.put(pageNumber, replacement);
        }

        COSDictionary openAction = null;
        if (command.isDocumentOpenActionUpdated()
                && command.getDocumentOpenAction() != null) {
            openAction = backendAction(
                    command.getDocumentOpenAction().getTarget(),
                    pageReferences);
        }

        COSDictionary catalog = document.getDocumentCatalog().getCOSObject();
        if (command.isDocumentOpenActionUpdated()) {
            if (openAction == null) {
                catalog.removeItem(OPEN_ACTION);
            } else {
                catalog.setItem(OPEN_ACTION, openAction);
            }
        }
        for (Map.Entry<Integer, COSDictionary> entry
                : pageReplacements.entrySet()) {
            resources.checkpoint();
            COSDictionary page = dictionary(
                    pageReferences.get(entry.getKey().intValue() - 1));
            if (entry.getValue().size() == 0) {
                page.removeItem(AA);
            } else {
                page.setItem(AA, entry.getValue());
            }
        }
    }

    private void addTouchedPages(
            Set<Integer> target,
            Iterable<Integer> pages) throws DocumentFailure {
        for (Integer page : pages) {
            resources.checkpoint();
            target.add(page);
        }
    }

    private void requireActionTargets(
            UpdateActions command,
            int pageCount,
            Set<String> namedDestinations) throws DocumentFailure {
        if (command.getDocumentOpenAction() != null) {
            requireActionTarget(
                    command.getDocumentOpenAction(),
                    pageCount,
                    namedDestinations);
        }
        for (GoToAction action : command.getPageOpenActions().values()) {
            resources.checkpoint();
            requireActionTarget(action, pageCount, namedDestinations);
        }
        for (GoToAction action : command.getPageCloseActions().values()) {
            resources.checkpoint();
            requireActionTarget(action, pageCount, namedDestinations);
        }
    }

    private static void requireActionTarget(
            GoToAction action,
            int pageCount,
            Set<String> namedDestinations) throws DocumentFailure {
        if (action.getTarget().getKind() == NavigationTarget.Kind.PAGE
                && action.getTarget().getPageDestination().get()
                        .getPageNumber() > pageCount) {
            throw invalidActionCommand();
        }
        if (!isKnownNamedTarget(action.getTarget(), namedDestinations)) {
            throw invalidActionCommand();
        }
    }

    DocumentActions evaluate(Actions query) throws DocumentFailure {
        resources.checkpoint();
        List<COSBase> pageReferences = pageReferencesForQuery();
        Set<String> namedDestinations;
        try {
            namedDestinations = metadataOperations.namedDestinationNames(
                    document);
        } catch (DocumentFailure invalidDestinations) {
            resources.rethrowTerminalFailure();
            throw invalidActionQuery();
        }
        IdentityHashMap<COSDictionary, Integer> pageNumbers =
                new IdentityHashMap<COSDictionary, Integer>();
        for (int index = 0; index < pageReferences.size(); index++) {
            resources.checkpoint();
            pageNumbers.put(dictionary(pageReferences.get(index)),
                    Integer.valueOf(index + 1));
        }
        ActionBudget budget = new ActionBudget(
                query.getMaximumActions(), resources);
        COSDictionary catalog = document.getDocumentCatalog().getCOSObject();
        GoToAction documentOpen = null;
        COSBase rawOpen = catalog.getItem(OPEN_ACTION);
        if (rawOpen != null) {
            budget.consume();
            NavigationTarget target = actionTarget(rawOpen, pageNumbers);
            if (!isKnownNamedTarget(target, namedDestinations)) {
                throw invalidActionQuery();
            }
            documentOpen = GoToAction.version1(target);
        }
        List<PageActions> pages = new ArrayList<PageActions>();
        for (int pageIndex = 0; pageIndex < pageReferences.size(); pageIndex++) {
            resources.checkpoint();
            COSDictionary page = dictionary(pageReferences.get(pageIndex));
            COSBase rawAa = dereference(page.getItem(AA));
            if (rawAa == null) {
                continue;
            }
            if (!(rawAa instanceof COSDictionary)
                    || rawAa instanceof COSStream) {
                throw invalidActionQuery();
            }
            COSDictionary aa = (COSDictionary) rawAa;
            try {
                requireOnlyKeys(aa, "O", "C");
            } catch (DocumentFailure invalid) {
                resources.rethrowTerminalFailure();
                throw invalidActionQuery();
            }
            GoToAction open = null;
            GoToAction close = null;
            if (aa.getItem(O) != null) {
                budget.consume();
                NavigationTarget target = actionTarget(
                        aa.getItem(O), pageNumbers);
                if (!isKnownNamedTarget(target, namedDestinations)) {
                    throw invalidActionQuery();
                }
                open = GoToAction.version1(target);
            }
            if (aa.getItem(C) != null) {
                budget.consume();
                NavigationTarget target = actionTarget(
                        aa.getItem(C), pageNumbers);
                if (!isKnownNamedTarget(target, namedDestinations)) {
                    throw invalidActionQuery();
                }
                close = GoToAction.version1(target);
            }
            if (open != null || close != null) {
                pages.add(new PageActions(pageIndex + 1, open, close));
            }
        }
        return new DocumentActions(documentOpen, pages);
    }


    private List<COSBase> pageReferencesForCommand()
            throws DocumentFailure {
        return annotationOperations.pageReferencesForCommand();
    }

    private List<COSBase> pageReferencesForQuery()
            throws DocumentFailure {
        return annotationOperations.pageReferencesForQuery();
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

    private static COSDictionary dictionary(COSBase raw)
            throws DocumentFailure {
        return PdfBoxAnnotationOperations.dictionary(raw);
    }

    private static COSBase dereference(COSBase value) {
        return PdfBoxAnnotationOperations.dereference(value);
    }

    private void requireOnlyKeys(
            COSDictionary dictionary,
            String... names) throws DocumentFailure {
        annotationOperations.requireOnlyKeys(dictionary, names);
    }

    private static boolean isKnownNamedTarget(
            NavigationTarget target,
            Set<String> namedDestinations) {
        return PdfBoxAnnotationOperations.isKnownNamedTarget(
                target,
                namedDestinations);
    }

    private static DocumentFailure invalidActionCommand() {
        return PdfBoxAnnotationOperations.invalidActionCommand();
    }

    private static DocumentFailure invalidActionQuery() {
        return PdfBoxAnnotationOperations.invalidActionQuery();
    }

    private static DocumentFailure actionLimitExceeded() {
        return PdfBoxAnnotationOperations.actionLimitExceeded();
    }

    private static final class ActionBudget {

        private final int maximum;
        private final WorkflowResourceContext resources;
        private int consumed;

        ActionBudget(
                int maximum,
                WorkflowResourceContext resources) {
            this.maximum = maximum;
            this.resources = resources;
        }

        void consume() throws DocumentFailure {
            resources.checkpoint();
            if (consumed >= maximum) {
                throw actionLimitExceeded();
            }
            consumed++;
        }
    }
}
