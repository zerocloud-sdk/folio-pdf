package net.zerocloud.pdf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An immutable encoded source-code item in page content execution order.
 *
 * <p>One item can map to zero, one, or multiple Unicode code points. Its text
 * contribution is empty for uncertain mappings and while enclosed by an
 * {@code ActualText} replacement.</p>
 *
 * @since 0.1.0
 */
public final class TextItem {

    private final int index;
    private final CharacterMapping characterMapping;
    private final String textContribution;
    private final TextGeometry geometry;
    private final List<Integer> markedContentSequenceIds;

    TextItem(
            int index,
            CharacterMapping characterMapping,
            String textContribution,
            TextGeometry geometry,
            List<Integer> markedContentSequenceIds) {
        this.index = index;
        this.characterMapping = Objects.requireNonNull(
                characterMapping, "characterMapping");
        this.textContribution = Objects.requireNonNull(
                textContribution, "textContribution");
        this.geometry = Objects.requireNonNull(geometry, "geometry");
        this.markedContentSequenceIds = Collections.unmodifiableList(
                new ArrayList<Integer>(markedContentSequenceIds));
    }

    /** Returns the one-based page-local item index. @return index */
    public int getIndex() { return index; }

    /** Returns the selected mapped Unicode when defensible. @return Unicode */
    public Optional<String> getUnicode() {
        return characterMapping.getUnicode();
    }

    /** Returns this item's contribution to aggregate Page Text. @return text */
    public String getTextContribution() { return textContribution; }

    /** Returns the source-code mapping evidence. @return mapping */
    public CharacterMapping getCharacterMapping() { return characterMapping; }

    /** Returns the effective text geometry. @return geometry */
    public TextGeometry getGeometry() { return geometry; }

    /** Returns enclosing marked-content sequence identifiers, outermost first. */
    public List<Integer> getMarkedContentSequenceIds() {
        return markedContentSequenceIds;
    }
}
