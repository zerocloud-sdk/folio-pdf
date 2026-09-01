package net.zerocloud.pdf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An immutable backend-neutral annotation supported by the Native Interface.
 *
 * @since 0.1.0
 */
public final class Annotation {

    /** Supported annotation subtypes. @since 0.1.0 */
    public enum Type {
        /** A Text note annotation. */
        TEXT,

        /** A rubber-stamp annotation. */
        STAMP,

        /** A text Highlight annotation. */
        HIGHLIGHT,

        /** A file-attachment annotation. */
        FILE_ATTACHMENT,

        /** A standalone Widget annotation without AcroForm field behavior. */
        WIDGET,

        /** A Link annotation with a local navigation target. */
        LINK
    }

    /** Supported standard Text annotation icon names. @since 0.1.0 */
    public enum TextIcon {
        /** Comment icon. */ COMMENT,
        /** Key icon. */ KEY,
        /** Note icon. */ NOTE,
        /** Help icon. */ HELP,
        /** New-paragraph icon. */ NEW_PARAGRAPH,
        /** Paragraph icon. */ PARAGRAPH,
        /** Insert icon. */ INSERT
    }

    /** Supported standard FileAttachment icon names. @since 0.1.0 */
    public enum FileAttachmentIcon {
        /** Graph icon. */ GRAPH,
        /** Push-pin icon. */ PUSHPIN,
        /** Paperclip icon. */ PAPERCLIP,
        /** Tag icon. */ TAG
    }

    private final Type type;
    private final AnnotationProperties properties;
    private final TextIcon textIcon;
    private final boolean open;
    private final String stampName;
    private final List<AnnotationQuad> quads;
    private final AnnotationColor color;
    private final EmbeddedFile attachment;
    private final FileAttachmentIcon fileAttachmentIcon;
    private final LinkActivation linkActivation;

    private Annotation(
            Type type,
            AnnotationProperties properties,
            TextIcon textIcon,
            boolean open,
            String stampName,
            List<AnnotationQuad> quads,
            AnnotationColor color,
            EmbeddedFile attachment,
            FileAttachmentIcon fileAttachmentIcon,
            LinkActivation linkActivation) {
        this.type = type;
        this.properties = properties;
        this.textIcon = textIcon;
        this.open = open;
        this.stampName = stampName;
        this.quads = quads;
        this.color = color;
        this.attachment = attachment;
        this.fileAttachmentIcon = fileAttachmentIcon;
        this.linkActivation = linkActivation;
    }

    /**
     * Creates a version-1 Text annotation.
     *
     * @param properties the shared annotation properties
     * @param icon the standard Text icon
     * @param open whether the note should initially be displayed open
     * @return the immutable annotation
     */
    public static Annotation text(
            AnnotationProperties properties,
            TextIcon icon,
            boolean open) {
        return new Annotation(
                Type.TEXT,
                Objects.requireNonNull(properties, "properties"),
                Objects.requireNonNull(icon, "icon"),
                open,
                null,
                Collections.<AnnotationQuad>emptyList(),
                null,
                null,
                null,
                null);
    }

    /**
     * Creates a version-1 Stamp annotation.
     *
     * @param properties the shared annotation properties
     * @param stampName the nonempty appearance-independent stamp name
     * @return the immutable annotation
     */
    public static Annotation stamp(
            AnnotationProperties properties,
            String stampName) {
        if (Objects.requireNonNull(stampName, "stampName").isEmpty()) {
            throw new IllegalArgumentException("stampName must not be empty");
        }
        return new Annotation(
                Type.STAMP,
                Objects.requireNonNull(properties, "properties"),
                null,
                false,
                stampName,
                Collections.<AnnotationQuad>emptyList(),
                null,
                null,
                null,
                null);
    }

    /**
     * Creates a version-1 Highlight annotation.
     *
     * @param properties the shared annotation properties
     * @param quads one or more text-markup quadrilaterals
     * @param color the annotation device color
     * @return the immutable annotation
     */
    public static Annotation highlight(
            AnnotationProperties properties,
            List<AnnotationQuad> quads,
            AnnotationColor color) {
        Objects.requireNonNull(quads, "quads");
        if (quads.isEmpty()) {
            throw new IllegalArgumentException("quads must not be empty");
        }
        List<AnnotationQuad> copied = new ArrayList<AnnotationQuad>(
                quads.size());
        for (AnnotationQuad quad : quads) {
            copied.add(Objects.requireNonNull(quad, "quads"));
        }
        return new Annotation(
                Type.HIGHLIGHT,
                Objects.requireNonNull(properties, "properties"),
                null,
                false,
                null,
                Collections.unmodifiableList(copied),
                Objects.requireNonNull(color, "color"),
                null,
                null,
                null);
    }

    /**
     * Creates a version-1 FileAttachment annotation.
     *
     * @param properties the shared annotation properties
     * @param attachment the embedded file related to the annotation
     * @param icon the standard file-attachment icon
     * @return the immutable annotation
     */
    public static Annotation fileAttachment(
            AnnotationProperties properties,
            EmbeddedFile attachment,
            FileAttachmentIcon icon) {
        return new Annotation(
                Type.FILE_ATTACHMENT,
                Objects.requireNonNull(properties, "properties"),
                null,
                false,
                null,
                Collections.<AnnotationQuad>emptyList(),
                null,
                Objects.requireNonNull(attachment, "attachment"),
                Objects.requireNonNull(icon, "icon"),
                null);
    }

    /**
     * Creates a version-1 standalone Widget annotation.
     *
     * <p>This value does not create or manage an AcroForm field.</p>
     *
     * @param properties the shared annotation properties
     * @return the immutable annotation
     */
    public static Annotation widget(AnnotationProperties properties) {
        return new Annotation(
                Type.WIDGET,
                Objects.requireNonNull(properties, "properties"),
                null,
                false,
                null,
                Collections.<AnnotationQuad>emptyList(),
                null,
                null,
                null,
                null);
    }

    /**
     * Creates a version-1 Link annotation.
     * @param properties the shared annotation properties
     * @param activation the local link activation
     * @return the immutable annotation
     */
    public static Annotation link(
            AnnotationProperties properties,
            LinkActivation activation) {
        return new Annotation(
                Type.LINK,
                Objects.requireNonNull(properties, "properties"),
                null,
                false,
                null,
                Collections.<AnnotationQuad>emptyList(),
                null,
                null,
                null,
                Objects.requireNonNull(activation, "activation"));
    }

    /** Returns the supported annotation type. @return the type */
    public Type getType() {
        return type;
    }

    /** Returns the shared annotation properties. @return the properties */
    public AnnotationProperties getProperties() {
        return properties;
    }

    /** Returns the Text icon when this is a Text annotation. @return the icon */
    public Optional<TextIcon> getTextIcon() {
        return Optional.ofNullable(textIcon);
    }

    /** Returns whether a Text note is initially open. @return the open state */
    public boolean isOpen() {
        return open;
    }

    /** Returns the stamp name when this is a Stamp annotation. @return the name */
    public Optional<String> getStampName() {
        return Optional.ofNullable(stampName);
    }

    /** Returns Highlight quadrilaterals, or an empty list. @return the quads */
    public List<AnnotationQuad> getQuads() {
        return quads;
    }

    /** Returns the Highlight color when present. @return the color */
    public Optional<AnnotationColor> getColor() {
        return Optional.ofNullable(color);
    }

    /** Returns the related embedded file when present. @return the file */
    public Optional<EmbeddedFile> getAttachment() {
        return Optional.ofNullable(attachment);
    }

    /** Returns the file-attachment icon when present. @return the icon */
    public Optional<FileAttachmentIcon> getFileAttachmentIcon() {
        return Optional.ofNullable(fileAttachmentIcon);
    }

    /** Returns the Link activation when present. @return the activation */
    public Optional<LinkActivation> getLinkActivation() {
        return Optional.ofNullable(linkActivation);
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof Annotation
                && type == ((Annotation) candidate).type
                && properties.equals(((Annotation) candidate).properties)
                && textIcon == ((Annotation) candidate).textIcon
                && open == ((Annotation) candidate).open
                && Objects.equals(stampName,
                        ((Annotation) candidate).stampName)
                && quads.equals(((Annotation) candidate).quads)
                && Objects.equals(color, ((Annotation) candidate).color)
                && Objects.equals(attachment,
                        ((Annotation) candidate).attachment)
                && fileAttachmentIcon
                        == ((Annotation) candidate).fileAttachmentIcon
                && Objects.equals(linkActivation,
                        ((Annotation) candidate).linkActivation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, properties, textIcon,
                Boolean.valueOf(open), stampName, quads, color,
                attachment, fileAttachmentIcon, linkActivation);
    }

    @Override
    public String toString() {
        return "Annotation[type=" + type + ", properties=" + properties
                + "]";
    }
}
