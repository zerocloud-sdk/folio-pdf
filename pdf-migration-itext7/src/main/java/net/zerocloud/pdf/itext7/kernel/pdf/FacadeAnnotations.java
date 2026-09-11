package net.zerocloud.pdf.itext7.kernel.pdf;

import net.zerocloud.pdf.Annotation;
import net.zerocloud.pdf.AnnotationAppearance;
import net.zerocloud.pdf.AnnotationFlag;
import net.zerocloud.pdf.AnnotationProperties;

/** Adapts page ownership while retaining the immutable Native subtype data. */
final class FacadeAnnotations {
    private FacadeAnnotations() {
    }

    static Annotation onPage(Annotation annotation, int pageNumber) {
        return copy(annotation, pageNumber, annotation.getProperties().getAppearance().orElse(null));
    }

    static Annotation withAppearance(Annotation annotation, AnnotationAppearance appearance) {
        return copy(annotation, annotation.getProperties().getPageNumber(), appearance);
    }

    private static Annotation copy(Annotation annotation, int pageNumber, AnnotationAppearance appearance) {
        AnnotationProperties original = annotation.getProperties();
        AnnotationProperties.Builder properties = AnnotationProperties.version1(
                original.getIdentifier(), pageNumber, original.getRectangle());
        if (original.getContents().isPresent()) {
            properties.contents(original.getContents().get());
        }
        for (AnnotationFlag flag : original.getFlags()) {
            properties.flag(flag);
        }
        if (appearance != null) {
            properties.appearance(appearance);
        }
        AnnotationProperties replacement = properties.build();
        switch (annotation.getType()) {
            case TEXT:
                return Annotation.text(replacement, annotation.getTextIcon().get(), annotation.isOpen());
            case STAMP:
                return Annotation.stamp(replacement, annotation.getStampName().get());
            case HIGHLIGHT:
                return Annotation.highlight(replacement, annotation.getQuads(), annotation.getColor().get());
            case FILE_ATTACHMENT:
                return Annotation.fileAttachment(replacement, annotation.getAttachment().get(),
                        annotation.getFileAttachmentIcon().get());
            case WIDGET:
                return Annotation.widget(replacement);
            case LINK:
                return Annotation.link(replacement, annotation.getLinkActivation().get());
            default:
                throw new IllegalArgumentException("Unknown annotation type.");
        }
    }
}
