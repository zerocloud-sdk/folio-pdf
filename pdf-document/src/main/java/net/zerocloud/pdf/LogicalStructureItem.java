package net.zerocloud.pdf;

import java.util.Objects;
import java.util.Optional;

/**
 * One ordered child of a Tagged PDF logical-structure element.
 *
 * @since 0.1.0
 */
public final class LogicalStructureItem {

    /** Supported version-1 logical child kinds. */
    public enum Kind {
        /** A nested logical-structure element. */
        ELEMENT,
        /** A reference to page marked content. */
        MARKED_CONTENT
    }

    private final Kind kind;
    private final LogicalStructureElement element;
    private final MarkedContentReference markedContent;

    private LogicalStructureItem(
            Kind kind,
            LogicalStructureElement element,
            MarkedContentReference markedContent) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.element = element;
        this.markedContent = markedContent;
    }

    static LogicalStructureItem element(LogicalStructureElement value) {
        return new LogicalStructureItem(
                Kind.ELEMENT,
                Objects.requireNonNull(value, "value"),
                null);
    }

    static LogicalStructureItem markedContent(MarkedContentReference value) {
        return new LogicalStructureItem(
                Kind.MARKED_CONTENT,
                null,
                Objects.requireNonNull(value, "value"));
    }

    /** @return the child kind */
    public Kind getKind() { return kind; }

    /** @return the nested element only for {@link Kind#ELEMENT} */
    public Optional<LogicalStructureElement> getElement() {
        return Optional.ofNullable(element);
    }

    /** @return the content reference only for {@link Kind#MARKED_CONTENT} */
    public Optional<MarkedContentReference> getMarkedContent() {
        return Optional.ofNullable(markedContent);
    }
}
