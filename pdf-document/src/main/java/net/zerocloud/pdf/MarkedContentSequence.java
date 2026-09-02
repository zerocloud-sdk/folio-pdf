package net.zerocloud.pdf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * One detached page-owned marked-content sequence in begin-operator order.
 *
 * <p>The owning {@link PageText} supplies the page relationship. Text-item
 * indices and the optional parent identifier make content nesting explicit.
 * Alternate text and {@code ActualText} replacement text are retained as
 * distinct values.</p>
 *
 * @since 0.1.0
 */
public final class MarkedContentSequence {

    private final int id;
    private final String tag;
    private final Integer markedContentId;
    private final Integer parentId;
    private final String language;
    private final String alternateText;
    private final String actualText;
    private final List<Integer> textItemIndices;

    MarkedContentSequence(
            int id,
            String tag,
            Integer markedContentId,
            Integer parentId,
            String language,
            String alternateText,
            String actualText,
            List<Integer> textItemIndices) {
        this.id = id;
        this.tag = tag;
        this.markedContentId = markedContentId;
        this.parentId = parentId;
        this.language = language;
        this.alternateText = alternateText;
        this.actualText = actualText;
        this.textItemIndices = Collections.unmodifiableList(
                new ArrayList<Integer>(textItemIndices));
    }

    /** @return the one-based page-local sequence identifier */
    public int getId() { return id; }

    /** @return the PDF marked-content tag */
    public String getTag() { return tag; }

    /** @return the optional PDF marked-content identifier ({@code MCID}) */
    public Optional<Integer> getMarkedContentId() {
        return Optional.ofNullable(markedContentId);
    }

    /** @return the optional enclosing page-local sequence identifier */
    public Optional<Integer> getParentId() {
        return Optional.ofNullable(parentId);
    }

    /** @return a directly declared marked-content language, when present */
    public Optional<String> getLanguage() {
        return Optional.ofNullable(language);
    }

    /** @return directly declared alternate text, when present */
    public Optional<String> getAlternateText() {
        return Optional.ofNullable(alternateText);
    }

    /** @return directly declared {@code ActualText}, when present */
    public Optional<String> getActualText() {
        return Optional.ofNullable(actualText);
    }

    /** @return immutable page-local text-item indices in execution order */
    public List<Integer> getTextItemIndices() { return textItemIndices; }
}
