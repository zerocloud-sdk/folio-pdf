package net.zerocloud.pdf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Detached immutable page-text and logical-structure extraction result.
 *
 * <p>The complete result remains usable after its evaluating Document Session
 * ends.</p>
 *
 * @since 0.1.0
 */
public final class TextStructureExtraction {

    private final List<PageText> pages;
    private final List<LogicalStructureElement> structureRoots;
    private final List<ExtractionDiagnostic> diagnostics;

    TextStructureExtraction(
            List<PageText> pages,
            List<LogicalStructureElement> structureRoots,
            List<ExtractionDiagnostic> diagnostics) {
        this.pages = Collections.unmodifiableList(new ArrayList<PageText>(pages));
        this.structureRoots = Collections.unmodifiableList(
                new ArrayList<LogicalStructureElement>(structureRoots));
        this.diagnostics = Collections.unmodifiableList(
                new ArrayList<ExtractionDiagnostic>(diagnostics));
    }

    /** Returns Page Text in one-based page order. @return immutable pages */
    public List<PageText> getPages() { return pages; }

    /** Returns structure roots in structure-tree order. @return immutable roots */
    public List<LogicalStructureElement> getStructureRoots() { return structureRoots; }

    /** Returns safe mapping diagnostics in page and text-item order. @return values */
    public List<ExtractionDiagnostic> getDiagnostics() { return diagnostics; }
}
