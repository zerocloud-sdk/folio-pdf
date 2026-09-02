package net.zerocloud.pdf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One detached resource record in deterministic first-declaration order.
 *
 * @since 0.1.0
 */
public class DocumentResource {

    /** Version-1 resource classifications. */
    public enum Kind {
        /** Image XObject or related image-mask stream. */ IMAGE,
        /** Form XObject. */ FORM,
        /** Font resource. */ FONT,
        /** Color-space resource. */ COLOR_SPACE,
        /** Pattern resource. */ PATTERN,
        /** Extended graphics-state resource. */ EXTENDED_GRAPHICS_STATE,
        /** Shading resource. */ SHADING,
        /** Marked-content properties resource. */ PROPERTIES,
        /** Procedure-set declaration. */ PROCEDURE_SET,
        /** XObject with a non-Image, non-Form subtype. */ XOBJECT_OTHER,
        /** Another declared Resources kind. */ OTHER
    }

    private final Kind kind;
    private final ObjectReference objectReference;
    private final List<ResourceDeclaration> declarations;
    private final List<Integer> pageUsage;

    DocumentResource(
            Kind kind,
            ObjectReference objectReference,
            List<ResourceDeclaration> declarations,
            List<Integer> pageUsage) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.objectReference = objectReference;
        this.declarations = Collections.unmodifiableList(
                new ArrayList<ResourceDeclaration>(
                        Objects.requireNonNull(declarations, "declarations")));
        this.pageUsage = Collections.unmodifiableList(
                new ArrayList<Integer>(
                        Objects.requireNonNull(pageUsage, "pageUsage")));
        if (this.declarations.isEmpty() || this.pageUsage.isEmpty()) {
            throw new IllegalArgumentException(
                    "A resource must have a declaration and Page Usage");
        }
    }

    /** Returns the resource classification. @return the kind */
    public final Kind getKind() {
        return kind;
    }

    /**
     * Returns the existing Session Object Reference for an indirect resource.
     * A direct resource deliberately has no fabricated reference.
     *
     * @return the optional reference
     */
    public final Optional<ObjectReference> getObjectReference() {
        return Optional.ofNullable(objectReference);
    }

    /** Returns all declaration locations in encounter order. @return declarations */
    public final List<ResourceDeclaration> getDeclarations() {
        return declarations;
    }

    /** Returns ascending declaration-reachable one-based pages. @return pages */
    public final List<Integer> getPageUsage() {
        return pageUsage;
    }
}
