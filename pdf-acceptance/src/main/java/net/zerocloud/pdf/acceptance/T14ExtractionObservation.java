package net.zerocloud.pdf.acceptance;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Properties;
import net.zerocloud.pdf.DocumentResource;
import net.zerocloud.pdf.DocumentResourceInventory;
import net.zerocloud.pdf.FontResource;
import net.zerocloud.pdf.ImageResource;
import net.zerocloud.pdf.ObjectReference;
import net.zerocloud.pdf.ResourceDeclaration;

/** Serializes detached public inventory values without consulting PDF syntax. */
final class T14ExtractionObservation {
    private T14ExtractionObservation() { }

    static Properties observe(DocumentResourceInventory inventory) {
        Properties values = new Properties();
        List<DocumentResource> resources = inventory.getResources();
        Map<ObjectReference, Integer> identities = new LinkedHashMap<ObjectReference, Integer>();
        put(values, "resources.count", resources.size());
        for (int index = 0; index < resources.size(); index++) {
            DocumentResource resource = resources.get(index);
            String prefix = "resources." + index + ".";
            put(values, prefix + "kind", resource.getKind());
            put(values, prefix + "indirect", resource.getObjectReference().isPresent());
            Object identity = "";
            if (resource.getObjectReference().isPresent()) {
                ObjectReference reference = resource.getObjectReference().get();
                if (!identities.containsKey(reference)) { identities.put(reference, identities.size()); }
                identity = identities.get(reference);
            }
            // First-occurrence ordinals preserve the public identity partition
            // across sessions without serializing session-specific token values.
            put(values, prefix + "identity", identity);
            put(values, prefix + "pages", join(resource.getPageUsage()));
            put(values, prefix + "declarations.count", resource.getDeclarations().size());
            for (int occurrence = 0; occurrence < resource.getDeclarations().size(); occurrence++) {
                ResourceDeclaration declaration = resource.getDeclarations().get(occurrence);
                String location = prefix + "declarations." + occurrence + ".";
                put(values, location + "page", declaration.getPageNumber());
                StringBuilder path = new StringBuilder();
                for (ResourceDeclaration.Segment segment : declaration.getPath()) {
                    if (path.length() != 0) { path.append('/'); }
                    path.append(segment.getCategory().getValue()).append('/').append(segment.getName().getValue());
                }
                put(values, location + "path", path);
            }
            if (resource instanceof FontResource) {
                FontResource font = (FontResource) resource;
                put(values, prefix + "font.kind", font.getFontKind());
                put(values, prefix + "font.status", font.getStatus());
                put(values, prefix + "font.embedding", font.getEmbedding());
                put(values, prefix + "font.base-font", font.getBaseFontName().isPresent()
                        ? font.getBaseFontName().get().getValue() : "");
                put(values, prefix + "font.subset", font.isSubset());
                put(values, prefix + "font.subset-prefix", font.getSubsetPrefix().orElse(""));
            }
            if (resource instanceof ImageResource) {
                image(values, prefix + "image.", (ImageResource) resource, resources);
            }
        }
        return values;
    }

    private static void image(Properties values, String prefix, ImageResource image, List<DocumentResource> resources) {
        put(values, prefix + "width", image.getWidth());
        put(values, prefix + "height", image.getHeight());
        optional(values, prefix + "bits", image.getBitsPerComponent());
        optional(values, prefix + "components", image.getColorComponents());
        put(values, prefix + "image-mask", image.isImageMask());
        put(values, prefix + "embedded-soft-mask", image.getEmbeddedSoftMask());
        ImageResource.ColorSpace color = image.getColorSpace();
        put(values, prefix + "color.family", color.getFamily());
        put(values, prefix + "color.status", color.getStatus());
        put(values, prefix + "color.declared", color.getDeclaredName().isPresent() ? color.getDeclaredName().get().getValue() : "");
        put(values, prefix + "color.resolved", color.getResolvedFamilyName().isPresent() ? color.getResolvedFamilyName().get().getValue() : "");
        optional(values, prefix + "color.components", color.getComponents());
        put(values, prefix + "color.icc-indirect", color.getIccProfile().isPresent()
                ? color.getIccProfile().get().getObjectReference().isPresent() : "");
        put(values, prefix + "color.icc-length", color.getIccProfile().isPresent() ? color.getIccProfile().get().getByteLength() : "");
        put(values, prefix + "color.icc-sha256", color.getIccProfile().isPresent() ? color.getIccProfile().get().getSha256() : "");
        put(values, prefix + "filters.count", image.getFilters().size());
        for (int index = 0; index < image.getFilters().size(); index++) {
            ImageResource.Filter filter = image.getFilters().get(index);
            String entry = prefix + "filters." + index + ".";
            put(values, entry + "name", filter.getName().getValue());
            put(values, entry + "support", filter.getDecodeSupport());
            optional(values, entry + "predictor", filter.getPredictor());
            optional(values, entry + "colors", filter.getColors());
            optional(values, entry + "bits", filter.getBitsPerComponent());
            optional(values, entry + "columns", filter.getColumns());
            optional(values, entry + "early-change", filter.getEarlyChange());
        }
        mask(values, prefix + "explicit-mask.", image.getExplicitMask().orElse(null), resources);
        mask(values, prefix + "soft-mask.", image.getSoftMask().orElse(null), resources);
        bytes(values, prefix + "encoded.", image.getEncodedData());
        bytes(values, prefix + "decoded.", image.getDecodedData());
    }

    private static void mask(Properties values, String prefix, ImageResource.Mask mask, List<DocumentResource> resources) {
        put(values, prefix + "kind", mask == null ? "" : mask.getKind());
        put(values, prefix + "image", mask != null && mask.getImage().isPresent() ? resources.indexOf(mask.getImage().get()) : "");
        put(values, prefix + "ranges", mask == null ? "" : join(mask.getColorKeyRanges()));
    }

    private static void bytes(Properties values, String prefix, ImageResource.ByteData data) {
        put(values, prefix + "selected", data.isSelected());
        put(values, prefix + "availability", data.getAvailability());
        byte[] bytes = data.getBytes().orElse(null);
        put(values, prefix + "length", bytes == null ? "" : bytes.length);
        put(values, prefix + "sha256", bytes == null ? "" : EvidenceFiles.sha256(bytes));
        if (bytes != null && bytes.length != 0) {
            bytes[0] ^= 0x7f;
            if (!values.getProperty(prefix + "sha256").equals(EvidenceFiles.sha256(data.getBytes().get()))) {
                throw new IllegalStateException("Image bytes are not defensive detached copies");
            }
        }
    }

    private static String join(List<?> values) {
        StringBuilder result = new StringBuilder();
        for (Object value : values) {
            if (result.length() != 0) { result.append(','); }
            result.append(value);
        }
        return result.toString();
    }

    private static void optional(Properties values, String key, OptionalInt value) {
        put(values, key, value.isPresent() ? value.getAsInt() : "");
    }

    private static void put(Properties values, String key, Object value) {
        values.setProperty(key, String.valueOf(value));
    }
}
