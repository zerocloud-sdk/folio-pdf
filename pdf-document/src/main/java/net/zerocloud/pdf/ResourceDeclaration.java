package net.zerocloud.pdf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One deterministic page-relative declaration location for a resource.
 *
 * <p>For a direct resource this is a location only, not fabricated object
 * identity.</p>
 *
 * @since 0.1.0
 */
public final class ResourceDeclaration {

    private final int pageNumber;
    private final List<Segment> path;

    ResourceDeclaration(int pageNumber, List<Segment> path) {
        if (pageNumber < 1) {
            throw new IllegalArgumentException("pageNumber must be positive");
        }
        this.pageNumber = pageNumber;
        this.path = Collections.unmodifiableList(
                new ArrayList<Segment>(Objects.requireNonNull(path, "path")));
        if (this.path.isEmpty()) {
            throw new IllegalArgumentException("path must not be empty");
        }
    }

    /** Returns the one-based page number. @return the page number */
    public int getPageNumber() {
        return pageNumber;
    }

    /** Returns the path from page Resources to the declaration. @return path */
    public List<Segment> getPath() {
        return path;
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof ResourceDeclaration
                && pageNumber == ((ResourceDeclaration) candidate).pageNumber
                && path.equals(((ResourceDeclaration) candidate).path);
    }

    @Override
    public int hashCode() {
        return 31 * pageNumber + path.hashCode();
    }

    @Override
    public String toString() {
        return "ResourceDeclaration[page=" + pageNumber + ", path=" + path + "]";
    }

    /** One category/name step in a declaration path. @since 0.1.0 */
    public static final class Segment {

        private final PdfName category;
        private final PdfName name;

        Segment(PdfName category, PdfName name) {
            this.category = Objects.requireNonNull(category, "category");
            this.name = Objects.requireNonNull(name, "name");
        }

        /** Returns the Resources category or relationship name. @return category */
        public PdfName getCategory() {
            return category;
        }

        /** Returns the declared resource name. @return name */
        public PdfName getName() {
            return name;
        }

        @Override
        public boolean equals(Object candidate) {
            return candidate instanceof Segment
                    && category.equals(((Segment) candidate).category)
                    && name.equals(((Segment) candidate).name);
        }

        @Override
        public int hashCode() {
            return 31 * category.hashCode() + name.hashCode();
        }

        @Override
        public String toString() {
            return category + ":" + name;
        }
    }
}
