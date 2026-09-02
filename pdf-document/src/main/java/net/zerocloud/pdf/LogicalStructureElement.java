package net.zerocloud.pdf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A detached immutable Tagged PDF logical-structure element.
 *
 * <p>Children preserve structure-tree order. Declared and resolved roles,
 * direct and inherited language, alternate text, and replacement text remain
 * separate. Untagged extraction returns no root elements.</p>
 *
 * @since 0.1.0
 */
public final class LogicalStructureElement {

    /** How the declared role was resolved by the version-1 profile. */
    public enum RoleResolution {
        /** The declared role is a standard structure type. */
        STANDARD,
        /** A document RoleMap chain resolves the role to a standard type. */
        ROLE_MAP,
        /** No version-1 mapping resolves the custom role. */
        UNRESOLVED
    }

    /** Source of the effective language exposed for this element. */
    public enum LanguageSource {
        /** The language is declared directly on this element. */
        SELF,
        /** The language is inherited from an ancestor structure element. */
        ANCESTOR,
        /** The language is inherited from the document catalog. */
        DOCUMENT,
        /** No language is available. */
        NONE
    }

    private final int id;
    private final String role;
    private final String resolvedRole;
    private final RoleResolution roleResolution;
    private final String declaredLanguage;
    private final String effectiveLanguage;
    private final LanguageSource languageSource;
    private final String alternateText;
    private final String actualText;
    private final List<LogicalStructureItem> children;

    LogicalStructureElement(
            int id,
            String role,
            String resolvedRole,
            RoleResolution roleResolution,
            String declaredLanguage,
            String effectiveLanguage,
            LanguageSource languageSource,
            String alternateText,
            String actualText,
            List<LogicalStructureItem> children) {
        this.id = id;
        this.role = Objects.requireNonNull(role, "role");
        this.resolvedRole = resolvedRole;
        this.roleResolution = Objects.requireNonNull(
                roleResolution, "roleResolution");
        this.declaredLanguage = declaredLanguage;
        this.effectiveLanguage = effectiveLanguage;
        this.languageSource = Objects.requireNonNull(
                languageSource, "languageSource");
        this.alternateText = alternateText;
        this.actualText = actualText;
        this.children = Collections.unmodifiableList(
                new ArrayList<LogicalStructureItem>(children));
    }

    /** @return the one-based extraction-wide element identifier */
    public int getId() { return id; }

    /** Returns the element's declared structure role. @return role */
    public String getRole() { return role; }

    /** @return the resolved standard role, when version 1 can resolve it */
    public Optional<String> getResolvedRole() {
        return Optional.ofNullable(resolvedRole);
    }

    /** @return how the role was resolved */
    public RoleResolution getRoleResolution() { return roleResolution; }

    /** @return the language declared directly on this element, when present */
    public Optional<String> getDeclaredLanguage() {
        return Optional.ofNullable(declaredLanguage);
    }

    /** @return the direct or inherited language, when present */
    public Optional<String> getEffectiveLanguage() {
        return Optional.ofNullable(effectiveLanguage);
    }

    /** @return the source of the effective language */
    public LanguageSource getLanguageSource() { return languageSource; }

    /** @return alternate description text, when present */
    public Optional<String> getAlternateText() {
        return Optional.ofNullable(alternateText);
    }

    /** @return {@code ActualText} replacement text, when present */
    public Optional<String> getActualText() {
        return Optional.ofNullable(actualText);
    }

    /** @return immutable child items in structure-tree order */
    public List<LogicalStructureItem> getChildren() { return children; }
}
