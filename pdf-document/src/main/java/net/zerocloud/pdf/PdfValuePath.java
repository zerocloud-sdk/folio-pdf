package net.zerocloud.pdf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * An immutable location reached from one Session Object Reference.
 *
 * <p>Names select dictionary entries and indices select array elements. Paths
 * resolve in Patch declaration order and do not confer new object identity.</p>
 *
 * @since 0.1.0
 */
public final class PdfValuePath {

    private final ObjectReference root;
    private final List<Step> steps;

    private PdfValuePath(ObjectReference root, List<Step> steps) {
        this.root = root;
        this.steps = Collections.unmodifiableList(steps);
    }

    /**
     * Selects the value of an indirect object.
     *
     * @param reference the Session-owned root
     * @return the root location
     */
    public static PdfValuePath root(ObjectReference reference) {
        return new PdfValuePath(Objects.requireNonNull(reference, "reference"),
                Collections.<Step>emptyList());
    }

    /**
     * Appends a dictionary-entry selection.
     *
     * @param name the entry name
     * @return the extended location
     */
    public PdfValuePath dictionaryEntry(PdfName name) {
        return append(new Step(Objects.requireNonNull(name, "name"), 0));
    }

    /**
     * Appends an array-element selection. The Session validates the index.
     *
     * @param index the zero-based element index
     * @return the extended location
     */
    public PdfValuePath arrayElement(int index) {
        return append(new Step(null, index));
    }

    /**
     * Returns the Session Object Reference anchoring this location.
     *
     * @return the root reference
     */
    public ObjectReference getRoot() {
        return root;
    }

    List<Step> getSteps() {
        return steps;
    }

    PdfValuePath parent() {
        return new PdfValuePath(root, new ArrayList<Step>(steps.subList(0, steps.size() - 1)));
    }

    private PdfValuePath append(Step step) {
        List<Step> copy = new ArrayList<Step>(steps);
        copy.add(step);
        return new PdfValuePath(root, copy);
    }

    static final class Step {
        private final PdfName name;
        private final int index;

        private Step(PdfName name, int index) {
            this.name = name;
            this.index = index;
        }

        PdfName getName() {
            return name;
        }

        int getIndex() {
            return index;
        }
    }
}
