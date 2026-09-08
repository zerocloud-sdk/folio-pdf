package net.zerocloud.pdf.itext7.kernel.pdf;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayDeque;
import java.util.Deque;
import net.zerocloud.pdf.PdfName;
import net.zerocloud.pdf.PdfNumber;
import net.zerocloud.pdf.PdfValuePath;

/** Shared attachment state for all views of one direct value location. */
final class FacadeLocation {
    private final FacadeLocation parent;
    private final PdfValuePath root;
    private final PdfName name;
    private int index;
    private boolean attached = true;
    private Map<PdfName, FacadeLocation> dictionary;
    private Map<Integer, FacadeLocation> array;
    PdfNumber number;

    FacadeLocation(PdfValuePath root) {
        this.root = root;
        parent = null;
        name = null;
    }

    private FacadeLocation(FacadeLocation parent, PdfName name, int index) {
        this.parent = parent;
        this.name = name;
        this.index = index;
        root = null;
    }

    FacadeLocation dictionaryEntry(PdfName key) {
        if (dictionary == null) {
            dictionary = new HashMap<PdfName, FacadeLocation>();
        }
        FacadeLocation child = dictionary.get(key);
        if (child == null) {
            child = new FacadeLocation(this, key, 0);
            dictionary.put(key, child);
        }
        return child;
    }

    FacadeLocation arrayElement(int position) {
        if (array == null) {
            array = new HashMap<Integer, FacadeLocation>();
        }
        FacadeLocation child = array.get(Integer.valueOf(position));
        if (child == null) {
            child = new FacadeLocation(this, null, position);
            array.put(Integer.valueOf(position), child);
        }
        return child;
    }

    boolean isAttached() {
        for (FacadeLocation current = this; current != null; current = current.parent) {
            if (!current.attached) {
                return false;
            }
        }
        return true;
    }

    PdfValuePath path() {
        if (!isAttached()) {
            throw new IllegalStateException("The facade value is no longer attached to this document.");
        }
        Deque<FacadeLocation> steps = new ArrayDeque<FacadeLocation>();
        FacadeLocation current = this;
        while (current.parent != null) {
            steps.push(current);
            current = current.parent;
        }
        PdfValuePath path = current.root;
        while (!steps.isEmpty()) {
            FacadeLocation step = steps.pop();
            path = step.name == null ? path.arrayElement(step.index) : path.dictionaryEntry(step.name);
        }
        return path;
    }

    void dictionaryChanged(PdfName key) {
        if (dictionary != null) {
            detach(dictionary.remove(key));
        }
    }

    void arrayReplaced(int position) {
        if (array != null) {
            detach(array.remove(Integer.valueOf(position)));
        }
    }

    void arrayInserted(int position) {
        shift(position, 1);
    }

    void arrayRemoved(int position) {
        arrayReplaced(position);
        shift(position + 1, -1);
    }

    private void shift(int from, int by) {
        if (array == null || array.isEmpty()) {
            return;
        }
        Map<Integer, FacadeLocation> shifted = new HashMap<Integer, FacadeLocation>(array.size());
        for (FacadeLocation child : array.values()) {
            if (child.index >= from) {
                child.index += by;
            }
            shifted.put(Integer.valueOf(child.index), child);
        }
        array = shifted;
    }

    private static void detach(FacadeLocation value) {
        if (value != null) {
            value.attached = false;
        }
    }
}
