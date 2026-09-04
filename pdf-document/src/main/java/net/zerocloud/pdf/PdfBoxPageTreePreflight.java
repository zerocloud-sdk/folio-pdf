package net.zerocloud.pdf;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.cos.COSStream;

/** Validates and bounds a page tree without recursive PDFBox traversal. */
final class PdfBoxPageTreePreflight {

    private PdfBoxPageTreePreflight() {
    }

    static List<PageView> pages(
            COSDictionary root,
            int maximumPages,
            int maximumNodes)
            throws IOException, LimitExceededException {
        try {
            return pages(root, maximumPages, maximumNodes, null);
        } catch (DocumentFailure impossible) {
            throw new IllegalStateException(
                    "No workflow resource context was supplied.",
                    impossible);
        }
    }

    static List<PageView> pages(
            COSDictionary root,
            int maximumPages,
            int maximumNodes,
            WorkflowResourceContext resources)
            throws IOException, LimitExceededException, DocumentFailure {
        if (maximumPages < 0 || maximumNodes < 0) {
            throw new IllegalArgumentException("maximums must not be negative");
        }
        NodeCounter nodes = new NodeCounter(maximumNodes);
        nodes.add(1);
        requireRoot(root);
        IdentityHashMap<COSDictionary, Boolean> visited =
                new IdentityHashMap<COSDictionary, Boolean>();
        visited.put(root, Boolean.TRUE);
        List<PageView> pages =
                new ArrayList<PageView>(Math.min(maximumPages, 1024));
        Deque<Frame> stack = new ArrayDeque<Frame>();
        if (resources != null) {
            resources.checkpoint();
            resources.requireNestingDepth(1L);
        }
        stack.push(frame(root, nodes, InheritedAttributes.empty(), 1));

        while (!stack.isEmpty()) {
            if (resources != null) {
                resources.checkpoint();
            }
            Frame current = stack.peek();
            if (current.index == current.children.size()) {
                if (current.declaredPages != current.observedPages) {
                    throw new IOException("Page-tree Count is inconsistent");
                }
                stack.pop();
                if (!stack.isEmpty()) {
                    stack.peek().observedPages += current.observedPages;
                }
                continue;
            }

            COSBase value = current.children.getObject(current.index++);
            if (!(value instanceof COSDictionary)
                    || value instanceof COSStream) {
                throw new IOException("Page-tree child is malformed");
            }
            COSDictionary child = (COSDictionary) value;
            if (visited.put(child, Boolean.TRUE) != null) {
                throw new IOException("Page-tree node is repeated or cyclic");
            }
            if (child.getDictionaryObject(COSName.PARENT) != current.node) {
                throw new IOException("Page-tree Parent is inconsistent");
            }

            COSName type = child.getCOSName(COSName.TYPE);
            if (COSName.PAGE.equals(type)) {
                if (child.getDictionaryObject(COSName.KIDS) != null) {
                    throw new IOException("Page dictionary has Kids");
                }
                if (pages.size() >= maximumPages) {
                    throw new LimitExceededException();
                }
                pages.add(new PageView(
                        child,
                        current.attributes.pageView(child)));
                current.observedPages++;
            } else if (COSName.PAGES.equals(type)) {
                int childDepth = current.depth + 1;
                if (resources != null) {
                    resources.requireNestingDepth(childDepth);
                }
                stack.push(frame(
                        child,
                        nodes,
                        current.attributes,
                        childDepth));
            } else {
                throw new IOException("Page-tree node Type is malformed");
            }
        }
        return pages;
    }

    private static void requireRoot(COSDictionary root) throws IOException {
        if (root == null
                || root instanceof COSStream
                || !COSName.PAGES.equals(root.getCOSName(COSName.TYPE))
                || root.getDictionaryObject(COSName.PARENT) != null) {
            throw new IOException("Page-tree root is malformed");
        }
    }

    private static Frame frame(
            COSDictionary node,
            NodeCounter nodes,
            InheritedAttributes inherited,
            int depth)
            throws IOException, LimitExceededException {
        COSBase children = node.getDictionaryObject(COSName.KIDS);
        COSBase count = node.getDictionaryObject(COSName.COUNT);
        if (!(children instanceof COSArray)
                || !(count instanceof COSInteger)) {
            throw new IOException("Pages node is malformed");
        }
        long declaredPageCount = ((COSInteger) count).longValue();
        if (declaredPageCount < 0L
                || declaredPageCount > Integer.MAX_VALUE) {
            throw new IOException("Page-tree Count is out of range");
        }
        int declaredPages = (int) declaredPageCount;
        COSArray array = (COSArray) children;
        nodes.add(array.size());
        return new Frame(
                node,
                array,
                declaredPages,
                inherited.extend(node),
                depth);
    }

    static final class PageView {

        private final COSDictionary source;
        private final COSDictionary effective;

        PageView(COSDictionary source, COSDictionary effective) {
            this.source = source;
            this.effective = effective;
        }

        COSDictionary source() {
            return source;
        }

        COSDictionary effective() {
            return effective;
        }
    }

    static final class LimitExceededException extends Exception {

        private static final long serialVersionUID = 1L;
    }

    private static final class Frame {

        private final COSDictionary node;
        private final COSArray children;
        private final int declaredPages;
        private final InheritedAttributes attributes;
        private final int depth;
        private int index;
        private int observedPages;

        Frame(
                COSDictionary node,
                COSArray children,
                int declaredPages,
                InheritedAttributes attributes,
                int depth) {
            this.node = node;
            this.children = children;
            this.declaredPages = declaredPages;
            this.attributes = attributes;
            this.depth = depth;
        }
    }

    private static final class InheritedAttributes {

        private final COSBase resources;
        private final COSBase mediaBox;
        private final COSBase cropBox;
        private final COSBase rotate;

        InheritedAttributes(
                COSBase resources,
                COSBase mediaBox,
                COSBase cropBox,
                COSBase rotate) {
            this.resources = resources;
            this.mediaBox = mediaBox;
            this.cropBox = cropBox;
            this.rotate = rotate;
        }

        static InheritedAttributes empty() {
            return new InheritedAttributes(null, null, null, null);
        }

        InheritedAttributes extend(COSDictionary dictionary) throws IOException {
            COSBase localResources = dictionary.getDictionaryObject(
                    COSName.RESOURCES);
            COSBase localMediaBox = dictionary.getDictionaryObject(
                    COSName.MEDIA_BOX);
            COSBase localCropBox = dictionary.getDictionaryObject(
                    COSName.CROP_BOX);
            COSBase localRotate = dictionary.getDictionaryObject(COSName.ROTATE);
            requireResources(localResources);
            requireRectangle(localMediaBox, "MediaBox");
            requireRectangle(localCropBox, "CropBox");
            requireRotation(localRotate);
            return new InheritedAttributes(
                    localResources == null ? resources : localResources,
                    localMediaBox == null ? mediaBox : localMediaBox,
                    localCropBox == null ? cropBox : localCropBox,
                    localRotate == null ? rotate : localRotate);
        }

        COSDictionary pageView(COSDictionary page) throws IOException {
            InheritedAttributes effective = extend(page);
            if (effective.mediaBox == null) {
                throw new IOException("Page has no MediaBox");
            }
            requireUserUnit(page.getDictionaryObject(COSName.USER_UNIT));
            COSDictionary view = new COSDictionary(page);
            effective.set(view, COSName.RESOURCES, effective.resources);
            effective.set(view, COSName.MEDIA_BOX, effective.mediaBox);
            effective.set(
                    view,
                    COSName.CROP_BOX,
                    effective.cropBox == null
                            ? effective.mediaBox
                            : effective.cropBox);
            effective.set(
                    view,
                    COSName.ROTATE,
                    effective.rotate == null ? COSInteger.ZERO : effective.rotate);
            view.removeItem(COSName.PARENT);
            view.removeItem(COSName.P);
            return view;
        }

        private static void requireResources(COSBase value) throws IOException {
            if (value != null
                    && (!(value instanceof COSDictionary)
                            || value instanceof COSStream)) {
                throw new IOException("Page Resources is not a dictionary");
            }
        }

        private static void requireRectangle(COSBase value, String name)
                throws IOException {
            if (value == null) {
                return;
            }
            if (!(value instanceof COSArray) || ((COSArray) value).size() != 4) {
                throw new IOException(name + " is not a four-number rectangle");
            }
            COSArray rectangle = (COSArray) value;
            for (int index = 0; index < rectangle.size(); index++) {
                requireFiniteNumber(rectangle.getObject(index), name);
            }
        }

        private static void requireRotation(COSBase value) throws IOException {
            if (value == null) {
                return;
            }
            if (!(value instanceof COSInteger)) {
                throw new IOException("Page Rotate is not an integer");
            }
            long rotation = ((COSInteger) value).longValue();
            if (rotation < Integer.MIN_VALUE
                    || rotation > Integer.MAX_VALUE
                    || rotation % 90L != 0L) {
                throw new IOException("Page Rotate is not a supported rotation");
            }
        }

        private static void requireUserUnit(COSBase value) throws IOException {
            if (value == null) {
                return;
            }
            float userUnit = requireFiniteNumber(value, "UserUnit");
            if (userUnit <= 0f || userUnit > 75000f) {
                throw new IOException("Page UserUnit is outside its valid range");
            }
        }

        private static float requireFiniteNumber(COSBase value, String name)
                throws IOException {
            if (!(value instanceof COSNumber)) {
                throw new IOException(name + " contains a non-number");
            }
            float number = ((COSNumber) value).floatValue();
            if (Float.isNaN(number) || Float.isInfinite(number)) {
                throw new IOException(name + " contains a non-finite number");
            }
            return number;
        }

        private void set(
                COSDictionary dictionary,
                COSName name,
                COSBase value) {
            if (value != null) {
                dictionary.setItem(name, value);
            }
        }
    }

    private static final class NodeCounter {

        private final int maximum;
        private int value;

        NodeCounter(int maximum) {
            this.maximum = maximum;
        }

        void add(int count) throws LimitExceededException {
            if (count < 0 || count > maximum - (long) value) {
                throw new LimitExceededException();
            }
            value += count;
        }
    }
}
