package net.zerocloud.pdf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable detached Image Resource and Document Resource Inventory result.
 *
 * @since 0.1.0
 */
public final class DocumentResourceInventory {

    private final List<DocumentResource> resources;
    private final List<ImageResource> images;
    private final List<FontResource> fonts;

    DocumentResourceInventory(List<DocumentResource> resources) {
        this.resources = Collections.unmodifiableList(
                new ArrayList<DocumentResource>(resources));
        List<ImageResource> imageValues = new ArrayList<ImageResource>();
        List<FontResource> fontValues = new ArrayList<FontResource>();
        for (DocumentResource resource : this.resources) {
            if (resource instanceof ImageResource) {
                imageValues.add((ImageResource) resource);
            }
            if (resource instanceof FontResource) {
                fontValues.add((FontResource) resource);
            }
        }
        this.images = Collections.unmodifiableList(imageValues);
        this.fonts = Collections.unmodifiableList(fontValues);
    }

    /** Returns every record in deterministic first-declaration order. @return records */
    public List<DocumentResource> getResources() { return resources; }

    /** Returns Image records in their inventory order. @return images */
    public List<ImageResource> getImages() { return images; }

    /** Returns Font records in their inventory order. @return fonts */
    public List<FontResource> getFonts() { return fonts; }
}
