package net.zerocloud.pdf.acceptance;

import java.math.BigDecimal;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import net.zerocloud.pdf.CharacterMapping;
import net.zerocloud.pdf.ExtractionDiagnostic;
import net.zerocloud.pdf.LogicalObjectReference;
import net.zerocloud.pdf.LogicalStructureElement;
import net.zerocloud.pdf.LogicalStructureItem;
import net.zerocloud.pdf.MarkedContentReference;
import net.zerocloud.pdf.MarkedContentSequence;
import net.zerocloud.pdf.ObjectReference;
import net.zerocloud.pdf.PageText;
import net.zerocloud.pdf.TextGeometry;
import net.zerocloud.pdf.TextItem;
import net.zerocloud.pdf.TextStructureExtraction;

/** Serializes detached public values; it never opens PDF syntax or backend objects. */
final class T13ExtractionObservation {
    private T13ExtractionObservation() { }

    static Properties observe(TextStructureExtraction extraction) {
        Properties values = new Properties();
        values.setProperty("pages.count", Integer.toString(extraction.getPages().size()));
        for (int pageIndex = 0; pageIndex < extraction.getPages().size(); pageIndex++) {
            PageText page = extraction.getPages().get(pageIndex);
            String prefix = "pages." + pageIndex + ".";
            values.setProperty(prefix + "number", Integer.toString(page.getPageNumber()));
            values.setProperty(prefix + "rotation", Integer.toString(page.getRotation()));
            number(values, prefix + "user-unit", page.getUserUnit());
            numbers(values, prefix + "crop-box", page.getCropBoxLeft(), page.getCropBoxBottom(),
                    page.getCropBoxRight(), page.getCropBoxTop());
            values.setProperty(prefix + "text", page.getText());
            values.setProperty(prefix + "items.count", Integer.toString(page.getTextItems().size()));
            for (int itemIndex = 0; itemIndex < page.getTextItems().size(); itemIndex++) {
                TextItem item = page.getTextItems().get(itemIndex);
                String itemPrefix = prefix + "items." + itemIndex + ".";
                CharacterMapping mapping = item.getCharacterMapping();
                values.setProperty(itemPrefix + "index", Integer.toString(item.getIndex()));
                values.setProperty(itemPrefix + "source", hex(mapping.getSourceCode()));
                values.setProperty(itemPrefix + "confidence", mapping.getConfidence().name());
                optional(values, itemPrefix + "unicode", mapping.getUnicode());
                optional(values, itemPrefix + "explicit", mapping.getExplicitUnicode());
                optional(values, itemPrefix + "inferred", mapping.getInferredUnicode());
                values.setProperty(itemPrefix + "contribution", item.getTextContribution());
                values.setProperty(itemPrefix + "rendering-mode", item.getRenderingMode().name());
                TextGeometry geometry = item.getGeometry();
                numbers(values, itemPrefix + "matrix", geometry.getA(), geometry.getB(), geometry.getC(),
                        geometry.getD(), geometry.getE(), geometry.getF());
                numbers(values, itemPrefix + "advance", geometry.getAdvanceX(), geometry.getAdvanceY());
                integers(values, itemPrefix + "marked-content", item.getMarkedContentSequenceIds());
            }
            List<MarkedContentSequence> sequences = page.getMarkedContentSequences();
            values.setProperty(prefix + "marked-content.count", Integer.toString(sequences.size()));
            for (int i = 0; i < sequences.size(); i++) {
                marked(values, prefix + "marked-content." + i + ".", sequences.get(i));
            }
        }
        Map<ObjectReference, Integer> references = new LinkedHashMap<ObjectReference, Integer>();
        values.setProperty("roots.count", Integer.toString(extraction.getStructureRoots().size()));
        for (int i = 0; i < extraction.getStructureRoots().size(); i++) {
            element(values, "roots." + i + ".", extraction.getStructureRoots().get(i), references);
        }
        values.setProperty("diagnostics.count", Integer.toString(extraction.getDiagnostics().size()));
        for (int i = 0; i < extraction.getDiagnostics().size(); i++) {
            ExtractionDiagnostic diagnostic = extraction.getDiagnostics().get(i);
            String prefix = "diagnostics." + i + ".";
            values.setProperty(prefix + "code", diagnostic.getCode().name());
            values.setProperty(prefix + "page", Integer.toString(diagnostic.getPageNumber()));
            values.setProperty(prefix + "item", Integer.toString(diagnostic.getTextItemIndex()));
            values.setProperty(prefix + "source", hex(diagnostic.getSourceCode()));
            values.setProperty(prefix + "message", diagnostic.getMessage());
        }
        return values;
    }

    private static void marked(Properties values, String prefix, MarkedContentSequence sequence) {
        values.setProperty(prefix + "id", Integer.toString(sequence.getId()));
        values.setProperty(prefix + "stream", Integer.toString(sequence.getContentStreamId()));
        values.setProperty(prefix + "tag", sequence.getTag());
        optional(values, prefix + "mcid", sequence.getMarkedContentId());
        optional(values, prefix + "parent", sequence.getParentId());
        optional(values, prefix + "language", sequence.getLanguage());
        optional(values, prefix + "alternate", sequence.getAlternateText());
        optional(values, prefix + "actual", sequence.getActualText());
        integers(values, prefix + "items", sequence.getTextItemIndices());
    }

    private static void element(Properties values, String prefix, LogicalStructureElement element,
            Map<ObjectReference, Integer> references) {
        values.setProperty(prefix + "id", Integer.toString(element.getId()));
        values.setProperty(prefix + "role", element.getRole());
        values.setProperty(prefix + "role-resolution", element.getRoleResolution().name());
        optional(values, prefix + "resolved-role", element.getResolvedRole());
        optional(values, prefix + "namespace", element.getDeclaredNamespaceName());
        optional(values, prefix + "namespace-reference", element.getNamespaceReference().map(value -> reference(references, value)));
        optional(values, prefix + "resolved-namespace", element.getResolvedNamespaceName());
        optional(values, prefix + "declared-language", element.getDeclaredLanguage());
        optional(values, prefix + "effective-language", element.getEffectiveLanguage());
        values.setProperty(prefix + "language-source", element.getLanguageSource().name());
        optional(values, prefix + "alternate", element.getAlternateText());
        optional(values, prefix + "actual", element.getActualText());
        values.setProperty(prefix + "children.count", Integer.toString(element.getChildren().size()));
        for (int i = 0; i < element.getChildren().size(); i++) {
            LogicalStructureItem child = element.getChildren().get(i);
            String childPrefix = prefix + "children." + i + ".";
            values.setProperty(childPrefix + "kind", child.getKind().name());
            switch (child.getKind()) {
                case ELEMENT:
                    element(values, childPrefix + "element.", child.getElement().get(), references);
                    break;
                case MARKED_CONTENT:
                    MarkedContentReference marked = child.getMarkedContent().get();
                    String markedPrefix = childPrefix + "marked-content.";
                    values.setProperty(markedPrefix + "page", Integer.toString(marked.getPageNumber()));
                    values.setProperty(markedPrefix + "mcid", Integer.toString(marked.getMarkedContentId()));
                    values.setProperty(markedPrefix + "stream", Integer.toString(marked.getContentStreamId()));
                    optional(values, markedPrefix + "sequence", marked.getMarkedContentSequenceId());
                    optional(values, markedPrefix + "owner", marked.getStreamOwner().map(value -> reference(references, value)));
                    break;
                case OBJECT:
                    LogicalObjectReference object = child.getObjectReference().get();
                    String objectPrefix = childPrefix + "object-reference.";
                    values.setProperty(objectPrefix + "page", Integer.toString(object.getPageNumber()));
                    values.setProperty(objectPrefix + "reference", Integer.toString(reference(references, object.getObjectReference())));
                    values.setProperty(objectPrefix + "subtype", object.getSubtype());
                    break;
                default:
                    throw new IllegalArgumentException("Unqualified logical child kind");
            }
        }
    }

    private static int reference(Map<ObjectReference, Integer> references, ObjectReference value) {
        Integer identity = references.get(value);
        if (identity == null) {
            identity = references.size() + 1;
            references.put(value, identity);
        }
        return identity;
    }

    private static void optional(Properties values, String key, Optional<?> value) {
        values.setProperty(key + "-present", Boolean.toString(value.isPresent()));
        values.setProperty(key, value.isPresent() ? value.get().toString() : "");
    }

    private static void integers(Properties values, String prefix, List<Integer> elements) {
        values.setProperty(prefix + ".count", Integer.toString(elements.size()));
        for (int i = 0; i < elements.size(); i++) { values.setProperty(prefix + "." + i, elements.get(i).toString()); }
    }

    private static void numbers(Properties values, String prefix, BigDecimal... elements) {
        values.setProperty(prefix + ".count", Integer.toString(elements.length));
        for (int i = 0; i < elements.length; i++) { number(values, prefix + "." + i, elements[i]); }
    }

    private static void number(Properties values, String key, BigDecimal value) {
        values.setProperty(key, value.stripTrailingZeros().toPlainString());
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(Character.forDigit((value & 255) >>> 4, 16)).append(Character.forDigit(value & 15, 16));
        }
        return result.toString();
    }
}
